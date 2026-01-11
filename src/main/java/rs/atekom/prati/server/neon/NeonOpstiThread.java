package rs.atekom.prati.server.neon;

import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rs.atekom.prati.server.OpstiServer;
import rs.atekom.prati.server.OpstiThread;

/**
 * NEON protocol handler thread.
 * 
 * <p>Обрађује TCP пакете од NEON GPS уређаја у формату:
 * {@code <oris,command,imei,data...>}</p>
 * 
 * <p><b>ВАЖНО:</b> Обрада података и снимање у базу остају НЕПРОМЕЊЕНИ!</p>
 * 
 * @author Atekom
 * @version 2.0
 */
public class NeonOpstiThread extends OpstiThread {
	
	// ДОДАТ: Logger
	private static final Logger logger = LoggerFactory.getLogger(NeonOpstiThread.class);
	
	// ДОДАТ: Константе за лакше разумевање кода
	private static final String PROTOCOL_PREFIX_ORIS = "<oris";
	private static final String PROTOCOL_PREFIX_HASH_ORIS = "#<oris";
	private static final String MESSAGE_DELIMITER = ">";
	private static final int MAX_FAILED_ATTEMPTS = 3;
	
	private String[] niz, da;
	private int brojPromasaja;
	
	public NeonOpstiThread(LinkedBlockingQueue<Socket> queue, OpstiServer srv) {
		super(queue, srv);
		brojPromasaja = 0;
	}
	
