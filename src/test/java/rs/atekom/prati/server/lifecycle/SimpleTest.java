package rs.atekom.prati.server.lifecycle;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Једноставан тест да проверимо JUnit setup.
 */
public class SimpleTest {
    
    @Test
    public void testBasicAssertion() {
        System.out.println("Тест почео!");
        assertEquals(2, 1 + 1);
        System.out.println("Тест прошао!");
    }
    
    @Test
    public void testServerManagerExists() {
        ServerManager manager = new ServerManager();
        assertNotNull("ServerManager не може бити null", manager);
        assertFalse("Нови ServerManager не треба да буде покренут", manager.isRunning());
    }
}