package demo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.Iterator;
import org.apache.wicket.Application;
import org.apache.wicket.protocol.ws.WebSocketSettings;
import org.apache.wicket.protocol.ws.api.IWebSocketConnection;
import org.apache.wicket.protocol.ws.api.message.ConnectedMessage;
import org.apache.wicket.protocol.ws.api.registry.IKey;
import org.apache.wicket.protocol.ws.api.registry.IWebSocketConnectionRegistry;

public class Updater {
    private static final Map<String, SessionInfo> activeSessions = new ConcurrentHashMap<>();
    private static volatile boolean isUpdaterRunning = false;
    private static final Object LOCK = new Object();

    public static void start(ConnectedMessage message, ScheduledExecutorService executor) {
        String sessionId = message.getSessionId();
        SessionInfo sessionInfo = new SessionInfo(
            message.getApplication().getName(),
            sessionId,
            message.getKey()
        );
        
        activeSessions.put(sessionId, sessionInfo);
        System.out.println("Added new session: " + sessionId + ". Total active sessions: " + activeSessions.size());

        startUpdaterIfNeeded(executor);
    }

    private static void startUpdaterIfNeeded(ScheduledExecutorService executor) {
        synchronized (LOCK) {
            if (!isUpdaterRunning) {
                executor.scheduleAtFixedRate(new GlobalUpdateTask(), 0, 1, TimeUnit.SECONDS);
                isUpdaterRunning = true;
                System.out.println("Started global updater task with 1 second interval");
            }
        }
    }

    public static void removeSession(String sessionId) {
        SessionInfo removed = activeSessions.remove(sessionId);
        if (removed != null) {
            System.out.println("Removed session: " + sessionId + ". Remaining sessions: " + activeSessions.size());
        }
    }

    // check active sessions for database pooling
    public static boolean hasActiveSessions() {
        return !activeSessions.isEmpty();
    }

    // clean up sessions with closed connections
    public static void cleanupInactiveSessions() {
        Iterator<Map.Entry<String, SessionInfo>> iterator = activeSessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SessionInfo> entry = iterator.next();
            SessionInfo info = entry.getValue();
            Application application = Application.get(info.applicationName);
            WebSocketSettings settings = WebSocketSettings.Holder.get(application);
            IWebSocketConnectionRegistry registry = settings.getConnectionRegistry();
            IWebSocketConnection conn = registry.getConnection(application, info.sessionId, info.key);
            if (conn == null || !conn.isOpen()) {
                System.out.println("Cleaning up inactive session: " + info.sessionId);
                iterator.remove();
            }
        }
    }

    private static class SessionInfo {
        final String applicationName;
        final String sessionId;
        final IKey key;
        int lastValue = -1;

        SessionInfo(String applicationName, String sessionId, IKey key) {
            this.applicationName = applicationName;
            this.sessionId = sessionId;
            this.key = key;
        }
    }

    private static class GlobalUpdateTask implements Runnable {
        private int lastBroadcastValue = -1;

        @Override
        public void run() {
            // Clean up sessions with closed connections
            cleanupInactiveSessions();
			
            try {
                activeSessions.forEach((sessionId, sessionInfo) -> {
                    try {
                        int currentValue = DatabaseManager.getInstance().getSessionValue(sessionId);
                        updateSession(sessionInfo, currentValue);
                    } catch (Exception e) {
                        System.err.println("Error updating session " + sessionId + ": " + e.getMessage());
                        // Naive check
                        if (e.getMessage() != null && 
                            (e.getMessage().contains("Connection is closed") || 
                             e.getMessage().contains("Connection closed"))) {
                            removeSession(sessionId);
                        }
                    }
                });
            } catch (Exception e) {
                System.err.println("Error in global update task: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private void updateSession(SessionInfo sessionInfo, int currentValue) {
            try {
                Application application = Application.get(sessionInfo.applicationName);
                WebSocketSettings settings = WebSocketSettings.Holder.get(application);
                IWebSocketConnectionRegistry registry = settings.getConnectionRegistry();
                IWebSocketConnection connection = registry.getConnection(application, sessionInfo.sessionId, sessionInfo.key);

                if (connection != null && connection.isOpen()) {
                    String update = String.format("%d", currentValue);
                    connection.sendMessage(update);
                    sessionInfo.lastValue = currentValue;
                    System.out.println("Sent update " + update + " to session " + sessionInfo.sessionId);
                } else {
                    throw new RuntimeException("Connection is closed for session: " + sessionInfo.sessionId);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to send message to session " + sessionInfo.sessionId + ": " + e.getMessage(), e);
            }
        }
    }
}
