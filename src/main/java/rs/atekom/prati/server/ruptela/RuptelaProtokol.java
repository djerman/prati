package rs.atekom.prati.server.ruptela;

import java.sql.Timestamp;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pratiBaza.tabele.Javljanja;
import pratiBaza.tabele.Obd;
import pratiBaza.tabele.Objekti;
import pratiBaza.tabele.SistemAlarmi;
import rs.atekom.prati.server.JavljanjeObd;
import rs.atekom.prati.server.Servis;

/**
 * RUPTELA протокол parser за GPS уређаје.
 * 
 * <p>Подржава два типа протокола:</p>
 * <ul>
 *   <li><b>Стандардни (Command 0x01)</b> - AVL records са IO елементима</li>
 *   <li><b>Проширени (Command 0x44)</b> - Extended records са 2-byte IO ID-jevima</li>
 * </ul>
 * 
 * <p><b>Формат пакета (Standard):</b></p>
 * <pre>
 * CRC-16 (2B) + Data Length (2B) + Codec (1B) + Command (1B) + 
 * Num Records (1B) + Records[] + Num Records (1B) + CRC-16 (2B)
 * </pre>
 * 
 * <p><b>Record структура:</b></p>
 * <pre>
 * Timestamp (4B) + Priority (1B) + Longitude (4B) + Latitude (4B) + 
 * Altitude (2B) + Angle (2B) + Satellites (1B) + Speed (2B) + 
 * Event ID (2B) + Total IO (1B) + 
 * 1B-IO (N1 + ID1 + Val1...) +
 * 2B-IO (N2 + ID2 + Val2...) +
 * 4B-IO (N4 + ID4 + Val4...) +
 * 8B-IO (N8 + ID8 + Val8...)
 * </pre>
 * 
 * @author Atekom
 * @version 2.0
 * @see <a href="https://wiki.ruptela.lt/index.php?title=Protocol">Ruptela Protocol Wiki</a>
 */
public class RuptelaProtokol {
	
	private static final Logger logger = LoggerFactory.getLogger(RuptelaProtokol.class);
	
	// ═══════════════════════════════════════════════════════════
	// КОНСТАНТЕ - IO Element IDs (према Ruptela документацији)
	// ═══════════════════════════════════════════════════════════
	
	// 1-byte IO elements
	private static final int IO_DIGITAL_INPUT_2 = 2;
	private static final int IO_DIGITAL_INPUT_3 = 3;
	private static final int IO_SOS_BUTTON = 4;
	private static final int IO_IGNITION = 5;
	private static final int IO_OBD_TEMPERATURE = 96;
	private static final int IO_OBD_FUEL_LEVEL = 98;
	private static final int IO_OBD_ENGINE_LOAD = 103;
	private static final int IO_TEMP_SENSOR = 115;
	private static final int IO_FUEL_LEVEL_PERCENT = 207;
	private static final int IO_VIRTUAL_IGNITION = 251;
	
	// 2-byte IO elements
	private static final int IO_BATTERY_VOLTAGE = 29;
	private static final int IO_OBD_RPM = 94;
	private static final int IO_OBD_AVG_FUEL_CONSUMPTION = 100;
	private static final int IO_OBD_TOTAL_ENGINE_HOURS = 107;
	private static final int IO_AVG_FUEL_CONSUMPTION = 116;
	private static final int IO_RPM = 197;
	private static final int IO_TRIP_FUEL = 205;
	
	// 4-byte IO elements
	private static final int IO_VIRTUAL_ODOMETER = 65;
	private static final int IO_HR_TOTAL_DISTANCE = 114;
	private static final int IO_ENGINE_HOURS = 203;
	private static final int IO_HR_TOTAL_FUEL_USED = 208;
	
	// Alarm sistem
	private SistemAlarmi redovno, sos, aktiviran, deaktiviran;
	
