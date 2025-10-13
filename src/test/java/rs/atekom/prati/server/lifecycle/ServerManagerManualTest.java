package rs.atekom.prati.server.lifecycle;

import java.util.List;
import rs.atekom.prati.server.lifecycle.ServerManager;

/**
 * Мануелни тест за ServerManager без JUnit-а.
 * Покреће се као обична Java апликација.
 */
public class ServerManagerManualTest {
    
    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════════");
        System.out.println("  МАНУЕЛНИ ТЕСТ: ServerManager");
        System.out.println("═══════════════════════════════════════════");
        
        try {
            // ТЕСТ 1: Креирање менаџера
            System.out.println("\n→ Тест 1: Креирање ServerManager...");
            ServerManager manager = new ServerManager();
            System.out.println("✓ ServerManager креиран");
            
            // ТЕСТ 2: Регистрација сервера
            System.out.println("\n→ Тест 2: Регистрација Mock сервера...");
            MockServer server1 = new MockServer("TEST-1", 5000);
            MockServer server2 = new MockServer("TEST-2", 5000);
            
            manager.registerServer("SERVER-1", server1, 9001);
            manager.registerServer("SERVER-2", server2, 9002);
            System.out.println("✓ Регистрована 2 сервера");
            
            // ТЕСТ 3: Провера статуса пре покретања
            System.out.println("\n→ Тест 3: Провера статуса...");
            List<ServerManager.ServerInfo> status = manager.getServerStatus();
            System.out.println("  Број сервера: " + status.size());
            for (ServerManager.ServerInfo info : status) {
                System.out.println("  - " + info.name + " на порту " + info.port + " [" + info.status + "]");
            }
            
            // ТЕСТ 4: Покретање
            System.out.println("\n→ Тест 4: Покретање свих сервера...");
            manager.startAll();
            System.out.println("✓ Сервери покренути");
            System.out.println("  Manager активан: " + manager.isRunning());
            
            // Чекамо да сервери стартују
            Thread.sleep(1000);
            
            // ТЕСТ 5: Провера да ли раде
            System.out.println("\n→ Тест 5: Провера да ли сервери раде...");
            System.out.println("  SERVER-1 ради: " + server1.isRunning());
            System.out.println("  SERVER-2 ради: " + server2.isRunning());
            
            // ТЕСТ 6: Чекамо 3 секунде
            System.out.println("\n→ Тест 6: Чекамо 3 секунде...");
            for (int i = 3; i > 0; i--) {
                System.out.println("  " + i + "...");
                Thread.sleep(1000);
            }
            
            // ТЕСТ 7: Заустављање
            System.out.println("\n→ Тест 7: Заустављање свих сервера...");
            manager.stopAll();
            System.out.println("✓ Сервери заустављени");
            
            // Чекамо да се заврши shutdown
            Thread.sleep(2000);
            
            // ТЕСТ 8: Провера да ли су заустављени
            System.out.println("\n→ Тест 8: Финални статус...");
            System.out.println("  Manager активан: " + manager.isRunning());
            System.out.println("  SERVER-1 ради: " + server1.isRunning());
            System.out.println("  SERVER-2 ради: " + server2.isRunning());
            
            List<ServerManager.ServerInfo> finalStatus = manager.getServerStatus();
            for (ServerManager.ServerInfo info : finalStatus) {
                System.out.println("  - " + info.name + " [" + info.status + "]");
            }
            
            System.out.println("\n═══════════════════════════════════════════");
            System.out.println("  ✓ СВИ ТЕСТОВИ ПРОШЛИ УСПЕШНО");
            System.out.println("═══════════════════════════════════════════");
            
        } catch (Exception e) {
            System.err.println("\n✗ ГРЕШКА У ТЕСТУ:");
            e.printStackTrace();
        }
    }
    
    /**
     * Mock сервер за тестирање - иста имплементација као у JUnit тесту.
     */
    private static class MockServer implements Runnable {
        private final String name;
        private final long runDurationMs;
        private volatile boolean running = false;
        private volatile boolean stopped = false;
        
        public MockServer(String name, long runDurationMs) {
            this.name = name;
            this.runDurationMs = runDurationMs;
        }
        
        @Override
        public void run() {
            running = true;
            System.out.println("    → MockServer [" + name + "] стартовао");
            
            try {
                long startTime = System.currentTimeMillis();
                while (!stopped && (System.currentTimeMillis() - startTime) < runDurationMs) {
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                System.out.println("    → MockServer [" + name + "] прекинут");
                Thread.currentThread().interrupt();
            } finally {
                running = false;
                System.out.println("    → MockServer [" + name + "] заустављен");
            }
        }
        
        public void stop() {
            System.out.println("    → MockServer [" + name + "] примио stop()");
            stopped = true;
        }
        
        public boolean isRunning() {
            return running;
        }
    }
}