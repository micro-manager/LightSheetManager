package org.micromanager.lightsheetmanager.model;

import mmcorej.CMMCore;
import mmcorej.Configuration;
import org.micromanager.Studio;
import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.api.data.AcquisitionMode;
import org.micromanager.lightsheetmanager.api.data.CameraMode;
import org.micromanager.lightsheetmanager.api.data.ChannelMode;
import org.micromanager.lightsheetmanager.api.internal.ScapeAcquisitionSettings;
import org.micromanager.lightsheetmanager.model.acquisitions.AcquisitionEngineScape;
import org.micromanager.lightsheetmanager.model.acquisitions.CameraTriggers;
import org.micromanager.lightsheetmanager.model.channels.ChannelSpec;
import org.micromanager.lightsheetmanager.model.devices.vendor.ASIPLogic;
import org.micromanager.lightsheetmanager.model.devices.vendor.ASIPiezo;
import org.micromanager.lightsheetmanager.model.devices.vendor.ASIScanner;
import org.micromanager.lightsheetmanager.model.devices.vendor.ASIXYStage;
import org.micromanager.lightsheetmanager.model.devices.vendor.ASIZStage;
import org.micromanager.lightsheetmanager.model.devices.vendor.SingleAxis;
import org.micromanager.lightsheetmanager.model.utils.GeometryUtils;
import org.micromanager.lightsheetmanager.model.utils.NumberUtils;
import org.micromanager.lightsheetmanager.model.utils.StageScanUtils;

import java.awt.geom.Point2D;
import java.util.Objects;

public class PLogicScape {

    private Studio studio_;
    private CMMCore core_;

    private DeviceManager devices_;

    private ASIScanner scanner_;
    private ASIPiezo piezo_;
    private ASIXYStage xyStage_;
    private ASIZStage zStage_;
    private ASIPLogic plcCamera_;
    private ASIPLogic plcLaser_;


    // generic variables
    private double scanDistance_;      // in microns; cached value from last call to prepareControllerForAcquisition()
    private double actualStepSizeUm_;  // cached value from last call to prepareControllerForAcquisition()
    private boolean zSpeedZero_;       // cached value from last call to prepareStageScanForAcquisition()

    // PLC
    private static final int acquisitionFlagAddr = 1;
    private static final int counterLSBAddr = 3;
    private static final int counterMSBAddr = 4;
    private static final int laserTriggerAddress = 10;  // this should be set to (42 || 8) = (TTL1 || manual laser on)

    private final ScapeAcquisitionSettings acqSettings_;

    private final LightSheetManager model_;

    public PLogicScape(final LightSheetManager model) {
        model_ = Objects.requireNonNull(model);
        studio_ = model_.studio();
        devices_ = model_.devices();
        core_ = studio_.core();

        acqSettings_ = model_.acquisitions().settings();

        // init variables
        scanDistance_ = 0;
        actualStepSizeUm_ = 0;
        zSpeedZero_ = true;

        // populate devices
        scanner_ = devices_.device("IllumSlice");
        piezo_ = devices_.device("ImagingFocus");
        plcCamera_ = devices_.device("TriggerCamera");
        plcLaser_ = devices_.device("TriggerLaser");
        xyStage_ = devices_.device("SampleXY");
        zStage_ = devices_.device("SampleZ");
    }

    // TODO: numViews > 2
    /**
     * call special version which will only set the slice offset and not refresh everything else
     * @param settings
     * @param channelOffset
     * @return
     */
    public boolean prepareControllerForAcquisitionOffsetOnly(
            final ScapeAcquisitionSettings settings,
            final double channelOffset) {

        final int numViews = settings.volume().numViews();
        final int firstView = settings.volume().firstView();

        if (numViews > 1 || firstView == 1) {
            final boolean success = prepareControllerForAcquisitionSide(settings, 1, channelOffset, true);
            if (!success) {
                return false;
            }
        }

        if (numViews > 1 || firstView != 1) {
            final boolean success = prepareControllerForAcquisitionSide(settings, 2, channelOffset, true);
            if (!success) {
                return false;
            }
        }
        return true;
    }

