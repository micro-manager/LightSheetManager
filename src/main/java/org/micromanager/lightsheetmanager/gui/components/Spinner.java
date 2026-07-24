package org.micromanager.lightsheetmanager.gui.components;

import javax.swing.JFormattedTextField;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

// TODO: use Math.max(min, Math.min(start, max)) to modify to start value,
//   what if this invalidates and changes saved acquisition settings?

public class Spinner extends JSpinner {

    private static final int DEFAULT_COLUMNS = 5;

    private Spinner(final Integer start, final Integer min, final Integer max, final Integer step) {
        super(new SpinnerNumberModel(start, min, max, step));
        setColumnSize(DEFAULT_COLUMNS);
    }

    private Spinner(final Double start, final Double min, final Double max, final Double step) {
        super(new SpinnerNumberModel(start, min, max, step));
        setColumnSize(DEFAULT_COLUMNS);
    }

    public static Spinner createIntegerSpinner(
            final Integer start,
            final Integer min,
            final Integer max,
            final Integer step) {
        return new Spinner(Math.max(min, Math.min(start, max)), min, max, step);
    }

    public static Spinner createDoubleSpinner(
            final Double start,
            final Double min,
            final Double max,
            final Double step) {
        return new Spinner(Math.max(min, Math.min(start, max)), min, max, step);
    }

    public static Spinner createIntegerSpinner(
            final Integer start,
            final Integer min,
            final Integer max,
            final Integer step,
            final int columns) {
        final Spinner spinner = createIntegerSpinner(start, min, max, step);
        spinner.setColumnSize(columns);
        return spinner;
    }

    public static Spinner createDoubleSpinner(
            final Double start,
            final Double min,
            final Double max,
            final Double step,
            final int columns) {
        final Spinner spinner = createDoubleSpinner(start, min, max, step);
        spinner.setColumnSize(columns);
        return spinner;
    }

    public void setColumnSize(final int width) {
        final JFormattedTextField textField = ((NumberEditor)getEditor()).getTextField();
        textField.setColumns(width);
    }

    public int getInt() {
        return (Integer)getValue();
    }

    public double getDouble() {
        return (Double)getValue();
    }

    public void setInt(final int n) {
        setValue(n);
    }

    public void setDouble(final double n) {
        setValue(n);
    }

    public void registerListener(final Runnable listener) {
        addChangeListener(e -> listener.run());
    }
}
