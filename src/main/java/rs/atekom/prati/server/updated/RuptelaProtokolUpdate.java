package rs.atekom.prati.server.updated;

import java.sql.Timestamp;
import java.util.Date;

import pratiBaza.tabele.Javljanja;
import pratiBaza.tabele.Obd;
import pratiBaza.tabele.Objekti;
import pratiBaza.tabele.SistemAlarmi;
import rs.atekom.prati.server.JavljanjeObd;
import rs.atekom.prati.server.Servis;

/**
 * Minimalna migracija RuptelaProtokol -> RuptelaProtokolUpdate
 * -- само промењено име класе; логика парсирања (substring хекса) непромењена.
 * -- иста map-а за upisi1bajt/upisi2bajta/upisi4bajta као у оригиналу.
 */
public class RuptelaProtokolUpdate {

    private SistemAlarmi redovno, sos, aktiviran, deaktiviran;

    public RuptelaProtokolUpdate() {
        // као и раније - узима из Servis-а
        redovno = Servis.sistemAlarmServis.nadjiAlarmPoSifri("0");
        sos = Servis.sistemAlarmServis.nadjiAlarmPoSifri("6022");
        aktiviran = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1092");
        deaktiviran = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1091");
    }

    public JavljanjeObd vratiJavljanje(int offset, Objekti objekat, String poruka) {
        Date sada = new Date();
        Javljanja javljanje = new Javljanja();
        javljanje.setObjekti(objekat);
        javljanje.setValid(true);
        javljanje.setVirtualOdo(0.0f);

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

        // datum vreme
        Timestamp datumVreme = new Timestamp(Long.parseLong(poruka.substring(offset, offset + 8), 16) * 1000);
        javljanje.setDatumVreme(datumVreme);
        obd.setDatumVreme(datumVreme);
        offset += 8;
        // timestamp ext
        offset += 2;
        // priority
        offset += 2;
        // longitude
        javljanje.setLon((double)((int)Long.parseLong(poruka.substring(offset, offset + 8 ), 16)) / 10000000);
        offset += 8;
        // latitude
        javljanje.setLat((double)((int)Long.parseLong(poruka.substring(offset, offset + 8 ), 16)) / 10000000);
        offset += 8;
        // altitude
        javljanje.setVisina((float)(Integer.parseInt(poruka.substring(offset, offset + 4), 16) / 10));
        offset += 4;
        // angle - pravac
        javljanje.setPravac((float)(Integer.parseInt(poruka.substring(offset, offset + 4), 16) / 100));
        offset += 4;
        // sateliti
        offset += 2;
        // brzina
        javljanje.setBrzina((Integer.parseInt(poruka.substring(offset, offset + 4), 16)));
        offset += 4;
        // HDOP
        offset += 2;
        // event id (standard)
        int eventId = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
        offset += 2;

        javljanje.setSistemAlarmi(redovno);
        javljanje.setEventData("0");
        javljanje.setIbutton("0");

        // 1 bajt polja
        int brJedan = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
        offset += 2;
        int i = 0;
        while(i < brJedan) {
            upisi1bajt(Integer.parseInt(poruka.substring(offset, offset + 2), 16), poruka.substring(offset + 2, offset + 4), javljanje, eventId, obd);
            offset += 4;
            i++;
        }
        // 2 bajta polja
        int brDva = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
        offset += 2;
        int j = 0;
        while(j < brDva) {
            upisi2bajta(Integer.parseInt(poruka.substring(offset, offset + 2), 16), poruka.substring(offset + 2, offset + 6), obd);
            offset += 6;
            j++;
        }
        // 4 bajta polja
        int brCetiri = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
        offset += 2;
        int k = 0;
        while(k < brCetiri) {
            upisi4bajta(Integer.parseInt(poruka.substring(offset, offset + 2), 16), poruka.substring(offset + 2, offset + 10), javljanje, obd);
            offset += 10;
            k++;
        }
        // 8 bajtova
        int brOsam = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
        offset += 2;
        int l = 0;
        while(l < brOsam) {
            offset += 18;
            l++;
        }

        obd.setKreirano(new Timestamp(sada.getTime()));
        obd.setIzmenjeno(new Timestamp(sada.getTime()));
        javljanje.setKreirano(new Timestamp(sada.getTime()));
        javljanje.setIzmenjeno(new Timestamp(sada.getTime()));
        if(obd.getNivoGoriva() == 0.0f && obd.getProsecnaPotrosnja() == 0.0f && obd.getRpm() == 0 && obd.getTemperatura() == 0
                && obd.getUkupnoGorivo() == 0.0f && obd.getUkupnoKm() == 0){
            obd = null;
        }

        return new JavljanjeObd(javljanje, obd);
    }