	public RuptelaProtokol() {
		try {
			if (Servis.sistemAlarmServis != null) {
				redovno = Servis.sistemAlarmServis.nadjiAlarmPoSifri("0");
				sos = Servis.sistemAlarmServis.nadjiAlarmPoSifri("6022");
				aktiviran = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1092");
				deaktiviran = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1091");
			} else {
				logger.warn("Sistem alarmi nisu dostupni (test environment)");
			}
		} catch (Exception e) {
			logger.error("Greška pri inicijalizaciji alarma", e);
		}
		
		logger.debug("RuptelaProtokol inicijalizovan");
	}
	
	// ═══════════════════════════════════════════════════════════
	// СТАНДАРДНИ ПРОТОКОЛ (Command 0x01)
	// ═══════════════════════════════════════════════════════════
	
	/**
	 * Парсира стандардни RUPTELA record (Command 0x01).
	 * 
	 * @param offset Почетни offset у hex string-у (треба бити 0)
	 * @param objekat Објекат коме припада уређај
	 * @param poruka Hex string са пакетом
	 * @return JavljanjeObd са парсираним подацима
	 */
	public JavljanjeObd vratiJavljanje(int offset, Objekti objekat, String poruka) {
		
		if (poruka == null || poruka.isEmpty()) {
			logger.error("Prazan paket prosleđen za parsiranje");
			return null;
		}
		
		if (objekat == null) {
			logger.error("Objekat je null");
			return null;
		}
		
	    // DODAJTE OVO - Minimalna dužina STANDARD paketa
	    // Timestamp(8) + TimestampExt(2) + Priority(2) + Lon(8) + Lat(8) + 
	    // Alt(4) + Angle(4) + Sat(2) + Speed(4) + HDOP(2) + EventID(2) + 
	    // IO counts (4 × 2 = 8) = 54 hex chars minimum
	    final int MIN_PACKET_LENGTH = 54;
	    
	    if (poruka.length() < MIN_PACKET_LENGTH) {
	        logger.error("Paket prekratak za parsiranje: dužina={}, očekivano minimum={}", 
	                     poruka.length(), MIN_PACKET_LENGTH);
	        return null;
	    }
	    
		logger.trace("Parsiranje STANDARD paketa za objekat {}: {}", 
		             objekat.getOznaka(), poruka.substring(0, Math.min(50, poruka.length())));
		
		try {
			Date sada = new Date();
			
			// Inicijalizacija javljanja
			Javljanja javljanje = initJavljanje(objekat, sada);
			
			// Inicijalizacija OBD podataka
			Obd obd = initObd(objekat, sada);
			
			// ═══════════════════════════════════════════════════════════
			// PARSIRANJE GPS PODATAKA
			// ═══════════════════════════════════════════════════════════
			
			// Timestamp (4 bytes) - Unix timestamp
			Timestamp datumVreme = parseTimestamp(poruka, offset);
			javljanje.setDatumVreme(datumVreme);
			obd.setDatumVreme(datumVreme);
			offset += 8;
			
			// Timestamp extension (1 byte) - milliseconds
			offset += 2;
			
			// Priority (1 byte)
			offset += 2;
			
			// Longitude (4 bytes) - signed int, scale 10^7
			double lon = parseLongitude(poruka, offset);
			javljanje.setLon(lon);
			offset += 8;
			
			// Latitude (4 bytes) - signed int, scale 10^7
			double lat = parseLatitude(poruka, offset);
			javljanje.setLat(lat);
			offset += 8;
			
			// Altitude (2 bytes) - meters, scale 10
			float visina = parseAltitude(poruka, offset);
			javljanje.setVisina(visina);
			offset += 4;
			
			// Angle (2 bytes) - degrees, scale 100
			float pravac = parseAngle(poruka, offset);
			javljanje.setPravac(pravac);
			offset += 4;
			
			// Satellites (1 byte)
			offset += 2;
			
			// Speed (2 bytes) - km/h
			int brzina = parseSpeed(poruka, offset);
			javljanje.setBrzina(brzina);
			offset += 4;
			
			// HDOP (1 byte)
			offset += 2;
			
			// Event ID (2 bytes) - STANDARD protocol
			int eventId = parseEventId(poruka, offset, false);
			offset += 2;
			
			logger.trace("GPS data: lat={}, lon={}, speed={}, eventId={}", 
			             lat, lon, brzina, eventId);
			
			// Postavi default alarm
			javljanje.setSistemAlarmi(redovno);
			javljanje.setEventData("0");
			javljanje.setIbutton("0");
			
			// ═══════════════════════════════════════════════════════════
			// PARSIRANJE IO ELEMENATA
			// ═══════════════════════════════════════════════════════════
			
			// 1-byte IO elements
			int brJedan = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
			offset += 2;
			logger.trace("1-byte IO count: {}", brJedan);
			
			for (int i = 0; i < brJedan; i++) {
				int ioId = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
				String ioValue = poruka.substring(offset + 2, offset + 4);
				upisi1bajt(ioId, ioValue, javljanje, eventId, obd);
				offset += 4;
			}
			
			// 2-byte IO elements
			int brDva = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
			offset += 2;
			logger.trace("2-byte IO count: {}", brDva);
			
			for (int j = 0; j < brDva; j++) {
				int ioId = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
				String ioValue = poruka.substring(offset + 2, offset + 6);
				upisi2bajta(ioId, ioValue, obd);
				offset += 6;
			}
			
			// 4-byte IO elements
			int brCetiri = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
			offset += 2;
			logger.trace("4-byte IO count: {}", brCetiri);
			
			for (int k = 0; k < brCetiri; k++) {
				int ioId = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
				String ioValue = poruka.substring(offset + 2, offset + 10);
				upisi4bajta(ioId, ioValue, javljanje, obd);
				offset += 10;
			}
			
			// 8-byte IO elements (preskačemo jer trenutno ne koristimo)
			int brOsam = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
			offset += 2;
			logger.trace("8-byte IO count: {} (preskačem)", brOsam);
			offset += brOsam * 18;
			
			// ═══════════════════════════════════════════════════════════
			// FINALIZACIJA
			// ═══════════════════════════════════════════════════════════
			
			// Ako nema OBD podataka, vrati null
			Obd finalObd = isObdEmpty(obd) ? null : obd;
			
			logger.debug("Uspešno parsiran STANDARD paket: objekat={}, brzina={}, kontakt={}, OBD={}", 
			             objekat.getOznaka(), brzina, javljanje.isKontakt(), finalObd != null);
			
			return new JavljanjeObd(javljanje, finalObd);
			
		} catch (Exception e) {
			logger.error("Greška pri parsiranju STANDARD paketa za objekat {}: {}", 
			             objekat.getOznaka(), poruka, e);
			return null;
		}
	}
	
