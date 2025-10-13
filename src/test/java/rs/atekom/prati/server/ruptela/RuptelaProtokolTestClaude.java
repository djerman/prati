package rs.atekom.prati.server.ruptela;

import static org.junit.Assert.*;

import java.sql.Timestamp;

import org.junit.Before;
import org.junit.Test;

import pratiBaza.tabele.Javljanja;
import pratiBaza.tabele.Obd;
import pratiBaza.tabele.Objekti;
import rs.atekom.prati.server.JavljanjeObd;

/**
 * Unit testovi za RuptelaProtokol parser.
 * 
 * <p>Testira parsiranje STANDARD i EXTENDED Ruptela paketa.</p>
 * 
 * @author Atekom
 * @version 1.0
 */
public class RuptelaProtokolTestClaude {
	
	private RuptelaProtokol protokol;
	private Objekti testObjekat;
	
	@Before
	public void setUp() {
		protokol = new RuptelaProtokol();
		
		// Mock objekat
		testObjekat = new Objekti();
		testObjekat.setId(1L);
		testObjekat.setOznaka("TEST-001");
	}
	
	// ═══════════════════════════════════════════════════════════
	// ТЕСТОВИ ЗА СТАНДАРДНИ ПРОТОКОЛ (Command 0x01) ОВО НЕ РАДИИИИИИИИ
	// ═══════════════════════════════════════════════════════════
	
	@Test
	public void testStandardProtocol_BasicGPSData() {
	    // Given: Simuliran STANDARD Ruptela paket
	    // Timestamp: 2023-10-10 12:00:00 (Unix: 1696939200 = 0x652D3760)
	    // Lon: 20.4612° E (204612000 = 0x0C33D420)
	    // Lat: 44.8125° N (448125000 = 0x1ABB7858)
	    // Speed: 80 km/h
	    // Altitude: 120m
	    // Angle: 180°
	    
	    String hexPacket = 
	        "65253CC0" +  // Timestamp: 4 bytes = 8 hex chars
	        "00" +        // Timestamp ext: 1 byte = 2 hex chars
	        "00" +        // Priority: 1 byte = 2 hex chars
	        "0C3221A0" +  // Longitude: 4 bytes = 8 hex chars 
	        "1AB5D848" +  // Latitude: 4 bytes = 8 hex chars 
	        "04B0" +      // Altitude: 2 bytes = 4 hex chars (1200 = 120.0m * 10)
	        "4650" +      // Angle: 2 bytes = 4 hex chars (18000 = 180.0° * 100)
	        "0C" +        // Satellites: 1 byte = 2 hex chars (12)
	        "0050" +      // Speed: 2 bytes = 4 hex chars (80 km/h)
	        "0A" +        // HDOP: 1 byte = 2 hex chars (10)
	        "00" +        // Event ID: 1 byte = 2 hex chars (0)
	        "00" +        // 1-byte IO count: 1 byte = 2 hex chars (0)
	        "00" +        // 2-byte IO count: 1 byte = 2 hex chars (0)
	        "00" +        // 4-byte IO count: 1 byte = 2 hex chars (0)
	        "00";         // 8-byte IO count: 1 byte = 2 hex chars (0)
	    
	    // When
	    JavljanjeObd result = protokol.vratiJavljanje(0, testObjekat, hexPacket);
	    
	    // Then
	    assertNotNull("Javljanje ne sme biti null", result);
	    assertNotNull("Javljanje objekat ne sme biti null", result.getJavljanje());
	    
	    assertEquals("Longitude", 20.4612, result.getJavljanje().getLon(), 0.0001);
	    assertEquals("Latitude", 44.8125, result.getJavljanje().getLat(), 0.0001);
	    assertEquals("Brzina", 80, result.getJavljanje().getBrzina());
	    assertEquals("Visina", 120.0f, result.getJavljanje().getVisina(), 0.1f);
	    assertEquals("Pravac", 180.0f, result.getJavljanje().getPravac(), 0.1f);
	    
	    Timestamp expectedTime = new Timestamp(1696939200000L);
	    assertEquals("Datum vreme", expectedTime, result.getJavljanje().getDatumVreme());
	}
	
