package rs.atekom.prati.server.integration;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import rs.atekom.prati.server.lifecycle.ServerManager;
import rs.atekom.prati.server.OpstiServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * Integration тестови за праве TCP серверe.
 * 
 * <p>Ови тестови покрећу праве OpstiServer инстанце и шаљу им пакете,
 * симулирајући понашање GPS уређаја.</p>
 * 
 * @author Atekom
 * @version 1.0
 */
public class TcpServerIntegrationTest {
    
    private ServerManager serverManager;
    private static final int TEST_PORT = 9000; // Користимо неконфликтан порт
    
    @Before
    public void setUp() {
        serverManager = new ServerManager();
    }
    
    @After
    public void tearDown() {
        if (serverManager != null && serverManager.isRunning()) {
            serverManager.stopAll();
        }
        // Мало чекамо да се портови ослободе
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    // ═══════════════════════════════════════════════════════════
    // ТЕСТ 1: Покретање правог NEON сервера
    // ═══════════════════════════════════════════════════════════
    
    @Test
    public void testNeonServerStartsAndAcceptsConnection() throws Exception {
        // Given: Прави NEON OpstiServer
        OpstiServer neonServer = new OpstiServer(TEST_PORT, 100);
        serverManager.registerServer("NEON-TEST", neonServer, TEST_PORT);
        
        // When: Покрећемо сервер
        serverManager.startAll();
        
        // Дајемо серверу време да се покрене и отвори socket
        Thread.sleep(1000);
        
        // Then: Требало би да можемо да се повежемо
        try (Socket client = new Socket("localhost", TEST_PORT)) {
            assertTrue("Клијент треба да се повеже на сервер", client.isConnected());
            assertFalse("Socket не треба да буде затворен", client.isClosed());
            
            System.out.println("✓ Успешно повезан на порт " + TEST_PORT);
        }
        
        // Cleanup
        serverManager.stopAll();
    }
    
    // ═══════════════════════════════════════════════════════════
    // ТЕСТ 2: Слање правог NEON пакета
    // ═══════════════════════════════════════════════════════════
    
    @Test
    public void testNeonServerReceivesPacket() throws Exception {
        // Given: Покренути NEON сервер
        OpstiServer neonServer = new OpstiServer(TEST_PORT, 100);
        serverManager.registerServer("NEON-TEST", neonServer, TEST_PORT);
        serverManager.startAll();
        Thread.sleep(1000);
        
        // When: Шаљемо прави NEON пакет
        try (Socket client = new Socket("localhost", TEST_PORT)) {
            OutputStream out = client.getOutputStream();
            
            // NEON формат: *HQ,IMEI,V1,timestamp,...#
            String packet = "*HQ,123456789012345,V1,100512,A," +
                          "3723.5324,N,12158.3456,E,0.50,231,150214,FFFFFFFF#\r\n";
            
            System.out.println("→ Шаљем пакет: " + packet.trim());
            out.write(packet.getBytes());
            out.flush();
            
            // Чекамо одговор од сервера (ако постоји)
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            client.setSoTimeout(2000); // 2 секунде timeout за читање
            
            // Дајемо серверу време да обради
            Thread.sleep(1000);
            
            // Then: Сервер треба да остане покренут (није пао)
            assertTrue("ServerManager треба да буде активан", serverManager.isRunning());
            System.out.println("✓ Сервер успешно обрадио пакет");
        }
        
        // Cleanup
        serverManager.stopAll();
    }
    
    // ═══════════════════════════════════════════════════════════
    // ТЕСТ 3: Више конкурентних клијената
    // ═══════════════════════════════════════════════════════════
    
    @Test
    public void testMultipleConcurrentClients() throws Exception {
        // Given: Покренути сервер
        OpstiServer neonServer = new OpstiServer(TEST_PORT, 100);
        serverManager.registerServer("NEON-TEST", neonServer, TEST_PORT);
        serverManager.startAll();
        Thread.sleep(1000);
        
        // When: Креирамо 5 клијената истовремено
        final int CLIENT_COUNT = 5;
        final CountDownLatch completionLatch = new CountDownLatch(CLIENT_COUNT);
        final CountDownLatch startLatch = new CountDownLatch(1);
        
        Thread[] clients = new Thread[CLIENT_COUNT];
        
        for (int i = 0; i < CLIENT_COUNT; i++) {
            final int clientId = i;
            clients[i] = new Thread(() -> {
                try {
                    // Чекамо да сви буду спремни
                    startLatch.await();
                    
                    try (Socket client = new Socket("localhost", TEST_PORT)) {
                        OutputStream out = client.getOutputStream();
                        
                        String packet = String.format(
                            "*HQ,12345678901234%d,V1,100512,A,3723.5324,N,12158.3456,E,0.50,231,150214,FFFFFFFF#\r\n",
                            clientId
                        );
                        
                        out.write(packet.getBytes());
                        out.flush();
                        
                        System.out.println("  → Клијент " + clientId + " послао пакет");
                        
                        // Мало чекамо
                        Thread.sleep(100);
                    }
                    
                    completionLatch.countDown();
                } catch (Exception e) {
                    System.err.println("  ✗ Клијент " + clientId + " грешка: " + e.getMessage());
                    e.printStackTrace();
                }
            });
            clients[i].start();
        }
        
        // Покрећемо све клијенте истовремено
        System.out.println("→ Покрећем " + CLIENT_COUNT + " конкурентних клијената...");
        startLatch.countDown();
        
        // Чекамо да сви заврше (max 10 секунди)
        boolean allFinished = completionLatch.await(10, TimeUnit.SECONDS);
        
        // Then: Сви клијенти треба да се повежу и пошаљу пакете
        assertTrue("Сви клијенти треба да заврше", allFinished);
        assertTrue("ServerManager треба да буде активан", serverManager.isRunning());
        
        System.out.println(" Сви клијенти успешно обрадили пакете");
        
        // Cleanup
        serverManager.stopAll();
    }
    
    // ═══════════════════════════════════════════════════════════
    // ТЕСТ 4: Client disconnect handling
    // ═══════════════════════════════════════════════════════════
    
    @Test
    public void testClientDisconnectDoesNotCrashServer() throws Exception {
        // Given: Покренути сервер
        OpstiServer neonServer = new OpstiServer(TEST_PORT, 100);
        serverManager.registerServer("NEON-TEST", neonServer, TEST_PORT);
        serverManager.startAll();
        Thread.sleep(1000);
        
        // When: Клијент се повеже и одмах дисконектује
        System.out.println("Клијент се повезује...");
        Socket client = new Socket("localhost", TEST_PORT);
        assertTrue("Клијент треба да се повеже", client.isConnected());
        
        System.out.println("→ Клијент се нагло дисконектује (симулирамо network failure)...");
        // Нагло затварање (симулира network failure)
        client.close();
        
        // Дајемо серверу време да обради disconnect
        Thread.sleep(1000);
        
        // Then: Сервер треба да настави да ради
        assertTrue("ServerManager треба да буде активан након disconnect-а", 
                   serverManager.isRunning());
        
        System.out.println("→ Покушавам нову конекцију након disconnect-а...");
        // Требало би да можемо да се повежемо поново
        try (Socket client2 = new Socket("localhost", TEST_PORT)) {
            assertTrue("Нови клијент треба да се повеже", client2.isConnected());
            System.out.println("✓ Нови клијент успешно повезан након disconnect-а");
        }
        
        // Cleanup
        serverManager.stopAll();
    }
    
    // ═══════════════════════════════════════════════════════════
    // ТЕСТ 5: Invalid packet handling
    // ═══════════════════════════════════════════════════════════
    
    @Test
    public void testInvalidPacketDoesNotCrashServer() throws Exception {
        // Given: Покренути сервер
        OpstiServer neonServer = new OpstiServer(TEST_PORT, 100);
        serverManager.registerServer("NEON-TEST", neonServer, TEST_PORT);
        serverManager.startAll();
        Thread.sleep(1000);
        
        // When: Шаљемо невалидан пакет
        try (Socket client = new Socket("localhost", TEST_PORT)) {
            OutputStream out = client.getOutputStream();
            
            // Невалидан формат
            String invalidPacket = "GARBAGE_DATA_INVALID_PACKET_12345\r\n";
            
            System.out.println("→ Шаљем невалидан пакет: " + invalidPacket.trim());
            out.write(invalidPacket.getBytes());
            out.flush();
            
            Thread.sleep(1000);
            
            // Then: Сервер треба да остане стабилан
            assertTrue("ServerManager треба да буде активан након невалидног пакета", 
                       serverManager.isRunning());
            
            System.out.println(" Сервер преживео невалидан пакет");
            
            // Требало би да можемо да пошаљемо валидан пакет после
            System.out.println("→ Шаљем валидан пакет након невалидног...");
            String validPacket = "*HQ,123456789012345,V1,100512,A," +
                               "3723.5324,N,12158.3456,E,0.50,231,150214,FFFFFFFF#\r\n";
            out.write(validPacket.getBytes());
            out.flush();
            
            Thread.sleep(1000);
            
            System.out.println("✓ Валидан пакет успешно послат након невалидног");
        }
        
        // Cleanup
        serverManager.stopAll();
    }
    
    // ═══════════════════════════════════════════════════════════
    // ТЕСТ 6: Server lifecycle под оптерећењем
    // ═══════════════════════════════════════════════════════════
    
    @Test
    public void testServerRestartWithClients() throws Exception {
        // Given: Покренути сервер
        OpstiServer neonServer = new OpstiServer(TEST_PORT, 100);
        serverManager.registerServer("NEON-TEST", neonServer, TEST_PORT);
        serverManager.startAll();
        Thread.sleep(1000);
        
        // When: Клијент шаље пакет
        System.out.println("→ Прва серија: слање пакета...");
        try (Socket client = new Socket("localhost", TEST_PORT)) {
            OutputStream out = client.getOutputStream();
            String packet = "*HQ,111111111111111,V1,100512,A,3723.5324,N,12158.3456,E,0.50,231,150214,FFFFFFFF#\r\n";
            out.write(packet.getBytes());
            out.flush();
            System.out.println("✓ Први пакет послат");
        }
        
        Thread.sleep(500);
        
        // Заустављамо сервер
        System.out.println("Заустављам сервер...");
        serverManager.stopAll();
        Thread.sleep(1000);
        
        // Поново покрећемо
        System.out.println("→ Поново покрећем сервер...");
        OpstiServer neonServer2 = new OpstiServer(TEST_PORT, 100);
        ServerManager serverManager2 = new ServerManager();
        serverManager2.registerServer("NEON-TEST", neonServer2, TEST_PORT);
        serverManager2.startAll();
        Thread.sleep(1000);
        
        // Then: Требало би да ради поново
        System.out.println("→ Друга серија: слање пакета након рестарта...");
        try (Socket client = new Socket("localhost", TEST_PORT)) {
            OutputStream out = client.getOutputStream();
            String packet = "*HQ,222222222222222,V1,100512,A,3723.5324,N,12158.3456,E,0.50,231,150214,FFFFFFFF#\r\n";
            out.write(packet.getBytes());
            out.flush();
            System.out.println("✓ Пакет након рестарта успешно послат");
        }
        
        assertTrue("ServerManager треба да буде активан након рестарта", 
                   serverManager2.isRunning());
        
        // Cleanup
        serverManager2.stopAll();
    }
}