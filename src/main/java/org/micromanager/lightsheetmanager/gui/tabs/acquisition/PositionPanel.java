package org.micromanager.lightsheetmanager.gui.tabs.acquisition;

import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.api.AcquisitionSettings;
import org.micromanager.lightsheetmanager.api.internal.ScapeAcquisitionSettings;
import org.micromanager.lightsheetmanager.gui.components.Button;
import org.micromanager.lightsheetmanager.gui.components.CheckBox;
import org.micromanager.lightsheetmanager.gui.components.Panel;
import org.micromanager.lightsheetmanager.gui.components.SettingsListener;
import org.micromanager.lightsheetmanager.gui.components.Spinner;
import org.micromanager.lightsheetmanager.gui.frames.XYZGridFrame;

import javax.swing.JLabel;
import java.util.Objects;

public class PositionPanel extends Panel implements SettingsListener {

    private JLabel lblPostMoveDelay_;
    private Spinner spnPostMoveDelay_;
    private Button btnOpenXYZGrid_;
    private Button btnEditPositionList_;

    private final XYZGridFrame xyzGridFrame_;
    private final LightSheetManager model_;

    public PositionPanel(final LightSheetManager model, final CheckBox checkBox) {
        super(checkBox);
        model_ = Objects.requireNonNull(model);
        xyzGridFrame_ = new XYZGridFrame(model_);
        createUserInterface();
        createEventHandlers();
        model.userSettings().addChangeListener(this);
    }

    private void createUserInterface() {

        // post move delay
        lblPostMoveDelay_ = new JLabel("Post-move delay [ms]:");
        Spinner.setDefaultSize(8);
        spnPostMoveDelay_ = Spinner.createIntegerSpinner(
                model_.acquisitions().settings().postMoveDelay(),
                0, Integer.MAX_VALUE, 100);

        // XYZ grid
        btnEditPositionList_ = new Button("Edit Position List...", 130, 24);
        btnOpenXYZGrid_ = new Button("XYZ Grid...", 90, 24);

        add(btnEditPositionList_, "");
        add(btnOpenXYZGrid_, "wrap");
        add(lblPostMoveDelay_, "");
        add(spnPostMoveDelay_, "");
    }

    private void createEventHandlers() {

        // open XYZ grid
        btnOpenXYZGrid_.registerListener(() -> {
            if (model_.devices().hasDevice("SampleXY")
                    && model_.devices().hasDevice("SampleZ")) {
                xyzGridFrame_.setVisible(true);
            } else {
                model_.studio().logs().showError(
                        "SampleXY and SampleZ must not be \"Undefined\" to use the XYZ grid.");
            }
        });

        // open position list
        btnEditPositionList_.registerListener(() -> model_.studio().app().showPositionList());

        spnPostMoveDelay_.registerListener(() -> model_.acquisitions()
                .settingsBuilder().postMoveDelay(spnPostMoveDelay_.getInt()));

    }

    public void setPanelEnabled(final boolean state) {
        lblPostMoveDelay_.setEnabled(state);
        spnPostMoveDelay_.setEnabled(state);
        btnEditPositionList_.setEnabled(state);
    }

    public XYZGridFrame getXYZGridFrame() {
        return xyzGridFrame_;
    }

    @Override
    public void onSettingsChanged(final AcquisitionSettings settings) {
        // TODO: multi positions should probably be a part of the base acq settings
        if (settings instanceof ScapeAcquisitionSettings) {
            var settingsScape = (ScapeAcquisitionSettings) settings;
            spnPostMoveDelay_.setValue(settingsScape.postMoveDelay());
        }
    }
}