	@Test
	public void testStandardProtocol_WithIgnitionOn() {
		// Given: Paket sa kontaktom uključenim (IO 5 = 1)
		String hexPacket = 
			"652D3760" +  // Timestamp
			"00" +        // Timestamp ext
			"00" +        // Priority
			"0C3221A0" +  // Longitude
			"1AB5D848" +  // Latitude
			"04B0" +      // Altitude
			"4650" +      // Angle
			"0C" +        // Satellites
			"0050" +      // Speed: 80 km/h
			"0A" +        // HDOP
			"00" +        // Event ID: 0
			"01" +        // 1-byte IO count: 1
			"05" + "01" + // IO ID 5 (Ignition) = 1 (ON)
			"00" +        // 2-byte IO count: 0
			"00" +        // 4-byte IO count: 0
			"00";         // 8-byte IO count: 0
		
		// When
		JavljanjeObd result = protokol.vratiJavljanje(0, testObjekat, hexPacket);
		
		// Then
		assertNotNull(result);
		assertTrue("Kontakt treba da bude uključen", result.getJavljanje().isKontakt());
	}
	
	@Test
	public void testStandardProtocol_WithIgnitionOff() {
		// Given: Paket sa kontaktom isključenim (IO 5 = 0)
		String hexPacket = 
			"652D3760" +  // Timestamp
			"00" + "00" + // Timestamp ext + Priority
			"0C3221A0" + "1AB5D848" + // Lon + Lat
			"04B0" + "4650" + "0C" + "0000" + "0A" + "00" +
			"01" +        // 1-byte IO count: 1
			"05" + "00" + // IO ID 5 (Ignition) = 0 (OFF)
			"00" + "00" + "00";
		
		// When
		JavljanjeObd result = protokol.vratiJavljanje(0, testObjekat, hexPacket);
		
		// Then
		assertNotNull(result);
		assertFalse("Kontakt treba da bude isključen", result.getJavljanje().isKontakt());
	}
	
	@Test
	public void testStandardProtocol_WithOBDData() {
		// Given: Paket sa OBD podacima
		// IO 94 (2-byte): RPM = 2000 (8000 * 0.25)
		// IO 207 (1-byte): Fuel level = 75% (187.5 * 0.4)
		
		String hexPacket = 
			"652D3760" +  // Timestamp
			"00" + "00" + // Timestamp ext + Priority
			"0C3221A0" + "1AB5D848" + // Lon + Lat
			"04B0" + "4650" + "0C" + "0050" + "0A" + "00" +
			"01" +        // 1-byte IO count: 1
			"CF" + "BB" + // IO 207 (fuel %) = 187 → 74.8%
			"01" +        // 2-byte IO count: 1
			"5E" + "1F40" + // IO 94 (RPM) = 8000 → 2000 RPM
			"00" + "00";
		
		// When
		JavljanjeObd result = protokol.vratiJavljanje(0, testObjekat, hexPacket);
		
		// Then
		assertNotNull(result);
		assertNotNull("OBD podaci ne smeju biti null", result.getObd());
		
		assertEquals("RPM", 2000, result.getObd().getRpm());
		assertEquals("Nivo goriva", 74.8f, result.getObd().getNivoGoriva(), 0.1f);
	}
	
	@Test
	public void testStandardProtocol_WithVirtualOdometer() {
		// Given: Paket sa virtuelnim odometrom
		// IO 65 (4-byte): Virtual ODO = 123456m → 123.456 km
		
		String hexPacket = 
			"652D3760" +  // Timestamp
			"00" + "00" + // Timestamp ext + Priority
			"0C3221A0" + "1AB5D848" + // Lon + Lat
			"04B0" + "4650" + "0C" + "0050" + "0A" + "00" +
			"00" +        // 1-byte IO count: 0
			"00" +        // 2-byte IO count: 0
			"01" +        // 4-byte IO count: 1
			"41" + "0001E240" + // IO 65 (Virtual ODO) = 123456m → 123.456km
			"00";
		
		// When
		JavljanjeObd result = protokol.vratiJavljanje(0, testObjekat, hexPacket);
		
		// Then
		assertNotNull(result);
		assertEquals("Virtual odometar", 123.456f, result.getJavljanje().getVirtualOdo(), 0.001f);
	}
	
	// ═══════════════════════════════════════════════════════════
	// ТЕСТОВИ ЗА ПРОШИРЕНИ ПРОТОКОЛ (Command 0x44)
	// ═══════════════════════════════════════════════════════════
	