    public boolean prepareControllerForAcquisition(
            final ScapeAcquisitionSettings settings,
            final double channelOffset) {
        // turn off beam and scan on both sides (they are turned off by SPIM state machine anyway)
        // also ensures that properties match reality at end of acquisition
        // SPIM state machine restores position of beam at end of SPIM state machine, now it
        // will be restored to blanking position
        scanner_.setBeamOn(false);
        scanner_.sa().setModeX(SingleAxis.Mode.DISABLED);

        final int numViews = settings.volume().numViews();
        final int firstView = settings.volume().firstView();

        // set up controller with appropriate SPIM parameters for each active side
        // some of these things only need to be done once if the same micro-mirror
        //   card is used (as is typical) but keeping code universal to handle
        //   case where MM devices reside on different controller cards
        // Note: firstView starts counting from 1...n views
        if (numViews > 1 || firstView == 1) {
            final boolean success = prepareControllerForAcquisitionSide(settings, 1, channelOffset, false);
            if (!success) {
                return false;
            }
        }
//        if (numViews > 1 || firstView != 1) {
//            final boolean success = prepareControllerForAcquisitionSide(settings, 2, channelOffset, false);
//            if (!success) {
//                return false;
//            }
//        }

        if (settings.stageScan().enabled()
                && settings.acquisitionMode() == AcquisitionMode.STAGE_SCAN_INTERLEAVED) {
            if (settings.volume().numViews() != 2) {
                model_.logging().reportError("Interleaved stage scan only possible for 2-sided acquisition.");
                return false;
            }
            if (settings.cameraMode() == CameraMode.OVERLAP) {
                model_.logging().reportError("Interleaved stage scan not compatible with overlap camera mode.");
                return false;
            }
        }

        // make sure set to use TTL signal from backplane in case PLOGIC_LASER is set to PLogicMode different from diSPIM shutter
        plcCamera_.setPreset(12);
        plcLaser_.setPreset(12);

        // make sure shutter is set to the PLOGIC_LASER device
        try {
            core_.setShutterDevice(plcLaser_.getDeviceName());
        } catch (Exception e) {
            model_.logging().reportError("could not set shutter to " + plcLaser_.getDeviceName()
                    + ": " + e.getMessage());
        }

        if (!acqSettings_.stageScan().enabled()) {
            scanDistance_ = 0;
        } else {
            // stage scanning with ASI stage
            // algorithm is as follows:
            // use the # of slices and slice spacing that the user specifies
            // because the XY stage is 45 degrees from the objectives have to move it sqrt(2) * slice step size
            // for now use the current X position as the start of acquisition and always start in positive X direction
            // for now always do serpentine scan with 2 passes at the same Y location, one pass each direction over the sample
            // => total scan distance = # slices * slice step size * sqrt(2)
            //    scan start position = current X position
            //    scan stop position = scan start position + total distance
            //    slow axis start = current Y position
            //    slow axis stop = current Y position
            //    X motor speed = slice step size * sqrt(2) / slice duration
            //    number of scans = number of sides (1 or 2)
            //    scan mode = serpentine for 2-sided non-interleaved, raster otherwise (need to revisit for 2D stage scanning)
            //    X acceleration time = use whatever current setting is
            //    scan settling time = delay before side
            final boolean isInterleaved = (settings.acquisitionMode() == AcquisitionMode.STAGE_SCAN_INTERLEAVED);

            // figure out the speed we should be going according to slice period, slice spacing, geometry, etc.
            final double requestedMotorSpeed = StageScanUtils.computeScanSpeed(settings);  // in mm/sec

            final double maxSpeed = xyStage_.getMaxSpeedX();
            if (requestedMotorSpeed > (maxSpeed * 0.8)) {
                // trying to go near max speed smooth scanning will be compromised
                model_.logging().reportError("Required stage speed is too fast, please reduce step size or increase sample exposure.");
                return false;
            }
            if (requestedMotorSpeed < (maxSpeed / 2000)) {
                // 1/2000 of the max speed is approximate place where smooth scanning breaks down (speed quantum is ~1/12000 max speed);
                // this also prevents setting to 0 which the controller rejects
                model_.logging().reportError("Required stage speed is too slow, please increase step size or decrease sample exposure.");
                return false;
            }
            xyStage_.setSpeedX(requestedMotorSpeed);

            // ask for the actual speed to calculate the actual step size
            final double actualMotorSpeed = xyStage_.getSpeedXUm() / 1000;

            // set the acceleration to a reasonable value for the (usually very slow) scan speed
            xyStage_.setAccelerationX(StageScanUtils.computeScanAcceleration(actualMotorSpeed,
                    xyStage_.getMaxSpeedX(), settings.stageScan().accelerationFactor()));

            // set the scan pattern and number of scans appropriately
            int numLines = settings.volume().numViews();
            if (isInterleaved) {
                numLines = 1;  // assure in acquisition code that we can't have single-sided interleaved
            }
            if (settings.channels().enabled()) {
                numLines = numLines * (settings.channels().count() / StageScanUtils.computeScanChannelsPerPass(settings));
            }
            xyStage_.setScanNumLines(numLines);

            final boolean isStageScan2Sided = (settings.acquisitionMode() == AcquisitionMode.STAGE_SCAN)
                    && settings.volume().numViews() == 2;

            xyStage_.setScanPattern(isStageScan2Sided ?
                    ASIXYStage.ScanPattern.SERPENTINE : ASIXYStage.ScanPattern.RASTER);
            xyStage_.setScanSettlingTime(acqSettings_.volume().delayBeforeView());

            if (xyStage_.getAxisPolarityX() != ASIXYStage.AxisPolarity.NORMAL) {
                model_.logging().reportError(
                        "Stage scanning requires X axis polarity set to " + ASIXYStage.AxisPolarity.NORMAL);
                return false;
            }

            // cache how far we scan each pass for later use
            final double speedFactor = GeometryUtils.getStageGeometricSpeedFactor(
                    settings.stageScan().firstViewAngle(),true);
            actualStepSizeUm_ = settings.volume().sliceStepSize() * (actualMotorSpeed / requestedMotorSpeed);
            scanDistance_ = settings.volume().slicesPerView() * actualStepSizeUm_ * speedFactor;

            if (!settings.isUsingMultiplePositions()) {
                // use current position as center position for stage scanning
                // multi-position situation is handled in position-switching code instead
                // TODO: use xyStage_.getXYPosition() again once a failed read stops returning (0, 0)
                // the position is read inside the try, so a failed read refuses the run instead
                // of centering the scan on 0 in place of the unread position
                final Point2D.Double p;
                try {
                    p = core_.getXYStagePosition(xyStage_.getDeviceName());
                } catch (Exception e) {
                    model_.logging().reportError("could not get the position of "
                            + xyStage_.getDeviceName() + " to center the stage scan: "
                            + e.getMessage());
                    return false;
                }
                // TODO: prepareStageScanForAcquisition(p.x, p.y, settings.getSPIMMode())
                prepareStageScanForAcquisition(p.x, p.y, settings);
            }
        }

        // sets PLogic "acquisition running" flag
        plcCamera_.setPreset(3);
        plcLaser_.setPreset(3);

        studio_.logs().logMessage("Finished preparing controller for acquisition with offset " + channelOffset +
                " with mode " + settings.acquisitionMode() + " and settings:\n" + settings.toPrettyJson());
        return true;
    }

