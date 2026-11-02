package org.micromanager.lightsheetmanager.model.utils;

import org.micromanager.lightsheetmanager.api.AcquisitionSettings;
import org.micromanager.lightsheetmanager.api.data.AcquisitionMode;
import org.micromanager.lightsheetmanager.api.data.ChannelMode;

public final class StageScanUtils {

    /** This class should not be instantiated. */
    private StageScanUtils() {
        throw new AssertionError("Utility class; do not instantiate.");
    }

    // Compute appropriate motor speed in mm/s for the given stage scanning settings
    public static double computeScanSpeed(final AcquisitionSettings settings) {
        double sliceDurationMs = settings.timing().sliceDurationMs();
        if (settings.acquisitionMode() == AcquisitionMode.STAGE_SCAN_INTERLEAVED) {
            // pretend like our slice takes twice as long so that we move the correct speed
            // this has the effect of halving the motor speed, but keeping the scan distance the same
            sliceDurationMs *= 2;
        }
        final int channelsPerPass = computeScanChannelsPerPass(settings);
        final double speedFactor = GeometryUtils.getStageGeometricSpeedFactor(
                settings.stageScan().firstViewAngle(), settings.volume().firstView() == 1);
        return settings.volume().sliceStepSize() * speedFactor / sliceDurationMs / channelsPerPass;
    }

    // compute how many channels we do in each one-way scan, at least 1 so the speed and the scan
    // line count never divide by 0 when channels are enabled but none is used
    public static int computeScanChannelsPerPass(final AcquisitionSettings settings) {
        return settings.channels().mode() == ChannelMode.SLICE_HW
                ? Math.max(1, settings.channels().count()) : 1;
    }

    /**
     * Compute appropriate acceleration time in ms for the specified motor speed.
     * Set to be 10ms + 0-100ms depending on relative speed to max, all scaled by factor specified on the settings panel
     *
     * @param motorSpeed the scan speed in mm/s
     * @param maxMotorSpeed the maximum speed of the stage in mm/s
     * @param stageScanAccelFactor the factor specified on the settings panel
     * @return the acceleration time in ms
     */
    public static double computeScanAcceleration(final double motorSpeed, final double maxMotorSpeed, final double stageScanAccelFactor) {
        return (10 + 100 * (motorSpeed / maxMotorSpeed)) * stageScanAccelFactor;
    }

}
