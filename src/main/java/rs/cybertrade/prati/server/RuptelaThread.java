package rs.cybertrade.prati.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;
import javax.xml.bind.DatatypeConverter;
import com.google.maps.GeocodingApi;
import com.google.maps.model.GeocodingResult;
import com.google.maps.model.LatLng;
import pratiBaza.tabele.Javljanja;
import pratiBaza.tabele.Obd;
import pratiBaza.tabele.Objekti;
import pratiBaza.tabele.Uredjaji;
import rs.cybertrade.prati.Broadcaster;


public class RuptelaThread implements Runnable{

	private Socket socket = null;
	private LinkedBlockingQueue<Socket> socketQueue;
	private InputStream input;
    private OutputStream out;
    private boolean isStopped = false;
    private byte[] data;
    private Objekti objekat;
    private Uredjaji uredjaj;
    private byte[] odg = {(byte)0x00, (byte)0x02, (byte)0x64, (byte)0x01, (byte)0x13, (byte)0xbc};
    private int offset;
    private RuptelaProtokol zapis;
    private Obd obd, obdStop;
    private Javljanja javljanje, javljanjePoslednje, javljanjeStop;
    private RuptelaServer server;
    
    public RuptelaThread(LinkedBlockingQueue<Socket> queue, RuptelaServer serverRuptela) {
    	socketQueue = queue;
		server = serverRuptela;
		data = new byte[1024];
		zapis = new RuptelaProtokol();
	}
	@Override
	public void run() {
		try {
			socket = socketQueue.take();
			input = socket.getInputStream();
			out = socket.getOutputStream();
			int br = 0;
			String ulaz = "";
			boolean zaustavljeno = false;
			boolean gorivo = false;
			Long imei = null;
			int brojIspodNivoa = 0;//kolil puta je nivop goriva manji za više od 1%
			while(!isStopped && !socket.isClosed()) {
				socket.setSoTimeout(720000);
				br = input.read(data, 0, data.length);
				if (br <= 0) {
					break;
				}
				offset = 0;
				ulaz = DatatypeConverter.printHexBinary(data);
				
				//System.out.println(ulaz);
	            //jednom odrediti koji je uredjaj, objekat, detalji
				offset += 4;//offset = 4;
				if(uredjaj == null) {
					imei = Long.parseLong(ulaz.substring(offset, offset + 16), 16);
					uredjaj = Servis.uredjajServis.nadjiUredjajPoKodu(imei.toString());
					if(uredjaj != null) {
						objekat = Servis.objekatServis.nadjiObjekatPoUredjaju(uredjaj);
						if(objekat != null) {
							javljanjePoslednje = Servis.javljanjeServis.nadjiPoslednjeJavljanjePoObjektu(objekat);
							if(javljanjePoslednje != null && javljanjePoslednje.getBrzina() < 6 && !javljanjePoslednje.getSistemAlarmi().getSifra().equals("")) {
								javljanjeStop = javljanjePoslednje;
								obdStop = Servis.obdServis.nadjiObdPoslednji(objekat);
								}else {
									javljanjeStop = null;
		            				obdStop = null;
		            				}
							}else {
								System.out.println("Ruptela nema objekta: " + imei.toString());
								}
						}else {
							System.out.println("Ruptela nema uredjaja: " + imei.toString());
							}
					//System.out.println("broj uzimanja objekta: " + broj);
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
		            	while(brZapisa < ukZapisa) {
		            		javljanje = zapis.vratiJavljanje(this, objekat, ulaz, komanda);
		            		if(javljanje != null) {
		            			if(javljanje.getBrzina() > 5) {
		            				javljanjeStop = null;
			            			obdStop = null;
			            			zaustavljeno = false;
			            			gorivo = false;
			            			brojIspodNivoa = 0;
			            			}else {
			            				if(javljanjeStop == null) {
			            					javljanjeStop = javljanje;
			            					obdStop = obd;
			            					}
			            				}
		            			//alarm stajanje
	            				if(javljanjeStop != null) {
	            					long vreme = javljanje.getDatumVreme().getTime() - javljanjeStop.getDatumVreme().getTime();
	            					if(!zaustavljeno) {
	            						if(objekat.getVremeStajanja() != 0 && vreme / 1000 > objekat.getVremeStajanja() * 60) {
	            							if(javljanje.getSistemAlarmi().getSifra().equals("0")) {
	            								javljanje.setSistemAlarmi(Servis.sistemAlarmServis.nadjiAlarmPoSifri("1095"));
	            								zaustavljeno = true;
	            								}else {
	            									alarmAdresa(javljanje);
	            									Servis.javljanjeServis.unesiJavljanja(javljanje);
	            									Broadcaster.broadcast(javljanje);
	            									javljanje.setSistemAlarmi(Servis.sistemAlarmServis.nadjiAlarmPoSifri("1095"));
	            									zaustavljeno = true;
	            								}
	            							}
	            						}
	            					}
			            		//alarm gorivo
			            		if(obd != null) {
			            			if(obdStop != null) {
			            				if(!gorivo) {
			            					if(obdStop.getNivoGoriva() - obd.getNivoGoriva() > 1 && brojIspodNivoa > 10) {
			            						if(javljanje.getSistemAlarmi().getSifra().equals("0")) {
			            							javljanje.setSistemAlarmi(Servis.sistemAlarmServis.nadjiAlarmPoSifri("1111"));
			            							gorivo = true;
			            							}else {
			            								alarmAdresa(javljanje);
			            								Servis.javljanjeServis.unesiJavljanja(javljanje);
			            								Broadcaster.broadcast(javljanje);
			            								gorivo = true;
			            							}
			            						}else {
				            						if(obdStop.getNivoGoriva() - obd.getNivoGoriva() > 1) {
				            							brojIspodNivoa++;
				            							}
				            						}
			            					}else {
				            					brojIspodNivoa = 0;
				            					}
			            				}
			            			Servis.obdServis.unesiObd(obd);
			            			}
			            		alarmAdresa(javljanje);
			            		Servis.javljanjeServis.unesiJavljanja(javljanje);
			            		//slanje korisniku obaveštenja
		                        Broadcaster.broadcast(javljanje);
			            		}
		            		brZapisa++;
		            		}
			            out.write(odg);
						out.flush();
		            	}
	            	}else {
	            		System.out.println("druga komanda... " + komanda);
	            		}
	            //ovde je bio odgovor
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
			//System.out.println("ruptela thread throwable greška " + e.getMessage());
			try {
				out.write(odg);
				out.flush();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
    		
		}
		
	}

	public void setOffset(int offset) {
		this.offset = offset;
	}
	
	public int getOffset() {
		return this.offset;
	}
	
	public Javljanja getJavljanje() {
		return javljanje;
	}

	public void setJavljanje(Javljanja javljanje) {
		this.javljanje = javljanje;
	}

	public Obd getObd() {
		return obd;
	}

	public void setObd(Obd obd) {
		this.obd = obd;
	}

	public synchronized boolean isStopped(){
		return this.isStopped;
	}
	
	public synchronized void stop(){
		this.isStopped = true;
    	try{
    		out.write(odg);
    		out.flush();
			if(!socket.isClosed()){
				input.close();
				out.close();
				socket.close();
				server.removeClientSocket(socket);
				//System.out.println("coban stream connection closed ");
			}
		}catch(IOException e){
			System.out.println("ruptela stream connection closed problem...");
		}
		return;
	}
	
    public long razlika(Date vreme){
    	return System.currentTimeMillis() - vreme.getTime();
    }
    
    private void alarmAdresa(Javljanja javljanje) {
    	String adresa = "";
    	LatLng pozicija;
		if(!javljanje.getSistemAlarmi().getSifra().equals("0") && (javljanje.getLat() != 0.00 && javljanje.getLon() != 0.00)){
			if(javljanje.getSistemAlarmi().isAdresa()){
				try {
					Address adressa = Servis.nominatim.getAdress(javljanje.getLat(), javljanje.getLon());
					if(adressa != null){
						if(!adressa.getHouseNumber().equals(""))
							adresa = adressa.getHouseNumber() + ", ";
						if(!adressa.getRoad().equals(""))
							adresa = adresa + adressa.getRoad() + ", ";
						if(!adressa.getSuburb().equals(""))
							adresa = adresa + adressa.getSuburb() + ", ";
						if(!adressa.getCity().equals(""))
							adresa = adresa + adressa.getCity() + " ";
						if(!adressa.getPostcode().equals(""))
							adresa = adresa + adressa.getPostcode();
						if(!adressa.getCounty().equals(""))
							adresa = adresa + ", " + adressa.getCounty() + ", ";
						if(!adressa.getCountry().equals(""))
							adresa = adresa + adressa.getCountry();
						}else{
							pozicija = new LatLng(javljanje.getLat(), javljanje.getLon());
							GeocodingResult[] adresaTrazena= GeocodingApi.reverseGeocode(Servis.gContext, pozicija).await();
							adresa = adresaTrazena[0].formattedAddress;
							}
					if(javljanje.getEventData().equals("0")){
						javljanje.setEventData(adresa);
						}else{
							javljanje.setEventData(javljanje.getEventData() + " " + adresa);
							if(javljanje.getEventData().length() > 250){
								javljanje.setEventData(adresa);
								} 
							}
					} catch (Exception e) {
						System.out.println("Problem sa adresama openstreet mape... ");	
						}
				}
			}
		}
}
