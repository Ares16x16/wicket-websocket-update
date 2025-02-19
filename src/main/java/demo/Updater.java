package demo;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.wicket.Application;
import org.apache.wicket.protocol.ws.WebSocketSettings;
import org.apache.wicket.protocol.ws.api.IWebSocketConnection;
import org.apache.wicket.protocol.ws.api.message.ConnectedMessage;
import org.apache.wicket.protocol.ws.api.registry.IKey;
import org.apache.wicket.protocol.ws.api.registry.IWebSocketConnectionRegistry;

public class Updater {
    public static void start(ConnectedMessage message, ScheduledExecutorService executor) {
        System.out.println("Starting Updater with session: " + message.getSessionId());
        UpdateTask updateTask = new UpdateTask(
            message.getApplication().getName(),
            message.getSessionId(),
            message.getKey()
        );
        executor.scheduleWithFixedDelay(updateTask, 0, 1, TimeUnit.SECONDS);
    }

    private static class UpdateTask implements Runnable {
        private final String applicationName;
        private final String sessionId;
        private final IKey key;

        private UpdateTask(String applicationName, String sessionId, IKey key) {
            this.applicationName = applicationName;
            this.sessionId = sessionId;
            this.key = key;
        }

        @Override
        public void run() {
            try {
                Application application = Application.get(applicationName);
                WebSocketSettings settings = WebSocketSettings.Holder.get(application);
                IWebSocketConnectionRegistry registry = settings.getConnectionRegistry();
                IWebSocketConnection connection = registry.getConnection(application, sessionId, key);

                if (connection == null || !connection.isOpen()) {
                    System.out.println("No active connection for session: " + sessionId);
                    return;
                }

                int randomValue = RandomValueGenerator.generateValue();
                String update = String.format("%d", randomValue);
                System.out.println("Sending update: " + update);
                connection.sendMessage(update);
            } catch (Exception e) {
                System.out.println("Error in update task: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
}
