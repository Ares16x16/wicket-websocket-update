package demo;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.protocol.ws.api.WebSocketBehavior;
import org.apache.wicket.protocol.ws.api.message.TextMessage;
import org.apache.wicket.protocol.ws.api.WebSocketRequestHandler;

public class SimulatePaymentPage extends WebPage {
    private static final long serialVersionUID = 1L;

    public SimulatePaymentPage() {
        add(new FeedbackPanel("feedback"));
        
        Form<?> form = new Form<Void>("form");
        add(form);

        Button simulateButton = new Button("simulateButton") {
            @Override
            public void onSubmit() {
                super.onSubmit();
                sendMessageToWebSocket("1");
            }
        };
        form.add(simulateButton);
    }

    private void sendMessageToWebSocket(String message) {
        add(new WebSocketBehavior() {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onMessage(WebSocketRequestHandler handler, TextMessage message) {
                handler.push((CharSequence) message);
            }
        });
    }
}
