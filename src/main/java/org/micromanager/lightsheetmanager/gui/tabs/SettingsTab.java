package org.micromanager.lightsheetmanager.gui.tabs;

import org.micromanager.lightsheetmanager.api.data.GeometryType;
import org.micromanager.lightsheetmanager.api.internal.ScapeAcquisitionSettings;
import org.micromanager.lightsheetmanager.gui.components.Button;
import org.micromanager.lightsheetmanager.gui.components.CheckBox;
import org.micromanager.lightsheetmanager.gui.components.ListeningPanel;
import org.micromanager.lightsheetmanager.gui.components.Panel;
import org.micromanager.lightsheetmanager.gui.components.Spinner;
import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.model.devices.vendor.ASIScanner;

import javax.swing.JLabel;
import java.util.Objects;

public class SettingsTab extends Panel implements ListeningPanel {

    // scan settings
    private Spinner spnScanAcceleration_;
    private Spinner spnScanOvershootDist_;
    private Spinner spnScanRetraceSpeed_;
    private Spinner spnScanFirstViewAngle_;
    private CheckBox cbxScanFromCurrentPosition_;
    private CheckBox cbxScanNegativeDirection_;
    private CheckBox cbxReturnToStart_;

    // light sheet scanner settings
    private Spinner spnSheetAxisFilterFreq_;
    private Spinner spnSliceAxisFilterFreq_;
    private Spinner spnLiveScanPeriod_;

    private Button btnCreateConfigGroup_;

    private CheckBox cbxAcquireFailQuietly_;

    // changes the ui setup
    private boolean isUsingPLogic_;
    private boolean isUsingScanSettings_;

    private final LightSheetManager model_;

    public SettingsTab(final LightSheetManager model) {
        model_ = Objects.requireNonNull(model);
        createUserInterface();
        createEventHandlers();
    }

    private void createUserInterface() {
        final ScapeAcquisitionSettings settings = model_.acquisitions().settings();

        // check for devices to set up the tab
        isUsingPLogic_ = model_.devices().isUsingPLogic();
        isUsingScanSettings_ = model_.devices().hasStageScanning();

        btnCreateConfigGroup_ = new Button("Create Devices Configuration Group", 220, 30);
        btnCreateConfigGroup_.setToolTipText("Creates or updates the \"System::Startup\" configuration " +
                "group with all editable properties from the Light Sheet Manager device adapter.");

        final JLabel lblGeometryType = new JLabel("Microscope Geometry: "
                + model_.devices().adapter().geometry());
        final JLabel lblLightSheetType = new JLabel("Light Sheet Type: "
                + model_.devices().adapter().lightSheetType());
        final JLabel lblNumImagingPaths = new JLabel("Imaging Paths: "
                + model_.devices().adapter().numImagingPaths());
        final JLabel lblNumIlluminationPaths = new JLabel("Illumination Paths: "
                + model_.devices().adapter().numIlluminationPaths());
        final JLabel lblNumSimultaneousCameras = new JLabel("Simultaneous Cameras: "
                + model_.devices().adapter().numSimultaneousCameras());

        final Panel pnlScanSettings = new Panel("Stage Scan Settings");
        pnlScanSettings.setMigLayout(
                "",
                "[]5[]",
                "[]5[]");

        final JLabel lblScanAcceleration = new JLabel("Relative acceleration time:");
        final JLabel lblScanOvershootDist = new JLabel("Scan overshoot distance [µm]:");
        final JLabel lblScanRetraceSpeed = new JLabel("Scan retrace speed [% of max]:");
        final JLabel lblScanAngleFirstView = new JLabel("Path A stage/objective angle [°]:");

        // Scan Spinners
        spnScanAcceleration_ = Spinner.createDoubleSpinner(
                settings.stageScan().accelerationFactor(),
                0.1, 1000.0, 1.0);

        spnScanOvershootDist_ = Spinner.createIntegerSpinner(
                settings.stageScan().overshootDistance(),
                0, 1000, 10);

        spnScanRetraceSpeed_ = Spinner.createDoubleSpinner(
                settings.stageScan().retraceSpeed(),
                0.01, 99.0, 1.0);

        spnScanFirstViewAngle_ = Spinner.createDoubleSpinner(
                settings.stageScan().firstViewAngle(),
                1.0, 89.0, 0.1);

        // Scan CheckBoxes
        cbxScanFromCurrentPosition_ = new CheckBox("Scan from current position instead of center",
                settings.stageScan().fromCurrentPosition());
        cbxScanNegativeDirection_ = new CheckBox("Scan negative direction",
                settings.stageScan().fromNegativeDirection());
        cbxReturnToStart_ = new CheckBox("Return to original position after scan",
                settings.stageScan().returnToStart());

        final Panel pnlLightSheet = new Panel("Light Sheet Scanner");
        pnlLightSheet.setMigLayout(
                "",
                "[right]16[center]",
                "[]4[]"
        );

        final JLabel lblSheetAxisFilterFreq = new JLabel("Filter freq, sheet axis [kHz]:");
        final JLabel lblSliceAxisFilterFreq = new JLabel("Filter freq, slice axis [kHz]:");
        final JLabel lblLiveScanPeriod = new JLabel("Live scan period [ms]:");

        // TODO: increase max filter freq based on build (to 10.0)
        spnSheetAxisFilterFreq_ = Spinner.createDoubleSpinner(0.4, 0.1, 1.0, 0.1);
        spnSliceAxisFilterFreq_ = Spinner.createDoubleSpinner(0.4, 0.1, 1.0, 0.1);
        spnLiveScanPeriod_ = Spinner.createIntegerSpinner(20, 2, 10000, 100);

        add(lblGeometryType, "wrap");
        add(lblLightSheetType, "wrap");
        add(lblNumImagingPaths, "wrap");
        add(lblNumIlluminationPaths, "wrap");
        add(lblNumSimultaneousCameras, "wrap");

        // scan settings panel
        if (isUsingScanSettings_) {
            pnlScanSettings.add(lblScanAcceleration, "");
            pnlScanSettings.add(spnScanAcceleration_, "wrap");
            pnlScanSettings.add(lblScanOvershootDist, "");
            pnlScanSettings.add(spnScanOvershootDist_, "wrap");
            pnlScanSettings.add(lblScanRetraceSpeed, "");
            pnlScanSettings.add(spnScanRetraceSpeed_, "wrap");
        }

        // the grid slice step size is derived from the angle as well, so it stays
        // available even when the stage cannot scan
        pnlScanSettings.add(lblScanAngleFirstView, "");
        pnlScanSettings.add(spnScanFirstViewAngle_, "wrap");

        if (isUsingScanSettings_) {
            pnlScanSettings.add(cbxScanFromCurrentPosition_, "span 2, wrap");
            pnlScanSettings.add(cbxScanNegativeDirection_, "span 2, wrap");
            pnlScanSettings.add(cbxReturnToStart_, "span 2, wrap");
        } else {
            final String reason = model_.devices().hasDevice("SampleXY")
                    ? "Stage scanning not supported by your firmware."
                    : "Stage scanning needs SampleXY to be set.";
            pnlScanSettings.add(new JLabel(reason), "span 2");
        }

        // light sheet scanner settings panel
        if (isUsingPLogic_) {
            pnlLightSheet.add(lblSheetAxisFilterFreq, "");
            pnlLightSheet.add(spnSheetAxisFilterFreq_, "wrap");
            pnlLightSheet.add(lblSliceAxisFilterFreq, "");
            pnlLightSheet.add(spnSliceAxisFilterFreq_, "wrap");
        }

        // changes scan rate for live mode viewing
        if (model_.devices().adapter().geometry() == GeometryType.DISPIM) {
            pnlLightSheet.add(lblLiveScanPeriod, "");
            pnlLightSheet.add(spnLiveScanPeriod_, "");
        }

        cbxAcquireFailQuietly_ = new CheckBox("Acquisition failures are quiet",
                model_.pluginSettings().isAcquireFailQuietly());
        cbxAcquireFailQuietly_.setToolTipText("Log acquisition errors instead of showing dialog " +
                "boxes, so unattended runs (playlist or scripting) can never hang on an error dialog.");

        add(pnlScanSettings, "wrap");
        if (isUsingPLogic_) {
            add(pnlLightSheet, "growx, wrap");
        }

        add(cbxAcquireFailQuietly_, "wrap");
        add(btnCreateConfigGroup_, "");
    }

