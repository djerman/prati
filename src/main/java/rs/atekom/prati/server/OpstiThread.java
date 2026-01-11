package rs.atekom.prati.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pratiBaza.tabele.AlarmiKorisnik;
import pratiBaza.tabele.Javljanja;
import pratiBaza.tabele.JavljanjaMirovanja;
import pratiBaza.tabele.JavljanjaPoslednja;
import pratiBaza.tabele.Obd;
import pratiBaza.tabele.ObjekatZone;
import pratiBaza.tabele.Objekti;
import pratiBaza.tabele.Uredjaji;
import pratiBaza.tabele.Zone;

public abstract class OpstiThread implements Runnable {

	// ДОДАЈТЕ LOGGER
	private static final Logger logger = LoggerFactory.getLogger(OpstiThread.class);
	// tolerancija: 1 минут
	private static final long SKEW_MS = 60_000L;
	
	// SOCKET TIMEOUT КОНСТАНТЕ
	protected static final int SOCKET_READ_TIMEOUT_MS = 300000; // 30 секунди
	protected static final int SOCKET_WRITE_TIMEOUT_MS = 5000;  // 5 секунди
	
	// ЗАМЕНА: instance variable уместо static
	protected final int connectionTimeoutMs;

	protected Socket socket = null;
	protected LinkedBlockingQueue<Socket> socketQueue;
	protected InputStream input;
	protected OutputStream out;
	protected volatile boolean isStopped = false; // volatile за visibility
	protected boolean prekoracenje = false;
	protected byte[] data;
	// ИСПРАВЉЕНО: ACK одговор према Ruptela протоколу v.1.80
	// Структура: Packet length (2B) + Command (1B = 0x00) + ACK (1B = 0x01) + CRC-16 (2B)
	// Packet length = 0x0002 (2 bytes без CRC-16)
	// Command = 0x00 (ACK/NACK одговор)
	// ACK = 0x01 (позитиван ACK)
	// CRC-16 = прерачунат динамички при иницијализацији
	protected static byte[] odg = initializeAckResponse();
	protected int offset;
	protected OpstiServer server;
	protected ArrayList<ObjekatZone> objekatZone;
	protected ArrayList<AlarmiKorisnik> alarmiKorisnici;
	protected String testDate;
	protected DateFormat formatter;
	protected Date date;
	protected JavljanjeObd javljanjeObd;
	protected Obd obdStop;
	protected Javljanja javljanjeTrenutno, javljanjeStop;
	protected Objekti objekat;
	protected Uredjaji uredjaj;
	protected String ulaz;
	protected boolean zaustavljeno;
	protected boolean gorivo;
	protected String kodUredjaja;
	protected int brojIspodNivoa;
	protected Date pocetak;
	protected String test;
	protected JavljanjaPoslednja poslednje;
	
	// НОВИ: Tracking connection ID-а
	private String clientId;
	
	public OpstiThread(LinkedBlockingQueue<Socket> queue, OpstiServer srv) {
		// Default (био 10) 5 минута
		// Druga opcija je da se smanji na 60-90s pa da se broje uzastopni tajmauti
		// ako pređe recimo 6-8 da onda izlazi
		this(queue, srv, SOCKET_READ_TIMEOUT_MS);
	}
	
	// НОВИ: Constructor са customizable timeout-ом
	public OpstiThread(LinkedBlockingQueue<Socket> queue, OpstiServer srv, int timeoutMs) {
		
		socketQueue = queue;
		server = srv;
		connectionTimeoutMs = timeoutMs;
		
		data = new byte[1024];
		testDate = "01/07/2019 00:00:00";
		formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
		
		try {
			date = formatter.parse(testDate);
		} catch (ParseException e) {
			logger.error("Greška parsiranja datuma '{}'", testDate, e);
		}
		
		pocetak = new Date();
		zaustavljeno = false;
		gorivo = false;
		brojIspodNivoa = 0;
		test = "test 0";
	}
	
