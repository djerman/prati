package rs.atekom.prati.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.stereotype.Service;

import pratiBaza.tabele.Javljanja;
import pratiBaza.tabele.SistemAlarmi;
import rs.atekom.prati.server.geneko.GenekoOpstiThread;
import rs.atekom.prati.server.geneko.GenekoProtokol;
import rs.atekom.prati.server.neon.NeonOpstiThread;
import rs.atekom.prati.server.neon.NeonProtokol;
import rs.atekom.prati.server.ruptela.RuptelaOpstiThread;
import rs.atekom.prati.server.ruptela.RuptelaProtokol;
import rs.atekom.prati.view.komponente.Izvrsavanje;

@Service
public class OpstiServer implements Runnable{

	private final int listeningPort;
	private ServerSocket serverSocket;
	private final ExecutorService pool;
	private ArrayList<Socket> clientSockets;
	private boolean isStopped = false;
	private int poolSize;
	private int rb = 1;
	public SistemAlarmi prekoracenjeBrzine, stajanje, istakanje, izlazak, ulazak, redovno;
	public NeonProtokol nProtokol;
	public RuptelaProtokol rProtokol;
	public GenekoProtokol gProtokol;
	public Izvrsavanje izvrsavanje;
	private String server;
	
	public OpstiServer(int port, int poolSizeS) {
		clientSockets = new ArrayList<Socket>();
		listeningPort = port;
		poolSize = poolSizeS;
		pool = Executors.newFixedThreadPool(poolSize);
		switch (listeningPort) {
		case 9000: nProtokol = new NeonProtokol(this);
                   server = " NEON ";
	        break;
		case 9030: gProtokol = new GenekoProtokol(this);
                   server = " GENEKO ";
            break;
		case 9040: rProtokol = new RuptelaProtokol();
		           server = " RUPTELA ";
			break;

		default:
			break;
		}
		prekoracenjeBrzine = Servis.sistemAlarmServis.nadjiAlarmPoSifri("6013");
		stajanje = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1095");
		istakanje = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1111");
		izlazak = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1100");
		ulazak = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1101");
		redovno = Servis.sistemAlarmServis.nadjiAlarmPoSifri("0");
		izvrsavanje = new Izvrsavanje();
	}
	
	@Override
	public void run() {
		LinkedBlockingQueue<Socket> queue = new LinkedBlockingQueue<>();
	     System.out.println(server);
	     try {
	    	 serverSocket = new ServerSocket(listeningPort);
	    	 while(!isStopped()) {
	    		 Socket soket = null;
	    		 try {
	    			 /*if(clientSockets.size() >= (poolSize - 2)) {
	    				 clientSockets.get(0).getInputStream().close();
	    				 clientSockets.get(0).close();
	    				 removeClientSocket(clientSockets.get(0));
	    				 System.out.println("server " + server + " izbačen soket - " + clientSockets.size());
	    				 }**/
	    			 /*int brUklonjenihSoketa = 0;
	    			 for(Socket sok : clientSockets) {
	    				 if(sok.isClosed()) {
	    					 clientSockets.remove(sok);
	    					 removeClientSocket(sok);
	    					 brUklonjenihSoketa++;
	    				 }
	    			 }**/
	    			 
	    			 soket = serverSocket.accept();
	    			 clientSockets.add(soket);
	    			 
	    			 switch (listeningPort) {
	    			 case 9000: pool.submit(new NeonOpstiThread(queue, this));
	    			    break;
	    			 case 9030: pool.submit(new GenekoOpstiThread(queue, this));
	    			    break;
	    			 case 9040: pool.submit(new RuptelaOpstiThread(queue, this));
	    			    break;
	    			 default:
						break;
						}
	    			 queue.put(soket);
	    			 
	    			 /*if(clientSockets.size() % 100 == 0) {
	    				 System.out.println("server " + server + " izbačenih soketa - " + brUklonjenihSoketa + " ukupno ostalo " + clientSockets.size());
	    			 }**/
	    			 
	    			 if(rb == 1 || rb % 1000 == 0) {
	    				 System.out.println();
	    				 System.out.println("************************************************************");
	    				 System.out.println(server + rb + " STARTOVAN" + " od " + ((ThreadPoolExecutor) pool).getActiveCount() + " " + getVreme() + " *****");
	    				 System.out.println("************************************************************");
	    				 System.out.println();
	    				 }
	    			 rb++;
	    			 } catch (Throwable e){
	    				 if (isStopped()) {
	    					 System.out.println("server " + server + "is stopped");
	    					 System.out.println(e.getMessage());
	    					 } else {
	    						 System.out.println("error accepting" + server + "client connection" + e.getMessage());
	    						 e.printStackTrace();
	    						 }
	    				 break;
	    				 }
	    		 }
				/*try{
					this.pool.shutdown();
		     	   for(Socket s: clientSockets){
		     		   try{
		     			   s.close();
		     			   }catch (Throwable e){
		     				   System.out.println("soket neon zatvoren: " + e.getMessage());
		     				   }
		     		   }
		     	   }catch(Throwable e){
		     		   System.out.println("shutdown neon: " + e.getMessage());
		     		   }**/
	    	 } catch (IOException ex) {
	    		 System.out.println("greška otvaranja" + server + "soketa: " + ex.getMessage());
	    		 }
	     }

	
	private synchronized boolean isStopped() {
		return this.isStopped;
		}
	
	public synchronized void stop() {
		isStopped = true;
		try {
			serverSocket.close();
			} catch (Throwable e) {
				System.out.println("error stopping server " + server + e.getMessage());
				}
		}
	
	public synchronized void removeClientSocket(Socket clientSocket) {
		try {
			clientSockets.remove(clientSocket);
			} catch (Throwable e) {
				System.out.println("error removing" + server + "socket" + e.getMessage());
				}
		}
	
    public String getVreme() {
    	return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    	}
    
    public void postaviAlarmStajanje(Javljanja javljanje) {
    	if(stajanje != null && stajanje.isAktivan()) {
        	javljanje.setSistemAlarmi(stajanje);
    	}else {
    		if(redovno != null) {
    			javljanje.setSistemAlarmi(redovno);
    		}
    	}
    }
    
    public void postaviAlarmPrekoracenjeBrzine(Javljanja javljanje) {
    	if(prekoracenjeBrzine != null && prekoracenjeBrzine.isAktivan()) {
        	javljanje.setSistemAlarmi(prekoracenjeBrzine);
    	}else {
    		if(redovno != null) {
    			javljanje.setSistemAlarmi(redovno);
    		}
    	}
    }
    
    public void postaviAlarmIstakanje(Javljanja javljanje) {
    	if(istakanje != null && istakanje.isAktivan()) {
        	javljanje.setSistemAlarmi(istakanje);
    	}else {
    		if(redovno != null) {
    			javljanje.setSistemAlarmi(redovno);
    		}
    	}
    }
    
    public void postaviAlarmIzlazakIzZone(Javljanja javljanje) {
    	if(izlazak != null && izlazak.isAktivan()) {
        	javljanje.setSistemAlarmi(izlazak);
    	}else {
    		if(redovno != null) {
    			javljanje.setSistemAlarmi(redovno);
    		}
    	}
    }
    public void postaviAlarmUlazakUZonu(Javljanja javljanje) {
    	if(ulazak != null && ulazak.isAktivan()) {
        	javljanje.setSistemAlarmi(ulazak);
    	}else {
    		if(redovno != null) {
    			javljanje.setSistemAlarmi(redovno);
    		}
    	}
    }
}
