package demo;

import javax.persistence.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DatabaseManager {
    private static final DatabaseManager instance = new DatabaseManager();
    private final Map<String, AtomicInteger> sessionValues = new ConcurrentHashMap<>();
    private final Map<String, Thread> pollingThreads = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Failed to load MySQL driver", e);
        }
    }

    private static final EntityManagerFactory emf = Persistence.createEntityManagerFactory("demo");

    private DatabaseManager() {
        // No global polling thread needed
    }

    public static DatabaseManager getInstance() {
        return instance;
    }

    public void startSessionPolling(String sessionId) {
        AtomicInteger sessionValue = new AtomicInteger(0);
        sessionValues.put(sessionId, sessionValue);

        Thread pollingThread = new Thread(() -> {
            while (running) {
                updateSessionValue(sessionId, sessionValue);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "DB-Polling-Thread-" + sessionId);
        pollingThread.setDaemon(true);
        pollingThread.start();
        pollingThreads.put(sessionId, pollingThread);
    }

    private void updateSessionValue(String sessionId, AtomicInteger sessionValue) {
        EntityManager em = emf.createEntityManager();
        try {
            TypedQuery<PaymentSession> query = em.createQuery(
                "SELECT p FROM PaymentSession p WHERE p.sessionId = :sessionId ORDER BY p.id DESC", PaymentSession.class);
            query.setParameter("sessionId", sessionId);
            query.setMaxResults(1);
            List<PaymentSession> results = query.getResultList();
            if (!results.isEmpty()) {
                sessionValue.set(results.get(0).getValue());
            }
        } finally {
            em.close();
        }
    }

    public int getSessionValue(String sessionId) {
        AtomicInteger sessionValue = sessionValues.get(sessionId);
        return sessionValue != null ? sessionValue.get() : 0;
    }

    public void addNewSessionValue(String sessionId, int value) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            PaymentSession entity = new PaymentSession();
            entity.setSessionId(sessionId);
            entity.setValue(value);
            entity.setStatus("pending");
            em.persist(entity);
            em.getTransaction().commit();
        } finally {
            em.close();
        }
    }

    public void stopSessionPolling(String sessionId) {
        Thread pollingThread = pollingThreads.remove(sessionId);
        if (pollingThread != null) {
            pollingThread.interrupt();
        }
        sessionValues.remove(sessionId);
    }

    public void shutdown() {
        running = false;
        pollingThreads.values().forEach(Thread::interrupt);
    }
}