    public boolean prepareStageScanForAcquisition(final double x, final double y, ScapeAcquisitionSettings settings) {
        final boolean scanFromCurrent = settings.stageScan().fromCurrentPosition();
        final boolean scanNegative = settings.stageScan().fromNegativeDirection();
        double xStartUm;
        double xStopUm;
        if (scanFromCurrent) {
            xStartUm = x;
            if (scanNegative) {
                xStopUm = x - scanDistance_;
            } else {
                xStopUm = x + scanDistance_;
            }
        } else {
            // centered
            if (scanNegative) {
                xStartUm = x + (scanDistance_ / 2);
                xStopUm = x - (scanDistance_ / 2);
            } else { // the original implementation
                xStartUm = x - (scanDistance_ / 2);
                xStopUm = x + (scanDistance_ / 2);
            }
        }

        xyStage_.setFastAxisStart(xStartUm / 1000);
        xyStage_.setFastAxisStop(xStopUm / 1000);
        xyStage_.setSlowAxisStart(y / 1000);
        xyStage_.setSlowAxisStop(y / 1000);

        zSpeedZero_ = true;  // will turn false if we are doing planar correction
        return true;
        // return preparePlanarCorrectionForAcquisition(); TODO: add planar correction later
    }

    /**
     * Returns the controller to "normal" state after an acquisition
     * @param centerPiezos true to move piezos to center position
     * @return false if there is a fatal error, true if successful
     */
    public boolean cleanUpControllerAfterAcquisition(final ScapeAcquisitionSettings settings, final boolean centerPiezos) {
        // clear "acquisition running" flag on PLC
        plcCamera_.setPreset(2);
        plcLaser_.setPreset(2);

        final int numViews = settings.volume().numViews();
        final int firstView = settings.volume().firstView();

        if (numViews > 1 || firstView == 1) {
            final boolean success = cleanUpControllerAfterAcquisitionSide(1, centerPiezos, 0.0);
            if (!success) {
                return false;
            }
        }
        if (numViews > 1 || firstView != 1) {
            final boolean success = cleanUpControllerAfterAcquisitionSide(2, centerPiezos, 0.0);
            if (!success) {
                return false;
            }
        }

        // clean up planar correction if needed
        if (!zSpeedZero_) {
            zStage_.setTTLInputMode("0 - none"); // TODO: make enum for TTL input mode
            zSpeedZero_ = true;
        }

        studio_.logs().logMessage("Finished controller cleanup after acquisition");
        return true;
    }

