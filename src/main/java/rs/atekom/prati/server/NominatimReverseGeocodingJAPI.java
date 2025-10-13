package rs.atekom.prati.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import javax.net.ssl.HttpsURLConnection;

/**
 * Reverse geocoding преко OpenStreetMap Nominatim сервиса.
 *
 * <p>ВАЖНО:
 * - Јавни Nominatim (https://nominatim.openstreetmap.org) има ограничения (rate-limit ~1 захтев/сек).
 * - Захтева јединствени User-Agent са контакт информацијом (нпр. email).
 * - Ако се rate-limit или UA не испоштују, често враћа 403/429.
 *
 * Ова имплементација:
 * - Поставља важећи User-Agent са контакт адресом (prati@atekom.rs).
 * - Додаје параметре accept-language и email у упит.
 * - Проверава HTTP код и при неуспеху чита error stream (ради дијагностике).
 * - Уводи минимални rate-limit (1 захтев/сек) унутар процеса.
 */
public class NominatimReverseGeocodingJAPI {

    /** Основни URL до Nominatim инстанце (нпр. "https://nominatim.openstreetmap.org"). */
    private final String nominatimInstance;

    /** Зоом ниво прецизности адресе (0–18). Већа вредност → детаљнија адреса. */
    private int zoomLevel = 18;

    // --- Минимални “throttle” да не погазимо rate-limit јавног Nominatim-а ---

    /** Минимум 1000 ms између два позива (≈1 req/s). По потреби прилагодити за self-hosted. */
    private static final long RATE_LIMIT_MS = 1000;

    /** Време последњег извршеног позива (у ms). Користимо га да “успавамо” наредни ако је прерано. */
    private static final AtomicLong LAST_CALL_AT = new AtomicLong(0);

    /**
     * Конструктор са подразумеваним зоомом 18.
     *
     * @param url базни URL Nominatim инстанце
     */
    public NominatimReverseGeocodingJAPI(String url) {
        this.nominatimInstance = url;
    }

    /**
     * Конструктор са експлицитним зоом нивоом (валидан опсег 0–18).
     *
     * @param url базни URL Nominatim инстанце
     * @param zoomLevel тражени зоом (ако је ван опсега, користи се 18)
     */
    public NominatimReverseGeocodingJAPI(String url, int zoomLevel) {
        this.nominatimInstance = url;
        if (zoomLevel < 0 || zoomLevel > 18) {
            System.err.println("invalid zoom level, using default value");
            zoomLevel = 18;
        }
        this.zoomLevel = zoomLevel;
    }

    /**
     * ВРАЋА адресни објекат за дате координате.
     * <p>Задржавамо изворни назив методе (getAdress) да не бисмо ломили постојеће позиве.
     *
     * @param lat географска ширина
     * @param lon географска дужина
     * @return Address парсиран из JSON-а или null ако је дошло до грешке/блокаде
     */
    public Address getAdress(double lat, double lon) {
        Address result = null;

        // Састављамо URL за reverse endpoint:
        // - format=jsonv2: стабилнији излаз у односу на “json”
        // - addressdetails=1: укључи структуриране компоненте адресе
        // - accept-language=sr-RS: адресе на српском (по жељи мењати у “en” итд.)
        // - email=prati@atekom.rs: комплементарно UA-у; помаже за поузданост и контакт
        String urlString = nominatimInstance
                + "/reverse?format=jsonv2&addressdetails=1"
                + "&lat=" + lat
                + "&lon=" + lon
                + "&zoom=" + zoomLevel
                + "&accept-language=sr-RS"
                + "&email=prati@atekom.rs";

        try {
            // Извршавамо HTTP GET; метод већ води рачуна о rate-limit-у и статус коду.
            String json = getJSON(urlString);

            // Овде се претпоставља да постоји класа Address која уме да парсира Nominatim JSON.
            result = new Address(json, zoomLevel);
        } catch (IOException e) {
            // Не штампамо stacktrace да не загушимо лог, али враћамо јасну поруку.
            System.err.println("Nominatim reverse geocode failed: " + e.getMessage());
        }
        return result;
    }

    /**
     * Проста блокада (sleep) да обезбедимо најмање RATE_LIMIT_MS између два позива.
     * <p>Напомена: ово је најједноставнији приступ. За вишенитне/масовне позиве боље је увести
     * пуферисање/ред и централизовано ограничавање брзине (token bucket, semaphore и сл.).
     */
    private static void honorRateLimit() {
        long now = System.currentTimeMillis();
        long last = LAST_CALL_AT.get();
        long delta = now - last;

        if (delta < RATE_LIMIT_MS) {
            long sleep = RATE_LIMIT_MS - delta;
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        LAST_CALL_AT.set(System.currentTimeMillis());
    }

    /**
     * Извршава HTTP GET према задатом URL-у и враћа тело одговора као String.
     *
     * <p>Имплементација:
     * - Поштује rate-limit (honorRateLimit()).
     * - Поставља валидан User-Agent са контакт-мејлом (обавезно за јавни Nominatim).
     * - Поставља timeout-ове (конекција/читање) да не блокира неограничено.
     * - Проверава HTTP статус код; ако није 2xx, чита error stream ради дијагнозе и баца IOException.
     *
     * @param urlString пуни URL
     * @return тело одговора као String
     * @throws IOException ако је код ≠ 2xx или ако настане I/O проблем
     */
    private String getJSON(String urlString) throws IOException {
        honorRateLimit(); // Минимални паузер да избегнемо 429.

        HttpsURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            // Конзервативни timeout-ови; прилагодити по потреби.
            conn.setConnectTimeout(4000); // максимум 4s за успостављање конекције
            conn.setReadTimeout(6000);    // максимум 6s за читање одговора

            // ✅ Јединствени UA са контактом (услов из Nominatim policy).
            conn.setRequestProperty("User-Agent", "PratiApp/1.0 (+mailto:prati@atekom.rs)");

            // Није строго неопходно, али помаже за дијагностику/доверљивост.
            conn.setRequestProperty("Referer", "https://prati.rs");
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();

            // Ако је све у реду (2xx), враћамо тело одговора.
            if (code >= 200 && code < 300) {
                return readAll(conn.getInputStream());
            } else {
                // У супротном, покушај да прочиташ error stream (често садржи поруку о 403/429).
                String err = readAllSafe(conn.getErrorStream());
                throw new IOException("HTTP " + code + " from Nominatim: " + (err.isEmpty() ? "<no body>" : err));
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Чита цео InputStream у String (UTF-8).
     */
    private static String readAll(InputStream is) throws IOException {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    /**
     * Безбедна варијанта која враћа празан String ако је stream null или ако читање падне.
     */
    private static String readAllSafe(InputStream is) {
        if (is == null) return "";
        try {
            return readAll(is);
        } catch (IOException e) {
            return "";
        }
    }
}
