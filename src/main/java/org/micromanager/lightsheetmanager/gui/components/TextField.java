package org.micromanager.lightsheetmanager.gui.components;

import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.Color;
import java.util.function.Consumer;
import java.util.function.Function;

public class TextField extends JTextField {

    private static final Color ERROR_COLOR = new Color(110, 25, 25);

    private final Color defaultColor_;

    public TextField() {
        setColumns(5);
        defaultColor_ = getBackground();
    }

    public TextField(final int size) {
        setColumns(size);
        defaultColor_ = getBackground();
    }

    public TextField(final String text, final int size) {
        super(text);
        setColumns(size);
        defaultColor_ = getBackground();
    }

    public void registerListener(final Runnable listener) {
        // fires when enter is pressed
        addActionListener(e -> listener.run());
    }

    /**
     * Registers a listener that checks the current text whenever the document changes,
     * and shows the error color while the text has a problem.
     *
     * @param problemOf returns why the text is invalid, or null when it is valid
     * @param listener receives the problem, or null when the current text is valid
     */
    public void registerValidationListener(final Function<String, String> problemOf,
                                           final Consumer<String> listener) {
        final Runnable validate = () -> {
            final String problem = problemOf.apply(getText());
            setBackground(problem == null ? defaultColor_ : ERROR_COLOR);
            listener.accept(problem);
        };

        final DocumentListener docListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                validate.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                validate.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                validate.run();
            }
        };

        getDocument().addDocumentListener(docListener);
    }

    /**
     * Marks the field valid or invalid by setting its background color.
     * <p>
     * The caller decides what valid means and owns any tooltip explaining it.
     *
     * @param isValid {@code false} to show the error color
     */
    public void setValid(final boolean isValid) {
        setBackground(isValid ? defaultColor_ : ERROR_COLOR);
    }
}
