package org.micromanager.lightsheetmanager.model.acquisitions;

import mmcorej.CMMCore;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.Studio;
import org.micromanager.acqj.main.Acquisition;
import org.micromanager.acquisition.internal.MMAcquistionControlCallbacks;
import org.micromanager.acquisition.internal.acqengjcompat.speedtest.SpeedTest;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.Pipeline;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.LightSheetManagerPlugin;
import org.micromanager.lightsheetmanager.api.AcquisitionManager;
import org.micromanager.lightsheetmanager.api.data.AcquisitionMode;
import org.micromanager.lightsheetmanager.api.internal.ScapeAcquisitionSettings;
import org.micromanager.lightsheetmanager.gui.tabs.acquisition.DurationPanel;
import org.micromanager.lightsheetmanager.model.DataStorage;
import org.micromanager.lightsheetmanager.model.autofocus.AutofocusAdapter;
import org.micromanager.lightsheetmanager.model.channels.ChannelSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public abstract class AcquisitionEngine implements AcquisitionManager, MMAcquistionControlCallbacks {

    protected final Studio studio_;
    protected final CMMCore core_;

    protected ScapeAcquisitionSettings.Builder asb_;
    protected ScapeAcquisitionSettings acqSettings_;

    private final ExecutorService acquisitionExecutor_ = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "Acquisition Thread"));
    protected volatile Acquisition currentAcquisition_ = null; // TODO: consider making a getter rather than protected?

    private final AutofocusAdapter autofocus_;

    private DataStorage data_; // TODO: use this, has enum that needs moved/deleted?
    protected Datastore curStore_;
    protected Pipeline curPipeline_;
    protected long nextWakeTime_ = -1;

    protected DurationPanel pnlDuration_;

    protected final LightSheetManager model_;

    public AcquisitionEngine(final LightSheetManager model) {
        model_ = Objects.requireNonNull(model);
        studio_ = model.studio();
        core_ = model.core();

        data_ = new DataStorage(studio_);
        autofocus_ = new AutofocusAdapter(model_);

        // default settings
        asb_ = ScapeAcquisitionSettings.builder();
        acqSettings_ = asb_.build();
    }

    //public abstract DefaultAcquisitionSettingsDISPIM settings();

    //public abstract <T extends DefaultAcquisitionSettings.Builder<Builder>> T settingsBuilder();

    abstract boolean setup();

    abstract boolean run();

    abstract boolean finish();

    public abstract void recalculateSliceTiming();

    public abstract void updateDurationLabels();

    public void setDurationPanel(final DurationPanel panel) {
        pnlDuration_ = Objects.requireNonNull(panel);;
    }

    /**
     * Sets the acquisition settings and update the acquisition settings builder with current values.
     * <p>
     * This is used to load the plugin settings from JSON.
     *
     * @param acqSettings the {@code DefaultAcquisitionSettingsSCAPE} to use
     */
    public void updateSettings(final ScapeAcquisitionSettings acqSettings) {
        asb_ = new ScapeAcquisitionSettings.Builder(acqSettings);
        acqSettings_ = acqSettings;
    }

    /**
     * Build the {@code DefaultAcquisitionSettingsSCAPE} with the builder and update settings.
     */
    public void updateSettings() {
        acqSettings_ = asb_.build();
    }

    public Future<?> requestRun() {
        return requestRun(false);
    }

    @Override
    public Future<?> requestRun(boolean speedTest) {
        // Run on a new thread, so it doesn't block the EDT
        Future<?> acqFinished = acquisitionExecutor_.submit(() -> {
            if (currentAcquisition_ != null) {
                studio_.logs().showError("Acquisition is already running.");
                return;
            }

            try {
                updateSettings(); // make sure settings are current

                if (speedTest) {
                    try {
                        SpeedTest.runSpeedTest(acqSettings_.saveDirectory(),
                              acqSettings_.saveNamePrefix(),
                              core_, acqSettings_.numTimePoints(), true);
                    } catch (Exception e) {
                        studio_.logs().showError(e);
                    }
                } else {
                    studio_.logs().logMessage("Preparing Acquisition: plugin version " + LightSheetManagerPlugin.version);
                    // run methods implemented by acquisition engine geometry types
                    if (!setup()) {
                        studio_.logs().logError("error during setup!");
                        return; // early exit => stop acquisition
                    }
                    run(); // run the acquisition and block until complete
                }
            } catch (Exception e) {
                studio_.logs().showError(e);
            } finally {
                try {
                    finish(); // cleanup any resources
                } catch (Exception e) {
                    studio_.logs().showError(e, "Error during acquisition cleanup");
                } finally {
                    // must ALWAYS run: if currentAcquisition_ is left set, every future
                    // acquisition is rejected until the plugin restarts
                    currentAcquisition_ = null;
                }
            }
        });
        return acqFinished;
    }

    @Override
    public void requestStop() {
        if (currentAcquisition_ == null || currentAcquisition_.getDataSink().isFinished()) {
            studio_.logs().showError("Acquisition is not running.");
            return;
        }
        currentAcquisition_.abort();
    }

    @Override
    public void requestPause() {
        if (currentAcquisition_ == null) {
            studio_.logs().showError("Acquisition is not running.");
        } else {
            currentAcquisition_.setPaused(true);
        }
    }

    @Override
    public void requestResume() {
        if (currentAcquisition_ != null) {
            if (currentAcquisition_.isPaused()) {
                currentAcquisition_.setPaused(false);
            }
        }
    }

    /**
     * Higher level stuff in MM may depend on many hidden, poorly documented
     * ways on summary metadata generated by the acquisition engine.
     * This function adds in its fields in order to achieve compatibility.
     */
    protected DefaultSummaryMetadata addMMSummaryMetadata(JSONObject summaryMetadata) {
        try {
            // These are the ones from the clojure engine that may yet need to be translated
            //        "Channels" -> {Long@25854} 2

            summaryMetadata.put(PropertyKey.CHANNEL_GROUP.key(), acqSettings_.channels().group());
            JSONArray chNames = new JSONArray();
            JSONArray chColors = new JSONArray();
            if (acqSettings_.channels().enabled() && acqSettings_.channels().count() > 0) {
                for (ChannelSpec c : acqSettings_.channels().used()) {
                    chNames.put(c.getName());
//                chColors.put(c.getRGB());
                }
            } else {
                chNames.put("Default");
            }
            summaryMetadata.put(PropertyKey.CHANNEL_NAMES.key(), chNames);
            summaryMetadata.put(PropertyKey.CHANNEL_COLORS.key(), chColors);

            // MM MDA acquisitions have a defined number of
            // frames/slices/channels/positions at the outset
            summaryMetadata.put(PropertyKey.FRAMES.key(), acqSettings_.isUsingTimePoints() ? acqSettings_.numTimePoints() : 1);

            summaryMetadata.put(PropertyKey.SLICES.key(), acqSettings_.volume().slicesPerView());

            summaryMetadata.put(PropertyKey.CHANNELS.key(), acqSettings_.channels().enabled() ?
                    acqSettings_.channels().count() : 1);
            summaryMetadata.put(PropertyKey.POSITIONS.key(), acqSettings_.isUsingMultiplePositions() ?
                        studio_.positions().getPositionList().getNumberOfPositions() : 1);

            // MM MDA acquisitions have a defined order
            summaryMetadata.put(PropertyKey.SLICES_FIRST.key(),
                  acqSettings_.acquisitionMode() == AcquisitionMode.STAGE_SCAN_INTERLEAVED);
            summaryMetadata.put(PropertyKey.TIME_FIRST.key(),
                  false); // currently only position, time ordering

            SummaryMetadata.Builder dsmb = new DefaultSummaryMetadata.Builder();

            List<String> axesOrdered = dsmb.build().getOrderedAxes();
            axesOrdered.add(LightSheetEventAdapter.CAMERA_AXIS);
            // convert to JSON array
            JSONArray axes = new JSONArray();
            for (String axis : axesOrdered) {
                axes.put(axis);
            }
            summaryMetadata.put(PropertyKey.AXIS_ORDER.key(), axes);

            // handle multiple cameras
            int numChannels = acqSettings_.channels().count();
            if (model_.devices().adapter().numSimultaneousCameras() > 1) {
                numChannels *= model_.devices().adapter().numSimultaneousCameras();
            }

            final int numPositions = studio_.positions().getPositionList().getNumberOfPositions();

            final Coords dims = studio_.data().coordsBuilder()
                    .channel(acqSettings_.channels().enabled() ? numChannels : 1)
                    .z(acqSettings_.volume().slicesPerView())
                    .timePoint(acqSettings_.isUsingTimePoints() ? acqSettings_.numTimePoints() : 1)
                    .stagePosition(acqSettings_.isUsingMultiplePositions() ? numPositions : 1)
                    .build();

            final List<String> axisOrder = new ArrayList<>();
            axisOrder.add(Coords.T);
            axisOrder.add(Coords.P);
            axisOrder.add(Coords.C);
            axisOrder.add(Coords.Z);

            // add "z-step_um" metadata to the image viewer (used in the deskew plugin)
            final DefaultSummaryMetadata dsmd = (DefaultSummaryMetadata) dsmb
                    .prefix("")
                    .axisOrder(axisOrder)
                    .channelGroup(model_.acquisitions().settings().channels().group())
                    .imageWidth((int)core_.getImageWidth())
                    .imageHeight((int)core_.getImageHeight())
                    .zStepUm(acqSettings_.volume().sliceStepSize())
                    .intendedDimensions(dims)
                    .build();

            summaryMetadata.put(PropertyKey.MICRO_MANAGER_VERSION.key(), dsmd.getMicroManagerVersion());
            return dsmd;
        } catch (JSONException e) {
            studio_.logs().logError(e);
            throw new RuntimeException(e);
        }
    }

    public ScapeAcquisitionSettings settings() {
        return acqSettings_;
    }

    public ScapeAcquisitionSettings.Builder settingsBuilder() {
        return asb_;
    }

    public AutofocusAdapter autofocus() {
        return autofocus_;
    }

//////////////////////// AcquisitionControl Callback methods ////////////////////////
    @Override
    public void stop(boolean interrupted) {
        // unclear that this parameter is used in other code
        if (currentAcquisition_ != null) {
            currentAcquisition_.abort();
        }
    }

    @Override
    public boolean isAcquisitionRunning() {
        return currentAcquisition_ != null && !currentAcquisition_.areEventsFinished();
    }

    @Override
    public double getFrameIntervalMs() {
        return acqSettings_.timePointInterval();
    }

    @Override
    public long getNextWakeTime() {
        return nextWakeTime_;
    }

    @Override
    public boolean isPaused() {
        if (currentAcquisition_ != null) {
            return currentAcquisition_.isPaused();
        }
        return false;
    }

    @Override
    public void setPause(boolean b) {
        if (currentAcquisition_ != null) {
            currentAcquisition_.setPaused(b);
        }
    }

    @Override
    public boolean abortRequest() {
        if (currentAcquisition_ != null) {
            currentAcquisition_.abort();
        }
        return true;
    }

    @Override
    public DataProvider getAcquisitionDatastore() {
        return curStore_;
    }

}
