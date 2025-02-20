package demo;

import javax.persistence.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import java.util.HashSet;
import java.util.Set;

public class DatabaseManager {
    private static final DatabaseManager instance = new DatabaseManager();
    private final Map<String, AtomicInteger> sessionValues = new ConcurrentHashMap<>();
    private final Map<String, Thread> pollingThreads = new ConcurrentHashMap<>();
    private final Set<String> activeSessions = new HashSet<>();
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
        synchronized (activeSessions) {
            if (activeSessions.contains(sessionId)) {
                return; // Session is already being polled
            }
        }

        // Check if the session has already timed out
        if (isSessionTimedOut(sessionId)) {
            return; // Do not start polling if the session has timed out
        }

        synchronized (activeSessions) {
            activeSessions.add(sessionId);
        }

        AtomicInteger sessionValue = new AtomicInteger(0);
        sessionValues.put(sessionId, sessionValue);

        Thread pollingThread = new Thread(() -> {
            long startTime = System.currentTimeMillis();
            while (running) {
                updateSessionValue(sessionId, sessionValue);
                if (System.currentTimeMillis() - startTime > TimeUnit.MINUTES.toMillis(5)) {
                    System.out.println("Session " + sessionId + " timed out.");
                    updateSessionValue(sessionId, -1); // -1 indicates timeout
                    stopSessionPolling(sessionId);
                    break;
                }
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

    private boolean isSessionTimedOut(String sessionId) {
        EntityManager em = emf.createEntityManager();
        try {
            PaymentSession entity = em.createQuery("SELECT p FROM PaymentSession p WHERE p.sessionId = :sessionId", PaymentSession.class)
                                      .setParameter("sessionId", sessionId)
                                      .getSingleResult();
            return "timeout".equals(entity.getStatus());
        } finally {
            em.close();
        }
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

    public void updateSessionValue(String sessionId, int value) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            // Use getResultList() to avoid exception if no record is found.
            List<PaymentSession> results = em.createQuery("SELECT p FROM PaymentSession p WHERE p.sessionId = :sessionId", PaymentSession.class)
                                          .setParameter("sessionId", sessionId)
                                          .getResultList();
            if (results.isEmpty()) {
                System.err.println("No PaymentSession found for sessionId: " + sessionId);
                tx.rollback();
                return;
            }
            PaymentSession entity = results.get(0);
            entity.setValue(value);
            if (value == 1) {
                entity.setStatus("success");
            } else if (value == -1) {
                entity.setStatus("timeout");
            }
            tx.commit();
            // Update our local cache
            AtomicInteger atomicValue = sessionValues.get(sessionId);
            if (atomicValue != null) {
                atomicValue.set(value);
            }
        } catch (Exception e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            e.printStackTrace();
        } finally {
            em.close();
        }
    }

    public void updateSessionValueDirect(String sessionId, int value) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            String status = (value == 1) ? "success" : (value == -1 ? "timeout" : "pending");
            int updated = em.createQuery("UPDATE PaymentSession p SET p.value = :value, p.status = :status WHERE p.sessionId = :sessionId")
                            .setParameter("value", value)
                            .setParameter("status", status)
                            .setParameter("sessionId", sessionId)
                            .executeUpdate();
            tx.commit();
            
            AtomicInteger atomicValue = sessionValues.get(sessionId);
            if (atomicValue != null) {
                atomicValue.set(value);
            }
            System.out.println("Updated session " + sessionId + " with value " + value + ". Rows updated: " + updated);
        } catch (Exception e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            e.printStackTrace();
        } finally {
            em.close();
        }
    }

    public void createNewSessionRecord(String sessionId) {
        EntityManager em = emf.createEntityManager();
        try {
            em.getTransaction().begin();
            TypedQuery<PaymentSession> query = em.createQuery("SELECT p FROM PaymentSession p WHERE p.sessionId = :sessionId", PaymentSession.class);
            query.setParameter("sessionId", sessionId);
            List<PaymentSession> results = query.getResultList();
            if (results.isEmpty()) {
                PaymentSession entity = new PaymentSession();
                entity.setSessionId(sessionId);
                entity.setValue(0);
                entity.setStatus("pending");
                em.persist(entity);
            }
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
        synchronized (activeSessions) {
            activeSessions.remove(sessionId);
        }
    }

    public void shutdown() {
        running = false;
        pollingThreads.values().forEach(Thread::interrupt);
    }

    public String getSessionStatus(String sessionId) {
        EntityManager em = emf.createEntityManager();
        try {
            PaymentSession entity = em.createQuery("SELECT p FROM PaymentSession p WHERE p.sessionId = :sessionId", PaymentSession.class)
                                      .setParameter("sessionId", sessionId)
                                      .getSingleResult();
            return entity.getStatus();
        } finally {
            em.close();
        }
    }

    public void updateSessionStatus(String sessionId, String status) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            List<PaymentSession> results = em.createQuery("SELECT p FROM PaymentSession p WHERE p.sessionId = :sessionId", PaymentSession.class)
                .setParameter("sessionId", sessionId)
                .getResultList();
            if (results.isEmpty()) {
                System.err.println("No PaymentSession found for sessionId: " + sessionId);
                tx.rollback();
                return;
            }
            PaymentSession entity = results.get(0);
            entity.setStatus(status);
            tx.commit();
        } catch (Exception e) {
            if (tx.isActive()) tx.rollback();
            e.printStackTrace();
        } finally {
            em.close();
        }
    }
}
