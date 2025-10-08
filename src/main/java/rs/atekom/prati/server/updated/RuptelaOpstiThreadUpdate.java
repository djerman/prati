package rs.atekom.prati.server.updated;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.LinkedBlockingQueue;
import javax.xml.bind.DatatypeConverter;

import pratiBaza.tabele.Javljanja;
import pratiBaza.tabele.Obd;
import rs.atekom.prati.server.JavljanjeObd;
// Мала, али важно: користимо OpstiServerUpdate и OpstiThreadUpdate (минимална промена)
import rs.atekom.prati.server.updated.OpstiServerUpdate;
import rs.atekom.prati.server.updated.OpstiThreadUpdate;

/**
 * Minimalna migracija originalnog RuptelaOpstiThread -> RuptelaOpstiThreadUpdate
 * -- само су промењени родитељ (OpstiThreadUpdate) и тип сервера (OpstiServerUpdate).
 * -- Логика парсирања поруке је остављена као у оригиналу (substring по хексу).
 *
 * Ако желиш да касније учинимо парсер робуснијим (byte[] итд.) - радимо у следећем кораку.
 */
public class RuptelaOpstiThreadUpdate extends OpstiThreadUpdate {

    public RuptelaOpstiThreadUpdate(LinkedBlockingQueue<Socket> queue, OpstiServerUpdate server) {
        super(queue, server);
    }

