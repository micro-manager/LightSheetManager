package org.micromanager.lightsheetmanager.model.acquisitions;

import mmcorej.CMMCore;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.PositionList;
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
import org.micromanager.lightsheetmanager.api.AcquisitionManager;
import org.micromanager.lightsheetmanager.api.AcquisitionSettings;
import org.micromanager.lightsheetmanager.api.TimingSettings;
import org.micromanager.lightsheetmanager.api.data.AcquisitionMode;
import org.micromanager.lightsheetmanager.api.internal.ScapeAcquisitionSettings;
import org.micromanager.lightsheetmanager.gui.tabs.acquisition.DurationPanel;
import org.micromanager.lightsheetmanager.model.autofocus.AutofocusAdapter;
import org.micromanager.lightsheetmanager.model.channels.ChannelSpec;
import org.micromanager.lightsheetmanager.model.devices.cameras.CameraBase;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

public abstract class AcquisitionEngine implements AcquisitionManager, MMAcquistionControlCallbacks {

    protected final Studio studio_;
    protected final CMMCore core_;

    private static final AtomicLong runIdCounter_ = new AtomicLong();

    protected ScapeAcquisitionSettings.Builder asb_;
    // TODO(IMMUTABLE-RUN): stop-gap. The user interface writes this field and the acquisition
    // thread reads it. Volatile only makes publication safe, so a reader cannot see a partly
    // built snapshot; it does NOT stop the user interface rebuilding this field mid-run, it only
    // makes those rebuilds land sooner. Remove when the run reads a snapshot frozen at arm time.
    protected volatile ScapeAcquisitionSettings acqSettings_;

    private final ExecutorService acquisitionExecutor_ = Executors.newSingleThreadExecutor(
            r -> new Thread(r, "Acquisition Thread"));
    protected volatile Acquisition currentAcquisition_ = null; // TODO: consider making a getter rather than protected?

    // true from the moment a run is requested until the acquisition thread exits. currentAcquisition_
    // cannot answer "is a run in flight?": the engines only assign it partway through run(), so it
    // stays null across all of setup() and the arming that follows.
    private volatile boolean acquisitionRequested_ = false;
    // true while a test acquisition is in flight, read by updateSettings()
    private volatile boolean testAcquisition_;

    // a stop asked for before the acquisition was started, acted on by the checks in requestRun()
    // and in each engine's run()
    private volatile boolean stopRequested_ = false;

    // true while a separate time point series waits for its next time point
    private volatile boolean awaitingTimePoint_ = false;

    private final AutofocusAdapter autofocus_;

    // visibility: written on the acquisition thread, read on the edt when a window asks to close
    protected volatile Datastore datastore_;
    protected Pipeline curPipeline_;
    protected long nextWakeTime_ = -1;

    // null until a run captures it, cleared by finish()
    protected ShutterState shutterState_;

    protected DurationPanel pnlDuration_;

    protected final LightSheetManager model_;

