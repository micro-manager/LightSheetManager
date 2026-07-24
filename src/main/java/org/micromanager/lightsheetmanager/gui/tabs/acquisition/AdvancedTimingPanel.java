package org.micromanager.lightsheetmanager.gui.tabs.acquisition;

import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.api.AcquisitionSettings;
import org.micromanager.lightsheetmanager.api.TimingSettings;
import org.micromanager.lightsheetmanager.api.internal.ScapeAcquisitionSettings;
import org.micromanager.lightsheetmanager.gui.components.CheckBox;
import org.micromanager.lightsheetmanager.gui.components.Panel;
import org.micromanager.lightsheetmanager.gui.components.SettingsListener;
import org.micromanager.lightsheetmanager.gui.components.Spinner;
import org.micromanager.lightsheetmanager.model.utils.NumberUtils;

import javax.swing.JLabel;
import java.util.Objects;

public class AdvancedTimingPanel extends Panel implements SettingsListener {

    private JLabel lblDelayBeforeScan_;
    private JLabel lblScansPerSlice_;
    private JLabel lblScanDuration_;
    private JLabel lblDelayBeforeLaser_;
    private JLabel lblDelayBeforeCamera_;
    private JLabel lblLaserTriggerDuration_;
    private JLabel lblCameraTriggerDuration_;
    private JLabel lblCameraExposure_;

    private Spinner spnDelayBeforeScan_;
    private Spinner spnDelayBeforeLaser_;
    private Spinner spnDelayBeforeCamera_;
    private Spinner spnLaserTriggerDuration_;
    private Spinner spnCameraTriggerDuration_;
    private Spinner spnScanDuration_;
    private Spinner spnScansPerSlice_;
    private Spinner spnCameraExposure_;

    private CheckBox cbxAlternateScanDirection_;

    private final LightSheetManager model_;

    public AdvancedTimingPanel(final LightSheetManager model) {
        super("Advanced Timing Settings");
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

        lblDelayBeforeScan_ = new JLabel("Delay Before Scan [ms]: ");
        lblScansPerSlice_ = new JLabel("Scans Per Slice: ");
        lblScanDuration_ = new JLabel("Scan Duration [ms]: ");
        lblDelayBeforeLaser_ = new JLabel("Delay Before Laser [ms]: ");
        lblDelayBeforeCamera_ = new JLabel("Delay Before Camera [ms]: ");
        lblLaserTriggerDuration_ = new JLabel("Laser Trigger Duration [ms]: ");
        lblCameraTriggerDuration_ = new JLabel("Camera Trigger Duration [ms]: ");
        lblCameraExposure_ = new JLabel("Camera Exposure [ms]: ");

        final TimingSettings timingSettings = model_.acquisitions().settings().timing();

        spnDelayBeforeScan_ = Spinner.createDoubleSpinner(
                timingSettings.delayBeforeScan(), 0.0, 10000.0, 0.25);
        spnDelayBeforeLaser_ = Spinner.createDoubleSpinner(
                timingSettings.delayBeforeLaser(), 0.0, 10000.0, 0.25);
        spnDelayBeforeCamera_ = Spinner.createDoubleSpinner(
                timingSettings.delayBeforeCamera(), 0.0, 10000.0, 0.25);
        spnLaserTriggerDuration_ = Spinner.createDoubleSpinner(
                timingSettings.laserTriggerDuration(), 0.0, 10000.0, 0.25);
        spnCameraTriggerDuration_ = Spinner.createDoubleSpinner(
                timingSettings.cameraTriggerDuration(), 0.0, 1000.0, 0.25);
        spnScanDuration_ = Spinner.createDoubleSpinner(
                timingSettings.scanDuration(), 0.0, 10000.0, 0.25);
        spnScansPerSlice_ = Spinner.createIntegerSpinner(
                timingSettings.scansPerSlice(), 1, 1000, 1);
        spnCameraExposure_ = Spinner.createDoubleSpinner(
                timingSettings.cameraExposure(), 0.0, 1000.0, 0.25);

        cbxAlternateScanDirection_ = new CheckBox(
                "Alternate scan direction", false, CheckBox.LEFT);

        add(lblDelayBeforeScan_, "");
        add(spnDelayBeforeScan_, "wrap");
        add(lblScansPerSlice_, "");
        add(spnScansPerSlice_, "wrap");
        add(lblScanDuration_, "");
        add(spnScanDuration_, "wrap");
        add(lblDelayBeforeLaser_, "");
        add(spnDelayBeforeLaser_, "wrap");
        add(lblLaserTriggerDuration_, "");
        add(spnLaserTriggerDuration_, "wrap");
        add(lblDelayBeforeCamera_, "");
        add(spnDelayBeforeCamera_, "wrap");
        add(lblCameraTriggerDuration_, "");
        add(spnCameraTriggerDuration_, "wrap");
        add(lblCameraExposure_, "");
        add(spnCameraExposure_, "wrap");
        add(cbxAlternateScanDirection_, "");
    }

