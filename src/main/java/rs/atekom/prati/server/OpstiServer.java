package rs.atekom.prati.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class OpstiServer implements Runnable {

	// ДОДАЈТЕ LOGGER
	private static final Logger logger = LoggerFactory.getLogger(OpstiServer.class);

	private final int listeningPort;
	private ServerSocket serverSocket;
	private final ExecutorService pool;
	private final ConcurrentHashMap<String, Socket> clientSockets;
	private final AtomicInteger connectionCounter;
	private boolean isStopped = false;
	private int poolSize;
	
	public SistemAlarmi prekoracenjeBrzine, stajanje, istakanje, izlazak, ulazak, redovno;
	public NeonProtokol nProtokol;
	public RuptelaProtokol rProtokol;
	public GenekoProtokol gProtokol;
	public Izvrsavanje izvrsavanje;
	private String server;
	
	public OpstiServer(int port, int poolSizeS) {
		clientSockets = new ConcurrentHashMap<>();
		connectionCounter = new AtomicInteger(0);
		
		listeningPort = port;
		poolSize = poolSizeS;
		pool = Executors.newFixedThreadPool(poolSize);
		
		switch (listeningPort) {
		case 9000: 
			nProtokol = new NeonProtokol(this);
			server = "NEON";
			break;
		case 9030: 
			gProtokol = new GenekoProtokol(this);
			server = "GENEKO";
			break;
		case 9040: 
			rProtokol = new RuptelaProtokol();
			server = "RUPTELA";
			break;
		default:
			server = "TEST";
			break;
		}
		
		// Иницијализација аларма са null провером
		try {
			if (Servis.sistemAlarmServis != null) {
				prekoracenjeBrzine = Servis.sistemAlarmServis.nadjiAlarmPoSifri("6013");
				stajanje = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1095");
				istakanje = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1111");
				izlazak = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1100");
				ulazak = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1101");
				redovno = Servis.sistemAlarmServis.nadjiAlarmPoSifri("0");
			} else {
				logger.warn("Alarmi nisu dostupni (test okruženje)");
			}
		} catch (Exception e) {
			logger.error("Greška pri inicijalizaciji alarma", e);
		}
		
		izvrsavanje = new Izvrsavanje();
		
		logger.info("OpstiServer [{}] inicijalizovan na portu {}", server, port);
	}
	
	@Override
	public void run() {
		LinkedBlockingQueue<Socket> queue = new LinkedBlockingQueue<>();
		logger.info(" Pokretanje {} TCP servera...", server);
		
		try {
			serverSocket = new ServerSocket(listeningPort);
			// SO_REUSEADDR - омогућава брже поновно покретање сервера
			serverSocket.setReuseAddress(true);
			logger.info(" {} server pokrenut i sluša na portu {} (SO_REUSEADDR=true)", server, listeningPort);
			
			while(!isStopped()) {
				Socket soket = null;
				try {
					soket = serverSocket.accept();
					
					// Генеришемо јединствени ID за конекцију
					int connectionId = connectionCounter.incrementAndGet();
					String clientId = server + "-" + connectionId;
					
					// Thread-safe додавање у мапу
					clientSockets.put(clientId, soket);
					
					switch (listeningPort) {
					case 9000: 
						pool.submit(new NeonOpstiThread(queue, this));
						break;
					case 9030: 
						pool.submit(new GenekoOpstiThread(queue, this));
						break;
					case 9040: 
						pool.submit(new RuptelaOpstiThread(queue, this));
						break;
					default:
						break;
					}
					
					queue.put(soket);
					
					// Логовање на сваку 1000-ту конекцију
					if(connectionId == 1 || connectionId % 1000 == 0) {
						logger.info("═══════════════════════════════════════════════════");
						logger.info("{} | Konekcija #{} | Aktivnih thread-ova: {} | Ukupno klijenata: {} | {}", 
						            server, connectionId, 
						            ((ThreadPoolExecutor) pool).getActiveCount(),
						            clientSockets.size(),
						            getVreme());
						logger.info("═══════════════════════════════════════════════════");
					}
					
					// Debug logging za svaku konakciju (može se isključiti u production)
					if (connectionId <= 10 || connectionId % 100 == 0) {
						logger.debug("Novi {} klijent prihvaćen: {} | Total: {}", 
						             server, clientId, clientSockets.size());
					}
					
				} catch (Throwable e) {
					if (isStopped()) {
						logger.info("Server {} zaustavljan - prekid accept petlje", server);
					} else {
						logger.error("Greška prihvatanja {} klijentske konekcije", server, e);
					}
					break;
				}
			}
		} catch (IOException ex) {
			logger.error("Greška otvaranja {} server socket-a na portu {}", 
			             server, listeningPort, ex);
		} finally {
			logger.info("Server {} napušta run petlju", server);
		}
	}

	
	private synchronized boolean isStopped() {
		return this.isStopped;
	}
	
	public synchronized void stop() {
		logger.info("═══════════════════════════════════════════════════");
		logger.info("  Zaustavljanje {} servera...", server);
		logger.info("═══════════════════════════════════════════════════");
		
		isStopped = true;
		
		// Затварамо све client socket-е
		int clientCount = clientSockets.size();
		if (clientCount > 0) {
			logger.info("Zatvaranje {} klijentskih konekcija...", clientCount);
			
			int closed = 0;
			for (Socket socket : clientSockets.values()) {
				try {
					if (socket != null && !socket.isClosed()) {
						socket.close();
						closed++;
					}
				} catch (IOException e) {
					logger.warn("Greška zatvaranja client socket-a: {}", e.getMessage());
				}
			}
			
			clientSockets.clear();
			logger.info("Zatvoreno {}/{} klijentskih socket-a", closed, clientCount);
		}
		
		// Затварамо server socket
		try {
			if (serverSocket != null && !serverSocket.isClosed()) {
				serverSocket.close();
				logger.info("✓ Server socket zatvoren");
			}
		} catch (IOException e) {
			logger.error("Greška zatvaranja server socket-a", e);
		}
		
		// Shutdown thread pool
		pool.shutdown();
		try {
			if (!pool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
				pool.shutdownNow();
				logger.warn("Thread pool forcefully zaustavljen nakon timeout-a");
			} else {
				logger.info("✓ Thread pool čisto zaustavljen");
			}
		} catch (InterruptedException e) {
			pool.shutdownNow();
			Thread.currentThread().interrupt();
			logger.warn("Thread pool shutdown prekinut");
		}
		
		logger.info("═══════════════════════════════════════════════════");
		logger.info("    Server {} uspešno zaustavljen", server);
		logger.info("═══════════════════════════════════════════════════");
	}
	
	/**
	 * Уклања client socket по ID-у
	 * проблем - clientId из самог наследника није исти 
	 * као онај који се креира за унос у листу коју 
	 * користи сервер па се гашење никада не деси 
	 * 
	 */
	public void removeClientSocket(String clientId) {
		Socket socket = clientSockets.remove(clientId);
		if (socket != null) {
			try {
				if (!socket.isClosed()) {
					socket.close();
				}
				logger.debug("Klijent {} otkačen i socket zatvoren", clientId);
			} catch (IOException e) {
				logger.warn("Greška zatvaranja socket-a {}: {}", clientId, e.getMessage());
			}
		}
	}
	
	/**
	 * Уклања све уносе који референцирају дати сокет и затвара сокет.
	 * Више није застарело: корисно када ID није доследан.
	 */
	public void removeClientSocket(Socket clientSocket) {
	    if (clientSocket == null) {
	        logger.debug("removeClientSocket: prosleđen null");
	        return;
	    }

	    // 1) Прво затвори сокет да одмах разблокираш I/O
	    try {
	        if (!clientSocket.isClosed()) {
	            try {
	                clientSocket.shutdownInput();
	            } catch (IOException ignore) {}
	            try {
	                clientSocket.shutdownOutput();
	            } catch (IOException ignore) {}
	            clientSocket.close();
	        }
	    } catch (IOException e) {
	        logger.warn("Грешка при затварању сокета: {}", e.getMessage());
	    }

	    // 2) Онда уклони све уносе из мапе који показују на исти сокет
	    int[] removed = {0};
	    clientSockets.entrySet().removeIf(entry -> {
	        boolean match = entry.getValue() == clientSocket; // референтна једнакост
	        if (match) removed[0]++;
	        return match;
	    });

	    logger.debug("Client socket уклоњен из мапе ({} унос/а)", removed[0]);
	}
	
	/**
	 * Враћа број активних клијената
	 */
	public int getActiveClientCount() {
		return clientSockets.size();
	}
	
	/**
	 * Проверава да ли клијент постоји
	 */
	public boolean hasClient(String clientId) {
		return clientSockets.containsKey(clientId);
	}
    
    public String getVreme() {
    	return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    }
    
    public void postaviAlarmStajanje(Javljanja javljanje) {
    	if(stajanje != null && stajanje.isAktivan()) {
        	javljanje.setSistemAlarmi(stajanje);
    	} else {
    		if(redovno != null) {
    			javljanje.setSistemAlarmi(redovno);
    		}
    	}
    }
    
    public void postaviAlarmPrekoracenjeBrzine(Javljanja javljanje) {
    	if(prekoracenjeBrzine != null && prekoracenjeBrzine.isAktivan()) {
        	javljanje.setSistemAlarmi(prekoracenjeBrzine);
    	} else {
    		if(redovno != null) {
    			javljanje.setSistemAlarmi(redovno);
    		}
    	}
    }
    
    public void postaviAlarmIstakanje(Javljanja javljanje) {
    	if(istakanje != null && istakanje.isAktivan()) {
        	javljanje.setSistemAlarmi(istakanje);
    	} else {
    		if(redovno != null) {
    			javljanje.setSistemAlarmi(redovno);
    		}
    	}
    }
    
    public void postaviAlarmIzlazakIzZone(Javljanja javljanje) {
    	if(izlazak != null && izlazak.isAktivan()) {
        	javljanje.setSistemAlarmi(izlazak);
    	} else {
    		if(redovno != null) {
    			javljanje.setSistemAlarmi(redovno);
    		}
    	}
    }
    
    public void postaviAlarmUlazakUZonu(Javljanja javljanje) {
    	if(ulazak != null && ulazak.isAktivan()) {
        	javljanje.setSistemAlarmi(ulazak);
    	} else {
    		if(redovno != null) {
    			javljanje.setSistemAlarmi(redovno);
    		}
    	}
    }
}