	/**
	 * NOVI: Postavi socket sa timeout-ima i optimizovanim opcijama
	 */
	protected void setupSocket(Socket socket, String clientId) throws SocketException {
		this.socket = socket;
		this.clientId = clientId;
		
		// Postavi timeout-e za čitanje/pisanje
		socket.setSoTimeout(SOCKET_READ_TIMEOUT_MS);
		
		// TCP_NODELAY - искључује Nagle алгоритам за смањење кашњења
		socket.setTcpNoDelay(true);
		
		// SO_KEEPALIVE - детектује прекинуте везе
		socket.setKeepAlive(true);
		
		logger.debug("Socket [{}] konfigurisan sa timeout-ом {}ms, TCP_NODELAY=true, SO_KEEPALIVE=true", 
		            clientId, SOCKET_READ_TIMEOUT_MS);
	}

	@Override
	public abstract void run(); // Implementiraju child klase
	
	/**
	 * REFAKTORISANO: Pronalazi i postavlja uređaj sa error handling-om
	 */
	public void pronadjiPostavi(String kodUredjaja) {
		poslednje = null;
		
		// Validacija input parametra
		if (kodUredjaja == null || kodUredjaja.isEmpty()) {
			logger.warn("Prazan kod uređaja prosleđen za obradu");
			return;
		}
		
		try {
			// Pronalaženje uređaja
			uredjaj = Servis.uredjajServis.nadjiUredjajPoKodu(kodUredjaja);
			
			if (uredjaj == null) {
				logger.warn("Uređaj sa kodom '{}' nije pronađen u bazi", kodUredjaja);
				return;
			}
			
			// Pronalaženje objekta
			objekat = uredjaj.getObjekti();
			
			if (objekat == null) {
				logger.warn("Uređaj '{}' nema pridružen objekat", kodUredjaja);
				return;
			}
			
			// Učitavanje zona i alarma
			objekatZone = Servis.zonaObjekatServis.nadjiZoneObjektePoObjektu(objekat);
			alarmiKorisnici = new ArrayList<>();
			alarmiKorisnici.addAll(Servis.alarmKorisnikServis.nadjiSveAlarmeKorisnikePoObjektu(objekat));
			
			// Učitavanje poslednjeg javljanja
			poslednje = Servis.javljanjePoslednjeServis.nadjiJavljanjaPoslednjaPoObjektu(objekat);
			
			// Provera starog stajanja
			boolean vremeStarijeOdStajanja = false;
			if (poslednje != null) {
				long vreme = pocetak.getTime() - poslednje.getDatumVreme().getTime();
				if (objekat.getVremeStajanja() > 0 && (vreme / 1000 > objekat.getVremeStajanja())) {
					vremeStarijeOdStajanja = true;
				}
				
				// Postavljanje stop javljanja ako je relevantno
				if (poslednje.getBrzina() < 6 
						&& !poslednje.getSistemAlarmi().getSifra().equals("1095") 
						&& !vremeStarijeOdStajanja) {
					javljanjeStop = new Javljanja();
					javljanjeStop.setDatumVreme(poslednje.getDatumVreme());
					obdStop = Servis.obdServis.nadjiObdPoslednji(objekat, null);
				} else {
					javljanjeStop = null;
					obdStop = null;
				}
			}
			
			logger.debug("Uređaj '{}' uspešno učitan: objekat={}, zona={}, alarmi={}", 
			             kodUredjaja, 
			             objekat != null ? objekat.getOznaka() : "null",
			             objekatZone != null ? objekatZone.size() : 0,
			             alarmiKorisnici != null ? alarmiKorisnici.size() : 0);
			
		} catch (Exception e) {
			logger.error("Greška pri pronalaženju/postavljanju uređaja '{}'", kodUredjaja, e);
			stop(); // Graceful shutdown na kritičnu grešku
		}
	}
	
