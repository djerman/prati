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
			// ✅ Uzimanje socket-a iz reda
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
				
				// Debug logging (samo prva 3 i svaki 100-ti paket)
				if (totalPackets <= 3 || totalPackets % 100 == 0) {
					logger.debug("NEON [{}]: Primljen paket #{} ({} bajtova)", clientId, totalPackets, br);
				}
				
				// ═══════════════════════════════════════════════════════════
				// ПАРСИРАЊЕ ПАКЕТА - ОРИГИНАЛНА ЛОГИКА (НЕПРОМЕЊЕНО!)
				// ═══════════════════════════════════════════════════════════
				
				niz = ulaz.split(MESSAGE_DELIMITER);
				
				for (int i = 0; i < niz.length; i++) {
					
					// Provera da li je validan ORIS protokol
					if (niz[i].startsWith(PROTOCOL_PREFIX_ORIS) || niz[i].startsWith(PROTOCOL_PREFIX_HASH_ORIS)) {
						
						da = niz[i].split(",");
						
						// Pronalaženje uređaja (prvi put)
						if (uredjaj == null) {
							kodUredjaja = da[2];
							logger.debug("NEON [{}]: Pronalaženje uređaja '{}'", clientId, kodUredjaja);
							pronadjiPostavi(kodUredjaja);
						}
						
						// ═══════════════════════════════════════════════════════════
						// ОБРАДА ЈАВЉАЊА - ОРИГИНАЛНА ЛОГИКА (НЕПРОМЕЊЕНО!)
						// ═══════════════════════════════════════════════════════════
						
						if (objekat != null) {
							try {
								// ПОЗИВ PROTOCOL HANDLER-А (без измена!)
								javljanjeTrenutno = server.nProtokol.neonObrada(da, ulaz, objekat);
								
								// ПОЗИВ OBRADE JAVLJANJA (без измена!)
								obradaJavljanja(javljanjeTrenutno, null);
								
								// Reset brojača promašaja nakon uspešne obrade
								brojPromasaja = 0;
								
							} catch (Exception e) {
								logger.error("NEON [{}]: Greška pri obradi javljanja: {}", clientId, ulaz, e);
								brojPromasaja++;
								
								if (brojPromasaja > MAX_FAILED_ATTEMPTS) {
									logger.warn("NEON [{}]: Previše grešaka ({}), prekidam vezu", clientId, brojPromasaja);
									break;
								}
							}
							
						} else {
							logger.warn("NEON [{}]: Objekat je null, ne mogu obraditi: {}", clientId, ulaz);
							brojPromasaja++;
							
							if (brojPromasaja > MAX_FAILED_ATTEMPTS) {
								logger.warn("NEON [{}]: Previše neuspešnih pokušaja, prekidam vezu", clientId);
								break;
							}
						}
						
					} else {
						// Nevažeći format poruke
						if (!niz[i].equals("#")) {
							logger.warn("NEON [{}]: Nevažeći format (očekivan ORIS): '{}'", clientId, niz[i]);
							brojPromasaja++;
							
							if (brojPromasaja > MAX_FAILED_ATTEMPTS) {
								logger.error("NEON [{}]: Previše nevažećih poruka ({}), prekidam vezu", 
								             clientId, brojPromasaja);
								break;
							}
						}
					}
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