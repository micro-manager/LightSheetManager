package org.micromanager.lightsheetmanager.gui.tabs;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.Future;
import javax.swing.SwingUtilities;
import org.micromanager.lightsheetmanager.LightSheetManagerFrame;
import org.micromanager.lightsheetmanager.api.AcquisitionSettings;
import org.micromanager.lightsheetmanager.api.internal.ScapeAcquisitionSettings;
import org.micromanager.lightsheetmanager.gui.components.ListeningPanel;
import org.micromanager.lightsheetmanager.gui.components.SettingsListener;
import org.micromanager.lightsheetmanager.gui.data.Icons;
import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.gui.tabs.acquisition.AdvancedTimingPanel;
import org.micromanager.lightsheetmanager.gui.tabs.acquisition.CameraPanel;
import org.micromanager.lightsheetmanager.gui.tabs.acquisition.PositionPanel;
import org.micromanager.lightsheetmanager.gui.tabs.acquisition.SavePanel;
import org.micromanager.lightsheetmanager.gui.tabs.acquisition.SlicePanel;
import org.micromanager.lightsheetmanager.gui.tabs.acquisition.TimePointsPanel;
import org.micromanager.lightsheetmanager.gui.tabs.acquisition.DurationPanel;
import org.micromanager.lightsheetmanager.gui.tabs.acquisition.VolumePanel;
import org.micromanager.lightsheetmanager.gui.tabs.channels.ChannelTablePanel;
import org.micromanager.lightsheetmanager.gui.playlist.AcquisitionTableFrame;
import org.micromanager.lightsheetmanager.gui.components.Button;
import org.micromanager.lightsheetmanager.gui.components.CheckBox;
import org.micromanager.lightsheetmanager.gui.components.ComboBox;
import org.micromanager.lightsheetmanager.gui.components.Panel;
import org.micromanager.lightsheetmanager.gui.components.ToggleButton;
import org.micromanager.lightsheetmanager.api.data.AcquisitionMode;

import javax.swing.JLabel;
import java.util.Objects;

public class AcquisitionTab extends Panel implements ListeningPanel, SettingsListener {

    // layout panel
    private Panel pnlRight_;
    private Panel pnlButtons_;

    private ComboBox<AcquisitionMode> cmbAcquisitionModes_;

    // acquisition buttons
    private ToggleButton btnRunAcquisition_;
    private ToggleButton btnPauseAcquisition_;
    private Button btnTestAcquisition_;
    private Button btnOpenPlaylist_;
    private Button btnSpeedTest_;
    private Button btnRunOverviewAcq_;

    // durations
    private DurationPanel pnlDurations_;

    // time points
    private TimePointsPanel pnlTimePoints_;
    private CheckBox cbxUseTimePoints_;

    // multiple positions
    private PositionPanel pnlMultiPositions_;
    private CheckBox cbxUseMultiplePositions_;

    // save data
    private SavePanel pnlSaveData_;

    // cameras
    private CameraPanel pnlCameras_;

    // channels
    private CheckBox cbxUseChannels_;
    private ChannelTablePanel pnlChannelTable_;

    // right panel
    private VolumePanel pnlVolumeSettings_;
    private SlicePanel pnlSliceSettings_;
    private AdvancedTimingPanel pnlAdvancedTiming_;
    private CheckBox cbxUseAdvancedTiming_;

    // acquisition playlist
    private final AcquisitionTableFrame acqTableFrame_;

    private final LightSheetManager model_;
    private final LightSheetManagerFrame frame_;

    public AcquisitionTab(final LightSheetManager model, final LightSheetManagerFrame frame) {
        model_ = Objects.requireNonNull(model);
        frame_ = Objects.requireNonNull(frame);
        acqTableFrame_ = new AcquisitionTableFrame(model_.studio());
        createUserInterface();
        createEventHandlers();
        model.userSettings().addChangeListener(this);
    }

