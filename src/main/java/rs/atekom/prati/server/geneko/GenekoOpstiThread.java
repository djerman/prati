package rs.atekom.prati.server.geneko;

import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.LinkedBlockingQueue;

import rs.atekom.prati.server.OpstiServer;
import rs.atekom.prati.server.OpstiThread;

public class GenekoOpstiThread extends OpstiThread{

	private String [] niz, da;
	
	public GenekoOpstiThread(LinkedBlockingQueue<Socket> queue, OpstiServer srv) {
		super(queue, srv);
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
				if (br <= 0) {
					break;
				}
				ulaz = new String(data, 0, br);

				niz = ulaz.split("</fox>");
				for(int i = 0; i < niz.length; i++) {
					if(niz[i].startsWith("<fox>")) {
						da = niz[i].split("\"");
						
	                	if(uredjaj == null){
		                	kodUredjaja = da[1];
		                	pronadjiPostavi(kodUredjaja);
	                	}
	                	
		                if(objekat != null){
		                	javljanjeTrenutno = server.gProtokol.genekoObrada(da[3], objekat);
		                	obradaJavljanja(javljanjeTrenutno, null);
		                	
		                }else {
		                		System.out.println("geneko objekat null... " + niz[i]);
	                			//break;
		                	}
					}else{
						System.out.println("nije ispravan fox.... " + niz[i]);
	                	}
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
				por += objekat.getOznaka();
			}
			System.out.println("neon thread throwable greška " + e.getMessage() + por);
			stop();
		}
	}
}
