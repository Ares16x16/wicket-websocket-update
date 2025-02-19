package demo;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.model.Model;
import org.apache.wicket.validation.validator.RangeValidator;

public class InputPage extends WebPage {
    private static final long serialVersionUID = 1L;

    public InputPage() {
        TextField<Integer> input = new TextField<>("input", Model.of(0), Integer.class);
        input.setRequired(true);
        input.add(RangeValidator.minimum(0));
        
        Form<Integer> form = new Form<>("form") {
            private static final long serialVersionUID = 1L;
            
            @Override
            protected void onSubmit() {
                Integer value = input.getModelObject();
                if (value != null) {
                    DatabaseManager.addNewValue(value);
                }
            }
        };
        
        form.add(input);
        add(form);
    }
}