    /**
     * Create the user interface.
     */
    private void createUserInterface() {

        final ScapeAcquisitionSettings settings = model_.acquisitions().settings();

        setMigLayout(
                "insets 10 10 10 10, ax center",
                "[300!]8[pref!]8[300!]",
                "[]5[]"
        );

        // layout panels
        final Panel pnlLeft = new Panel();
        final Panel pnlCenter = new Panel();
        pnlRight_ = new Panel();

        pnlCenter.setMigLayout(
                "",
                "",
                "[]10[]"
        );

        pnlDurations_ = new DurationPanel(model_);
        pnlVolumeSettings_ = new VolumePanel(model_);

        // switch between these two panels
        pnlSliceSettings_ = new SlicePanel(model_);
        pnlAdvancedTiming_ = new AdvancedTimingPanel(model_);

        // multiple positions
        cbxUseMultiplePositions_ = new CheckBox(
                "Multiple Positions", settings.isUsingMultiplePositions());
        pnlMultiPositions_ = new PositionPanel(model_, cbxUseMultiplePositions_);
        // disable elements based on settings
        pnlMultiPositions_.setPanelEnabled(settings.isUsingMultiplePositions());

        pnlSaveData_ = new SavePanel(model_, frame_);
        pnlCameras_ = new CameraPanel(model_);

        // time points
        cbxUseTimePoints_ = new CheckBox("Time Points", settings.isUsingTimePoints());
        pnlTimePoints_ = new TimePointsPanel(model_, cbxUseTimePoints_);
        // disable elements based on settings
        pnlTimePoints_.setPanelEnabled(settings.isUsingTimePoints());

        // acquisition buttons
        pnlButtons_ = new Panel();
        pnlButtons_.setMigLayout(
                "center",
                "[]26[]",
                ""
        );

        btnRunAcquisition_ = new ToggleButton(
                "Start Acquisition", "Stop Acquisition",
                Icons.ARROW_RIGHT, Icons.CANCEL, 120, 30
        );
        btnRunAcquisition_.setEnabled(true);

        btnPauseAcquisition_ = new ToggleButton(
                "Pause", "Resume",
                Icons.PAUSE, Icons.PLAY, 120, 30
        );
        btnPauseAcquisition_.setEnabled(false);

        btnTestAcquisition_ = new Button("Test Acquisition", 120, 30);
        btnOpenPlaylist_ = new Button("Playlist...", 120, 30);
        btnSpeedTest_ = new Button("Speed test", 120, 30);

        btnRunOverviewAcq_ = new Button("Overview Acquisition", 140, 30);

        final boolean channelsEnabled = settings.channels().enabled();
        cbxUseChannels_ = new CheckBox("Channels", channelsEnabled);
        pnlChannelTable_ = new ChannelTablePanel(model_, cbxUseChannels_);

        // disable elements based on settings
        pnlChannelTable_.setItemsEnabled(channelsEnabled);

        // acquisition mode combo box
        cmbAcquisitionModes_ = new ComboBox<>(
                AcquisitionMode.modesByType(
                        model_.devices().adapter().geometry(),
                        model_.devices().hasStageScanning()),
                settings.acquisitionMode(),
                180, 24);

        final boolean isUsingAdvancedTiming = settings.isUsingAdvancedTiming();
        cbxUseAdvancedTiming_ = new CheckBox("Use advanced timing settings",
                12, isUsingAdvancedTiming, CheckBox.RIGHT);
        // initial enabled or disabled state
        swapTimingSettingsPanels(isUsingAdvancedTiming);

        btnRunOverviewAcq_.setEnabled(false); // TODO: re-enable when these features are put in
        btnTestAcquisition_.setEnabled(false);

        // set ui sizes, should match the MigLayout constraints
        pnlChannelTable_.setAbsoluteSize(280, 400);
        pnlLeft.setAbsoluteSize(300, 580);
        pnlRight_.setAbsoluteSize(300, 580);

        // acquisition buttons
        pnlButtons_.add(btnRunAcquisition_, "");
        pnlButtons_.add(btnPauseAcquisition_, "");
        pnlButtons_.add(btnTestAcquisition_, "");
        pnlButtons_.add(btnRunOverviewAcq_, "");
        pnlButtons_.add(btnOpenPlaylist_, "");
        pnlButtons_.add(btnSpeedTest_, "");

        // 3 panel layout
        pnlLeft.add(pnlDurations_, "growx, growy");
        pnlLeft.add(pnlTimePoints_, "growx, growy, wrap");
        pnlLeft.add(pnlMultiPositions_, "growx, span 2, wrap");
        pnlLeft.add(pnlSaveData_, "growx, span 2, wrap");
        pnlLeft.add(pnlCameras_, "growx, span 2");

        pnlCenter.add(pnlChannelTable_, "wrap");
        pnlCenter.add(new JLabel("Acquisition Mode:"), "split 2");
        pnlCenter.add(cmbAcquisitionModes_, "");

        pnlRight_.add(pnlVolumeSettings_, "growx, wrap");
        pnlRight_.add(pnlSliceSettings_, "growx, wrap");
        pnlRight_.add(pnlAdvancedTiming_, "growx, wrap");
        pnlRight_.add(cbxUseAdvancedTiming_, "growx");

        // add panels
        add(pnlLeft, "aligny top");
        add(pnlCenter, "aligny top");
        add(pnlRight_, "aligny top, wrap");

        add(pnlButtons_, "span 3, growx, center, pushy, aligny bottom");
    }