    public boolean prepareControllerForAcquisitionSide(
            final ScapeAcquisitionSettings settings,
            final int view,
            final double channelOffset,
            final boolean offsetOnly) {

        if (!offsetOnly) {
            // TODO: get and set light sheet props

            // if we are changing color slice by slice then set controller to do multiple slices per piezo move
            // otherwise just set to 1 slice per piezo move
            scanner_.setSPIMNumSlicesPerPiezo(CameraTriggers.channelsPerSlice(settings));

            // set controller to do multiple volumes per start trigger if we are doing
            //   multiple channels with  hardware switching of channel volume by volume
            // otherwise (no channels, software switching, slice by slice HW switching)
            //   just do one volume per start trigger
            int numVolumesPerTrigger = 1;
            if (settings.channels().enabled() && settings.channels().mode() == ChannelMode.VOLUME_HW) {
                numVolumesPerTrigger = settings.channels().count();
            }

            // can either trigger controller once for all the time points and
            //  have the number of repeats pre-programmed (hardware timing)
            //  or let plugin send trigger for each time point (software timing)
            double delayRepeats = 0.0;
            if (settings.isUsingHardwareTimePoints() && settings.isUsingTimePoints()) {
                // delayRepeats is the gap between volumes, so the controller's period is
                // volumeDuration + delayRepeats. The engine holds the settings this method was
                // called with, so its volume duration matches the settings here.
                final double volumeDurationMs =
                        ((AcquisitionEngineScape) model_.acquisitions()).computeVolumeDuration();
                final double volumeIntervalMs = settings.timePointIntervalSec() * 1000.0;
                delayRepeats = volumeIntervalMs - volumeDurationMs;
                numVolumesPerTrigger = settings.numTimePoints();
            }
            // TODO: ported from 1.4 but unreachable in both plugins. Hardware time points only
            // engage when the interval is under volumeDuration + 750 ms, and the timepoint
            // duration equals the volume duration outside multiple positions, which hardware
            // time points disable. That bounds delayRepeats below 750. This becomes reachable
            // only if something lets the user force hardware time points at any interval.
            if (delayRepeats > 32000) {
                model_.logging().reportError("Cannot use hardware time points with a time point "
                        + "interval this large; the controller cannot hold the delay.");
                return false;
            }
            scanner_.setSPIMDelayBeforeRepeat(delayRepeats);
            scanner_.setSPIMNumRepeats(numVolumesPerTrigger);

            scanner_.setSPIMDelayBeforeSide(
                    settings.stageScan().enabled() ? 0  // minimal delay on micro-mirror card for stage scanning (can't actually be less than 2ms but this will get as small as possible)
                            : settings.volume().delayBeforeView()); // this is the usual behavior
        }
        // the calibrated imaging plane (piezo microns); the galvo/sheet must sit here in every
        // acquisition mode so all modes image the same plane (issue #404)
        final double imagingCenter =
                model_.acquisitions().settings().sheetCalibration().imagingCenter();
        double piezoCenter;
        if (settings.stageScan().enabled()) {
            // for stage scanning we define the piezo position to be the home position (normally 0)
            // this is basically required for interleaved mode (otherwise piezo would be moving every slice)
            //    and by convention we'll do it for all stage scanning
            piezoCenter = piezo_.getHomePosition() * 1000.0; // convert mm to um
        } else {
            // TODO: add centerAtCurrentZ to acqSettings
            final boolean centerAtCurrentZ = false;
            if (centerAtCurrentZ) {
                piezoCenter = piezo_.getPosition(); //positions_.getUpdatedPosition(piezoDevice, Joystick.Directions.NONE);
            } else {
                piezoCenter = imagingCenter;
            }
        }

        // if we set piezoAmplitude to 0 here then sliceAmplitude will also be 0
        double piezoAmplitude;
        if (settings.stageScan().enabled() || settings.acquisitionMode() == AcquisitionMode.NO_SCAN) {
            piezoAmplitude = 0.0;
        } else {
            piezoAmplitude = (settings.volume().slicesPerView() - 1) * settings.volume().sliceStepSize();
        }

        // how many slices we ask the hardware for, which is one more than the number of images
        // wanted in synchronous/overlap mode.
        // Only the hardware time point event factories are sized from this same count, because
        // only they describe one continuous burst in which the extra trigger's frame reads out.
        // The software time point factories stay sized to the images wanted, and must: each of
        // their volumes is its own camera sequence that ends before the extra trigger's frame
        // arrives, so arming for this count instead would wait for a frame per volume that never
        // comes.
        final int numSlicesHW = CameraTriggers.slicesPerVolume(settings);

        // tweak the piezo parameters if we are using synchronous/overlap mode
        // object is to get exact same piezo/scanner positions in first N frames (piezo/scanner will move to N+1st position but no image taken)
        // amplitude is (N-1)*stepSize and artificially make N*stepSize instead => multiply amplitude by (N)/(N-1)
        // offset shifts by half a step
        final CameraMode cameraMode = settings.cameraMode();
        if (cameraMode == CameraMode.OVERLAP) {
            // the number of images wanted, not the number of triggers: the extra trigger moves the
            // piezo to a position no wanted image is taken at
            final int numSlices = settings.volume().slicesPerView();
            if (numSlices > 1) {
                piezoAmplitude *= numSlices / (numSlices - 1.0);
            }
            // was piezoCenter += piezoAmplitude/(2*numSlicesHW) which isn't quite the same but close enough that nobody probably noticed
            piezoCenter += settings.volume().sliceStepSize() / 2;
        }

        // FIXME: more light sheet setup
        //final double slope1 = settings.sliceCalibration(1).sliceSlope();
        //final double slope2 = settings.sliceCalibration(2).sliceSlope();
        double sliceRate = settings.sliceCalibration().slope();//(view == 1) ? slope1 : slope2;
        if (NumberUtils.doublesEqual(sliceRate, 0.0)) {
            model_.logging().reportError("The \"Galvo constant\" is not set, it must not be 0.\n" +
                    "Please update the value on the setup tab.");
            return false;
        }
        //final double offset1 = settings.sliceCalibration(1).sliceOffset() + channelOffset;
        //final double offset2 = settings.sliceCalibration(2).sliceOffset() + channelOffset;
        double sliceOffset = settings.sliceCalibration().offset() + channelOffset; //(view == 1) ? offset1 : offset2;
        double sliceAmplitude = piezoAmplitude / sliceRate;
        // The galvo/sheet offset must always land on the true imaging plane. For a stage scan the
        // piezo convention parks piezoCenter at home (~0); using that here would place the sheet on
        // the home plane instead of the imaging center, so the acquired volume is offset from the
        // stage sweep. diSPIM 1.4 SCOPE never writes the galvo offset during a stage scan (it holds
        // the value the Setup tab computed from the imaging center); LSM writes it at acq time, so
        // derive it from the imaging center here.
        final double galvoCenter = settings.stageScan().enabled() ? imagingCenter : piezoCenter;
        double sliceCenter = (galvoCenter - sliceOffset) / sliceRate;

        // round to nearest 0.0001 degrees, which is approximately the DAC resolution
        sliceAmplitude = NumberUtils.roundToPlace(sliceAmplitude, 4);
        sliceCenter = NumberUtils.roundToPlace(sliceCenter, 4);
        studio_.logs().logMessage("Preparing the slices with slope " + sliceRate + ", offset "
                + sliceOffset + ", piezo center " + piezoCenter + " and slice center " + sliceCenter);

        // Refuse a sweep that would drive the galvo past its travel limits. This has to run here,
        // after the rounding and before either branch below writes to the card. The piezo check
        // further down cannot stand in for it: GALVO_SCAN zeroes piezoAmplitude first, so that
        // check degenerates to validating a center with no sweep around it, and galvo scan is the
        // mode most runs use. A failed property read returns zero for both limits, so an
        // unreadable scanner refuses the sweep rather than passing it through.
        final double halfSweep = Math.abs(sliceAmplitude) / 2;
        if (sliceCenter + halfSweep > scanner_.getMaxDeflectionY()) {
            model_.logging().reportError("Scanner will exceed allowed range in positive direction.");
            return false;
        }
        if (sliceCenter - halfSweep < scanner_.getMinDeflectionY()) {
            model_.logging().reportError("Scanner will exceed allowed range in negative direction.");
            return false;
        }

        if (offsetOnly) {
            scanner_.sa().setOffsetY(sliceCenter);
        } else { // normal case
            // only alternate scan directions if the user is using advanced timing
            //    and user has option enabled on the advanced timing panel
            final boolean oppositeDirections = false;

            scanner_.setSPIMAlternateDirections(oppositeDirections);
            scanner_.setSPIMScanDuration(
                  settings.timing().sliceDurationMs() - settings.timing().delayBeforeScanMs());
            // program the remaining per-slice timing onto the scanner card; diSPIM 1.4 wrote
            // these through the advanced-timing GUI spinner bindings, a path lost in the port,
            // so galvo scans ran on whatever stale timing was left on the card (issue #417)
            scanner_.setSPIMDelayBeforeScan(settings.timing().delayBeforeScanMs());
            scanner_.setSPIMNumScansPerSlice(settings.timing().scansPerSlice());
            scanner_.setSPIMDelayBeforeLaser(settings.timing().delayBeforeLaserMs());
            scanner_.setSPIMLaserDuration(settings.timing().laserTriggerDurationMs());
            scanner_.setSPIMDelayBeforeCamera(settings.timing().delayBeforeCameraMs());
            scanner_.setSPIMCameraDuration(settings.timing().cameraTriggerDurationMs());
            scanner_.sa().setAmplitudeY(sliceAmplitude);
            scanner_.sa().setOffsetY(sliceCenter);
            scanner_.setSPIMNumSlices(numSlicesHW);
            scanner_.setSPIMNumSides(settings.volume().numViews());

            if (settings.volume().firstView() == 1) {
                scanner_.setSPIMFirstSide(ASIScanner.SPIMSide.A);
            } else {
                scanner_.setSPIMFirstSide(ASIScanner.SPIMSide.B);
            }

            // get the piezo card ready
            // need to do this for stage scanning too, which makes sure the piezo amplitude is 0

            // if mode GALVO_SCAN we have computed slice movement as if we
            //   were moving the piezo but now make piezo stay still
            if (settings.acquisitionMode() == AcquisitionMode.GALVO_SCAN) {
                // if we artificially shifted centers due to extra trigger and only moving piezo
                // then move galvo center back to where it would have been
                if (settings.cameraMode() == CameraMode.OVERLAP) {
                    piezoCenter -= settings.volume().sliceStepSize() / 2;
                }
                piezoAmplitude = 0.0;
            }

            double piezoMin = piezo_.getLowerLimit() * 1000;
            double piezoMax = piezo_.getUpperLimit() * 1000;

            if (NumberUtils.outsideRange(piezoCenter - piezoAmplitude / 2, piezoMin, piezoMax)
                    || NumberUtils.outsideRange(piezoCenter + piezoAmplitude / 2, piezoMin, piezoMax)) {
                model_.logging().reportError("Imaging piezo for view " + view +
                        " would travel outside the piezo limits during acquisition.");
                return false;
            }

            // round to nearest 0.001 micron, which is approximately the DAC resolution
            piezoAmplitude = NumberUtils.roundToPlace(piezoAmplitude, 3);
            piezoCenter = NumberUtils.roundToPlace(piezoCenter, 3);
            piezo_.sa().setAmplitude(piezoAmplitude);
            piezo_.sa().setOffset(piezoCenter);

            if (!settings.stageScan().enabled()) {
                if (settings.acquisitionMode() == AcquisitionMode.GALVO_SCAN) {
                    // the piezo holds the imaging center for the whole galvo scan, but arming alone
                    // doesn't move it there: the move happens when the state machine fires, inside
                    // the delay-before-view window, so the first slice can be exposed with the
                    // piezo still in transit (issue #407); settle it here before arming instead
                    // TODO: consider pre-positioning the other acquisition modes too (piezo stacks
                    //   start at piezoCenter - piezoAmplitude / 2, so this would only halve the jump)
                    piezo_.setPosition(piezoCenter);
                    piezo_.waitForDevice();
                }
                piezo_.setSPIMNumSlices(numSlicesHW);
                piezo_.setSPIMState(ASIPiezo.SPIMState.ARMED);
            }

            // TODO figure out what we should do with piezo illumination/center position during stage scan
            // set up stage scan parameters if necessary
            if (settings.stageScan().enabled()) {
                // TODO update UI to hide image center control for stage scanning
                // diSPIM 1.4 homes the imaging piezo here only for the dual-objective (non-SCOPE)
                // build; for SCOPE it deliberately "leaves the piezo the way it is" (ControllerUtils
                // line 983). Homing it drives ImagingFocus to 0, off the imaging plane, so the whole
                // stage scan is defocused (issue #404 symptom 1). Instead put the piezo at the
                // imaging center; LSM can't assume the Setup tab already positioned it there.
                // The limit check above validated the home position (piezoCenter), not this target,
                // so guard imagingCenter explicitly before commanding the move (galvo/no-scan get
                // this for free since there piezoCenter == imagingCenter).
                if (NumberUtils.outsideRange(imagingCenter, piezoMin, piezoMax)) {
                    model_.logging().reportError("The imaging center (" + imagingCenter
                            + " µm) is outside the imaging piezo limits; "
                            + "cannot position the piezo for stage scanning.");
                    return false;
                }
                piezo_.setPosition(imagingCenter);
                piezo_.waitForDevice();
            }

            final boolean isInterleaved = settings.stageScan().enabled()
                    && settings.acquisitionMode() == AcquisitionMode.STAGE_SCAN_INTERLEAVED;

            // even though we have moved piezos to home position let's still tell firmware
            //    not to move piezos anywhere (i.e. maybe setting "home disable" to true doesn't have any really effect)
            scanner_.setSPIMPiezoHomeDisable(isInterleaved);

            // set interleaved sides flag low unless we are doing interleaved stage scan
            scanner_.setSPIMInterleaveSides(isInterleaved);

            // SCAPE leaves the galvo's x axis alone, so no sheet width or offset is sent
        }
        return true;
    }

