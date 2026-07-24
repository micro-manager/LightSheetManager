package org.micromanager.lightsheetmanager.gui.components;

import javax.swing.ImageIcon;
import javax.swing.JToggleButton;

import java.awt.Insets;
import java.awt.Dimension;

public class ToggleButton extends JToggleButton {

    private final String onText;
    private final String offText;
    private final ImageIcon onImage;
    private final ImageIcon offImage;

    public ToggleButton(final String offText, final String onText,
                        final ImageIcon offImage, final ImageIcon onImage,
                        final int width, final int height) {
        this.offText = offText;
        this.onText = onText;
        this.offImage = offImage;
        this.onImage = onImage;
        setSize(width, height);
        init();
    }

    private void init() {
        setState(false); // set the initial state
        setMargins(1, 1, 1, 1);
        setFocusPainted(false); // remove focus highlight
    }

    public void setState(boolean state) {
        setSelected(state);
        if (state) {
            setText(onText);
            setIcon(onImage);
        } else {
            setText(offText);
            setIcon(offImage);
        }
    }

    public void setMargins(int top, int left, int bottom, int right) {
        setMargin(new Insets(top, left, bottom, right));
    }

    @Override
    public void setSize(final int width, final int height) {
        final Dimension size = new Dimension(width, height);
        setPreferredSize(size);
        setMinimumSize(size);
        setMaximumSize(size);
    }

    public void registerListener(final Runnable listener) {
        addActionListener(event -> {
            setState(isSelected());
            listener.run();
        });
    }

}