    private void createEventHandlers() {

        // Scan Settings
        if (isUsingScanSettings_) {
            spnScanAcceleration_.registerListener(
                    () -> model_.acquisitions().settingsBuilder().stageScanBuilder()
                            .accelerationFactor(spnScanAcceleration_.getDouble()));
            spnScanOvershootDist_.registerListener(
                    () -> model_.acquisitions().settingsBuilder().stageScanBuilder()
                            .overshootDistance(spnScanOvershootDist_.getInt()));
            spnScanRetraceSpeed_.registerListener(
                    () -> model_.acquisitions().settingsBuilder().stageScanBuilder()
                            .retraceSpeed(spnScanRetraceSpeed_.getDouble()));

            cbxScanFromCurrentPosition_.registerListener(
                    () -> model_.acquisitions().settingsBuilder().stageScanBuilder()
                            .fromCurrentPosition(cbxScanFromCurrentPosition_.isSelected()));
            cbxScanNegativeDirection_.registerListener(
                    () -> model_.acquisitions().settingsBuilder().stageScanBuilder()
                            .fromNegativeDirection(cbxScanNegativeDirection_.isSelected()));
            cbxReturnToStart_.registerListener(
                    () -> model_.acquisitions().settingsBuilder().stageScanBuilder()
                            .returnToStart(cbxReturnToStart_.isSelected()));
        }

        // registered outside the block above because the angle spinner is always shown
        spnScanFirstViewAngle_.registerListener(
                () -> model_.acquisitions().settingsBuilder().stageScanBuilder()
                        .firstViewAngle(spnScanFirstViewAngle_.getDouble()));

        // ASIScanner Filter Freq
        if (isUsingPLogic_) {
            final ASIScanner scanner = model_.devices().device("IllumSlice");

            spnSheetAxisFilterFreq_.registerListener(
                    () -> scanner.setFilterFreqX(spnSheetAxisFilterFreq_.getDouble()));

            spnSliceAxisFilterFreq_.registerListener(
                    () -> scanner.setFilterFreqY(spnSliceAxisFilterFreq_.getDouble()));
        }

        cbxAcquireFailQuietly_.registerListener(() -> model_.pluginSettings()
                .setAcquireFailQuietly(cbxAcquireFailQuietly_.isSelected()));

        btnCreateConfigGroup_.registerListener(() -> model_.devices().createConfigGroup());

        // TODO: make this work with diSPIM settings
//        spnLiveScanPeriod_.registerListener(
//                e -> model_.acquisitions().settingsBuilder()
//                        .liveScanPeriod(spnLiveScanPeriod_.getDouble()));

    }

    @Override
    public void selected() {

    }

    @Override
    public void unselected() {

    }
}
