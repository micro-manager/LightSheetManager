package org.micromanager.lightsheetmanager.gui.tabs;

import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.api.data.CameraMode;
import org.micromanager.lightsheetmanager.gui.components.ListeningPanel;
import org.micromanager.lightsheetmanager.gui.components.Panel;
import org.micromanager.lightsheetmanager.gui.tabs.setup.SetupPanel;

import javax.swing.JLabel;
import java.awt.Font;
import java.util.Objects;

public class SetupPathTab extends Panel implements ListeningPanel {

    private final int pathNum_;
    private SetupPanel setupPanel_;

    private final LightSheetManager model_;

    public SetupPathTab(final LightSheetManager model, final int pathNum) {
        model_ = Objects.requireNonNull(model);
        pathNum_ = pathNum;
        createUserInterface();
    }

    private void createUserInterface() {
        final String title = (model_.devices().adapter().numImagingPaths() > 1)
                ? ("Setup Path " + pathNum_) : "Setup Path";
        final JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));

        setupPanel_ = new SetupPanel(model_, pathNum_);

        add(lblTitle, "wrap");
        add(setupPanel_, "wrap");
    }

    public void swapPanels(final CameraMode cameraMode) {
        setupPanel_.getLightSheetPanel().swapPanels(cameraMode);
    }

    @Override
    public void selected() {
        setupPanel_.selected();
    }

    @Override
    public void unselected() {

    }
}
