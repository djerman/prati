package rs.atekom.prati.server.ruptela;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.LinkedBlockingQueue;

import javax.xml.bind.DatatypeConverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pratiBaza.tabele.Javljanja;
import pratiBaza.tabele.Obd;
import rs.atekom.prati.server.JavljanjeObd;
import rs.atekom.prati.server.OpstiServer;
import rs.atekom.prati.server.OpstiThread;

/**
 * RUPTELA protocol handler thread.
 * 
 * <p>Обрађује TCP пакете од RUPTELA GPS уређаја у бинарном формату.
 * Подржава два типа протокола:</p>
 * <ul>
 *   <li><b>Стандардни (0x01)</b> - Records са 4 типа података</li>
 *   <li><b>Проширени (0x44)</b> - Extended records са додатним OBD подацима</li>
 * </ul>
 * 
 * <p><b>КРИТИЧНО:</b> ACK одговор се шаље тек након што је податак
 * успешно обрађен и снимљен у базу!</p>
 * 
 * @author Atekom
 * @version 2.0
 */
public class RuptelaOpstiThread extends OpstiThread {
	
	// ДОДАТ: Logger
	private static final Logger logger = LoggerFactory.getLogger(RuptelaOpstiThread.class);
	
	// ДОДАТ: Константе за протокол комaнде
	private static final int COMMAND_STANDARD = 0x01;   // Стандардни протокол
	private static final int COMMAND_EXTENDED = 0x44;   // Проширени протокол (68 decimal)
	
	// ДОДАТ: Offset константе за парсирање
	private static final int OFFSET_PACKET_LENGTH = 4;
	private static final int OFFSET_IMEI = 16;
	private static final int OFFSET_COMMAND = 2;
	private static final int OFFSET_RECORDS = 4;  // command id + recordsLeft

	public RuptelaOpstiThread(LinkedBlockingQueue<Socket> queue, OpstiServer server) {
		super(queue, server);
	}
	
