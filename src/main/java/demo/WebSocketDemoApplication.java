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
        // setRootRequestMapper(new HttpsMapper(getRootRequestMapper(), new HttpsConfig(8080, 8443)));
        
        mountPage("/", HomePage.class);
        
        WebSocketSettings webSocketSettings = WebSocketSettings.Holder.get(this);
        webSocketSettings.setPort(8080);
        // webSocketSettings.setSecurePort(8443);
        
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
        executor.shutdownNow();
        super.onDestroy();
    }
    
    public static WebSocketDemoApplication get() {
        return (WebSocketDemoApplication) WebApplication.get();
    }
}
