package rs.atekom.prati.view.komponente;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;

import org.junit.Before;
import org.junit.Test;

import pratiBaza.tabele.Javljanja;
import pratiBaza.tabele.Objekti;
import pratiBaza.tabele.SistemAlarmi;
import pratiBaza.tabele.SistemPretplatnici;
import rs.atekom.prati.server.NominatimClient;

public class IzvrsavanjeAlarmAdresaTest {

    // Минималан „stub“ NominatimClient-а за unit тест (без Spring-а)
    static class NominatimClientStub extends NominatimClient {
        public NominatimClientStub() { super("test@example.com", "https://nominatim.openstreetmap.org"); }
        @Override public String getAddress(Double lat, Double lon) {
            return "Fallback Addr, Beograd";
        }
    }

    @Before
    public void setup() {
        // У unit тесту ручно постављамо fallback клијент (у продукцији га прави sistemServis)
    	NominatimClientStub nClient = new NominatimClientStub();
    }

    private Javljanja napraviJavljanjeSaGoogleKeyKojiCePasti() {
        Javljanja j = new Javljanja();
        j.setLat(44.7866);
        j.setLon(20.4489);
        j.setEventData("0");

        SistemAlarmi sa = new SistemAlarmi();
        sa.setSifra("1");     // != "0" → омогући адресирање
        sa.setAdresa(true);
        j.setSistemAlarmi(sa);

        Objekti obj = new Objekti();
        SistemPretplatnici sp = new SistemPretplatnici();
        sp.setApiKey("DUMMY_KEY_THAT_WILL_FAIL"); // намерно „лош“ кључ → Google грана пада → fallback
        obj.setSistemPretplatnici(sp);
        j.setObjekti(obj);

        return j;
    }

    @Test
    public void kadaGooglePukne_ideFallbackNaNominatimClient_iPuniEventData() throws Exception {
        Izvrsavanje comp = new Izvrsavanje();
        Javljanja j = napraviJavljanjeSaGoogleKeyKojiCePasti();

        // Позивамо приватни метод преко рефлексије да изолујемо само адресирање
        Method m = Izvrsavanje.class.getDeclaredMethod("alarmAdresa", Javljanja.class);
        m.setAccessible(true);
        m.invoke(comp, j);

        String ev = j.getEventData();
        assertTrue("Очекиван је fallback на NominatimClient", ev != null && ev.contains("Fallback Addr"));
    }
}
