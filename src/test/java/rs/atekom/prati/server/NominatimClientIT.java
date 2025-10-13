package rs.atekom.prati.server;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Интеграциони тест: стварно погађа јавни Nominatim.
 * За координате Београда треба да добијемо неку разумну адресу.
 */
public class NominatimClientIT {

    @Test
    public void reverseGeocode_Beograd_realCall_printsAddress() {
        // arrange
        String email = "prati@atekom.rs";
        String baseUrl = "https://nominatim.openstreetmap.org";
        NominatimClient client = new NominatimClient(email, baseUrl);

        Double lat = 44.7866;   // Београд
        Double lon = 20.4489;

        // act
        String addr = client.getAddress(lat, lon);

        // print — видећеш у консоли/Surefire извештају
        System.out.println("NOMINATIM_ADDR = " + addr);

        // assert — барем нешто смислено мора да стигне
        assertNotNull("Адреса не сме бити null", addr);
        assertFalse("Адреса не сме бити празна", addr.trim().isEmpty());
        // толерантно: прихватамо латиницу/ћирилицу и различите варијанте
        String lower = addr.toLowerCase();
        assertTrue(
            "Очекивано је да садржи референцу на Београд/Србију",
            lower.contains("beograd") || lower.contains("belgrade")
             || lower.contains("србија") || lower.contains("serbia")
        );
    }
}