	// ═══════════════════════════════════════════════════════════
	// ПРОШИРЕНИ ПРОТОКОЛ (Command 0x44)
	// ═══════════════════════════════════════════════════════════
	
	/**
	 * Парсира проширени RUPTELA record (Command 0x44).
	 * 
	 * @param offset Почетни offset у hex string-у (треба бити 0)
	 * @param objekat Објекат коме припада уређај
	 * @param poruka Hex string са пакетом
	 * @return JavljanjeObd са парсираним подацима
	 */
	public JavljanjeObd vratiExtended(int offset, Objekti objekat, String poruka) {
		
		if (poruka == null || poruka.isEmpty()) {
			logger.error("Prazan EXTENDED paket prosleđen za parsiranje");
			return null;
		}
		
		if (objekat == null) {
			logger.error("Objekat je null");
			return null;
		}
		
	    // EXTENDED ima dodatni RecordExt byte (2 hex chars)
	    // Minimum: 54 + 2 = 56 hex chars
	    final int MIN_EXTENDED_PACKET_LENGTH = 56;
	    
	    if (poruka.length() < MIN_EXTENDED_PACKET_LENGTH) {
	        logger.error("EXTENDED paket prekratak: dužina={}, očekivano minimum={}", 
	                     poruka.length(), MIN_EXTENDED_PACKET_LENGTH);
	        return null;
	    }
	    
		logger.trace("Parsiranje EXTENDED paketa za objekat {}: {}", 
		             objekat.getOznaka(), poruka.substring(0, Math.min(50, poruka.length())));
		
		try {
			Date sada = new Date();
			
			// Inicijalizacija
			Javljanja javljanje = initJavljanje(objekat, sada);
			Obd obd = initObd(objekat, sada);
			
			// ═══════════════════════════════════════════════════════════
			// PARSIRANJE GPS PODATAKA (isti format kao Standard + 2B Record Ext)
			// ═══════════════════════════════════════════════════════════
			
			// Timestamp (4 bytes)
			Timestamp datumVreme = parseTimestamp(poruka, offset);
			javljanje.setDatumVreme(datumVreme);
			obd.setDatumVreme(datumVreme);
			offset += 8;
			
			// Timestamp extension (1 byte)
			offset += 2;
			
			// Record extension for EXTENDED protocol (1 byte) DODATO!
			offset += 2;
			
			// Priority (1 byte)
			offset += 2;
			
			// Longitude (4 bytes)
			double lon = parseLongitude(poruka, offset);
			javljanje.setLon(lon);
			offset += 8;
			
			// Latitude (4 bytes)
			double lat = parseLatitude(poruka, offset);
			javljanje.setLat(lat);
			offset += 8;
			
			// Altitude (2 bytes)
			float visina = parseAltitude(poruka, offset);
			javljanje.setVisina(visina);
			offset += 4;
			
			// Angle (2 bytes)
			float pravac = parseAngle(poruka, offset);
			javljanje.setPravac(pravac);
			offset += 4;
			
			// Satellites (1 byte)
			offset += 2;
			
			// Speed (2 bytes)
			int brzina = parseSpeed(poruka, offset);
			javljanje.setBrzina(brzina);
			offset += 4;
			
			// HDOP (1 byte)
			offset += 2;
			
			// Event ID (2 bytes) - ✅ EXTENDED = 4 hex chars (2 bytes)
			int eventId = parseEventId(poruka, offset, true);
			offset += 4;
			
			logger.trace("EXTENDED GPS data: lat={}, lon={}, speed={}, eventId={}", 
			             lat, lon, brzina, eventId);
			
			// Postavi default alarm
			javljanje.setSistemAlarmi(redovno);
			javljanje.setEventData("0");
			javljanje.setIbutton("0");
			
			// ═══════════════════════════════════════════════════════════
			// PARSIRANJE IO ELEMENATA (EXTENDED: 2-byte IO IDs!)
			// ═══════════════════════════════════════════════════════════
			
			// 1-byte IO elements (IO ID је 2 bytes у Extended!)
			int brJedan = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
			offset += 2;
			logger.trace("EXTENDED 1-byte IO count: {}", brJedan);
			
			for (int i = 0; i < brJedan; i++) {
				int ioId = Integer.parseInt(poruka.substring(offset, offset + 4), 16); // 4 hex chars!
				String ioValue = poruka.substring(offset + 4, offset + 6);
				upisi1bajt(ioId, ioValue, javljanje, eventId, obd);
				offset += 6;
			}
			
			// 2-byte IO elements
			int brDva = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
			offset += 2;
			logger.trace("EXTENDED 2-byte IO count: {}", brDva);
			
			for (int j = 0; j < brDva; j++) {
				int ioId = Integer.parseInt(poruka.substring(offset, offset + 4), 16); // 4 hex chars!
				String ioValue = poruka.substring(offset + 4, offset + 8);
				upisi2bajta(ioId, ioValue, obd);
				offset += 8;
			}
			
			// 4-byte IO elements
			int brCetiri = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
			offset += 2;
			logger.trace("EXTENDED 4-byte IO count: {}", brCetiri);
			
			for (int k = 0; k < brCetiri; k++) {
				int ioId = Integer.parseInt(poruka.substring(offset, offset + 4), 16); // 4 hex chars!
				String ioValue = poruka.substring(offset + 4, offset + 12);
				upisi4bajta(ioId, ioValue, javljanje, obd);
				offset += 12;
			}
			
			// 8-byte IO elements
			int brOsam = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
			offset += 2;
			logger.trace("EXTENDED 8-byte IO count: {} (preskačem)", brOsam);
			offset += brOsam * 20;
			
			// ═══════════════════════════════════════════════════════════
			// FINALIZACIJA
			// ═══════════════════════════════════════════════════════════
			
			Obd finalObd = isObdEmpty(obd) ? null : obd;
			
			logger.debug("Uspešno parsiran EXTENDED paket: objekat={}, brzina={}, kontakt={}, OBD={}", 
			             objekat.getOznaka(), brzina, javljanje.isKontakt(), finalObd != null);
			
			return new JavljanjeObd(javljanje, finalObd);
			
		} catch (Exception e) {
			logger.error("Greška pri parsiranju EXTENDED paketa za objekat {}: {}", 
			             objekat.getOznaka(), poruka, e);
			return null;
		}
	}
	
