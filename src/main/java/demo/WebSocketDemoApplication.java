package demo;

import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.protocol.https.HttpsConfig;
import org.apache.wicket.protocol.https.HttpsMapper;
import org.apache.wicket.protocol.ws.WebSocketSettings;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class WebSocketDemoApplication extends WebApplication {
    private ScheduledExecutorService executor;

    @Override
    public Class<HomePage> getHomePage() {
        return HomePage.class;
    }

    @Override   
    public void init() {
        super.init();
        
        executor = Executors.newScheduledThreadPool(5);
        
        // Initialize and start DatabaseManager
        try {
            DatabaseManager.getInstance().start();
        } catch (Exception e) {
            System.err.println("Failed to initialize DatabaseManager: " + e.getMessage());
            throw new RuntimeException("Application initialization failed", e);
        }
        
        // Update page mappings
        mountPage("/", HomePage.class);
        mountPage("/input", InputPage.class);
        
        WebSocketSettings webSocketSettings = WebSocketSettings.Holder.get(this);
        webSocketSettings.setPort(8080);
        
        // debugging
        getDebugSettings().setDevelopmentUtilitiesEnabled(true);
        getDebugSettings().setAjaxDebugModeEnabled(true);
        
        System.out.println("WebSocketDemoApplication initialized with WebSocket support");
    }

    public ScheduledExecutorService getExecutor() {
        return executor;
    }

    @Override
    protected void onDestroy() {
        DatabaseManager.getInstance().shutdown();
        executor.shutdownNow();
        super.onDestroy();
    }
    
    public static WebSocketDemoApplication get() {
        return (WebSocketDemoApplication) WebApplication.get();
    }
}
