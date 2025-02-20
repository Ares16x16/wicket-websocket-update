package demo;

import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AbstractAjaxTimerBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.model.Model;
import org.apache.wicket.protocol.ws.api.WebSocketBehavior;
import org.apache.wicket.protocol.ws.api.message.ConnectedMessage;
import org.apache.wicket.protocol.ws.api.message.TextMessage;
import org.apache.wicket.protocol.ws.api.WebSocketRequestHandler;
import org.apache.wicket.protocol.ws.api.message.ClosedMessage;
import org.apache.wicket.util.time.Duration;

public class HomePage extends WebPage {
    private static final long serialVersionUID = 1L;
    private final WebMarkupContainer valuePanel;
    private final Label wsMessage;
    private final Label qrCode;
    private final Label counterLabel;
    private String currentSessionId;
    private int totalTime = 300; // 5 minutes
    // Store timer behavior to stop it
    private AbstractAjaxTimerBehavior timerBehavior;

    public HomePage() {
        valuePanel = new WebMarkupContainer("valuePanel");
        valuePanel.setOutputMarkupPlaceholderTag(true);
        
        qrCode = new Label("qrCode", Model.of("<img src='qr.png' alt='QR Code'/>"));
        qrCode.setEscapeModelStrings(false);
        qrCode.setOutputMarkupId(true);
        
        wsMessage = new Label("wsMessage", Model.of("Please scan the QR code to pay"));
        wsMessage.setOutputMarkupId(true);
        wsMessage.setOutputMarkupPlaceholderTag(true);
        
        // Calculate remaining time based on session start timestamp
        Long startTimestamp = (Long) getSession().getAttribute("startTimestamp");
        if(startTimestamp == null) {
            startTimestamp = System.currentTimeMillis();
            getSession().setAttribute("startTimestamp", startTimestamp);
        }
        int elapsed = (int)((System.currentTimeMillis() - startTimestamp) / 1000);
        int remaining = Math.max(totalTime - elapsed, 0);
        counterLabel = new Label("counter", Model.of(formatTime(remaining)));
        counterLabel.setOutputMarkupId(true);
        counterLabel.setOutputMarkupPlaceholderTag(true); // Add this line
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        valuePanel.setOutputMarkupPlaceholderTag(true);
        valuePanel.add(qrCode);
        valuePanel.add(wsMessage);
        valuePanel.add(counterLabel);
        add(valuePanel);
        // update counter each second
        timerBehavior = new AbstractAjaxTimerBehavior(Duration.seconds(1)) {
            @Override
            protected void onTimer(AjaxRequestTarget target) {
                Long startTimestamp = (Long) getSession().getAttribute("startTimestamp");
                int elapsed = (int)((System.currentTimeMillis() - startTimestamp) / 1000);
                int remaining = Math.max(totalTime - elapsed, 0);
                counterLabel.setDefaultModelObject(formatTime(remaining));
                target.add(counterLabel);
                if (remaining == 0) {
                    DatabaseManager.getInstance().updateSessionValue(currentSessionId, -1);
                    stop(target);
                }
            }
        };
        counterLabel.add(timerBehavior);
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
                    DatabaseManager.getInstance().createNewSessionRecord(currentSessionId);
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
                        DatabaseManager.getInstance().stopSessionPolling(currentSessionId);
                    } else if ("-1".equals(text)) {
                        wsMessage.setDefaultModelObject("Session timed out");
                        DatabaseManager.getInstance().stopSessionPolling(currentSessionId);
                    } else {
                        wsMessage.setDefaultModelObject("Please scan the QR code to pay");
                    }
                    handler.add(wsMessage);
                } catch (Exception ex) {
                    System.err.println("Error handling WebSocket message: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
            @Override
            protected void onClose(ClosedMessage message) {
                super.onClose(message);
                DatabaseManager.getInstance().stopSessionPolling(currentSessionId);
            }
        });
    }

    private String formatTime(int seconds) {
        int minutes = seconds / 60;
        int remSeconds = seconds % 60;
        return String.format("%02d:%02d", minutes, remSeconds);
    }

    private void updatePaymentStatus(String sessionId, String status) {
        DatabaseManager.getInstance().updateSessionValue(sessionId, 1);
    }
}