    /**
     * Validates that the acquisition can actually be written to disk, before anything is acquired.
     * <p>
     * The images are written by {@code finish()}, i.e. only AFTER the run completes, so without this
     * check an unusable save location is discovered at the very end and the data is lost.
     * <p>
     * Called from both geometry engines' {@code setup()} before any hardware is touched, so a failure
     * costs nothing and leaves the microscope untouched.
     *
     * @return true if saving is off, or the save location is usable; false to abort setup
     */
    protected boolean validateSaveLocation() {
        if (!acqSettings_.isSavingImagesDuringAcquisition()) {
            return true; // not saving => nothing to validate
        }

        final String saveNamePrefix = acqSettings_.saveNamePrefix();
        if (saveNamePrefix == null || saveNamePrefix.trim().isEmpty()) {
            model_.logging().reportError("The save name prefix is empty.\n\n"
                    + "Set a name on the Save Settings panel, or uncheck "
                    + "\"Save images during acquisition\".");
            return false;
        }

        final String saveDirectory = acqSettings_.saveDirectory();
        if (saveDirectory == null || saveDirectory.trim().isEmpty()) {
            model_.logging().reportError("The save directory is not set.\n\n"
                    + "Set a directory on the Save Settings panel, or uncheck "
                    + "\"Save images during acquisition\".");
            return false;
        }

        final File directory = new File(saveDirectory);
        if (!directory.exists()) {
            model_.logging().reportError("The save directory does not exist:\n\n" + saveDirectory
                    + "\n\nCreate it, or choose another directory on the Save Settings panel.");
            return false;
        }
        if (!directory.isDirectory()) {
            model_.logging().reportError("The save directory is a file, not a directory:\n\n"
                    + saveDirectory);
            return false;
        }
        // canWrite() is advisory on Windows (it reports the read-only attribute, not the ACL), so it
        // catches the common cases without being authoritative; a real write failure still surfaces
        // at finish(). Cheap enough to be worth keeping.
        if (!directory.canWrite()) {
            model_.logging().reportError("The save directory is not writable:\n\n" + saveDirectory);
            return false;
        }

        studio_.logs().logMessage("save location validated: " + saveDirectory
                + File.separator + saveNamePrefix);
        return true;
    }

    /**
     * Validates that every imaging camera will deliver the same frame size, before anything is armed.
     *
     * <p>Cameras that disagree overrun the shared Core circular buffer and take the whole JVM with
     * them: {@code EXCEPTION_ACCESS_VIOLATION} inside {@code popNextImageMD}, no Java exception, no
     * recovery, no data. Refusing to arm is the only place this can be stopped from inside LSM.
     *
     * <p>Called from both geometry engines' {@code setup()} before any hardware is touched, so a
     * failure costs nothing and leaves the microscope untouched.
     *
     * @return true if the cameras agree, or there is only one; false to abort setup
     */
    protected boolean validateCameraFrameSizes() {
        final CameraBase[] cameras = model_.devices().imagingCameras();
        final String mismatch = CameraBase.describeFrameSizeMismatch(cameras);
        if (mismatch == null) {
            return true;
        }
        model_.logging().reportError("The imaging cameras have different frame sizes: " + mismatch
                + ".\n\nAcquiring with mismatched frame sizes crashes Micro-Manager outright, so this "
                + "acquisition was not started.\n\nSet the same ROI and binning on every imaging "
                + "camera from the Camera tab, then try again.");
        return false;
    }

    /**
     * Refuses to arm when the computed slice timing is not physically realizable.
     *
     * <p>In EDGE mode the solver derives the camera exposure by subtracting the camera's reset and
     * readout time from the sample exposure, and nothing stops the result going negative. A sample
     * exposure shorter than the camera needs to reset and read out therefore yields a negative
     * exposure, which is handed to {@code setExposure()} and accepted by the device without
     * complaint, so the run proceeds and produces nothing usable.
     *
     * <p>Deliberately conservative: only the two values that are meaningless at or below zero are
     * required to be positive. Delays and the remaining durations are allowed to be zero.
     *
     * <p>Note "minimize slice period" does not rescue this: it is only consulted on the galvo path
     * ({@code getTimingFromPeriodAndLightExposure}), while the stage-scan path derives the exposure
     * as sample exposure minus the camera's reset plus readout regardless.
     *
     * <p>Called from both geometry engines' {@code setup()} before any hardware is touched.
     *
     * @return true if the timing is usable; false to abort setup
     */
    protected boolean validateSliceTiming() {
        final String problems = describeUnusableTiming(acqSettings_.timing());
        if (problems == null) {
            return true;
        }
        model_.logging().reportError("The computed slice timing cannot be used: " + problems
                + ".\n\nThis happens when the sample exposure is shorter than the time the camera "
                + "needs to reset and read out, so the acquisition was not started.\n\nRaise the "
                + "sample exposure, or shorten the readout with a smaller camera ROI or higher "
                + "binning, then try again.");
        return false;
    }

