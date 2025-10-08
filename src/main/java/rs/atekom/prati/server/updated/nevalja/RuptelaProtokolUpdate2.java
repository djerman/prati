package rs.atekom.prati.server.updated.nevalja;

import java.sql.Timestamp;
import java.util.Date;

import pratiBaza.tabele.Javljanja;
import pratiBaza.tabele.Obd;
import pratiBaza.tabele.Objekti;
import pratiBaza.tabele.SistemAlarmi;
import rs.atekom.prati.server.JavljanjeObd;
import rs.atekom.prati.server.Servis;

/**
 * Robust, defensive Ruptela protocol parser.
 *
 * Овде сам додао само један додатни лог (при успешној парси) и очувао све остало.
 * Ако желиш непобитну потврду уписа у базу, додај logger у obradaJavljanja (пример доле).
 */
public class RuptelaProtokolUpdate2 {

    private SistemAlarmi redovno, sos, aktiviran, deaktiviran;

    private static final int MAX_RECORDS_LIMIT = 1000;

    public RuptelaProtokolUpdate2() {
        initFromServis();
    }

    public RuptelaProtokolUpdate2(boolean skipServiceInit) {
        if (!skipServiceInit) initFromServis();
    }

    private void initFromServis() {
        try {
            redovno = Servis.sistemAlarmServis.nadjiAlarmPoSifri("0");
            sos = Servis.sistemAlarmServis.nadjiAlarmPoSifri("6022");
            aktiviran = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1092");
            deaktiviran = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1091");
        } catch (Throwable t) {
            redovno = null; sos = null; aktiviran = null; deaktiviran = null;
        }
    }

