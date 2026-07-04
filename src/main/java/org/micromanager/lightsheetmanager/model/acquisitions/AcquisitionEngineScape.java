package org.micromanager.lightsheetmanager.model.acquisitions;

import mmcorej.StrVector;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.acqj.api.AcquisitionHook;
import org.micromanager.acqj.main.Acquisition;
import org.micromanager.acqj.main.AcquisitionEvent;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.acquisition.internal.MMAcquisition;
import org.micromanager.acquisition.internal.acqengjcompat.AcqEngJAdapter;
import org.micromanager.acquisition.internal.acqengjcompat.AcqEngJMDADataSink;
import org.micromanager.data.Datastore;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.internal.MMStudio;
import org.micromanager.lightsheetmanager.api.data.AcquisitionMode;
import org.micromanager.lightsheetmanager.api.data.CameraLibrary;
import org.micromanager.lightsheetmanager.api.data.CameraMode;
import org.micromanager.lightsheetmanager.api.data.ChannelMode;
import org.micromanager.lightsheetmanager.api.internal.DefaultTimingSettings;
import org.micromanager.lightsheetmanager.api.internal.ScapeAcquisitionSettings;
import org.micromanager.lightsheetmanager.gui.utils.DialogUtils;
import org.micromanager.lightsheetmanager.model.DataStorage;
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
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Manages the acquisition for SCAPE microscopes.
 */
public class AcquisitionEngineScape extends AcquisitionEngine {

    PLogicScape controller_;
    ArrayList<Double> savedExposures_ = new ArrayList<>();
    Point2D.Double xyPosUm_;
    private double origSpeedX_;
    private double origAccelX_;
    private double scanSpeedX_;
    private double scanAccelX_;
    private boolean isShutterOpen_;
    private boolean autoShutter_;
    private boolean isPolling_; // true if polling was enabled at the start of an acquisition

