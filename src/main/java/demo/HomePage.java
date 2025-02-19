package demo;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.protocol.ws.api.WebSocketBehavior;
import org.apache.wicket.protocol.ws.api.message.ConnectedMessage;
import org.apache.wicket.protocol.ws.api.message.TextMessage;
import org.apache.wicket.protocol.ws.api.WebSocketRequestHandler;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.model.Model;

public class HomePage extends WebPage {
    private static final long serialVersionUID = 1L;
    private final WebMarkupContainer valuePanel;
    private final Label wsMessage;

    public HomePage() {
        valuePanel = new WebMarkupContainer("valuePanel");
        wsMessage = new Label("wsMessage", Model.of("Waiting for update"));
        wsMessage.setOutputMarkupId(true);
        wsMessage.setOutputMarkupPlaceholderTag(true); 
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        valuePanel.setOutputMarkupPlaceholderTag(true);
        wsMessage.setOutputMarkupId(true);
        valuePanel.add(wsMessage);
        add(valuePanel);
        addWebSocketUpdating();
    }

    private void addWebSocketUpdating() {
        add(new WebSocketBehavior() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onConnect(ConnectedMessage message) {
                try {
                    super.onConnect(message);
                    System.out.println("WebSocket connected! SessionId: " + message.getSessionId());
                    Updater.start(message, WebSocketDemoApplication.get().getExecutor());
                } catch (Exception ex) {
                    System.err.println("Error in onConnect: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }

            @Override
            protected void onMessage(WebSocketRequestHandler handler, TextMessage message) {
                try {
                    String text = message.getText();
                    System.out.println("WebSocket received message: " + text);
                    wsMessage.setDefaultModelObject(text);
                    handler.add(wsMessage);
                    handler.add(valuePanel);
                    handler.appendJavaScript("document.querySelector('[wicket\\\\:id=\"wsMessage\"]').innerHTML = '" + text + "';");
                } catch (Exception ex) {
                    System.err.println("Error handling WebSocket message: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        });
    }
}
