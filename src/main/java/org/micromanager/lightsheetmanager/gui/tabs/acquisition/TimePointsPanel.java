package org.micromanager.lightsheetmanager.gui.tabs.acquisition;

import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.api.AcquisitionSettings;
import org.micromanager.lightsheetmanager.api.internal.ScapeAcquisitionSettings;
import org.micromanager.lightsheetmanager.gui.components.CheckBox;
import org.micromanager.lightsheetmanager.gui.components.Panel;
import org.micromanager.lightsheetmanager.gui.components.SettingsListener;
import org.micromanager.lightsheetmanager.gui.components.Spinner;

import javax.swing.JLabel;
import java.util.Objects;

public class TimePointsPanel extends Panel implements SettingsListener {

    private JLabel lblNumTimePoints_;
    private JLabel lblTimePointInterval_;
    private Spinner spnNumTimePoints_;
    private Spinner spnTimePointInterval_;

    private final LightSheetManager model_;

    public TimePointsPanel(final LightSheetManager model, final CheckBox cbxUseTimePoints) {
        super(cbxUseTimePoints);
        model_ = Objects.requireNonNull(model);
        createUserInterface();
        createEventHandlers();
        model.userSettings().addChangeListener(this);
    }

    private void createUserInterface() {
        final ScapeAcquisitionSettings acqSettings =
                model_.acquisitions().settings();

        setMigLayout(
                "",
                "[]5[]",
                "[]5[]"
        );

        Spinner.setDefaultSize(6);
        lblNumTimePoints_ = new JLabel("Number:");
        lblTimePointInterval_ = new JLabel("Interval [s]:");
        spnNumTimePoints_ = Spinner.createIntegerSpinner(
                acqSettings.numTimePoints(), 1, Integer.MAX_VALUE,1);
        spnTimePointInterval_ = Spinner.createDoubleSpinner(
                acqSettings.timePointInterval(), 0.1, Double.MAX_VALUE, 0.1);

        add(lblNumTimePoints_, "");
        add(spnNumTimePoints_, "wrap");
        add(lblTimePointInterval_, "");
        add(spnTimePointInterval_, "");
    }

    // TODO: update duration labels
    private void createEventHandlers() {

        spnNumTimePoints_.registerListener(() -> {
            model_.acquisitions().settingsBuilder().numTimePoints(spnNumTimePoints_.getInt());
            model_.acquisitions().updateDurationLabels();
        });

        spnTimePointInterval_.registerListener(() -> {
            model_.acquisitions().settingsBuilder().timePointInterval(spnTimePointInterval_.getDouble());
            model_.acquisitions().updateDurationLabels();
        });
    }

    public void setPanelEnabled(final boolean state) {
        lblNumTimePoints_.setEnabled(state);
        lblTimePointInterval_.setEnabled(state);
        spnNumTimePoints_.setEnabled(state);
        spnTimePointInterval_.setEnabled(state);
    }

    @Override
    public void onSettingsChanged(final AcquisitionSettings settings) {
        // TODO: timepoints should probably be a part of the base acq settings
        if (settings instanceof ScapeAcquisitionSettings) {
            var settingsScape = (ScapeAcquisitionSettings) settings;
            spnNumTimePoints_.setValue(settingsScape.numTimePoints());
            spnTimePointInterval_.setValue(settingsScape.timePointInterval());
        }
    }
}