	/**
	 * REFAKTORISANO: Obrada javljanja sa boljim error handling-om
	 */
	public void obradaJavljanja(Javljanja javljanjeTrenutno, Obd obdTrenutni) {
		if (javljanjeTrenutno == null) {
			logger.warn("Primljeno null javljanje za obradu, preskačem. Ulaz: {}", ulaz);
			return;
		}
		
		try {
			test = "ulaz";
			JavljanjaPoslednja poslednje = Servis.javljanjePoslednjeServis.nadjiJavljanjaPoslednjaPoObjektu(objekat);
			
			boolean mladje = true;
			if (poslednje != null) {
				mladje = javljanjeTrenutno.getDatumVreme().after(poslednje.getDatumVreme());
			}
			
			// Validacija brzine i datuma
			if (javljanjeTrenutno.getBrzina() >= 250) {
				logger.warn("Odbačeno javljanje sa nerealno visokom brzinom: {} km/h", javljanjeTrenutno.getBrzina());
				return;
			}
			
			if (javljanjeTrenutno.getDatumVreme().before(date)) {
				logger.warn("Odbačeno javljanje sa datumom pre minimuma: {}", javljanjeTrenutno.getDatumVreme());
				return;
			}
			
			/*if (javljanjeTrenutno.getDatumVreme().after(new Date())) {
				logger.warn("Odbačeno javljanje iz budućnosti: {}", javljanjeTrenutno.getDatumVreme());
				return;
			}*/
			Date ts = javljanjeTrenutno.getDatumVreme();
			long now = System.currentTimeMillis();
			if (ts.getTime() - now > SKEW_MS) {
			    // строго из будућности (више од 1 минута)
			    logger.warn("Odbačeno javljanje iz budućnosti (>1min): {}", ts);
			    return;
			}
			
			// Obračun kilometraže
			test = "obračun";
			obracunKilometraze(javljanjeTrenutno, poslednje, mladje);
			
			// Stop handling
			test = "za stop";
			handleStopCondition(javljanjeTrenutno, obdTrenutni);
			
			// Alarm: Stajanje
			test = "stajanje";
			handleAlarmStajanje(javljanjeTrenutno, mladje);
			
			// Alarm: Prekoračenje brzine
			test = "prekoračenje";
			handleAlarmPrekoracenjeBrzine(javljanjeTrenutno, mladje);
			
			// Alarm: Gorivo
			test = "gorivo";
			handleAlarmGorivo(javljanjeTrenutno, obdTrenutni, mladje);
			
			// Alarm: Zone
			test = "zona";
			handleAlarmZone(javljanjeTrenutno, mladje);
			
			// Finalna obrada alarma
			test = "izvrsavanje";
			server.izvrsavanje.obradaAlarma(javljanjeTrenutno, alarmiKorisnici);
			
		} catch (Exception e) {
			logger.error("Greška pri obradi javljanja (faza: {})", test, e);
		}
	}
	
	/**
	 * IZDVOJENO: Obračun kilometraže
	 */
	private void obracunKilometraze(Javljanja trenutno, JavljanjaPoslednja poslednje, boolean mladje) {
		if (poslednje != null) {
			if (mladje) {
				trenutno.setVirtualOdo(poslednje.getVirtualOdo() + 
				                       (float) Servis.obracun.rastojanje(trenutno, poslednje));
			} else {
				trenutno.setVirtualOdo(poslednje.getVirtualOdo());
			}
		} else {
			trenutno.setVirtualOdo(0.0f);
		}
	}
	
	/**
	 * IZDVOJENO: Stop condition handling
	 */
	private void handleStopCondition(Javljanja trenutno, Obd obdTrenutni) {
		if (trenutno.getBrzina() > 5) {
			javljanjeStop = null;
			obdStop = null;
			zaustavljeno = false;
			gorivo = false;
			brojIspodNivoa = 0;
		} else {
			if (javljanjeStop == null) {
				javljanjeStop = trenutno;
				obdStop = obdTrenutni;
			}
		}
	}
	
