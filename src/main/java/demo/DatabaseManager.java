package demo;

import javax.persistence.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class DatabaseManager {
    private static final AtomicReference<DatabaseManager> INSTANCE = new AtomicReference<>();
    private final AtomicInteger currentValue = new AtomicInteger(0);
    private volatile boolean running = true;
    private final Thread pollingThread;
    private static EntityManagerFactory emf;
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY = 5000; // 5 seconds

    private static synchronized EntityManagerFactory getEntityManagerFactory() {
        if (emf == null) {
            try {
                // Ensure MySQL driver is loaded
                Class.forName("com.mysql.cj.jdbc.Driver");
                emf = Persistence.createEntityManagerFactory("demo");
                // Test connection
                EntityManager testEm = emf.createEntityManager();
                testEm.close();
            } catch (Exception e) {
                System.err.println("Failed to initialize EntityManagerFactory: " + e.getMessage());
                throw new RuntimeException("Database initialization failed", e);
            }
        }
        return emf;
    }

    public static DatabaseManager getInstance() {
        DatabaseManager instance = INSTANCE.get();
        if (instance == null) {
            synchronized (DatabaseManager.class) {
                instance = INSTANCE.get();
                if (instance == null) {
                    instance = new DatabaseManager();
                    INSTANCE.set(instance);
                }
            }
        }
        return instance;
    }

    private DatabaseManager() {
        pollingThread = new Thread(() -> {
            int retryCount = 0;
            while (running) {
                try {
                    // Added check for active browser connections.
                    if (!demo.Updater.hasActiveSessions()) {
                        System.out.println("No active sessions, skipping database polling.");
                        Thread.sleep(1000);
                        continue;
                    }
                    updateCurrentValue();
                    retryCount = 0; // Reset retry count on success
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Error in polling thread: " + e.getMessage());
                    retryCount++;
                    if (retryCount >= MAX_RETRIES) {
                        System.err.println("Max retries reached, stopping polling thread");
                        running = false;
                        break;
                    }
                    try {
                        Thread.sleep(RETRY_DELAY);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, "DB-Polling-Thread");
        pollingThread.setDaemon(true);
    }

    public void start() {
        if (!pollingThread.isAlive()) {
            running = true;
            pollingThread.start();
        }
    }

    private void updateCurrentValue() {
        EntityManager em = getEntityManagerFactory().createEntityManager();
        try {
            TypedQuery<ValueEntity> query = em.createQuery(
                "SELECT v FROM ValueEntity v ORDER BY v.id DESC", ValueEntity.class);
            query.setMaxResults(1);
            List<ValueEntity> results = query.getResultList();
            if (!results.isEmpty()) {
                int newValue = results.get(0).getValue();
                int oldValue = currentValue.get();
                if (newValue != oldValue) {
                    System.out.println("Database value updated from " + oldValue + " to " + newValue);
                    currentValue.set(newValue);
                }
            }
        } finally {
            em.close();
        }
    }

    public static int getCurrentValue() {
        return getInstance().currentValue.get();
    }

    public static void addNewValue(int value) {
        EntityManager em = getEntityManagerFactory().createEntityManager();
        try {
            em.getTransaction().begin();
            ValueEntity entity = new ValueEntity();
            entity.setValue(value);
            em.persist(entity);
            em.getTransaction().commit();
        } catch (Exception e) {
            System.err.println("Error adding new value: " + e.getMessage());
            throw e;
        } finally {
            em.close();
        }
    }

    public void shutdown() {
        running = false;
        pollingThread.interrupt();
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }
}
