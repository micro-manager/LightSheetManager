package org.micromanager.lightsheetmanager.api;

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

    interface Builder<T extends AcquisitionSettings.Builder<T>> extends AcquisitionSettings.Builder<T> {

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
         * Creates a new {@link AcquisitionSettingsScape} instance based on the current configuration.
         *
         * @return a new immutable settings instance
         */
        AcquisitionSettingsScape build();
    }

}
