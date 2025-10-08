package rs.atekom.prati.server.updated.nevalja;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import javax.xml.bind.DatatypeConverter;

import pratiBaza.tabele.Javljanja;
import pratiBaza.tabele.Obd;
import rs.atekom.prati.server.JavljanjeObd;
import rs.atekom.prati.server.updated.OpstiServerUpdate;
import rs.atekom.prati.server.updated.OpstiThreadUpdate;

/**
 * RuptelaOpstiThreadUpdate - побољшана и дефензивнија верзија
 * оригиналне RuptelaOpstiThread класе.
 *
 * Додао само кратке, конкретне логове:
 *  - parsed OK ...
 *  - obradaJavljanja invoked ...
 *  - ACK sent ...
 *
 * Напомена: да би добио непобитну потврду да је запис у бази сачуван,
 * треба додати једну линију лог-а УНУТАР obradaJavljanja након persist-а.
 * (пример коментарисан на дну).
 */
public class RuptelaOpstiThreadUpdate2 extends OpstiThreadUpdate {

    private static final int MAX_RECORDS_LIMIT = 1000;

    public RuptelaOpstiThreadUpdate2(LinkedBlockingQueue<Socket> queue, rs.atekom.prati.server.updated.OpstiServerUpdate server) {
        super(queue, server);
    }