    /**
     * Create event handlers for the user interface.
     */
    private void createEventHandlers() {

        // toggle acquisition running
        btnRunAcquisition_.registerListener(() -> {
            if (btnRunAcquisition_.isSelected()) {
                runAcquisition(false);
            } else {
                model_.acquisitions().requestStop();
            }
        });

        btnPauseAcquisition_.registerListener(() -> {
            if (btnPauseAcquisition_.isSelected()) {
                model_.acquisitions().requestPause();
            } else {
                model_.acquisitions().requestResume();
            }
        });

        btnOpenPlaylist_.registerListener(() -> acqTableFrame_.setVisible(true));
        btnOpenPlaylist_.setEnabled(false); // TODO: enable when playlist is implemented

        btnSpeedTest_.registerListener(() -> runAcquisition(true));
        btnRunOverviewAcq_.registerListener(() -> {
            // TODO: run the overview acq
        });

        // multiple positions
        cbxUseMultiplePositions_.registerListener(() -> {
            final boolean selected = cbxUseMultiplePositions_.isSelected();
            model_.acquisitions().settingsBuilder().useMultiplePositions(selected);
            model_.acquisitions().updateDurationLabels();
            pnlMultiPositions_.setPanelEnabled(selected);
        });

        // time points
        cbxUseTimePoints_.registerListener(() -> {
            final boolean selected = cbxUseTimePoints_.isSelected();
            model_.acquisitions().settingsBuilder().useTimePoints(selected);
            model_.acquisitions().updateDurationLabels();
            pnlTimePoints_.setPanelEnabled(selected);
        });

        // use channels
        cbxUseChannels_.registerListener(() -> {
            final boolean selected = cbxUseChannels_.isSelected();
            model_.acquisitions().settingsBuilder().channelBuilder().enabled(selected);
            model_.acquisitions().updateDurationLabels();
            pnlChannelTable_.setItemsEnabled(selected);
        });

        // select the acquisition mode
        cmbAcquisitionModes_.registerListener(() -> {
            model_.acquisitions().settingsBuilder()
                    .acquisitionMode(cmbAcquisitionModes_.getSelected());
            model_.acquisitions().updateDurationLabels();
        });

        // switches timing panels based on check box
        cbxUseAdvancedTiming_.registerListener(() -> {
            final boolean selected = cbxUseAdvancedTiming_.isSelected();
            model_.acquisitions().settingsBuilder().useAdvancedTiming(selected);
            swapTimingSettingsPanels(selected);
            if (selected) {
                pnlAdvancedTiming_.updateSpinners();
            }
            // update slice timing and duration labels
            model_.acquisitions().updateSettings();
            model_.acquisitions().updateDurationLabels();
        });
    }

    /**
     * Switch between slice timing panel and advanced timing panel.
     *
     * @param useAdvancedTiming {@code true} to swap to the advanced timing panel
     */
    private void swapTimingSettingsPanels(final boolean useAdvancedTiming) {
        pnlAdvancedTiming_.setPanelEnabled(useAdvancedTiming);
        pnlSliceSettings_.setPanelEnabled(!useAdvancedTiming);
    }

    public PositionPanel getMultiPositionPanel() {
        return pnlMultiPositions_;
    }

    private void acqFinishedCallback() {
        try {
            SwingUtilities.invokeAndWait(() -> {
                btnRunAcquisition_.setState(false);
                btnPauseAcquisition_.setEnabled(false);
                btnSpeedTest_.setEnabled(true);
            });
        } catch (InterruptedException e) {
            model_.studio().logs().logError("Acquisition was interrupted!");
        } catch (InvocationTargetException e) {
            model_.studio().logs().logError("Could not update UI components.");
        }
    }

    private void runAcquisition(boolean speedTest) {
        btnPauseAcquisition_.setEnabled(true);
        btnSpeedTest_.setEnabled(false);
        Future<?> acqFinished = model_.acquisitions().requestRun(speedTest);
        // Launch new thread to update the button when the acquisition is complete
        new Thread(() -> {
            try {
                acqFinished.get();
            } catch (Exception e) {
                model_.studio().logs().logError("error in runAcquisition");
            }
            // update the GUI when acquisition complete
            acqFinishedCallback();
        }).start();
    }

    @Override
    public void selected() {

    }

    @Override
    public void unselected() {

    }

    @Override
    public void onSettingsChanged(final AcquisitionSettings settings) {
        if (settings instanceof ScapeAcquisitionSettings) {
            var settingsScape = (ScapeAcquisitionSettings) settings;
            // update ui elements
            cmbAcquisitionModes_.setSelected(settingsScape.acquisitionMode());
            cbxUseTimePoints_.setSelected(settingsScape.isUsingTimePoints());
            cbxUseMultiplePositions_.setSelected(settingsScape.isUsingMultiplePositions());
            cbxUseChannels_.setSelected(settingsScape.channels().enabled());
            cbxUseAdvancedTiming_.setSelected(settingsScape.isUsingAdvancedTiming());
            // enable or disable ui
            pnlTimePoints_.setPanelEnabled(settingsScape.isUsingTimePoints());
            pnlMultiPositions_.setPanelEnabled(settingsScape.isUsingMultiplePositions());
            pnlChannelTable_.setItemsEnabled(settingsScape.channels().enabled());
            swapTimingSettingsPanels(settingsScape.isUsingAdvancedTiming());
            // display the updates
            revalidate();
            repaint();
        }
    }
}