    private boolean cleanUpControllerAfterAcquisitionSide(
            final int view,
            final boolean movePiezo,
            final double piezoPosition) {

        // make sure SPIM state machine is stopped
        scanner_.setSPIMState(ASIScanner.SPIMState.IDLE);

        // NB: no sheet width/offset to restore here; SCAPE never writes the galvo x-axis
        // (see prepareControllerForAcquisitionSide), so nothing can have clobbered it.

        // move piezo back to desired position
        if (movePiezo) {
            piezo_.setPosition(piezoPosition);
        }

        // make sure we stop SPIM and SCAN state machines every time we trigger controller (in AcquisitionPanel code)
        return true;
    }

    public boolean setupHardwareChannelSwitching(final ScapeAcquisitionSettings settings) {

        ChannelMode channelMode = settings.channels().mode();

        // PLogic can only handle up to 4 channels
        if ((settings.channels().count() > 4) &&
                (channelMode == ChannelMode.VOLUME_HW || channelMode == ChannelMode.SLICE_HW)) {
            model_.logging().reportError("PLogic card cannot handle more than 4 channels for hardware switching.");
            return false;
        }

        // set up clock for counters
        switch (channelMode) {
            case SLICE_HW:
                plcLaser_.setPreset(17);
                break;
            case VOLUME_HW:
                if (settings.volume().firstView() == 1) {
                    plcLaser_.setPreset(18); // A first
                } else {
                    plcLaser_.setPreset(26); // B first
                }
                break;
            default:
                model_.logging().reportError("Unknown multichannel mode for hardware switching.");
                return false;
        }

        // three channels need preset 60, the mod3 counter that can be reset, from firmware 3.51 on
        final boolean canResetCounter =
                settings.channels().count() != 3 || plcLaser_.hasPreset60();

        // set up hardware counter
        switch (settings.channels().count()) {
            case 1:
                plcLaser_.setPreset(22); // no counter
                break;
            case 2:
                plcLaser_.setPreset(21); // mod2 counter
                break;
            case 3:
                plcLaser_.setPreset(canResetCounter ? 60 : 16); // mod3 counter
                break;
            case 4:
                plcLaser_.setPreset(15); // mod4 counter
                break;
            default:
                model_.logging().reportError("Hardware channel switching only supports 1-4 channels");
                return false;
        }

        // speed things up by turning off updates, will restore value later
        final boolean editCellUpdates = plcLaser_.isAutoUpdateCellsOn();
        if (editCellUpdates) {
            plcLaser_.setAutoUpdateCells(false);
        }

        // make sure the counters get reset on the acquisition start flag
        if (canResetCounter) {
            plcLaser_.setPointerPosition(counterLSBAddr);
            plcLaser_.setCellInput(3, acquisitionFlagAddr + ASIPLogic.addrEdge);
            plcLaser_.setPointerPosition(counterMSBAddr);
            plcLaser_.setCellInput(3, acquisitionFlagAddr + ASIPLogic.addrEdge);
        }

        // there are 2 separate 7-channel cases different in the property value for "PLogicMode"
        // 1. (original) with 7-channel laser on own PLogic card, seems to have some odd things that I won't change including only uses 6 lasers
        // 2. (newer) with 7-channel TTL-triggered on PLogic card shared with single camera trigger output (i.e. not dual-view system)
        // however they share some things like using cells 17-24 and building a 3-input LUT which code is just copy/paste right now
        final boolean isSevenChannelShutter = plcLaser_.getShutterMode() == ASIPLogic.ShutterMode.SEVEN_CHANNEL_SHUTTER;
        final boolean isSevenChannelShutterTTL = plcLaser_.getShutterMode() == ASIPLogic.ShutterMode.SEVEN_CHANNEL_TTL_SHUTTER;

        if (isSevenChannelShutter) {
            // original special 7-channel case
            if (plcLaser_.getNumCells() < 24) {
                plcLaser_.setAutoUpdateCells(editCellUpdates); // restore update setting
                model_.logging().reportError("Require 24-cell PLC firmware to use hardware channel switching with 7-channel shutter");
                return false;
            }

            // make sure cells 17-24 are controlling BNCs 1-8
            plcLaser_.setPreset(ASIPLogic.Preset.BNC1_8_ON_17_24);

            // now set cells 17-22 so they reflect the counter state used to track state as well as the global laser trigger
            // NB that this only uses 6 lasers (we need 2 free BNCs, BNC#7 for FW trigger and BNC#8 for supplemental X trigger
            for (int laserNum = 1; laserNum < 7; ++laserNum) {
                plcLaser_.setPointerPosition(laserNum + 16);
                plcLaser_.setCellType(ASIPLogic.CellType.LUT3);
                int lutValue = 0;
                // populate a 3-input lookup table with the combinations of lasers present
                // the LUT "MSB" is the laserTrigger, then the counter MSB, then the counter LSB
                for (int channelNum = 0; channelNum < settings.channels().count(); ++channelNum) {
                    if (doesPLogicChannelIncludeLaser(laserNum, settings.channels().used()[channelNum], settings.channels().group())) {
                        lutValue += (int) Math.pow(2, channelNum + 4);  // LUT adds 2^(code in decimal) for each setting, but trigger is MSB of this code
                    }
                }
                plcLaser_.setCellConfig(lutValue);
                plcLaser_.setCellInput(1, counterLSBAddr);
                plcLaser_.setCellInput(2, counterMSBAddr);
                plcLaser_.setCellInput(3, laserTriggerAddress);
            }
        } else if (isSevenChannelShutterTTL) {
            // new 7-channel case with camera trigger on BNC #8
            if  (plcLaser_.getNumCells() < 24) {
                plcLaser_.setAutoUpdateCells(editCellUpdates); // restore update setting
                model_.logging().reportError("Require 24-cell PLC firmware to use hardware channel switching with 7-channel shutter");
                return false;
            }

            // set cells 17-24 to control BNCs 1-8, but then immediately change BNC8 to reflect camera (firmware ensures all are set to push-pull outputs)
            // note that the device adapter should have already set BNC8 to be the camera so this is just resetting it
            plcLaser_.setPreset(ASIPLogic.Preset.BNC1_8_ON_17_24);
            final int addrFrontPanel8 = 40;
            final int addrInternalTTLCameraA = 41;
            plcLaser_.setPointerPosition(addrFrontPanel8); // address 40 is front panel #8
            plcLaser_.setCellType(ASIPLogic.CellType.OUTPUT_PUSH_PULL);
            plcLaser_.setCellConfig(addrInternalTTLCameraA); // address 41 is internal TTL0 signal for CameraA

            // now set cells 17-23, so they reflect the counter state used to track state as well as the global laser trigger
            for (int laserNum = 1; laserNum <= 7; ++laserNum) {
                plcLaser_.setPointerPosition(laserNum + 16);
                plcLaser_.setCellType(ASIPLogic.CellType.LUT3);
                int lutValue = 0;
                // populate a 3-input lookup table with the combinations of lasers present
                // the LUT "MSB" is the laserTrigger, then the counter MSB, then the counter LSB
                for (int channelNum = 0; channelNum < settings.channels().count(); ++channelNum) {
                    if (doesPLogicChannelIncludeLaser(laserNum,
                          settings.channels().used()[channelNum],
                          settings.channels().group())) {
                        // LUT adds 2^(code in decimal) for each setting, but trigger is MSB of this code
                        lutValue += (int) Math.pow(2, channelNum + 4);
                    }
                }
                plcLaser_.setCellConfig(lutValue);
                plcLaser_.setCellInput(1, counterLSBAddr);
                plcLaser_.setCellInput(2, counterMSBAddr);
                plcLaser_.setCellInput(3, laserTriggerAddress);
            }

        } else {
            // original 4-channel mode
            // initialize cells 13-16 which control BNCs 5-8
            for (int cellNum = 13; cellNum <= 16; cellNum++) {
                plcLaser_.setPointerPosition(cellNum);
                plcLaser_.setCellType(ASIPLogic.CellType.AND4);
                plcLaser_.setCellInput(2, laserTriggerAddress);
                // note that PLC diSPIM assumes "laser + side" output mode is selected for micro-mirror card
            }

            // identify BNC from the preset and set counter inputs for 13-16 appropriately
            boolean[] hardwareChannelUsed = new boolean[4]; // initialized to all false
            for (int channelNum = 0; channelNum < settings.channels().count(); channelNum++) {
                // we already know there are between 1 and 4 channels
                int outputNum = getPLogicOutputFromChannel(settings.channels().used()[channelNum], settings.channels().group());
                // TODO: handle case where we have multiple simultaneous outputs, e.g. outputs 6/7 together
                // Note: harsh recently asked about double triggering, but ended up needing to split 1-4
                if (outputNum < 5) {  // check for error in getPLogicOutputFromChannel()
                    // restore update setting
                    plcLaser_.setAutoUpdateCells(editCellUpdates);
                    return false;  // already displayed error
                }
                // make sure we don't have multiple Micro-Manager channels using same hardware channel
                if (hardwareChannelUsed[outputNum - 5]) {
                    // restore update setting
                    plcLaser_.setAutoUpdateCells(editCellUpdates);
                    model_.logging().reportError("Multiple channels cannot use same laser for PLogic triggering");
                    return false;
                } else {
                    hardwareChannelUsed[outputNum - 5] = true;
                }
                plcLaser_.setPointerPosition(outputNum + 8);
                plcLaser_.setCellInput(1, ASIPLogic.addrInvert); // enable this AND4
                // if we are doing per-volume switching with side B first then counter will start at 1 instead of 0
                // the following lines account for this by incrementing the channel number "match" by 1 in this special case
                int adjustedChannelNum = channelNum;
                if (channelMode == ChannelMode.VOLUME_HW && !(settings.volume().firstView() == 1)) {
                    adjustedChannelNum = (channelNum + 1) % settings.channels().count();
                }
                // map the channel number to the equivalent addresses for the AND4
                // inputs should be either 3 (for LSB high) or 67 (for LSB low)
                //                     and 4 (for MSB high) or 68 (for MSB low)
                final int in3 = (adjustedChannelNum & 0x01) > 0 ? counterLSBAddr : counterLSBAddr + ASIPLogic.addrInvert;
                final int in4 = (adjustedChannelNum & 0x02) > 0 ? counterMSBAddr : counterMSBAddr + ASIPLogic.addrInvert;
                plcLaser_.setCellInput(3, in3);
                plcLaser_.setCellInput(4, in4);
            }

            // make sure cells 13-16 are controlling BNCs 5-8
            plcLaser_.setPreset(ASIPLogic.Preset.BNC5_8_ON_13_16);
        }

        // restore update setting
        plcLaser_.setAutoUpdateCells(editCellUpdates);
        return true;
    }

