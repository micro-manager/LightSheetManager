package org.micromanager.lightsheetmanager.gui.tabs.acquisition;

import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.gui.components.Panel;

import javax.swing.JLabel;
import java.util.Objects;

public class DurationPanel extends Panel {

    private JLabel lblSliceTimeValue_;
    private JLabel lblVolumeTimeValue_;
    private JLabel lblTotalTimeValue_;

    public DurationPanel(final LightSheetManager model) {
        super("Durations");
        Objects.requireNonNull(model)
              .acquisitions().setDurationPanel(this);
        createUserInterface();
    }

    private void createUserInterface() {
        // prevent panel from moving when values change
        setAbsoluteSize(120, 95);

        lblSliceTimeValue_ = new JLabel("0.0 ms");
        lblVolumeTimeValue_ = new JLabel("0.0 ms");
        lblTotalTimeValue_ = new JLabel("0.0 s");

        add(new JLabel("Slice:"), "");
        add(lblSliceTimeValue_, "wrap");
        add(new JLabel("Volume:"), "");
        add(lblVolumeTimeValue_, "wrap");
        add(new JLabel("Total:"), "");
        add(lblTotalTimeValue_, "");
    }

    public JLabel getSliceDurationLabel() {
        return lblSliceTimeValue_;
    }

    public JLabel getVolumeDurationLabel() {
        return lblVolumeTimeValue_;
    }

    public JLabel getTotalDurationLabel() {
        return lblTotalTimeValue_;
    }

}
