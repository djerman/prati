package rs.atekom.prati.server.ruptela;

import static org.junit.Assert.*;
import org.junit.Test;
import pratiBaza.tabele.Objekti;
import rs.atekom.prati.server.JavljanjeObd;

public class RuptelaProtokolPacketTest {

    // Твоја порука – скраћена: одсечене су крајње нуле (buffer padding)
    private static final String PACKET_HEX =
        "00540003167D76748C57010001"
      + "68E7236E00000BEED8E21B4C95260380484410001009090A0501020003001C01AD018600870082108F000400071D36B81E0F1816004217003D8B000289000083000001410166C7AB"
      + "0003F2234D00000BEEE1161B4CC73603B5481210000C07090A0501020003001C01AD0186008700820C8F000400071D37001E0F1316004217003D8B000189000083000001410166C71F"
      // CRC16 (последња 4 хекса) можеш додати ако је познат, али за овај тест није потребно јер не верификујемо CRC
      ;

    private static String stripTransportAndTakeFirstRecord(String hex) {
        // 1) packet length: 2B = 4 hex
        int idx = 4;
        // 2) IMEI: 8B = 16 hex
        idx += 16;
        // 3) Command: 1B = 2 hex (0x01 => STANDARD)
        //String cmdHex = hex.substring(idx, idx+2);
        idx += 2;
        // 4) Records left: 1B = 2 hex
        idx += 2;
        // 5) Number of records: 1B = 2 hex (нпр. 01)
        //int numRecords = Integer.parseInt(hex.substring(idx, idx+2), 16);
        idx += 2;

        // Сада на idx почиње први RECORD (header+body) у hex-у.
        // Пошто Standard nema „record length“ унутра, морамо парсирати „на суво“
        // или „od oka“ уз помоћ твоје имплементације.
        // За тест, узећемо цео преостали део до краја (без CRC, ако желиш скрати).
        String payloadNoCrc = hex; // ако желиш, одсеците 4 хекса CRC-а
        return payloadNoCrc.substring(idx);
    }

    @Test
    public void testParseStandardRecordFromDevicePacket() {
        String recordHex = stripTransportAndTakeFirstRecord(PACKET_HEX);

        RuptelaProtokol ruptela = new RuptelaProtokol();

        // Dummy Objekti – довољно је да има ознаку за лог и да није null
        Objekti obj = new Objekti();
        obj.setOznaka("TEST-OBJ");

        JavljanjeObd jo = ruptela.vratiJavljanje(0, obj, recordHex);
        assertNotNull("Парсирање није смело да врати null", jo);
        assertNotNull("Javljanje мора бити set-овано", jo.getJavljanje());

        // Основне провере: lat/lon у разумном опсегу
        double lat = jo.getJavljanje().getLat();
        double lon = jo.getJavljanje().getLon();
        assertTrue("lat опсег", lat >= -90 && lat <= 90);
        assertTrue("lon опсег", lon >= -180 && lon <= 180);

        // Брзина је non-negative 0..300, рецимо
        int speed = jo.getJavljanje().getBrzina();
        assertTrue("speed опсег", speed >= 0 && speed < 300);

        // EventID у STANDARD је 1B → 0..255
        // (Унутрашњи код га већ чита као 1B)
        // Нема конкретне бројке из спецификације — само sanity check:
        // Не бацати изузетак је главни циљ овог smoke теста.

        // Ако желиш испис адресе/вредности у конзолу док тестирамо:
        System.out.println("Parsed lat=" + lat + ", lon=" + lon 
        		+ ", speed=" + speed + ", direction=" + jo.getJavljanje().getPravac());
    }
}