    public boolean triggerControllerStartAcquisition(final AcquisitionMode acqMode) {
        switch (acqMode) {
            case STAGE_SCAN:
            case STAGE_SCAN_INTERLEAVED:
            case STAGE_SCAN_UNIDIRECTIONAL:
                // for stage scan we send trigger to stage card, which sends
                // hardware trigger to the micro-mirror card
                scanner_.setSPIMState(ASIScanner.SPIMState.ARMED);
                xyStage_.setScanState(ASIXYStage.ScanState.RUNNING);
                break;
            case GALVO_SCAN:
            case NO_SCAN:
                scanner_.setSPIMState(ASIScanner.SPIMState.RUNNING);
                break;
            default:
                model_.logging().reportError("Unknown acquisition mode: " + acqMode);
                return false;
        }
        return true;
    }

    // TODO: "output 5 only" needs to be added to ASIPLogic
    private int getPLogicOutputFromChannel(final ChannelSpec channel, final String channelGroup) {
        try {
            Configuration configData = core_.getConfigData(channelGroup, channel.getName());
            if (!configData.isPropertyIncluded(plcLaser_.getDeviceName(), "OutputChannel")) {
                model_.logging().reportError("Channel preset \"" + channel.getName() + "\" (group \""
                        + channelGroup + "\") must include the PLogic \"OutputChannel\" property to use "
                        + "hardware channel switching. Edit the preset in the main Micro-Manager window and "
                        + "set \"OutputChannel\" to one of \"output 5 only\" through \"output 8 only\".");
                return 0;
            }
            final String setting = configData.getSetting(plcLaser_.getDeviceName(), "OutputChannel").getPropertyValue();
            if (setting.equals("output 5 only")) {
                return 5;
            } else if (setting.equals("output 6 only")) {
                return 6;
            } else if (setting.equals("output 7 only")) {
                return 7;
            } else if (setting.equals("output 8 only")) {
                return 8;
            } else {
                model_.logging().reportError("Channel preset \"" + channel.getName() + "\" (group \""
                        + channelGroup + "\") has PLogic \"OutputChannel\" set to \"" + setting + "\"; it "
                        + "must be one of \"output 5 only\" through \"output 8 only\" (each channel a "
                        + "distinct output 5-8). Edit the preset in the main Micro-Manager window.");
                return 0;
            }
        } catch (Exception e) {
            model_.logging().reportError(e, "Could not get PLogic output from channel");
            return 0;
        }
    }