	// ═══════════════════════════════════════════════════════════
	// HELPER МЕТОДЕ ЗА ПАРСИРАЊЕ
	// ═══════════════════════════════════════════════════════════
	
	private Javljanja initJavljanje(Objekti objekat, Date sada) {
		Javljanja j = new Javljanja();
		j.setObjekti(objekat);
		j.setValid(true);
		j.setVirtualOdo(0.0f);
		j.setKreirano(new Timestamp(sada.getTime()));
		j.setIzmenjeno(new Timestamp(sada.getTime()));
		return j;
	}
	
	private Obd initObd(Objekti objekat, Date sada) {
		Obd o = new Obd();
		o.setObjekti(objekat);
		o.setRpm(0);
		o.setTemperatura(0);
		o.setOpterecenje(0.0f);
		o.setGas(0.0f);
		o.setNivoGoriva(0.0f);
		o.setAkumulator(0.0f);
		o.setTripKm(0.0f);
		o.setTripGorivo(0.0f);
		o.setUkupnoVreme(0.0f);
		o.setUkupnoKm(0);
		o.setUkupnoGorivo(0.0f);
		o.setProsecnaPotrosnja(0.0f);
		o.setGreske("");
		o.setKreirano(new Timestamp(sada.getTime()));
		o.setIzmenjeno(new Timestamp(sada.getTime()));
		return o;
	}
	
