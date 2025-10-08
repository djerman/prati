package rs.atekom.prati.server.updated;

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
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import pratiBaza.tabele.Javljanja;
import pratiBaza.tabele.SistemAlarmi;
import rs.atekom.prati.server.Servis;
import rs.atekom.prati.view.komponente.Izvrsavanje;

/**
 * OpstiServerUpdate - исправљена, минимално-инвазивна верзија OpstiServer.
 *
 * Кључна промена у односу на оригинал:
 *  - Не submit-ујемо нови worker (NeonOpstiThread / GenekoOpstiThread / RuptelaOpstiThread)
 *    на сваки accept(). Уместо тога креирамо фиксни број worker-a (poolSize) једном при
 *    старту и они читају socket-ове из заједничке queue.
 *
 * Ово решава проблем "једна нит по конекцији" који је изазивао исцрпљивање ресурса.
 *
 * Да би ово радило без даљих промена, NeonOpstiThread/GenekoOpstiThread/RuptelaOpstiThread
 * морају прихватати LinkedBlockingQueue<Socket> и унутра радити blocking take() и обраду.
 */
@Service
public class OpstiServerUpdate implements Runnable {

    private final int listeningPort;
    private ServerSocket serverSocket;
    private final ExecutorService workerPool; // пул радника (за обраду веза)
    private final LinkedBlockingQueue<Socket> queue;
    private ArrayList<Socket> clientSockets;
    private volatile boolean isStopped = false;
    private final int poolSize;
    private int rb = 1;

    public SistemAlarmi prekoracenjeBrzine, stajanje, istakanje, izlazak, ulazak, redovno;
    public NeonProtokolUpdate nProtokol;
    public RuptelaProtokolUpdate rProtokol;
    public GenekoProtokolUpdate gProtokol;
    public Izvrsavanje izvrsavanje;
    private String server;

    /**
     * Конструктор: port и poolSize (колико worker-а ће бити покренуто једном).
     */
    public OpstiServerUpdate(int port, int poolSizeS) {
        this.clientSockets = new ArrayList<>();
        this.listeningPort = port;
        this.poolSize = Math.max(1, poolSizeS);
        this.queue = new LinkedBlockingQueue<>();
        // workerPool size = poolSize (fixed); користимо cached именијама да лако shutdown-ујемо
        this.workerPool = Executors.newFixedThreadPool(this.poolSize);

        // иницијализација протокола као и у оригиналу
        switch (listeningPort) {
            case 9000:
                nProtokol = new NeonProtokolUpdate(this);
                server = " NEON ";
                break;
            case 9030:
                gProtokol = new GenekoProtokolUpdate(this);
                server = " GENEKO ";
                break;
            case 9040:
                rProtokol = new RuptelaProtokolUpdate();
                server = " RUPTELA ";
                break;
            default:
                server = " UNKNOWN ";
                break;
        }

        prekoracenjeBrzine = Servis.sistemAlarmServis.nadjiAlarmPoSifri("6013");
        stajanje = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1095");
        istakanje = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1111");
        izlazak = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1100");
        ulazak = Servis.sistemAlarmServis.nadjiAlarmPoSifri("1101");
        redovno = Servis.sistemAlarmServis.nadjiAlarmPoSifri("0");
        izvrsavanje = new Izvrsavanje();

        // Start worker-a **једном** при конструкцији: сваки worker ће чекати на queue.take()
        startWorkers();
    }

