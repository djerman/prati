package rs.atekom.prati.server.ruptela;

import java.sql.Timestamp;
import java.util.Date;
import pratiBaza.tabele.Javljanja;
import pratiBaza.tabele.Obd;
import pratiBaza.tabele.Objekti;
import pratiBaza.tabele.SistemAlarmi;
import rs.atekom.prati.server.JavljanjeObd;
import rs.atekom.prati.server.Servis;

public class RuptelaProtokol {
	
	private SistemAlarmi redovno, sos, aktiviran, deaktiviran;
	
	public RuptelaProtokol() {
		redovno = Servis.sistemAlarmServis.nadjiAlarmPoSifri("0");
		sos = Servis.sistemAlarmServis.nadjiAlarmPoSifri("6022");
		aktiviran = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1092");
		deaktiviran = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1091");
	}
	
	public JavljanjeObd vratiJavljanje(int offset, Objekti objekat, String poruka) {
		Date sada = new Date();
		Javljanja javljanje = new Javljanja();
		javljanje.setObjekti(objekat);
		javljanje.setValid(true);
		javljanje.setVirtualOdo(0.0f);
		 
		Obd obd = new Obd();
		obd.setObjekti(objekat);
		obd.setRpm(0);
		obd.setTemperatura(0);
		obd.setOpterecenje(0.0f);
		obd.setGas(0.0f);
		obd.setNivoGoriva(0.0f);
		obd.setAkumulator(0.0f);
		obd.setTripKm(0.0f);
		obd.setTripGorivo(0.0f);
		obd.setUkupnoVreme(0.0f);
		obd.setUkupnoKm(0);
		obd.setUkupnoGorivo(0.0f);
		obd.setProsecnaPotrosnja(0.0f);
		obd.setGreske("");
		
		//za greške nema??
		//datum vreme
		Timestamp datumVreme = new Timestamp(Long.parseLong(poruka.substring(offset, offset + 8), 16) * 1000);
		javljanje.setDatumVreme(datumVreme);
		obd.setDatumVreme(datumVreme);
		offset += 8;
		//timestamp ext
		offset += 2;
		//priority
		offset += 2;
		//longitude
		javljanje.setLon((double)((int)Long.parseLong(poruka.substring(offset, offset + 8 ), 16)) / 10000000);
		offset += 8;
		//latitude
		javljanje.setLat((double)((int)Long.parseLong(poruka.substring(offset, offset + 8 ), 16)) / 10000000);
		offset += 8;
		//altitude
		javljanje.setVisina((float)(Integer.parseInt(poruka.substring(offset, offset + 4), 16) / 10));
		offset += 4;
		//angle - pravac
		javljanje.setPravac((float)(Integer.parseInt(poruka.substring(offset, offset + 4), 16) / 100));
		offset += 4;
		//sateliti
		offset += 2;
		//brzina
		javljanje.setBrzina((Integer.parseInt(poruka.substring(offset, offset + 4), 16)));
		offset += 4;
		//HDOP
		offset += 2;
		//event id
		//System.out.println("event id: " + poruka.substring(offset, offset + 2)); komanda 68 za extended protokol
		int eventId = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
		offset += 2;
		 
		javljanje.setSistemAlarmi(redovno);
		javljanje.setEventData("0");
		javljanje.setIbutton("0");
		//očitavanje vrednosti od 1 bajta
		int brJedan = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
		offset += 2;
		int i = 0;
		while(i < brJedan) {
			upisi1bajt(Integer.parseInt(poruka.substring(offset, offset + 2), 16), poruka.substring(offset + 2, offset + 4), javljanje, eventId, obd);
			offset += 4;
			i++;
			}
		//očitavanje vrednosti od 2 bajta
		int brDva = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
		offset += 2;
		int j = 0;
		while(j < brDva) {
			upisi2bajta(Integer.parseInt(poruka.substring(offset, offset + 2), 16), poruka.substring(offset + 2, offset + 6), obd);
			offset += 6;
			j++;
			}
		//očitavanje vrednosti od 4 bajta
		int brCetiri = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
		offset += 2;
		int k = 0;
		while(k < brCetiri) {
			upisi4bajta(Integer.parseInt(poruka.substring(offset, offset + 2), 16), poruka.substring(offset + 2, offset + 10), javljanje, obd);
			offset += 10;
			k++;
			}
		//očitavanje vrednosti od 8 bajtova
		int brOsam = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
		offset += 2;
		int l = 0;
		while(l < brOsam) {
			offset += 18;
			l++;
			}
		//završetak
		//thread.setOffset(offset);
		 
		obd.setKreirano(new Timestamp(sada.getTime()));
		obd.setIzmenjeno(new Timestamp(sada.getTime()));
		javljanje.setKreirano(new Timestamp(sada.getTime()));
		javljanje.setIzmenjeno(new Timestamp(sada.getTime()));
		if(obd.getNivoGoriva() == 0.0f && obd.getProsecnaPotrosnja() == 0.0f && obd.getRpm() == 0 && obd.getTemperatura() == 0 
				&& obd.getUkupnoGorivo() == 0.0f && obd.getUkupnoKm() == 0){
			obd = null;
			}
		
		return new JavljanjeObd(javljanje, obd);	
	
		}
	
	
	
