package demo;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.Model;

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
                simulatePayment();
            }
        };
        form.add(simulateButton);
    }

    private void simulatePayment() {
        String sessionId = getSession().getId();
        DatabaseManager.getInstance().updateSessionValue(sessionId, 1);
        info("Payment simulation successful for session: " + sessionId);
    }
}
