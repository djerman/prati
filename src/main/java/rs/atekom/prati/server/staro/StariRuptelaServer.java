package rs.atekom.prati.server.staro;

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

import pratiBaza.tabele.SistemAlarmi;
import rs.atekom.prati.server.Servis;
import rs.atekom.prati.server.ruptela.RuptelaProtokol;

public class StariRuptelaServer implements Runnable{

	private final int listeningPort;
	private ServerSocket serverSocket;
	private final ExecutorService pool;
	private ArrayList<Socket> clientSockets;
	private boolean isStopped = false;
	private int poolSize;
	private int rb = 1;
	public SistemAlarmi prekoracenjeBrzine, stajanje, istakanje, izlazak, ulazak;
	public RuptelaProtokol zapis;
	
	public StariRuptelaServer(int port, int poolSizeS) {
		clientSockets = new ArrayList<Socket>();
		listeningPort = port;
		poolSize = poolSizeS;
		pool = Executors.newFixedThreadPool(poolSize);
		zapis = new RuptelaProtokol();
		prekoracenjeBrzine = Servis.sistemAlarmServis.nadjiAlarmPoSifri("6013");
		stajanje = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1095");
		istakanje = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1111");
		izlazak = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1100");
		ulazak = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1101");
	}
	
	@Override
	public void run() {
		LinkedBlockingQueue<Socket> queue = new LinkedBlockingQueue<>();
	     System.out.println("RUPTELA SERVER");
	     try {
	    	 serverSocket = new ServerSocket(listeningPort);
	    	 while(!isStopped()) {
	    		 Socket soket = null;
	    		 try {
	    			 if(clientSockets.size() >= (poolSize - 2)) {
	    				 clientSockets.get(0).getInputStream().close();
	    				 clientSockets.get(0).close();
	    				 removeClientSocket(clientSockets.get(0));
	    				 System.out.println("server ruptela izbačen soket - " + clientSockets.size());
	    				 }
	    			 soket = serverSocket.accept();
	    			 clientSockets.add(soket);
	    			 //pool.submit(new RuptelaThread(queue, this));
	    			 queue.put(soket);
	    			 if(rb == 1 || rb % 1000 == 0) {
	    				 System.out.println("RUPTELA " + rb + " STARTOVAN" + " od " + ((ThreadPoolExecutor) pool).getActiveCount() + " " + getVreme());
	    				 }
	    			 rb++;
	    			 } catch (Throwable e){
	    				 if (isStopped()) {
	    					 System.out.println("server ruptela is stopped");
	    					 System.out.println(e.getMessage());
	    					 } else {
	    						 System.out.println("error accepting ruptela client connection" + e.getMessage());
	    						 e.printStackTrace();
	    						 }
	    				 break;
	    				 }
	    		 }
	    	 } catch (IOException ex) {
	    		 System.out.println("greška otvaranja ruptela soketa: " + ex.getMessage());
	    		 }
	     }
	
	private synchronized boolean isStopped() {
		return this.isStopped;
		}
	
	public synchronized void stop() {
		this.isStopped = true;
		try {
			this.serverSocket.close();
			} catch (Throwable e) {
				System.out.println("error stopping server" + e.getMessage());
				}
		}
	
	public synchronized void removeClientSocket(Socket clientSocket) {
		try {
			clientSockets.remove(clientSocket);
			} catch (Throwable e) {
				System.out.println("error removing ruptela socket" + e.getMessage());
				}
		}
	
    public String getVreme() {
    	return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    	}
    }