    private void createEventHandlers() {

        spnDelayBeforeScan_.registerListener(() -> {
            final double value = spnDelayBeforeScan_.getDouble();
            final double rounded = NumberUtils.roundToQuarterMs(value);
            if (Math.abs(value - rounded) > 1e-6) {
                spnDelayBeforeScan_.setDouble(rounded);
                return; // early exit => setDouble will handle model update
            }

            model_.acquisitions().settingsBuilder()
                    .timingBuilder().delayBeforeScan(rounded);
            model_.acquisitions().updateDurationLabels();
        });

        spnScansPerSlice_.registerListener(() -> {
            model_.acquisitions().settingsBuilder()
                    .timingBuilder().scansPerSlice(spnScansPerSlice_.getInt());
            model_.acquisitions().updateDurationLabels();
        });

        spnScanDuration_.registerListener(() -> {
            final double value = spnScanDuration_.getDouble();
            final double rounded = NumberUtils.roundToQuarterMs(value);
            if (Math.abs(value - rounded) > 1e-6) {
                spnScanDuration_.setDouble(rounded);
                return; // early exit => setDouble will handle model update
            }

            model_.acquisitions().settingsBuilder()
                    .timingBuilder().scanDuration(rounded);
            model_.acquisitions().updateDurationLabels();
        });

        spnDelayBeforeLaser_.registerListener(() -> {
            final double value = spnDelayBeforeLaser_.getDouble();
            final double rounded = NumberUtils.roundToQuarterMs(value);
            if (Math.abs(value - rounded) > 1e-6) {
                spnDelayBeforeLaser_.setDouble(rounded);
                return; // early exit => setDouble will handle model update
            }

            model_.acquisitions().settingsBuilder()
                    .timingBuilder().delayBeforeLaser(rounded);
            model_.acquisitions().updateDurationLabels();
        });

        spnLaserTriggerDuration_.registerListener(() -> {
            final double value = spnLaserTriggerDuration_.getDouble();
            final double rounded = NumberUtils.roundToQuarterMs(value);
            if (Math.abs(value - rounded) > 1e-6) {
                spnLaserTriggerDuration_.setDouble(rounded);
                return; // early exit => setDouble will handle model update
            }

            model_.acquisitions().settingsBuilder()
                    .timingBuilder().laserTriggerDuration(rounded);
            model_.acquisitions().updateDurationLabels();
        });

        spnDelayBeforeCamera_.registerListener(() -> {
            final double value = spnDelayBeforeCamera_.getDouble();
            final double rounded = NumberUtils.roundToQuarterMs(value);
            if (Math.abs(value - rounded) > 1e-6) {
                spnDelayBeforeCamera_.setDouble(rounded);
                return; // early exit => setDouble will handle model update
            }

            model_.acquisitions().settingsBuilder()
                    .timingBuilder().delayBeforeCamera(rounded);
            model_.acquisitions().updateDurationLabels();
        });

        spnCameraTriggerDuration_.registerListener(() -> {
            final double value = spnCameraTriggerDuration_.getDouble();
            final double rounded = NumberUtils.roundToQuarterMs(value);
            if (Math.abs(value - rounded) > 1e-6) {
                spnCameraTriggerDuration_.setDouble(rounded);
                return; // early exit => setDouble will handle model update
            }

            model_.acquisitions().settingsBuilder()
                    .timingBuilder().cameraTriggerDuration(rounded);
            model_.acquisitions().updateDurationLabels();
        });

        spnCameraExposure_.registerListener(() -> {
            model_.acquisitions().settingsBuilder()
                    .timingBuilder().cameraExposure(spnCameraExposure_.getDouble());
            model_.acquisitions().updateDurationLabels();
        });

        cbxAlternateScanDirection_.registerListener(() -> {
            model_.acquisitions().settingsBuilder()
                    .timingBuilder().useAlternateScanDirection(cbxAlternateScanDirection_.isSelected());
        });
    }