    public JavljanjeObd vratiExtended(int offset, Objekti objekat, String poruka) {
        Date sada = new Date();
        Javljanja javljanje = new Javljanja();
        javljanje.setObjekti(objekat);
        javljanje.setValid(true);
        javljanje.setVirtualOdo(0.0f);

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

        // datum vreme
        Timestamp datumVreme = new Timestamp(Long.parseLong(poruka.substring(offset, offset + 8), 16) * 1000);
        javljanje.setDatumVreme(datumVreme);
        obd.setDatumVreme(datumVreme);
        offset += 8;
        // timestamp ext
        offset += 2;
        // Record ext
        offset += 2;
        // priority
        offset += 2;
        // longitude
        javljanje.setLon((double)((int)Long.parseLong(poruka.substring(offset, offset + 8 ), 16)) / 10000000);
        offset += 8;
        // latitude
        javljanje.setLat((double)((int)Long.parseLong(poruka.substring(offset, offset + 8 ), 16)) / 10000000);
        offset += 8;
        // altitude
        javljanje.setVisina((float)(Integer.parseInt(poruka.substring(offset, offset + 4), 16) / 10));
        offset += 4;
        // angle
        javljanje.setPravac((float)(Integer.parseInt(poruka.substring(offset, offset + 4), 16) / 100));
        offset += 4;
        // sateliti
        offset += 2;
        // brzina
        javljanje.setBrzina((Integer.parseInt(poruka.substring(offset, offset + 4), 16)));
        offset += 4;
        // HDOP
        offset += 2;
        // event id (2 bytes)
        int eventId = Integer.parseInt(poruka.substring(offset, offset + 4), 16);
        offset += 4;

        javljanje.setSistemAlarmi(redovno);
        javljanje.setEventData("0");
        javljanje.setIbutton("0");

        int brJedan = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
        offset += 2;
        int i = 0;
        while(i < brJedan) {
            upisi1bajt(Integer.parseInt(poruka.substring(offset, offset + 4), 16), poruka.substring(offset + 4, offset + 6), javljanje, eventId, obd);
            offset += 6;
            i++;
        }
        int brDva = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
        offset += 2;
        int j = 0;
        while(j < brDva) {
            upisi2bajta(Integer.parseInt(poruka.substring(offset, offset + 4), 16), poruka.substring(offset + 4, offset + 8), obd);
            offset += 8;
            j++;
        }
        int brCetiri = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
        offset += 2;
        int k = 0;
        while(k < brCetiri) {
            upisi4bajta(Integer.parseInt(poruka.substring(offset, offset + 4), 16), poruka.substring(offset + 4, offset + 12), javljanje, obd);
            offset += 12;
            k++;
        }
        int brOsam = Integer.parseInt(poruka.substring(offset, offset + 2), 16);
        offset += 2;
        int l = 0;
        while(l < brOsam) {
            offset += 20;
            l++;
        }

        obd.setKreirano(new Timestamp(sada.getTime()));
        obd.setIzmenjeno(new Timestamp(sada.getTime()));
        javljanje.setKreirano(new Timestamp(sada.getTime()));
        javljanje.setIzmenjeno(new Timestamp(sada.getTime()));
        if(obd.getNivoGoriva() == 0.0f && obd.getProsecnaPotrosnja() == 0.0f && obd.getRpm() == 0 && obd.getTemperatura() == 0
                && obd.getUkupnoGorivo() == 0.0f && obd.getUkupnoKm() == 0){
            obd = null;
        }

        return new JavljanjeObd(javljanje, obd);
    }

    private void upisi1bajt(int id, String vrednost, Javljanja javljanje, int eventId, Obd obd) {
        int rezultat = Integer.parseInt(vrednost, 16);
        switch (id) {
            case 2:
                break;
            case 3:
                break;
            case 4:
                if(rezultat == 1) {
                    javljanje.setSistemAlarmi(sos);
                }
                break;
            case 5:
                if(rezultat == 1) {
                    javljanje.setKontakt(true);
                    if(eventId == 5) {
                        javljanje.setSistemAlarmi(aktiviran);
                    }
                } else {
                    javljanje.setKontakt(false);
                    if(eventId == 5) {
                        javljanje.setSistemAlarmi(deaktiviran);
                    }
                }
                break;
            case 96:
                obd.setTemperatura(rezultat);
                break;
            case 98:
                obd.setNivoGoriva((float) rezultat);
                break;
            case 103:
                obd.setOpterecenje((float)rezultat);
                break;
            case 115:
                if(!javljanje.isKontakt()) {
                    obd.setTemperatura(0);
                } else {
                    obd.setTemperatura(rezultat - 40);
                }
                break;
            case 207:
                if(rezultat < 251) {
                    obd.setNivoGoriva((float)(rezultat * 0.4));
                } else {
                    obd.setNivoGoriva(0.0f);
                }
                break;
            case 251:
                if(rezultat == 1) {
                    javljanje.setKontakt(true);
                    if(eventId == 5) {
                        javljanje.setSistemAlarmi(aktiviran);
                    }
                } else {
                    javljanje.setKontakt(false);
                    if(eventId == 5) {
                        javljanje.setSistemAlarmi(deaktiviran);
                    }
                }
                break;
            default:
                break;
        }
    }

    private void upisi2bajta(int id, String vrednost, Obd obd) {
        int rez = Integer.parseInt(vrednost, 16);
        switch (id) {
            case 29:
                obd.setAkumulator((float)(rez/1000));
                break;
            case 94:
                Double rpmObd = rez * 0.25;
                obd.setRpm(rpmObd.intValue());
                break;
            case 100:
                obd.setProsecnaPotrosnja((float)rez);
                break;
            case 107:
                obd.setUkupnoVreme((float)rez);
                break;
            case 116:
                obd.setProsecnaPotrosnja((float)(rez * 0.05));
                break;
            case 197:
                Double rpm = rez * 0.125;
                obd.setRpm(rpm.intValue());
                break;
            case 205:
                obd.setTripGorivo((float)rez);
                break;
            default:
                break;
        }
    }

    private void upisi4bajta(int id, String vrednost, Javljanja javljanje, Obd obd) {
        long rez = Long.parseLong(vrednost, 16);
        switch (id) {
            case 65:
                javljanje.setVirtualOdo((float)rez / 1000);
                break;
            case 114:
                obd.setUkupnoKm((int)((rez * 5)/ 1000));
                break;
            case 203:
                obd.setUkupnoVreme((float)(rez * 0.05));
                break;
            case 208:
                if(obd.getUkupnoGorivo() == 0.0f) {
                    obd.setUkupnoGorivo((float)(rez * 0.5));
                }
                break;
            default:
                break;
        }
    }
}
