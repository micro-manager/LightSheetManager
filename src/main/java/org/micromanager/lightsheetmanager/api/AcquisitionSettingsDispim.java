package org.micromanager.lightsheetmanager.api;

import org.micromanager.lightsheetmanager.api.data.AcquisitionMode;

/**
 * Acquisition settings for diSPIM microscopes.
 */
public interface AcquisitionSettingsDispim extends AcquisitionSettings {

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
     * Returns the immutable DefaultSliceSettings instance.
     *
     * @return immutable DefaultSliceSettings instance.
     */
    SliceSettings slice();

    /**
     * Returns the immutable DefaultSliceSettingsLS instance.
     *
     * @return immutable DefaultSliceSettingsLS instance.
     */
    SliceSettingsLightSheet sliceLS();

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
    SheetCalibration sheetCalibration(final int view);

    /**
     * Returns the immutable DefaultSliceCalibration instance.
     *
     * @return immutable DefaultSliceCalibration instance.
     */
    SliceCalibration sliceCalibration(final int view);

    /**
     * Returns the acquisition mode.
     *
     * @return the acquisition mode.
     */
    AcquisitionMode acquisitionMode();

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

    double liveScanPeriod();

    interface Builder<T extends AcquisitionSettings.Builder<T>> extends AcquisitionSettings.Builder<T> {

        /**
         * Sets the acquisition mode.
         * <p>
         * If the mode is a stage scanning mode,
         * set the stage scanning flag to true.
         *
         * @param acqMode the acquisition mode
         * @return {@code this} builder
         */
        T acquisitionMode(final AcquisitionMode acqMode);

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

        T liveScanPeriod(final double liveScanPeriod);

        /**
         * Creates a new {@link AcquisitionSettingsDispim} instance based on the current configuration.
         *
         * @return a new immutable settings instance
         */
        AcquisitionSettingsDispim build();
    }

}
