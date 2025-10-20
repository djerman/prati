package rs.atekom.prati.server.lifecycle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Централизовани менаџер за управљање животним циклусом свих сервера.
 * 
 * <p>Одговорности:</p>
 * <ul>
 *   <li>Покретање и заустављање свих серверских инстанци</li>
 *   <li>Graceful shutdown са timeout механизмом</li>
 *   <li>Праћење статуса сервера</li>
 *   <li>Thread pool менаџмент</li>
 * </ul>
 * 
 * <p>Thread-safe имплементација за коришћење у ServletContext lifecycle.</p>
 * 
 * <p><b>Употреба:</b></p>
 * <pre>
 * ServerManager manager = new ServerManager();
 * manager.registerServer("NEON", neonServer, 9000);
 * manager.registerServer("RUPTELA", ruptelaServer, 9040);
 * manager.startAll();
 * // ... апликација ради ...
 * manager.stopAll(); // graceful shutdown
 * </pre>
 * 
 * @author Atekom
 * @version 1.0
 * @since 2025-01-09
 */
public class ServerManager {
    
    private static final Logger logger = LoggerFactory.getLogger(ServerManager.class);
    
    /**
     * Максимално време чекања (у секундама) за graceful shutdown.
     * Ако се сервери не заустави у овом року, биће форсирано заустављени.
     */
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 30;
    
    /**
     * Листа свих регистрованих серверских инстанци.
     * Thread-safe јер се модификује само пре покретања.
     */
    private final List<ServerInstance> serverInstances;
    
    /**
     * Executor који управља главним серверским нитима.
     * По једна нит за сваки сервер.
     */
    private ExecutorService serverExecutor;
    
    /**
     * Статус менаџера - да ли је тренутно активан.
     * AtomicBoolean обезбеђује thread-safety.
     */
    private final AtomicBoolean running;
    
    /**
     * Креира нови ServerManager у неактивном стању.
     */
    public ServerManager() {
        this.serverInstances = new ArrayList<>();
        this.running = new AtomicBoolean(false);
        logger.info("ServerManager иницијализован");
    }
    
    /**
     * Региструје нову серверску инстанцу која ће бити менаџована.
     * 
     * <p>Сервер мора имплементирати Runnable и имати методу stop() за заустављање.</p>
     * 
     * @param name Име сервера (за логовање и праћење), нпр. "NEON", "RUPTELA"
     * @param server Инстанца сервера који имплементира Runnable
     * @param port Порт на коме сервер слуша TCP конекције
     * @throws IllegalStateException ако је менаџер већ покренут
     * @throws NullPointerException ако су параметри null
     */
    public void registerServer(String name, Runnable server, int port) {
        if (running.get()) {
            throw new IllegalStateException(
                "Не може се регистровати сервер док је менаџер покренут");
        }
        
        if (name == null || server == null) {
            throw new NullPointerException("Име и сервер не могу бити null");
        }
        
        ServerInstance instance = new ServerInstance(name, server, port);
        serverInstances.add(instance);
        logger.info("Регистрован сервер: {} на порту {}", name, port);
    }
    
    /**
     * Покреће све регистроване серверe истовремено.
     * 
     * <p>Процес:</p>
     * <ol>
     *   <li>Креира thread pool величине једнаке броју сервера</li>
     *   <li>Сваки сервер добија посебну нит</li>
     *   <li>Ажурира статусе сервера</li>
     * </ol>
     * 
     * @throws IllegalStateException ако је већ покренут или нема регистрованих сервера
     */
    public void startAll() {
        if (running.get()) {
            throw new IllegalStateException("ServerManager је већ покренут");
        }
        
        if (serverInstances.isEmpty()) {
            throw new IllegalStateException("Нема регистрованих сервера за покретање");
        }
        
        // Креирамо fixed thread pool - по један thread за сваки сервер
        serverExecutor = Executors.newFixedThreadPool(
            serverInstances.size(),
            new ServerThreadFactory()
        );
        
        logger.info("Покретање {} сервера...", serverInstances.size());
        
        // Покрећемо сваки сервер у посебној нити
        for (ServerInstance instance : serverInstances) {
            try {
                Future<?> future = serverExecutor.submit(instance.server);
                instance.setFuture(future);
                instance.setStatus(ServerStatus.RUNNING);
                logger.info("Сервер {} покренут на порту {}", 
                    instance.name, instance.port);
            } catch (Exception e) {
                instance.setStatus(ServerStatus.FAILED);
                logger.error("Грешка при покретању сервера {}: {}", 
                    instance.name, e.getMessage(), e);
            }
        }
        
        running.set(true);
        logger.info("═══════════════════════════════════════════════════");
        logger.info("  Сви сервери успешно покренути");
        logger.info("═══════════════════════════════════════════════════");
    }
    