	/**
	 * IZDVOJENO: Alarm stajanje
	 */
	private void handleAlarmStajanje(Javljanja trenutno, boolean mladje) {
		if (javljanjeStop != null && !zaustavljeno && mladje) {
			long vreme = trenutno.getDatumVreme().getTime() - javljanjeStop.getDatumVreme().getTime();
			
			if (objekat.getVremeStajanja() > 0 && (vreme > (objekat.getVremeStajanja() * 60 * 1000))) {
				if (!trenutno.getSistemAlarmi().getSifra().equals("0")) {
					server.izvrsavanje.obradaAlarma(trenutno, alarmiKorisnici);
				}
				server.postaviAlarmStajanje(trenutno);
				zaustavljeno = true;
				
				logger.debug("Alarm STAJANJE aktiviran za objekat: {} (trajanje: {}ms)", 
				             objekat.getOznaka(), vreme);
			}
		}
	}
	
	/**
	 * IZDVOJENO: Alarm prekoračenje brzine
	 */
	private void handleAlarmPrekoracenjeBrzine(Javljanja trenutno, boolean mladje) {
		if (objekat.getPrekoracenjeBrzine() != 0 && mladje) {
			if (trenutno.getBrzina() > objekat.getPrekoracenjeBrzine() && !prekoracenje) {
				prekoracenje = true;
				
				if (!trenutno.getSistemAlarmi().getSifra().equals("0")) {
					server.izvrsavanje.obradaAlarma(trenutno, alarmiKorisnici);
				}
				server.postaviAlarmPrekoracenjeBrzine(trenutno);
				
				// Dodaj brzinu u event data
				if (trenutno.getEventData().equals("0")) {
					trenutno.setEventData(trenutno.getBrzina() + "км/ч");
				} else {
					trenutno.setEventData(trenutno.getBrzina() + "км/ч, " + trenutno.getEventData());
				}
				
				logger.info("Alarm PREKORAČENJE BRZINE: objekat={}, brzina={}km/h, limit={}km/h", 
				            objekat.getOznaka(), trenutno.getBrzina(), objekat.getPrekoracenjeBrzine());
			} else {
				prekoracenje = false;
			}
		}
	}
	
	/**
	 * IZDVOJENO: Alarm gorivo (pojednostavljena logika)
	 */
	private void handleAlarmGorivo(Javljanja trenutno, Obd obdTrenutni, boolean mladje) {
		if (obdTrenutni == null || !mladje || trenutno.getBrzina() >= 6) {
			gorivo = false;
			return;
		}
		
		try {
			JavljanjaMirovanja poslednjeSaBrzinom = 
				Servis.javljanjeMirovanjeServis.nadjiJavljanjaMirovanjaPoObjektu(objekat);
			
			if (!gorivo && poslednjeSaBrzinom != null) {
				ArrayList<Obd> poslednjiObdUMirovanju = Servis.obdServis.nadjiObdPoslednjaStajanja(
					objekat, new Timestamp(poslednjeSaBrzinom.getDatumVreme().getTime())
				);
				
				if (poslednjiObdUMirovanju != null && poslednjiObdUMirovanju.size() > 0) {
					float razlika = poslednjiObdUMirovanju.get(0).getNivoGoriva() - obdTrenutni.getNivoGoriva();
					
					if (razlika > 3) {
						if (!trenutno.getSistemAlarmi().getSifra().equals("0")) {
							server.izvrsavanje.obradaAlarma(trenutno, alarmiKorisnici);
						}
						server.postaviAlarmIstakanje(trenutno);
						gorivo = true;
						
						logger.info("Alarm ISTAKANJE GORIVA: objekat={}, razlika={}%", 
						            objekat.getOznaka(), razlika);
					}
				}
			}
			
			// Snimanje OBD podataka
			Servis.obdServis.unesiObd(obdTrenutni);
			
		} catch (Exception e) {
			logger.error("Greška pri obradi alarma goriva", e);
		}
	}
	
