package rs.atekom.prati.server.updated;

import java.sql.Timestamp;
import java.util.Date;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import pratiBaza.tabele.Javljanja;
import pratiBaza.tabele.Objekti;
import pratiBaza.tabele.SistemAlarmi;
import rs.atekom.prati.server.Servis;

public class GenekoProtokolUpdate {
	
	private OpstiServerUpdate server;
	
	public GenekoProtokolUpdate(OpstiServerUpdate srv) {
		server = srv;
	}
	
	public Javljanja genekoObrada(String podaci, Objekti objekat) {

		Javljanja  javljanje = null;
		Date sada;
		Timestamp upis;
		SistemAlarmi alarm;
		String alarmId = "0";
		Boolean valid;
		Timestamp datumVreme = new Timestamp(parseDateTime("010170", "000000").getTime());;
		Double longitude = 0.00;
		Double latitude = 0.00;
		Integer brzina = 0;
		Float pravac = 0.00f;
		Float virtualOdo = 0.00f;
		String eventData = "0";
		Float nadmorskaVisina = 0.00f;
		String iButton = "0";
		Boolean kontakt = false;
		boolean ispravnoVreme;
		try{
			String[] da = podaci.split(",");
			if(da[1].equals("A")){
				valid = true;
			}else {
				valid = false;
			}
			if(valid || !da[6].equals("0000.0000")){
				if(da[0].equals("1071")){
					alarmId = "6022";
					}else{
						alarmId = da[0];
						}
				try{
					datumVreme = new Timestamp(parseDateTime(da[2], da[3]).getTime());
					ispravnoVreme = true;
					}catch(Exception e){
						System.out.println("Greška vreme");
						ispravnoVreme = false;
					}
				//pozicija 4 i 5, 6 i 7
				longitude = parseDouble(da[6].substring(0, 3)) + parseDouble(da[6].substring(3)) / 60.0;
				if (da[5].equalsIgnoreCase("S")) {
					longitude *= -1;
					}
				latitude = parseDouble(da[4].substring(0, 2)) + parseDouble(da[4].substring(2)) / 60.0;
				if (da[7].equalsIgnoreCase("W")) {
					latitude *= -1;
					}
				//brzina 8
				if(da[8]!=null && !da[8].isEmpty()){
					Float gpsBrzina = (float)(Float.parseFloat(da[8]) * 1.609344);
					brzina = gpsBrzina.intValue();
					}
				//pravac 9
				if(da[9]!=null && !da[9].isEmpty()){
					pravac = parseFloat(da[9]);
					}
				//event data
				eventData = "0";
				//ibutton
				iButton = "0";
				String ostatak = da[11];
				
				if(ostatak.charAt(8) == '1'){
					kontakt = true;
				}else{
					kontakt = false;
				}
				sada = new Date();
				upis = new Timestamp(sada.getTime());
				alarm = Servis.sistemAlarmServis.nadjiAlarmPoSifri(alarmId);
				
				if(alarm == null || alarmId.equals("0")){
					alarm = server.redovno;
					}
				
				if(objekat != null){
					if(brzina < 200 && ispravnoVreme){
						javljanje = new Javljanja();
						javljanje.setValid(valid);
						javljanje.setObjekti(objekat);
						javljanje.setDatumVreme(datumVreme);
						javljanje.setLon(longitude);
						javljanje.setLat(latitude);
						javljanje.setPravac(pravac);
						javljanje.setBrzina(brzina);
						javljanje.setVisina(nadmorskaVisina);
						javljanje.setSistemAlarmi(alarm);
						javljanje.setKontakt(kontakt);
						javljanje.setVirtualOdo(virtualOdo);
						javljanje.setEventData(eventData);
						javljanje.setIbutton(iButton);
						javljanje.setKreirano(upis);
						javljanje.setIzmenjeno(upis);
					}
					return javljanje;
					}else{
						return null;
						}
				}
			}catch(Exception e){
				System.out.println("Nije podržan protokol" + " " + e.getMessage());
				return null;
				}
		return javljanje;
	}
	
	private Date parseDateTime(String dt, String tm) {
		try {
			int dd = Integer.parseInt(dt.substring(0, 2));
			int mm = Integer.parseInt(dt.substring(2, 4));
			int yy = Integer.parseInt("20" + dt.substring(4));
			int hh = Integer.parseInt(tm.substring(0, 2));
			int ii = Integer.parseInt(tm.substring(2, 4));
			int ss = Integer.parseInt(tm.substring(4));
			DateTime dateTime = new DateTime(yy, mm, dd, hh, ii, ss, DateTimeZone.UTC);
			return dateTime.toDate();
		} catch (NumberFormatException e) {
			return null;
		}
	}
	private Double parseDouble(String v) {
		return parseDouble(v, null);
	}
	
	private Double parseDouble(String v, Double dv) {
		try {
			Double pv = Double.parseDouble(v);
			return pv;
		} catch (NumberFormatException ex) {
			return dv;
		}
	}
	
	private Float parseFloat(String v) {
		return parseFloat(v, null);
	}

	private Float parseFloat(String v, Float dv) {
		try {
			Float pv = Float.parseFloat(v);
			return pv;
		} catch (NumberFormatException ex) {
			return dv;
		}
	}
}