    private void startWorkers() {
        for (int i = 0; i < poolSize; i++) {
            switch (listeningPort) {
                case 9000:
                    workerPool.submit(new NeonOpstiThreadUpdate(queue, this));
                    break;
                case 9030:
                    workerPool.submit(new GenekoOpstiThreadUpdate(queue, this));
                    break;
                case 9040:
                    workerPool.submit(new RuptelaOpstiThreadUpdate(queue, this));
                    break;
                default:
                    // опрез: ако је неизвестан порт, покрећемо general worker (можеш додати друге)
                    workerPool.submit(() -> {
                        while (!isStopped) {
                            try {
                                Socket s = queue.take();
                                try { s.close(); } catch (Throwable ignored) {}
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                    });
                    break;
            }
        }
    }

    @Override
    public void run() {
        System.out.println(server);
        try {
            serverSocket = new ServerSocket(listeningPort);

            while (!isStopped()) {
                Socket soket = null;
                try {
                    soket = serverSocket.accept();
                    // optional: set socket SO_TIMEOUT to allow faster detection on shutdown
                    try {
                        soket.setSoTimeout(60_000);
                    } catch (Throwable ignored) {}

                    // track socket for cleanup
                    clientSockets.add(soket);

                    // enqueue socket for existing (already started) workers
                    queue.put(soket);

                    if (rb == 1 || rb % 1000 == 0) {
                        System.out.println();
                        System.out.println("************************************************************");
                        System.out.println(server + rb + " STARTOVAN" + " od " + ((ThreadPoolExecutor) workerPool).getActiveCount() + " " + getVreme() + " *****");
                        System.out.println("************************************************************");
                        System.out.println();
                    }
                    rb++;
                } catch (Throwable e) {
                    if (isStopped()) {
                        System.out.println("server " + server + " is stopped");
                        // don't print stacktrace in normal shutdown
                    } else {
                        System.out.println("error accepting " + server + " client connection: " + e.getMessage());
                        e.printStackTrace();
                    }
                    // break only on fatal accept error
                    break;
                }
            }

        } catch (IOException ex) {
            System.out.println("greška otvaranja " + server + " soketa: " + ex.getMessage());
        } finally {
            // ensure cleanup if run() exits unexpectedly
            stopAndCleanup();
        }
    }

    private synchronized boolean isStopped() {
        return this.isStopped;
    }

    /**
     * Stop server: mark stopped, close server socket and close client sockets; shutdown worker pool.
     */
    public synchronized void stop() {
        isStopped = true;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (Throwable e) {
            System.out.println("error stopping server " + server + e.getMessage());
        }
        // signal workers by interrupting pool (workers taking from queue will unblock when queue is empty
        // or when interrupted). Also clear queue.
        stopAndCleanup();
    }

    private void stopAndCleanup() {
        // close all client sockets
        try {
            for (Socket s : clientSockets) {
                try {
                    if (s != null && !s.isClosed()) s.close();
                } catch (Throwable ignore) {
                }
            }
            clientSockets.clear();
        } catch (Throwable e) {
            System.out.println("error during closing client sockets: " + e.getMessage());
        }

        // shutdown worker pool
        try {
            workerPool.shutdownNow();
            if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                workerPool.shutdownNow();
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            System.out.println("error shutting down worker pool: " + t.getMessage());
        }
    }

    public synchronized void removeClientSocket(Socket clientSocket) {
        try {
            clientSockets.remove(clientSocket);
        } catch (Throwable e) {
            System.out.println("error removing" + server + " socket" + e.getMessage());
        }
    }

    public String getVreme() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
    }

    public void postaviAlarmStajanje(Javljanja javljanje) {
        if (stajanje != null && stajanje.isAktivan()) {
            javljanje.setSistemAlarmi(stajanje);
        } else {
            if (redovno != null) {
                javljanje.setSistemAlarmi(redovno);
            }
        }
    }

    public void postaviAlarmPrekoracenjeBrzine(Javljanja javljanje) {
        if (prekoracenjeBrzine != null && prekoracenjeBrzine.isAktivan()) {
            javljanje.setSistemAlarmi(prekoracenjeBrzine);
        } else {
            if (redovno != null) {
                javljanje.setSistemAlarmi(redovno);
            }
        }
    }

    public void postaviAlarmIstakanje(Javljanja javljanje) {
        if (istakanje != null && istakanje.isAktivan()) {
            javljanje.setSistemAlarmi(istakanje);
        } else {
            if (redovno != null) {
                javljanje.setSistemAlarmi(redovno);
            }
        }
    }

    public void postaviAlarmIzlazakIzZone(Javljanja javljanje) {
        if (izlazak != null && izlazak.isAktivan()) {
            javljanje.setSistemAlarmi(izlazak);
        } else {
            if (redovno != null) {
                javljanje.setSistemAlarmi(redovno);
            }
        }
    }

    public void postaviAlarmUlazakUZonu(Javljanja javljanje) {
        if (ulazak != null && ulazak.isAktivan()) {
            javljanje.setSistemAlarmi(ulazak);
        } else {
            if (redovno != null) {
                javljanje.setSistemAlarmi(redovno);
            }
        }
    }
}