	@Test
	public void testExtendedProtocol_BasicStructure() {
		// Given: EXTENDED paket (ima dodatni Record Extension byte)
		String hexPacket = 
			"652D3760" +  // Timestamp
			"00" +        // Timestamp ext
			"00" +        // Record extension (EXTENDED specific!)
			"00" +        // Priority
			"0C3221A0" +  // Longitude
			"1AB5D848" +  // Latitude
			"04B0" +      // Altitude
			"4650" +      // Angle
			"0C" +        // Satellites
			"0050" +      // Speed: 80 km/h
			"0A" +        // HDOP
			"0000" +      // Event ID: 0 (2 bytes u Extended!)
			"00" +        // 1-byte IO count: 0
			"00" +        // 2-byte IO count: 0
			"00" +        // 4-byte IO count: 0
			"00";         // 8-byte IO count: 0
		
		// When
		JavljanjeObd result = protokol.vratiExtended(0, testObjekat, hexPacket);
		
		// Then
		assertNotNull("Extended javljanje ne sme biti null", result);
		assertNotNull(result.getJavljanje());
		
		assertEquals("Latitude", 44.8125, result.getJavljanje().getLat(), 0.0001);
		assertEquals("Longitude", 20.4612, result.getJavljanje().getLon(), 0.0001);
		assertEquals("Brzina", 80, result.getJavljanje().getBrzina());
	}
	
	@Test
	public void testExtendedProtocol_With2ByteIOIDs() {
		// Given: EXTENDED paket sa 2-byte IO ID-jevima
		// IO 0x0005 (Ignition) = 1
		
		String hexPacket = 
			"652D3760" +  // Timestamp
			"00" + "00" + // Timestamp ext + Record ext
			"00" +        // Priority
			"0C3221A0" + "1AB5D848" + // Lon + Lat
			"04B0" + "4650" + "0C" + "0050" + "0A" +
			"0000" +      // Event ID (2 bytes)
			"01" +        // 1-byte IO count: 1
			"0005" + "01" + // IO 0x0005 (Ignition) = 1 (2-byte ID!)
			"00" + "00" + "00";
		
		// When
		JavljanjeObd result = protokol.vratiExtended(0, testObjekat, hexPacket);
		
		// Then
		assertNotNull(result);
		assertTrue("Kontakt treba da bude uključen (Extended)", result.getJavljanje().isKontakt());
	}
	
	// ═══════════════════════════════════════════════════════════
	// EDGE CASE ТЕСТОВИ
	// ═══════════════════════════════════════════════════════════
	
	@Test
	public void testNullPacket() {
		// Given: null paket
		
		// When
		JavljanjeObd result = protokol.vratiJavljanje(0, testObjekat, null);
		
		// Then
		assertNull("Result treba da bude null za null paket", result);
	}
	
	@Test
	public void testEmptyPacket() {
		// Given: prazan paket
		
		// When
		JavljanjeObd result = protokol.vratiJavljanje(0, testObjekat, "");
		
		// Then
		assertNull("Result treba da bude null za prazan paket", result);
	}
	
	@Test
	public void testNullObjekat() {
		// Given: validan paket ali null objekat
		String hexPacket = "652D376000001ABB78580C33D38004B046500C00500A0000000000";
		
		// When
		JavljanjeObd result = protokol.vratiJavljanje(0, null, hexPacket);
		
		// Then
		assertNull("Result treba da bude null za null objekat", result);
	}
	
	@Test
	public void testInvalidHexData() {
		// Given: nevažeći hex (kratak paket)
		String shortPacket = "652D3760";
		
		// When
		JavljanjeObd result = protokol.vratiJavljanje(0, testObjekat, shortPacket);
		
		// Then
		assertNull("Result treba da bude null za nevažeći paket", result);
	}
	
	@Test
	public void testEmptyOBD_ReturnsNull() {
		// Given: Paket bez OBD podataka
		String hexPacket = 
			"652D3760" +  // Timestamp
			"00" + "00" + // Timestamp ext + Priority
			"0C3221A0" + "0C33D380" + // Lon + Lat
			"04B0" + "4650" + "0C" + "0050" + "0A" + "00" +
			"00" +        // Nema IO elemenata
			"00" + "00" + "00";
		
		// When
		JavljanjeObd result = protokol.vratiJavljanje(0, testObjekat, hexPacket);
		
		// Then
		assertNotNull("Javljanje ne sme biti null", result);
		assertNull("OBD treba da bude null kad nema podataka", result.getObd());
	}
	
	@Test
	public void testNegativeCoordinates() {
	    String hexPacket = 
	        "652D3760" +  // Timestamp: 4B
	        "00" +        // Timestamp ext: 1B
	        "00" +        // Priority: 1B
	        "F3CDDE60" +  // Longitude: -204,612,000 → -20.4612°
	        "E54A27B8" +  // Latitude: -448,125,000 → -44.8125°
	        "04B0" +      // Altitude: 2B
	        "4650" +      // Angle: 2B
	        "0C" +        // Satellites: 1B
	        "0050" +      // Speed: 2B
	        "0A" +        // HDOP: 1B
	        "00" +        // Event ID: 1B
	        "00" +        // 1-byte IO count
	        "00" +        // 2-byte IO count
	        "00" +        // 4-byte IO count
	        "00";         // 8-byte IO count
	    
	    // When
	    JavljanjeObd result = protokol.vratiJavljanje(0, testObjekat, hexPacket);
	    
	    // Then
	    assertNotNull(result);
	    assertEquals("Negativna longitude", -20.4612, result.getJavljanje().getLon(), 0.0001);
	    assertEquals("Negativna latitude", -44.8125, result.getJavljanje().getLat(), 0.0001);
	}
	
