package demo;

import javax.persistence.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DatabaseManager {
    private static final DatabaseManager instance = new DatabaseManager();
    private final AtomicInteger currentValue = new AtomicInteger(0);
    private volatile boolean running = true;
    private final Thread pollingThread;

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load MySQL driver", e);
        }
    }

    private static final EntityManagerFactory emf = Persistence.createEntityManagerFactory("demo");

    private DatabaseManager() {
        pollingThread = new Thread(() -> {
            while (running) {
                updateCurrentValue();
                try {
                    Thread.sleep(100); // Poll every 100ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "DB-Polling-Thread");
        pollingThread.setDaemon(true);
        pollingThread.start();
    }

    private void updateCurrentValue() {
        EntityManager em = emf.createEntityManager();
        try {
            TypedQuery<ValueEntity> query = em.createQuery(
                "SELECT v FROM ValueEntity v ORDER BY v.id DESC", ValueEntity.class);
            query.setMaxResults(1);
            List<ValueEntity> results = query.getResultList();
            if (!results.isEmpty()) {
                currentValue.set(results.get(0).getValue());
            }
        } finally {
            em.close();
        }
    }

    public static int getCurrentValue() {
        return instance.currentValue.get();
    }

    public static void addNewValue(int value) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            ValueEntity entity = new ValueEntity();
            entity.setValue(value);
            em.persist(entity);
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    public void shutdown() {
        running = false;
        pollingThread.interrupt();
    }
}