    /**
     * Describes what is wrong with a computed timing schedule, or returns null when it is usable.
     *
     * @param timing the computed timing settings
     * @return a comma-separated description with no trailing punctuation, or null if usable
     */
    private static String describeUnusableTiming(final TimingSettings timing) {
        if (timing == null) {
            return "no timing has been computed";
        }
        final StringBuilder problems = new StringBuilder();
        // must be strictly positive: a slice that exposes for zero time images nothing
        appendTimingProblem(problems, "camera exposure", timing.cameraExposureMs(), true);
        appendTimingProblem(problems, "slice duration", timing.sliceDurationMs(), true);
        // may legitimately be zero, so only reject negatives
        appendTimingProblem(problems, "scan duration", timing.scanDurationMs(), false);
        appendTimingProblem(problems, "laser trigger duration", timing.laserTriggerDurationMs(), false);
        appendTimingProblem(problems, "camera trigger duration", timing.cameraTriggerDurationMs(), false);
        appendTimingProblem(problems, "delay before scan", timing.delayBeforeScanMs(), false);
        appendTimingProblem(problems, "delay before laser", timing.delayBeforeLaserMs(), false);
        appendTimingProblem(problems, "delay before camera", timing.delayBeforeCameraMs(), false);
        return problems.length() == 0 ? null : problems.toString();
    }

    private static void appendTimingProblem(final StringBuilder problems, final String name,
                                            final double valueMs, final boolean mustBePositive) {
        if (mustBePositive ? valueMs > 0.0 : valueMs >= 0.0) {
            return;
        }
        if (problems.length() > 0) {
            problems.append(", ");
        }
        problems.append(name).append(" is ").append(valueMs).append(" ms");
    }

    public AcquisitionEngine(final LightSheetManager model) {
        model_ = Objects.requireNonNull(model);
        studio_ = model.studio();
        core_ = model.core();

        autofocus_ = new AutofocusAdapter(model_);

        // default settings
        asb_ = ScapeAcquisitionSettings.builder();
        // seeded from the geometry because the angle has no single sensible default;
        // a saved profile replaces it later when UserSettings loads
        asb_.stageScanBuilder().firstViewAngle(
                model.devices().adapter().geometry().defaultFirstViewAngle());
        acqSettings_ = asb_.build();
    }


    //public abstract DefaultAcquisitionSettingsDISPIM settings();

    //public abstract <T extends DefaultAcquisitionSettings.Builder<Builder>> T settingsBuilder();

    abstract boolean setup();

    abstract boolean run();

    abstract void finish();

    /**
     * Whether the user asked to stop before the acquisition was started.
     * <p>
     * Everything from the moment a run is requested up to {@code currentAcquisition_.start()} is
     * preparation, and for the first part of it {@code currentAcquisition_} is still null, so
     * {@code requestStop()} has nothing to abort and only records the request. Engines must check
     * this before starting, or a stop clicked while arming is silently ignored and the run proceeds.
     *
     * @return true if the run should be given up instead of started
     */
    protected boolean isStopRequested() {
        return stopRequested_;
    }

    /**
     * Records whether a series is waiting between time points, for the stop and abort log lines.
     *
     * @param awaiting true while waiting for the next time point
     */
    protected void setAwaitingTimePoint(final boolean awaiting) {
        awaitingTimePoint_ = awaiting;
    }

    /**
     * Names the stage the run is in, for a log line reading "... during &lt;stage&gt;".
     */
    private String runPhase() {
        if (currentAcquisition_ != null && !currentAcquisition_.getDataSink().isFinished()) {
            return "acquisition";
        }
        return awaitingTimePoint_ ? "the wait between time points" : "setup";
    }

    public abstract void recalculateSliceTiming();

    public abstract void updateDurationLabels();

