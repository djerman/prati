package rs.atekom.prati.server.lifecycle;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import rs.atekom.prati.server.lifecycle.ServerManager.ServerInfo;
import rs.atekom.prati.server.lifecycle.ServerManager.ServerStatus;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

/**
 * Unit тестови за ServerManager класу.
 * 
 * <p>Тестира:</p>
 * <ul>
 *   <li>Регистрацију сервера</li>
 *   <li>Покретање и заустављање</li>
 *   <li>Graceful shutdown</li>
 *   <li>Thread safety</li>
 * </ul>
 * 
 * @author Atekom
 * @version 1.0
 */
public class ServerManagerTest {
    
    private ServerManager manager;
    
    @Before
    public void setUp() {
        manager = new ServerManager();
    }
    
    @After
    public void tearDown() {
        if (manager != null && manager.isRunning()) {
            manager.stopAll();
        }
    }
    
    // ═══════════════════════════════════════════════════════════
    // ТЕСТ 1: Основна регистрација сервера
    // ═══════════════════════════════════════════════════════════
    
    @Test
    public void testRegisterServer_Success() {
        // Given: Mock сервер
        MockServer mockServer = new MockServer("TEST-SERVER", 3000);
        
        // When: Региструјемо сервер
        manager.registerServer("TEST", mockServer, 9999);
        
        // Then: Требало би да буде регистрован
        List<ServerInfo> status = manager.getServerStatus();
        assertEquals(1, status.size());
        assertEquals("TEST", status.get(0).name);
        assertEquals(9999, status.get(0).port);
        assertEquals(ServerStatus.REGISTERED, status.get(0).status);
    }
    
    @Test(expected = IllegalStateException.class)
    public void testRegisterServer_WhileRunning_ThrowsException() {
        // Given: Покренут менаџер
        MockServer mockServer = new MockServer("TEST", 1000);
        manager.registerServer("TEST1", mockServer, 9999);
        manager.startAll();
        
        // When: Покушавамо да додамо још један (требало би да падне)
        MockServer anotherServer = new MockServer("TEST2", 1000);
        manager.registerServer("TEST2", anotherServer, 9998);
        
        // Then: Очекујемо IllegalStateException (аутоматски)
    }
    
    @Test(expected = NullPointerException.class)
    public void testRegisterServer_NullServer_ThrowsException() {
        // When: Покушавамо да региструјемо null
        manager.registerServer("TEST", null, 9999);
        
        // Then: Очекујемо NullPointerException
    }
    
    // ═══════════════════════════════════════════════════════════
    // ТЕСТ 2: Покретање сервера
    // ═══════════════════════════════════════════════════════════
    
    @Test
    public void testStartAll_SingleServer_StartsSuccessfully() throws InterruptedException {
        // Given: Један регистрован сервер
        MockServer mockServer = new MockServer("TEST", 2000);
        manager.registerServer("TEST", mockServer, 9999);
        
        // When: Покрећемо
        manager.startAll();
        
        // Then: Статус треба да буде RUNNING
        assertTrue(manager.isRunning());
        
        // Чекамо да сервер стартује
        assertTrue("Сервер није стартовао", 
            mockServer.waitForStart(3, TimeUnit.SECONDS));
        
        List<ServerInfo> status = manager.getServerStatus();
        assertEquals(ServerStatus.RUNNING, status.get(0).status);
    }
    
    @Test
    public void testStartAll_MultipleServers_AllStart() throws InterruptedException {
        // Given: Три сервера
        MockServer server1 = new MockServer("SERVER1", 2000);
        MockServer server2 = new MockServer("SERVER2", 2000);
        MockServer server3 = new MockServer("SERVER3", 2000);
        
        manager.registerServer("S1", server1, 9001);
        manager.registerServer("S2", server2, 9002);
        manager.registerServer("S3", server3, 9003);
        
        // When: Покрећемо све
        manager.startAll();
        
        // Then: Сви треба да раде
        assertTrue(server1.waitForStart(3, TimeUnit.SECONDS));
        assertTrue(server2.waitForStart(3, TimeUnit.SECONDS));
        assertTrue(server3.waitForStart(3, TimeUnit.SECONDS));
        
        assertEquals(3, manager.getServerStatus().size());
    }
    
    @Test(expected = IllegalStateException.class)
    public void testStartAll_NoServers_ThrowsException() {
        // When: Покушавамо да покренемо без регистрованих сервера
        manager.startAll();
        
        // Then: Очекујемо IllegalStateException
    }
    
    @Test(expected = IllegalStateException.class)
    public void testStartAll_CalledTwice_ThrowsException() {
        // Given: Један сервер
        MockServer mockServer = new MockServer("TEST", 1000);
        manager.registerServer("TEST", mockServer, 9999);
        manager.startAll();
        
        // When: Покушавамо да покренемо поново
        manager.startAll();
        
        // Then: Очекујемо IllegalStateException
    }
    
