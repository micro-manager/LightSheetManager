package org.micromanager.lightsheetmanager.gui.components;

import java.awt.Dimension;

import javax.swing.Icon;
import javax.swing.JButton;

public class Button extends JButton {

    public Button(final Icon icon, final int width, final int height) {
        super(icon);
        setAbsoluteSize(width, height);
        setFocusPainted(false); // remove highlight when clicked
    }

    public Button(final String text, final int width, final int height) {
        super(text);
        setAbsoluteSize(width, height);
        setFocusPainted(false); // remove highlight when clicked
    }

    public void setAbsoluteSize(final int width, final int height) {
        final Dimension size = new Dimension(width, height);
        setPreferredSize(size);
        setMinimumSize(size);
        setMaximumSize(size);
    }

    public void registerListener(final Runnable listener) {
        addActionListener(e -> listener.run());
    }
}
