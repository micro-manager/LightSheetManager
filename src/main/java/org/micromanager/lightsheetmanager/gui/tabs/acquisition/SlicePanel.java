package org.micromanager.lightsheetmanager.gui.tabs.acquisition;

import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.api.AcquisitionSettings;
import org.micromanager.lightsheetmanager.api.SliceSettings;
import org.micromanager.lightsheetmanager.api.data.CameraMode;
import org.micromanager.lightsheetmanager.api.internal.ScapeAcquisitionSettings;
import org.micromanager.lightsheetmanager.gui.components.CheckBox;
import org.micromanager.lightsheetmanager.gui.components.Panel;
import org.micromanager.lightsheetmanager.gui.components.SettingsListener;
import org.micromanager.lightsheetmanager.gui.components.Spinner;

import javax.swing.JLabel;
import java.util.Objects;

// TODO: make a separate panel for diSPIM?
public class SlicePanel extends Panel implements SettingsListener {

    // regular panel
    private CheckBox cbxMinimizePeriod_;
    private JLabel lblSlicePeriod_;
    private JLabel lblSampleExposure_;
    private Spinner spnSlicePeriod_;
    private Spinner spnSampleExposure_;

    // virtual slit panel
    private JLabel lblScanResetTime_;
    private JLabel lblScanSettleTime_;
    private JLabel lblShutterWidth_;
    private JLabel lblShutterSpeed_;
    private Spinner spnScanResetTime_;
    private Spinner spnScanSettleTime_;
    private Spinner spnShutterWidth_;
    private Spinner spnShutterSpeed_;

    private final LightSheetManager model_;

    public SlicePanel(final LightSheetManager model) {
        super("Slice Settings");
        model_ = Objects.requireNonNull(model);
        createUserInterface();
        createEventHandlers();
        model.userSettings().addChangeListener(this);
    }

    private void createUserInterface() {
        setMigLayout(
                "insets 10 10 10 10, fillx",
                "[grow, left] 10 [right]",
                "[]5[]"
        );

        final SliceSettings sliceSettings = model_.acquisitions().settings().slice();
        final boolean periodMinimized = sliceSettings.periodMinimized();

        // regular panel
        lblSlicePeriod_ = new JLabel("Slice period [ms]:");
        lblSampleExposure_ = new JLabel("Sample exposure [ms]:");
        cbxMinimizePeriod_ = new CheckBox(
                "Minimize slice period", 12, periodMinimized, CheckBox.RIGHT);
        spnSlicePeriod_ = Spinner.createDoubleSpinner(
                sliceSettings.period(), 0.0, Double.MAX_VALUE, 0.25);
        spnSampleExposure_ = Spinner.createDoubleSpinner(
                sliceSettings.sampleExposure(), 0.0, Double.MAX_VALUE, 0.25);

        setSpinnerEnabled(!periodMinimized);

        // TODO: this should added back in for diSPIM
//        if (model_.devices().adapter().geometry() == GeometryType.DISPIM) {
//            final DefaultSliceSettingsLS sliceSettingsLS = model_.acquisitions().settings().sliceLS();
//
//            // virtual slit panel
//            lblScanResetTime_ = new JLabel("Scan Reset Time [ms]:");
//            lblScanSettleTime_ = new JLabel("Scan Settle Time [ms]:");
//            lblShutterWidth_ = new JLabel("Shutter Width [µs]:");
//            lblShutterSpeed_ = new JLabel("1 / (shutter speed):");
//            spnScanResetTime_ = Spinner.createDoubleSpinner(
//                    sliceSettingsLS.scanResetTime(), 1.0, 100.0, 0.25);
//            spnScanSettleTime_ = Spinner.createDoubleSpinner(
//                    sliceSettingsLS.scanSettleTime(), 0.25, 100.0, 0.25);
//            spnShutterWidth_ = Spinner.createDoubleSpinner(
//                    sliceSettingsLS.shutterWidth(), 0.1, 100.0, 1.0);
//            spnShutterSpeed_ = Spinner.createDoubleSpinner(
//                    sliceSettingsLS.shutterSpeedFactor(), 1.0, 10.0, 1.0);
//        }

        // create the ui based on the camera trigger mode
        switchDisplayPanel(model_.acquisitions().settings().cameraMode());
    }