    // ═══════════════════════════════════════════════════════════
    // ТЕСТ 3: Заустављање сервера
    // ═══════════════════════════════════════════════════════════
    
    @Test
    public void testStopAll_GracefulShutdown_Success() throws InterruptedException {
        // Given: Покренут сервер
        MockServer mockServer = new MockServer("TEST", 5000);
        manager.registerServer("TEST", mockServer, 9999);
        manager.startAll();
        
        assertTrue(mockServer.waitForStart(3, TimeUnit.SECONDS));
        
        // When: Заустављамо
        manager.stopAll();
        
        // Then: Сервер треба да буде заустављен
        assertTrue("Сервер није заустављен", 
            mockServer.waitForStop(5, TimeUnit.SECONDS));
        assertFalse(manager.isRunning());
        
        List<ServerInfo> status = manager.getServerStatus();
        assertEquals(ServerStatus.STOPPED, status.get(0).status);
    }
    
    @Test
    public void testStopAll_CalledWhenNotRunning_DoesNothing() {
        // Given: Неактиван менаџер
        assertFalse(manager.isRunning());
        
        // When: Покушавамо да зауставимо
        manager.stopAll();
        
        // Then: Не треба да падне, само warning у логу
        assertFalse(manager.isRunning());
    }
    
    @Test
    public void testStopAll_MultipleServers_AllStop() throws InterruptedException {
        // Given: Три покренута сервера
        MockServer server1 = new MockServer("S1", 3000);
        MockServer server2 = new MockServer("S2", 3000);
        MockServer server3 = new MockServer("S3", 3000);
        
        manager.registerServer("S1", server1, 9001);
        manager.registerServer("S2", server2, 9002);
        manager.registerServer("S3", server3, 9003);
        manager.startAll();
        
        // Чекамо да сви стартују
        server1.waitForStart(2, TimeUnit.SECONDS);
        server2.waitForStart(2, TimeUnit.SECONDS);
        server3.waitForStart(2, TimeUnit.SECONDS);
        
        // When: Заустављамо све
        manager.stopAll();
        
        // Then: Сви треба да буду заустављени
        assertTrue(server1.waitForStop(5, TimeUnit.SECONDS));
        assertTrue(server2.waitForStop(5, TimeUnit.SECONDS));
        assertTrue(server3.waitForStop(5, TimeUnit.SECONDS));
    }
    
    // ═══════════════════════════════════════════════════════════
    // MOCK СЕРВЕР - симулира прави TCP сервер
    // ═══════════════════════════════════════════════════════════
    
    /**
     * Mock имплементација сервера за тестирање.
     * Симулира понашање правог TCP сервера са методом stop().
     */
    private static class MockServer implements Runnable {
        private final String name;
        private final long runDurationMs;
        private final AtomicBoolean running = new AtomicBoolean(false);
        private final AtomicBoolean stopped = new AtomicBoolean(false);
        private final CountDownLatch startLatch = new CountDownLatch(1);
        private final CountDownLatch stopLatch = new CountDownLatch(1);
        
        public MockServer(String name, long runDurationMs) {
            this.name = name;
            this.runDurationMs = runDurationMs;
        }
        
        @Override
        public void run() {
            running.set(true);
            startLatch.countDown(); // Сигнализирамо да смо стартовали
            
            System.out.println("MockServer [" + name + "] стартовао");
            
            try {
                // Симулирамо рад сервера - чекамо или док не истекне време или док нас не зауставе
                long startTime = System.currentTimeMillis();
                while (!stopped.get() && 
                       (System.currentTimeMillis() - startTime) < runDurationMs) {
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                System.out.println("MockServer [" + name + "] прекинут");
                Thread.currentThread().interrupt();
            } finally {
                running.set(false);
                stopLatch.countDown(); // Сигнализирамо да смо се зауставили
                System.out.println("MockServer [" + name + "] заустављен");
            }
        }
        
        /**
         * Метода коју ServerManager позива за заустављање.
         * Мора имати исти потпис као у правим серверима.
         */
        public void stop() {
            System.out.println("MockServer [" + name + "] примио stop сигнал");
            stopped.set(true);
        }
        
        /**
         * Чека да сервер стартује.
         * @return true ако је стартовао, false ако је timeout
         */
        public boolean waitForStart(long timeout, TimeUnit unit) throws InterruptedException {
            return startLatch.await(timeout, unit);
        }
        
        /**
         * Чека да сервер престане са радом.
         * @return true ако је заустављен, false ако је timeout
         */
        public boolean waitForStop(long timeout, TimeUnit unit) throws InterruptedException {
            return stopLatch.await(timeout, unit);
        }
        
        public boolean isRunning() {
            return running.get();
        }
    }
}