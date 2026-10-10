package org.micromanager.lightsheetmanager.model.acquisitions;

import mmcorej.StrVector;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import com.google.gson.GsonBuilder;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.PropertyMaps;
import org.micromanager.acqj.api.AcquisitionHook;
import org.micromanager.acqj.main.Acquisition;
import org.micromanager.acqj.main.AcquisitionEvent;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.acquisition.internal.MMAcquisition;
import org.micromanager.acquisition.internal.acqengjcompat.AcqEngJAdapter;
import org.micromanager.acquisition.internal.acqengjcompat.AcqEngJMDADataSink;
import org.micromanager.data.Datastore;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DataViewerListener;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.DefaultDisplayManager;
import org.micromanager.display.internal.RememberedDisplaySettings;
import org.micromanager.lightsheetmanager.api.data.AcquisitionMode;
import org.micromanager.lightsheetmanager.api.data.CameraLibrary;
import org.micromanager.lightsheetmanager.api.data.CameraMode;
import org.micromanager.lightsheetmanager.api.data.ChannelMode;
import org.micromanager.lightsheetmanager.api.data.SaveMode;
import org.micromanager.lightsheetmanager.api.internal.DefaultTimingSettings;
import org.micromanager.lightsheetmanager.api.internal.ScapeAcquisitionSettings;
import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.model.PLogicScape;
import org.micromanager.lightsheetmanager.model.devices.DeviceAdapter;
import org.micromanager.lightsheetmanager.model.devices.NIDAQ;
import org.micromanager.lightsheetmanager.model.devices.cameras.AndorCamera;
import org.micromanager.lightsheetmanager.model.devices.cameras.CameraBase;
import org.micromanager.lightsheetmanager.model.devices.cameras.DemoCamera;
import org.micromanager.lightsheetmanager.model.devices.cameras.HamamatsuCamera;
import org.micromanager.lightsheetmanager.model.devices.cameras.PcoCamera;
import org.micromanager.lightsheetmanager.model.devices.cameras.PvCamera;
import org.micromanager.lightsheetmanager.model.devices.vendor.ASIScanner;
import org.micromanager.lightsheetmanager.model.devices.vendor.ASIXYStage;
import org.micromanager.lightsheetmanager.model.utils.FileUtils;
import org.micromanager.lightsheetmanager.model.utils.GeometryUtils;
import org.micromanager.lightsheetmanager.model.utils.NumberUtils;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.geom.Point2D;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Manages the acquisition for SCAPE microscopes.
 */
public class AcquisitionEngineScape extends AcquisitionEngine {

    // NDTiff grows each new file to this length on open and trims it on close, so every dataset
    // needs this much free space however little it holds
    private static final long NDTIFF_FILE_RESERVATION_BYTES = 4L << 30;

    PLogicScape controller_;
    ArrayList<Double> savedExposures_ = new ArrayList<>();
    Point2D.Double xyPosUm_;
    // Snapshot taken when the run is armed. The position list is user-editable at any time, so
    // a live read can give different answers to different parts of one run: the saved
    // position_list.pos, the generated events, and the per-arm stage scan setup must agree.
    private volatile PositionList positionList_;
    private double origSpeedX_;
    private double origAccelX_;
    private double scanSpeedX_;
    private double scanAccelX_;
    private boolean isPolling_; // true if polling was enabled at the start of an acquisition
    // the Core-Camera and channel preset setup() found, which finish() puts back; null means
    // this run never captured them
    private String originalCoreCamera_;
    private String originalChannelPreset_;

    // Vetoes closing the running acquisition's window unless the user confirms an abort. Otherwise
    // the display manager closes the store under the running acquisition and every later image
    // is dropped without an abort.
    private final DataViewerListener liveWindowCloseGuard_ = new DataViewerListener() {
        @Override
        public boolean canCloseViewer(final DataViewer viewer) {
            final Datastore live = datastore_;
            if (live == null || viewer.getDataProvider() != live) {
                return true; // not the running acquisition's window
            }
            return abortRequest();
        }
    };

    public AcquisitionEngineScape(final LightSheetManager model) {
        super(Objects.requireNonNull(model));
    }

    @Override
    boolean setup() {

        isPolling_ = model_.positions().isPolling();
        if (isPolling_) {
            model_.positions().stopPolling();
            studio_.logs().logMessage("stopped position polling");
        }

        asb_.sheetCalibrationBuilder().autoSheetWidthEnabled(true);
        asb_.sheetCalibrationBuilder().autoSheetWidthPerPixel(0.0);

        // make settings current
        updateSettings();

        // initialize stage scanning so we can restore state
        // set before any validation below can return early: finish() runs on every exit from setup(),
        // including the refusals, and restores these unconditionally. Initialized further down they
        // are still at their field defaults on those paths, so finish() writes 0.0. The stage rejects
        // that for speed but ACCEPTS it for acceleration, leaving it unable to move properly.
        // null means this run never captured a position, so finish() leaves the stage alone.
        // (0,0) cannot say that because it is a position the stage can actually be at.
        xyPosUm_ = null;
        origSpeedX_ = 1.0; // don't want 0 in case something goes wrong
        origAccelX_ = 1.0; // don't want 0 in case something goes wrong
        originalCoreCamera_ = null;
        originalChannelPreset_ = null;

        // fail before touching any hardware: the datastore is written by finish(), so an unusable
        // save location would otherwise cost a full acquisition before it is discovered
        if (!validateSaveLocation()) {
            return false; // early exit => save location unusable
        }

        // each time point's store is closed once the next one starts, so without saving the
        // images would be lost. checked here because the api can set both flags independently
        if (isSeparatingTimePoints(acqSettings_)
                && !acqSettings_.isSavingImagesDuringAcquisition()) {
            model_.logging().reportError("Separate time points requires saving.\n\n"
                    + "Check \"Save images during acquisition\" on the Save Settings panel, "
                    + "or uncheck \"Separate file for each time point\".");
            return false; // early exit => nothing would reach disk
        }

        // make sure that there are positions in the PositionList; a pure read, so it belongs
        // above the hardware changes below for the same reason as the save location check
        positionList_ = studio_.positions().getPositionList();
        if (acqSettings_.isUsingMultiplePositions()) {
            final int numPositions = positionList_.getNumberOfPositions();
            if (numPositions == 0) {
                studio_.logs().showError("XY positions expected but the position list is empty");
                return false;
            }
        }

        // checked here so demo and NIDAQ runs, which skip doHardwareCalculations, refuse too
        if (acqSettings_.channels().enabled() && acqSettings_.channels().count() == 0) {
            studio_.logs().showError("\"Channels\" is checked, but no channels are selected");
            return false;
        }

        // the builder refuses these modes, but loaded settings bypass it
        final String cameraModeProblem =
                ScapeAcquisitionSettings.cameraModeProblem(acqSettings_.cameraMode());
        if (cameraModeProblem != null) {
            studio_.logs().showError(cameraModeProblem + ".\n\n"
                    + "Select a mode in the Trigger Mode menu on the Acquisition tab.");
            return false;
        }

        // mismatched camera frame sizes kill the JVM once acquisition starts, so refuse to arm
        if (!validateCameraFrameSizes()) {
            return false; // early exit => cameras disagree on frame size
        }

        // an impossible slice period yields a negative camera exposure, which the device accepts
        // without complaint and then images nothing; refuse rather than run it
        if (!validateSliceTiming()) {
            return false; // early exit => computed timing is not realizable
        }

//        // check pixel size
//        if (core_.getPixelSizeUm() < 1e-6) {
//            studio_.logs().showError(
//                    "Pixel size not set, navigate to \"Devices > Pixel Size Calibration...\" to set the value.");
//            return false;
//        }

        // Live Mode must be stopped before setting the "Core-Camera" property below,
        // MMCore throws if a sequence acquisition (Live Mode) is running.
        final boolean isLiveModeOn = studio_.live().isLiveModeOn();
        if (isLiveModeOn) {
            studio_.live().setLiveModeOn(false);
            // close the live mode window if it exists
            if (studio_.live().getDisplay() != null) {
                studio_.live().getDisplay().close();
            }
        }

        // set the "Core-Camera" property to the first logical camera device
        final String cameraName = model_.devices().firstImagingCamera().getDeviceName();
        originalCoreCamera_ = core_.getCameraDevice();
        try {
            core_.setCameraDevice(cameraName);
        } catch (Exception e) {
            studio_.logs().showError("Could not set \"Core-Camera\" to the first logical camera device.");
            return false;
        }

        // the run's events switch presets in the channel group
        if (acqSettings_.channels().enabled()) {
            final String group = acqSettings_.channels().group();
            try {
                originalChannelPreset_ = core_.getCurrentConfig(group);
            } catch (Exception e) {
                studio_.logs().logError("Could not read the current preset of channel group " + group);
            }
        }

        // this is needed for LSMAcquisitionEvents to work with multiple positions
        if (core_.getFocusDevice().isEmpty()
                && acqSettings_.isUsingMultiplePositions()) {
            studio_.logs().showError(
                    "The default focus device \"Core-Focus\" needs to be set to use multiple positions.");
            return false;
        }


        return true;
    }

