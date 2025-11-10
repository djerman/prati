package rs.atekom.prati.server;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Live integration test against https://nominatim.openstreetmap.org for Žike Popović street.
 *
 * <p>Овај тест прави прави HTTP позив ка јавној Nominatim инстанци и због тога може да падне ако
 * нема интернет конекције, ако сервис није доступан или ако се прекрши rate-limit. Намена је да се
 * ручно покрене када је потребно проверити да ли {@link NominatimReverseGeocodingJAPI} враћа реалну
 * адресу за конкретне координате.
 */
public class NominatimReverseGeocodingJAPITest {

    /**
     * Омогућава да се тест покрене и као обичан Java program (`Run → Java Application`).
     */
    public static void main(String[] args) {
        Address address = new NominatimReverseGeocodingJAPITest().executeReverseGeocode();
        if (address == null) {
            System.out.println("Nominatim позив није вратио адресу (null).");
        }
    }

    @Test
    public void liveReverseGeocoding_returnsAddressForZikePopovic() {
        Address address = executeReverseGeocode();

        assertNotNull("Адреса не сме бити null", address);
        assertNotNull("Дисплеј име не сме бити null", address.getDisplayName());
        assertFalse("Дисплеј име не сме бити празно", address.getDisplayName().trim().isEmpty());
        assertNotNull("Пут (road) не сме бити null", address.getRoad());
        assertTrue(
                "Пут мора да садржи Žike Popović",
                address.getRoad().toLowerCase().contains("žike popović"));
    }

    private Address executeReverseGeocode() {
        NominatimReverseGeocodingJAPI api =
                new NominatimReverseGeocodingJAPI("https://nominatim.openstreetmap.org");

        Address a = api.getAdress(44.75179152321391, 19.70317555223673);

        System.out.println("Reverse geocode display name: " + (a != null ? a.getDisplayName() : "<null>"));
        System.out.println("Reverse geocode road: " + (a != null ? a.getRoad() : "<null>"));
        System.out.println("Reverse geocode city: " + (a != null ? a.getCity() : "<null>"));
        System.out.println("Reverse geocode postcode: " + (a != null ? a.getPostcode() : "<null>"));

        if (a != null) {
        	String adresa = "";
            StringBuilder sb = new StringBuilder();
            if (!a.getHouseNumber().isEmpty()) sb.append(a.getHouseNumber()).append(", ");
            if (!a.getDisplayName().isEmpty()) sb.append(a.getDisplayName());
            if (!a.getRoad().isEmpty())        sb.append(a.getRoad()).append(", ");
            if (!a.getSuburb().isEmpty())      sb.append(a.getSuburb()).append(", ");
            if (!a.getCity().isEmpty())        sb.append(a.getCity()).append(' ');
            if (!a.getPostcode().isEmpty())    sb.append(a.getPostcode());
            if (!a.getCounty().isEmpty())      sb.append(", ").append(a.getCounty()).append(", ");
            if (!a.getCountry().isEmpty())     sb.append(a.getCountry());
            adresa = sb.toString().trim();
            System.out.println("Adresa: " + adresa);
        }
        
        return a;
    }
}