    /**
     * Зауставља све серверe са graceful shutdown механизмом.
     * 
     * <p>Фазе заустављања:</p>
     * <ol>
     *   <li><b>Сигнализација:</b> Шаље stop() сигнал свим серверима</li>
     *   <li><b>Graceful чекање:</b> Чека до 30 секунди да се заврше</li>
     *   <li><b>Форсирано гашење:</b> АкоTimeout истекне, форсира shutdownNow()</li>
     * </ol>
     * 
     * <p>Thread-safe и може се позвати више пута без проблема.</p>
     */
    public void stopAll() {
        if (!running.get()) {
            logger.warn("ServerManager није покренут, заустављање прескочено");
            return;
        }
        
        logger.info("═══════════════════════════════════════════════════");
        logger.info("  Заустављање сервера...");
        logger.info("═══════════════════════════════════════════════════");
        
        // ФАЗА 1: Сигнализирамо свим серверима да престану са радом
        for (ServerInstance instance : serverInstances) {
            try {
                // Користимо рефлексију да позовемо stop() јер немамо заједнички интерфејс
                instance.server.getClass().getMethod("stop").invoke(instance.server);
                instance.setStatus(ServerStatus.STOPPING);
                logger.info("→ Послат stop сигнал за: {}", instance.name);
            } catch (Exception e) {
                logger.error("Грешка при слању stop сигнала за {}: {}", 
                    instance.name, e.getMessage());
            }
        }
        
        // ФАЗА 2: Graceful shutdown executor-а
        serverExecutor.shutdown();
        
        try {
            logger.info("Чекање да се сервери заустави (timeout: {}s)...", 
                SHUTDOWN_TIMEOUT_SECONDS);
            
            boolean terminated = serverExecutor.awaitTermination(
                SHUTDOWN_TIMEOUT_SECONDS, 
                TimeUnit.SECONDS
            );
            
            if (!terminated) {
                // ФАЗА 3: Форсирани shutdown ако graceful није успео
                logger.warn("⚠ Сервери се нису заустављи у предвиђеном времену");
                logger.warn("⚠ Покрећем форсирано заустављање...");
                
                List<Runnable> notExecuted = serverExecutor.shutdownNow();
                
                if (!notExecuted.isEmpty()) {
                    logger.warn("Број задатака који нису извршени: {}", 
                        notExecuted.size());
                }
                
                // Чекамо још 10 секунди након shutdownNow
                if (!serverExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.error("✗ Неки сервери се нису зауставили ни након форсираног shutdown-а!");
                } else {
                    logger.info("✓ Сервери заустављени након форсираног shutdown-а");
                }
            } else {
                logger.info("✓ Сви сервери успешно заустављени");
            }
            
            // Ажурирамо статус свих инстанци
            for (ServerInstance instance : serverInstances) {
                instance.setStatus(ServerStatus.STOPPED);
            }
            
            running.set(false);
            
        } catch (InterruptedException e) {
            logger.error("Shutdown процес прекинут", e);
            serverExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Враћа тренутни статус свих регистрованих сервера.
     * 
     * @return Непроменљива листа информација о серверима
     */
    public List<ServerInfo> getServerStatus() {
        List<ServerInfo> statusList = new ArrayList<>();
        for (ServerInstance instance : serverInstances) {
            statusList.add(new ServerInfo(
                instance.name,
                instance.port,
                instance.status
            ));
        }
        return statusList;
    }
    
    /**
     * Проверава да ли је менаџер тренутно активан.
     * 
     * @return true ако је покренут, false иначе
     */
    public boolean isRunning() {
        return running.get();
    }
    
    // ════════════════════════════════════════════════════════════
    // УНУТРАШЊЕ КЛАСЕ
    // ════════════════════════════════════════════════════════════
    
    /**
     * Интерна класа која репрезентује једну серверску инстанцу
     * са њеним метаподацима и стањем.
     */
    private static class ServerInstance {
        final String name;
        final Runnable server;
        final int port;
        volatile ServerStatus status;  // volatile јер се чита из више нити
        Future<?> future;
        
        ServerInstance(String name, Runnable server, int port) {
            this.name = name;
            this.server = server;
            this.port = port;
            this.status = ServerStatus.REGISTERED;
        }
        
        void setStatus(ServerStatus status) {
            this.status = status;
        }
        
        void setFuture(Future<?> future) {
            this.future = future;
        }
    }
    
    /**
     * Могући статуси сервера током његовог животног циклуса.
     */
    public enum ServerStatus {
        /** Регистрован али није још покренут */
        REGISTERED,
        
        /** Активно ради и прима конекције */
        RUNNING,
        
        /** У процесу заустављања */
        STOPPING,
        
        /** Успешно заустављен */
        STOPPED,
        
        /** Грешка при покретању */
        FAILED
    }
    
    /**
     * Јавна класа за информације о серверу.
     * Користи се за враћање статуса споља.
     */
    public static class ServerInfo {
        public final String name;
        public final int port;
        public final ServerStatus status;
        
        ServerInfo(String name, int port, ServerStatus status) {
            this.name = name;
            this.port = port;
            this.status = status;
        }
        
        @Override
        public String toString() {
            return String.format("Server [име=%s, порт=%d, статус=%s]", 
                name, port, status);
        }
    }
    
    /**
     * Custom ThreadFactory за именовање серверских нити.
     * Олакшава debugging тако што нити имају описна имена.
     */
    private static class ServerThreadFactory implements ThreadFactory {
        private int counter = 0;
        
        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r, "Server-Main-" + (++counter));
            // Не желимо daemon threads - сервери морају да се експлицитно заустави
            thread.setDaemon(false);
            return thread;
        }
    }
}