	private Timestamp parseTimestamp(String poruka, int offset) {
		long unixTime = Long.parseLong(poruka.substring(offset, offset + 8), 16);
		return new Timestamp(unixTime * 1000);
	}
	
	private double parseLongitude(String poruka, int offset) {
	    String hexValue = poruka.substring(offset, offset + 8);
	    long value = Long.parseLong(hexValue, 16);
	    
	    // Two's complement 32-bit conversion
	    if ((value & 0x80000000L) != 0) {
	        value -= 0x100000000L;
	    }
	    
	    return value / 10000000.0;
	}

	private double parseLatitude(String poruka, int offset) {
	    String hexValue = poruka.substring(offset, offset + 8);
	    long value = Long.parseLong(hexValue, 16);
	    
	    // Two's complement 32-bit conversion
	    if ((value & 0x80000000L) != 0) {
	        value -= 0x100000000L;
	    }
	    
	    return value / 10000000.0;
	}
	
	private float parseAltitude(String poruka, int offset) {
		int rawAlt = Integer.parseInt(poruka.substring(offset, offset + 4), 16);
		return (float) rawAlt / 10.0f;
	}
	
	private float parseAngle(String poruka, int offset) {
		int rawAngle = Integer.parseInt(poruka.substring(offset, offset + 4), 16);
		return (float) rawAngle / 100.0f;
	}
	
	private int parseSpeed(String poruka, int offset) {
		return Integer.parseInt(poruka.substring(offset, offset + 4), 16);
	}
	
	private int parseEventId(String poruka, int offset, boolean extended) {
		if (extended) {
			// Extended protocol: Event ID = 2 bytes (4 hex chars)
			return Integer.parseInt(poruka.substring(offset, offset + 4), 16);
		} else {
			// Standard protocol: Event ID = 1 byte (2 hex chars)
			return Integer.parseInt(poruka.substring(offset, offset + 2), 16);
		}
	}
	