    public void setDurationPanel(final DurationPanel panel) {
        pnlDuration_ = Objects.requireNonNull(panel);
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
        // Build fully before assigning: acqSettings_ is read from other threads, and a second
        // assignment here would briefly publish the user's settings during a test acquisition.
        ScapeAcquisitionSettings settings = asb_.build();
        // TODO(IMMUTABLE-RUN): stop-gap. Re-applied here and not at the call site because
        // acqSettings_ is rebuilt from the builder at several points during a run, and the builder
        // holds the user's settings, so an override applied once is discarded by the next rebuild.
        // Once the run reads a snapshot frozen at arm time this is applied once at the freeze and
        // testAcquisition_ goes away.
        if (testAcquisition_) {
            settings = settings.copyBuilder()
                    .saveImagesDuringAcquisition(false)
                    // setup() refuses separate time points without saving
                    .separateTimePoints(false)
                    .useTimePoints(false)
                    .numTimePoints(1)
                    .build();
        }
        acqSettings_ = settings;
    }

    public Future<?> requestRun() {
        return requestRun(false);
    }

    @Override
    public Future<?> requestRun(boolean speedTest) {
        return requestRun(speedTest, false);
    }

    @Override
    public Future<?> requestTestAcquisition() {
        return requestRun(false, true);
    }

    private Future<?> requestRun(boolean speedTest, boolean testAcquisition) {
        // set here and not inside the task: a Stop clicked while the task is still queued, or
        // anywhere inside setup(), must find a run in flight
        acquisitionRequested_ = true;
        stopRequested_ = false; // never let a previous run's stop request kill this one
        awaitingTimePoint_ = false;

        // Run on a new thread, so it doesn't block the EDT
        Future<?> acqFinished = acquisitionExecutor_.submit(() -> {
            if (currentAcquisition_ != null) {
                model_.logging().reportError("Acquisition is already running.");
                return;
            }

            // marker data
            long runId = -1; // set at START; guards STOP so the speed-test path emits no marker
            long startNs = 0; // set alongside runId at START

            try {
                // set inside the task and not at request time: the executor runs one task at a
                // time, so this cannot reach a run that is already in flight, and the finally
                // below only ever clears the request it belongs to. Set before updateSettings()
                // so the first rebuild already carries the override.
                testAcquisition_ = testAcquisition;

                updateSettings(); // make sure settings are current

                if (speedTest) {
                    try {
                        SpeedTest.runSpeedTest(acqSettings_.saveDirectory(),
                              acqSettings_.saveNamePrefix(),
                              core_, acqSettings_.numTimePoints(), true);
                    } catch (Exception e) {
                        model_.logging().reportError(e);
                    }
                    return; // early exit => do speed test
                }

                // LSM-ACQ-START/STOP bracket the acquisition window for CoreLog diffing
                runId = runIdCounter_.incrementAndGet();
                // nanoTime() = monotonic; a wall-clock (currentTimeMillis) jump
                // mid-run can't corrupt the elapsed duration
                startNs = System.nanoTime();
                studio_.logs().logMessage("LSM-ACQ-START " + runId
                        + " " + acqSettings_.acquisitionMode());

                try {
                    if (!setup()) {
                        // every setup() failure path already showed its own specific error,
                        // so log this rather than stacking a second dialog on top of it
                        studio_.logs().logMessage("Error during setup!");
                        return; // early exit => stop acquisition
                    }
                } catch (Exception e) {
                    model_.logging().reportError(e, "Error during acquisition setup");
                    return; // early exit => stop acquisition
                }
                if (stopRequested_) {
                    // Stop was clicked during setup(), when there was no Acquisition to abort.
                    // Give up here so run() never touches the hardware. run() checks again just
                    // before it starts, which covers the rest of the window.
                    studio_.logs().logMessage("Acquisition stopped during setup.");
                    return; // early exit => finish() still tears down in the finally
                }

                run(); // run the acquisition and block until complete
            } catch (Exception e) {
                model_.logging().reportError(e);
            } finally {
                try {
                    finish(); // cleanup any resources
                } catch (Exception e) {
                    model_.logging().reportError(e, "Error during acquisition cleanup");
                } finally {
                    // must ALWAYS run: if currentAcquisition_ is left set, every future
                    // acquisition is rejected until the plugin restarts
                    currentAcquisition_ = null;
                    // cleared last of the run-state flags: while it is set, requestStop() treats
                    // a stop as something to act on rather than an error
                    acquisitionRequested_ = false;
                    // cleared here so the next run rebuilds from the user's own settings
                    testAcquisition_ = false;
                    // free the datastore so a large store isn't kept in memory (matches MM's
                    // AcqEngJAdapter.onAcquisitionEnded); also what the save guard checks to skip aborted/empty runs
                    datastore_ = null;
                    // LSM-ACQ-STOP in the innermost finally: fires on completion, error, abort, throwing finish()
                    if (runId != -1) {
                        final long elapsedMs = (System.nanoTime() - startNs) / 1_000_000L;
                        studio_.logs().logMessage("LSM-ACQ-STOP " + runId + " " + elapsedMs + " ms");
                    }
                }
            }
        });
        return acqFinished;
    }