	@Override
	public void run() {
		Socket socket = null;
		String clientId = null;
		
		try {
			// Uzimanje socket-a iz reda
			socket = socketQueue.take();
			clientId = "NEON-" + socket.getRemoteSocketAddress();
			
			// POZIV setupSocket() iz parent klase (thread-safe)
			setupSocket(socket, clientId);
			
			input = socket.getInputStream();
			
			logger.info("NEON klijent [{}] započeo obradu", clientId);
			
			int br = 0;
			int totalPackets = 0;
			
			// ═══════════════════════════════════════════════════════════
			// ГЛАВНА ПЕТЉА - ОБРАДА ПОДАТАКА (НЕПРОМЕЊЕНО!)
			// ═══════════════════════════════════════════════════════════
			
			while (!isStopped() && !socket.isClosed()) {
				
				// Čitanje podataka sa socket-a
				br = input.read(data, 0, data.length);
				
				if (br <= 0) {
					logger.debug("NEON [{}]: Kraj stream-a (pročitano {} bajtova)", clientId, br);
					break;
				}
				
				ulaz = new String(data, 0, br);
				totalPackets++;
				
				// WARN logging за дијагностику
				logger.warn("NEON [{}]: Примљен пакет #{} ({} бајтова), садржај (првих 200 карактера): {}", 
				            clientId, totalPackets, br, 
				            ulaz.length() > 200 ? ulaz.substring(0, 200) + "..." : ulaz);
				
				// ═══════════════════════════════════════════════════════════
				// ПАРСИРАЊЕ ПАКЕТА - ОРИГИНАЛНА ЛОГИКА (НЕПРОМЕЊЕНО!)
				// ═══════════════════════════════════════════════════════════
				
				niz = ulaz.split(MESSAGE_DELIMITER);
				
				// Провера да ли је последња порука комплетна (завршава се са '>')
				// Ако не завршава са '>', то значи да је сечена на граници буфера
				boolean poslednjaKompletna = ulaz.endsWith(MESSAGE_DELIMITER);
				
				// Обрађујемо све елементе осим последњег ако није комплетна
				int krajIndeksa = poslednjaKompletna ? niz.length : niz.length - 1;
				boolean closeConnection = false;
				
				if (!poslednjaKompletna && niz.length > 0 && niz[niz.length - 1].length() > 0) {
					logger.debug("NEON [{}]: Детектована некомплетна порука на крају пакета ({} карактера), чека се следећи пакет", 
					            clientId, niz[niz.length - 1].length());
				}
				
				for (int i = 0; i < krajIndeksa; i++) {
					
					// Provera da li je validan ORIS protokol
					if (niz[i].startsWith(PROTOCOL_PREFIX_ORIS) || niz[i].startsWith(PROTOCOL_PREFIX_HASH_ORIS)) {
						
						da = niz[i].split(",");
						
						// Провера да ли порука има довољно поља (потребно најмање 3 за IMEI на позицији 2)
						if (da.length < 3) {
							logger.warn("NEON [{}]: Недостатак поља у поруци (потребно најмање 3, пронађено {}): '{}'", 
							            clientId, da.length, niz[i]);
							brojPromasaja++;
							if (brojPromasaja > MAX_FAILED_ATTEMPTS) {
								logger.error("NEON [{}]: Превише неважећих порука ({}), прекидам везу", 
								             clientId, brojPromasaja);
								break;
							}
							continue; // Прескочи ову поруку
						}
						
						// Pronalaženje uređaja (prvi put)
						if (uredjaj == null) {
							try {
								kodUredjaja = da[2];
								logger.warn("NEON [{}]: Покушај проналажења уређаја '{}' (порука: '{}')", 
								            clientId, kodUredjaja, niz[i]);
								pronadjiPostavi(kodUredjaja);
								logger.warn("NEON [{}]: Уређај пронађен: uredjaj={}, objekat={}", 
								            clientId, uredjaj != null ? uredjaj.getKod() : "null", 
								            objekat != null ? objekat.getOznaka() : "null");
							} catch (ArrayIndexOutOfBoundsException e) {
								logger.warn("NEON [{}]: Грешка приступа IMEI-ју (da[2]), da.length={}, порука: '{}'", 
								            clientId, da.length, niz[i], e);
								brojPromasaja++;
								if (brojPromasaja > MAX_FAILED_ATTEMPTS) {
									logger.error("NEON [{}]: Превише грешака, прекидам везу", clientId);
									break;
								}
								continue;
							}
						}
						
						// ═══════════════════════════════════════════════════════════
						// ОБРАДА ЈАВЉАЊА - ОРИГИНАЛНА ЛОГИКА (НЕПРОМЕЊЕНО!)
						// ═══════════════════════════════════════════════════════════
						
						if (objekat != null) {
							try {
								// ПОЗИВ PROTOCOL HANDLER-А (без измена!)
								javljanjeTrenutno = server.nProtokol.neonObrada(da, ulaz, objekat);
								
								logger.warn("NEON [{}]: Javljanje обрађено: javljanjeTrenutno={}, порука: '{}'", 
								            clientId, javljanjeTrenutno != null ? "OK" : "NULL", niz[i]);
								
								// ПОЗИВ OBRADE JAVLJANJA (без измена!)
								obradaJavljanja(javljanjeTrenutno, null);
								
								logger.warn("NEON [{}]: obradaJavljanja() завршена успешно", clientId);
								
								// Reset brojača promašaja nakon uspešne obrade
								brojPromasaja = 0;
								
							} catch (Exception e) {
								logger.warn("NEON [{}]: Грешка при обради javljanja, порука: '{}', грешка: {}", 
								            clientId, niz[i], e.getMessage(), e);
								brojPromasaja++;
								
								if (brojPromasaja > MAX_FAILED_ATTEMPTS) {
									logger.warn("NEON [{}]: Превише грешака ({}), прекидам везу", clientId, brojPromasaja);
									break;
								}
							}
							
						} else {
							logger.warn("NEON [{}]: Objekat је null, не могу обрадити. uredjaj={}, kodUredjaja={}, порука: '{}'", 
							            clientId, uredjaj != null ? uredjaj.getKod() : "null", 
							            kodUredjaja, niz[i]);
							brojPromasaja++;
							
							if (brojPromasaja > MAX_FAILED_ATTEMPTS) {
								logger.warn("NEON [{}]: Превише неуспешних покушаја, прекидам везу", clientId);
								break;
							}
						}
						
					} else {
						// Nevažeći format poruke
						if (!niz[i].equals("#")) {
							String preview = niz[i].length() > 20 ? niz[i].substring(0, 20) : niz[i];
							logger.warn("NEON [{}]: Nevažeći format (očekivan ORIS), početak poruke: '{}'", 
							            clientId, preview);
							brojPromasaja++;
							
							if (brojPromasaja >= MAX_FAILED_ATTEMPTS) {
								logger.error("NEON [{}]: Previše nevažećih poruka ({}), prekidam vezu", 
								             clientId, brojPromasaja);
								closeConnection = true;
								break;
							}
						}
					}
				}
				
				if (closeConnection) {
					break;
				}
				
				// ═══════════════════════════════════════════════════════════
				// ПРОВЕРА INTERRUPT СИГНАЛА
				// ═══════════════════════════════════════════════════════════
				
				if (Thread.currentThread().isInterrupted()) {
					logger.info("NEON [{}]: Thread interrupted, izlazim iz petlje", clientId);
					break;
				}
			}
			
			// ═══════════════════════════════════════════════════════════
			// КРАЈ ОБРАДЕ - СТАТИСТИКА
			// ═══════════════════════════════════════════════════════════
			
			logger.info("NEON [{}]: Završena obrada. Ukupno paketa: {}, promašaji: {}", 
			            clientId, totalPackets, brojPromasaja);
			
		} catch (SocketTimeoutException e) {
			logger.info("NEON [{}]: Socket timeout nakon {}ms neaktivnosti", 
			            clientId, connectionTimeoutMs);
			
		} catch (SocketException e) {
			// Normalno zatvaranje konekcije
			if (isStopped()) {
				logger.debug("NEON [{}]: Socket zatvoren (graceful shutdown)", clientId);
			} else {
				logger.warn("NEON [{}]: Socket greška: {}", clientId, e.getMessage());
			}
			
		} catch (InterruptedException e) {
			logger.info("NEON [{}]: Thread interrupted tokom čekanja socket-a", clientId);
			Thread.currentThread().interrupt(); // Restore interrupt status
			
		} catch (Throwable e) {
			// Neočekivana greška - loguj kontekst
			String context = " NEON: ";
			if (objekat != null) {
				context += objekat.getOznaka() + " " + test;
			}
			
			logger.error("NEON [{}]: Neočekivana greška{}", clientId, context, e);
			
		} finally {
			// GRACEFUL CLEANUP (uvek!)
			stop();
			
			if (clientId != null) {
				logger.debug("NEON [{}]: Thread završio", clientId);
			}
		}
	}
}
