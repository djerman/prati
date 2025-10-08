package rs.atekom.prati.server.ruptela;

import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.LinkedBlockingQueue;
import javax.xml.bind.DatatypeConverter;
import pratiBaza.tabele.Javljanja;
import pratiBaza.tabele.Obd;
import rs.atekom.prati.server.JavljanjeObd;
import rs.atekom.prati.server.OpstiServer;
import rs.atekom.prati.server.OpstiThread;

public class RuptelaOpstiThread extends OpstiThread{

	public RuptelaOpstiThread(LinkedBlockingQueue<Socket> queue, OpstiServer server) {
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
				socket.setSoTimeout(vreme);
				br = input.read(data, 0, data.length);
				if (br <= 0) {
					break;
				}
				offset = 0;
				ulaz = DatatypeConverter.printHexBinary(data);
				
				//System.out.println("ruptela " + ulaz);
				//System.out.println(" ");
	            //jednom odrediti koji je uredjaj, objekat, detalji
				offset += 4;//offset = 4;
				
				if(uredjaj == null) {
					Long imei = Long.parseLong(ulaz.substring(offset, offset + 16), 16);
					kodUredjaja = imei.toString();
					pronadjiPostavi(kodUredjaja);
				}
				
	            offset += 16;//offset = 20
	        	//ako je komanda 01
	            int komanda = Integer.parseInt(ulaz.substring(offset, offset + 2), 16);
	            
	            //System.out.println(imei + " komanda je: " + komanda);
	            if(komanda == 1 || komanda == 68) {
	            	//da dobijem broj zapisa
	            	offset += 4;//offset = 24, uključuje command id i recordsLeft
	            	if(objekat != null) {
	            		int brZapisa = 0;
		            	offset += 2;
		            	int ukZapisa = Integer.parseInt(ulaz.substring(offset - 2, offset),  16);
		            	//System.out.println("zapisa: " + ukZapisa + " za " + objekat.getOznaka());
            			/*if(objekat.getId().equals(Long.parseLong("2"))) {
            				System.out.println(ulaz);
            			}**/
		            	
	            		//standardni protokol
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
			            		JavljanjeObd javljanjeObd = server.rProtokol.vratiJavljanje(0, objekat, ulaz.substring(pocetak, offset));
			            		obradaJavljanja(javljanjeObd.getJavljanje(), javljanjeObd.getObd()); 
			            		brZapisa++;
			            	}		            	
			            	out.write(odg);
							out.flush();
	            		//prošireni protokol
	            		}else {
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
			            		//zapisi.add(ulaz.substring(pocetak, offset));
			            		JavljanjeObd javljanjeObd = server.rProtokol.vratiExtended(0, objekat, ulaz.substring(pocetak, offset));
			            		if(drugi <= prvi) {
				            		if(drugi == 0) {
				            			prvo = javljanjeObd.getJavljanje();
				            			prvoObd = javljanjeObd.getObd();
				            			obradaJavljanja(prvo, prvoObd);
				            		}else {
				            			if(prvoObd == null) {
				            				prvoObd = javljanjeObd.getObd();
				            			}else {
		            						if(javljanjeObd.getObd().getAkumulator() != 0.0f) {
		            							prvoObd.setAkumulator(javljanjeObd.getObd().getAkumulator());
		            						}
		            						if(javljanjeObd.getObd().getGas() != 0.0f) {
		            							prvoObd.setGas(javljanjeObd.getObd().getGas());
		            						}
		            						if(javljanjeObd.getObd().getGreske() != "") {
		            							prvoObd.setGreske(javljanjeObd.getObd().getGreske());
		            						}
		            						if(javljanjeObd.getObd().getNivoGoriva() != 0.0f) {
		            							prvoObd.setNivoGoriva(javljanjeObd.getObd().getNivoGoriva());
		            						}
		            						if(javljanjeObd.getObd().getOpterecenje() != 0.0f) {
		            							prvoObd.setOpterecenje(javljanjeObd.getObd().getOpterecenje());
		            						}
		            						if(javljanjeObd.getObd().getProsecnaPotrosnja() != 0.0f) {
		            							prvoObd.setProsecnaPotrosnja(javljanjeObd.getObd().getProsecnaPotrosnja());
		            						}
		            						if(javljanjeObd.getObd().getRpm() != 0) {
		            							prvoObd.setRpm(javljanjeObd.getObd().getRpm());
		            						}
		            						if(javljanjeObd.getObd().getTripGorivo() != 0.0f) {
		            							prvoObd.setTripGorivo(javljanjeObd.getObd().getTripGorivo());
		            						}
		            						if(javljanjeObd.getObd().getTripKm() != 0.0f) {
		            							prvoObd.setTripKm(javljanjeObd.getObd().getTripKm());
		            						}
		            						if(javljanjeObd.getObd().getUkupnoVreme() != 0.0f) {
		            							prvoObd.setUkupnoVreme(javljanjeObd.getObd().getUkupnoVreme());
		            						}
		            						if(javljanjeObd.getObd().getUkupnoGorivo() != 0.0f) {
		            							prvoObd.setUkupnoGorivo(javljanjeObd.getObd().getUkupnoGorivo());
		            						}
		            						if(javljanjeObd.getObd().getUkupnoKm() != 0.0f) {
		            							prvoObd.setUkupnoKm(javljanjeObd.getObd().getUkupnoKm());
		            						}
				            			}
				            		}
			            		}
			            		
			            		brZapisa++;
	            			}
			            	out.write(odg);
							out.flush();
	            		}

		            	}
	            	}else {
	            		System.out.println("ruptela druga komanda... " + komanda);
	            		}
				//System.out.println("odgovor " + imei);
				if (Thread.currentThread().isInterrupted()) {
					System.out.println("thread ruptela interrupted exiting");
					return;
					}
				}
			stop();
		} catch(SocketTimeoutException e){
			//System.out.println("ruptela thread soket timeout " + e.getMessage());
			stop();
		} catch(SocketException e){
			//System.out.println("ruptela thread soket greška " + e.getMessage());
	    	stop();
		} catch (Throwable e) {
			String por = " ruptela: ";
			if(objekat != null) {
				por += objekat.getOznaka() + " " + test;
			}
			System.out.println("ruptela thread throwable greška " + e.getMessage() + por);
			/*try {
				out.write(odg);
				out.flush();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}**/
			stop();
		}
	}

}