    public JavljanjeObd vratiJavljanje(int offsetBytes, Objekti objekat, String poruka) {
        if (poruka == null) return null;
        byte[] b;
        try {
            b = hexToBytes(poruka);
        } catch (IllegalArgumentException iae) {
            System.out.println("ruptela parser: invalid hex payload: " + iae.getMessage());
            return null;
        }

        try {
            int off = offsetBytes;
            if (!ensureAvailable(b, off, 4)) return null;

            long tsSec = readUInt32BE(b, off);
            Timestamp datumVreme = new Timestamp(tsSec * 1000L);
            off += 4;

            if (!ensureAvailable(b, off, 1)) return null;
            off += 1;

            if (!ensureAvailable(b, off, 1)) return null;
            off += 1;

            if (!ensureAvailable(b, off, 4)) return null;
            int lonInt = readInt32BE(b, off);
            double lon = ((double) lonInt) / 1e7;
            off += 4;

            if (!ensureAvailable(b, off, 4)) return null;
            int latInt = readInt32BE(b, off);
            double lat = ((double) latInt) / 1e7;
            off += 4;

            if (!ensureAvailable(b, off, 2)) return null;
            float visina = ((float) readUInt16BE(b, off)) / 10.0f;
            off += 2;

            if (!ensureAvailable(b, off, 2)) return null;
            float pravac = ((float) readUInt16BE(b, off)) / 100.0f;
            off += 2;

            if (!ensureAvailable(b, off, 1)) return null;
            off += 1;

            if (!ensureAvailable(b, off, 2)) return null;
            int brzina = readUInt16BE(b, off);
            off += 2;

            if (!ensureAvailable(b, off, 1)) return new JavljanjeObd(new Javljanja(), null); // partial but return minimal
            off += 1;

            if (!ensureAvailable(b, off, 1)) return new JavljanjeObd(new Javljanja(), null);
            int eventId = readUInt8(b, off);
            off += 1;

            Date sada = new Date();
            Javljanja javljanje = new Javljanja();
            javljanje.setObjekti(objekat);
            javljanje.setValid(true);
            javljanje.setVirtualOdo(0.0f);
            javljanje.setDatumVreme(datumVreme);
            javljanje.setLon(lon);
            javljanje.setLat(lat);
            javljanje.setVisina(visina);
            javljanje.setPravac(pravac);
            javljanje.setBrzina(brzina);
            if (redovno != null) javljanje.setSistemAlarmi(redovno);
            javljanje.setEventData("0");
            javljanje.setIbutton("0");

            Obd obd = new Obd();
            obd.setObjekti(objekat);
            obd.setRpm(0);
            obd.setTemperatura(0);
            obd.setOpterecenje(0.0f);
            obd.setGas(0.0f);
            obd.setNivoGoriva(0.0f);
            obd.setAkumulator(0.0f);
            obd.setTripKm(0.0f);
            obd.setTripGorivo(0.0f);
            obd.setUkupnoVreme(0.0f);
            obd.setUkupnoKm(0);
            obd.setUkupnoGorivo(0.0f);
            obd.setProsecnaPotrosnja(0.0f);
            obd.setGreske("");
            obd.setDatumVreme(datumVreme);

            if (!ensureAvailable(b, off, 1)) return new JavljanjeObd(javljanje, maybeNullObd(obd));
            int brJedan = readUInt8(b, off);
            off += 1;
            for (int i = 0; i < brJedan; i++) {
                if (!ensureAvailable(b, off, 2)) { return new JavljanjeObd(javljanje, maybeNullObd(obd)); }
                int id = readUInt8(b, off);
                int val = readUInt8(b, off + 1);
                off += 2;
                upisi1bajt(id, val, javljanje, eventId, obd);
            }

            if (!ensureAvailable(b, off, 1)) return new JavljanjeObd(javljanje, maybeNullObd(obd));
            int brDva = readUInt8(b, off);
            off += 1;
            for (int j = 0; j < brDva; j++) {
                if (!ensureAvailable(b, off, 3)) { return new JavljanjeObd(javljanje, maybeNullObd(obd)); }
                int id = readUInt8(b, off);
                int val = readUInt16BE(b, off + 1);
                off += 3;
                upisi2bajta(id, val, obd);
            }

            if (!ensureAvailable(b, off, 1)) return new JavljanjeObd(javljanje, maybeNullObd(obd));
            int brCetiri = readUInt8(b, off);
            off += 1;
            for (int k = 0; k < brCetiri; k++) {
                if (!ensureAvailable(b, off, 5)) { return new JavljanjeObd(javljanje, maybeNullObd(obd)); }
                int id = readUInt8(b, off);
                long val = readUInt32BE(b, off + 1);
                off += 5;
                upisi4bajta(id, val, javljanje, obd);
            }

            if (!ensureAvailable(b, off, 1)) return new JavljanjeObd(javljanje, maybeNullObd(obd));
            int brOsam = readUInt8(b, off);
            off += 1;
            for (int l = 0; l < brOsam; l++) {
                if (!ensureAvailable(b, off, 9)) { return new JavljanjeObd(javljanje, maybeNullObd(obd)); }
                off += 9;
            }

            obd.setKreirano(new Timestamp(sada.getTime()));
            obd.setIzmenjeno(new Timestamp(sada.getTime()));
            javljanje.setKreirano(new Timestamp(sada.getTime()));
            javljanje.setIzmenjeno(new Timestamp(sada.getTime()));

            // PARSER: uspešno parsed -> print short confirm (ovde nemamo imei, thread loguje imei)
            System.out.println("ruptela parser: vratiJavljanje parsed ts=" + datumVreme + " lat=" + lat + " lon=" + lon + " speed=" + brzina);

            if (obd.getNivoGoriva() == 0.0f && obd.getProsecnaPotrosnja() == 0.0f && obd.getRpm() == 0 && obd.getTemperatura() == 0
                    && obd.getUkupnoGorivo() == 0.0f && obd.getUkupnoKm() == 0) {
                obd = null;
            }

            return new JavljanjeObd(javljanje, obd);

        } catch (Exception ex) {
            System.out.println("RuptelaProtokolUpdate.vratiJavljanje: parse error - " + ex.getMessage());
            ex.printStackTrace();
            return null;
        }
    }