	private boolean isObdEmpty(Obd obd) {
		return obd.getNivoGoriva() == 0.0f 
				&& obd.getProsecnaPotrosnja() == 0.0f 
				&& obd.getRpm() == 0 
				&& obd.getTemperatura() == 0
				&& obd.getUkupnoGorivo() == 0.0f 
				&& obd.getUkupnoKm() == 0;
	}
	
	// ═══════════════════════════════════════════════════════════
	// IO ELEMENT ПАРСИРАЊЕ (1-byte values)
	// ═══════════════════════════════════════════════════════════
	
	private void upisi1bajt(int id, String vrednost, Javljanja javljanje, int eventId, Obd obd) {
		int rezultat = Integer.parseInt(vrednost, 16);
		
		switch (id) {
		case IO_DIGITAL_INPUT_2:
			logger.trace("IO 2: Digital Input 2 = {}", rezultat);
			break;
			
		case IO_DIGITAL_INPUT_3:
			logger.trace("IO 3: Digital Input 3 = {}", rezultat);
			break;
			
		case IO_SOS_BUTTON: // SOS dugme
			if (rezultat == 1 && sos != null) {
				javljanje.setSistemAlarmi(sos);
				logger.debug("SOS alarm aktiviran!");
			}
			break;
			
		case IO_IGNITION: // Kontakt (physical ignition)
			boolean kontakt = (rezultat == 1);
			javljanje.setKontakt(kontakt);
			
			if (kontakt && eventId == 5 && aktiviran != null) {
				javljanje.setSistemAlarmi(aktiviran);
				logger.debug("Ignition ON (eventId=5)");
			} else if (!kontakt && eventId == 5 && deaktiviran != null) {
				javljanje.setSistemAlarmi(deaktiviran);
				logger.debug("Ignition OFF (eventId=5)");
			}
			break;
			
		case IO_OBD_TEMPERATURE: // OBD temperatura
			obd.setTemperatura(rezultat);
			logger.trace("IO 96: OBD temperatura = {}°C", rezultat);
			break;
			
		case IO_OBD_FUEL_LEVEL: // OBD nivo goriva
			obd.setNivoGoriva((float) rezultat);
			logger.trace("IO 98: OBD nivo goriva = {}%", rezultat);
			break;
			
		case IO_OBD_ENGINE_LOAD: // OBD opterećenje motora
			obd.setOpterecenje((float) rezultat);
			logger.trace("IO 103: OBD opterećenje = {}%", rezultat);
			break;
			
		case IO_TEMP_SENSOR: // Eksterni temp senzor (sa offset-om -40°C)
			if (!javljanje.isKontakt()) {
				obd.setTemperatura(0);
			} else {
				obd.setTemperatura(rezultat - 40);
			}
			logger.trace("IO 115: Temperatura senzor = {}°C (raw={})", obd.getTemperatura(), rezultat);
			break;
			
		case IO_FUEL_LEVEL_PERCENT: // Nivo goriva u % (0.4% per bit)
			if (rezultat < 251) {
				obd.setNivoGoriva((float) (rezultat * 0.4));
				logger.trace("IO 207: Nivo goriva = {}%", obd.getNivoGoriva());
			} else {
				obd.setNivoGoriva(0.0f);
				logger.trace("IO 207: Nivo goriva = invalid ({})", rezultat);
			}
			break;
			
		case IO_VIRTUAL_IGNITION: // Virtuelni kontakt
			boolean virtualKontakt = (rezultat == 1);
			javljanje.setKontakt(virtualKontakt);
			
			if (virtualKontakt && eventId == 5 && aktiviran != null) {
				javljanje.setSistemAlarmi(aktiviran);
				logger.debug("Virtual Ignition ON (eventId=5)");
			} else if (!virtualKontakt && eventId == 5 && deaktiviran != null) {
				javljanje.setSistemAlarmi(deaktiviran);
				logger.debug("Virtual Ignition OFF (eventId=5)");
			}
			break;
			
		default:
			logger.trace("IO {}: Nepoznat 1-byte element = {}", id, rezultat);
			break;
		}
	}
	