	@Test
	public void testHighSpeed() {
		// Given: Velika brzina (250 km/h)
		String hexPacket = 
			"652D3760" +  // Timestamp
			"00" + "00" + // Timestamp ext + Priority
			"0C3221A0" + "1AB5D848" + // Lon + Lat
			"04B0" + "4650" + "0C" +
			"00FA" +      // Speed: 250 km/h (0x00FA = 250)
			"0A" + "00" +
			"00" + "00" + "00" + "00";
		
		// When
		JavljanjeObd result = protokol.vratiJavljanje(0, testObjekat, hexPacket);
		
		// Then
		assertNotNull(result);
		assertEquals("Velika brzina", 250, result.getJavljanje().getBrzina());
	}
	
	@Test
	public void testMultipleIOElements() {
		// Given: Paket sa više IO elemenata različitih veličina
		String hexPacket = 
			"652D3760" +  // Timestamp
			"00" + "00" + // Timestamp ext + Priority
			"0C3221A0" + "1AB5D848" + // Lon + Lat
			"04B0" + "4650" + "0C" + "0050" + "0A" + "00" +
			"02" +        // 1-byte IO count: 2
			"05" + "01" + // IO 5 (Ignition) = 1
			"CF" + "64" + // IO 207 (Fuel %) = 100 → 40%
			"01" +        // 2-byte IO count: 1
			"5E" + "0FA0" + // IO 94 (RPM) = 4000 → 1000 RPM
			"01" +        // 4-byte IO count: 1
			"41" + "000186A0" + // IO 65 (ODO) = 100000m → 100km
			"00";
		
		// When
		JavljanjeObd result = protokol.vratiJavljanje(0, testObjekat, hexPacket);
		
		// Then
		assertNotNull(result);
		assertTrue("Kontakt", result.getJavljanje().isKontakt());
		assertEquals("Virtual ODO", 100.0f, result.getJavljanje().getVirtualOdo(), 0.1f);
		
		assertNotNull("OBD podaci", result.getObd());
		assertEquals("RPM", 1000, result.getObd().getRpm());
		assertEquals("Fuel", 40.0f, result.getObd().getNivoGoriva(), 0.1f);
	}
	
	@Test
	public void debugNegativeCoordinates() {
	    String hexPacket = 
	        "652D3760" +  // Timestamp
	        "00" +        // Timestamp ext
	        "00" +        // Priority
	        "F3CDDE60" +  // Longitude hex (8 chars)
	        "E54A27B8" +  // Latitude hex (8 chars)
	        "04B0" + "4650" + "0C" + "0050" + "0A" + "00" +
	        "00" + "00" + "00" + "00";
	    
	    // Manual parse za debug
	    int offset = 0;
	    offset += 8;  // Timestamp (4 bytes = 8 hex chars)
	    offset += 2;  // Timestamp ext
	    offset += 2;  // Priority
	    
	    System.out.println("Offset pre lon: " + offset);
	    String lonHex = hexPacket.substring(offset, offset + 8);
	    System.out.println("Lon hex: " + lonHex);
	    
	    long lonUnsigned = Long.parseUnsignedLong(lonHex, 16);
	    System.out.println("Lon unsigned: " + lonUnsigned);
	    
	    int lonSigned = (int) lonUnsigned;
	    System.out.println("Lon signed: " + lonSigned);
	    
	    double lon = (double) lonSigned / 10000000.0;
	    System.out.println("Lon degrees: " + lon);
	    
	    offset += 8;
	    System.out.println("\nOffset pre lat: " + offset);
	    String latHex = hexPacket.substring(offset, offset + 8);
	    System.out.println("Lat hex: " + latHex);
	    
	    long latUnsigned = Long.parseUnsignedLong(latHex, 16);
	    System.out.println("Lat unsigned: " + latUnsigned);
	    
	    int latSigned = (int) latUnsigned;
	    System.out.println("Lat signed: " + latSigned);
	    
	    double lat = (double) latSigned / 10000000.0;
	    System.out.println("Lat degrees: " + lat);
	    
	    // Сада позови прави parser
	    JavljanjeObd result = protokol.vratiJavljanje(0, testObjekat, hexPacket);
	    
	    System.out.println("\nParser резултат:");
	    System.out.println("Lon: " + result.getJavljanje().getLon());
	    System.out.println("Lat: " + result.getJavljanje().getLat());
	}
	