    public JavljanjeObd vratiExtended(int offsetBytes, Objekti objekat, String poruka) {
        if (poruka == null) return null;
        byte[] b;
        try {
            b = hexToBytes(poruka);
        } catch (IllegalArgumentException iae) {
            System.out.println("ruptela parser: invalid hex payload: " + iae.getMessage());
            return null;
        }

        try {
            int off = offsetBytes;
            if (!ensureAvailable(b, off, 4)) return null;
            long tsSec = readUInt32BE(b, off);
            Timestamp datumVreme = new Timestamp(tsSec * 1000L);
            off += 4;

            if (!ensureAvailable(b, off, 1)) return null;
            off += 1;

            if (!ensureAvailable(b, off, 1)) return null;
            off += 1;

            if (!ensureAvailable(b, off, 1)) return null;
            off += 1;

            if (!ensureAvailable(b, off, 4)) return null;
            int lonInt = readInt32BE(b, off);
            double lon = ((double) lonInt) / 1e7;
            off += 4;

            if (!ensureAvailable(b, off, 4)) return null;
            int latInt = readInt32BE(b, off);
            double lat = ((double) latInt) / 1e7;
            off += 4;

            if (!ensureAvailable(b, off, 2)) return null;
            float visina = ((float) readUInt16BE(b, off)) / 10.0f;
            off += 2;

            if (!ensureAvailable(b, off, 2)) return null;
            float pravac = ((float) readUInt16BE(b, off)) / 100.0f;
            off += 2;

            if (!ensureAvailable(b, off, 1)) return null;
            off += 1;

            if (!ensureAvailable(b, off, 2)) return null;
            int brzina = readUInt16BE(b, off);
            off += 2;

            if (!ensureAvailable(b, off, 1)) return null;
            off += 1;

            if (!ensureAvailable(b, off, 2)) return null;
            int eventId = readUInt16BE(b, off);
            off += 2;

            Date sada = new Date();
            Javljanja javljanje = new Javljanja();
            javljanje.setObjekti(objekat);
            javljanje.setValid(true);
            javljanje.setVirtualOdo(0.0f);
            javljanje.setDatumVreme(datumVreme);
            javljanje.setLon(lon);
            javljanje.setLat(lat);
            javljanje.setVisina(visina);
            javljanje.setPravac(pravac);
            javljanje.setBrzina(brzina);
            if (redovno != null) javljanje.setSistemAlarmi(redovno);
            javljanje.setEventData("0");
            javljanje.setIbutton("0");

            Obd obd = new Obd();
            obd.setObjekti(objekat);
            obd.setRpm(0);
            obd.setTemperatura(0);
            obd.setOpterecenje(0.0f);
            obd.setGas(0.0f);
            obd.setNivoGoriva(0.0f);
            obd.setAkumulator(0.0f);
            obd.setTripKm(0.0f);
            obd.setTripGorivo(0.0f);
            obd.setUkupnoVreme(0.0f);
            obd.setUkupnoKm(0);
            obd.setUkupnoGorivo(0.0f);
            obd.setProsecnaPotrosnja(0.0f);
            obd.setGreske("");
            obd.setDatumVreme(datumVreme);

            if (!ensureAvailable(b, off, 1)) return new JavljanjeObd(javljanje, maybeNullObd(obd));
            int brJedan = readUInt8(b, off);
            off += 1;
            for (int i = 0; i < brJedan; i++) {
                if (!ensureAvailable(b, off, 2)) return new JavljanjeObd(javljanje, maybeNullObd(obd));
                int id = readUInt8(b, off);
                int val = readUInt8(b, off + 1);
                off += 2;
                upisi1bajt(id, val, javljanje, eventId, obd);
            }

            if (!ensureAvailable(b, off, 1)) return new JavljanjeObd(javljanje, maybeNullObd(obd));
            int brDva = readUInt8(b, off);
            off += 1;
            for (int j = 0; j < brDva; j++) {
                if (!ensureAvailable(b, off, 3)) return new JavljanjeObd(javljanje, maybeNullObd(obd));
                int id = readUInt8(b, off);
                int val = readUInt16BE(b, off + 1);
                off += 3;
                upisi2bajta(id, val, obd);
            }

            if (!ensureAvailable(b, off, 1)) return new JavljanjeObd(javljanje, maybeNullObd(obd));
            int brCetiri = readUInt8(b, off);
            off += 1;
            for (int k = 0; k < brCetiri; k++) {
                if (!ensureAvailable(b, off, 5)) return new JavljanjeObd(javljanje, maybeNullObd(obd));
                int id = readUInt8(b, off);
                long val = readUInt32BE(b, off + 1);
                off += 5;
                upisi4bajta(id, val, javljanje, obd);
            }

            if (!ensureAvailable(b, off, 1)) return new JavljanjeObd(javljanje, maybeNullObd(obd));
            int brOsam = readUInt8(b, off);
            off += 1;
            for (int l = 0; l < brOsam; l++) {
                if (!ensureAvailable(b, off, 9)) return new JavljanjeObd(javljanje, maybeNullObd(obd));
                off += 9;
            }

            obd.setKreirano(new Timestamp(sada.getTime()));
            obd.setIzmenjeno(new Timestamp(sada.getTime()));
            javljanje.setKreirano(new Timestamp(sada.getTime()));
            javljanje.setIzmenjeno(new Timestamp(sada.getTime()));

            System.out.println("ruptela parser: vratiExtended parsed ts=" + datumVreme + " lat=" + lat + " lon=" + lon + " speed=" + brzina);

            if (obd.getNivoGoriva() == 0.0f && obd.getProsecnaPotrosnja() == 0.0f && obd.getRpm() == 0 && obd.getTemperatura() == 0
                    && obd.getUkupnoGorivo() == 0.0f && obd.getUkupnoKm() == 0) {
                obd = null;
            }
            return new JavljanjeObd(javljanje, obd);

        } catch (Exception ex) {
            System.out.println("RuptelaProtokolUpdate.vratiExtended: parse error - " + ex.getMessage());
            ex.printStackTrace();
            return null;
        }
    }