	/**
	 * IZDVOJENO: Alarm zone (ulazak/izlazak)
	 */
	private void handleAlarmZone(Javljanja trenutno, boolean mladje) {
		if (!mladje || objekatZone == null || objekatZone.isEmpty()) {
			return;
		}
		
		Zone zonaPoslednja = (poslednje != null) ? poslednje.getZona() : null;
		
		if (zonaPoslednja == null) {
			// Provera ulaska u zonu
			handleUlazakUZonu(trenutno);
		} else {
			// Provera izlaska iz zone
			handleIzlazakIzZone(trenutno, zonaPoslednja);
		}
	}
	
	/**
	 * IZDVOJENO: Ulazak u zonu
	 */
	private void handleUlazakUZonu(Javljanja trenutno) {
		for (ObjekatZone objekatZona : objekatZone) {
			if (!objekatZona.isAktivan() || !objekatZona.isIzlaz()) {
				continue;
			}
			
			double rastojanje = Servis.obracun.rastojanjeKoordinate(
				trenutno, 
				objekatZona.getZone().getLat(), 
				objekatZona.getZone().getLon()
			);
			
			if (rastojanje <= objekatZona.getZone().getPrecnik()) {
				trenutno.setZona(objekatZona.getZone());
				
				if (!trenutno.getSistemAlarmi().getSifra().equals("0")) {
					server.izvrsavanje.obradaAlarma(trenutno, alarmiKorisnici);
				}
				server.postaviAlarmUlazakUZonu(trenutno);
				trenutno.setEventData(objekatZona.getZone().getNaziv());
				
				logger.info("Alarm ULAZAK U ZONU: objekat={}, zona={}", 
				            objekat.getOznaka(), objekatZona.getZone().getNaziv());
				break;
			}
		}
	}
	
	/**
	 * IZDVOJENO: Izlazak iz zone
	 */
	private void handleIzlazakIzZone(Javljanja trenutno, Zone zonaPoslednja) {
		trenutno.setZona(zonaPoslednja);
		
		ObjekatZone objZona = Servis.zonaObjekatServis.nadjiObjekatZonuPoZoniObjektu(objekat, zonaPoslednja);
		
		if (objZona != null && objZona.isAktivan() && objZona.isIzlaz()) {
			double rastojanje = Servis.obracun.rastojanjeKoordinate(
				trenutno, 
				zonaPoslednja.getLat(), 
				zonaPoslednja.getLon()
			);
			
			if (rastojanje > zonaPoslednja.getPrecnik()) {
				if (!trenutno.getSistemAlarmi().getSifra().equals("0")) {
					server.izvrsavanje.obradaAlarma(trenutno, alarmiKorisnici);
				}
				server.postaviAlarmIzlazakIzZone(trenutno);
				trenutno.setEventData(zonaPoslednja.getNaziv());
				trenutno.setZona(null);
				
				logger.info("Alarm IZLAZAK IZ ZONE: objekat={}, zona={}", 
				            objekat.getOznaka(), zonaPoslednja.getNaziv());
			}
		}
	}
	
	/**
	 * POBOLJŠANO: Graceful stop sa notifikacijom servera
	 */
	public synchronized void stop() {
		if (isStopped) {
			return; // Već zaustavljen
		}
		
		isStopped = true;
		
		try {
			// Zatvaranje stream-ova
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					logger.warn("Greška zatvaranja input stream-a: {}", e.getMessage());
				}
			}
			
			if (out != null) {
				try {
					out.flush();
					out.close();
				} catch (IOException e) {
					logger.warn("Greška zatvaranja output stream-a: {}", e.getMessage());
				}
			}
			
			// Zatvaranje socket-a
			if (socket != null && !socket.isClosed()) {
				socket.close();
				logger.debug("Socket [{}] zatvoren", clientId);
			}
			