	@Override
	public void run() {
		Socket socket = null;
		String clientId = null;
		
		try {
			// ✅ Узимање socket-a из реда
			socket = socketQueue.take();
			clientId = "RUPTELA-" + socket.getRemoteSocketAddress();
			
			// ✅ ПОЗИВ setupSocket() из parent klase (thread-safe)
			setupSocket(socket, clientId);
			
			input = socket.getInputStream();
			out = socket.getOutputStream();
			
			logger.info("RUPTELA klijent [{}] započeo obradu", clientId);
			
			int br = 0;
			int totalPackets = 0;
			
			// ═══════════════════════════════════════════════════════════
			// ГЛАВНА ПЕТЉА - ОБРАДА ПОДАТАКА (НЕПРОМЕЊЕНО!)
			// ═══════════════════════════════════════════════════════════
			
			while (!isStopped() && !socket.isClosed()) {
				
				// Читање података са socket-a
				br = input.read(data, 0, data.length);
				
				if (br <= 0) {
					logger.debug("RUPTELA [{}]: Kraj stream-a (pročitano {} bajtova)", clientId, br);
					break;
				}
				
				offset = 0;
				//ulaz = DatatypeConverter.printHexBinary(data);
				ulaz = DatatypeConverter.printHexBinary(java.util.Arrays.copyOf(data, br));
				totalPackets++;
				
				// Debug logging (само први 3 и сваки 100-ти пакет)
				if (totalPackets <= 3 || totalPackets % 100 == 0) {
					logger.debug("RUPTELA [{}]: Primljen paket #{} ({} bajtova)", clientId, totalPackets, br);
				}
				
				// ═══════════════════════════════════════════════════════════
				// ПАРСИРАЊЕ HEADER-А - ОРИГИНАЛНА ЛОГИКА (НЕПРОМЕЊЕНО!)
				// ═══════════════════════════════════════════════════════════
				
				offset += OFFSET_PACKET_LENGTH; // offset = 4
				
				// Pronalaženje uređaja (prvi put)
				if (uredjaj == null) {
					Long imei = Long.parseLong(ulaz.substring(offset, offset + OFFSET_IMEI), 16);
					kodUredjaja = imei.toString();
					
					logger.debug("RUPTELA [{}]: Pronalaženje uređaja IMEI={}", clientId, kodUredjaja);
					pronadjiPostavi(kodUredjaja);
				}
				
				offset += OFFSET_IMEI; // offset = 20
				
				// Čitanje komande (command ID)
				int komanda = Integer.parseInt(ulaz.substring(offset, offset + OFFSET_COMMAND), 16);
				
				logger.trace("RUPTELA [{}]: Komanda={} (0x{})", 
				             clientId, komanda, Integer.toHexString(komanda).toUpperCase());
				
				// ═══════════════════════════════════════════════════════════
				// ОБРАДА КОМAНДИ - ОРИГИНАЛНА ЛОГИКА (НЕПРОМЕЊЕНО!)
				// ═══════════════════════════════════════════════════════════
				
				if (komanda == COMMAND_STANDARD || komanda == COMMAND_EXTENDED) {
					
					offset += OFFSET_RECORDS; // offset = 24 (uključuje command id i recordsLeft)
					
					if (objekat != null) {
						
						int brZapisa = 0;
						offset += 2;
						int ukZapisa = Integer.parseInt(ulaz.substring(offset - 2, offset), 16);
						
						logger.debug("RUPTELA [{}]: Objekat={}, Ukupno zapisa={}, Komanda=0x{}", 
						             clientId, objekat.getOznaka(), ukZapisa, 
						             Integer.toHexString(komanda).toUpperCase());
						
						// ═══════════════════════════════════════════════════════════
						// СТАНДАРДНИ ПРОТОКОЛ (0x01)
						// ═══════════════════════════════════════════════════════════
						
						if (komanda == COMMAND_STANDARD) {
							
							logger.trace("RUPTELA [{}]: Obrada standardnog protokola", clientId);
							
							while (brZapisa < ukZapisa) {
								int pocetak = offset;
								
								// Parsiranje record strukture (NEPROMENJENO!)
								offset += 46;
								int brJedan = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								offset += 2;
								offset += brJedan * 4;
								
								int brDva = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								offset += 2;
								offset += brDva * 6;
								
								int brCetiri = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								offset += 2;
								offset += brCetiri * 10;
								
								int brOsam = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								offset += 2;
								offset += brOsam * 18;
								
								// PROTOCOL HANDLER - poziva se IDENTIČNO!
								JavljanjeObd javljanjeObd = server.rProtokol.vratiJavljanje(
									0, objekat, ulaz.substring(pocetak, offset)
								);
								
								// OBRADA I SNIMANJE U BAZU - poziva se IDENTIČNO!
								obradaJavljanja(javljanjeObd.getJavljanje(), javljanjeObd.getObd());
								
								brZapisa++;
							}
							
							// KRITIČNO: ACK se šalje TEK NAKON uspešne obrade!
							try {
								out.write(odg);
								out.flush();
								logger.trace("RUPTELA [{}]: ACK poslat za {} zapisa", clientId, ukZapisa);
							} catch (IOException e) {
								logger.error("RUPTELA [{}]: Greška slanja ACK", clientId, e);
								break; // Prekini vezu ako ne možemo da pošaljemo ACK
							}
							
						// ═══════════════════════════════════════════════════════════
						// ПРОШИРЕНИ ПРОТОКОЛ (0x44)
						// ═══════════════════════════════════════════════════════════
						
						} else { // komanda == COMMAND_EXTENDED
							
							logger.trace("RUPTELA [{}]: Obrada proširenog protokola", clientId);
							
							Javljanja prvo = null;
							Obd prvoObd = null;
							
							while (brZapisa < ukZapisa) {
								
								// Parsiranje proširene strukture (NEPROMENJENO!)
								int prvi = Integer.parseInt(ulaz.substring(offset + 10, offset + 11));
								int drugi = Integer.parseInt(ulaz.substring(offset + 11, offset + 12));
								
								int pocetak = offset;
								offset += 50;
								
								int brJedan = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								offset += 2;
								offset += brJedan * 6;
								
								int brDva = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								offset += 2;
								offset += brDva * 8;
								
								int brCetiri = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								offset += 2;
								offset += brCetiri * 12;
								
								int brOsam = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								offset += 2;
								offset += brOsam * 20;
								
								// PROTOCOL HANDLER - poziva se IDENTIČNO!
								JavljanjeObd javljanjeObd = server.rProtokol.vratiExtended(
									0, objekat, ulaz.substring(pocetak, offset)
								);
								
								// ═══════════════════════════════════════════════════════════
								// СПАЈАЊЕ OBD ПОДАТАКА - ОРИГИНАЛНА ЛОГИКА (НЕПРОМЕЊЕНО!)
								// ═══════════════════════════════════════════════════════════
								
								if (drugi <= prvi) {
									if (drugi == 0) {
										// Прво јављање
										prvo = javljanjeObd.getJavljanje();
										prvoObd = javljanjeObd.getObd();
										
										// OBRADA I SNIMANJE - poziva se IDENTIČNO!
										obradaJavljanja(prvo, prvoObd);
										
									} else {
										// Накнадни OBD подаци - спајање (НЕПРОМЕЊЕНО!)
										if (prvoObd == null) {
											prvoObd = javljanjeObd.getObd();
										} else {
											// Спајање свих OBD поља (НЕПРОМЕЊЕНО!)
											if (javljanjeObd.getObd().getAkumulator() != 0.0f) {
												prvoObd.setAkumulator(javljanjeObd.getObd().getAkumulator());
											}
											if (javljanjeObd.getObd().getGas() != 0.0f) {
												prvoObd.setGas(javljanjeObd.getObd().getGas());
											}
											if (javljanjeObd.getObd().getGreske() != "") {
												prvoObd.setGreske(javljanjeObd.getObd().getGreske());
											}
											if (javljanjeObd.getObd().getNivoGoriva() != 0.0f) {
												prvoObd.setNivoGoriva(javljanjeObd.getObd().getNivoGoriva());
											}
											if (javljanjeObd.getObd().getOpterecenje() != 0.0f) {
												prvoObd.setOpterecenje(javljanjeObd.getObd().getOpterecenje());
											}
											if (javljanjeObd.getObd().getProsecnaPotrosnja() != 0.0f) {
												prvoObd.setProsecnaPotrosnja(javljanjeObd.getObd().getProsecnaPotrosnja());
											}
											if (javljanjeObd.getObd().getRpm() != 0) {
												prvoObd.setRpm(javljanjeObd.getObd().getRpm());
											}
											if (javljanjeObd.getObd().getTripGorivo() != 0.0f) {
												prvoObd.setTripGorivo(javljanjeObd.getObd().getTripGorivo());
											}
											if (javljanjeObd.getObd().getTripKm() != 0.0f) {
												prvoObd.setTripKm(javljanjeObd.getObd().getTripKm());
											}
											if (javljanjeObd.getObd().getUkupnoVreme() != 0.0f) {
												prvoObd.setUkupnoVreme(javljanjeObd.getObd().getUkupnoVreme());
											}
											if (javljanjeObd.getObd().getUkupnoGorivo() != 0.0f) {
												prvoObd.setUkupnoGorivo(javljanjeObd.getObd().getUkupnoGorivo());
											}
											if (javljanjeObd.getObd().getUkupnoKm() != 0.0f) {
												prvoObd.setUkupnoKm(javljanjeObd.getObd().getUkupnoKm());
											}
										}
									}
								}
								
								brZapisa++;
							}
							
							// KRITIČNO: ACK se šalje TEK NAKON uspešne obrade!
							try {
								out.write(odg);
								out.flush();
								logger.trace("RUPTELA [{}]: ACK poslat za {} extended zapisa", clientId, ukZapisa);
							} catch (IOException e) {
								logger.error("RUPTELA [{}]: Greška slanja ACK", clientId, e);
								break;
							}
						}
						
					} else {
						logger.warn("RUPTELA [{}]: Objekat je null, ne mogu obraditi paket", clientId);
					}
					
				} else {
					// Непозната команда
					logger.warn("RUPTELA [{}]: Nepoznata komanda: {} (0x{})", 
					            clientId, komanda, Integer.toHexString(komanda).toUpperCase());
				}
				
				// ═══════════════════════════════════════════════════════════
				// ПРОВЕРА INTERRUPT СИГНАЛА
				// ═══════════════════════════════════════════════════════════
				
				if (Thread.currentThread().isInterrupted()) {
					logger.info("RUPTELA [{}]: Thread interrupted, izlazim iz petlje", clientId);
					break;
				}
			}
			
			// ═══════════════════════════════════════════════════════════
			// КРАЈ ОБРАДЕ - СТАТИСТИКА
			// ═══════════════════════════════════════════════════════════
			
			logger.info("RUPTELA [{}]: Završena obrada. Ukupno paketa: {}", clientId, totalPackets);
			
		} catch (SocketTimeoutException e) {
			logger.info("RUPTELA [{}]: Socket timeout nakon {}ms neaktivnosti", 
			            clientId, connectionTimeoutMs);
			
		} catch (SocketException e) {
			// Normalno zatvaranje konekcije
			if (isStopped()) {
				logger.debug("RUPTELA [{}]: Socket zatvoren (graceful shutdown)", clientId);
			} else {
				logger.warn("RUPTELA [{}]: Socket greška: {}", clientId, e.getMessage());
			}
			
		} catch (InterruptedException e) {
			logger.info("RUPTELA [{}]: Thread interrupted tokom čekanja socket-a", clientId);
			Thread.currentThread().interrupt(); // Restore interrupt status
			
		} catch (Throwable e) {
			// Neočekivana greška - loguj kontekst
			String context = " RUPTELA: ";
			if (objekat != null) {
				context += objekat.getOznaka() + " " + test;
			}
			
			logger.error("RUPTELA [{}]: Neočekivana greška{}", clientId, context, e);
			
		} finally {
			// GRACEFUL CLEANUP (uvek!)
			stop();
			
			if (clientId != null) {
				logger.debug("RUPTELA [{}]: Thread završio", clientId);
			}
		}
	}
}