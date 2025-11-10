package rs.atekom.prati.view.komponente;

import java.util.ArrayList;
import java.util.Date;
//import com.google.maps.model.LatLng;
import com.ibm.icu.text.SimpleDateFormat;
//import com.google.maps.model.GeocodingResult;
//import com.google.maps.GeoApiContext;
//import com.google.maps.GeocodingApi;
import pratiBaza.tabele.AlarmiKorisnik;
import pratiBaza.tabele.Javljanja;
import rs.atekom.prati.Broadcaster;
import rs.atekom.prati.server.Address;
import rs.atekom.prati.server.Servis;

public class Izvrsavanje {

	private static final String DATUMVREME = "dd/MM/yyyy HH:mm:ss";
	private SimpleDateFormat datumVreme;
	
	public Izvrsavanje() {
		datumVreme = new SimpleDateFormat(DATUMVREME);
	}
	
	
	public void obradaAlarma(Javljanja javljanje, ArrayList<AlarmiKorisnik> alarmiKorisnici) {
		alarmAdresa(javljanje);
		
		Servis.javljanjeServis.unesiJavljanja(javljanje);//ovde negde problem??
		
        Broadcaster.broadcast(javljanje);
        
        for(AlarmiKorisnik alarmKorisnik : alarmiKorisnici) {
        	if(alarmKorisnik.getSistemAlarmi().getId().equals(javljanje.getSistemAlarmi().getId()) && alarmKorisnik.isEmail()) {
        		String zaglavlje = "Праћење возила - " + javljanje.getObjekti().getOznaka() + " - " + javljanje.getSistemAlarmi().getNaziv();
        		String poruka = String.join("\n"
        		         , "Поштовани,"
        		         , "Објекат " + javljanje.getObjekti().getOznaka() + " је активирао аларм " + javljanje.getSistemAlarmi().getNaziv()
        		         , "у " + datumVreme.format(javljanje.getDatumVreme()) + " " + javljanje.getEventData()
        		         , " "
        		         , "Порука је аутоматски генерисана, немојте одговарати."
        		         , "Атеком доо, www.atekom.rs prati@atekom.rs"
        		);
        		Servis.posta.posaljiMail(alarmKorisnik.getKorisnik().getEmail(), zaglavlje, poruka);
        	}
        }
	}
	
	private void alarmAdresa(Javljanja javljanje) {
	    if (javljanje == null || javljanje.getSistemAlarmi() == null) return;
	    if ("0".equals(javljanje.getSistemAlarmi().getSifra())) return;
	    double lat = javljanje.getLat(), lon = javljanje.getLon();
	    if (lat == 0.0 || lon == 0.0) return;

	    String adresa = "";
	    try {
	        if (javljanje.getSistemAlarmi().isAdresa()) {
	            // 1) Google (globalni ključ iz Servis.ensureGContext)
	            /*try {
	                GeoApiContext gctx = Servis.ensureGContext();
	                LatLng poz = new LatLng(lat, lon);
	                GeocodingResult[] r = GeocodingApi.reverseGeocode(gctx, poz).await();
	                
	                if (r != null && r.length > 0 && r[0] != null && r[0].formattedAddress != null) {
	                    adresa = r[0].formattedAddress;
	                }
	                
	            } catch (Exception ge) {
	                System.err.println("[alarmAdresa] Google error: " + ge.getMessage());
	            }*/

	            // 2) Ako nema Google adrese → Nominatim JSON
	            if (adresa.isEmpty()) {
	                try {
	                    Address a = Servis.ensureNominatimJson().getAdress(lat, lon);
	                    if (a != null) {
	                        StringBuilder sb = new StringBuilder();
	                        if (!a.getHouseNumber().isEmpty()) sb.append(a.getHouseNumber()).append(", ");
	                        if (!a.getRoad().isEmpty())        sb.append(a.getRoad()).append(", ");
	                        if (!a.getSuburb().isEmpty())      sb.append(a.getSuburb()).append(", ");
	                        if (!a.getCity().isEmpty())        sb.append(a.getCity()).append(' ');
	                        if (!a.getPostcode().isEmpty())    sb.append(a.getPostcode());
	                        if (!a.getCounty().isEmpty())      sb.append(", ").append(a.getCounty()).append(", ");
	                        if (!a.getCountry().isEmpty())     sb.append(a.getCountry());
	                        adresa = sb.toString().trim();
	                    }
	                } catch (Exception ne) {
	                    System.err.println("[alarmAdresa] Nominatim JSON error: " + ne.getMessage());
	                }
	            }

	            // 3) Ako i dalje prazno → NominatimClient
	            if (adresa.isEmpty()) {
	                try {
	                    adresa = Servis.ensureNClient().getAddress(lat, lon);
	                    if (adresa == null) adresa = "";
	                } catch (Exception ce) {
	                    System.err.println("[alarmAdresa] NominatimClient error: " + ce.getMessage());
	                }
	            }

	            // 4) Upis u eventData
	            if (!adresa.isEmpty()) {
	                String cur = javljanje.getEventData();
	                if (cur == null || cur.isBlank() || "0".equals(cur)) {
	                    javljanje.setEventData(adresa);
	                } else {
	                    String novo = (cur + " " + adresa).trim();
	                    javljanje.setEventData(novo.length() > 250 ? adresa : novo);
	                }
	            }
	        }
	    } catch (Exception e) {
	        System.err.println("[alarmAdresa] Neočekivana greška: " + e.getMessage());
	    }
	}


	
    public long razlika(Date vreme){
    	return System.currentTimeMillis() - vreme.getTime();
    }
}