			// OBAVESTI SERVER DA UKLONI SOCKET
			if (clientId != null && server != null) {
				//server.removeClientSocket(clientId);
				//због разлика у clientId
				server.removeClientSocket(socket);
			}
			
		} catch (IOException e) {
			logger.error("Greška pri zaustavljanju thread-a [{}]", clientId, e);
		}
	}
	
	/**
	 * NOVO: Getter za client ID
	 */
	public String getClientId() {
		return clientId;
	}
	
	/**
	 * NOVO: CRC-16 Kermit алгоритам за Ruptela протокол
	 * Према Ruptela protocol v.1.80 спецификацији
	 * 
	 * @param data Подаци за које се рачуна CRC
	 * @return CRC-16 вредност
	 */
	/**
	 * CRC-16 Kermit калкулација
	 * Користи полином 0x8408 (CRC-16 Kermit reversed polynomial)
	 * Усклађено са референци кодом из Ruptela програма
	 */
	protected static int calculateCrc16Kermit(byte[] data) {
		int crc = 0x0000;
		int polynomial = 0x8408; // CRC-16 Kermit полином (reversed)
		
		for (byte b : data) {
			crc ^= (b & 0xFF);
			for (int i = 0; i < 8; i++) {
				boolean carry = (crc & 0x0001) != 0;
				crc >>>= 1;
				if (carry) {
					crc ^= polynomial;
				}
			}
		}
		
		return crc & 0xFFFF;
	}
	
	/**
	 * NOVO: Иницијализација ACK одговора са правилним CRC-16
	 * Позива се једном при иницијализацији класе
	 */
	private static byte[] initializeAckResponse() {
		// ACK структура: Packet length (2B) + Command (1B) + ACK (1B) + CRC-16 (2B)
		// Packet length = 0x0002 (2 bytes без CRC-16)
		// Command = 0x00
		// ACK = 0x01 (позитиван)
		byte[] ackData = {(byte)0x00, (byte)0x02, (byte)0x00, (byte)0x01};
		int crc = calculateCrc16Kermit(ackData);
		
		// CRC се шаље као Big Endian (виши бајт први)
		byte[] ack = new byte[6];
		ack[0] = ackData[0]; // Packet length high byte
		ack[1] = ackData[1]; // Packet length low byte
		ack[2] = ackData[2]; // Command
		ack[3] = ackData[3]; // ACK
		ack[4] = (byte)((crc >>> 8) & 0xFF); // CRC high byte
		ack[5] = (byte)(crc & 0xFF);         // CRC low byte
		
		return ack;
	}
	
	/**
	 * NOVO: Иницијализација NACK (Negative ACK) одговора са правилним CRC-16
	 * Позива се једном при иницијализацији класе
	 */
	private static byte[] initializeNackResponse() {
		// NACK структура: Packet length (2B) + Command (1B) + ACK (1B) + CRC-16 (2B)
		// Packet length = 0x0002 (2 bytes без CRC-16)
		// Command = 0x00
		// ACK = 0x00 (негативан NACK)
		byte[] nackData = {(byte)0x00, (byte)0x02, (byte)0x00, (byte)0x00};
		int crc = calculateCrc16Kermit(nackData);
		
		// CRC се шаље као Big Endian (виши бајт први)
		byte[] nack = new byte[6];
		nack[0] = nackData[0]; // Packet length high byte
		nack[1] = nackData[1]; // Packet length low byte
		nack[2] = nackData[2]; // Command
		nack[3] = nackData[3]; // ACK (0x00 = негативан)
		nack[4] = (byte)((crc >>> 8) & 0xFF); // CRC high byte
		nack[5] = (byte)(crc & 0xFF);         // CRC low byte
		
		return nack;
	}
	
	/**
	 * NOVO: NACK одговор за Ruptela протокол
	 * Структура: Packet length (2B) + Command (1B = 0x00) + ACK (1B = 0x00) + CRC-16 (2B)
	 */
	protected static final byte[] nack = initializeNackResponse();
	
	/**
	 * NOVO: Provera da li je thread zaustavljen
	 */
	public synchronized boolean isStopped() {
		return this.isStopped;
	}
}