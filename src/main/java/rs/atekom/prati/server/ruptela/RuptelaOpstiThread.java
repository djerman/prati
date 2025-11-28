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
				// ДЕТЕКЦИЈА И ПАРСИРАЊЕ HEADER-А - TCP PACKET WRAPPER (опционално)
				// ═══════════════════════════════════════════════════════════
				// 
				// НАПОМЕНА: Неки уређаји шаљу TCP Packet Wrapper (Packet length + CRC),
				// а неки шаљу директно AVL Data Packet (са "preamble" од 4 бајта).
				// Проверавамо да ли постоји TCP Packet Wrapper:
				// - Ако прва 4 бајта изгледају као Packet length (мањи од максимума),
				//   и следећа 4 бајта имају валидан CRC, онда је то TCP Packet Wrapper.
				// - Иначе, прескачемо прва 4 бајта као "preamble" и читамо директно AVL Data Packet.
				
				// Провера минималне дужине пакета
				if (ulaz.length() < 8) {
					logger.warn("RUPTELA [{}]: Пакет прекратак ({} hex карактера, минимално 8)", 
					            clientId, ulaz.length());
					try {
						out.write(nack);
						out.flush();
						logger.debug("RUPTELA [{}]: NACK послат - пакет прекратак", clientId);
					} catch (IOException e) {
						logger.error("RUPTELA [{}]: Грешка слања NACK", clientId, e);
					}
					continue; // Прескочи овај пакет
				}
				
				// Покушај да детектујемо TCP Packet Wrapper
				boolean hasTcpWrapper = false;
				int packetLength = 0;
				int tcpWrapperOffset = 0; // Offset после TCP Packet Wrapper или preamble (4 или 8)
				
				try {
					// Читање првих 4 бајтова као Packet length
					packetLength = Integer.parseInt(ulaz.substring(0, 4), 16);
					
					// Провера да ли Packet length изгледа разумно (мањи од 10000 бајтова)
					// и да ли пакет има довољно података за TCP Packet Wrapper
					if (packetLength > 0 && packetLength < 10000 && ulaz.length() >= (packetLength * 2 + 8)) {
						// Верификација CRC-16 за TCP Packet Wrapper
						byte[] packetLengthBytes = new byte[2];
						packetLengthBytes[0] = (byte)((packetLength >>> 8) & 0xFF);
						packetLengthBytes[1] = (byte)(packetLength & 0xFF);
						int calculatedCrc = calculateCrc16Kermit(packetLengthBytes);
						
						// Читање CRC-16 из пакета (после Packet length, 2 bytes = 4 hex chars)
						int receivedCrc = Integer.parseInt(ulaz.substring(4, 8), 16);
						
						// Ако се CRC поклапа, онда је то TCP Packet Wrapper
						if (calculatedCrc == receivedCrc) {
							hasTcpWrapper = true;
							tcpWrapperOffset = 8; // Packet length (4) + CRC (4) = 8 hex chars
							logger.trace("RUPTELA [{}]: Детектован TCP Packet Wrapper (Packet length: {}, CRC: 0x{})", 
							            clientId, packetLength, String.format("%04X", calculatedCrc));
						}
					}
				} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
					// Не може да се парсира као TCP Packet Wrapper, користимо стари формат
					hasTcpWrapper = false;
				}
				
				// Ако нема TCP Packet Wrapper, прескачемо прва 4 бајта као "preamble" (као у оригиналном коду)
				if (!hasTcpWrapper) {
					tcpWrapperOffset = 4; // Прескачемо "preamble" од 4 бајта
					logger.warn("RUPTELA [{}]: Користи се стари формат (без TCP Packet Wrapper), tcpWrapperOffset={}", 
					            clientId, tcpWrapperOffset);
				} else {
					logger.warn("RUPTELA [{}]: Детектован TCP Packet Wrapper, tcpWrapperOffset={}, packetLength={}", 
					            clientId, tcpWrapperOffset, packetLength);
				}
				
				offset = tcpWrapperOffset;
				logger.warn("RUPTELA [{}]: Почетни offset постављен на {} (hex дужина пакета: {})", 
				            clientId, offset, ulaz.length());
				
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
						kodUredjaja = imei.toString();
						
						logger.debug("RUPTELA [{}]: Pronalaženje uređaja IMEI={}", clientId, kodUredjaja);
						pronadjiPostavi(kodUredjaja);
					} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
						logger.error("RUPTELA [{}]: Грешка парсирања IMEI-ја: {}", clientId, e.getMessage());
						continue; // Прескочи овај пакет
					}
				}
				
				offset += OFFSET_IMEI; // offset = tcpWrapperOffset + 16 (20 или 24 зависно од формата)
				
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
						logger.warn("RUPTELA [{}]: Number of records прочитан: {} (hex: 0x{}, offset: {}, recordsLeft: {})", 
						            clientId, ukZapisa, numRecordsHex, offset, recordsLeft);
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
						
						logger.warn("RUPTELA [{}]: Objekat={}, Ukupno zapisa={}, Records left={}, Komanda=0x{}, offset={}", 
						             clientId, objekat.getOznaka(), ukZapisa, recordsLeft, 
						             Integer.toHexString(komanda).toUpperCase(), offset);
						
						int brZapisa = 0;
						
						// ═══════════════════════════════════════════════════════════
						// СТАНДАРДНИ ПРОТОКОЛ (0x01)
						// ═══════════════════════════════════════════════════════════
						
						if (komanda == COMMAND_STANDARD) {
							
							logger.trace("RUPTELA [{}]: Obrada standardnog protokola", clientId);
							
							// Флаг за праћење успешности обраде - ACK се шаље само ако је обрада успешна
							boolean obradaUspesna = true;
							
							while (brZapisa < ukZapisa) {
								int pocetak = offset;
								
								// Провера да ли има довољно података за record header (46 карактера)
								if (ulaz.length() < offset + 46) {
									logger.warn("RUPTELA [{}]: Недостатак података за record header (запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									obradaUspesna = false;
									break; // Прекини обраду записа
								}
								
								// Parsiranje record strukture (NEPROMENJENO!)
								offset += 46;
								
								// Провера пре читања brJedan
								if (ulaz.length() < offset + 2) {
									logger.warn("RUPTELA [{}]: Недостатак података за brJedan (запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									obradaUspesna = false;
									break;
								}
								int brJedan;
								try {
									brJedan = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
									logger.error("RUPTELA [{}]: Грешка парсирања brJedan (запис {}/{}): {}", 
									            clientId, brZapisa + 1, ukZapisa, e.getMessage());
									obradaUspesna = false;
									break;
								}
								offset += 2;
								offset += brJedan * 4;
								
								// Провера пре читања brDva
								if (ulaz.length() < offset + 2) {
									logger.warn("RUPTELA [{}]: Недостатак података за brDva (запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									obradaUspesna = false;
									break;
								}
								int brDva;
								try {
									brDva = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
									logger.error("RUPTELA [{}]: Грешка парсирања brDva (запис {}/{}): {}", 
									            clientId, brZapisa + 1, ukZapisa, e.getMessage());
									obradaUspesna = false;
									break;
								}
								offset += 2;
								offset += brDva * 6;
								
								// Провера пре читања brCetiri
								if (ulaz.length() < offset + 2) {
									logger.warn("RUPTELA [{}]: Недостатак података за brCetiri (запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									obradaUspesna = false;
									break;
								}
								int brCetiri;
								try {
									brCetiri = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
									logger.error("RUPTELA [{}]: Грешка парсирања brCetiri (запис {}/{}): {}", 
									            clientId, brZapisa + 1, ukZapisa, e.getMessage());
									obradaUspesna = false;
									break;
								}
								offset += 2;
								offset += brCetiri * 10;
								
								// Провера пре читања brOsam
								if (ulaz.length() < offset + 2) {
									logger.warn("RUPTELA [{}]: Недостатак података за brOsam (запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									obradaUspesna = false;
									break;
								}
								int brOsam;
								try {
									brOsam = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
									logger.error("RUPTELA [{}]: Грешка парсирања brOsam (запис {}/{}): {}", 
									            clientId, brZapisa + 1, ukZapisa, e.getMessage());
									obradaUspesna = false;
									break;
								}
								offset += 2;
								offset += brOsam * 18;
								
								// Провера да ли има довољно података за цео record пре позива protocol handler-а
								if (ulaz.length() < offset) {
									logger.warn("RUPTELA [{}]: Недостатак података за цео record (запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									obradaUspesna = false;
									break;
								}
								
								// PROTOCOL HANDLER - poziva se IDENTIČNO!
								JavljanjeObd javljanjeObd = server.rProtokol.vratiJavljanje(
									0, objekat, ulaz.substring(pocetak, offset)
								);
								
								// Провера да ли protocol handler врати null
								if (javljanjeObd == null) {
									logger.warn("RUPTELA [{}]: Protocol handler вратио null за запис {}/{}", 
									            clientId, brZapisa + 1, ukZapisa);
									brZapisa++;
									continue; // Прескочи овај запис
								}
								
								// Провера да ли javljanje није null
								if (javljanjeObd.getJavljanje() == null) {
									logger.warn("RUPTELA [{}]: Javljanje је null за запис {}/{}", 
									            clientId, brZapisa + 1, ukZapisa);
									brZapisa++;
									continue; // Прескочи овај запис
								}
								
								// OBRADA I SNIMANJE U BAZU - poziva se IDENTIČNO!
								obradaJavljanja(javljanjeObd.getJavljanje(), javljanjeObd.getObd());
								
								brZapisa++;
							}
							
							// ═══════════════════════════════════════════════════════════
							// ВЕРИФИКАЦИЈА AVL DATA PACKET CRC-16 И NUMBER OF RECORDS
							// ═══════════════════════════════════════════════════════════
							
							// ═══════════════════════════════════════════════════════════
							// ВЕРИФИКАЦИЈА AVL DATA PACKET CRC-16 И NUMBER OF RECORDS
							// ═══════════════════════════════════════════════════════════
							
							// Провера да ли има довољно података за Number of records (1 byte) + CRC-16 (2 bytes)
							if (ulaz.length() < offset + 6) {
								logger.warn("RUPTELA [{}]: Недостатак података за Number of records и CRC-16 на крају пакета", 
								            clientId);
								obradaUspesna = false;
							} else if (obradaUspesna) {
								// Провера Number of records на крају пакета
								int numRecordsEnd = -1;
								try {
									numRecordsEnd = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								} catch (NumberFormatException e) {
									logger.error("RUPTELA [{}]: Грешка парсирања Number of records на крају пакета", clientId, e);
									obradaUspesna = false;
								}
								
								if (obradaUspesna && numRecordsEnd != ukZapisa) {
									logger.warn("RUPTELA [{}]: Number of records се не поклапа (почетак: {}, крај: {}, offset: {}, обрађено записа: {})", 
									            clientId, ukZapisa, numRecordsEnd, offset, brZapisa);
									obradaUspesna = false;
								} else if (obradaUspesna) {
									logger.warn("RUPTELA [{}]: Number of records се поклапа (почетак: {}, крај: {}, обрађено записа: {})", 
									            clientId, ukZapisa, numRecordsEnd, brZapisa);
								}
								
								// Верификација CRC-16 за AVL Data Packet
								// CRC се рачуна за: IMEI + Command + Records left + Number of records + Records + Number of records
								// Дакле, од tcpWrapperOffset (после TCP Packet Wrapper или preamble) до offset + 2 (укључујући Number of records на крају)
								if (obradaUspesna) {
									int avlDataStart = tcpWrapperOffset; // Почетак AVL Data Packet-а (после TCP Packet Wrapper или preamble)
									
									// Израчунај CRC за AVL Data Packet (од IMEI до Number of records на крају, без CRC-16)
									// Конвертујемо hex string у byte array
									byte[] avlDataBytes = hexStringToByteArray(ulaz.substring(avlDataStart, offset + 2));
									int calculatedAvlCrc = calculateCrc16Kermit(avlDataBytes);
									
									// Читање CRC-16 из пакета (после Number of records на крају, 2 bytes = 4 hex chars)
									int receivedAvlCrc = -1;
									try {
										receivedAvlCrc = Integer.parseInt(ulaz.substring(offset + 2, offset + 6), 16);
									} catch (NumberFormatException e) {
										logger.error("RUPTELA [{}]: Грешка парсирања AVL Data Packet CRC-16", clientId, e);
										obradaUspesna = false;
									}
									
									if (obradaUspesna && calculatedAvlCrc != receivedAvlCrc) {
									logger.warn("RUPTELA [{}]: AVL Data Packet CRC-16 невалидан (израчунато: 0x{}, примљено: 0x{})", 
									            clientId, String.format("%04X", calculatedAvlCrc), String.format("%04X", receivedAvlCrc));
										obradaUspesna = false;
									} else if (obradaUspesna) {
										logger.trace("RUPTELA [{}]: AVL Data Packet CRC-16 валидан (0x{})", clientId, String.format("%04X", calculatedAvlCrc));
									}
								}
							}
							
							// KRITIČНО: ACK se šalje TEK NAKON uspešne obrade И верификације!
							if (obradaUspesna) {
								try {
									out.write(odg);
									out.flush();
									logger.trace("RUPTELA [{}]: ACK poslat за {} zapisa (обрада успешна, CRC валидан)", clientId, ukZapisa);
								} catch (IOException e) {
									logger.error("RUPTELA [{}]: Greška slanja ACK", clientId, e);
									break; // Prekini vezu ako ne možemo da pošaljemo ACK
								}
							} else {
								logger.warn("RUPTELA [{}]: ACK НИЈЕ послат - обрада није успешна или CRC невалидан (обрађено {}/{} записа)", 
								            clientId, brZapisa, ukZapisa);
								// Пошаљи NACK ако је обрада неуспешна због CRC или Number of records проблема
								try {
									out.write(nack);
									out.flush();
									logger.debug("RUPTELA [{}]: NACK послат - обрада неуспешна или CRC невалидан", clientId);
								} catch (IOException e) {
									logger.error("RUPTELA [{}]: Грешка слања NACK", clientId, e);
								}
							}
							
						// ═══════════════════════════════════════════════════════════
						// ПРОШИРЕНИ ПРОТОКОЛ (0x44)
						// ═══════════════════════════════════════════════════════════
						
						} else { // komanda == COMMAND_EXTENDED
							
							logger.trace("RUPTELA [{}]: Obrada proširenog protokola", clientId);
							
							// Флаг за праћење успешности обраде - ACK се шаље само ако је обрада успешна
							boolean obradaUspesnaExtended = true;
							
							Javljanja prvo = null;
							Obd prvoObd = null;
							
							while (brZapisa < ukZapisa) {
								
								// Провера да ли има довољно података за почетак проширеног record-а (12 карактера за prvi/drugi)
								if (ulaz.length() < offset + 12) {
									logger.warn("RUPTELA [{}]: Недостатак података за проширени record header (запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									obradaUspesnaExtended = false;
									break; // Прекини обраду записа
								}
								
								// Parsiranje proširene strukture (NEPROMENJENO!)
								int prvi, drugi;
								try {
									prvi = Integer.parseInt(ulaz.substring(offset + 10, offset + 11));
									drugi = Integer.parseInt(ulaz.substring(offset + 11, offset + 12));
								} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
									logger.error("RUPTELA [{}]: Грешка парсирања prvi/drugi (запис {}/{}): {}", 
									            clientId, brZapisa + 1, ukZapisa, e.getMessage());
									obradaUspesnaExtended = false;
									break;
								}
								
								int pocetak = offset;
								
								// Провера да ли има довољно података за проширени record header (50 карактера)
								if (ulaz.length() < offset + 50) {
									logger.warn("RUPTELA [{}]: Недостатак података за проширени record header (запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									obradaUspesnaExtended = false;
									break;
								}
								offset += 50;
								
								// Провера пре читања brJedan
								if (ulaz.length() < offset + 2) {
									logger.warn("RUPTELA [{}]: Недостатак података за brJedan (проширени, запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									obradaUspesnaExtended = false;
									break;
								}
								int brJedan;
								try {
									brJedan = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
									logger.error("RUPTELA [{}]: Грешка парсирања brJedan (проширени, запис {}/{}): {}", 
									            clientId, brZapisa + 1, ukZapisa, e.getMessage());
									obradaUspesnaExtended = false;
									break;
								}
								offset += 2;
								offset += brJedan * 6;
								
								// Провера пре читања brDva
								if (ulaz.length() < offset + 2) {
									logger.warn("RUPTELA [{}]: Недостатак података за brDva (проширени, запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									obradaUspesnaExtended = false;
									break;
								}
								int brDva;
								try {
									brDva = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
									logger.error("RUPTELA [{}]: Грешка парсирања brDva (проширени, запис {}/{}): {}", 
									            clientId, brZapisa + 1, ukZapisa, e.getMessage());
									obradaUspesnaExtended = false;
									break;
								}
								offset += 2;
								offset += brDva * 8;
								
								// Провера пре читања brCetiri
								if (ulaz.length() < offset + 2) {
									logger.warn("RUPTELA [{}]: Недостатак података за brCetiri (проширени, запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									obradaUspesnaExtended = false;
									break;
								}
								int brCetiri;
								try {
									brCetiri = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
									logger.error("RUPTELA [{}]: Грешка парсирања brCetiri (проширени, запис {}/{}): {}", 
									            clientId, brZapisa + 1, ukZapisa, e.getMessage());
									obradaUspesnaExtended = false;
									break;
								}
								offset += 2;
								offset += brCetiri * 12;
								
								// Провера пре читања brOsam
								if (ulaz.length() < offset + 2) {
									logger.warn("RUPTELA [{}]: Недостатак података за brOsam (проширени, запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									obradaUspesnaExtended = false;
									break;
								}
								int brOsam;
								try {
									brOsam = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								} catch (NumberFormatException | StringIndexOutOfBoundsException e) {
									logger.error("RUPTELA [{}]: Грешка парсирања brOsam (проширени, запис {}/{}): {}", 
									            clientId, brZapisa + 1, ukZapisa, e.getMessage());
									obradaUspesnaExtended = false;
									break;
								}
								offset += 2;
								offset += brOsam * 20;
								
								// Провера да ли има довољно података за цео record пре позива protocol handler-а
								if (ulaz.length() < offset) {
									logger.warn("RUPTELA [{}]: Недостатак података за цео проширени record (запис {}/{})", 
									            clientId, brZapisa + 1, ukZapisa);
									obradaUspesnaExtended = false;
									break;
								}
								
								// PROTOCOL HANDLER - poziva se IDENTIČNO!
								JavljanjeObd javljanjeObd = server.rProtokol.vratiExtended(
									0, objekat, ulaz.substring(pocetak, offset)
								);
								
								// Провера да ли protocol handler врати null
								if (javljanjeObd == null) {
									logger.warn("RUPTELA [{}]: Protocol handler вратио null за проширени запис {}/{}", 
									            clientId, brZapisa + 1, ukZapisa);
									brZapisa++;
									continue; // Прескочи овај запис
								}
								
								// ═══════════════════════════════════════════════════════════
								// СПАЈАЊЕ OBD ПОДАТАКА - ОРИГИНАЛНА ЛОГИКА (НЕПРОМЕЊЕНО!)
								// ═══════════════════════════════════════════════════════════
								
								if (drugi <= prvi) {
									if (drugi == 0) {
										// Прво јављање
										prvo = javljanjeObd.getJavljanje();
										prvoObd = javljanjeObd.getObd();
										
										// Провера да ли javljanje није null
										if (prvo == null) {
											logger.warn("RUPTELA [{}]: Javljanje је null за проширени запис {}/{} (drugi=0)", 
											            clientId, brZapisa + 1, ukZapisa);
											brZapisa++;
											continue; // Прескочи овај запис
										}
										
										// OBRADA I SNIMANJE - poziva se IDENTIČNO!
										obradaJavljanja(prvo, prvoObd);
										
									} else {
										// Накнадни OBD подаци - спајање (НЕПРОМЕЊЕНО!)
										Obd trenutniObd = javljanjeObd.getObd();
										
										// Провера да ли OBD није null пре приступа пољима
										if (trenutniObd == null) {
											logger.warn("RUPTELA [{}]: OBD је null за проширени запис {}/{} (drugi={})", 
											            clientId, brZapisa + 1, ukZapisa, drugi);
											brZapisa++;
											continue; // Прескочи овај запис
										}
										
										if (prvoObd == null) {
											prvoObd = trenutniObd;
										} else {
											// Спајање свих OBD поља (НЕПРОМЕЊЕНО!)
											if (trenutniObd.getAkumulator() != 0.0f) {
												prvoObd.setAkumulator(trenutniObd.getAkumulator());
											}
											if (trenutniObd.getGas() != 0.0f) {
												prvoObd.setGas(trenutniObd.getGas());
											}
											if (trenutniObd.getGreske() != "") {
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
							}
							
							// ═══════════════════════════════════════════════════════════
							// ВЕРИФИКАЦИЈА AVL DATA PACKET CRC-16 И NUMBER OF RECORDS (EXTENDED)
							// ═══════════════════════════════════════════════════════════
							
							// Провера да ли има довољно података за Number of records (1 byte) + CRC-16 (2 bytes)
							if (ulaz.length() < offset + 6) {
								logger.warn("RUPTELA [{}]: Недостатак података за Number of records и CRC-16 на крају пакета (Extended)", 
								            clientId);
								obradaUspesnaExtended = false;
							} else if (obradaUspesnaExtended) {
								// Провера Number of records на крају пакета
								int numRecordsEnd = -1;
								try {
									numRecordsEnd = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
								} catch (NumberFormatException e) {
									logger.error("RUPTELA [{}]: Грешка парсирања Number of records на крају пакета (Extended)", clientId, e);
									obradaUspesnaExtended = false;
								}
								
								if (obradaUspesnaExtended && numRecordsEnd != ukZapisa) {
									logger.warn("RUPTELA [{}]: Number of records се не поклапа (Extended, почетак: {}, крај: {})", 
									            clientId, ukZapisa, numRecordsEnd);
									obradaUspesnaExtended = false;
								}
								
								// Верификација CRC-16 за AVL Data Packet (Extended)
								if (obradaUspesnaExtended) {
									int avlDataStart = tcpWrapperOffset; // Почетак AVL Data Packet-а (после TCP Packet Wrapper или preamble)
									
									// Израчунај CRC за AVL Data Packet (од IMEI до Number of records на крају, без CRC-16)
									byte[] avlDataBytes = hexStringToByteArray(ulaz.substring(avlDataStart, offset + 2));
									int calculatedAvlCrc = calculateCrc16Kermit(avlDataBytes);
									
									// Читање CRC-16 из пакета (после Number of records на крају, 2 bytes = 4 hex chars)
									int receivedAvlCrc = -1;
									try {
										receivedAvlCrc = Integer.parseInt(ulaz.substring(offset + 2, offset + 6), 16);
									} catch (NumberFormatException e) {
										logger.error("RUPTELA [{}]: Грешка парсирања AVL Data Packet CRC-16 (Extended)", clientId, e);
										obradaUspesnaExtended = false;
									}
									
									if (obradaUspesnaExtended && calculatedAvlCrc != receivedAvlCrc) {
										logger.warn("RUPTELA [{}]: AVL Data Packet CRC-16 невалидан (Extended, израчунато: 0x{}, примљено: 0x{})", 
										            clientId, String.format("%04X", calculatedAvlCrc), String.format("%04X", receivedAvlCrc));
										obradaUspesnaExtended = false;
									} else if (obradaUspesnaExtended) {
										logger.trace("RUPTELA [{}]: AVL Data Packet CRC-16 валидан (Extended, 0x{})", clientId, String.format("%04X", calculatedAvlCrc));
									}
								}
							}
							
							// KRITIЧНО: ACK se šalje TEK NAKON uspešne obrade И верификације!
							if (obradaUspesnaExtended) {
								try {
									out.write(odg);
									out.flush();
									logger.trace("RUPTELA [{}]: ACK poslat за {} extended zapisa (обрада успешна, CRC валидан)", clientId, ukZapisa);
								} catch (IOException e) {
									logger.error("RUPTELA [{}]: Greška slanja ACK", clientId, e);
									break;
								}
							} else {
								logger.warn("RUPTELA [{}]: ACK НИЈЕ послат - обрада није успешна или CRC невалидан (обрађено {}/{} extended записа)", 
								            clientId, brZapisa, ukZapisa);
								// Пошаљи NACK ако је обрада неуспешна због CRC или Number of records проблема
								try {
									out.write(nack);
									out.flush();
									logger.debug("RUPTELA [{}]: NACK послат - обрада неуспешна или CRC невалидан (Extended)", clientId);
								} catch (IOException e) {
									logger.error("RUPTELA [{}]: Грешка слања NACK", clientId, e);
								}
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
	
	/**
	 * NOVO: Конвертује hex string у byte array
	 * 
	 * @param hex Hex string (парен број карактера)
	 * @return Byte array
	 */
	private static byte[] hexStringToByteArray(String hex) {
		int len = hex.length();
		byte[] data = new byte[len / 2];
		for (int i = 0; i < len; i += 2) {
			data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
		}
		return data;
	}
}