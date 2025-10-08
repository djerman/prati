package rs.atekom.prati.server.updated;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * GenekoOpstiThreadUpdate - минимална адаптација оригиналне GenekoOpstiThread
 * за модел у коме радник узима везе из заједничке queue и обрађује их по вези.
 *
 * - пакет: rs.atekom.prati.server.updated
 * - класа: GenekoOpstiThreadUpdate (наслеђује OpstiThreadUpdate)
 * - конструктор прима OpstiServerUpdate
 * - run() ради у спољној петљи: take() везу, унутрашњи read-loop, cleanup по вези;
 *   не позива stop() да не би угасио самог радника.
 *
 * Остала парсер/обрада логика није промењена у односу на оригинал.
 */
public class GenekoOpstiThreadUpdate extends OpstiThreadUpdate {

    private String[] niz, da;

    public GenekoOpstiThreadUpdate(LinkedBlockingQueue<Socket> queue, OpstiServerUpdate srv) {
        super(queue, srv);
    }

    @Override
    public void run() {
        // Worker loop: узимамо сокете док радник није заустављен
        while (!isStopped()) {
            try {
                socket = socketQueue.take();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }

            if (socket == null) continue;

            try {
                input = socket.getInputStream();
            } catch (IOException e) {
                // Не могу добити stream за ову везу - затвори и настави
                closeConnectionQuietly();
                continue;
            }

            int br = 0;

            try {
                while (!isStopped() && socket != null && !socket.isClosed()) {
                    try {
                        // постави timeout како би могли graceful shutdown / преглед
                        try {
                            socket.setSoTimeout(vreme);
                        } catch (Throwable ignore) {}

                        br = input.read(data, 0, data.length);
                        if (br <= 0) {
                            break;
                        }

                        ulaz = new String(data, 0, br);

                        niz = ulaz.split("</fox>");
                        for (int i = 0; i < niz.length; i++) {
                            if (niz[i].startsWith("<fox>")) {
                                da = niz[i].split("\"");

                                if (uredjaj == null) {
                                    kodUredjaja = da[1];
                                    pronadjiPostavi(kodUredjaja);
                                }

                                if (objekat != null) {
                                    javljanjeTrenutno = server.gProtokol.genekoObrada(da[3], objekat);
                                    obradaJavljanja(javljanjeTrenutno, null);
                                } else {
                                    System.out.println("geneko objekat null... " + niz[i]);
                                }
                            } else {
                                System.out.println("nije ispravan fox.... " + niz[i]);
                            }
                        }

                        if (Thread.currentThread().isInterrupted()) {
                            System.out.println("thread geneko interrupted exiting... ");
                            break;
                        }

                    } catch (SocketTimeoutException ste) {
                        // read timeout за ову везу - изађи из унутрашње петље и cleanup везе
                        break;
                    }
                } // end inner read-loop
            } catch (SocketException se) {
                // socket greška за ову везу - затвори и настави на следећу
            } catch (Throwable e) {
                String por = " geneko: ";
                if (objekat != null) {
                    por += objekat.getOznaka();
                }
                System.out.println("geneko thread throwable greška " + e.getMessage() + por);
                // не позивамо stop() - желимо да радник настави са другим везама
            } finally {
                // cleanup само ове везе
                closeConnectionQuietly();
            }
            // настави са следећим socket-ом
        } // end while !isStopped
    }

    /**
     * Close streams and socket for the current connection, without stopping the worker.
     * Also attempt to remove socket from server tracking list (safe-guarded).
     */
    private void closeConnectionQuietly() {
        try {
            if (input != null) {
                try { input.close(); } catch (Throwable ignored) {}
                input = null;
            }
            if (out != null) {
                try { out.flush(); } catch (Throwable ignored) {}
                try { out.close(); } catch (Throwable ignored) {}
                out = null;
            }
            if (socket != null && !socket.isClosed()) {
                try { socket.close(); } catch (Throwable ignored) {}
            }
            try {
                if (server != null) {
                    server.removeClientSocket(socket);
                }
            } catch (Throwable ignored) {}
        } finally {
            socket = null;
        }
    }
}