    @Override
    boolean run() {

        // save current exposure to restore later
        CameraBase[] cameras = model_.devices().imagingCameras();
        savedExposures_ = new ArrayList<>();
        for (CameraBase camera : cameras) {
            savedExposures_.add(camera.getExposure());
        }

        // used to detect if the plugin is using ASI hardware
        final boolean isUsingPLC = model_.devices().isUsingPLogic();

        // make sure stage scan is supported if selected
        if (acqSettings_.stageScan().enabled()) {
            final ASIXYStage xyStage = model_.devices().device("SampleXY");
            if (xyStage != null) {
                if (!xyStage.hasProperty(ASIXYStage.Properties.SCAN_NUM_LINES)) {
                    studio_.logs().showError("Must have stage with scan-enabled firmware for stage scanning.");
                    return false;
                }
                // second part: initialize stage scanning, so we can restore state later
                xyPosUm_ = xyStage.getXYPosition();
                origSpeedX_ = xyStage.getSpeedX();
                origAccelX_ = xyStage.getAccelerationX();

                // if X speed is less than 0.2 mm/s then it probably wasn't restored to correct speed some other time
                if (origSpeedX_ < 0.2) {
                    // quiet answer is "Yes": the small speed is residue of an interrupted scan, and
                    // an unattended run should take the recovery path rather than hang on a dialog
                    final boolean result = model_.logging().confirmOrDefault("Change Speed",
                            "Max speed of X axis is small, perhaps it was not correctly restored after " +
                                    "stage scanning previously. Do you want to set it to 1 mm/s now?", true);
                    if (result) {
                        xyStage.setSpeedX(1.0);
                        // origSpeedX_ is the value finish() restores, so it has to track the change we
                        // just made; otherwise we put the small speed straight back at the end of the run
                        origSpeedX_ = 1.0;
                    }
                }
                // TODO: add more checks from original plugin here... Z speed?
            }
        }

        // Assume demo mode if default camera is DemoCamera
        boolean demoMode = false;
        try {
            demoMode = core_.getDeviceLibrary(core_.getCameraDevice()).equals("DemoCamera");
        } catch (Exception e) {
            studio_.logs().logError(e);
        }

        if (!demoMode) {

            if (isUsingPLC) {
                controller_ = new PLogicScape(model_);

                final boolean success = doHardwareCalculations(controller_);
                if (!success) {
                    return false; // early exit => could not set up hardware
                }
            } else {
                doHardwareCalculationsNIDAQ();
            }
        }


            // --- testing code below ---
//            StrVector deviceNames = core_.getLoadedDevices();
//            for (String deviceName : deviceNames) {
//                System.out.println("deviceName: " + deviceName);
//                StrVector propertyNames;
//                try {
//                    propertyNames = core_.getDevicePropertyNames(deviceName);
//                } catch (Exception e) {
//                    propertyNames = null;
//                }
//
//                Gson gsonObj = new Gson();
//                HashMap<String, String> deviceProps = new HashMap<>();
//                for (String propName : propertyNames) {
//                    String propValue;
//                    try {
//                        propValue = core_.getProperty(deviceName, propName);
//                    } catch (Exception e) {
//                        propValue = "";
//                        System.out.println("failed!");
//                    }
//                    deviceProps.put(propName, propValue);
//                    //System.out.println(propName);
//                }
//
//                String jsonStr = gsonObj.toJson(deviceProps);
//                System.out.println(jsonStr);
//            }

        updateSettings();

        // The run snapshot: everything below reads this, never acqSettings_, so an edit made while
        // the run is in flight cannot change it. finish() still reads the live field.
        // TODO(IMMUTABLE-RUN): remove once acqSettings_ is the single run-time source of truth.
        final ScapeAcquisitionSettings settings = acqSettings_;

        final String settingsJson = settings.toPrettyJson();
        studio_.logs().logMessage("Starting Acquisition with settings:\n" + settingsJson);

        final String saveDir = settings.saveDirectory();
        final String saveName = settings.saveNamePrefix();

        // Sets MM's persisted preferred save mode. MMAcquisition reads it when SequenceSettings
        // has save() and root() set, which is what the saving branch below does, so this is what
        // picks ND-TIFF over multipage TIFF or a single plane series for the images written during
        // the run. It is the only channel MMAcquisition offers for that choice.
        // Once per run, not per time point: every profile write delays the profile's flush.
        if (settings.saveMode() == SaveMode.ND_TIFF) {
            DefaultDatastore.setPreferredSaveMode(studio_, Datastore.SaveMode.ND_TIFF);
        } else if (settings.saveMode() == SaveMode.MULTIPAGE_TIFF) {
            DefaultDatastore.setPreferredSaveMode(studio_, Datastore.SaveMode.MULTIPAGE_TIFF);
        } else if (settings.saveMode() == SaveMode.SINGLEPLANE_TIFF_SERIES) {
            DefaultDatastore.setPreferredSaveMode(studio_, Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES);
        } else {
            studio_.logs().showError("Unsupported save mode: " + settings.saveMode());
            return false;
        }

        studio_.events().registerForEvents(this);
        // commented because this is prob specific to MM MDAs
//        studio_.events().post(new DefaultAcquisitionStartedEvent(datastore_, this,
//              acquisitionSettings));

        final String[] cameraNames = resolveCameraNames(settings, demoMode);

        // read once, before the acquisition starts, so every channel's offset has the same origin
        final Double baseFocusUm = readBaseFocusPosition(settings);

        // Last chance to honor a Stop clicked while everything above was being armed. Checked
        // before the shutter is touched, so giving up here cannot leave it open.
        if (isStopRequested()) {
            studio_.logs().logMessage("Acquisition stopped before it started.");
            return false; // early exit => finish() still restores whatever was armed
        }

        // once per run: finish() restores what is captured here, and a second capture would
        // record the first one's autoshutter off and shutter open
        captureShutterStateAndOpen();

        if (!isSeparatingTimePoints(settings)) {
            if (!startTimePointAcquisition(settings, cameraNames, baseFocusUm, settingsJson,
                    saveDir, saveName, -1)) {
                // never wait on an Acquisition that was not finished: the wait is unbounded
                return false;
            }
            currentAcquisition_.waitForCompletion();
            return true;
        }

        return runSeparateTimePoints(settings, cameraNames, baseFocusUm, settingsJson,
                saveDir, saveName);
    }

    /**
     * True when this run writes one dataset per time point. The flag is ignored when time points
     * are off, as numTimePoints is.
     */
    private static boolean isSeparatingTimePoints(final ScapeAcquisitionSettings settings) {
        return settings.isUsingTimePoints() && settings.isUsingSeparateTimePoints();
    }