    @Override
    public void run() {
        while (!isStopped()) {
            try {
                socket = socketQueue.take();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }

            if (socket == null) continue;

            try {
                System.out.println("RuptelaOpstiThreadUpdate: taken socket from queue: " + safeRemoteAddr(socket));
            } catch (Throwable ignored) {}

            try {
                input = socket.getInputStream();
                out = socket.getOutputStream();
            } catch (IOException e) {
                System.out.println("ruptela: cannot obtain streams for connection " + safeRemoteAddr(socket) + " -> closing");
                closeConnectionQuietly();
                continue;
            }

            try {
                int br = 0;
                while (!isStopped() && socket != null && !socket.isClosed()) {
                    try {
                        try {
                            socket.setSoTimeout(vreme);
                        } catch (Throwable ignore) {}

                        br = input.read(data, 0, data.length);
                        if (br <= 0) {
                            break;
                        }

                        byte[] slice = Arrays.copyOfRange(data, 0, br);
                        String ulaz = DatatypeConverter.printHexBinary(slice);
                        int shown = Math.min(200, ulaz.length());
                        System.out.println("ruptela: recv from " + safeRemoteAddr(socket) + " bytes=" + br + " hexLen=" + ulaz.length() +
                                " snippet=\"" + (shown > 0 ? ulaz.substring(0, shown) : "") + (ulaz.length() > shown ? "..." : "") + "\"");

                        int offset = 0;
                        final int hexLen = ulaz.length();

                        if (!ensureHexAvailable(ulaz, offset, 4 + 16)) {
                            System.out.println("ruptela: message too short for imei/header, hexLen=" + hexLen + " from " + safeRemoteAddr(socket));
                            break;
                        }
                        offset += 4; // skip preamble
                        String imeiHex = ulaz.substring(offset, offset + 16);
                        Long imei = null;
                        try {
                            imei = Long.parseLong(imeiHex, 16);
                            kodUredjaja = imei.toString();
                        } catch (NumberFormatException nfe) {
                            System.out.println("ruptela: invalid imei hex '" + imeiHex + "' from " + safeRemoteAddr(socket));
                            break;
                        }

                        pronadjiPostavi(kodUredjaja);
                        offset += 16;

                        if (!ensureHexAvailable(ulaz, offset, 2)) {
                            System.out.println("ruptela: truncated before command byte, imei=" + kodUredjaja);
                            break;
                        }
                        int komanda = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);

                        if (komanda == 1 || komanda == 68) {
                            offset += 4; // skip command + recordsLeft
                            if (!ensureHexAvailable(ulaz, offset, 2)) {
                                System.out.println("ruptela: missing records count, imei=" + kodUredjaja);
                                break;
                            }
                            int ukZapisa = Integer.parseInt(ulaz.substring(offset - 2, offset), 16);
                            if (ukZapisa < 0 || ukZapisa > MAX_RECORDS_LIMIT) {
                                System.out.println("ruptela: suspicious record count " + ukZapisa + " for imei=" + kodUredjaja + " hexLen=" + hexLen);
                                break;
                            }

                            if (objekat != null) {
                                int brZapisa = 0;
                                if (komanda == 1) {
                                    while (brZapisa < ukZapisa) {
                                        if (!ensureHexAvailable(ulaz, offset, 46)) {
                                            System.out.println("ruptela: incomplete standard record for imei=" + kodUredjaja +
                                                    " brZapisa=" + brZapisa + " offset=" + offset + " hexLen=" + hexLen);
                                            break;
                                        }
                                        int pocetak = offset;
                                        offset += 46;

                                        if (!ensureHexAvailable(ulaz, offset, 2)) { break; }
                                        int brJedan = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
                                        offset += 2;
                                        if (!ensureHexAvailable(ulaz, offset, brJedan * 4)) { break; }
                                        offset += brJedan * 4;

                                        if (!ensureHexAvailable(ulaz, offset, 2)) { break; }
                                        int brDva = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
                                        offset += 2;
                                        if (!ensureHexAvailable(ulaz, offset, brDva * 6)) { break; }
                                        offset += brDva * 6;

                                        if (!ensureHexAvailable(ulaz, offset, 2)) { break; }
                                        int brCetiri = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
                                        offset += 2;
                                        if (!ensureHexAvailable(ulaz, offset, brCetiri * 10)) { break; }
                                        offset += brCetiri * 10;

                                        if (!ensureHexAvailable(ulaz, offset, 2)) { break; }
                                        int brOsam = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
                                        offset += 2;
                                        if (!ensureHexAvailable(ulaz, offset, brOsam * 18)) { break; }
                                        offset += brOsam * 18;

                                        String recordHex = ulaz.substring(pocetak, offset);

                                        JavljanjeObd javljanjeObd = null;
                                        try {
                                            javljanjeObd = server.rProtokol.vratiJavljanje(0, objekat, recordHex);
                                            if (javljanjeObd == null || javljanjeObd.getJavljanje() == null) {
                                                String snippet = recordHex.length() > 200 ? recordHex.substring(0,200) : recordHex;
                                                System.out.println("ruptela: vratiJavljanje returned null for imei=" + kodUredjaja
                                                    + " recordLen=" + recordHex.length() + " snippet=" + snippet);
                                            } else {
                                                // PARSER: uspešno извукао поља -> логирај кључне вредности
                                                try {
                                                    Javljanja j = javljanjeObd.getJavljanje();
                                                    Obd o = javljanjeObd.getObd();
                                                    String ts = j.getDatumVreme() == null ? "null" : j.getDatumVreme().toString();
                                                    System.out.println("ruptela: parsed OK imei=" + kodUredjaja +
                                                            " ts=" + ts +
                                                            " lat=" + j.getLat() + " lon=" + j.getLon() + " speed=" + j.getBrzina());
                                                    // позови обраду
                                                    obradaJavljanja(j, o);
                                                    // након позива, логирај да је позвано
                                                    System.out.println("ruptela: obradaJavljanja invoked imei=" + kodUredjaja +
                                                            " javljanjeId=" + (j.getId() == null ? "null" : j.getId().toString()));
                                                } catch (Throwable tLog) {
                                                    System.out.println("ruptela: error logging parsed fields for imei=" + kodUredjaja + " : " + tLog.getMessage());
                                                }
                                            }
                                        } catch (Throwable t) {
                                            System.out.println("ruptela: parser threw for imei=" + kodUredjaja + " : " + t.getMessage());
                                            t.printStackTrace();
                                        }

                                        brZapisa++;
                                    } // end while brZapisa

                                    try {
                                        out.write(odg);
                                        out.flush();
                                        System.out.println("ruptela: ACK sent imei=" + kodUredjaja);
                                    } catch (IOException ioe) {
                                        System.out.println("ruptela: failed to send ack for imei=" + kodUredjaja + " : " + ioe.getMessage());
                                    }

                                } else { // extended
                                    Javljanja prvo = null;
                                    Obd prvoObd = null;
                                    while (brZapisa < ukZapisa) {
                                        if (!ensureHexAvailable(ulaz, offset, 50)) {
                                            System.out.println("ruptela: incomplete extended record header for imei=" + kodUredjaja +
                                                    " brZapisa=" + brZapisa + " offset=" + offset + " hexLen=" + hexLen);
                                            break;
                                        }

                                        int prvi = 0, drugi = 0;
                                        try {
                                            if (ensureHexAvailable(ulaz, offset + 10, 2)) {
                                                prvi = Integer.parseInt(ulaz.substring(offset + 10, offset + 11));
                                            }
                                            if (ensureHexAvailable(ulaz, offset + 11, 2)) {
                                                drugi = Integer.parseInt(ulaz.substring(offset + 11, offset + 12));
                                            }
                                        } catch (Exception e) {
                                            System.out.println("ruptela: cannot parse 'prvi/drugi' flags for imei=" + kodUredjaja + " : " + e.getMessage());
                                        }

                                        int pocetak = offset;
                                        offset += 50;

                                        if (!ensureHexAvailable(ulaz, offset, 2)) { break; }
                                        int brJedan = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
                                        offset += 2;
                                        if (!ensureHexAvailable(ulaz, offset, brJedan * 6)) { break; }
                                        offset += brJedan * 6;

                                        if (!ensureHexAvailable(ulaz, offset, 2)) { break; }
                                        int brDva = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
                                        offset += 2;
                                        if (!ensureHexAvailable(ulaz, offset, brDva * 8)) { break; }
                                        offset += brDva * 8;

                                        if (!ensureHexAvailable(ulaz, offset, 2)) { break; }
                                        int brCetiri = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
                                        offset += 2;
                                        if (!ensureHexAvailable(ulaz, offset, brCetiri * 12)) { break; }
                                        offset += brCetiri * 12;

                                        if (!ensureHexAvailable(ulaz, offset, 2)) { break; }
                                        int brOsam = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
                                        offset += 2;
                                        if (!ensureHexAvailable(ulaz, offset, brOsam * 20)) { break; }
                                        offset += brOsam * 20;

                                        String recordHex = ulaz.substring(pocetak, offset);
                                        JavljanjeObd javljanjeObd = null;
                                        try {
                                            javljanjeObd = server.rProtokol.vratiExtended(0, objekat, recordHex);
                                        } catch (Throwable t) {
                                            System.out.println("ruptela: parser threw (extended) for imei=" + kodUredjaja + " : " + t.getMessage());
                                            t.printStackTrace();
                                        }

                                        if (javljanjeObd != null) {
                                            // PARSER success log
                                            try {
                                                Javljanja j = javljanjeObd.getJavljanje();
                                                Obd o = javljanjeObd.getObd();
                                                System.out.println("ruptela: parsed (extended) imei=" + kodUredjaja +
                                                        " ts=" + (j == null ? "null" : j.getDatumVreme()) +
                                                        " lat=" + (j == null ? "null" : j.getLat()) +
                                                        " lon=" + (j == null ? "null" : j.getLon()) +
                                                        " speed=" + (j == null ? "null" : j.getBrzina()));
                                                // obrada i merge logic (као и раније)
                                                if (drugi <= prvi) {
                                                    if (drugi == 0) {
                                                        prvo = javljanjeObd.getJavljanje();
                                                        prvoObd = javljanjeObd.getObd();
                                                        if (prvo != null) obradaJavljanja(prvo, prvoObd);
                                                        System.out.println("ruptela: obradaJavljanja invoked (extended) imei=" + kodUredjaja +
                                                                " javljanjeId=" + (prvo == null ? "null" : (prvo.getId() == null ? "null" : prvo.getId().toString())));
                                                    } else {
                                                        if (prvoObd == null) {
                                                            prvoObd = javljanjeObd.getObd();
                                                        } else {
                                                            if (javljanjeObd.getObd() != null) {
                                                                if (javljanjeObd.getObd().getAkumulator() != 0.0f) prvoObd.setAkumulator(javljanjeObd.getObd().getAkumulator());
                                                                if (javljanjeObd.getObd().getGas() != 0.0f) prvoObd.setGas(javljanjeObd.getObd().getGas());
                                                                if (!"".equals(javljanjeObd.getObd().getGreske())) prvoObd.setGreske(javljanjeObd.getObd().getGreske());
                                                                if (javljanjeObd.getObd().getNivoGoriva() != 0.0f) prvoObd.setNivoGoriva(javljanjeObd.getObd().getNivoGoriva());
                                                                if (javljanjeObd.getObd().getOpterecenje() != 0.0f) prvoObd.setOpterecenje(javljanjeObd.getObd().getOpterecenje());
                                                                if (javljanjeObd.getObd().getProsecnaPotrosnja() != 0.0f) prvoObd.setProsecnaPotrosnja(javljanjeObd.getObd().getProsecnaPotrosnja());
                                                                if (javljanjeObd.getObd().getRpm() != 0) prvoObd.setRpm(javljanjeObd.getObd().getRpm());
                                                                if (javljanjeObd.getObd().getTripGorivo() != 0.0f) prvoObd.setTripGorivo(javljanjeObd.getObd().getTripGorivo());
                                                                if (javljanjeObd.getObd().getTripKm() != 0.0f) prvoObd.setTripKm(javljanjeObd.getObd().getTripKm());
                                                                if (javljanjeObd.getObd().getUkupnoVreme() != 0.0f) prvoObd.setUkupnoVreme(javljanjeObd.getObd().getUkupnoVreme());
                                                                if (javljanjeObd.getObd().getUkupnoGorivo() != 0.0f) prvoObd.setUkupnoGorivo(javljanjeObd.getObd().getUkupnoGorivo());
                                                                if (javljanjeObd.getObd().getUkupnoKm() != 0) prvoObd.setUkupnoKm(javljanjeObd.getObd().getUkupnoKm());
                                                            }
                                                        }
                                                    }
                                                }
                                            } catch (Throwable tLog) {
                                                System.out.println("ruptela: error logging extended parse for imei=" + kodUredjaja + " : " + tLog.getMessage());
                                            }
                                        } else {
                                            int snippet = Math.min(300, recordHex.length());
                                            System.out.println("ruptela: vratiExtended returned null for imei=" + kodUredjaja +
                                                    " recordLen=" + recordHex.length() + " snippet=\"" + recordHex.substring(0, snippet) +
                                                    (recordHex.length() > snippet ? "..." : "") + "\"");
                                        }

                                        brZapisa++;
                                    } // end extended loop

                                    try {
                                        out.write(odg);
                                        out.flush();
                                        System.out.println("ruptela: ACK sent imei=" + kodUredjaja);
                                    } catch (IOException ioe) {
                                        System.out.println("ruptela: failed to send ack for imei=" + kodUredjaja + " : " + ioe.getMessage());
                                    }

                                } // end extended handling
                            } else {
                                System.out.println("ruptela: objekat is null for imei=" + kodUredjaja);
                            } // end objekat != null branch

                        } else {
                            System.out.println("ruptela: unknown command " + komanda + " for imei=" + kodUredjaja + " from " + safeRemoteAddr(socket));
                        }

                        if (Thread.currentThread().isInterrupted()) {
                            System.out.println("thread ruptela interrupted exiting");
                            break;
                        }

                    } catch (SocketTimeoutException ste) {
                        break;
                    }
                } // end inner while
            } catch (SocketException se) {
                System.out.println("ruptela: socket exception for imei=" + kodUredjaja + " : " + se.getMessage());
            } catch (IOException ioe) {
                System.out.println("ruptela: I/O error for imei=" + kodUredjaja + " : " + ioe.getMessage());
            } catch (Exception ex) {
                String por = " ruptela: ";
                if (objekat != null) por += objekat.getOznaka() + " ";
                System.out.println("ruptela thread unexpected error " + ex.getMessage() + por);
                ex.printStackTrace();
            } finally {
                closeConnectionQuietly();
            }
        } // end while !isStopped
    }

    private String safeRemoteAddr(Socket s) {
        try {
            return s == null ? "null" : String.valueOf(s.getRemoteSocketAddress());
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private boolean ensureHexAvailable(String hex, int off, int need) {
        if (hex == null) return false;
        return (off + need) <= hex.length();
    }

    private void closeConnectionQuietly() {
        try {
            if (input != null) {
                try { input.close(); } catch (Throwable ignored) {}
                input = null;
            }
            if (out != null) {
                try { out.flush(); } catch (Throwable ignored) {}
                try { out.close(); } catch (Throwable ignored) {}
                out = null;
            }
            if (socket != null && !socket.isClosed()) {
                try { socket.close(); } catch (Throwable ignored) {}
            }
            try {
                if (server != null) server.removeClientSocket(socket);
            } catch (Throwable ignored) {}
        } finally {
            socket = null;
        }
    }
}
