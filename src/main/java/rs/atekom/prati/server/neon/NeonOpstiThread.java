package rs.atekom.prati.server.neon;

import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.LinkedBlockingQueue;

import rs.atekom.prati.server.OpstiServer;
import rs.atekom.prati.server.OpstiThread;

public class NeonOpstiThread extends OpstiThread{

	private String [] niz, da;
	private int brojPromasaja;
	
	public NeonOpstiThread(LinkedBlockingQueue<Socket> queue, OpstiServer srv) {
		super(queue, srv);
		brojPromasaja = 0;
	}

	@Override
	public void run() {
		try {
			socket = socketQueue.take();
			input = socket.getInputStream();
			int br = 0;
			
			while (!isStopped() && !socket.isClosed()) {
				socket.setSoTimeout(vreme);
				br = input.read(data, 0, data.length);
				if(br <= 0){
					break;
				}
				
		    	ulaz = new String(data, 0, br);
		    	
		    	//System.out.println("poruka početak " + inputLine);
		    	niz = ulaz.split(">");
		    	for(int i = 0; i < niz.length; i++){
					if(niz[i].startsWith("<oris") || niz[i].startsWith("#<oris")) {
						da = niz[i].split(",");
						
						if(uredjaj == null){
							kodUredjaja = da[2];
			        		pronadjiPostavi(kodUredjaja);
			        		}
						
						if(objekat != null ){
							//System.out.println("objekat " + inputLine);
							javljanjeTrenutno = server.nProtokol.neonObrada(da, ulaz, objekat);
							obradaJavljanja(javljanjeTrenutno, null);
		        			//ako je javljanje null - greška iz protokola??
		        			}else {
		        				System.out.println("neon objekat null... " + ulaz);
		        				//break;
		        				}
		        		//ako je objekat null
			        	//ako je inputline prazno
			        	}else {
			        		if(!niz[i].equals("#")) {
			        			System.out.println("nije oris... " + niz[i]);
			        			if(brojPromasaja > 3) {
			        				break;
			        				}else {
			        					brojPromasaja++;
			        					}
			        			}
			        		}
					}
		    	
				if (Thread.currentThread().isInterrupted()) {
					System.out.println("thread neon interrupted exiting... ");
					break;
					}
				}
			stop();
			} catch(SocketTimeoutException e){
				//System.out.println("neon thread soket timeout " + e.getMessage());
				stop();
			} catch(SocketException e){
				//System.out.println("neon thread soket greška " + e.getMessage());
				stop();
			} catch (Throwable e) {
				String por = " neon: ";
				if(objekat != null) {
					por += objekat.getOznaka() + " " + test;
				}
				System.out.println("neon thread throwable greška " + e.getMessage() + por);
				stop();
			}
		}
}