    // TODO: make a variable for "OutputChannel", was in Properties before (this value is always the same)
    private boolean doesPLogicChannelIncludeLaser(final int laserNum, final ChannelSpec channel, final String channelGroup) {
        try {
            Configuration configData = core_.getConfigData(channelGroup, channel.getName());
            if (!configData.isPropertyIncluded(plcLaser_.getDeviceName(), "OutputChannel")) {
                model_.logging().reportError("Must include PLogic \"OutputChannel\" in preset for hardware switching");
                return false;
            }
            String setting = configData.getSetting(plcLaser_.getDeviceName(), "OutputChannel").getPropertyValue();
            return setting.contains(String.valueOf(laserNum));
        } catch (Exception e) {
            model_.logging().reportError(e, "Could not get PLogic output from channel");
            return false;
        }
    }

    /**
     * Gets the actual step size for stage scanning acquisitions.
     * Only valid after call to prepareControllerForAcquisition().
     * @return
     */
    public double getActualStepSizeUm() {
        return actualStepSizeUm_;
    }

    public void stopSPIMStateMachines() {
        scanner_.setSPIMState(ASIScanner.SPIMState.IDLE);
        if (acqSettings_.stageScan().enabled()) {
            // give the stage scan 5 seconds to clean itself up, after which we stop it
            // once all images come in there is still a time when stage is moving back to its start/center position
            final int timeoutStageScanCleanupMs = 5000;
            final long deadline = System.currentTimeMillis() + timeoutStageScanCleanupMs;
            boolean done = false;
            while (!done) {
                done = xyStage_.getScanStateForceRefresh() == ASIXYStage.ScanState.IDLE;
                if (!done && System.currentTimeMillis() > deadline) {
                    xyStage_.setScanState(ASIXYStage.ScanState.IDLE); // force-set to idle
                    studio_.logs().logError("Force-set XY stage scan to IDLE state with stage speed "
                            + xyStage_.getSpeedX() + ".");
                    done = true;
                }
                if (!done) {
                    try {
                        Thread.sleep(25); // still waiting...
                    } catch (InterruptedException e) {
                        // ignore => only need to busy wait
                    }
                }
            }
        }
    }

}
