package rs.atekom.prati.view.komponente;

import static org.junit.Assert.*;

import java.lang.reflect.Method;

import org.junit.Before;
import org.junit.Test;

import pratiBaza.tabele.Javljanja;
import pratiBaza.tabele.Objekti;
import pratiBaza.tabele.SistemAlarmi;
import pratiBaza.tabele.SistemPretplatnici;
import rs.atekom.prati.server.NominatimClient;
import rs.atekom.prati.server.NominatimReverseGeocodingJAPI;

/**
 * Интеграциони тест који стварно позива приватни метод alarmAdresa(...)
 * и штампа адресу коју је уписао у javljanje.eventData.
 *
 * Напомена: тест користи јавни Nominatim (1 req/s). Правимо један позив.
 */
public class IzvrsavanjeAlarmAdresaIT {

    @Before
    public void setup() {
        // Иницијализуј Nominatim клијенте које користи Izvrsavanje.alarmAdresa(...)
        // 1) JSON клијент (наша класа)
    	NominatimReverseGeocodingJAPI nominatim = new NominatimReverseGeocodingJAPI(
                "https://nominatim.openstreetmap.org", 18);

        // 2) NominatimClient (fr.dudie) као fallback
    	NominatimClient nClient = new NominatimClient(
                "prati@atekom.rs",
                "https://nominatim.openstreetmap.org");
    }

    private Javljanja napraviJavljanjeBezGoogleKljuca(double lat, double lon) {
        Javljanja j = new Javljanja();
        j.setLat(lat);
        j.setLon(lon);
        j.setEventData("0"); // тако да се упише чиста адреса

        SistemAlarmi sa = new SistemAlarmi();
        sa.setSifra("1");      // != "0" → омогући адресирање
        sa.setAdresa(true);    // тражи адресу
        j.setSistemAlarmi(sa);

        Objekti obj = new Objekti();
        SistemPretplatnici sp = new SistemPretplatnici();
        sp.setApiKey(null);    // без Google API key → иде на Nominatim
        obj.setSistemPretplatnici(sp);
        j.setObjekti(obj);

        return j;
    }

    @Test
    public void pozoviAlarmAdresa_i_odstampajDobijenuAdresu() throws Exception {
        // Координате Београда
        double lat = 44.75187;
        double lon = 19.70321;

        Javljanja j = napraviJavljanjeBezGoogleKljuca(lat, lon);

        Izvrsavanje comp = new Izvrsavanje();
        Method m = Izvrsavanje.class.getDeclaredMethod("alarmAdresa", Javljanja.class);
        m.setAccessible(true);

        // позови приватни метод
        m.invoke(comp, j);

        String ev = j.getEventData();
        System.out.println("EVENT_DATA = " + ev); // ово ћеш видети у излазу

        assertNotNull("Адреса не сме бити null", ev);
        assertFalse("Адреса не сме бити празна", ev.trim().isEmpty());

        // толерантна провера: бар да садржи референцу на град/државу
        String lower = ev.toLowerCase();
        assertTrue(lower.contains("beograd") || lower.contains("belgrade")
                || lower.contains("србија") || lower.contains("serbia"));
    }
}
