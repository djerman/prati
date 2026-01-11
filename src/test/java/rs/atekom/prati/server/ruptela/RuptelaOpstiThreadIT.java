package rs.atekom.prati.server.ruptela;

import static org.junit.Assert.*;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import org.junit.Test;
import pratiBaza.tabele.Javljanja;
import pratiBaza.tabele.Obd;
import pratiBaza.tabele.Objekti;
import rs.atekom.prati.server.JavljanjeObd;
import rs.atekom.prati.server.OpstiServer;

/**
 * Интеграциони тест: покреће RuptelaOpstiThread са унапред припремљеним packet-ом
 * и на крају исписује све парсиране податке (lat, lon, speed, event/alarmi, OBD...).
 */
public class RuptelaOpstiThreadIT {

    /** Твоја порука (скраћена – без нула padding-а). По потреби замени својим packet-ом. */
    @SuppressWarnings("unused")
	private static final String PACKET_HEX =
        "00540003167D76748C57010001"
      + "68E7236E00000BEED8E21B4C95260380484410001009090A0501020003001C01AD018600870082108F000400071D36B81E0F1816004217003D8B000289000083000001410166C7AB"
      + "0003F2234D00000BEEE1161B4CC73603B5481210000C07090A0501020003001C01AD0186008700820C8F000400071D37001E0F1316004217003D8B000189000083000001410166C71F";

    private static final String PACKET_HEX1 =
    	    "005B000312FA451DB83A01000168E7492600010848259E1AE7AD3F0DD2185610000"
    	    + "007050B05001B11020003001C012025AD008600870082008F00071D34E81E0FBF"
    	    + "16004017003D8B0002890002830000024110E60EF4960000559600452BD7A1"
    	    + "0000084825D01AE7ACBA0DC7047400000000070B05001B00020003001C0120"
    	    + "1CAD008600870082008F00071D34F41E0FE016003E17003C8B000089000083"
    	    + "0000024110E60EF496000000000068E6E6150000084825D01AE7ACBA0DC7047"
    	    + "400000000070B05001B00020003001C012019AD008600870082008F00071D34"
    	    + "FD1E0FDE16003F17003D8B0000890000830000024110E60EF49600000000006"
    	    + "8E6F4890000084825D01AE7ACBA0DC7047400000000070B05001B0002000300"
    	    + "1C012017AD008600870082008F00071D34FE1E0FDC16003F17003C8B0000890"
    	    + "000830000024110E60EF496000000000068E702FD0000084825D01AE7ACBA0D"
    	    + "C7047400000000070B05001B00020003001C012015AD008600870082008F000"
    	    + "71D34EF1E0FD816003F17003C8B0000890000830000024110E60EF496000000"
    	    + "000068E711710000084825D01AE7ACBA0DC7047400000000070B05001B00020"
    	    + "003001C012014AD008600870082008F00071D34EB1E0FD616003F17003D8B00"
    	    + "00890000830000024110E60EF496000000000068E71FE50000084825D01AE7A"
    	    + "CBA0DC7047400000000070B05001B00020003001C012012AD00860087008200"
    	    + "8F00071D34E11E0FD216003F17003C8B0000890000830000024110E60EF4960"
    	    + "00000000068E72E590000084825D01AE7ACBA0DC7047400000000070B05001B"
    	    + "00020003001C012011AD008600870082008F00071D34E61E0FCE16003F17003"
    	    + "C8B0000890000830000024110E60EF496000000000068E730850000084825D0"
    	    + "1AE7ACBA0DC7047400000000070B05001B00020003001C012011AD008600870"
    	    + "082008F00071D34EA1E0FCD16003F17003D8B0000890000830000024110E60E"
    	    + "F496000000000068E730880001084825D01AE7ACBA0DC7047400000000050B0"
    	    + "5011B00020003001C012011AD008600870082008F00071D34D81E0FCD16003F"
    	    + "17003D8B0003890000830000024110E60EF4960000000000961100000000000"
    	    + "000000000000000000000000000000000000000000000000000000000000000"
    	    + "000000000000000000000000000000000000000000000000000000000000000"
    	    + "000000000000000000000000000000000000000000000000000000000000000"
    	    + "000000000000000000000000000000000000000000000000000000000000000"
    	    + "000000000000000000000000000000000000000000000000000000000000000"
    	    + "000000000000000000000000000000000000000000000000000000000000000"
    	    + "00000000000000000000000000000";