    /* ---------------------- helpers ---------------------- */

    private Obd maybeNullObd(Obd o) {
        return o;
    }

    private void upisi1bajt(int id, int rezultat, Javljanja javljanje, int eventId, Obd obd) {
        switch (id) {
            case 2: break;
            case 3: break;
            case 4:
                if (rezultat == 1 && sos != null) javljanje.setSistemAlarmi(sos);
                break;
            case 5:
                if (rezultat == 1) {
                    javljanje.setKontakt(true);
                    if (eventId == 5 && aktiviran != null) javljanje.setSistemAlarmi(aktiviran);
                } else {
                    javljanje.setKontakt(false);
                    if (eventId == 5 && deaktiviran != null) javljanje.setSistemAlarmi(deaktiviran);
                }
                break;
            case 96: obd.setTemperatura(rezultat); break;
            case 98: obd.setNivoGoriva((float) rezultat); break;
            case 103: obd.setOpterecenje((float) rezultat); break;
            case 115:
                if (!javljanje.isKontakt()) obd.setTemperatura(0); else obd.setTemperatura(rezultat - 40);
                break;
            case 207:
                if (rezultat < 251) obd.setNivoGoriva((float) (rezultat * 0.4)); else obd.setNivoGoriva(0.0f);
                break;
            case 251:
                if (rezultat == 1) {
                    javljanje.setKontakt(true);
                    if (eventId == 5 && aktiviran != null) javljanje.setSistemAlarmi(aktiviran);
                } else {
                    javljanje.setKontakt(false);
                    if (eventId == 5 && deaktiviran != null) javljanje.setSistemAlarmi(deaktiviran);
                }
                break;
            default: break;
        }
    }

    private void upisi2bajta(int id, int rez, Obd obd) {
        switch (id) {
            case 29: obd.setAkumulator((float) (rez / 1000.0f)); break;
            case 94: Double rpmObd = rez * 0.25; obd.setRpm(rpmObd.intValue()); break;
            case 100: obd.setProsecnaPotrosnja((float) rez); break;
            case 107: obd.setUkupnoVreme((float) rez); break;
            case 116: obd.setProsecnaPotrosnja((float) (rez * 0.05)); break;
            case 197: Double rpm = rez * 0.125; obd.setRpm(rpm.intValue()); break;
            case 205: obd.setTripGorivo((float) rez); break;
            default: break;
        }
    }

    private void upisi4bajta(int id, long rez, Javljanja javljanje, Obd obd) {
        switch (id) {
            case 65: javljanje.setVirtualOdo((float) rez / 1000.0f); break;
            case 114: obd.setUkupnoKm((int) ((rez * 5) / 1000)); break;
            case 203: obd.setUkupnoVreme((float) (rez * 0.05)); break;
            case 208:
                if (obd.getUkupnoGorivo() == 0.0f) obd.setUkupnoGorivo((float) (rez * 0.5));
                break;
            default: break;
        }
    }

    /* ---------------------- binary helpers ---------------------- */

    private static byte[] hexToBytes(String hex) {
        if (hex == null) throw new IllegalArgumentException("hex string null");
        String s = hex.trim();
        if ((s.length() & 1) != 0) throw new IllegalArgumentException("hex string must have even length");
        int len = s.length() / 2;
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) {
            int hi = Character.digit(s.charAt(i * 2), 16);
            int lo = Character.digit(s.charAt(i * 2 + 1), 16);
            if (hi == -1 || lo == -1) throw new IllegalArgumentException("invalid hex char at pos " + (i * 2));
            out[i] = (byte) ((hi << 4) + lo);
        }
        return out;
    }

    private boolean ensureAvailable(byte[] b, int off, int need) {
        return b != null && off >= 0 && (off + need) <= b.length;
    }

    private int readUInt8(byte[] b, int off) {
        return b[off] & 0xFF;
    }

    private int readUInt16BE(byte[] b, int off) {
        return ((b[off] & 0xFF) << 8) | (b[off + 1] & 0xFF);
    }

    private long readUInt32BE(byte[] b, int off) {
        return ((long) (b[off] & 0xFF) << 24) | ((long) (b[off + 1] & 0xFF) << 16)
                | ((long) (b[off + 2] & 0xFF) << 8) | ((long) (b[off + 3] & 0xFF));
    }

    private int readInt32BE(byte[] b, int off) {
        return (b[off] << 24) | ((b[off + 1] & 0xFF) << 16) | ((b[off + 2] & 0xFF) << 8) | (b[off + 3] & 0xFF);
    }
}