	@Test
	public void testRealRuptelaMessage_WithParser() {
	    // Given: Реална порука из production лога
	    String realHex = 
	        "00540003167D76748C5701000168E7236E00000BEED8E21B4C95260380484410001009090A0501020003001C01AD018600870082108F000400071D36B81E0F1816004217003D8B000289000083000001410166C7AB0003F2234D00000BEEE1161B4CC73603B5481210000C07090A0501020003001C01AD0186008700820C8F000400071D37001E0F1316004217003D8B000189000083000001410166C71F008079";
	    
	    System.out.println("\n========================================");
	    System.out.println("  PARSER TEST - РЕАЛНА ПОРУКА");
	    System.out.println("========================================\n");
	    
	    // Идентификујмо тип протокола
	    int offset = 8; // Прескочи Data Length + CRC
	    offset += 16; // Прескочи IMEI
	    
	    String commandHex = realHex.substring(offset, offset + 2);
	    int command = Integer.parseInt(commandHex, 16);
	    offset += 2;
	    
	    offset += 2; // Прескочи Records Left
	    
	    String numRecordsHex = realHex.substring(offset, offset + 2);
	    int numRecords = Integer.parseInt(numRecordsHex, 16);
	    offset += 2;
	    
	    System.out.println("Command type: 0x" + commandHex + " (" + 
	                       (command == 0x01 ? "STANDARD" : command == 0x44 ? "EXTENDED" : "UNKNOWN") + ")");
	    System.out.println("Number of records: " + numRecords + "\n");
	    
	    // Екстрахујмо сваки record и парсирајмо га
	    for (int i = 1; i <= numRecords; i++) {
	        System.out.println("--- Парсирање Record #" + i + " ---");
	        
	        // Пронађи почетак и крај овог record-а
	        // Ово је комплексно јер морамо да пратимо IO counts
	        // За сада, ручно екстраховујемо први record
	        
	        //int recordStart = offset;
	        
	        // Ово је апроксимација - требамо прецизно одредити границе record-а
	        // Засад узимамо први record ручно
	        
	        if (i == 1) {
	            // Први record почиње на offset-у 28 (после header-а)
	            // Морамо израчунати тачну дужину на основу IO counts
	            
	            // За тест, узимамо фиксну дужину првог record-а
	            // (Ово ће анализа изнад показати)
	            
	            // Привремено: узми све до краја као један record
	            String recordHex = realHex.substring(28, realHex.length() - 4); // Без footer CRC
	            
	            System.out.println("Record hex (прва 100 chars): " + recordHex.substring(0, Math.min(100, recordHex.length())) + "...");
	            
	            // Парсирај record
	            try {
	                JavljanjeObd result;
	                if (command == 0x01) {
	                    result = protokol.vratiJavljanje(0, testObjekat, recordHex);
	                } else if (command == 0x44) {
	                    result = protokol.vratiExtended(0, testObjekat, recordHex);
	                } else {
	                    System.err.println("Непознат command type!");
	                    continue;
	                }
	                
	                if (result != null && result.getJavljanje() != null) {
	                    Javljanja j = result.getJavljanje();
	                    System.out.println("\nУспешно парсирано:");
	                    System.out.println("  Datum/vreme: " + j.getDatumVreme());
	                    System.out.println("  Latitude: " + j.getLat() + "°");
	                    System.out.println("  Longitude: " + j.getLon() + "°");
	                    System.out.println("  Brzina: " + j.getBrzina() + " km/h");
	                    System.out.println("  Visina: " + j.getVisina() + " m");
	                    System.out.println("  Pravac: " + j.getPravac() + "°");
	                    System.out.println("  Kontakt: " + j.isKontakt());
	                    System.out.println("  Virtual ODO: " + j.getVirtualOdo() + " km");
	                    
	                    if (result.getObd() != null) {
	                        Obd  obd = result.getObd();
	                        System.out.println("\n  OBD podaci:");
	                        System.out.println("    RPM: " + obd.getRpm());
	                        System.out.println("    Temperatura: " + obd.getTemperatura() + "°C");
	                        System.out.println("    Nivo goriva: " + obd.getNivoGoriva() + "%");
	                        System.out.println("    Akumulator: " + obd.getAkumulator() + " V");
	                    } else {
	                        System.out.println("\n  OBD podaci: NEMA");
	                    }
	                    
	                    assertNotNull("Javljanje ne sme biti null", result.getJavljanje());
	                    
	                } else {
	                    System.err.println("Parser vratio NULL!");
	                    fail("Parser nije uspeo da parsira realnu poruku");
	                }
	                
	            } catch (Exception e) {
	                System.err.println("ГРЕШКА при парсирању: " + e.getMessage());
	                e.printStackTrace();
	                fail("Greška pri parsiranju: " + e.getMessage());
	            }
	        }
	    }
	    
	    System.out.println("\n========================================");
	}
	