    @Override
    public void requestStop() {
        if (!acquisitionRequested_) {
            model_.logging().reportError("Acquisition is not running.");
            return;
        }
        // record the request before trying to abort. During setup() there is nothing to abort yet,
        // and reporting "not running" there would leave the run going with the button reading
        // "Start Acquisition", which is also the way into a second, unwanted run.
        stopRequested_ = true;
        final boolean isAcquisitionLive = currentAcquisition_ != null
                && !currentAcquisition_.getDataSink().isFinished();
        studio_.logs().logMessage("stop requested during " + runPhase());
        if (isAcquisitionLive) {
            currentAcquisition_.abort();
        }
    }

    @Override
    public void requestPause() {
        if (currentAcquisition_ == null) {
            model_.logging().reportError("Acquisition is not running.");
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
     * Returns the number of time points a run acquires.
     *
     * <p>The time point count keeps its value while time points are off, so that turning them
     * back on restores it. Anything that sizes or checks a run reads the count through here,
     * which gives 1 in that case.
     *
     * @param settings the acquisition settings
     * @return the time point count, or 1 when time points are off
     */
    static int numTimePointsToAcquire(final AcquisitionSettings settings) {
        return settings.isUsingTimePoints() ? settings.numTimePoints() : 1;
    }

    /**
     * Higher level stuff in MM may depend on many hidden, poorly documented
     * ways on summary metadata generated by the acquisition engine.
     * This function adds in its fields in order to achieve compatibility.
     */
    protected DefaultSummaryMetadata addMMSummaryMetadata(JSONObject summaryMetadata) {
        return addMMSummaryMetadata(summaryMetadata, acqSettings_,
                studio_.positions().getPositionList(),
                numTimePointsToAcquire(acqSettings_));
    }

    /**
     * As above, but from a run snapshot, so every dataset of a series describes the same run
     * even if the settings or the position list are edited while it is in flight.
     *
     * @param summaryMetadata the acquisition's own summary metadata, mutated in place
     * @param settings the run snapshot
     * @param positionList the run's position list snapshot
     * @param numFrames the number of time points this dataset will hold
     */
    protected DefaultSummaryMetadata addMMSummaryMetadata(JSONObject summaryMetadata,
            final ScapeAcquisitionSettings settings, final PositionList positionList,
            final int numFrames) {
        try {
            // These are the ones from the clojure engine that may yet need to be translated
            //        "Channels" -> {Long@25854} 2

            summaryMetadata.put(PropertyKey.CHANNEL_GROUP.key(), settings.channels().group());

            // one name per position on the store's channel axis; with simultaneous cameras the
            // channel index varies fastest, so walk cameras outermost and repeat the whole channel
            // list per camera. This loop order is the slot order: reverse one and every image gets
            // the wrong name.
            final List<String> channelNames = new ArrayList<>();
            final List<String> baseChannelNames = new ArrayList<>();
            if (settings.channels().enabled() && settings.channels().count() > 0) {
                for (ChannelSpec c : settings.channels().used()) {
                    baseChannelNames.add(c.getName());
                }
            } else {
                baseChannelNames.add("Default");
            }
            if (model_.devices().adapter().numSimultaneousCameras() > 1) {
                for (CameraBase camera : model_.devices().imagingCameras()) {
                    for (String channelName : baseChannelNames) {
                        channelNames.add(settings.channels().enabled()
                                ? channelName + "-" + camera.getDeviceName()
                                : camera.getDeviceName());
                    }
                }
            } else {
                channelNames.addAll(baseChannelNames);
            }

            JSONArray chNames = new JSONArray();
            JSONArray chColors = new JSONArray();
            for (String channelName : channelNames) {
                chNames.put(channelName);
//                chColors.put(c.getRGB());
            }
            summaryMetadata.put(PropertyKey.CHANNEL_NAMES.key(), chNames);
            summaryMetadata.put(PropertyKey.CHANNEL_COLORS.key(), chColors);

            // MM MDA acquisitions have a defined number of
            // frames/slices/channels/positions at the outset
            summaryMetadata.put(PropertyKey.FRAMES.key(), numFrames);

            summaryMetadata.put(PropertyKey.SLICES.key(), settings.volume().slicesPerView());

            summaryMetadata.put(PropertyKey.CHANNELS.key(), channelNames.size());
            summaryMetadata.put(PropertyKey.POSITIONS.key(), settings.isUsingMultiplePositions() ?
                        positionList.getNumberOfPositions() : 1);

            // MM MDA acquisitions have a defined order
            summaryMetadata.put(PropertyKey.SLICES_FIRST.key(),
                  settings.acquisitionMode() == AcquisitionMode.STAGE_SCAN_INTERLEAVED);
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

            final int numPositions = positionList.getNumberOfPositions();

            // channelNames.size() already includes the simultaneous-camera factor
            final Coords dims = studio_.data().coordsBuilder()
                    .channel(channelNames.size())
                    .z(settings.volume().slicesPerView())
                    .timePoint(numFrames)
                    .stagePosition(settings.isUsingMultiplePositions() ? numPositions : 1)
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
                    .channelGroup(settings.channels().group())
                    .channelNames(channelNames)
                    .imageWidth((int)core_.getImageWidth())
                    .imageHeight((int)core_.getImageHeight())
                    .zStepUm(settings.volume().sliceStepSize())
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

    @Override
    public Acquisition current() {
        return currentAcquisition_;
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
        return acqSettings_.timePointIntervalSec();
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
        // the acquisition thread clears this field as soon as finish() returns
        final Acquisition acq = currentAcquisition_;
        if (acq == null) {
            return true; // nothing is running, so there is nothing to protect
        }
        // always refuse while a run is live: closing the live window would close the datastore
        // the run is still writing. answering yes only aborts, so the close succeeds on the next
        // attempt.
        if (model_.logging().confirmOrDefault("Abort Acquisition",
                "Abort the current acquisition task?", false)) {
            // the flag stops a series between time points. re-read the Acquisition: the series
            // runs on behind the modal dialog, so the one read above may have finished
            studio_.logs().logMessage("abort confirmed during " + runPhase());
            stopRequested_ = true;
            final Acquisition live = currentAcquisition_;
            if (live != null) {
                live.abort();
            }
        }
        return false;
    }

    @Override
    public DataProvider getAcquisitionDatastore() {
        return datastore_;
    }

    /**
     * Shutter state as it was before a run changed it.
     *
     * <p>finish() runs on paths that never reach the capture (setup failure, speed test), so the
     * reference is null until a run has captured it and is cleared again once restored. Assigning
     * it is the same statement as capturing it, so the two cannot disagree.
     */
    protected static final class ShutterState {
        final boolean isOpen;
        final boolean autoShutter;

        ShutterState(final boolean isOpen, final boolean autoShutter) {
            this.isOpen = isOpen;
            this.autoShutter = autoShutter;
        }
    }

}
