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
    private static final int MAX_INVALID_FRAME_ATTEMPTS = 3;

    private String[] frames;
    private String[] tokens;
    private int invalidFrameAttempts;

    public GenekoOpstiThread(LinkedBlockingQueue<Socket> queue, OpstiServer srv) {
        super(queue, srv);
        invalidFrameAttempts = 0;
    }

    @Override
    public void run() {
        Socket localSocket = null;
        String clientId = null;

        try {
            localSocket = socketQueue.take();
            clientId = "GENEKO-" + localSocket.getRemoteSocketAddress();

            setupSocket(localSocket, clientId);

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
        
        logger.warn("GENEKO [{}]: Примљен пакет ({} бајтова), садржај (првих 200 карактера): {}", 
                    clientId, bytesRead, 
                    ulaz.length() > 200 ? ulaz.substring(0, 200) + "..." : ulaz);
        
        frames = ulaz.split(FOX_FRAME_END);
        
        logger.warn("GENEKO [{}]: Парсирано {} FOX frame-ова", clientId, frames.length);

        // Провера да ли је последњи frame комплетан (завршава се са '</fox>')
        // Ако не завршава са '</fox>', то значи да је сечен на граници буфера
        boolean poslednjiKompletan = ulaz.endsWith(FOX_FRAME_END);
        
        // Обрађујемо све frame-ове осим последњег ако није комплетан
        int krajIndeksa = poslednjiKompletan ? frames.length : frames.length - 1;
        
        if (!poslednjiKompletan && frames.length > 0 && frames[frames.length - 1].length() > 0) {
            logger.warn("GENEKO [{}]: Детектован некомплетан FOX frame на крају пакета ({} карактера), чека се следећи пакет", 
                        clientId, frames[frames.length - 1].length());
        }

        for (int i = 0; i < krajIndeksa; i++) {
            String frame = frames[i];
            
            logger.warn("GENEKO [{}]: Обрада frame #{}: '{}'", clientId, i + 1, frame);
            
            if (!frame.startsWith(FOX_FRAME_START)) {
                String preview = frame.length() > 20 ? frame.substring(0, 20) : frame;
                logger.warn("GENEKO [{}]: Неисправан FOX frame (не почиње са '<fox>'), почетак: '{}'", 
                            clientId, preview);
                invalidFrameAttempts++;
                if (invalidFrameAttempts >= MAX_INVALID_FRAME_ATTEMPTS) {
                    logger.error("GENEKO [{}]: Превише неважећих frame-ова ({}), прекидам везу", 
                                clientId, invalidFrameAttempts);
                    stop();
                    return;
                }
                continue;
            }

            tokens = frame.split("\"");

            logger.warn("GENEKO [{}]: FOX frame парсиран: tokens.length={}", clientId, tokens.length);

            if (tokens.length < 4) {
                logger.warn("GENEKO [{}]: Недовољно поља у FOX frame-у (потребно најмање 4, пронађено {}): '{}'", 
                            clientId, tokens.length, frame);
                continue;
            }

            if (uredjaj == null) {
                kodUredjaja = tokens[1];
                logger.warn("GENEKO [{}]: Покушај проналажења уређаја '{}'", clientId, kodUredjaja);
                pronadjiPostavi(kodUredjaja);
                logger.warn("GENEKO [{}]: Уређај пронађен: uredjaj={}, objekat={}", 
                            clientId, uredjaj != null ? uredjaj.getKod() : "null", 
                            objekat != null ? objekat.getOznaka() : "null");
            }

            if (objekat == null) {
                logger.warn("GENEKO [{}]: Objekat је null, не могу обрадити. uredjaj={}, kodUredjaja={}, frame: '{}'", 
                            clientId, uredjaj != null ? uredjaj.getKod() : "null", 
                            kodUredjaja, frame);
                continue;
            }

            logger.warn("GENEKO [{}]: Позивање genekoObrada() са podaci='{}'", clientId, tokens[3]);
            Javljanja trenutno = server.gProtokol.genekoObrada(tokens[3], objekat);
            logger.warn("GENEKO [{}]: genekoObrada() завршена: javljanje={}", 
                        clientId, trenutno != null ? "OK" : "NULL");
            
            obradaJavljanja(trenutno, null);
            logger.warn("GENEKO [{}]: obradaJavljanja() завршена успешно", clientId);
        }
    }
}