	// ═══════════════════════════════════════════════════════════
	// IO ELEMENT ПАРСИРАЊЕ (2-byte values)
	// ═══════════════════════════════════════════════════════════
	
	private void upisi2bajta(int id, String vrednost, Obd obd) {
		int rez = Integer.parseInt(vrednost, 16);
		
		switch (id) {
		case IO_BATTERY_VOLTAGE: // Napon akumulatora (mV)
			obd.setAkumulator((float) (rez / 1000.0));
			logger.trace("IO 29: Akumulator = {}V", obd.getAkumulator());
			break;
			
		case IO_OBD_RPM: // OBD RPM (0.25 RPM per bit)
			int rpmObd = (int) (rez * 0.25);
			obd.setRpm(rpmObd);
			logger.trace("IO 94: OBD RPM = {}", rpmObd);
			break;
			
		case IO_OBD_AVG_FUEL_CONSUMPTION: // Prosečna potrošnja (L/h)
			obd.setProsecnaPotrosnja((float) rez);
			logger.trace("IO 100: Prosečna potrošnja = {} L/h", rez);
			break;
			
		case IO_OBD_TOTAL_ENGINE_HOURS: // Ukupno radnih sati
			obd.setUkupnoVreme((float) rez);
			logger.trace("IO 107: Ukupno vreme = {} h", rez);
			break;
			
		case IO_AVG_FUEL_CONSUMPTION: // Prosečna potrošnja (0.05 L/h per bit)
			obd.setProsecnaPotrosnja((float) (rez * 0.05));
			logger.trace("IO 116: Prosečna potrošnja = {} L/h", obd.getProsecnaPotrosnja());
			break;
			
		case IO_RPM: // Broj obrtaja (0.125 RPM per bit)
			int rpm = (int) (rez * 0.125);
			obd.setRpm(rpm);
			break;
			
		case IO_TRIP_FUEL: // Trip gorivo (L)
			obd.setTripGorivo((float) rez);
			logger.trace("IO 205: Trip gorivo = {} L", rez);
			break;
			
		default:
			logger.trace("IO {}: Nepoznat 2-byte element = {}", id, rez);
			break;
		}
	}
	
	// ═══════════════════════════════════════════════════════════
	// IO ELEMENT ПАРСИРАЊЕ (4-byte values)
	// ═══════════════════════════════════════════════════════════
	
	private void upisi4bajta(int id, String vrednost, Javljanja javljanje, Obd obd) {
		long rez = Long.parseLong(vrednost, 16);
		
		switch (id) {
		case IO_VIRTUAL_ODOMETER: // Virtuelni odometar (m → km)
			float odo = (float) (rez / 1000.0);
			javljanje.setVirtualOdo(odo);
			logger.trace("IO 65: Virtuelni odometar = {} km", odo);
			break;
			
		case IO_HR_TOTAL_DISTANCE: // HR ukupna distanca (5m/bit → km)
			int totalKm = (int) ((rez * 5) / 1000);
			obd.setUkupnoKm(totalKm);
			logger.trace("IO 114: Ukupna distanca = {} km", totalKm);
			break;
			
		case IO_ENGINE_HOURS: // Radni sati motora (0.05h per bit)
			float engineHours = (float) (rez * 0.05);
			obd.setUkupnoVreme(engineHours);
			logger.trace("IO 203: Radni sati motora = {} h", engineHours);
			break;
			
		case IO_HR_TOTAL_FUEL_USED: // HR ukupno gorivo (0.5L per bit)
			if (obd.getUkupnoGorivo() == 0.0f) { // Samo ako već nije postavljeno
				float totalFuel = (float) (rez * 0.5);
				obd.setUkupnoGorivo(totalFuel);
				logger.trace("IO 208: Ukupno gorivo = {} L", totalFuel);
			}
			break;
			
		default:
			logger.trace("IO {}: Nepoznat 4-byte element = {}", id, rez);
			break;
		}
	}
}