	public JavljanjeObd vratiExtended(int offset, Objekti objekat, String poruka) {
		//System.out.println("obj id: " + objekat.getOznaka());
		Date sada = new Date();
		Javljanja javljanje = new Javljanja();
		javljanje.setObjekti(objekat);
		javljanje.setValid(true);
		javljanje.setVirtualOdo(0.0f);
		 
		Obd obd = new Obd();
		obd.setObjekti(objekat);
		obd.setRpm(0);
		obd.setTemperatura(0);
		obd.setOpterecenje(0.0f);
		obd.setGas(0.0f);
		obd.setNivoGoriva(0.0f);
		obd.setAkumulator(0.0f);
		obd.setTripKm(0.0f);
		obd.setTripGorivo(0.0f);
		obd.setUkupnoVreme(0.0f);
		obd.setUkupnoKm(0);
		obd.setUkupnoGorivo(0.0f);
		obd.setProsecnaPotrosnja(0.0f);
		obd.setGreske("");

		//datum vreme
		Timestamp datumVreme = new Timestamp(Long.parseLong(poruka.substring(offset, offset + 8), 16) * 1000);
		javljanje.setDatumVreme(datumVreme);
		obd.setDatumVreme(datumVreme);
		offset += 8;
		//timestamp ext
		offset += 2;
		//Record ext for extended protocol
		offset += 2;
		//priority
		offset += 2;
		//longitude
		javljanje.setLon((double)((int)Long.parseLong(poruka.substring(offset, offset + 8 ), 16)) / 10000000);
		offset += 8;
		//latitude
		javljanje.setLat((double)((int)Long.parseLong(poruka.substring(offset, offset + 8 ), 16)) / 10000000);
		offset += 8;
		//altitude
		javljanje.setVisina((float)(Integer.parseInt(poruka.substring(offset, offset + 4), 16) / 10));
		offset += 4;
		//angle - pravac
		javljanje.setPravac((float)(Integer.parseInt(poruka.substring(offset, offset + 4), 16) / 100));
		offset += 4;
		//sateliti
		offset += 2;
		//brzina
		javljanje.setBrzina((Integer.parseInt(poruka.substring(offset, offset + 4), 16)));
		offset += 4;
		//HDOP
		offset += 2;
		//event id
		//proveriti eventID
		int eventId = Integer.parseInt(poruka.substring(offset, offset + 4), 16);
		offset += 4;
		javljanje.setSistemAlarmi(redovno);
		javljanje.setEventData("0");
		javljanje.setIbutton("0");
		//očitavanje vrednosti od 1 bajta
		int brJedan = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
		//System.out.println("br 1: " + brJedan);
		offset += 2;
		int i = 0;
		while(i < brJedan) {
			upisi1bajt(Integer.parseInt(poruka.substring(offset, offset + 4), 16), poruka.substring(offset + 4, offset + 6), javljanje, eventId, obd);
			offset += 6;
			i++;
			}
		//očitavanje vrednosti od 2 bajta
		int brDva = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
		//System.out.println("br 2: " + brDva);
		offset += 2;
		int j = 0;
		while(j < brDva) {
			upisi2bajta(Integer.parseInt(poruka.substring(offset, offset + 4), 16), poruka.substring(offset + 4, offset + 8), obd);
			offset += 8;
			j++;
			}
		//očitavanje vrednosti od 4 bajta
		int brCetiri = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
		//System.out.println("br 4: " + brCetiri);
		offset += 2;
		int k = 0;
		while(k < brCetiri) {
			upisi4bajta(Integer.parseInt(poruka.substring(offset, offset + 4), 16), poruka.substring(offset + 4, offset + 12), javljanje, obd);
			offset += 12;
			k++;
			}
		//očitavanje vrednosti od 8 bajtova
		int brOsam = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
		//System.out.println("br 8: " + brOsam);
		offset += 2;
		int l = 0;
		while(l < brOsam) {
			offset += 20;
			l++;
			}
		//završetak
		//thread.setOffset(offset);
		 
		obd.setKreirano(new Timestamp(sada.getTime()));
		obd.setIzmenjeno(new Timestamp(sada.getTime()));
		javljanje.setKreirano(new Timestamp(sada.getTime()));
		javljanje.setIzmenjeno(new Timestamp(sada.getTime()));
		if(obd.getNivoGoriva() == 0.0f && obd.getProsecnaPotrosnja() == 0.0f && obd.getRpm() == 0 && obd.getTemperatura() == 0 
				&& obd.getUkupnoGorivo() == 0.0f && obd.getUkupnoKm() == 0){
			obd = null;
			}

		return new JavljanjeObd(javljanje, obd);
		
	}
	