	@Test
	public void testAnalyzeRealRuptelaMessage() {
	    // Given: Реална порука из production лога
	    String realHex = 
	        "00540003167D76748C5701000168E7236E00000BEED8E21B4C95260380484410001009090A0501020003001C01AD018600870082108F000400071D36B81E0F1816004217003D8B000289000083000001410166C7AB0003F2234D00000BEEE1161B4CC73603B5481210000C07090A0501020003001C01AD0186008700820C8F000400071D37001E0F1316004217003D8B000189000083000001410166C71F008079";
	    
	    System.out.println("========================================");
	    System.out.println("  АНАЛИЗА РЕАЛНЕ RUPTELA ПОРУКЕ");
	    System.out.println("========================================\n");
	    
	    System.out.println("Hex length: " + realHex.length() + " chars (" + (realHex.length() / 2) + " bytes)");
	    System.out.println("Raw hex: " + realHex.substring(0, Math.min(80, realHex.length())) + "...\n");
	    
	    int offset = 0;
	    
	    // ==== PACKET WRAPPER (Ruptela TCP packet structure) ====
	    System.out.println("--- PACKET HEADER ---");
	    
	    // Data Length (2 bytes) - excluding CRC
	    String dataLenHex = realHex.substring(offset, offset + 4);
	    int dataLength = Integer.parseInt(dataLenHex, 16);
	    System.out.println("Data Length: 0x" + dataLenHex + " = " + dataLength + " bytes");
	    offset += 4;
	    
	    // CRC-16 (2 bytes)
	    String crcHex = realHex.substring(offset, offset + 4);
	    System.out.println("CRC-16: 0x" + crcHex);
	    offset += 4;
	    
	    // ==== AVL DATA PACKET ====
	    System.out.println("\n--- AVL DATA ---");
	    
	    // IMEI (8 bytes)
	    String imeiHex = realHex.substring(offset, offset + 16);
	    long imei = Long.parseUnsignedLong(imeiHex, 16);
	    System.out.println("IMEI: " + imei + " (0x" + imeiHex + ")");
	    offset += 16;
	    
	    // Command Type (1 byte)
	    String commandHex = realHex.substring(offset, offset + 2);
	    int command = Integer.parseInt(commandHex, 16);
	    System.out.println("Command: 0x" + commandHex + " = " + command + 
	                       (command == 0x01 ? " (STANDARD)" : command == 0x44 ? " (EXTENDED)" : " (UNKNOWN)"));
	    offset += 2;
	    
	    // Records Left (1 byte) - should be 0 for most packets
	    String recordsLeftHex = realHex.substring(offset, offset + 2);
	    int recordsLeft = Integer.parseInt(recordsLeftHex, 16);
	    System.out.println("Records Left: " + recordsLeft);
	    offset += 2;
	    
	    // Number of Records (1 byte)
	    String numRecordsHex = realHex.substring(offset, offset + 2);
	    int numRecords = Integer.parseInt(numRecordsHex, 16);
	    System.out.println("Number of Records: " + numRecords);
	    offset += 2;
	    
	    System.out.println("\n========================================");
	    System.out.println("  ПАРСИРАЊЕ " + numRecords + " RECORD(S)");
	    System.out.println("========================================");
	    
	    // ==== PARSE EACH RECORD ====
	    for (int recordNum = 1; recordNum <= numRecords; recordNum++) {
	        System.out.println("\n--- RECORD #" + recordNum + " (offset: " + offset + ") ---");
	        
	        int recordStart = offset;
	        
	        try {
	            // Timestamp (4 bytes)
	            String timestampHex = realHex.substring(offset, offset + 8);
	            long timestamp = Long.parseUnsignedLong(timestampHex, 16);
	            java.util.Date date = new java.util.Date(timestamp * 1000);
	            System.out.println("Timestamp: " + timestamp + " = " + date);
	            offset += 8;
	            
	            // Timestamp extension (1 byte)
	            offset += 2;
	            
	            // Record extension (1 byte) - само за Extended
	            if (command == 0x44) {
	                offset += 2;
	            }
	            
	            // Priority (1 byte)
	            String priorityHex = realHex.substring(offset, offset + 2);
	            int priority = Integer.parseInt(priorityHex, 16);
	            System.out.println("Priority: " + priority);
	            offset += 2;
	            
	            // Longitude (4 bytes)
	            String lonHex = realHex.substring(offset, offset + 8);
	            long lonUnsigned = Long.parseUnsignedLong(lonHex, 16);
	            if ((lonUnsigned & 0x80000000L) != 0) {
	                lonUnsigned -= 0x100000000L;
	            }
	            double lon = lonUnsigned / 10000000.0;
	            System.out.println("Longitude: " + lon + "° (0x" + lonHex + ")");
	            offset += 8;
	            
	            // Latitude (4 bytes)
	            String latHex = realHex.substring(offset, offset + 8);
	            long latUnsigned = Long.parseUnsignedLong(latHex, 16);
	            if ((latUnsigned & 0x80000000L) != 0) {
	                latUnsigned -= 0x100000000L;
	            }
	            double lat = latUnsigned / 10000000.0;
	            System.out.println("Latitude: " + lat + "° (0x" + latHex + ")");
	            offset += 8;
	            
	            // Altitude (2 bytes)
	            String altHex = realHex.substring(offset, offset + 4);
	            int altitude = Integer.parseInt(altHex, 16) / 10;
	            System.out.println("Altitude: " + altitude + " m");
	            offset += 4;
	            
	            // Angle (2 bytes)
	            String angleHex = realHex.substring(offset, offset + 4);
	            float angle = Integer.parseInt(angleHex, 16) / 100.0f;
	            System.out.println("Angle: " + angle + "°");
	            offset += 4;
	            
	            // Satellites (1 byte)
	            String satHex = realHex.substring(offset, offset + 2);
	            int satellites = Integer.parseInt(satHex, 16);
	            System.out.println("Satellites: " + satellites);
	            offset += 2;
	            
	            // Speed (2 bytes)
	            String speedHex = realHex.substring(offset, offset + 4);
	            int speed = Integer.parseInt(speedHex, 16);
	            System.out.println("Speed: " + speed + " km/h");
	            offset += 4;
	            
	            // HDOP (1 byte)
	            String hdopHex = realHex.substring(offset, offset + 2);
	            int hdop = Integer.parseInt(hdopHex, 16);
	            System.out.println("HDOP: " + hdop);
	            offset += 2;
	            
	            // Event ID (1 или 2 bytes зависно од протокола)
	            String eventIdHex;
	            int eventId;
	            if (command == 0x44) {
	                // Extended: 2 bytes
	                eventIdHex = realHex.substring(offset, offset + 4);
	                eventId = Integer.parseInt(eventIdHex, 16);
	                offset += 4;
	            } else {
	                // Standard: 1 byte
	                eventIdHex = realHex.substring(offset, offset + 2);
	                eventId = Integer.parseInt(eventIdHex, 16);
	                offset += 2;
	            }
	            System.out.println("Event ID: " + eventId + " (0x" + eventIdHex + ")");
	            
	            // ==== IO ELEMENTS ====
	            System.out.println("\n  IO Elements:");
	            
	            // 1-byte IO
	            String io1CountHex = realHex.substring(offset, offset + 2);
	            int io1Count = Integer.parseInt(io1CountHex, 16);
	            offset += 2;
	            System.out.println("  1-byte IO count: " + io1Count);
	            
	            for (int i = 0; i < io1Count; i++) {
	                int ioIdLen = (command == 0x44) ? 4 : 2; // Extended има 2-byte ID
	                String ioIdHex = realHex.substring(offset, offset + ioIdLen);
	                int ioId = Integer.parseInt(ioIdHex, 16);
	                offset += ioIdLen;
	                
	                String ioValHex = realHex.substring(offset, offset + 2);
	                int ioVal = Integer.parseInt(ioValHex, 16);
	                offset += 2;
	                
	                System.out.println("    IO " + ioId + " = " + ioVal + " (0x" + ioValHex + ")");
	            }
	            
	            // 2-byte IO
	            String io2CountHex = realHex.substring(offset, offset + 2);
	            int io2Count = Integer.parseInt(io2CountHex, 16);
	            offset += 2;
	            System.out.println("  2-byte IO count: " + io2Count);
	            
	            for (int i = 0; i < io2Count; i++) {
	                int ioIdLen = (command == 0x44) ? 4 : 2;
	                String ioIdHex = realHex.substring(offset, offset + ioIdLen);
	                int ioId = Integer.parseInt(ioIdHex, 16);
	                offset += ioIdLen;
	                
	                String ioValHex = realHex.substring(offset, offset + 4);
	                int ioVal = Integer.parseInt(ioValHex, 16);
	                offset += 4;
	                
	                System.out.println("    IO " + ioId + " = " + ioVal + " (0x" + ioValHex + ")");
	            }
	            
	            // 4-byte IO
	            String io4CountHex = realHex.substring(offset, offset + 2);
	            int io4Count = Integer.parseInt(io4CountHex, 16);
	            offset += 2;
	            System.out.println("  4-byte IO count: " + io4Count);
	            
	            for (int i = 0; i < io4Count; i++) {
	                int ioIdLen = (command == 0x44) ? 4 : 2;
	                String ioIdHex = realHex.substring(offset, offset + ioIdLen);
	                int ioId = Integer.parseInt(ioIdHex, 16);
	                offset += ioIdLen;
	                
	                String ioValHex = realHex.substring(offset, offset + 8);
	                long ioVal = Long.parseUnsignedLong(ioValHex, 16);
	                offset += 8;
	                
	                System.out.println("    IO " + ioId + " = " + ioVal + " (0x" + ioValHex + ")");
	            }
	            
	            // 8-byte IO
	            String io8CountHex = realHex.substring(offset, offset + 2);
	            int io8Count = Integer.parseInt(io8CountHex, 16);
	            offset += 2;
	            System.out.println("  8-byte IO count: " + io8Count);
	            
	            for (int i = 0; i < io8Count; i++) {
	                int ioIdLen = (command == 0x44) ? 4 : 2;
	                offset += ioIdLen + 16; // Skip ID + 8-byte value
	            }
	            
	            int recordLength = offset - recordStart;
	            System.out.println("\nRecord length: " + recordLength + " hex chars (" + (recordLength / 2) + " bytes)");
	            
	        } catch (Exception e) {
	            System.err.println("ГРЕШКА при парсирању record-а #" + recordNum + ": " + e.getMessage());
	            e.printStackTrace();
	            break;
	        }
	    }
	    
	    // Number of records at end (1 byte) - should match beginning
	    if (offset < realHex.length()) {
	        String numRecordsEndHex = realHex.substring(offset, Math.min(offset + 2, realHex.length()));
	        try {
	            int numRecordsEnd = Integer.parseInt(numRecordsEndHex, 16);
	            System.out.println("\n--- PACKET FOOTER ---");
	            System.out.println("Number of Records (end): " + numRecordsEnd);
	            offset += 2;
	        } catch (Exception e) {
	            // Ignore
	        }
	    }
	    
	    // CRC at end
	    if (offset < realHex.length()) {
	        String crcEndHex = realHex.substring(offset, Math.min(offset + 4, realHex.length()));
	        System.out.println("CRC-16 (end): 0x" + crcEndHex);
	    }
	    
	    System.out.println("\n========================================");
	    System.out.println("Ukupno parsirao: " + offset + " hex chars");
	    System.out.println("========================================");
	}
	