    /**
     * Set the enabled state of the entire panel.
     *
     * @param state true to set to enabled
     */
    public void setPanelEnabled(final boolean state) {
        // labels
        lblDelayBeforeScan_.setEnabled(state);
        lblScansPerSlice_.setEnabled(state);
        lblScanDuration_.setEnabled(state);
        lblDelayBeforeLaser_.setEnabled(state);
        lblDelayBeforeCamera_.setEnabled(state);
        lblLaserTriggerDuration_.setEnabled(state);
        lblCameraTriggerDuration_.setEnabled(state);
        lblCameraExposure_.setEnabled(state);
        // spinners
        spnDelayBeforeScan_.setEnabled(state);
        spnScansPerSlice_.setEnabled(state);
        spnScanDuration_.setEnabled(state);
        spnDelayBeforeLaser_.setEnabled(state);
        spnLaserTriggerDuration_.setEnabled(state);
        spnDelayBeforeCamera_.setEnabled(state);
        spnCameraTriggerDuration_.setEnabled(state);
        spnCameraExposure_.setEnabled(state);
        cbxAlternateScanDirection_.setEnabled(state);
    }

    /**
     * Updates the spinner values from the timing settings builder.
     */
    public void updateSpinners() {
        final TimingSettings timingSettings = model_.acquisitions()
                .settingsBuilder().timingBuilder().build();
        spnDelayBeforeScan_.setDouble(timingSettings.delayBeforeScan());
        spnScansPerSlice_.setInt(timingSettings.scansPerSlice());
        spnScanDuration_.setDouble(timingSettings.scanDuration());
        spnDelayBeforeLaser_.setDouble(timingSettings.delayBeforeLaser());
        spnLaserTriggerDuration_.setDouble(timingSettings.laserTriggerDuration());
        spnDelayBeforeCamera_.setDouble(timingSettings.delayBeforeCamera());
        spnCameraTriggerDuration_.setDouble(timingSettings.cameraTriggerDuration());
        spnCameraExposure_.setDouble(timingSettings.cameraExposure());
        cbxAlternateScanDirection_.setSelected(timingSettings.useAlternateScanDirection());
    }

    @Override
    public void onSettingsChanged(final AcquisitionSettings settings) {
        if (settings instanceof ScapeAcquisitionSettings) {
            var settingsScape = (ScapeAcquisitionSettings) settings;
            spnDelayBeforeScan_.setValue(settingsScape.timing().delayBeforeScan());
            spnScansPerSlice_.setValue(settingsScape.timing().scansPerSlice());
            spnScanDuration_.setValue(settingsScape.timing().scanDuration());
            spnDelayBeforeLaser_.setValue(settingsScape.timing().delayBeforeLaser());
            spnLaserTriggerDuration_.setValue(settingsScape.timing().laserTriggerDuration());
            spnDelayBeforeCamera_.setValue(settingsScape.timing().cameraTriggerDuration());
            spnLaserTriggerDuration_.setValue(settingsScape.timing().cameraTriggerDuration());
            spnDelayBeforeCamera_.setDouble(settingsScape.timing().cameraTriggerDuration());
            spnCameraTriggerDuration_.setDouble(settingsScape.timing().cameraTriggerDuration());
            spnCameraExposure_.setValue(settingsScape.timing().cameraExposure());
            cbxAlternateScanDirection_.setSelected(settingsScape.timing().useAlternateScanDirection());
        }
    }
}