	private void upisi1bajt(int id, String vrednost, Javljanja javljanje, int eventId, Obd obd) {
		int rezultat = Integer.parseInt(vrednost, 16);
		//System.out.println("1 id: " + id);
		 switch (id) {
		case 2:
			break;
		case 3:
			break;
			//SOS
		case 4: if(rezultat == 1) {
			javljanje.setSistemAlarmi(sos);
			}
		    break;
		  //stanje kontakta 
		case 5: if(rezultat == 1) {    
			javljanje.setKontakt(true);
			if(eventId == 5) {
				javljanje.setSistemAlarmi(aktiviran);
				}
			}else {
				javljanje.setKontakt(false);
				if(eventId == 5) {
					javljanje.setSistemAlarmi(deaktiviran);
					}
				}
		    //System.out.println(" kontakt " + javljanje.isKontakt() + " event id " + eventId);
		    break;
		case 96: obd.setTemperatura(rezultat);//obd temperatura
		    break;
		case 98: obd.setNivoGoriva((float) rezultat);//obf nivo goriva
		    break;
		case 103: obd.setOpterecenje((float)rezultat);
		    break;
		case 115: if(!javljanje.isKontakt()) {
			obd.setTemperatura(0);//temperatura, odstupanje -40 stepeni
		}else {
			obd.setTemperatura(rezultat - 40);
		}
		    break;
		case 207: if(rezultat < 251) {
			obd.setNivoGoriva((float)(rezultat * 0.4));//nivo goriva u %
		}else {
			obd.setNivoGoriva(0.0f);
		}
		    break;
		case 251: if(rezultat == 1) {    //stanje kontakta - virtual ignition
			javljanje.setKontakt(true);
			if(eventId == 5) {
				javljanje.setSistemAlarmi(aktiviran);
			}
		}else {
			javljanje.setKontakt(false);
			if(eventId == 5) {
				javljanje.setSistemAlarmi(deaktiviran);
			}
		}
		    break;
		default:
			break;
		}
	 }
	 
	 private void upisi2bajta(int id, String vrednost, Obd obd) {
		 int rez = Integer.parseInt(vrednost, 16);
		 //System.out.println("2 id: " + id);
		 switch (id) {
		case 29:  obd.setAkumulator((float)(rez/1000));//nivo akumulatora
			break;
		case 94: Double rpmObd = rez * 0.25;//rpm sa obd-a
			     obd.setRpm(rpmObd.intValue());
		    break;
		case 100: obd.setProsecnaPotrosnja((float)rez);//prosečna potrošnja sa obd-a l/sat
			break;
		case 107: obd.setUkupnoVreme((float)rez);//sa obd-a
		    break;
		case 116: obd.setProsecnaPotrosnja((float)(rez * 0.05));//prosečna potrošnja u l/sat
		    break;
		case 197: Double rpm = rez * 0.125;//broj obrtaja
			      obd.setRpm(rpm.intValue());
			break;
		case 205: obd.setTripGorivo((float)rez);//količina gorivo u litrima u rezervoaru
		    break;
		default:
			break;
		}
	 }
	 
	 private void upisi4bajta(int id, String vrednost, Javljanja javljanje, Obd obd) {
		 long rez = Long.parseLong(vrednost, 16);
		 //System.out.println("4 id: " + id);
		 switch (id) {
		case 65:  javljanje.setVirtualOdo((float)rez / 1000);//vituelni odometar
			break;
		/*case 92:  obd.setUkupnogorivo((float)(rez / 1000));//hr total fuel used
		          System.out.println("92 " + vrednost + " " +  rez);
		    break;**/
		//case 93: obd.s - ovde treba greške
		case 114: obd.setUkupnoKm((int)((rez * 5)/ 1000));//hr total distance 5m/bit pa u km
	        break;
		case 203: obd.setUkupnoVreme((float)(rez * 0.05));// radni sati motora
		    break;
		case 208: if(obd.getUkupnoGorivo() == 0.0f) {//hr ukupna potrošeno goriva
			          obd.setUkupnoGorivo((float)(rez * 0.5));
			          //System.out.println("208 " + vrednost + " " + rez);
		          }
		    break;
		default:
			break;
		}
	 }
}