    /** Помоћно: hex → bytes */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
        }
        return out;
    }

    /** Мини socket који враћа наш улаз и хвата излаз. */
    static class TestSocket extends Socket {
        private final InputStream in;
        private final ByteArrayOutputStream out;

        TestSocket(byte[] payload) {
            this.in = new ByteArrayInputStream(payload);
            this.out = new ByteArrayOutputStream();
        }

        @Override public InputStream getInputStream() { return in; }
        @Override public OutputStream getOutputStream() { return out; }
        public byte[] getWritten() { return out.toByteArray(); }
        @Override public synchronized void close() throws IOException {
            super.close();
            in.close();
            out.close();
        }
    }

    /** Минимални OpstiServer са RuptelaProtokol инстанцом. */
    static class TestServer extends OpstiServer {
        public final RuptelaProtokol rProtokol = new RuptelaProtokol();
        // ако OpstiServer има обавезни конструктор/поља – подеси их по потреби
        public TestServer() {
            super(
                9040,          // port (0 = OS бира слободан, ако је потребно)
                1              // poolsize
            );
        }
    }

    /** Тест-подкласа која пресреће резултате и онемогућава споредне ефекте. */
    static class TestableRuptelaThread extends RuptelaOpstiThread {
        public final List<JavljanjeObd> parsed = new ArrayList<>();

        public TestableRuptelaThread(LinkedBlockingQueue<Socket> q, OpstiServer s) {
            super(q, s);
        }

        // ⬇️ КЉУЧНО: заобиђи базу/сервисе — само „измисли“ објекат за IMEI
        @Override
        public void pronadjiPostavi(String kodUredjaja) {
            this.kodUredjaja = kodUredjaja;   // чисто ради логова
            Objekti obj = new Objekti();
            obj.setOznaka("TEST-" + (kodUredjaja != null && kodUredjaja.length() > 4
                    ? kodUredjaja.substring(kodUredjaja.length()-4) : "OBJ"));
            this.objekat = obj;               // ✅ довољно да парсирање крене
            // ако ти треба и „уређај“, овде можеш да сетујеш и this.uredjaj (dummy)
        }
        
        @Override
        public void obradaJavljanja(Javljanja j, Obd o) {
            parsed.add(new JavljanjeObd(j, o));
            // НЕ ради ништа друго (без базе, без Broadcaster-а)
        }
    }

    @Test
    public void fullThread_parsesPacket_andPrintsAllFields() throws Exception {
        // 1) Припреми „сокет“ са packet-ом
        byte[] bytes = hexToBytes(PACKET_HEX1);
        TestSocket sock = new TestSocket(bytes);

        // 2) Queue и server
        LinkedBlockingQueue<Socket> q = new LinkedBlockingQueue<>();
        q.add(sock);
        TestServer server = new TestServer();

        // 3) Тестирани thread
        TestableRuptelaThread t = new TestableRuptelaThread(q, server);

        // 4) (Опционо) иницијализуј контекст у thread-у ако очекује да је уређај већ „познат“.
        //    Ако RuptelaOpstiThread сам позива pronadjiPostavi(imei) – није потребно.

        // 5) Покрени и сачекај да заврши један круг (прочита stream до краја)
        t.run();
        // 2) прочитај ACK из излазног бафера "сокета"
        byte[] ack = sock.getWritten();

        // 3) провера и (опционо) штампа
        assertTrue("Очекујем да је ACK послат (није празан)", ack.length > 0);

        // ако желиш да видиш ACK у hex-у:
        StringBuilder sb = new StringBuilder(ack.length * 2);
        for (byte b : ack) sb.append(String.format("%02X", b));
        System.out.println("ACK HEX = " + sb.toString());

        // 6) Провере: бар један запис је упарсиран
        assertFalse("Очекивао сам бар један упарсиран запис", t.parsed.isEmpty());

        // 7) Испиши све што је стигло
        int idx = 0;
        for (JavljanjeObd jo : t.parsed) {
            Javljanja j = jo.getJavljanje();
            Obd o = jo.getObd();

            System.out.println("==== RECORD #" + (++idx) + " ====");
            System.out.println("Time       : " + (j.getDatumVreme() != null ? j.getDatumVreme() : "null"));
            System.out.println("Lat, Lon   : " + j.getLat() + ", " + j.getLon());
            System.out.println("Speed(km/h): " + j.getBrzina());
            System.out.println("Angle/Alt  : " + j.getPravac() + "°, " + j.getVisina() + " m");
            System.out.println("Kontakt    : " + j.isKontakt());
            System.out.println("Alarm      : " + (j.getSistemAlarmi() != null ? j.getSistemAlarmi().getSifra() : "null"));
            System.out.println("EventData  : " + j.getEventData());
            System.out.println("VirtOdo(km): " + j.getVirtualOdo());
            if (o != null) {
                System.out.println("-- OBD --");
                System.out.println("RPM           : " + o.getRpm());
                System.out.println("Temp(°C)      : " + o.getTemperatura());
                System.out.println("Opterećenje % : " + o.getOpterecenje());
                System.out.println("Nivo goriva % : " + o.getNivoGoriva());
                System.out.println("Akumulator (V): " + o.getAkumulator());
                System.out.println("Trip Km       : " + o.getTripKm());
                System.out.println("Trip Gorivo L : " + o.getTripGorivo());
                System.out.println("Ukupno Km     : " + o.getUkupnoKm());
                System.out.println("Ukupno Gorivo : " + o.getUkupnoGorivo());
                System.out.println("Prosečna L/h  : " + o.getProsecnaPotrosnja());
                System.out.println("Greske        : " + o.getGreske());
            } else {
                System.out.println("-- OBD: (nema) --");
            }
        }

        // 8) Примитиван sanity check: координате у опсегу
        Javljanja first = t.parsed.get(0).getJavljanje();
        assertTrue(first.getLat() >= -90 && first.getLat() <= 90);
        assertTrue(first.getLon() >= -180 && first.getLon() <= 180);
    }
    
    @SuppressWarnings("unused")
	private static String bytesToHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

}
