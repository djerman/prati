package rs.atekom.prati.server.updated;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * NeonOpstiThreadUpdate - minimal adaptation for OpstiServerUpdate worker model.
 *
 * - package: rs.atekom.prati.server.update
 * - constructor accepts rs.atekom.prati.server.update.OpstiServerUpdate (FQCN as requested)
 * - run() repeatedly takes sockets from the shared queue and processes each connection
 *   in an inner read-loop, then closes only that connection and continues.
 *
 * Note: keep parsing/processing logic identical to original NeonOpstiThread.
 */
public class NeonOpstiThreadUpdate extends OpstiThreadUpdate {

    private String[] niz, da;
    private int brojPromasaja;

    public NeonOpstiThreadUpdate(LinkedBlockingQueue<Socket> queue, OpstiServerUpdate srv) {
        // call super with same arguments - ensure OpstiThread has matching constructor signature.
        super(queue, srv);
        brojPromasaja = 0;
    }

    @Override
    public void run() {
        // Worker loop: keep taking connections until worker is stopped.
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
                // cannot obtain streams -> close socket and continue to next
                closeConnectionQuietly();
                continue;
            }

            int br = 0;
            brojPromasaja = 0;

            try {
                while (!isStopped() && socket != null && !socket.isClosed()) {
                    try {
                        // set socket read timeout to allow graceful shutdown checks
                        try {
                            socket.setSoTimeout(vreme);
                        } catch (Throwable ignore) {}

                        br = input.read(data, 0, data.length);
                        if (br <= 0) {
                            break; // remote closed or no data
                        }

                        ulaz = new String(data, 0, br);

                        // message processing - preserved from original
                        niz = ulaz.split(">");
                        for (int i = 0; i < niz.length; i++) {
                            if (niz[i].startsWith("<oris") || niz[i].startsWith("#<oris")) {
                                da = niz[i].split(",");

                                if (uredjaj == null) {
                                    kodUredjaja = da[2];
                                    pronadjiPostavi(kodUredjaja);
                                }

                                if (objekat != null) {
                                    javljanjeTrenutno = server.nProtokol.neonObrada(da, ulaz, objekat);
                                    obradaJavljanja(javljanjeTrenutno, null);
                                } else {
                                    System.out.println("neon objekat null... " + ulaz);
                                }
                            } else {
                                if (!niz[i].equals("#")) {
                                    System.out.println("nije oris... " + niz[i]);
                                    if (brojPromasaja > 3) {
                                        // too many misses on this connection -> break inner loop and close conn
                                        break;
                                    } else {
                                        brojPromasaja++;
                                    }
                                }
                            }
                        }

                        if (Thread.currentThread().isInterrupted()) {
                            System.out.println("thread neon interrupted exiting... ");
                            break;
                        }

                    } catch (SocketTimeoutException ste) {
                        // read timed out for this connection: exit inner loop and close connection (worker stays alive)
                        break;
                    }
                } // end inner read-loop
            } catch (SocketException se) {
                // socket error on this connection - close and continue
            } catch (Throwable e) {
                String por = " neon: ";
                if (objekat != null) {
                    por += objekat.getOznaka() + " " + test;
                }
                System.out.println("neon thread throwable greška " + e.getMessage() + por);
                // do not call stop() here; we want worker to continue processing other sockets
            } finally {
                // close only this connection and cleanup local state
                closeConnectionQuietly();
            }
            // continue to next socket from queue
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