    @Override
    public void run() {
        try {
            socket = socketQueue.take();
            input = socket.getInputStream();
            out = socket.getOutputStream();
            int br = 0;
            while(!isStopped && !socket.isClosed()) {
                try {
                    socket.setSoTimeout(vreme);
                } catch (Throwable ignore) {}
                br = input.read(data, 0, data.length);
                if (br <= 0) {
                    break;
                }
                offset = 0;
                // овде се ради као и раније — чита се пун бајт[] у data па се претвара у хекс
                ulaz = DatatypeConverter.printHexBinary(data);

                // једном одредити који је уређај, објекат, детаљи
                offset += 4; // skip preamble

                if(uredjaj == null) {
                    try {
                        Long imei = Long.parseLong(ulaz.substring(offset, offset + 16), 16);
                        kodUredjaja = imei.toString();
                        pronadjiPostavi(kodUredjaja);
                    } catch (Exception e) {
                        System.out.println("ruptela: cannot parse imei from hex snippet: " + (ulaz.length() > 40 ? ulaz.substring(0,40) : ulaz));
                        break;
                    }
                }

                offset += 16; // imei consumed
                // ako je komanda 01 ili 44 (0x44=68)
                int komanda = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);

                if(komanda == 1 || komanda == 68) {
                    // da dobijem broj zapisa
                    offset += 4; // uključuje command id i recordsLeft (original code)
                    if(objekat != null) {
                        int brZapisa = 0;
                        offset += 2;
                        int ukZapisa = Integer.parseInt(ulaz.substring(offset - 2, offset),  16);

                        // standardni protokol
                        if(komanda == 1) {
                            while(brZapisa < ukZapisa) {
                                int pocetak = offset;
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

                                String recordHex = ulaz.substring(pocetak, offset);
                                try {
                                    JavljanjeObd javljanjeObd = ((OpstiServerUpdate)server).rProtokol.vratiJavljanje(0, objekat, recordHex);
                                    if (javljanjeObd != null && javljanjeObd.getJavljanje() != null) {
                                        obradaJavljanja(javljanjeObd.getJavljanje(), javljanjeObd.getObd());
                                    } else {
                                        // diagnostic minimal
                                        System.out.println("ruptela: vratiJavljanje returned null for recordLen=" + recordHex.length() +
                                                " imei=" + kodUredjaja);
                                    }
                                } catch (Throwable t) {
                                    System.out.println("ruptela: parser threw for imei=" + kodUredjaja + " : " + t.getMessage());
                                    t.printStackTrace();
                                }

                                brZapisa++;
                            }
                            // ack kao раније
                            try {
                                out.write(odg);
                                out.flush();
                                //System.out.println("ruptela: ACK sent for imei=" + kodUredjaja);
                            } catch (IOException ioe) {
                                //System.out.println("ruptela: failed to send ack for imei=" + kodUredjaja + " : " + ioe.getMessage());
                            }

                        } else { // prošireni protokol (komanda == 68)
                            Javljanja prvo = null;
                            Obd prvoObd = null;
                            while(brZapisa < ukZapisa) {
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

                                String recordHex = ulaz.substring(pocetak, offset);
                                try {
                                    JavljanjeObd javljanjeObd = ((OpstiServerUpdate)server).rProtokol.vratiExtended(0, objekat, recordHex);
                                    if (javljanjeObd != null) {
                                        if (drugi <= prvi) {
                                            if (drugi == 0) {
                                                prvo = javljanjeObd.getJavljanje();
                                                prvoObd = javljanjeObd.getObd();
                                                obradaJavljanja(prvo, prvoObd);
                                            } else {
                                                if (prvoObd == null) {
                                                    prvoObd = javljanjeObd.getObd();
                                                } else {
                                                    if (javljanjeObd.getObd() != null) {
                                                        if(javljanjeObd.getObd().getAkumulator() != 0.0f) prvoObd.setAkumulator(javljanjeObd.getObd().getAkumulator());
                                                        if(javljanjeObd.getObd().getGas() != 0.0f) prvoObd.setGas(javljanjeObd.getObd().getGas());
                                                        if(javljanjeObd.getObd().getGreske() != "") prvoObd.setGreske(javljanjeObd.getObd().getGreske());
                                                        if(javljanjeObd.getObd().getNivoGoriva() != 0.0f) prvoObd.setNivoGoriva(javljanjeObd.getObd().getNivoGoriva());
                                                        if(javljanjeObd.getObd().getOpterecenje() != 0.0f) prvoObd.setOpterecenje(javljanjeObd.getObd().getOpterecenje());
                                                        if(javljanjeObd.getObd().getProsecnaPotrosnja() != 0.0f) prvoObd.setProsecnaPotrosnja(javljanjeObd.getObd().getProsecnaPotrosnja());
                                                        if(javljanjeObd.getObd().getRpm() != 0) prvoObd.setRpm(javljanjeObd.getObd().getRpm());
                                                        if(javljanjeObd.getObd().getTripGorivo() != 0.0f) prvoObd.setTripGorivo(javljanjeObd.getObd().getTripGorivo());
                                                        if(javljanjeObd.getObd().getTripKm() != 0.0f) prvoObd.setTripKm(javljanjeObd.getObd().getTripKm());
                                                        if(javljanjeObd.getObd().getUkupnoVreme() != 0.0f) prvoObd.setUkupnoVreme(javljanjeObd.getObd().getUkupnoVreme());
                                                        if(javljanjeObd.getObd().getUkupnoGorivo() != 0.0f) prvoObd.setUkupnoGorivo(javljanjeObd.getObd().getUkupnoGorivo());
                                                        if(javljanjeObd.getObd().getUkupnoKm() != 0) prvoObd.setUkupnoKm(javljanjeObd.getObd().getUkupnoKm());
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        System.out.println("ruptela: vratiExtended returned null for imei=" + kodUredjaja);
                                    }
                                } catch (Throwable t) {
                                    System.out.println("ruptela: parser threw (extended) for imei=" + kodUredjaja + " : " + t.getMessage());
                                    t.printStackTrace();
                                }

                                brZapisa++;
                            }

                            try {
                                out.write(odg);
                                out.flush();
                                //System.out.println("ruptela: ACK sent for imei=" + kodUredjaja);
                            } catch (IOException ioe) {
                                System.out.println("ruptela: failed to send ack for imei=" + kodUredjaja + " : " + ioe.getMessage());
                            }
                        } // end extended handling
                    } // end objekat != null
                } else {
                    System.out.println("ruptela druga komanda... " + komanda);
                }

                if (Thread.currentThread().isInterrupted()) {
                    System.out.println("thread ruptela interrupted exiting");
                    return;
                }
            }
            stop();
        } catch(SocketTimeoutException e){
            stop();
        } catch(SocketException e){
            stop();
        } catch (Throwable e) {
            String por = " ruptela: ";
            if(objekat != null) {
                por += objekat.getOznaka() + " " + test;
            }
            System.out.println("ruptela thread throwable greška " + e.getMessage() + por);
            stop();
        }
    }
}
