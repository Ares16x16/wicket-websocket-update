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
    private final Label qrCode;
    private String currentSessionId;

    public HomePage() {
        valuePanel = new WebMarkupContainer("valuePanel");
        qrCode = new Label("qrCode", Model.of("<img src='qr.png' alt='QR Code'/>"));
        qrCode.setEscapeModelStrings(false);
        qrCode.setOutputMarkupId(true);
        wsMessage = new Label("wsMessage", Model.of("Please scan the QR code to pay"));
        wsMessage.setOutputMarkupId(true);
        wsMessage.setOutputMarkupPlaceholderTag(true);
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        valuePanel.setOutputMarkupPlaceholderTag(true);
        wsMessage.setOutputMarkupId(true);
        valuePanel.add(qrCode);
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
                    currentSessionId = message.getSessionId();
                    System.out.println("WebSocket connected! SessionId: " + currentSessionId);
                    Updater.start(message, WebSocketDemoApplication.get().getExecutor());
                    createNewSessionRecord(currentSessionId);
                    DatabaseManager.getInstance().startSessionPolling(currentSessionId);
                } catch (Exception ex) {
                    System.err.println("Error in onConnect: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }

            @Override
            protected void onMessage(WebSocketRequestHandler handler, TextMessage message) {
                try {
                    String text = message.getText();
                    if ("1".equals(text)) {
                        updatePaymentStatus(currentSessionId, "success");
                        wsMessage.setDefaultModelObject("Payment Successful");
                    } else {
                        wsMessage.setDefaultModelObject("Please scan the QR code to pay");
                    }
                    handler.add(wsMessage);
                    handler.add(valuePanel);
                    handler.appendJavaScript("document.querySelector('[wicket\\\\:id=\"wsMessage\"]').innerHTML = '" + wsMessage.getDefaultModelObject() + "';");
                } catch (Exception ex) {
                    System.err.println("Error handling WebSocket message: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }

            @Override
            protected void onClose(org.apache.wicket.protocol.ws.api.message.ClosedMessage message) {
                super.onClose(message);
                DatabaseManager.getInstance().stopSessionPolling(currentSessionId);
            }
        });
    }

    private void createNewSessionRecord(String sessionId) {
        DatabaseManager.getInstance().addNewSessionValue(sessionId, 0);
    }

    private void updatePaymentStatus(String sessionId, String status) {
        DatabaseManager.getInstance().addNewSessionValue(sessionId, 1); // Assuming value 1 indicates success
    }
}
