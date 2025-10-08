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
import rs.atekom.prati.server.neon.NeonProtokol;

public class StariNeonServer implements Runnable{

	private final int listeningPort;
	private ServerSocket serverSocket;
	private final ExecutorService pool;
	private ArrayList<Socket> clientSockets;
	private boolean isStopped = false;
	private int poolSize;
	private int rb = 1;
	public SistemAlarmi prekoracenjeBrzine, stajanje, istakanje, izlazak, ulazak, redovno;
	public NeonProtokol nProtokol;
	
	public StariNeonServer(int port, int poolSizeS) {
		clientSockets = new ArrayList<Socket>();
		listeningPort = port;
		poolSize = poolSizeS;
		pool = Executors.newFixedThreadPool(poolSize);
		prekoracenjeBrzine = Servis.sistemAlarmServis.nadjiAlarmPoSifri("6013");
		stajanje = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1095");
		istakanje = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1111");
		izlazak = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1100");
		ulazak = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1101");
		redovno = Servis.sistemAlarmServis.nadjiAlarmPoSifri(String.valueOf(0));
		//nProtokol = new NeonProtokol(this);
	}
	
	@Override
	public void run() {
		LinkedBlockingQueue<Socket> queue = new LinkedBlockingQueue<>();
		System.out.println("NEON");
		try {
			serverSocket = new ServerSocket(listeningPort);
			while (!isStopped()) {
				Socket soket = null;
				try {
					if(clientSockets.size() >= (poolSize - 2)){
	        			   if(!clientSockets.get(0).isClosed()) {
		        			   clientSockets.get(0).getInputStream().close();
		        			   clientSockets.get(0).close();
	        			   }
						removeClientSocket(clientSockets.get(0));
						System.out.println("server neon izbačen soket - " + clientSockets.size());
						}
					soket = serverSocket.accept();
					clientSockets.add(soket);
					//pool.submit(new NeonThread(queue, this));
					queue.put(soket);
					//System.out.println("NEON " + rb + " STARTOVAN" + " od " + ((ThreadPoolExecutor) pool).getActiveCount() + " " + getVreme());
					if(rb == 1 || rb % 1000 == 0)
						System.out.println("NEON " + rb + " STARTOVAN" + " od " + ((ThreadPoolExecutor) pool).getActiveCount() + " " + getVreme());
					rb++;
					} catch (Throwable e) {
					if (isStopped()) {
						System.out.println("server neon is stopped");
						System.out.println(e.getMessage());
					} else {
						System.out.println("error accepting neon client connection" + e.getMessage());
						e.printStackTrace();
						}
					break;
					} 
				}
			try{
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
	     		   }
			} catch (IOException e) {
				System.out.println("greška otvaranja neon soketa: " + e.getMessage());
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
			System.out.println("error stopping neon server" + e.getMessage());
		}
	}
	
	public synchronized void removeClientSocket(Socket clientSocket) {
		try {
			clientSockets.remove(clientSocket);
		} catch (Throwable e) {
			System.out.println("error removing neon sccket" + e.getMessage());
		}
	}
	
    public String getVreme() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    }
}