	@Test
	public void verifyHexConversion() {
		System.out.println("негативне вредности...");
	    double lon = -20.4612;
	    int lonScaled = (int)(lon * 10000000);
	    
	    System.out.println("Lon scaled: " + lonScaled);
	    System.out.println("Lon hex: " + String.format("%08X", lonScaled));
	    
	    // Verify
	    String hexResult = String.format("%08X", lonScaled);
	    long verify = Long.parseLong(hexResult, 16);
	    if ((verify & 0x80000000L) != 0) {
	        verify -= 0x100000000L;
	    }
	    System.out.println("Verify: " + (verify / 10000000.0));
	    
	    double lat = -44.8125;
	    int latScaled = (int)(lat * 10000000);
	    System.out.println("Lat scaled: " + latScaled);
	    System.out.println("Lat hex: " + String.format("%08X", latScaled));
	    
	    System.out.println();
	    System.out.println("позитивне вредности...");
	    double lon2 = 20.4612;
	    int lonScaled2 = (int)(lon2 * 10000000);
	    System.out.println("Lon scaled: " + lonScaled2);
	    System.out.println("Lon hex: " + String.format("%08X", lonScaled2));

	    double lat2 = 44.8125;
	    int latScaled2 = (int)(lat2 * 10000000);
	    System.out.println("Lat scaled: " + latScaled2);
	    System.out.println("Lat hex: " + String.format("%08X", latScaled2));
	    
	    // 2023-10-10 12:00:00 UTC
	    System.out.println();
	    System.out.println("време...");
	    long timestamp = 1696939200L;
	    String hex = String.format("%08X", timestamp);
	    System.out.println("Timestamp hex: " + hex);
	    
	    // Provera vašeg hex-a
	    long yourTimestamp = Long.parseLong("652D3760", 16);
	    System.out.println("Vaš timestamp: " + new java.util.Date(yourTimestamp * 1000));
	}
	
}