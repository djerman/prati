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
	private static final int OFFSET_IMEI = 16;
	private static final int OFFSET_COMMAND = 2;

	public RuptelaOpstiThread(LinkedBlockingQueue<Socket> queue, OpstiServer server) {
		super(queue, server);
	}
	
	@Override
	public void run() {
		Socket socket = null;
		String clientId = null;
		
		try {
			//Узимање socket-a из реда
			socket = socketQueue.take();
			clientId = "RUPTELA-" + socket.getRemoteSocketAddress();
			
			//ПОЗИВ setupSocket() из parent klase (thread-safe)
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
				// ПАРСИРАЊЕ HEADER-А - ПРЕСКАЧЕМО PREAMBLE (4 бајта)
				// ═══════════════════════════════════════════════════════════
				// Према оригиналном коду, прва 4 бајта се прескачу као "preamble"
				
				// Провера минималне дужине пакета
				if (ulaz.length() < 4) {
					logger.warn("RUPTELA [{}]: Пакет прекратак ({} hex карактера, минимално 4)", 
					            clientId, ulaz.length());
					continue; // Прескочи овај пакет
				}
				
				// Прескачемо прва 4 бајта као "preamble" (као у оригиналном коду)
				offset = 4;
				
				// Pronalaženje uređaja (prvi put)
				if (uredjaj == null) {
					// Провера да ли има довољно података за IMEI
					if (ulaz.length() < offset + OFFSET_IMEI) {
						logger.warn("RUPTELA [{}]: Недостатак података за IMEI (потребно {} карактера, доступно {})", 
						            clientId, offset + OFFSET_IMEI, ulaz.length());
						continue; // Прескочи овај пакет
					}
					
					try {
						Long imei = Long.parseLong(ulaz.substring(offset, offset + OFFSET_IMEI), 16);
						
						// Валидација IMEI-ја
						if (imei <= 0 || imei > 999999999999999L) {
							logger.warn("RUPTELA [{}]: Невалидан IMEI: {}", clientId, imei);
							continue; // Прескочи овај пакет
						}
						
						kodUredjaja = imei.toString();
						
						logger.debug("RUPTELA [{}]: Pronalaženje uređaja IMEI={}", clientId, kodUredjaja);
						pronadjiPostavi(kodUredjaja);
					} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
						logger.error("RUPTELA [{}]: Грешка парсирања IMEI-ја: {}", clientId, e.getMessage());
						continue; // Прескочи овај пакет
					}
				}
				
				offset += OFFSET_IMEI; // offset = 4 + 16 = 20
				
				// Čitanje komande (command ID)
				// Провера да ли има довољно података за команду
				if (ulaz.length() < offset + OFFSET_COMMAND) {
					logger.warn("RUPTELA [{}]: Недостатак података за команду (потребно {} карактера, доступно {})", 
					            clientId, offset + OFFSET_COMMAND, ulaz.length());
					continue; // Прескочи овај пакет
				}
				
				int komanda;
				try {
					komanda = Integer.parseInt(ulaz.substring(offset, offset + OFFSET_COMMAND), 16);
				} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
					logger.error("RUPTELA [{}]: Грешка парсирања команде: {}", clientId, e.getMessage());
					continue; // Прескочи овај пакет
				}
				
				logger.trace("RUPTELA [{}]: Komanda={} (0x{})", 
				             clientId, komanda, Integer.toHexString(komanda).toUpperCase());
				
				// ═══════════════════════════════════════════════════════════
				// ОБРАДА КОМAНДИ - AVL DATA PACKET
				// ═══════════════════════════════════════════════════════════
				
				if (komanda == COMMAND_STANDARD || komanda == COMMAND_EXTENDED) {
					
					// ═══════════════════════════════════════════════════════════
					// ЧИТАЊЕ RECORDS LEFT И NUMBER OF RECORDS
					// ═══════════════════════════════════════════════════════════
					// 
					// ВАЖНО: Према оригиналном коду, после Command-а се offset увећава за 4
					// (Command (2) + Records left (2)), па се затим чита Number of records
					// Ова логика је задржана за компатибилност са оригиналним кодом
					
					// Провера да ли има довољно података за Records left (1 byte) + Number of records (1 byte)
					if (ulaz.length() < offset + 6) { // Command (2) + Records left (2) + Number of records (2)
						logger.warn("RUPTELA [{}]: Недостатак података за Records left и Number of records (потребно {} карактера, доступно {})", 
						            clientId, offset + 6, ulaz.length());
						try {
							out.write(nack);
							out.flush();
							logger.debug("RUPTELA [{}]: NACK послат - недостатак података за Records left/Number of records", clientId);
						} catch (IOException e) {
							logger.error("RUPTELA [{}]: Грешка слања NACK", clientId, e);
						}
						continue; // Прескочи овај пакет
					}
					
					// Према оригиналном коду: offset += 4 (Command + Records left)
					offset += OFFSET_COMMAND; // Прескочи Command (2 hex chars)
					
					// Читање Records left (1 byte = 2 hex chars)
					int recordsLeft = -1;
					try {
						recordsLeft = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
					} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
						logger.error("RUPTELA [{}]: Грешка парсирања Records left: {}", clientId, e.getMessage());
						try {
							out.write(nack);
							out.flush();
							logger.debug("RUPTELA [{}]: NACK послат - грешка парсирања Records left", clientId);
						} catch (IOException ioex) {
							logger.error("RUPTELA [{}]: Грешка слања NACK", clientId, ioex);
						}
						continue; // Прескочи овај пакет
					}
					
					offset += 2; // Прескочи Records left
					
					// Читање Number of records (1 byte = 2 hex chars)
					int ukZapisa = -1;
					try {
						String numRecordsHex = ulaz.substring(offset, offset + 2);
						ukZapisa = Integer.parseInt(numRecordsHex, 16);
						
						// Валидација броја записа
						if (ukZapisa <= 0 || ukZapisa > 255) {
							logger.warn("RUPTELA [{}]: Невалидан број записа: {} (очекивано 1-255)", clientId, ukZapisa);
							try {
								out.write(nack);
								out.flush();
								logger.debug("RUPTELA [{}]: NACK послат - невалидан број записа", clientId);
							} catch (IOException ioex) {
								logger.error("RUPTELA [{}]: Грешка слања NACK", clientId, ioex);
							}
							continue; // Прескочи овај пакет
						}
						
						logger.debug("RUPTELA [{}]: Number of records: {}, Records left: {}", 
						            clientId, ukZapisa, recordsLeft);
					} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
						logger.error("RUPTELA [{}]: Грешка парсирања Number of records на offset {}: {}", 
						            clientId, offset, e.getMessage());
						try {
							out.write(nack);
							out.flush();
							logger.debug("RUPTELA [{}]: NACK послат - грешка парсирања Number of records", clientId);
						} catch (IOException ioex) {
							logger.error("RUPTELA [{}]: Грешка слања NACK", clientId, ioex);
						}
						continue; // Прескочи овај пакет
					}
					
					offset += 2; // Прескочи Number of records
					
					// Логирање Records left за debugging и мониторинг
					if (recordsLeft > 0) {
						logger.debug("RUPTELA [{}]: Records left={} (уређај има још {} записа након овог пакета)", 
						             clientId, recordsLeft, recordsLeft);
					}
					
					if (objekat != null) {
						
						logger.debug("RUPTELA [{}]: Objekat={}, Ukupno zapisa={}, Records left={}, Komanda=0x{}", 
						             clientId, objekat.getOznaka(), ukZapisa, recordsLeft, 
						             Integer.toHexString(komanda).toUpperCase());
						
						int brZapisa = 0;
						
						// ═══════════════════════════════════════════════════════════
						// СТАНДАРДНИ ПРОТОКОЛ (0x01)
						// ═══════════════════════════════════════════════════════════
						
						if (komanda == COMMAND_STANDARD) {
							
							logger.trace("RUPTELA [{}]: Obrada standardnog protokola", clientId);
							
							// ═══════════════════════════════════════════════════════════
							// ПАРСИРАЊЕ И УПИС ЗАПИСА (ОДМАХ ТОКОМ ПАРСИРАЊА)
							// ═══════════════════════════════════════════════════════════
							
							while (brZapisa < ukZapisa) {
								int pocetak = offset;
								
								// Провера да ли има довољно података за record header (46 карактера)
								if (ulaz.length() < offset + 46) {
									logger.warn("RUPTELA [{}]: Недостатак података за record header (запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									break; // Прекини обраду записа
								}
								
								// Parsiranje record strukture
								offset += 46;
								
								// Провера пре читања brJedan
								if (ulaz.length() < offset + 2) {
									logger.warn("RUPTELA [{}]: Недостатак података за brJedan (запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									break;
								}
								int brJedan;
								try {
									brJedan = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
									logger.error("RUPTELA [{}]: Грешка парсирања brJedan (запис {}/{}): {}", 
									            clientId, brZapisa + 1, ukZapisa, e.getMessage());
									break;
								}
								offset += 2;
								offset += brJedan * 4;
								
								// Провера пре читања brDva
								if (ulaz.length() < offset + 2) {
									logger.warn("RUPTELA [{}]: Недостатак података за brDva (запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									break;
								}
								int brDva;
								try {
									brDva = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
									logger.error("RUPTELA [{}]: Грешка парсирања brDva (запис {}/{}): {}", 
									            clientId, brZapisa + 1, ukZapisa, e.getMessage());
									break;
								}
								offset += 2;
								offset += brDva * 6;
								
								// Провера пре читања brCetiri
								if (ulaz.length() < offset + 2) {
									logger.warn("RUPTELA [{}]: Недостатак података за brCetiri (запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									break;
								}
								int brCetiri;
								try {
									brCetiri = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
									logger.error("RUPTELA [{}]: Грешка парсирања brCetiri (запис {}/{}): {}", 
									            clientId, brZapisa + 1, ukZapisa, e.getMessage());
									break;
								}
								offset += 2;
								offset += brCetiri * 10;
								
								// Провера пре читања brOsam
								if (ulaz.length() < offset + 2) {
									logger.warn("RUPTELA [{}]: Недостатак података за brOsam (запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									break;
								}
								int brOsam;
								try {
									brOsam = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
									logger.error("RUPTELA [{}]: Грешка парсирања brOsam (запис {}/{}): {}", 
									            clientId, brZapisa + 1, ukZapisa, e.getMessage());
									break;
								}
								offset += 2;
								offset += brOsam * 18;
								
								// Провера да ли има довољно података за цео record пре позива protocol handler-а
								if (ulaz.length() < offset) {
									logger.warn("RUPTELA [{}]: Недостатак података за цео record (запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									break;
								}
								
								// PROTOCOL HANDLER - парсирање записа
								try {
									JavljanjeObd javljanjeObd = server.rProtokol.vratiJavljanje(
										0, objekat, ulaz.substring(pocetak, offset)
									);
									
									// Упис у базу одмах након парсирања (као у старом коду)
									if (javljanjeObd != null && javljanjeObd.getJavljanje() != null) {
										obradaJavljanja(javljanjeObd.getJavljanje(), javljanjeObd.getObd());
										brZapisa++;
									} else {
										logger.warn("RUPTELA [{}]: Protocol handler вратио null за запис {}/{}", 
										            clientId, brZapisa + 1, ukZapisa);
										// Настави са следећим записом
									}
								} catch (Exception e) {
									logger.error("RUPTELA [{}]: Грешка при парсирању/упису записа {}/{}: {}", 
									            clientId, brZapisa + 1, ukZapisa, e.getMessage());
									// Настави са следећим записом
								}
							}
							
							// ═══════════════════════════════════════════════════════════
							// СЛАЊЕ ACK (НАКОН СВИХ ЗАПИСА)
							// ═══════════════════════════════════════════════════════════
							
							try {
								out.write(odg);
								out.flush();
								logger.trace("RUPTELA [{}]: ACK poslat за {} zapisa", clientId, brZapisa);
							} catch (IOException e) {
								logger.error("RUPTELA [{}]: Greška slanja ACK", clientId, e);
								break; // Prekini vezu ako ne možemo da pošaljemo ACK
							}
							
						// ═══════════════════════════════════════════════════════════
						// ПРОШИРЕНИ ПРОТОКОЛ (0x44)
						// ═══════════════════════════════════════════════════════════
						
						} else { // komanda == COMMAND_EXTENDED
							
							logger.trace("RUPTELA [{}]: Obrada proširenog protokola", clientId);
							
							// ═══════════════════════════════════════════════════════════
							// ПАРСИРАЊЕ И УПИС ЗАПИСА (ОДМАХ ТОКОМ ПАРСИРАЊА)
							// ═══════════════════════════════════════════════════════════
							
							Javljanja prvo = null;
							Obd prvoObd = null;
							brZapisa = 0; // Ресетујемо бројач за Extended протокол
							
							while (brZapisa < ukZapisa) {
								
								// Провера да ли има довољно података за почетак проширеног record-а (12 карактера за prvi/drugi)
								if (ulaz.length() < offset + 12) {
									logger.warn("RUPTELA [{}]: Недостатак података за проширени record header (запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									break; // Прекини обраду записа
								}
								
								// Parsiranje proširene strukture
								int prvi, drugi;
								try {
									prvi = Integer.parseInt(ulaz.substring(offset + 10, offset + 11));
									drugi = Integer.parseInt(ulaz.substring(offset + 11, offset + 12));
								} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
									logger.error("RUPTELA [{}]: Грешка парсирања prvi/drugi (запис {}/{}): {}", 
									            clientId, brZapisa + 1, ukZapisa, e.getMessage());
									break;
								}
								
								int pocetak = offset;
								
								// Провера да ли има довољно података за проширени record header (50 карактера)
								if (ulaz.length() < offset + 50) {
									logger.warn("RUPTELA [{}]: Недостатак података за проширени record header (запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									break;
								}
								offset += 50;
								
								// Провера пре читања brJedan
								if (ulaz.length() < offset + 2) {
									logger.warn("RUPTELA [{}]: Недостатак података за brJedan (проширени, запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									break;
								}
								int brJedan;
								try {
									brJedan = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
									logger.error("RUPTELA [{}]: Грешка парсирања brJedan (проширени, запис {}/{}): {}", 
									            clientId, brZapisa + 1, ukZapisa, e.getMessage());
									break;
								}
								offset += 2;
								offset += brJedan * 6;
								
								// Провера пре читања brDva
								if (ulaz.length() < offset + 2) {
									logger.warn("RUPTELA [{}]: Недостатак података за brDva (проширени, запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									break;
								}
								int brDva;
								try {
									brDva = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
									logger.error("RUPTELA [{}]: Грешка парсирања brDva (проширени, запис {}/{}): {}", 
									            clientId, brZapisa + 1, ukZapisa, e.getMessage());
									break;
								}
								offset += 2;
								offset += brDva * 8;
								
								// Провера пре читања brCetiri
								if (ulaz.length() < offset + 2) {
									logger.warn("RUPTELA [{}]: Недостатак података за brCetiri (проширени, запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									break;
								}
								int brCetiri;
								try {
									brCetiri = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
									logger.error("RUPTELA [{}]: Грешка парсирања brCetiri (проширени, запис {}/{}): {}", 
									            clientId, brZapisa + 1, ukZapisa, e.getMessage());
									break;
								}
								offset += 2;
								offset += brCetiri * 12;
								
								// Провера пре читања brOsam
								if (ulaz.length() < offset + 2) {
									logger.warn("RUPTELA [{}]: Недостатак података за brOsam (проширени, запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									break;
								}
								int brOsam;
								try {
									brOsam = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
									logger.error("RUPTELA [{}]: Грешка парсирања brOsam (проширени, запис {}/{}): {}", 
									            clientId, brZapisa + 1, ukZapisa, e.getMessage());
									break;
								}
								offset += 2;
								offset += brOsam * 20;
								
								// Провера да ли има довољно података за цео record пре позива protocol handler-а
								if (ulaz.length() < offset) {
									logger.warn("RUPTELA [{}]: Недостатак података за цео проширени record (запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									break;
								}
								
								// PROTOCOL HANDLER - парсирање записа
								try {
									JavljanjeObd javljanjeObd = server.rProtokol.vratiExtended(
										0, objekat, ulaz.substring(pocetak, offset)
									);
									
									// ═══════════════════════════════════════════════════════════
									// СПАЈАЊЕ OBD ПОДАТАКА - ОРИГИНАЛНА ЛОГИКА
									// ═══════════════════════════════════════════════════════════
									
									if (javljanjeObd != null) {
										if (drugi <= prvi) {
											if (drugi == 0) {
												// Прво јављање - чувамо за каснији упис
												prvo = javljanjeObd.getJavljanje();
												prvoObd = javljanjeObd.getObd();
												
												if (prvo == null) {
													logger.warn("RUPTELA [{}]: Javljanje је null за проширени запис {}/{} (drugi=0)", 
													            clientId, brZapisa + 1, ukZapisa);
												}
											} else {
												// Накнадни OBD подаци - спајање
												Obd trenutniObd = javljanjeObd.getObd();
												
												if (trenutniObd != null && prvoObd != null) {
													// Спајање свих OBD поља
													if (trenutniObd.getAkumulator() != 0.0f) {
														prvoObd.setAkumulator(trenutniObd.getAkumulator());
													}
													if (trenutniObd.getGas() != 0.0f) {
														prvoObd.setGas(trenutniObd.getGas());
													}
													if (trenutniObd.getGreske() != null && !trenutniObd.getGreske().isEmpty()) {
														prvoObd.setGreske(trenutniObd.getGreske());
													}
													if (trenutniObd.getNivoGoriva() != 0.0f) {
														prvoObd.setNivoGoriva(trenutniObd.getNivoGoriva());
													}
													if (trenutniObd.getOpterecenje() != 0.0f) {
														prvoObd.setOpterecenje(trenutniObd.getOpterecenje());
													}
													if (trenutniObd.getProsecnaPotrosnja() != 0.0f) {
														prvoObd.setProsecnaPotrosnja(trenutniObd.getProsecnaPotrosnja());
													}
													if (trenutniObd.getRpm() != 0) {
														prvoObd.setRpm(trenutniObd.getRpm());
													}
													if (trenutniObd.getTripGorivo() != 0.0f) {
														prvoObd.setTripGorivo(trenutniObd.getTripGorivo());
													}
													if (trenutniObd.getTripKm() != 0.0f) {
														prvoObd.setTripKm(trenutniObd.getTripKm());
													}
													if (trenutniObd.getUkupnoVreme() != 0.0f) {
														prvoObd.setUkupnoVreme(trenutniObd.getUkupnoVreme());
													}
													if (trenutniObd.getUkupnoGorivo() != 0.0f) {
														prvoObd.setUkupnoGorivo(trenutniObd.getUkupnoGorivo());
													}
													if (trenutniObd.getUkupnoKm() != 0.0f) {
														prvoObd.setUkupnoKm(trenutniObd.getUkupnoKm());
													}
												}
											}
										}
										brZapisa++;
									} else {
										logger.warn("RUPTELA [{}]: Protocol handler вратио null за проширени запис {}/{}", 
										            clientId, brZapisa + 1, ukZapisa);
									}
								} catch (Exception e) {
									logger.error("RUPTELA [{}]: Грешка при парсирању/спајању проширеног записа {}/{}: {}", 
									            clientId, brZapisa + 1, ukZapisa, e.getMessage());
								}
							}
							
							// ═══════════════════════════════════════════════════════════
							// УПИС У БАЗУ И СЛАЊЕ ACK (НАКОН СВИХ ЗАПИСА)
							// ═══════════════════════════════════════════════════════════
							
							if (prvo != null) {
								// Уписујемо prvo/prvoObd у базу
								obradaJavljanja(prvo, prvoObd);
								
								// Шаљемо ACK
								try {
									out.write(odg);
									out.flush();
									logger.trace("RUPTELA [{}]: ACK poslat за {} extended zapisa", clientId, brZapisa);
								} catch (IOException e) {
									logger.error("RUPTELA [{}]: Greška slanja ACK", clientId, e);
									break;
								}
							} else {
								logger.warn("RUPTELA [{}]: Нема валидног првог записа за упис (обрађено {}/{} extended записа)", 
								            clientId, brZapisa, ukZapisa);
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