    /**
     * Setup event handlers for the regular and virtual slit camera trigger mode versions of the ui.
     */
    private void createEventHandlers() {

        // regular panel
        cbxMinimizePeriod_.registerListener(() -> {
            final boolean selected = cbxMinimizePeriod_.isSelected();
            lblSlicePeriod_.setEnabled(!selected);
            spnSlicePeriod_.setEnabled(!selected);
            model_.acquisitions().settingsBuilder()
                    .sliceBuilder().periodMinimized(selected);
            // update slice timing and duration labels
            model_.acquisitions().updateSettings();
            model_.acquisitions().updateDurationLabels();
        });

        spnSlicePeriod_.registerListener(() -> {
            model_.acquisitions().settingsBuilder()
                    .sliceBuilder().period(spnSlicePeriod_.getDouble());
            // update slice timing and duration labels
            model_.acquisitions().updateSettings();
            model_.acquisitions().updateDurationLabels();
        });

        spnSampleExposure_.registerListener(() -> {
            model_.acquisitions().settingsBuilder()
                    .sliceBuilder().sampleExposure(spnSampleExposure_.getDouble());
            // update slice timing and duration labels
            model_.acquisitions().updateSettings();
            model_.acquisitions().updateDurationLabels();
        });

//        if (model_.devices().adapter().geometry() == GeometryType.DISPIM) {
//
//            // virtual slit panel
//            spnScanResetTime_.registerListener(() -> {
//                model_.acquisitions().settingsBuilder()
//                        .sliceLSBuilder().scanResetTime(spnScanResetTime_.getDouble());
//            });
//
//            spnScanSettleTime_.registerListener(() -> {
//                model_.acquisitions().settingsBuilder()
//                        .sliceLSBuilder().scanSettleTime(spnScanSettleTime_.getDouble());
//            });
//
//            spnShutterWidth_.registerListener(() -> {
//                model_.acquisitions().settingsBuilder()
//                        .sliceLSBuilder().shutterWidth(spnShutterWidth_.getDouble());
//            });
//
//            spnShutterSpeed_.registerListener(() -> {
//                model_.acquisitions().settingsBuilder()
//                        .sliceLSBuilder().shutterSpeedFactor(spnShutterSpeed_.getDouble());
//            });
//        }
    }

    /**
     * Switches the displayed ui based on the camera trigger mode.
     *
     * @param cameraMode the current camera trigger mode
     */
    private void switchDisplayPanel(final CameraMode cameraMode) {
        removeAll();
        if (cameraMode != CameraMode.VIRTUAL_SLIT) {
            add(cbxMinimizePeriod_, "wrap");
            add(lblSlicePeriod_, "");
            add(spnSlicePeriod_, "wrap");
            add(lblSampleExposure_, "");
            add(spnSampleExposure_, "wrap");
        } else {
            add(lblScanResetTime_, "");
            add(spnScanResetTime_, "wrap");
            add(lblScanSettleTime_, "");
            add(spnScanSettleTime_, "wrap");
            add(lblShutterWidth_, "");
            add(spnShutterWidth_, "wrap");
            add(lblShutterSpeed_, "");
            add(spnShutterSpeed_, "wrap");
        }
        revalidate();
        repaint();
    }

    public void setPanelEnabled(final boolean state) {
        cbxMinimizePeriod_.setEnabled(state);
        if (!cbxMinimizePeriod_.isSelected()) {
            lblSlicePeriod_.setEnabled(state);
            spnSlicePeriod_.setEnabled(state);
        }
        lblSampleExposure_.setEnabled(state);
        spnSampleExposure_.setEnabled(state);
    }

    /**
     * Sets the enabled status of the label and spinner.
     * Used when the user clicks "Minimize Slice Period"
     *
     * @param state true to disable the spinner
     */
    private void setSpinnerEnabled(final boolean state) {
        lblSlicePeriod_.setEnabled(state);
        spnSlicePeriod_.setEnabled(state);
    }

    @Override
    public void onSettingsChanged(final AcquisitionSettings settings) {
        // TODO: add dispim part
        if (settings instanceof ScapeAcquisitionSettings) {
            var settingsScape = (ScapeAcquisitionSettings) settings;
            spnSlicePeriod_.setDouble(settingsScape.slice().period());
            spnSampleExposure_.setDouble(settingsScape.slice().sampleExposure());
            final boolean periodMinimized = settingsScape.slice().periodMinimized();
            cbxMinimizePeriod_.setSelected(periodMinimized);
            setSpinnerEnabled(!periodMinimized);
        }
    }
}
