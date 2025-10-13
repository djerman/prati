package rs.atekom.prati.server.geneko;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pratiBaza.tabele.Javljanja;
import rs.atekom.prati.server.OpstiServer;
import rs.atekom.prati.server.OpstiThread;

/**
 * Handler thread for Geneko TCP devices.
 *
 * <p>The implementation mirrors the legacy behaviour while improving
 * readability and observability. All business logic and protocol parsing
 * remain unchanged.</p>
 */
public class GenekoOpstiThread extends OpstiThread {

    private static final Logger logger = LoggerFactory.getLogger(GenekoOpstiThread.class);

    private static final String FOX_FRAME_START = "<fox>";
    private static final String FOX_FRAME_END = "</fox>";

    private String[] frames;
    private String[] tokens;

    public GenekoOpstiThread(LinkedBlockingQueue<Socket> queue, OpstiServer srv) {
        super(queue, srv);
    }

    @Override
    public void run() {
        Socket localSocket = null;
        String clientId = null;

        try {
            localSocket = socketQueue.take();
            clientId = "GENEKO-" + localSocket.getRemoteSocketAddress();

            setupSocket(localSocket, clientId);
            localSocket.setSoTimeout(connectionTimeoutMs);

            input = localSocket.getInputStream();
            out = localSocket.getOutputStream();

            logger.info("GENEKO [{}]: obrada započeta", clientId);

            int bytesRead;

            while (!isStopped() && !localSocket.isClosed()) {
                bytesRead = readFromSocket(clientId);

                if (bytesRead <= 0) {
                    logger.debug("GENEKO [{}]: kraj stream-a ({} bajtova)", clientId, bytesRead);
                    break;
                }

                processPayload(clientId, bytesRead);

                if (Thread.currentThread().isInterrupted()) {
                    logger.info("GENEKO [{}]: thread prekinut", clientId);
                    break;
                }
            }

            logger.info("GENEKO [{}]: obrada završena", clientId);

        } catch (SocketTimeoutException e) {
            logger.info("GENEKO [{}]: socket timeout после {}ms", clientId, connectionTimeoutMs);
        } catch (SocketException e) {
            if (isStopped()) {
                logger.debug("GENEKO [{}]: socket затворен (graceful)", clientId);
            } else {
                logger.warn("GENEKO [{}]: socket greška: {}", clientId, e.getMessage());
            }
        } catch (InterruptedException e) {
            logger.info("GENEKO [{}]: thread interrupted при чекању socket-а", clientId);
            Thread.currentThread().interrupt();
        } catch (Throwable e) {
            String poruka = " neon: ";
            if (objekat != null) {
                poruka += objekat.getOznaka();
            }
            logger.error("GENEKO [{}]: неочекивана грешка{}", clientId, poruka, e);
        } finally {
            stop();
            if (clientId != null) {
                logger.debug("GENEKO [{}]: thread завршио", clientId);
            }
        }
    }

    private int readFromSocket(String clientId) throws IOException {
        int bytesRead;
        try {
            bytesRead = input.read(data, 0, data.length);
        } catch (SocketTimeoutException e) {
            throw e;
        } catch (IOException e) {
            logger.error("GENEKO [{}]: грешка читања", clientId, e);
            throw e;
        }
        return bytesRead;
    }

    private void processPayload(String clientId, int bytesRead) {
        ulaz = new String(data, 0, bytesRead, StandardCharsets.UTF_8);
        frames = ulaz.split(FOX_FRAME_END);

        for (String frame : frames) {
            if (!frame.startsWith(FOX_FRAME_START)) {
                logger.warn("GENEKO [{}]: неисправан FOX frame: {}", clientId, frame);
                continue;
            }

            tokens = frame.split("\"");

            if (tokens.length < 4) {
                logger.warn("GENEKO [{}]: недовољно поља у FOX frame-у: {}", clientId, frame);
                continue;
            }

            if (uredjaj == null) {
                kodUredjaja = tokens[1];
                pronadjiPostavi(kodUredjaja);
            }

            if (objekat == null) {
                logger.warn("GENEKO [{}]: objekat null, frame прескочен: {}", clientId, frame);
                continue;
            }

            Javljanja trenutno = server.gProtokol.genekoObrada(tokens[3], objekat);
            obradaJavljanja(trenutno, null);
        }
    }
}