    /**
     * Resolves the camera device names the events are addressed to.
     *
     * @param settings the run snapshot
     * @param demoMode true if the default camera is a DemoCamera
     * @return the camera device names, in slot order
     */
    private String[] resolveCameraNames(final ScapeAcquisitionSettings settings,
            final boolean demoMode) {
        String[] cameraNames;
        if (demoMode) {
            ArrayList<String> cameraDeviceNames = new ArrayList<>();
            StrVector loadedDevices = core_.getLoadedDevices();
            for (int i = 0; i < loadedDevices.size(); i++) {
                try {
                    if (core_.getDeviceType(loadedDevices.get(i)).toString().equals("CameraDevice")) {
                        cameraDeviceNames.add(loadedDevices.get(i));
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            cameraNames = cameraDeviceNames.toArray(new String[0]);
        } else {
            final DeviceAdapter adapter = model_.devices().adapter();
            if (adapter.numSimultaneousCameras() > 1 && adapter.numImagingPaths() == 1) {
                // multiple simultaneous cameras
                final ArrayList<String> names = new ArrayList<>();
                final CameraBase[] cameraList = model_.devices().imagingCameras();
                for (CameraBase camera: cameraList) {
                    names.add(camera.getDeviceName());
                }
                cameraNames = names.toArray(String[]::new);
            } else {
               // standard camera setup
               if (settings.volume().numViews() > 1) {
                  cameraNames = new String[] {
                        model_.devices().device("Imaging1Camera").getDeviceName(),
                        model_.devices().device("Imaging2Camera").getDeviceName()
                  };
               } else {
                  cameraNames = new String[] {
                        model_.devices().device("ImagingCamera").getDeviceName()
                  };
               }
            }
        }
        return cameraNames;
    }

    /**
     * Reads the focus position the channel offsets are applied to, or null when the events do not
     * apply them.
     *
     * <p>Read once, before the acquisition starts. An event factory that reads the stage itself
     * can find it already moved by the events submitted before it, and adds its channel's offset
     * on top of another channel's.
     *
     * @param settings the run snapshot
     * @return the focus position in micrometers, or null
     */
    private Double readBaseFocusPosition(final ScapeAcquisitionSettings settings) {
        if (!settings.channels().enabled() || core_.getFocusDevice().isEmpty()) {
            return null;
        }
        // the same cases as the factories: software channels always read it, hardware channel
        // switching only when a single channel is baked onto the base event
        final boolean readsFocus = settings.channels().mode() == ChannelMode.VOLUME
                || settings.channels().used().length == 1;
        if (!readsFocus) {
            return null;
        }
        try {
            return core_.getPosition();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Captures the shutter and autoshutter state for finish() to restore, then opens the shutter
     * for the run.
     */
    private void captureShutterStateAndOpen() {
        ///////////// Turn off autoshutter /////////////////
        try {
            shutterState_ = new ShutterState(core_.getShutterOpen(), core_.getAutoShutter());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // TODO: should the shutter be left open for the full duration of acquisition?
        //  because that's what this code currently does
        if (shutterState_.autoShutter) {
            core_.setAutoShutter(false);
            if (!shutterState_.isOpen) {
                try {
                    core_.setShutterOpen(true);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Builds and starts one dataset, and returns once every event for it has been submitted.
     * The controller is already armed and the shutter already open.
     *
     * <p>Call waitForCompletion() only if this returned true: an early exit leaves an Acquisition
     * that was never finished, and waiting on it blocks forever.
     *
     * @param settings the run snapshot
     * @param cameraNames the camera device names, resolved once for the run
     * @param baseFocusUm the focus position channel offsets are applied to, or null
     * @param settingsJson the run settings, written into the dataset directory
     * @param root the directory MMAcquisition creates the dataset in
     * @param prefix the dataset name, which MMAcquisition suffixes with a counter of its own
     * @param datasetTimeIndex the time point this dataset holds, or -1 when the dataset is the
     *                         whole run
     * @return true if the acquisition started and every event was submitted
     */
    private boolean startTimePointAcquisition(final ScapeAcquisitionSettings settings,
            final String[] cameraNames, final Double baseFocusUm, final String settingsJson,
            final String root, final String prefix, final int datasetTimeIndex) {

        // used to detect if the plugin is using ASI hardware
        final boolean isUsingPLC = model_.devices().isUsingPLogic();
        final boolean separate = datasetTimeIndex >= 0;
        // one time point per dataset in separate mode, the whole series otherwise
        final int numTimePoints = separate ? 1
                : numTimePointsToAcquire(settings);

        //////////////////////////////////////
        // Begin AcqEngJ integration
        //      The acqSettings object should be static at this point, it will now
        //      be parsed and used to create acquisition events, each of which
        //      will "order" the acquisition of 1 image (per each camera)
        //////////////////////////////////////
        // Create acquisition
        AcqEngJMDADataSink sink = new AcqEngJMDADataSink(studio_.events(), new AcqEngJAdapter(studio_));

        currentAcquisition_ = new Acquisition(sink);

        JSONObject summaryMetadata = currentAcquisition_.getSummaryMetadata();
        try {
            summaryMetadata.put("z-step_um", settings.volume().sliceStepSize());
        } catch (JSONException e) {
            studio_.logs().logError("Failed to add z-step_um metadata: " + e.getMessage());
        }
        SummaryMetadata dsmd = addMMSummaryMetadata(summaryMetadata, settings, positionList_,
                numTimePoints);
        if (separate) {
            // Every dataset holds time index 0, so record which time point this is. The start
            // time is the actual one, taken as the dataset opens: a late time point starts after
            // its slot.
            dsmd = dsmd.copyBuilder()
                    // Micro-Manager's own start time key and format
                    .startDate(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.ROOT)
                            .format(new Date()))
                    .userData(PropertyMaps.builder()
                            .putBoolean("SeparateTimePoints", true)
                            .putInteger("TimePointIndex", datasetTimeIndex)
                            .build())
                    .build();
        }

        // TODO(Brandon): where should i get this from?
        SequenceSettings.Builder sequenceSettingsBuilder = new SequenceSettings.Builder();
        // LSM opens the window itself, see createAcquisitionDisplay()
        sequenceSettingsBuilder.shouldDisplayImages(false);
        // Write images to disk as they arrive instead of accumulating the run in memory.
        // MMAcquisition swaps StorageRAM for the preferred save mode set above only when both
        // save() and root() are set, so setting them here is what selects streaming. This is what
        // the checkbox means in 1.4 as well: checked writes during the run, unchecked keeps the
        // run in memory.
        if (settings.isSavingImagesDuringAcquisition()) {
            sequenceSettingsBuilder.save(true)
                    .root(root)
                    .prefix(prefix);
        }

        MMAcquisition acq = new MMAcquisition(studio_, dsmd,
                this, sequenceSettingsBuilder.build());

        datastore_ = acq.getDatastore();
        curPipeline_ = acq.getPipeline();
        sink.setDatastore(datastore_);
        sink.setPipeline(curPipeline_);

        // TODO: put this in AcquisitionEngine base class, between setup and run once structure is better
        // Write the run settings and the position list into the dataset directory instead of the
        // parent, so a dataset carries the record of what produced it. Written here rather than
        // earlier because MMAcquisition above chooses the directory name.
        if (settings.isSavingImagesDuringAcquisition()) {
            // the path MMAcquisition gave the storage, which is the dataset directory
            final String datasetDir = datastore_.getSavePath();
            if (datasetDir == null) {
                // saving was never set up: MMAcquisition failed before creating the storage and
                // has already shown its error
                studio_.logs().logError("The dataset directory could not be created under "
                        + root + "; the acquisition was not started.");
                return false; // early exit => nothing started, so nothing to wait for
            }
            FileUtils.writeStringToFile(
                    datasetDir + File.separator + "acq_settings.json", settingsJson);
            if (settings.isUsingMultiplePositions()
                    && positionList_.getNumberOfPositions() > 0) {
                try {
                    final String path = datasetDir + File.separator + "position_list.pos";
                    positionList_.save(path);
                    studio_.logs().logMessage("Position list saved to " + path);
                } catch (Exception e) {
                    studio_.logs().logError(e, "Could not save position list.");
                }
            }
        }

        // after the directory check above, so a dataset that could not be created gets no window
        createAcquisitionDisplay(dsmd);

        // TODO if position time ordering ever implemented, this should be reactivated and the
        //  timelapse hook copied from org.micromanager.acquisition.internal.acqengjcompat.AcqEngJAdapter
//        if (sequenceSettings_.acqOrderMode() == AcqOrderMode.POS_TIME_CHANNEL_SLICE
//              || sequenceSettings_.acqOrderMode() == AcqOrderMode.POS_TIME_SLICE_CHANNEL) {
//            // Pos_time ordered acquisition need their timelapse minimum start time to be
//            // adjusted for each position.  The only place to do that seems to be a hardware hook.
//            currentAcquisition_.addHook(timeLapseHook(acquisitionSettings),
//                  AcquisitionAPI.BEFORE_HARDWARE_HOOK);
//        }

        ////////////  Acquisition hooks ////////////////////
        // These functions will be run on different threads during the acquisition process
        //    Hooks will run on the Acquisition Engine thread, the one that controls all hardware

        // TODO add any code that needs to be executed on the acquisition thread (i.e. the one
        //  that controls hardware)

        // TODO: autofocus
        currentAcquisition_.addHook(new AcquisitionHook() {
            @Override
            public AcquisitionEvent run(AcquisitionEvent event) {
                // TODO: does the Tiger controller need to be cleared and/or checked for errors here?

                if (event.isAcquisitionFinishedEvent()) {
                    // Acquisition is finished, pass along event so things shut down properly
                    return event;
                }

                if (event.getMinimumStartTimeAbsolute() != null) {
                    nextWakeTime_ = event.getMinimumStartTimeAbsolute();
                }

                try {
                    core_.waitForSystem();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                ////////////////////////////////////
                ///////// Run autofocus ////////////
                ///////////////////////////////////

                // TODO: where should these come from? In diSPIM they appear to come from preferences,
                //  not settings...
                boolean doAutofocus = settings.autofocus().enabled();

                boolean autofocusAtT0 = false;
                // TODO: this is where they come from in diSPIM?
//                prefs_.getBoolean(org.micromanager.asidispim.Data.MyStrings.PanelNames.AUTOFOCUS.toString(),
//                      org.micromanager.asidispim.Data.Properties.Keys.PLUGIN_AUTOFOCUS_ACQBEFORESTART, false);
                boolean autofocusEveryStagePass = false;
                boolean autofocusEachNFrames = false;
                boolean autofocusChannel = false;

                // TODO: this is the diSPIM plugin's autofocus code, which needs to be reimplemented
                //   and translated.
//                if (acqSettings_.autofocus().enabled()) {
//                    // (Copied from diSPIM): Note that we will not autofocus as expected when using hardware
//                    // timing.  Seems OK, since hardware timing will result in short
//                    // acquisition times that do not need autofocus.
//                    if ( (autofocusAtT0 && timePoint == 0) || ( (timePoint > 0) &&
//                          (timePoint % autofocusEachNFrames == 0 ) ) ) {
//                        if (acqSettings.useChannels) {
//                            multiChannelPanel_.selectChannel(autofocusChannel);
//                        }
//                        if (sideActiveA) {
//                            if (acqSettings.usePathPresets) {
//                                controller_.setPathPreset(org.micromanager.asidispim.Data.Devices.Sides.A);
//                                // blocks until all devices done
//                            }
//                            org.micromanager.asidispim.Utils.AutofocusUtils.FocusResult score = autofocus_.runFocus(
//                                  this, org.micromanager.asidispim.Data.Devices.Sides.A, false,
//                                  sliceTiming_, false);
//                            updateCalibrationOffset(org.micromanager.asidispim.Data.Devices.Sides.A, score);
//                        }
//                        if (sideActiveB) {
//                            if (acqSettings.usePathPresets) {
//                                controller_.setPathPreset(org.micromanager.asidispim.Data.Devices.Sides.B);
//                                // blocks until all devices done
//                            }
//                            org.micromanager.asidispim.Utils.AutofocusUtils.FocusResult score = autofocus_.runFocus(
//                                  this, org.micromanager.asidispim.Data.Devices.Sides.B, false,
//                                  sliceTiming_, false);
//                            updateCalibrationOffset(org.micromanager.asidispim.Data.Devices.Sides.B, score);
//                        }
//                        // Restore settings of the controller
//                        controller_.prepareControllerForAquisition(acqSettings, extraChannelOffset_);
//                        if (acqSettings.useChannels && acqSettings.channelMode != org.micromanager.asidispim.Data.MultichannelModes.Keys.VOLUME) {
//                            controller_.setupHardwareChannelSwitching(acqSettings, hideErrors);
//                        }
//                    }
//                }

                if (isUsingPLC) {
                    // move between positions fast
                    scanSpeedX_ = 1.0;
                    scanAccelX_ = 1.0;
                    if (settings.stageScan().enabled() && settings.isUsingMultiplePositions()) {
                        final ASIXYStage xyStage = model_.devices().device("SampleXY");
                        scanSpeedX_ = xyStage.getSpeedX();
                        scanAccelX_ = xyStage.getAccelerationX();
                        xyStage.setSpeedX(origSpeedX_);
                        xyStage.setAccelerationX(origAccelX_);
                    }
                }
                return event;
            }

            @Override
            public void close() {

            }
        }, Acquisition.BEFORE_HARDWARE_HOOK);


//        final PLogicSCAPE finalController = controller;
//        currentAcquisition_.addHook(new AcquisitionHook() {
//            @Override
//            public AcquisitionEvent run(AcquisitionEvent event) {
//                System.out.println("After hardware hook");
//                // for stage scanning: restore speed and set up scan at new position
//                // non-multi-position situation is handled in prepareControllerForAcquisition instead
////                if (acqSettings_.stageScan().enabled() && acqSettings_.isUsingMultiplePositions()) {
////                    final ASIXYStage xyStage = model_.devices().getDevice("SampleXY");
////                    final Point2D.Double pos = xyStage.getXYPosition();
////                    xyStage.setSpeedX(scanSpeedX_);
////                    xyStage.setAccelerationX(scanAccelX_);
////                    System.out.println("AFTER_HARDWARE_HOOK trigger");
////                    finalController.prepareStageScanForAcquisition(pos.x, pos.y, acqSettings_);
/////                   finalController.triggerControllerStartAcquisition(acqSettings_.acquisitionMode(),
////                           acqSettings_.volumeSettings().firstView());
////                }
//                return event;
//            }
//
//            @Override
//            public void close() {
//
//            }
//        }, Acquisition.AFTER_HARDWARE_HOOK);

        final PLogicScape controllerInstance = controller_;
        // TODO This after camera hook is called after the camera has been readied to acquire a
        //  sequence. I assume we want to tell the Tiger to start sending TTLs etc here
        currentAcquisition_.addHook(new AcquisitionHook() {
            @Override
            public AcquisitionEvent run(AcquisitionEvent event) {
                // TODO: Cameras are now ready to receive triggers, so we can send (software) trigger
                //  to the tiger to tell it to start outputting TTLs

                // When the acquisition finishes, AcqEngJ runs every hook once more with the
                // finished event so they can shut down. No cameras are armed for it, so triggering
                // the controller here starts a scan whose frames nobody collects and leaves the
                // scanner RUNNING as teardown begins. The before-hardware hook above already
                // guards this; this one did not.
                if (event.isAcquisitionFinishedEvent()) {
                    return event;
                }

                if (isUsingPLC) {
                    if (settings.stageScan().enabled() && settings.isUsingMultiplePositions()) {
                        final ASIXYStage xyStage = model_.devices().device("SampleXY");
                        // Scan from the coordinate this event was generated for instead of reading
                        // the stage. The read only agrees with the target when a move was issued
                        // for this arm. When the same position repeats across time points no move
                        // is issued, the stage is still parked on the previous scan start, and
                        // centering on that subtracts half the scan distance again, so the window
                        // walks half a field every arm while plane counts stay exact. The 1.4
                        // plugin takes this value from the position list for the same reason.
                        // A hardware sequence arrives here as a wrapper event whose own
                        // coordinates and axis positions are null by construction. The constituent
                        // events keep theirs, so read the coordinate off the first of them.
                        AcquisitionEvent coordEvent = event;
                        final List<AcquisitionEvent> sequence = event.getSequence();
                        if (sequence != null && !sequence.isEmpty()) {
                            coordEvent = sequence.get(0);
                        }
                        final Double eventX = coordEvent.getXPosition();
                        final Double eventY = coordEvent.getYPosition();
                        if (eventX == null || eventY == null) {
                            // An exception thrown here is invisible. AcqEngJ leaves both cameras
                            // armed and the run stops with nothing in the log, so refuse through
                            // abort() rather than let a dereference escape the hook.
                            studio_.logs().logError("stage scan: acquisition event carried no XY "
                                    + "coordinate, aborting rather than scanning at an unknown "
                                    + "position");
                            currentAcquisition_.abort();
                            return event;
                        }
                        xyStage.setSpeedX(scanSpeedX_);
                        xyStage.setAccelerationX(scanAccelX_);
                        controllerInstance.prepareStageScanForAcquisition(
                                eventX, eventY, settings);
                        controllerInstance.triggerControllerStartAcquisition(settings.acquisitionMode());
                        return event;
                    }

                    // TODO: is this the best place to set state to idle?
                    ASIScanner scanner = model_.devices().device("IllumSlice");
                    // need to set to IDLE to re-arm for each z-stack
                    if (!settings.isUsingHardwareTimePoints()) {
                        if (scanner.getSPIMState().equals(ASIScanner.SPIMState.RUNNING)) {
                            scanner.setSPIMState(ASIScanner.SPIMState.IDLE);
                        }
                        // Stage scan leaves the scanner ARMED (not RUNNING) after each volume:
                        // triggerControllerStartAcquisition() sets the scanner to ARMED and lets the
                        // XY stage card drive it, whereas galvo/no-scan set it to RUNNING. getSPIMState()
                        // returns the cached last-set value instead of querying the controller, so that
                        // ARMED state persists into the next timepoint. Without resetting it, the IDLE
                        // guard below is false on timepoint 2+, the controller is never re-triggered, and
                        // the camera arms with no incoming TTLs until it times out (multi-timepoint stage
                        // scan otherwise stalls after the first timepoint).
                        if (scanner.getSPIMState().equals(ASIScanner.SPIMState.ARMED)) {
                            scanner.setSPIMState(ASIScanner.SPIMState.IDLE);
                        }
                    }

                    // NOTE: not sure why this is being triggered twice with only 1 camera; so we need guard
                    // TODO: enable 2 sided acquisition
                    if (scanner.getSPIMState().equals(ASIScanner.SPIMState.IDLE)) {
                        controllerInstance.triggerControllerStartAcquisition(settings.acquisitionMode());
                    }
                }
                return event;
            }

            @Override
            public void close() {

            }
        }, Acquisition.AFTER_CAMERA_HOOK);

        // A throw before finish() would leave the Acquisition started and never finished, so
        // abort with the exception, which ends it and still lets the exception reach the caller.
        try {
            currentAcquisition_.start();

            submitEvents(settings, cameraNames, baseFocusUm, separate, numTimePoints);

            // No more instructions (i.e. AcquisitionEvents); tell the acquisition to initiate shutdown
            // once everything finishes
            currentAcquisition_.finish();
        } catch (RuntimeException e) {
            currentAcquisition_.abort(e);
            throw e;
        }

        return true;
    }

    /**
     * Opens the acquisition's window, with the settings and position MMAcquisition would use.
     * MMAcquisition's own window is not used because its abort and pause buttons are never
     * unsubscribed after an LSM acquisition, so every window it opens stays in memory after it is
     * closed.
     *
     * @param summary the summary metadata the dataset was created with, which names its channels
     */
    private void createAcquisitionDisplay(final SummaryMetadata summary) {
        // before the window: closing the last window closes the store only if it is managed
        studio_.displays().manage(datastore_);

        // start from the settings of the last acquisition window that was closed
        final String profileKey = PropertyKey.ACQUISITION_DISPLAY_SETTINGS.key();
        final DisplaySettings remembered =
                studio_.displays().displaySettingsFromProfile(profileKey);
        final DisplaySettings.Builder builder = remembered != null
                ? remembered.copyBuilder()
                : studio_.displays().displaySettingsBuilder();
        final List<String> channelNames = summary.getChannelNameList();
        if (channelNames.size() == 1) {
            builder.colorModeGrayscale();
        } else if (channelNames.size() > 1) {
            builder.colorModeComposite();
        }
        for (int i = 0; i < channelNames.size(); i++) {
            builder.channel(i, RememberedDisplaySettings.loadChannel(studio_,
                    summary.getChannelGroup(), channelNames.get(i), null));
        }

        final DisplayWindow display =
                studio_.displays().createDisplay(datastore_, null, builder.build());
        display.setWindowPositionKey(DefaultDisplayManager.MDA_DISPLAY);
        display.setDisplaySettingsProfileKey(profileKey);
        // ahead of the display manager's listener at 100, which is the one that closes the store
        display.addListener(liveWindowCloseGuard_, 1);
    }

    /**
     * Builds and submits the event iterators for one dataset.
     *
     * @param settings the run snapshot
     * @param cameraNames the camera device names, in slot order
     * @param baseFocusUm the focus position channel offsets are applied to, or null
     * @param separate true if this dataset is one time point of a separate time point series
     * @param numTimePoints the time points this dataset holds
     */
    private void submitEvents(final ScapeAcquisitionSettings settings, final String[] cameraNames,
            final Double baseFocusUm, final boolean separate, final int numTimePoints) {

        ////////////  Create and submit acquisition events ////////////////////
        // Create iterators of acquisition events and submit them to the engine for execution
        // The engine will (try to) automatically iterate over the AcquisitionEvents of each
        // iterator, but not over multiple iterators. So there should be one iterator submitted for
        // each expected triggering of the Tiger controller.

        // TODO: execute any start-acquisition runnables

        // Loop 1: XY positions
        PositionList pl = positionList_;

        // never in separate mode: without a controller the hardware flag is not recomputed, so
        // it can be left set by earlier settings
        if (!separate && settings.isUsingHardwareTimePoints()) {
            AcquisitionEvent baseEvent = new AcquisitionEvent(currentAcquisition_);
            if (settings.channels().enabled()) {
                currentAcquisition_.submitEventIterator(
                        LightSheetEventAdapter.createTimelapseMultiChannelVolumeAcqEvents(
                                baseEvent.copy(), settings, cameraNames,
                                settings.channels().used(), null));
            } else {
                currentAcquisition_.submitEventIterator(
                        LightSheetEventAdapter.createTimelapseVolumeAcqEvents(
                                baseEvent.copy(), settings, cameraNames, null));
            }

        } else {

            final int numPositions = settings.isUsingMultiplePositions() ? pl.getNumberOfPositions() : 1;

            // Loop 1: Multiple time points
            for (int timeIndex = 0; timeIndex < numTimePoints; timeIndex++) {
                //System.out.println("time index: " + timeIndex);
                AcquisitionEvent baseEvent = new AcquisitionEvent(currentAcquisition_);
                if (baseFocusUm != null) {
                    baseEvent.setZ(null, baseFocusUm);
                }
                // in separate mode each dataset holds time index 0 and the series loop keeps the
                // schedule, so no time axis and no minimum start time
                if (!separate && settings.isUsingTimePoints()) {
                    baseEvent.setAxisPosition(LightSheetEventAdapter.TIME_AXIS, timeIndex);
                    baseEvent.setMinimumStartTime((long) (timeIndex * (settings.timePointIntervalSec() * 1000.0)));
                }
                // Loop 2: XY positions
                for (int positionIndex = 0; positionIndex < numPositions; positionIndex++) {
                    //System.out.println("pos index: " + positionIndex);
                    if (settings.isUsingMultiplePositions()) {
                        baseEvent.setAxisPosition(LightSheetEventAdapter.POSITION_AXIS, positionIndex);
                        // is this the best way to do stage movements with new acq engine?
                        MultiStagePosition position = pl.getPosition(positionIndex);
                        baseEvent.setX(position.getX());
                        baseEvent.setY(position.getY());
                    }
                    // TODO: what to do if multiple positions not defined: acquire at current stage position?
                    //  If yes, then nothing more to do here.

                    // Loop 3: Channels; Loop 4: Z slices
                    if (settings.channels().enabled()) {
                        if (settings.channels().mode() == ChannelMode.VOLUME) {
                            // software "Every Volume" multichannel submits ONE event
                            // iterator PER channel, so AcqEngJ flushes a SequenceEnd between
                            // channels (Engine.java:187) and the controller re-fires once per
                            // channel-volume, mirroring 1.4's per-channel loop and LSM's own
                            // per-timepoint loop. Submitting all channels in a single iterator lets
                            // AcqEngJ merge identical-preset channels into one sequence that fires the
                            // controller once, collapsing the channel dimension (hang on hardware,
                            // silent wrong data in demo).
                            final var used = settings.channels().used();
                            for (int channelIndex = 0; channelIndex < used.length; channelIndex++) {
                                currentAcquisition_.submitEventIterator(
                                        LightSheetEventAdapter.createSingleChannelVolumeAcqEvents(
                                                baseEvent.copy(), settings, cameraNames, null,
                                                channelIndex, used[channelIndex], used.length));
                            }
                        } else {
                            // SLICE_HW with hardware timepoints off: the controller emits all
                            // channels within one armed run, so this submits a single iterator
                            // carrying no per-channel presets. Stamping a preset per channel made
                            // AcqEngJ split the merge and start one more camera sequence than the
                            // controller was armed to deliver, which then waited for frames that
                            // never arrived.
                            // VOLUME_HW reaches this branch too and is filed wrong when it does:
                            // it switches channel once per volume, so it needs the channel axis
                            // outside the z axis rather than innermost. The channel panel steers
                            // users away from it but does not write the corrected mode back to the
                            // settings, so a stored profile or an API caller still arrives here.
                            // Nothing refuses it on this path yet.
                            currentAcquisition_.submitEventIterator(
                                    LightSheetEventAdapter.createChannelPerSliceAcqEvents(
                                            baseEvent.copy(), settings, cameraNames,
                                            settings.channels().used(), null));
                        }
                    } else {
                        currentAcquisition_.submitEventIterator(
                                LightSheetEventAdapter.createAcqEvents(
                                        baseEvent.copy(), settings, cameraNames, null));
                    }
                }
            }

        }
    }

    /**
     * Runs one acquisition per time point, each into its own dataset. The controller is armed
     * once for the whole series, so this loop never touches it.
     *
     * <p>Time point t is due at start + t * interval on a monotonic clock, so a late time point
     * does not delay the later ones: they run back to back until the schedule catches up.
     *
     * <p>Any abort or failure ends the series. There is no camera timeout, so a starved time
     * point hangs the series.
     *
     * @return true if at least one dataset was completed
     */
    private boolean runSeparateTimePoints(final ScapeAcquisitionSettings settings,
            final String[] cameraNames, final Double baseFocusUm, final String settingsJson,
            final String saveDir, final String saveName) {

        final int numTimePoints = settings.numTimePoints();
        final long intervalMs = Math.round(settings.timePointIntervalSec() * 1000.0);

        final String seriesDir = createSeriesDirectory(saveDir, saveName);
        if (seriesDir == null) {
            return false; // early exit => nowhere to write
        }

        final long seriesStartNs = System.nanoTime();
        // directory names of the completed datasets
        final List<String> datasetNames = new ArrayList<>();

        // the last dataset's window stays open until the next time point starts, so a series
        // that stops in the wait still shows it
        Datastore previousStore = null;
        // the time point not yet accounted for, so an exception still marks its dataset
        int inFlightIndex = -1;
        try {
            for (int timeIndex = 0; timeIndex < numTimePoints; timeIndex++) {
                if (!awaitTimePointSlot(seriesStartNs, timeIndex, intervalMs)) {
                    break; // stop requested during the wait
                }
                if (!hasRoomForOneTimePoint(seriesDir, settings, cameraNames, timeIndex)) {
                    break; // not enough free space
                }
                // set before the start, which can throw after its dataset directory exists
                inFlightIndex = timeIndex;
                // Locale.ROOT: a locale with its own digits would put them in the directory name
                if (!startTimePointAcquisition(settings, cameraNames, baseFocusUm, settingsJson,
                        seriesDir, String.format(Locale.ROOT, "%s_%04d", saveName, timeIndex),
                        timeIndex)) {
                    break; // nothing started, so nothing to wait for
                }
                if (previousStore != null) {
                    closeDisplaysOnInterfaceThread(previousStore, timeIndex - 1);
                    previousStore = null;
                }
                // returns once the store is frozen, so the dataset is complete on disk
                currentAcquisition_.waitForCompletion();

                final String datasetDir = datastore_.getSavePath();
                previousStore = datastore_;
                final boolean keepGoing =
                        finishTimePoint(settings, timeIndex, datasetDir, datasetNames);
                inFlightIndex = -1; // accounted for by finishTimePoint
                if (!keepGoing) {
                    break;
                }
            }
        } finally {
            // no save path means no directory was created, so there is nothing to mark
            final String inFlightDir = datastore_ == null ? null : datastore_.getSavePath();
            if (inFlightIndex >= 0 && inFlightDir != null) {
                writeIncompleteMarker(inFlightDir, inFlightIndex, "failed");
            }
            final int completed = datasetNames.size();
            studio_.logs().logMessage("separate time points: " + completed + " of " + numTimePoints
                    + " datasets complete in " + seriesDir);
        }
        return !datasetNames.isEmpty();
    }

    /**
     * Sleeps until the time point is due, in short steps so a Stop is acted on promptly.
     *
     * @return false if the series should stop instead of running this time point
     */
    private boolean awaitTimePointSlot(final long seriesStartNs, final int timeIndex,
            final long intervalMs) {
        final long dueNs = seriesStartNs + timeIndex * intervalMs * 1_000_000L;
        long remainingMs = (dueNs - System.nanoTime()) / 1_000_000L;
        if (remainingMs < 0 && timeIndex > 0) {
            studio_.logs().logMessage("separate time points: time point " + timeIndex
                    + " is starting " + (-remainingMs) + " ms late");
        }
        setAwaitingTimePoint(true);
        try {
            while (remainingMs > 0) {
                if (isStopRequested()) {
                    return false;
                }
                // MM's countdown compares this against its own monotonic clock in milliseconds
                nextWakeTime_ = System.nanoTime() / 1_000_000L + remainingMs;
                try {
                    Thread.sleep(Math.min(250L, remainingMs));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                remainingMs = (dueNs - System.nanoTime()) / 1_000_000L;
            }
            return !isStopRequested();
        } finally {
            setAwaitingTimePoint(false);
        }
    }

    /**
     * Checks that the disk can take one more dataset before it is opened, so a full disk ends the
     * series with one message instead of failing every remaining time point.
     *
     * @return true if the next dataset can be written in full
     */
    private boolean hasRoomForOneTimePoint(final String seriesDir,
            final ScapeAcquisitionSettings settings, final String[] cameraNames,
            final int timeIndex) {
        final long usable = new File(seriesDir).getUsableSpace();
        final long payload = estimateTimePointBytes(settings, cameraNames);
        final long reservation =
                settings.saveMode() == SaveMode.ND_TIFF ? NDTIFF_FILE_RESERVATION_BYTES : 0L;
        if (usable >= payload + reservation) {
            return true;
        }
        model_.logging().reportError("Not enough free space for time point " + timeIndex + ": "
                + (usable >> 20) + " MB free, about " + ((payload + reservation) >> 20)
                + " MB needed. The series stopped here; earlier time points are complete on disk.");
        return false;
    }

    /**
     * Estimates the bytes one time point writes, from the frame size and the image count.
     */
    private long estimateTimePointBytes(final ScapeAcquisitionSettings settings,
            final String[] cameraNames) {
        final long frameBytes =
                core_.getImageWidth() * core_.getImageHeight() * core_.getBytesPerPixel();
        final long channels = settings.channels().enabled()
                ? Math.max(1, settings.channels().count()) : 1L;
        final long positions = settings.isUsingMultiplePositions()
                ? Math.max(1, positionList_.getNumberOfPositions()) : 1L;
        return frameBytes * settings.volume().slicesPerView() * channels * positions
                * cameraNames.length;
    }

    /**
     * Creates the folder the series' datasets are written into and returns its path, or null.
     * An existing folder is never reused: a fresh one keeps MMAcquisition's name counter at one,
     * so the datasets are named saveName_0000_1, saveName_0001_1 and so on.
     */
    private String createSeriesDirectory(final String saveDir, final String saveName) {
        final String seriesDir = FileUtils.createUniquePath(saveDir, saveName);
        if (!new File(seriesDir).mkdirs()) {
            model_.logging().reportError("Could not create the series directory:\n\n" + seriesDir);
            return null;
        }
        if (!seriesDir.equals(saveDir + File.separator + saveName)) {
            studio_.logs().logMessage("A folder named " + saveName + " already exists in "
                    + saveDir + "; this series is in " + seriesDir);
        }
        return seriesDir;
    }

    /**
     * Ends one time point of a series, after waitForCompletion(), and says whether the series
     * should go on. Records the dataset as complete or marks it incomplete, and leaves its window
     * for the caller to close.
     *
     * @param settings the run snapshot
     * @param timeIndex the time point that just ended
     * @param datasetDir where this time point was written
     * @param datasetNames the completed datasets, appended to when this one is whole
     * @return true if the next time point should run
     */
    private boolean finishTimePoint(final ScapeAcquisitionSettings settings, final int timeIndex,
            final String datasetDir, final List<String> datasetNames) {

        // check both: some aborts set only the flag, others reach only this Acquisition
        final boolean aborted = currentAcquisition_.isAbortRequested();
        boolean keepGoing = !aborted && !isStopRequested();
        if (!keepGoing) {
            studio_.logs().logMessage("separate time points: the series stopped at time point "
                    + timeIndex);
        }
        // null while the dataset is whole; a stop after it finished ends the series but does not
        // make it incomplete
        String incompleteReason = aborted ? "aborted" : null;
        try {
            currentAcquisition_.checkForExceptions();
        } catch (Exception e) {
            // shown, not only logged, so a long series that stops unattended says why
            model_.logging().reportError(e, "The acquisition failed at time point " + timeIndex
                    + " and the series stopped there. Earlier time points are complete on disk.");
            incompleteReason = "failed";
            keepGoing = false;
        }

        // an incomplete dataset is marked inside its folder, so it is not mistaken for a whole one
        if (incompleteReason == null) {
            datasetNames.add(new File(datasetDir).getName());
        } else {
            writeIncompleteMarker(datasetDir, timeIndex, incompleteReason);
        }

        // stage scan only: let the stage finish retracing before the next time point moves it.
        // finish() does this after the last one.
        if (keepGoing && settings.stageScan().enabled() && model_.devices().isUsingPLogic()
                && controller_ != null) {
            controller_.stopSPIMStateMachines();
        }

        // released before the caller closes the window, or the close guard would treat it as the
        // live store and ask to abort
        datastore_ = null;
        return keepGoing;
    }

    /**
     * Marks a dataset directory whose time point did not finish.
     *
     * @param datasetDir the directory the incomplete dataset was written to
     * @param timeIndex the time point that did not finish
     * @param reason what ended it
     */
    private void writeIncompleteMarker(final String datasetDir, final int timeIndex,
            final String reason) {
        final Map<String, Object> marker = new LinkedHashMap<>();
        marker.put("complete", false);
        marker.put("timePointIndex", timeIndex);
        marker.put("reason", reason);
        marker.put("writtenEpochMs", System.currentTimeMillis());
        FileUtils.writeStringToFile(datasetDir + File.separator + "incomplete.json",
                new GsonBuilder().setPrettyPrinting().create().toJson(marker));
    }

    /**
     * Closes a finished time point's window. The store is managed, so this also closes the store
     * and releases the dataset's files.
     */
    private void closeDisplaysOnInterfaceThread(final Datastore store, final int timeIndex) {
        try {
            SwingUtilities.invokeAndWait(() -> {
                studio_.logs().logMessage("separate time points: closing the window for time "
                        + "point " + timeIndex);
                if (!studio_.displays().closeDisplaysFor(store)) {
                    studio_.logs().logError("The window for time point " + timeIndex
                            + " refused to close");
                }
            });
        } catch (Exception e) {
            studio_.logs().logError(e, "Could not close the window for time point " + timeIndex);
        }
    }

    @Override
    void finish() {
        // finish() runs on EVERY path (the requestRun finally), even a setup-abort where the
        // acquisition never started. That splits its work into two jobs. Keep them grouped:
        //
        //   Job A: restore hardware/system state. Must ALWAYS run: setup() can mutate hardware
        //          before it fails, so each step self-guards on whether its state was changed.
        //   Job B: end-of-acquisition work. Only valid if the run actually happened, so each step
        //          self-guards on that (don't, e.g., report on an acquisition that never started).
        //
        // Adding a step? Pick its job: Job A runs unconditionally, Job B only when the run happened.
        // Don't interleave them.

        final CameraBase[] cameras = model_.devices().imagingCameras();

        // --- Job A ---

        // Stop all cameras sequences
        try {
            for (CameraBase camera : cameras) {
                if (core_.isSequenceRunning(camera.getDeviceName())) {
                    core_.stopSequenceAcquisition(camera.getDeviceName());
                }
            }
        } catch (Exception e) {
            studio_.logs().logError("Could not stop camera sequences: " + e.getMessage());
        }

        // clean up controller settings after acquisition
        // want to do this, even with demo cameras, so we can test everything else
        // TODO: figure out if we really want to return piezos to 0 position (maybe center position,
        //   maybe not at all since we move when we switch to setup tab, something else??)
        if (model_.devices().isUsingPLogic() && controller_ != null) {
            // SCAPE: leave the imaging piezo where it is instead of driving it to 0. diSPIM 1.4
            // passes centerPiezos=false for SCOPE (AcquisitionPanel line 4979). Parking at 0
            // defocuses the live view and re-introduces the first-frame jump on the next run
            // (issues #404 symptom 3 / #407).
            controller_.cleanUpControllerAfterAcquisition(acqSettings_, false);
            controller_.stopSPIMStateMachines();
        }

        // if we did stage scanning restore the original position and speed
        if (acqSettings_.stageScan().enabled()) {
            final ASIXYStage xyStage = model_.devices().device("SampleXY");
            if (xyStage == null) {
                // setup() can return before its own stage checks, such as a save location refusal
                studio_.logs().logError("Could not restore the stage: no SampleXY device");
            } else {
                final boolean returnToOriginalPosition =
                        acqSettings_.stageScan().returnToStart();

                // make sure stage scanning state machine is stopped,
                // otherwise setting speed/position won't take
                xyStage.setScanState(ASIXYStage.ScanState.IDLE);
                xyStage.setSpeedX(origSpeedX_);
                xyStage.setAccelerationX(origAccelX_);

                // xyPosUm_ is null when setup() returned before it captured a position
                if (returnToOriginalPosition && xyPosUm_ != null) {
                    xyStage.setXYPosition(xyPosUm_.x, xyPosUm_.y);
                }
            }
        }

        // Restore shutter/autoshutter to original state; null means this run never captured it
        if (shutterState_ != null) {
            try {
                core_.setShutterOpen(shutterState_.isOpen);
                core_.setAutoShutter(shutterState_.autoShutter);
            } catch (Exception e) {
                studio_.logs().logError("Couldn't restore shutter to original state");
            } finally {
                shutterState_ = null;
            }
        }

        // set the camera trigger modes back to internal for live mode
        if (savedExposures_.size() == cameras.length) {
            for (int i = 0; i < cameras.length; i++) {
                CameraBase camera = cameras[i];
                camera.setTriggerMode(CameraMode.INTERNAL);
                camera.setExposure(savedExposures_.get(i));
            }
        }

        // put back the Core-Camera and channel preset setup() found
        if (originalCoreCamera_ != null) {
            try {
                core_.setCameraDevice(originalCoreCamera_);
            } catch (Exception e) {
                studio_.logs().logError("Could not restore Core-Camera to " + originalCoreCamera_);
            } finally {
                originalCoreCamera_ = null;
            }
        }
        // an empty preset means the group matched none of its presets, so there is nothing to set
        if (originalChannelPreset_ != null && !originalChannelPreset_.isEmpty()) {
            final String group = acqSettings_.channels().group();
            try {
                core_.setConfig(group, originalChannelPreset_);
            } catch (Exception e) {
                studio_.logs().logError("Could not restore channel group " + group
                        + " to " + originalChannelPreset_);
            }
        }
        originalChannelPreset_ = null;

        // unregister to stop ghost events
        studio_.events().unregisterForEvents(this);

        // start polling for navigation panel
        if (isPolling_) {
            studio_.logs().logMessage("started position polling after acquisition");
            model_.positions().startPolling();
        }

        // --- Job B ---

        // check if acquisition ended due to an exception and show error
        // currentAcquisition_ can be null if an error occurred during setup
        if (currentAcquisition_ != null) {
            try {
                currentAcquisition_.checkForExceptions();
            } catch (Exception e) {
                studio_.logs().logError(e);
            }
        }

        // TODO: execute any end-acquisition runnables

        // Nothing to save here. Images are written during the run when saving is enabled, and a
        // run acquired without saving is held in memory and discarded, which is what 1.4 does.
        // The unsaved case still offers the data: MM prompts to save when its window is closed,
        // because StorageRAM leaves the datastore save path unset.
    }

    private boolean doHardwareCalculations(PLogicScape plc) {

        // TODO: find a better place to set the camera trigger mode for SCAPE
        CameraBase[] cameras = model_.devices().imagingCameras();
        for (CameraBase camera : cameras) {
            camera.setTriggerMode(acqSettings_.cameraMode());
            studio_.logs().logMessage("camera \"" + camera.getDeviceName()
                 + "\" requested mode: " + camera.getTriggerMode());
        }

        // make sure slice timings are up-to-date
        recalculateSliceTiming();

        // TODO: was only checked in light sheet mode
//        if (core_.getPixelSizeUm() < 1e-6) {
//            studio_.logs().showError("Need to set the pixel size in Micro-Manager.");
//        }

        // setup channels
        int nrChannelsSoftware = acqSettings_.channels().count();  // how many times we trigger the controller per stack
        int nrSlicesSoftware = acqSettings_.volume().slicesPerView();
        //acqSettings_.volumeSettings().slicesPerView();
        // TODO: channels need to modify panels and need extraChannelOffset_
        boolean changeChannelPerVolumeSoftware = false;
        boolean changeChannelPerVolumeDoneFirst = false;
        if (acqSettings_.channels().enabled()) {
            switch (acqSettings_.channels().mode()) {
                case VOLUME:
                    changeChannelPerVolumeSoftware = true;
                    changeChannelPerVolumeDoneFirst = true;
                    break;
                case VOLUME_HW:
                    if (acqSettings_.channels().count() > 1) {
                        // The controller switches channel once per volume here, so it delivers every
                        // slice of one channel before starting the next. The event stream built for
                        // this geometry puts the channel axis innermost, which is the interleaved
                        // order, so the counts match, nothing fails, and nearly every frame is filed
                        // against the wrong channel and slice. Refuse rather than record that.
                        // Refusing here also covers the combination with hardware time points, which
                        // 1.4 rejects separately: both drive the controller's repeat counter, and
                        // hardware time points overwrite the repeat count this mode depends on.
                        // The channel panel steers away from this mode but does not write the
                        // correction back to the settings, so it still arrives here from a stored
                        // profile or through the API.
                        studio_.logs().showError("Channel mode \"" + ChannelMode.VOLUME_HW
                                + "\" is not supported: images would be saved against the wrong "
                                + "channel and slice. Use \"" + ChannelMode.SLICE_HW
                                + "\" for hardware channel switching, or \"" + ChannelMode.VOLUME
                                + "\" to switch channels in software.");
                        return false; // early exit
                    }
                    // one channel needs no hardware switching at all, so it behaves like the
                    // single channel case below and cannot be misordered
                    break;
                case SLICE_HW:
                    if (acqSettings_.channels().count() == 1) {
                        // only 1 channel selected so don't have to really use hardware switching
                        //multiChannelPanel_.initializeChannelCycle();
                        //extraChannelOffset_ = multiChannelPanel_.selectNextChannelAndGetOffset();
                    } else {
                        // we have at least 2 channels
                        // intentionally leave extraChannelOffset_ untouched so that it can be specified by user by choosing a preset
                        //   for the channel in the main Micro-Manager window
                        final boolean success = plc.setupHardwareChannelSwitching(acqSettings_);
                        if (!success) {
                            studio_.logs().showError("Couldn't set up slice hardware channel switching.");
                            return false; // early exit
                        }
                        nrChannelsSoftware = 1;
                        nrSlicesSoftware = acqSettings_.volume().slicesPerView() * acqSettings_.channels().count();
                    }
                    break;
                default:
                    studio_.logs().showError(
                            "Unsupported multichannel mode \"" + acqSettings_.channels().mode().toString() + "\"");
                    return false; // early exit
            }
        }
//        // TODO: add code to check if cameras are active
//        if (model_.devices().getDeviceAdapter().getNumSimultaneousCameras() > 1) {
//            nrSlicesSoftware *= 2;
//        }

        // 1.4 adjusts nrSlicesSoftware at this point when hardware timepoints are in use: one
        // controller trigger covers every timepoint, so the camera sequence has to be sized for
        // the whole burst rather than one volume. The counterpart here is the event stream itself,
        // since AcqEngJ sizes the sequence from however many events it merges, and the hardware
        // timepoint factories carry the whole burst for exactly that reason. CameraTriggers holds
        // the arithmetic both that stream and the controller are built from. nrSlicesSoftware
        // stays dead: it is assigned above and never read.

        // TODO: make this more robust, should this be the first imaging camera?
        String cameraName;
        if (model_.devices().adapter().numSimultaneousCameras() > 1) {
           cameraName = "ImagingCamera1";
        } else {
           cameraName = "ImagingCamera";
        }

        // TODO: maybe wrap this up into a method for clarity
        double cameraReadoutTime;
        final CameraLibrary cameraLibrary = CameraLibrary.fromString(
                model_.devices().device(cameraName).getDeviceLibrary());
        switch (cameraLibrary) {
            case HAMAMATSU: {
                HamamatsuCamera camera = model_.devices().device(cameraName);
                cameraReadoutTime = camera.getReadoutTime(acqSettings_.cameraMode());
                break;
            }
            case PVCAM: {
                PvCamera camera = model_.devices().device(cameraName);
                cameraReadoutTime = camera.getReadoutTime(acqSettings_.cameraMode());
                break;
            }
            case PCOCAMERA: {
                PcoCamera camera = model_.devices().device(cameraName);
                cameraReadoutTime = camera.getReadoutTime(acqSettings_.cameraMode());
                break;
            }
            case ANDORSDK3: {
                AndorCamera camera = model_.devices().device(cameraName);
                cameraReadoutTime = camera.getReadoutTime(acqSettings_.cameraMode());
                break;
            }
            case DEMOCAMERA: {
                DemoCamera camera = model_.devices().device(cameraName);
                cameraReadoutTime = camera.getReadoutTime(acqSettings_.cameraMode());
                break;
            }
            default:
                CameraBase camera = model_.devices().device(cameraName);
                cameraReadoutTime = camera.getReadoutTime(acqSettings_.cameraMode());
                break;
        }
        final double exposureTime = acqSettings_.timing().cameraExposureMs();

        // test acq was here

        final double volumeDuration = computeVolumeDuration();
        final double timepointDuration = computeTimePointDuration();
        final long timepointIntervalMs = Math.round(acqSettings_.timePointIntervalSec() * 1000.0);

        // use hardware timing if < 1 second between time points
        // experimentally need ~0.5 sec to set up acquisition, this gives a bit of cushion
        // cannot do this in getCurrentAcquisitionSettings because of mutually recursive
        // call with computeVolumeDuration()
        // reset the flag every run and recompute below
        asb_.useHardwareTimePoints(false);
        boolean isUsingHardwareTimePoints = false; // TODO: asb_ not built yet

        // Separate time points never uses hardware time points, which would run the whole series
        // on one trigger. Decided here because this recompute turns the hardware flag on by
        // itself when the interval is short.
        final boolean separateTimePoints = isSeparatingTimePoints(acqSettings_);

        if (acqSettings_.isUsingTimePoints()
                && !separateTimePoints
                && acqSettings_.numTimePoints() > 1
                && timepointIntervalMs < (timepointDuration + 750)
                && !acqSettings_.stageScan().enabled()) {
            asb_.useHardwareTimePoints(true);
            isUsingHardwareTimePoints = true;
        }

        // TODO: implement multiple positions using hardware time points, currently
        //  set hardware time points to false if using multiple positions. The 1.4 plugin does
        //  not support this combination either, so it is new capability rather than a gap in
        //  the port.
        if (acqSettings_.isUsingMultiplePositions()) {
            if (isUsingHardwareTimePoints
                    || (numTimePointsToAcquire(acqSettings_) > 1
                        && timepointIntervalMs < timepointDuration * 1.2)) {
                // warn the user but allow the acquisition to continue
                asb_.useHardwareTimePoints(false);
                isUsingHardwareTimePoints = false;
                model_.logging().reportError("Time point interval may not be sufficient "
                        + "depending on actual time required to change positions. "
                        + "Proceed at your own risk.");
            }
        }

        // only use hardware time points when use time points is checked
        if (isUsingHardwareTimePoints) {
            if (!acqSettings_.isUsingTimePoints()) {
                asb_.useHardwareTimePoints(false);
                isUsingHardwareTimePoints = false;
            }
        }

        final double sliceDurationMs = asb_.timingBuilder().sliceDurationMs();
        if (exposureTime + cameraReadoutTime > sliceDurationMs) {
            // should only possible to mess this up using advanced timing settings
            // or if there are errors in our own calculations
            studio_.logs().showError("Exposure time of " + exposureTime +
                    " is longer than time needed for a line scan with" +
                    " readout time of " + cameraReadoutTime + "\n" +
                    "This will result in dropped frames. " +
                    "Please change input. " +
                    "Formula: (" + exposureTime + " + " + cameraReadoutTime + ") > " + sliceDurationMs);
            return false; // early exit
        }

        // must use PLogic for channels when using hardware time points
        if (isUsingHardwareTimePoints) {
            // name the actual threshold so the user knows how much to raise the interval; round up to
            // 0.1 s so the suggested value clears the threshold (interval < timepointDuration + 750 ms)
            final double minIntervalSec = Math.ceil((timepointDuration + 750.0) / 100.0) / 10.0;
            if (acqSettings_.channels().enabled() && acqSettings_.channels().mode() == ChannelMode.VOLUME) {
                studio_.logs().showError("Time point interval is too short for software (\"Every Volume\") "
                        + "channels: intervals under about " + minIntervalSec + " s switch to hardware time "
                        + "points, which require hardware (PLogic) channel switching. Either raise the time "
                        + "point interval to at least " + minIntervalSec + " s, or set the channel mode to "
                        + "\"Every Slice (PLogic)\".");
                return false;
            }
            if (acqSettings_.stageScan().enabled()) {
                // stage scanning needs to be triggered for each time point
                studio_.logs().showError("Time point interval is too short: intervals under about "
                        + minIntervalSec + " s switch to hardware time points, which can't be combined with "
                        + "stage scanning. Raise the time point interval to at least " + minIntervalSec + " s.");
                return false;
            }
            if (CameraTriggers.hasSurplusFrames(acqSettings_)) {
                // Overlap mode delivers more images than the dataset wants, and the extra ones
                // arrive at the end of every volume rather than at the end of the run. Keeping the
                // events lined up with the frames means consuming those images and discarding them
                // afterwards, and no mechanism for discarding one exists yet. Refuse here rather
                // than file them as the next time point's first slices and shift everything after.
                studio_.logs().showError("Time point interval is too short: intervals under about "
                        + minIntervalSec + " s switch to hardware time points, which can't yet be combined "
                        + "with the \"Overlap/Synchronous\" camera mode. Either raise the time point "
                        + "interval to at least " + minIntervalSec + " s, or choose a different camera mode.");
                return false;
            }
        }

        final int numTimePoints = numTimePointsToAcquire(acqSettings_);
        if (!acqSettings_.isUsingMultiplePositions() && numTimePoints > 1) {
            if (timepointIntervalMs < volumeDuration) {
                studio_.logs().showError("Time point interval shorter than the time to collect a single volume.");
                return false;
            }
        }

        // warn and continue: in separate mode a short interval makes time points late rather
        // than losing data. below the refusals so a refused run shows only the refusal
        if (separateTimePoints
                && acqSettings_.numTimePoints() > 1
                && timepointIntervalMs < (timepointDuration + 750)) {
            model_.logging().reportError("The time point interval is close to the time one time "
                    + "point takes to acquire, leaving little or no time to open and close its "
                    + "dataset. Time points will run late and the schedule will catch up rather "
                    + "than skip. Proceed at your own risk.");
        }

        // set exposure for imaging camera
        for (CameraBase camera : cameras) {
           camera.setExposure(exposureTime);
        }

        // FREEZE POINT: rebuild the run-time snapshot from asb_ before arming the controller.
        // run() reaches this method (via doHardwareCalculations) BEFORE it rebuilds acqSettings_ with
        // updateSettings(). Without this rebuild the controller is armed from a stale acqSettings_, e.g.
        // useHardwareTimePoints left true by a prior short-interval reject, while the AcqEngJ event loop
        // later runs from the fresh (false) value, so the hardware is armed for hardware timepoints while
        // the software issues software timepoints ("stopped at first timepoint"). Rebuilding here is
        // strictly fresher: doHardwareCalculations only mutates asb_ (the flag reset above + recalculated
        // timing), never acqSettings_ directly.
        // TODO(IMMUTABLE-RUN): remove once acqSettings_ is the single run-time source of truth.
        updateSettings();

        double extraChannelOffset = 0.0;
        return plc.prepareControllerForAcquisition(acqSettings_, extraChannelOffset);
    }

    private void doHardwareCalculationsNIDAQ() {
        NIDAQ daq = model_.devices().device("TriggerCamera");
        //daq.setProperty("PropertyName", "1");
    }

    @Override
    public void recalculateSliceTiming() {
        // update timing settings if not using advanced timing
        if (!acqSettings_.isUsingAdvancedTiming()) {
            asb_.timingBuilder(getTimingFromExposure());
        }
        // Note: sliceDurationMs is computed automatically when build() is called
        //final double sliceDurationMs = getSliceDuration(asb_.timingSettingsBuilder().build());
        //asb_.timingSettingsBuilder().sliceDurationMs(sliceDurationMs);
        //System.out.println(asb_.timingSettingsBuilder());
    }

    /**
     * Single objective timing settings.
     *
     * @return a builder for DefaultTimingSettings
     */
    public DefaultTimingSettings.Builder getTimingFromExposure() {
        // temporary measure: use diSPIM-like settings unless we are doing stage scanning
        if (!acqSettings_.stageScan().enabled()) {
           return getTimingFromPeriodAndLightExposure();
        }

        final CameraBase camera = model_.devices().firstImagingCamera();
        final CameraMode cameraMode = acqSettings_.cameraMode();

        final double cameraResetTime = camera.getResetTime(cameraMode);     // recalculate for safety, 0 for light sheet
        final double cameraReadoutTime = camera.getReadoutTime(cameraMode); // recalculate for safety, 0 for overlap

        final double cameraTotalTime = NumberUtils.ceilToQuarterMs(cameraResetTime + cameraReadoutTime);
        final double laserDuration = NumberUtils.roundToQuarterMs(
                model_.acquisitions().settings().slice().sampleExposure());
        // max of laser on time (for static light sheet) and total camera reset/readout time; will add excess later
        final double slicePeriodMin = Math.max(laserDuration, cameraTotalTime);
        final double sliceDeadTime = NumberUtils.roundToQuarterMs(slicePeriodMin - laserDuration);
        // extra quarter millisecond to make sure interleaved slices works (otherwise laser signal never goes low)
        final double sliceLaserInterleaved =
                (acqSettings_.channels().mode() == ChannelMode.SLICE_HW ? 0.25 : 0.0);

        // TODO: is this getting the correct value?
        final double actualCameraResetTime =
              camera.getDeviceName().equals(PvCamera.Models.PRIME_95B) ||
              camera.getDeviceName().equals(PvCamera.Models.KINETIX)
              ? camera.getPropertyFloat(PvCamera.Properties.READOUT_TIME) / 1e6 : cameraResetTime;

        // timing settings
        int scansPerSlice = 0;
        double scanDurationMs = 0.0;
        double cameraTriggerDurationMs = 0.0;
        double laserTriggerDurationMs = 0.0;
        double delayBeforeCameraMs = 0.0;
        double delayBeforeLaserMs = 0.0;
        double delayBeforeScanMs = 0.0;
        double cameraExposureMs = 0.0;

        DefaultTimingSettings.Builder tsb = DefaultTimingSettings.builder();
        switch (cameraMode) {
            case PSEUDO_OVERLAP: // e.g. Kinetix
                scansPerSlice = 1;
                scanDurationMs = 0.25;
                cameraExposureMs = laserDuration;
                laserTriggerDurationMs = laserDuration;
                cameraTriggerDurationMs = laserDuration;
                delayBeforeCameraMs = 0.25;
                delayBeforeLaserMs = sliceDeadTime;
                delayBeforeScanMs = 0.0;
                break;
            case OVERLAP: // e.g.
                if (acqSettings_.channels().enabled() && acqSettings_.channels().count() > 1
                        && acqSettings_.channels().mode() == ChannelMode.SLICE_HW) {
                    // for interleaved slices we should illuminate during global exposure but not during readout/reset time after each trigger
                    scansPerSlice = 1;
                    scanDurationMs = 1.0;
                    cameraExposureMs = 0.25;
                    laserTriggerDurationMs = laserDuration;
                    cameraTriggerDurationMs = 1.0;
                    delayBeforeCameraMs = 0.0;
                    delayBeforeLaserMs = sliceDeadTime + NumberUtils.ceilToQuarterMs(cameraResetTime);
                    delayBeforeScanMs = 0.0;
                } else {
                    // the usual case
                    scansPerSlice = 1;
                    scanDurationMs = 1.0;
                    cameraExposureMs = 0.25;
                    laserTriggerDurationMs = laserDuration;
                    cameraTriggerDurationMs = 1.0;
                    delayBeforeCameraMs = 0.0;
                    delayBeforeLaserMs = sliceDeadTime + sliceLaserInterleaved;
                    delayBeforeScanMs = 0.0;
                }
                break;
            case EDGE:
                // should illuminate during the entire exposure (or as much as needed) => will be exposing during camera reset and readout too
                // Note: that this may be faster than overlap for interleaved channels
                scansPerSlice = 1;
                scanDurationMs = 1.0;
                cameraExposureMs = laserDuration - NumberUtils.ceilToQuarterMs(actualCameraResetTime + cameraReadoutTime);
                laserTriggerDurationMs = laserDuration;
                cameraTriggerDurationMs = 1.0;
                delayBeforeCameraMs = sliceLaserInterleaved;
                delayBeforeLaserMs = sliceDeadTime + sliceLaserInterleaved;
                delayBeforeScanMs = 0.0;
                break;
            default:
                studio_.logs().showError("Invalid camera mode");
                break;
        }

        // sync with builder so that tsb.sliceDurationMs() is accurate
        tsb.scansPerSlice(scansPerSlice)
                .scanDurationMs(scanDurationMs)
                .cameraTriggerDurationMs(cameraTriggerDurationMs)
                .laserTriggerDurationMs(laserTriggerDurationMs)
                .delayBeforeScanMs(delayBeforeScanMs)
                .delayBeforeLaserMs(delayBeforeLaserMs)
                .delayBeforeCameraMs(delayBeforeCameraMs)
                .cameraExposureMs(cameraExposureMs);

        // if a specific slice period was requested, add corresponding delay to scan/laser/camera
        if (!acqSettings_.slice().periodMinimized()) {
            double globalDelay = acqSettings_.slice().period() - tsb.sliceDurationMs();
            // only true when user has specified period that is unattainable
            if (globalDelay < 0) {
                globalDelay = 0;
                studio_.logs().logDebugMessage("Increasing slice period to meet laser exposure constraint\n"
                        + "(time required for camera readout; readout time depends on ROI).");
            }
            delayBeforeCameraMs += globalDelay;
            delayBeforeLaserMs += globalDelay;
            delayBeforeScanMs += globalDelay;

            // sync with builder so that tsb.sliceDurationMs() is accurate
            tsb.delayBeforeScanMs(delayBeforeScanMs)
                    .delayBeforeLaserMs(delayBeforeLaserMs)
                    .delayBeforeCameraMs(delayBeforeCameraMs);
        }

        // fix corner case of (exposure time + readout time) being greater than the slice duration
        // most of the time the slice duration is already larger
        final double extraGlobalDelay = NumberUtils.ceilToQuarterMs(
                (cameraExposureMs + cameraReadoutTime) - tsb.sliceDurationMs());
        if (extraGlobalDelay > 0) {
            delayBeforeCameraMs += extraGlobalDelay;
            delayBeforeLaserMs += extraGlobalDelay;
            delayBeforeScanMs += extraGlobalDelay;
        }

        // final sync, only values updated are delays
        tsb.delayBeforeScanMs(delayBeforeScanMs)
                .delayBeforeLaserMs(delayBeforeLaserMs)
                .delayBeforeCameraMs(delayBeforeCameraMs);

        return tsb;
    }

    public DefaultTimingSettings.Builder getTimingFromPeriodAndLightExposure() {
        // uses algorithm Jon worked out in Octave code; each slice period goes like this:
        // 1. camera readout time (none if in overlap mode, 0.25ms in pseudo-overlap)
        // 2. any extra delay time
        // 3. camera reset time
        // 4. start scan 0.25ms before camera global exposure and shifted up in time to account for delay introduced by Bessel filter
        // 5. turn on laser as soon as camera global exposure, leave laser on for desired light exposure time
        // 7. end camera exposure in final 0.25ms, post-filter scan waveform also ends now

        CameraBase camera = model_.devices().firstImagingCamera(); //.getDevice("ImagingCamera");
        if (camera == null) {
            // just a dummy to test demo mode
            return DefaultTimingSettings.builder();
        }

        // settings are the source of truth for camera mode
        CameraMode camMode = acqSettings_.cameraMode();

        final double scanLaserBufferTime = NumberUtils.roundToQuarterMs(0.25);  // below assumed to be multiple of 0.25ms

        final double cameraResetTime = camera.getResetTime(camMode);      // recalculate for safety, 0 for light sheet
        final double cameraReadoutTime = camera.getReadoutTime(camMode);  // recalculate for safety, 0 for overlap

        final double cameraReadoutMax = NumberUtils.ceilToQuarterMs(cameraReadoutTime);
        final double cameraResetMax = NumberUtils.ceilToQuarterMs(cameraResetTime);

        // we will wait cameraReadoutMax before triggering camera, then wait another cameraResetMax for global exposure
        // this will also be in 0.25ms increment
        final double globalExposureDelayMax = cameraReadoutMax + cameraResetMax;
        double laserTriggerDurationMs = NumberUtils.roundToQuarterMs(acqSettings_.slice().sampleExposure());
        double scanDurationMs = laserTriggerDurationMs + 2*scanLaserBufferTime;
        // scan will be longer than laser by 0.25ms at both start and end

        // account for delay in scan position due to Bessel filter by starting the scan slightly earlier
        // than we otherwise would (Bessel filter selected b/c stretches out pulse without any ripples)
        // delay to start is (empirically) 0.07ms + 0.25/(freq in kHz)
        // delay to midpoint is empirically 0.38/(freq in kHz)
        // group delay for 5th-order Bessel filter ~0.39/freq from theory and ~0.4/freq from IC datasheet

        // Only read the scanner's Bessel-filter freq when PLogic is in use (mirrors the isUsingPLogic
        // gate below at the scanDelayFilter adjustment). Avoids device2() logging "IllumSlice not found"
        // on demo / non-PLogic configs. Note: a non-PLogic-but-real-scanner (NIDAQ) config falls back
        // to the 0.4 default here rather than reading the real freq. (Brandon 2026-07-24)
        double scanFilterFreq = 0.4; // default value
        if (model_.devices().isUsingPLogic()) {
            scanFilterFreq = model_.devices()
                    .device2("IllumSlice", ASIScanner.class)
                    .map(ASIScanner::getFilterFreqX)
                    .orElse(0.4);
        }

        double scanDelayFilter = 0;
        if (scanFilterFreq != 0) {
            scanDelayFilter = NumberUtils.roundToQuarterMs(0.39 / scanFilterFreq);
        }

        // If the PLogic card is used, account for 0.25ms delay it introduces to
        // the camera and laser trigger signals => subtract 0.25ms from the scanner delay
        // (start scanner 0.25ms later than it would be otherwise)
        // this time-shift opposes the Bessel filter delay
        // scanDelayFilter won't be negative unless scanFilterFreq is more than 3kHz which shouldn't happen
        if (model_.devices().isUsingPLogic()) {
            scanDelayFilter -= 0.25;
        }

        double delayBeforeScanMs = globalExposureDelayMax - scanLaserBufferTime   // start scan 0.25ms before camera's global exposure
                - scanDelayFilter; // start galvo moving early due to card's Bessel filter and delay of TTL signals via PLC
        double delayBeforeLaserMs = globalExposureDelayMax; // turn on laser as soon as camera's global exposure is reached
        double delayBeforeCameraMs = cameraReadoutMax; // camera must read out last frame before triggering again

        // figure out desired time for camera to be exposing (including reset time)
        // because both camera trigger and laser on occur on 0.25ms intervals (i.e. we may not
        //    trigger the laser until 0.24ms after global exposure) use cameraReset_max
        // special adjustment for Photometrics cameras that possibly has extra clear time which is counted in reset time
        //    but not in the camera exposure time
        // TODO: skipped PVCAM case, this should already be handled by camera.getResetTime(camMode); but there may be differences

        // make sure to accumulate values so our comparison logic works at the end of the method
        double cameraExposureMs = NumberUtils.ceilToQuarterMs(cameraResetTime) + laserTriggerDurationMs;

        double cameraTriggerDurationMs = 1.0; // a reasonable default

        // NOTE: tsb.sliceDurationMs() is needed in PSEUDO_OVERLAP camera mode.
        // cameraExposureMs will be modified in the switch, sliceDurationMs does not depend on it
        DefaultTimingSettings.Builder tsb = DefaultTimingSettings.builder();
        tsb.scansPerSlice(1)
                .scanDurationMs(scanDurationMs)
                .cameraTriggerDurationMs(cameraTriggerDurationMs)
                .laserTriggerDurationMs(laserTriggerDurationMs)
                .delayBeforeScanMs(delayBeforeScanMs)
                .delayBeforeLaserMs(delayBeforeLaserMs)
                .delayBeforeCameraMs(delayBeforeCameraMs)
                .cameraExposureMs(cameraExposureMs); // base exposure before camera mode specific adjustments

        final CameraMode cameraMode = acqSettings_.cameraMode();
        switch (cameraMode) {
            case EDGE:
                // cameraTriggerDurationMs: doesn't really matter, 1ms should be plenty fast yet easy to see for debugging
                cameraExposureMs += 0.1; // add 0.1ms as safety margin, may require adding 0.25ms to slice
                // slight delay between trigger and actual exposure start
                //   is included in exposure time for Hamamatsu and negligible for Andor and PCO cameras
                // ensure not to miss triggers by not being done with readout in time for next trigger, add 0.25ms if needed
                if (tsb.sliceDurationMs() < (cameraExposureMs + cameraReadoutTime)) {
                    delayBeforeCameraMs += 0.25;
                    delayBeforeLaserMs += 0.25;
                    delayBeforeScanMs += 0.25;
                }
                break;
            case LEVEL: // AKA "bulb mode", TTL rising starts exposure, TTL falling ends it
                cameraTriggerDurationMs = NumberUtils.ceilToQuarterMs(cameraExposureMs);
                cameraExposureMs = 1.0; // doesn't really matter, controlled by TTL
                break;
            case OVERLAP: // only Hamamatsu or Andor
                // cameraTriggerDurationMs: doesn't really matter, 1ms should be plenty fast yet easy to see for debugging
                cameraExposureMs = 1.0; // doesn't really matter, controlled by interval between triggers
                break;
            case PSEUDO_OVERLAP:// PCO or Photometrics, enforce 0.25ms between end exposure and start of next exposure by triggering camera 0.25ms into the slice
                // cameraTriggerDurationMs: doesn't really matter, 1ms should be plenty fast yet easy to see for debugging
                switch (CameraLibrary.fromString(camera.getDeviceLibrary())) {
                    case PVCAM:
                        // leave cameraExposureMs alone
                        break;
                    case PCOCAMERA:
                        cameraExposureMs = tsb.sliceDurationMs() - delayBeforeCameraMs;  // delayBeforeCameraMs should be 0.25ms for PCO
                        break;
                    default:
                        studio_.logs().showError("Unknown camera library for pseudo-overlap "
                                + "calculations: " + camera.getDeviceLibrary());
                        break;
                }
                if (cameraReadoutMax < 0.24) {
                    studio_.logs().showError("Camera delay should be at least 0.25ms for pseudo-overlap mode.");
                }
                break;
            default:
                studio_.logs().showError("Invalid camera mode");
                break;
        }

        // sync with builder so that tsb.sliceDurationMs() is accurate
        tsb.cameraTriggerDurationMs(cameraTriggerDurationMs)
                .delayBeforeScanMs(delayBeforeScanMs)
                .delayBeforeLaserMs(delayBeforeLaserMs)
                .delayBeforeCameraMs(delayBeforeCameraMs);

        // fix corner case of negative calculated scanDelay
        if (delayBeforeScanMs < 0) {
            delayBeforeCameraMs -= delayBeforeScanMs;
            delayBeforeLaserMs -= delayBeforeScanMs;
            delayBeforeScanMs = 0; // same as (-= delayBeforeScanMs)
            // sync with builder
            tsb.delayBeforeScanMs(delayBeforeScanMs)
                    .delayBeforeLaserMs(delayBeforeLaserMs)
                    .delayBeforeCameraMs(delayBeforeCameraMs);
        }

        // if a specific slice period was requested, add corresponding delay to scan/laser/camera
        if (!acqSettings_.slice().periodMinimized()) {
            double globalDelay = acqSettings_.slice().period() - tsb.sliceDurationMs();
            // only true when user has specified period that is unattainable
            if (globalDelay < 0) {
                globalDelay = 0;
                studio_.logs().logDebugMessage("Increasing slice period to meet laser exposure constraint\n"
                        + "(time required for camera readout; readout time depends on ROI).");
            }
            delayBeforeCameraMs += globalDelay;
            delayBeforeLaserMs += globalDelay;
            delayBeforeScanMs += globalDelay;

            // sync with builder so that tsb.sliceDurationMs() is accurate
            tsb.delayBeforeScanMs(delayBeforeScanMs)
                    .delayBeforeLaserMs(delayBeforeLaserMs)
                    .delayBeforeCameraMs(delayBeforeCameraMs);
        }

        // fix corner case of (exposure time + readout time) being greater than the slice duration
        // most of the time the slice duration is already larger
        final double extraGlobalDelay = NumberUtils.ceilToQuarterMs(
                (cameraExposureMs + cameraReadoutTime) - tsb.sliceDurationMs());
        if (extraGlobalDelay > 0) {
            delayBeforeCameraMs += extraGlobalDelay;
            delayBeforeLaserMs += extraGlobalDelay;
            delayBeforeScanMs += extraGlobalDelay;
        }

        tsb.scansPerSlice(1)
                .scanDurationMs(scanDurationMs)
                .cameraTriggerDurationMs(cameraTriggerDurationMs)
                .laserTriggerDurationMs(laserTriggerDurationMs)
                .delayBeforeScanMs(delayBeforeScanMs)
                .delayBeforeLaserMs(delayBeforeLaserMs)
                .delayBeforeCameraMs(delayBeforeCameraMs)
                .cameraExposureMs(cameraExposureMs);

        return tsb;
    }

    @Override
    public void updateDurationLabels() {
        // TODO(IMMUTABLE-RUN): sync the snapshot to the builder before recomputing; real fix = the
        // derive/arm split so the timing math reads one source.
        //
        // Every caller has just written the user's edit to the BUILDER, but the timing math reads
        // the frozen snapshot (getTimingFromExposure and getTimingFromPeriodAndLightExposure both
        // take sampleExposure, cameraMode, period and stageScan.enabled off acqSettings_). Without
        // this sync the recompute runs on the previous edit: for most controls that leaves the
        // labels one edit stale, and for the acquisition-mode dropdown it selects the wrong branch
        // outright, computing galvo timing for a stage-scan run. That pair is then frozen and the
        // next Run fails validation comparing one mode's exposure against the other's duration.
        model_.acquisitions().updateSettings();
        model_.acquisitions().recalculateSliceTiming();
        model_.acquisitions().updateSettings();
        // update durations now that settings are current
        updateSlicePeriodLabel(pnlDuration_.getSliceDurationLabel());
        updateVolumeDurationLabel(pnlDuration_.getVolumeDurationLabel());
        updateTotalTimeDurationLabel(pnlDuration_.getTotalDurationLabel());
    }

    private void updateSlicePeriodLabel(final JLabel label) {
        label.setText(NumberUtils.doubleToDisplayString(acqSettings_.timing().sliceDurationMs()) + " ms");
    }

    private void updateVolumeDurationLabel(final JLabel label) {
        final double duration = computeVolumeDuration();
        if (duration > 1000) {
            // round to ms
            label.setText(NumberUtils.doubleToDisplayString(duration / 1000) + " s");
        } else {
            // round to tenth of ms
            label.setText(NumberUtils.doubleToDisplayString((double)Math.round(10 * duration) / 10) + " ms");
        }
    }

    // TODO: Inherited rounding quirk, not a regression: Math.round(duration % 60) can produce "1 min 60 s"
    //   for e.g. duration = 119.7 s. diSPIM has the same behavior, so as a faithful port this is fine.
    /**
     * Update the displayed total time duration.
     */
    private void updateTotalTimeDurationLabel(final JLabel label) {
        final double duration = computeTotalTimeDuration();
        if (duration < 60) {  // less than 1 min
            label.setText(NumberUtils.doubleToDisplayString(duration) + " s");
        } else if (duration < (60*60)) { // between 1 min and 1 hour
            final String minutes = NumberUtils.doubleToDisplayString(Math.floor(duration/60)) + " min ";
            final String seconds = NumberUtils.doubleToDisplayString((double)Math.round(duration % 60)) + " s";
            label.setText(minutes + seconds);
        } else { // longer than 1 hour
            final String hours = NumberUtils.doubleToDisplayString(Math.floor(duration/(60*60))) + " hr ";
            final String minutes = NumberUtils.doubleToDisplayString((double)Math.round((duration % (60*60))/60)) + " min";
            label.setText(hours + minutes);
        }
    }

    private double computeTotalTimeDuration() {
        final int numTimePoints = numTimePointsToAcquire(acqSettings_);
        return (numTimePoints - 1) * acqSettings_.timePointIntervalSec() + computeTimePointDuration() / 1000.0;
    }

    /**
     * Compute the time point duration in ms. Only difference from computeVolumeDuration()
     * is that it also takes into account the multiple positions, if any.
     *
     * @return duration in ms
     */
    private double computeTimePointDuration() {
        final double volumeDuration = computeVolumeDuration();
        if (acqSettings_.isUsingMultiplePositions()) {
            try {
                // use 1.5 seconds motor move between positions
                // (could be wildly off but was estimated using actual system
                // and then slightly padded to be conservative to avoid errors
                // where positions aren't completed in time for next position)
                // could estimate the actual time by analyzing the position's relative locations
                //   and using the motor speed and acceleration time
                return studio_.positions().getPositionList().getNumberOfPositions() *
                        (volumeDuration + 1500 + acqSettings_.postMoveDelay());
            } catch (Exception e) {
                studio_.logs().logError("Error getting position list for multiple XY positions");
                return volumeDuration;
            }
        }
        return volumeDuration;
    }

    public double computeVolumeDuration() {
        final ChannelMode channelMode = acqSettings_.channels().mode();
        final int numViews = acqSettings_.volume().numViews();
        final int numChannels = acqSettings_.channels().count();
        final double delayBeforeView = acqSettings_.volume().delayBeforeView();

        int numCameraTriggers = acqSettings_.volume().slicesPerView();
        if (acqSettings_.cameraMode() == CameraMode.OVERLAP) {
            numCameraTriggers += 1;
        }

        // stackDuration is per-view, per-channel, per-position
        final double stackDuration = numCameraTriggers * acqSettings_.timing().sliceDurationMs();

        if (acqSettings_.stageScan().enabled()) {
            final double rampDuration = getStageRampDuration(acqSettings_);
            final double retraceTime = getStageRetraceDuration(acqSettings_);
            // TODO(Jon): double-check these calculations below, at least they are better than before ;-)
            if (acqSettings_.acquisitionMode() == AcquisitionMode.STAGE_SCAN) {
                if (channelMode == ChannelMode.SLICE_HW) {
                    return retraceTime + (numViews * ((rampDuration * 2) + (stackDuration * numChannels)));
                } else {
                    // "normal" stage scan with volume channel switching
                    if (numViews == 1) {
                        // single-view so will retrace at beginning of each channel
                        return ((rampDuration * 2) + stackDuration + retraceTime) * numChannels;
                    } else {
                        // will only retrace at very start/end
                        return retraceTime + (numViews * ((rampDuration * 2) + stackDuration) * numChannels);
                    }
                }
            } else {
                // TODO: do i need this case? is it correct?
                // catch-all for NO_SCAN, etc
                return (rampDuration * 2) + (stackDuration * numChannels * numViews) + retraceTime;
            }
        } else {
            // GALVO_SCAN (piezo-like logic for SCAPE)
            // estimate channel switching overhead time as 0.5s, actual value will be hardware-dependent
            final double channelSwitchDelay = (channelMode == ChannelMode.VOLUME) ? 500.0 : 0.0;
            if (channelMode == ChannelMode.SLICE_HW) {
                // channels switched per slice
                return numViews * (delayBeforeView + stackDuration * numChannels); // channelSwitchDelay = 0
            } else { // VOLUME and VOLUME_HW
                // channels switched per volume
                return numViews * numChannels * (delayBeforeView + stackDuration)
                        + (numChannels - 1) * channelSwitchDelay;
            }
        }
    }

    private double getStageRampDuration(final ScapeAcquisitionSettings settings) {
        final double rampDuration = settings.volume().delayBeforeView() + getScanStageAcceleration(settings);
        model_.studio().logs().logDebugMessage("stage ramp duration is " + rampDuration + " milliseconds");
        return rampDuration;
    }

    private double getScanStageAcceleration(final ScapeAcquisitionSettings settings) {
        // TODO: remove this and find a better way
        if (controller_ == null) {
            controller_ = new PLogicScape(model_);
        }
        // extra 1 for rounding up that often happens in controller
        return controller_.computeScanAcceleration(controller_.computeScanSpeed(settings), settings) + 1;
    }

    private double getStageRetraceDuration(final ScapeAcquisitionSettings settings) {
        final ASIXYStage stage = model_.devices().device("SampleXY");
        if (stage == null) {
            studio_.logs().showError("could not find XY stage!");
            return 0.0; // early exit => error
        }
        final double retraceRelativeSpeedPercent;
        if (stage.hasProperty(ASIXYStage.Properties.SCAN_RETRACE_SPEED)) {
            // this added in firmware v3.30; if not present then we set to firmware default hardcoded previously
            retraceRelativeSpeedPercent = stage.getScanRetraceSpeed();
        } else {
            retraceRelativeSpeedPercent = 67.0;
        }
        final double retraceSpeed = retraceRelativeSpeedPercent / 100 * stage.getMaxSpeedX();
        final double speedFactor = GeometryUtils.getStageGeometricSpeedFactor(
                settings.stageScan().firstViewAngle(), settings.volume().firstView() == 1);
        final double scanDistance = settings.volume().slicesPerView() * settings.volume().sliceStepSize() * speedFactor;
        final double accelerationX = getScanStageAcceleration(settings);
        final double retraceDuration = scanDistance / retraceSpeed + accelerationX * 2;
        studio_.logs().logDebugMessage("stage retrace duration is " + retraceDuration + " milliseconds");
        return retraceDuration;
    }

}
