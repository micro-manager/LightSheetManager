package org.micromanager.lightsheetmanager.api;

import org.micromanager.lightsheetmanager.api.data.AcquisitionMode;

/**
 * Acquisition settings for SCAPE microscopes.
 */
public interface AcquisitionSettingsScape extends AcquisitionSettings {

    /**
     * Returns a builder initialized with the current settings.
     *
     * @return a builder to create a modified copy of these settings
     */
    Builder copyBuilder();

    /**
     * Returns the immutable DefaultTimingSettings instance.
     *
     * @return immutable DefaultTimingSettings instance.
     */
    TimingSettings timing();

    /**
     * Returns the immutable DefaultVolumeSettings instance.
     *
     * @return immutable DefaultVolumeSettings instance.
     */
    VolumeSettings volume();

    /**
     * Returns the immutable SliceSettings instance.
     *
     * @return immutable SliceSettings instance.
     */
    SliceSettings slice();

    /**
     * Returns the immutable DefaultScanSettings instance.
     *
     * @return immutable DefaultScanSettings instance.
     */
    StageScanSettings stageScan();

    /**
     * Returns the immutable DefaultSheetCalibration instance.
     *
     * @return immutable DefaultSheetCalibration instance.
     */
    SheetCalibration sheetCalibration();

    /**
     * Returns the immutable DefaultSliceCalibration instance.
     *
     * @return immutable DefaultSliceCalibration instance.
     */
    SliceCalibration sliceCalibration();

    /**
     * Returns the acquisition mode.
     *
     * @return the acquisition mode.
     */
    AcquisitionMode acquisitionMode();

    /**
     * Returns true if using time points.
     *
     * @return true if using time points.
     */
    boolean isUsingTimePoints();

    /**
     * Returns true if using hardware time points.
     *
     * @return true if using hardware time points.
     */
    boolean isUsingHardwareTimePoints();

    /**
     * Returns true if using advanced timing settings.
     *
     * @return true if using advanced timing settings.
     */
    boolean isUsingAdvancedTiming();

    /**
     * Returns the number of time points.
     *
     * @return the number of time points.
     */
    int numTimePoints();

    /**
     * Returns the time point interval in seconds.
     *
     * @return the time point interval in seconds.
     */
    double timePointInterval();

    interface Builder<T extends AcquisitionSettings.Builder<T>> extends AcquisitionSettings.Builder<T> {

        /**
         * Sets the acquisition mode.
         * <p>
         * If the mode is a stage scanning mode,
         * set the stage scanning flag to true.
         *
         * @param mode the acquisition mode
         * @return {@code this} builder
         */
        T acquisitionMode(final AcquisitionMode mode);

        /**
         * Sets the acquisition to use time points.
         *
         * @param state true to use time points
         * @return {@code this} builder
         */
        T useTimePoints(final boolean state);

        /**
         * Sets the acquisition to use hardware time points.
         *
         * @param state true to use time points
         * @return {@code this} builder
         */
        T useHardwareTimePoints(final boolean state);

        /**
         * Sets the acquisition to use advanced timing settings.
         *
         * @param state true to use advanced timing settings
         * @return {@code this} builder
         */
        T useAdvancedTiming(final boolean state);

        /**
         * Sets the number of time points.
         *
         * @param numTimePoints the number of time points
         * @return {@code this} builder
         */
        T numTimePoints(final int numTimePoints);

        /**
         * Sets the time point interval between time points in seconds.
         *
         * @param timePointInterval the time point interval in seconds
         * @return {@code this} builder
         */
        T timePointInterval(final double timePointInterval);

        /**
         * Creates a new {@link AcquisitionSettingsScape} instance based on the current configuration.
         *
         * @return a new immutable settings instance
         */
        AcquisitionSettingsScape build();
    }

}