    public AcquisitionEngineScape(final LightSheetManager model) {
        super(model);
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
        try {
            core_.setCameraDevice(cameraName);
        } catch (Exception e) {
            studio_.logs().showError("Could not set \"Core-Camera\" to the first logical camera device.");
            return false;
        }

        // this is needed for LSMAcquisitionEvents to work with multiple positions
        if (core_.getFocusDevice().isEmpty()
                && acqSettings_.isUsingMultiplePositions()) {
            studio_.logs().showError(
                    "The default focus device \"Core-Focus\" needs to be set to use multiple positions.");
            return false;
        }

        // make sure that there are positions in the PositionList
        if (acqSettings_.isUsingMultiplePositions()) {
            final int numPositions = studio_.positions().getPositionList().getNumberOfPositions();
            if (numPositions == 0) {
                studio_.logs().showError(("XY positions expected but the position list is empty"));
                return false;
            }
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

        // initialize stage scanning so we can restore state
        xyPosUm_ = new Point2D.Double();
        origSpeedX_ = 1.0; // don't want 0 in case something goes wrong
        origAccelX_ = 1.0; // don't want 0 in case something goes wrong

        // make sure stage scan is supported if selected
        if (acqSettings_.stageScan().enabled()) {
            final ASIXYStage xyStage = model_.devices().device("SampleXY");
            if (xyStage != null) {
                if (!xyStage.hasProperty(ASIXYStage.Properties.SCAN_NUM_LINES)) {
                    studio_.logs().showError("Must have stage with scan-enabled firmware for stage scanning.");
                    return false;
                }
                if (acqSettings_.acquisitionMode() == AcquisitionMode.STAGE_SCAN_INTERLEAVED) {
                    if (acqSettings_.volume().numViews() < 2) {
                        studio_.logs().showError("Interleaved stage scan requires two sides.");
                    }
                    return false;
                }

                // second part: initialize stage scanning, so we can restore state later
                xyPosUm_ = xyStage.getXYPosition();
                origSpeedX_ = xyStage.getSpeedX();
                origAccelX_ = xyStage.getAccelerationX();

                // if X speed is less than 0.2 mm/s then it probably wasn't restored to correct speed some other time
                if (origSpeedX_ < 0.2) {
                    final boolean result = DialogUtils.showYesNoDialog(null, "Change Speed",
                            "Max speed of X axis is small, perhaps it was not correctly restored after " +
                                    "stage scanning previously. Do you want to set it to 1 mm/s now?");
                    if (result) {
                        xyStage.setSpeedX(1.0);
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

        final String settingsJson = acqSettings_.toPrettyJson();
        studio_.logs().logMessage("Starting Acquisition with settings:\n" + settingsJson);

        String saveDir = acqSettings_.saveDirectory();
        String saveName = acqSettings_.saveNamePrefix();

        // TODO: put this in AcquisitionEngine base class, between setup and run once structure is better
        // save settings as JSON to the save directory
        if (model_.acquisitions().settings().isSavingImagesDuringAcquisition()) {
            FileUtils.writeStringToFile(saveDir + File.separator + "acq_settings.json", settingsJson);
        }

        // write the position list if we are using multiple positions
        if (model_.acquisitions().settings().isSavingImagesDuringAcquisition()
                && model_.acquisitions().settings().isUsingMultiplePositions()) {
            final PositionList positionList = model_.studio().positions().getPositionList();
            if (positionList.getNumberOfPositions() > 0) {
                try {
                    final String path = saveDir + File.separator + "position_list.pos";
                    positionList.save(path);
                    model_.studio().logs().logMessage("Position list saved to " + path);
                } catch (Exception e) {
                    model_.studio().logs().logError(e, "Could not save position list.");
                }
            }
        }

        // This sets the preferred save mode for DefaultDatastore, this value
        // is used in the MMAcquisition constructor to set the Storage object.
        if (acqSettings_.saveMode() == DataStorage.SaveMode.ND_TIFF) {
            DefaultDatastore.setPreferredSaveMode(studio_, Datastore.SaveMode.ND_TIFF);
        } else if (acqSettings_.saveMode() == DataStorage.SaveMode.MULTIPAGE_TIFF) {
            DefaultDatastore.setPreferredSaveMode(studio_, Datastore.SaveMode.MULTIPAGE_TIFF);
        } else if (acqSettings_.saveMode() == DataStorage.SaveMode.SINGLEPLANE_TIFF_SERIES) {
            DefaultDatastore.setPreferredSaveMode(studio_, Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES);
        } else {
            studio_.logs().showError("Unsupported save mode: " + acqSettings_.saveMode());
            return false;
        }

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
            summaryMetadata.put("z-step_um", acqSettings_.volume().sliceStepSize());
        } catch (JSONException e) {
            studio_.logs().logError("Failed to add z-step_um metadata: " + e.getMessage());
        }
        DefaultSummaryMetadata dsmd = addMMSummaryMetadata(summaryMetadata);

        // TODO(Brandon): where should i get this from?
        SequenceSettings.Builder sequenceSettingsBuilder = new SequenceSettings.Builder();
        sequenceSettingsBuilder.shouldDisplayImages(true);

        MMAcquisition acq = new MMAcquisition(studio_, dsmd,
                this, sequenceSettingsBuilder.build());

        curStore_ = acq.getDatastore();
        curPipeline_ = acq.getPipeline();
        sink.setDatastore(curStore_);
        sink.setPipeline(curPipeline_);

        studio_.events().registerForEvents(this);
        // commented because this is prob specific to MM MDAs
//        studio_.events().post(new DefaultAcquisitionStartedEvent(curStore_, this,
//              acquisitionSettings));


        // TODO if position time ordering ever implemented, this should be reactivated and the
        //  timelapse hook copied from org.micromanager.acquisition.internal.acqengjcompat.AcqEngJAdapter
//        if (sequenceSettings_.acqOrderMode() == AcqOrderMode.POS_TIME_CHANNEL_SLICE
//              || sequenceSettings_.acqOrderMode() == AcqOrderMode.POS_TIME_SLICE_CHANNEL) {
//            // Pos_time ordered acquisition need their timelapse minimum start time to be
//            // adjusted for each position.  The only place to do that seems to be a hardware hook.
//            currentAcquisition_.addHook(timeLapseHook(acquisitionSettings),
//                  AcquisitionAPI.BEFORE_HARDWARE_HOOK);
//        }

        long acqButtonStart = System.currentTimeMillis();

        ////////////  Acquisition hooks ////////////////////
        // These functions will be run on different threads during the acquisition process
        //    Hooks will run on the Acquisition Engine thread--the one that controls all hardware

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

                // Translate event to timeIndex/channel/etc
                AcquisitionEvent firstAcqEvent = event.getSequence().get(0);
                // TODO: add later when autofocus is complete, prevent errors if no time index is found for now
                //int timePoint = firstAcqEvent.getTIndex();

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
                boolean doAutofocus = acqSettings_.autofocus().enabled();

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
                    if (acqSettings_.stageScan().enabled() && acqSettings_.isUsingMultiplePositions()) {
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
                if (isUsingPLC) {
                    if (acqSettings_.stageScan().enabled() && acqSettings_.isUsingMultiplePositions()) {
                        final ASIXYStage xyStage = model_.devices().device("SampleXY");
                        final Point2D.Double pos = xyStage.getXYPosition();
                        xyStage.setSpeedX(scanSpeedX_);
                        xyStage.setAccelerationX(scanAccelX_);
                        controllerInstance.prepareStageScanForAcquisition(pos.x, pos.y, acqSettings_);
                        controllerInstance.triggerControllerStartAcquisition(acqSettings_.acquisitionMode(),
                            acqSettings_.volume().firstView());
                        return event;
                    }

                    // TODO: is this the best place to set state to idle?
                    ASIScanner scanner = model_.devices().device("IllumSlice");
                    // need to set to IDLE to re-arm for each z-stack
                    if (!acqSettings_.isUsingHardwareTimePoints()) {
                        if (scanner.getSPIMState().equals(ASIScanner.SPIMState.RUNNING)) {
                            scanner.setSPIMState(ASIScanner.SPIMState.IDLE);
                        }
                    }

                    int side = 0;
                    // NOTE: not sure why this is being triggered twice with only 1 camera; so we need guard
                    // TODO: enable 2 sided acquisition
                    if (scanner.getSPIMState().equals(ASIScanner.SPIMState.IDLE)) {
                        controllerInstance.triggerControllerStartAcquisition(acqSettings_.acquisitionMode(), side);
                    }
                }
                return event;
            }

            @Override
            public void close() {

            }
        }, Acquisition.AFTER_CAMERA_HOOK);

        ///////////// Turn off autoshutter /////////////////
        try {
            isShutterOpen_ = core_.getShutterOpen();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // TODO: should the shutter be left open for the full duration of acquisition?
        //  because that's what this code currently does
        autoShutter_ = core_.getAutoShutter();
        if (autoShutter_) {
            core_.setAutoShutter(false);
            if (!isShutterOpen_) {
                try {
                    core_.setShutterOpen(true);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        currentAcquisition_.start();

        ////////////  Create and submit acquisition events ////////////////////
        // Create iterators of acquisition events and submit them to the engine for execution
        // The engine will (try to) automatically iterate over the AcquisitionEvents of each
        // iterator, but not over multiple iterators. So there should be one iterator submitted for
        // each expected triggering of the Tiger controller.

        // TODO: execute any start-acquisition runnables


        // Loop 1: XY positions
        PositionList pl = MMStudio.getInstance().positions().getPositionList();

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
               if (acqSettings_.volume().numViews() > 1) {
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

        if (acqSettings_.isUsingHardwareTimePoints()) {
            AcquisitionEvent baseEvent = new AcquisitionEvent(currentAcquisition_);
            if (acqSettings_.channels().enabled()) {
                currentAcquisition_.submitEventIterator(
                        LightSheetEventAdapter.createTimelapseMultiChannelVolumeAcqEvents(
                                baseEvent.copy(), acqSettings_, cameraNames, null));
            } else {
                currentAcquisition_.submitEventIterator(
                        LightSheetEventAdapter.createTimelapseVolumeAcqEvents(
                                baseEvent.copy(), acqSettings_, cameraNames, null));
            }

        } else {

            final int numPositions = acqSettings_.isUsingMultiplePositions() ? pl.getNumberOfPositions() : 1;
            final int numTimePoints = acqSettings_.isUsingTimePoints() ? acqSettings_.numTimePoints() : 1;

            // Loop 1: Multiple time points
            for (int timeIndex = 0; timeIndex < numTimePoints; timeIndex++) {
                //System.out.println("time index: " + timeIndex);
                AcquisitionEvent baseEvent = new AcquisitionEvent(currentAcquisition_);
                if (acqSettings_.isUsingTimePoints()) {
                    baseEvent.setAxisPosition(LightSheetEventAdapter.TIME_AXIS, timeIndex);
                    baseEvent.setMinimumStartTime((long) (timeIndex * (model_.acquisitions().settings().timePointInterval() * 1000.0)));
                }
                // Loop 2: XY positions
                for (int positionIndex = 0; positionIndex < numPositions; positionIndex++) {
                    //System.out.println("pos index: " + positionIndex);
                    if (acqSettings_.isUsingMultiplePositions()) {
                        baseEvent.setAxisPosition(LightSheetEventAdapter.POSITION_AXIS, positionIndex);
                        // is this the best way to do stage movements with new acq engine?
                        MultiStagePosition position = pl.getPosition(positionIndex);
                        baseEvent.setX(position.getX());
                        baseEvent.setY(position.getY());
                    }
                    // TODO: what to do if multiple positions not defined: acquire at current stage position?
                    //  If yes, then nothing more to do here.

                    // Loop 3: Channels; Loop 4: Z slices
                    if (acqSettings_.channels().enabled()) {
                        currentAcquisition_.submitEventIterator(
                                LightSheetEventAdapter.createChannelAcqEvents(
                                        baseEvent.copy(), acqSettings_, cameraNames, null));
                    } else {
                        currentAcquisition_.submitEventIterator(
                                LightSheetEventAdapter.createAcqEvents(
                                        baseEvent.copy(), acqSettings_, cameraNames, null));
                    }
                }
            }

        }

//            for (int positionIndex = 0; positionIndex < numPositions; positionIndex++) {
//                AcquisitionEvent baseEvent = new AcquisitionEvent(currentAcquisition_);
//                if (acqSettings_.isUsingMultiplePositions()) {
//                    baseEvent.setAxisPosition(LSMAcquisitionEvents.POSITION_AXIS, positionIndex);
//                    // is this the best way to do stage movements with new acq engine?
//                    MultiStagePosition position = pl.getPosition(positionIndex);
//                    baseEvent.setX(position.getX());
//                    baseEvent.setY(position.getY());
//                }
//                // TODO: what to do if multiple positions not defined: acquire at current stage position?
//                //  If yes, then nothing more to do here.
//
//                if (acqSettings_.isUsingHardwareTimePoints()) {
//                    // create a full iterator of TCZ acquisition events, and Tiger controller
//                    // will handle everything else
//                    if (acqSettings_.isUsingChannels()) {
//                        currentAcquisition_.submitEventIterator(
//                                LSMAcquisitionEvents.createTimelapseMultiChannelVolumeAcqEvents(
//                                        baseEvent.copy(), acqSettings_, cameraNames, null));
//                    } else {
//                        currentAcquisition_.submitEventIterator(
//                                LSMAcquisitionEvents.createTimelapseVolumeAcqEvents(
//                                        baseEvent.copy(), acqSettings_, cameraNames, null));
//                    }
//                } else {
//                    // Loop 2: Multiple time points
//                    for (int timeIndex = 0; timeIndex < numTimePoints; timeIndex++) {
//                        baseEvent.setTimeIndex(timeIndex);
//                        // Loop 3: Channels; Loop 4: Z slices (non-interleaved)
//                        // Loop 3: Channels; Loop 4: Z slices (interleaved)
//                        if (acqSettings_.isUsingChannels()) {
//                            currentAcquisition_.submitEventIterator(
//                                    LSMAcquisitionEvents.createMultiChannelVolumeAcqEvents(
//                                            baseEvent.copy(), acqSettings_, cameraNames, null,
//                                            acqSettings_.acquisitionMode() ==
//                                                    AcquisitionMode.STAGE_SCAN_INTERLEAVED));
//                        } else {
//                            currentAcquisition_.submitEventIterator(
//                                    LSMAcquisitionEvents.createVolumeAcqEvents(
//                                            baseEvent.copy(), acqSettings_, cameraNames, null));
//                        }
//                    }
//                }
//            }

        // No more instructions (i.e. AcquisitionEvents); tell the acquisition to initiate shutdown
        // once everything finishes
        currentAcquisition_.finish();

        currentAcquisition_.waitForCompletion();

        // report elapsed time
        final long elapsedTimeMs = System.currentTimeMillis() - acqButtonStart;
        studio_.logs().logMessage("SCAPE plugin acquisition took: " + elapsedTimeMs + " milliseconds");

        return true;
    }

    @Override
    boolean finish() {

        final CameraBase[] cameras = model_.devices().imagingCameras();

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
            controller_.cleanUpControllerAfterAcquisition(acqSettings_, true);
            controller_.stopSPIMStateMachines();
        }

        // if we did stage scanning restore the original position and speed
        if (acqSettings_.stageScan().enabled()) {
            final ASIXYStage xyStage = model_.devices().device("SampleXY");
            final boolean returnToOriginalPosition =
                    acqSettings_.stageScan().returnToStart();

            // make sure stage scanning state machine is stopped,
            // otherwise setting speed/position won't take
            xyStage.setScanState(ASIXYStage.ScanState.IDLE);
            xyStage.setSpeedX(origSpeedX_);
            xyStage.setAccelerationX(origAccelX_);

            if (returnToOriginalPosition) {
                xyStage.setXYPosition(xyPosUm_.x, xyPosUm_.y);
            }
        }

        // Restore shutter/autoshutter to original state
        try {
            core_.setShutterOpen(isShutterOpen_);
            core_.setAutoShutter(autoShutter_);
        } catch (Exception e) {
            studio_.logs().logError("Couldn't restore shutter to original state");
        }

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

        // set the camera trigger modes back to internal for live mode
        if (savedExposures_.size() == cameras.length) {
            for (int i = 0; i < cameras.length; i++) {
                CameraBase camera = cameras[i];
                camera.setTriggerMode(CameraMode.INTERNAL);
                camera.setExposure(savedExposures_.get(i));
            }
        }

        if (acqSettings_.isSavingImagesDuringAcquisition()) {
            final String savePath = FileUtils.createUniquePath(
                    acqSettings_.saveDirectory(), acqSettings_.saveNamePrefix());
            try {
                // convert from DataStorage.SaveMode to Datastore.SaveMode
                final Datastore.SaveMode saveMode =
                        DataStorage.SaveMode.convert(acqSettings_.saveMode());
                curStore_.save(saveMode, savePath);
            } catch (IOException e) {
                model_.studio().logs().showError("could not save the acquisition data to: \n" + savePath);
            }
        }

        // unregister to stop ghost events
        studio_.events().unregisterForEvents(this);

        // start polling for navigation panel
        if (isPolling_) {
            studio_.logs().logMessage("started position polling after acquisition");
            model_.positions().startPolling();
        }
        return true;
    }

    private boolean doHardwareCalculations(PLogicScape plc) {

        // TODO: find a better place to set the camera trigger mode for SCAPE
        CameraBase[] cameras = model_.devices().imagingCameras();
        for (CameraBase camera : cameras) {
            camera.setTriggerMode(acqSettings_.cameraMode());
            studio_.logs().logMessage("camera \"" + camera.getDeviceName()
                 + "\" set to mode: " + camera.getTriggerMode());
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
            if (acqSettings_.channels().count() == 0) {
                studio_.logs().showError("\"Channels\" is checked, but no channels are selected");
                return false; // early exit
            }
            switch (acqSettings_.channels().mode()) {
                case VOLUME:
                    changeChannelPerVolumeSoftware = true;
                    changeChannelPerVolumeDoneFirst = true;
                    break;
                case VOLUME_HW:
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
        final double exposureTime = acqSettings_.timing().cameraExposure();

        // test acq was here

        final double volumeDuration = computeVolumeDuration();
        final double timepointDuration = computeTimePointDuration();
        final long timepointIntervalMs = Math.round(acqSettings_.timePointInterval() * 1000.0);

        // use hardware timing if < 1 second between time points
        // experimentally need ~0.5 sec to set up acquisition, this gives a bit of cushion
        // cannot do this in getCurrentAcquisitionSettings because of mutually recursive
        // call with computeVolumeDuration()
        boolean isUsingHardwareTimePoints = false; // TODO: asb_ not built yet
        if (acqSettings_.isUsingTimePoints()
                && acqSettings_.numTimePoints() > 1
                && timepointIntervalMs < (timepointDuration + 750)
                && !acqSettings_.stageScan().enabled()) {
            asb_.useHardwareTimePoints(true);
            isUsingHardwareTimePoints = true;
        }

        // TODO: implement multiple positions using hardware time points, currently
        //  set hardware time points to false if using multiple positions
        if (acqSettings_.isUsingMultiplePositions()) {
            if (acqSettings_.isUsingHardwareTimePoints()) {
//                    || acqSettings_.numTimePoints() > 1)
//                    && (timepointIntervalMs < timepointDuration*1.2)) {
                asb_.useHardwareTimePoints(false);
//                studio_.logs().showError("Time point interval may not be sufficient "
//                        + "depending on actual time required to change positions. "
//                        + "Proceed at your own risk.");
            }
        }

        // only use hardware time points when use time points is checked
        if (acqSettings_.isUsingHardwareTimePoints()) {
            if (!acqSettings_.isUsingTimePoints()) {
                asb_.useHardwareTimePoints(false);
            }
        }

        // TODO: this is not necessary below
//        if (acqSettings_.isUsingHardwareTimePoints()) {
//            final int numTimePoints = acqSettings_.numTimePoints();
//            final int numChannels = acqSettings_.numChannels();
//            final int slicePerView = acqSettings_.volumeSettings().slicesPerView();
//            // in hardwareTimepoints case we trigger controller once for all timepoints => need to
//            //   adjust number of frames we expect back from the camera during MM's SequenceAcquisition
//            if (acqSettings_.cameraMode() == CameraMode.OVERLAP) {
//                // For overlap mode we are send one extra trigger per channel per side for volume-switching (both PLogic and not)
//                // This holds for all multichannel modes, just the order in which the extra trigger comes varies
//                // Very last trigger won't ever return a frame so subtract 1.
//                final int hardwareSlicesPerView = (slicePerView + 1) * numChannels * numTimePoints;
//                asb_.volumeSettingsBuilder().slicesPerView(hardwareSlicesPerView - 1);
//            } else {
//                asb_.volumeSettingsBuilder().slicesPerView(slicePerView * numTimePoints);
//            }
//        }

        final double sliceDuration = asb_.timingBuilder().sliceDuration();
        if (exposureTime + cameraReadoutTime > sliceDuration) {
            // should only possible to mess this up using advanced timing settings
            // or if there are errors in our own calculations
            studio_.logs().showError("Exposure time of " + exposureTime +
                    " is longer than time needed for a line scan with" +
                    " readout time of " + cameraReadoutTime + "\n" +
                    "This will result in dropped frames. " +
                    "Please change input. " +
                    "Formula: (" + exposureTime + " + " + cameraReadoutTime + ") > " + sliceDuration);
            return false; // early exit
        }

        // must use PLogic for channels when using hardware time points
        if (isUsingHardwareTimePoints) {
            if (acqSettings_.channels().enabled() && acqSettings_.channels().mode() == ChannelMode.VOLUME) {
                studio_.logs().showError("Cannot use hardware time points (small time point interval) " +
                        "with software channels (need to use PLogic channel switching).");
                return false;
            }
            if (acqSettings_.stageScan().enabled()) {
                // stage scanning needs to be triggered for each time point
                studio_.logs().showError("Cannot use hardware time points (small time point interval) "
                        + "with stage scanning.");
                return false;
            }
        }

        final int numTimePoints = acqSettings_.isUsingTimePoints() ? acqSettings_.numTimePoints() : 1;
        if (!acqSettings_.isUsingMultiplePositions() && numTimePoints > 1) {
            if (timepointIntervalMs < volumeDuration) {
                studio_.logs().showError("Time point interval shorter than the time to collect a single volume.");
                return false;
            }
        }

        // set exposure for imaging camera
        for (CameraBase camera : cameras) {
           camera.setExposure(exposureTime);
        }

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
        // Note: sliceDuration is computed automatically when build() is called
        //final double sliceDuration = getSliceDuration(asb_.timingSettingsBuilder().build());
        //asb_.timingSettingsBuilder().sliceDuration(sliceDuration);
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
        double scanDuration = 0.0;
        double cameraTriggerDuration = 0.0;
        double laserTriggerDuration = 0.0;
        double delayBeforeCamera = 0.0;
        double delayBeforeLaser = 0.0;
        double delayBeforeScan = 0.0;
        double cameraExposure = 0.0;

        DefaultTimingSettings.Builder tsb = DefaultTimingSettings.builder();
        switch (cameraMode) {
            case PSEUDO_OVERLAP: // e.g. Kinetix
                scansPerSlice = 1;
                scanDuration = 0.25;
                cameraExposure = laserDuration;
                laserTriggerDuration = laserDuration;
                cameraTriggerDuration = laserDuration;
                delayBeforeCamera = 0.25;
                delayBeforeLaser = sliceDeadTime;
                delayBeforeScan = 0.0;
                break;
            case OVERLAP: // e.g.
                if (acqSettings_.channels().enabled() && acqSettings_.channels().count() > 1
                        && acqSettings_.channels().mode() == ChannelMode.SLICE_HW) {
                    // for interleaved slices we should illuminate during global exposure but not during readout/reset time after each trigger
                    scansPerSlice = 1;
                    scanDuration = 1.0;
                    cameraExposure = 0.25;
                    laserTriggerDuration = laserDuration;
                    cameraTriggerDuration = 1.0;
                    delayBeforeCamera = 0.0;
                    delayBeforeLaser = sliceDeadTime + NumberUtils.ceilToQuarterMs(cameraResetTime);
                    delayBeforeScan = 0.0;
                } else {
                    // the usual case
                    scansPerSlice = 1;
                    scanDuration = 1.0;
                    cameraExposure = 0.25;
                    laserTriggerDuration = laserDuration;
                    cameraTriggerDuration = 1.0;
                    delayBeforeCamera = 0.0;
                    delayBeforeLaser = sliceDeadTime + sliceLaserInterleaved;
                    delayBeforeScan = 0.0;
                }
                break;
            case EDGE:
                // should illuminate during the entire exposure (or as much as needed) => will be exposing during camera reset and readout too
                // Note: that this may be faster than overlap for interleaved channels
                scansPerSlice = 1;
                scanDuration = 1.0;
                cameraExposure = laserDuration - NumberUtils.ceilToQuarterMs(actualCameraResetTime + cameraReadoutTime);
                laserTriggerDuration = laserDuration;
                cameraTriggerDuration = 1.0;
                delayBeforeCamera = sliceLaserInterleaved;
                delayBeforeLaser = sliceDeadTime + sliceLaserInterleaved;
                delayBeforeScan = 0.0;
                break;
            default:
                studio_.logs().showError("Invalid camera mode");
                break;
        }

        // sync with builder so that tsb.sliceDuration() is accurate
        tsb.scansPerSlice(scansPerSlice)
                .scanDuration(scanDuration)
                .cameraTriggerDuration(cameraTriggerDuration)
                .laserTriggerDuration(laserTriggerDuration)
                .delayBeforeScan(delayBeforeScan)
                .delayBeforeLaser(delayBeforeLaser)
                .delayBeforeCamera(delayBeforeCamera)
                .cameraExposure(cameraExposure);

        // if a specific slice period was requested, add corresponding delay to scan/laser/camera
        if (!acqSettings_.slice().periodMinimized()) {
            double globalDelay = acqSettings_.slice().period() - tsb.sliceDuration();
            // only true when user has specified period that is unattainable
            if (globalDelay < 0) {
                globalDelay = 0;
                studio_.logs().logDebugMessage("Increasing slice period to meet laser exposure constraint\n"
                        + "(time required for camera readout; readout time depends on ROI).");
            }
            delayBeforeCamera += globalDelay;
            delayBeforeLaser += globalDelay;
            delayBeforeScan += globalDelay;

            // sync with builder so that tsb.sliceDuration() is accurate
            tsb.delayBeforeScan(delayBeforeScan)
                    .delayBeforeLaser(delayBeforeLaser)
                    .delayBeforeCamera(delayBeforeCamera);
        }

        // fix corner case of (exposure time + readout time) being greater than the slice duration
        // most of the time the slice duration is already larger
        final double extraGlobalDelay = NumberUtils.ceilToQuarterMs(
                (cameraExposure + cameraReadoutTime) - tsb.sliceDuration());
        if (extraGlobalDelay > 0) {
            delayBeforeCamera += extraGlobalDelay;
            delayBeforeLaser += extraGlobalDelay;
            delayBeforeScan += extraGlobalDelay;
        }

        // final sync, only values updated are delays
        tsb.delayBeforeScan(delayBeforeScan)
                .delayBeforeLaser(delayBeforeLaser)
                .delayBeforeCamera(delayBeforeCamera);

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

        // TODO: is this necessary? setTriggerMode is called in doHardwareCalculations too
        CameraBase[] cameras = model_.devices().imagingCameras();
        for (CameraBase cam : cameras) {
            cam.setTriggerMode(acqSettings_.cameraMode());
        }

        // TODO: camera.getTriggerMode(); does not match up with actual selected trigger mode for PVCAM (pseudo overlap reads as edge trigger)
        //System.out.println(camera.getDeviceName());
        CameraMode camMode = acqSettings_.cameraMode(); // camera.getTriggerMode();
        //System.out.println(camMode);

        final double scanLaserBufferTime = NumberUtils.roundToQuarterMs(0.25);  // below assumed to be multiple of 0.25ms

        final double cameraResetTime = camera.getResetTime(camMode);      // recalculate for safety, 0 for light sheet
        final double cameraReadoutTime = camera.getReadoutTime(camMode);  // recalculate for safety, 0 for overlap

        final double cameraReadoutMax = NumberUtils.ceilToQuarterMs(cameraReadoutTime);
        final double cameraResetMax = NumberUtils.ceilToQuarterMs(cameraResetTime);

        // we will wait cameraReadoutMax before triggering camera, then wait another cameraResetMax for global exposure
        // this will also be in 0.25ms increment
        final double globalExposureDelayMax = cameraReadoutMax + cameraResetMax;
        double laserTriggerDuration = NumberUtils.roundToQuarterMs(acqSettings_.slice().sampleExposure());
        double scanDuration = laserTriggerDuration + 2*scanLaserBufferTime;
        // scan will be longer than laser by 0.25ms at both start and end

        // account for delay in scan position due to Bessel filter by starting the scan slightly earlier
        // than we otherwise would (Bessel filter selected b/c stretches out pulse without any ripples)
        // delay to start is (empirically) 0.07ms + 0.25/(freq in kHz)
        // delay to midpoint is empirically 0.38/(freq in kHz)
        // group delay for 5th-order Bessel filter ~0.39/freq from theory and ~0.4/freq from IC datasheet
        final double scanFilterFreq = model_.devices()
                .device2("IllumSlice", ASIScanner.class)
                .map(ASIScanner::getFilterFreqX)
                .orElse(0.4); // default value

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

        double delayBeforeScan = globalExposureDelayMax - scanLaserBufferTime   // start scan 0.25ms before camera's global exposure
                - scanDelayFilter; // start galvo moving early due to card's Bessel filter and delay of TTL signals via PLC
        double delayBeforeLaser = globalExposureDelayMax; // turn on laser as soon as camera's global exposure is reached
        double delayBeforeCamera = cameraReadoutMax; // camera must read out last frame before triggering again

        // figure out desired time for camera to be exposing (including reset time)
        // because both camera trigger and laser on occur on 0.25ms intervals (i.e. we may not
        //    trigger the laser until 0.24ms after global exposure) use cameraReset_max
        // special adjustment for Photometrics cameras that possibly has extra clear time which is counted in reset time
        //    but not in the camera exposure time
        // TODO: skipped PVCAM case, this should already be handled by camera.getResetTime(camMode); but there may be differences

        // make sure to accumulate values so our comparison logic works at the end of the method
        double cameraExposure = NumberUtils.ceilToQuarterMs(cameraResetTime) + laserTriggerDuration;

        double cameraTriggerDuration = 1.0; // a reasonable default

        // NOTE: tsb.sliceDuration() is needed in PSEUDO_OVERLAP camera mode.
        // cameraExposure will be modified in the switch, sliceDuration does not depend on it
        DefaultTimingSettings.Builder tsb = DefaultTimingSettings.builder();
        tsb.scansPerSlice(1)
                .scanDuration(scanDuration)
                .cameraTriggerDuration(cameraTriggerDuration)
                .laserTriggerDuration(laserTriggerDuration)
                .delayBeforeScan(delayBeforeScan)
                .delayBeforeLaser(delayBeforeLaser)
                .delayBeforeCamera(delayBeforeCamera)
                .cameraExposure(cameraExposure); // base exposure before camera mode specific adjustments

        final CameraMode cameraMode = acqSettings_.cameraMode();
        switch (cameraMode) {
            case EDGE:
                // cameraTriggerDuration: doesn't really matter, 1ms should be plenty fast yet easy to see for debugging
                cameraExposure += 0.1; // add 0.1ms as safety margin, may require adding 0.25ms to slice
                // slight delay between trigger and actual exposure start
                //   is included in exposure time for Hamamatsu and negligible for Andor and PCO cameras
                // ensure not to miss triggers by not being done with readout in time for next trigger, add 0.25ms if needed
                if (tsb.sliceDuration() < (cameraExposure + cameraReadoutTime)) {
                    delayBeforeCamera += 0.25;
                    delayBeforeLaser += 0.25;
                    delayBeforeScan += 0.25;
                }
                break;
            case LEVEL: // AKA "bulb mode", TTL rising starts exposure, TTL falling ends it
                cameraTriggerDuration = NumberUtils.ceilToQuarterMs(cameraExposure);
                cameraExposure = 1.0; // doesn't really matter, controlled by TTL
                break;
            case OVERLAP: // only Hamamatsu or Andor
                // cameraTriggerDuration: doesn't really matter, 1ms should be plenty fast yet easy to see for debugging
                cameraExposure = 1.0; // doesn't really matter, controlled by interval between triggers
                break;
            case PSEUDO_OVERLAP:// PCO or Photometrics, enforce 0.25ms between end exposure and start of next exposure by triggering camera 0.25ms into the slice
                // cameraTriggerDuration: doesn't really matter, 1ms should be plenty fast yet easy to see for debugging
                // leave cameraExposure alone if using PVCAM device library
                if (!camera.getDeviceLibrary().equals("PVCAM")) {
                    cameraExposure = tsb.sliceDuration() - delayBeforeCamera;  // delayBeforeCamera should be 0.25ms for PCO
                }
                if (cameraReadoutMax < 0.24) {
                    studio_.logs().showError("Camera delay should be at least 0.25ms for pseudo-overlap mode.");
                }
                break;
            default:
                studio_.logs().showError("Invalid camera mode");
                break;
        }

        // sync with builder so that tsb.sliceDuration() is accurate
        tsb.cameraTriggerDuration(cameraTriggerDuration)
                .delayBeforeScan(delayBeforeScan)
                .delayBeforeLaser(delayBeforeLaser)
                .delayBeforeCamera(delayBeforeCamera);

        // fix corner case of negative calculated scanDelay
        if (delayBeforeScan < 0) {
            delayBeforeCamera -= delayBeforeScan;
            delayBeforeLaser -= delayBeforeScan;
            delayBeforeScan = 0; // same as (-= delayBeforeScan)
            // sync with builder
            tsb.delayBeforeScan(delayBeforeScan)
                    .delayBeforeLaser(delayBeforeLaser)
                    .delayBeforeCamera(delayBeforeCamera);
        }

        // if a specific slice period was requested, add corresponding delay to scan/laser/camera
        if (!acqSettings_.slice().periodMinimized()) {
            double globalDelay = acqSettings_.slice().period() - tsb.sliceDuration();
            // only true when user has specified period that is unattainable
            if (globalDelay < 0) {
                globalDelay = 0;
                studio_.logs().logDebugMessage("Increasing slice period to meet laser exposure constraint\n"
                        + "(time required for camera readout; readout time depends on ROI).");
            }
            delayBeforeCamera += globalDelay;
            delayBeforeLaser += globalDelay;
            delayBeforeScan += globalDelay;

            // sync with builder so that tsb.sliceDuration() is accurate
            tsb.delayBeforeScan(delayBeforeScan)
                    .delayBeforeLaser(delayBeforeLaser)
                    .delayBeforeCamera(delayBeforeCamera);
        }

        // fix corner case of (exposure time + readout time) being greater than the slice duration
        // most of the time the slice duration is already larger
        final double extraGlobalDelay = NumberUtils.ceilToQuarterMs(
                (cameraExposure + cameraReadoutTime) - tsb.sliceDuration());
        if (extraGlobalDelay > 0) {
            delayBeforeCamera += extraGlobalDelay;
            delayBeforeLaser += extraGlobalDelay;
            delayBeforeScan += extraGlobalDelay;
        }

        tsb.scansPerSlice(1)
                .scanDuration(scanDuration)
                .cameraTriggerDuration(cameraTriggerDuration)
                .laserTriggerDuration(laserTriggerDuration)
                .delayBeforeScan(delayBeforeScan)
                .delayBeforeLaser(delayBeforeLaser)
                .delayBeforeCamera(delayBeforeCamera)
                .cameraExposure(cameraExposure);

        return tsb;
    }

    @Override
    public void updateDurationLabels() {
        model_.acquisitions().recalculateSliceTiming();
        model_.acquisitions().updateSettings();
        // update durations now that settings are current
        updateSlicePeriodLabel(pnlDuration_.getSliceDurationLabel());
        updateVolumeDurationLabel(pnlDuration_.getVolumeDurationLabel());
        updateTotalTimeDurationLabel(pnlDuration_.getTotalDurationLabel());
    }

    private void updateSlicePeriodLabel(final JLabel label) {
        label.setText(NumberUtils.doubleToDisplayString(acqSettings_.timing().sliceDuration()) + " ms");
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
        final int numTimePoints = acqSettings_.isUsingTimePoints() ? acqSettings_.numTimePoints() : 1;
        return (numTimePoints - 1) * acqSettings_.timePointInterval() + computeTimePointDuration() / 1000.0;
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
        final double stackDuration = numCameraTriggers * acqSettings_.timing().sliceDuration();

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
