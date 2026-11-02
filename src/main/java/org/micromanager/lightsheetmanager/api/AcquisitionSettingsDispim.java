package org.micromanager.lightsheetmanager.api;

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
     * Returns the immutable TimingSettings instance.
     *
     * @return immutable TimingSettings instance.
     */
    TimingSettings timing();

    /**
     * Returns the immutable SliceSettings instance.
     *
     * @return immutable SliceSettings instance.
     */
    SliceSettings slice();

    /**
     * Returns the immutable SliceSettingsLightSheet instance.
     *
     * @return immutable SliceSettingsLightSheet instance.
     */
    SliceSettingsLightSheet sliceLS();

    /**
     * Returns the immutable SheetCalibration instance.
     *
     * @return immutable SheetCalibration instance.
     */
    SheetCalibration sheetCalibration(final int view);

    /**
     * Returns the immutable SliceCalibration instance.
     *
     * @return immutable SliceCalibration instance.
     */
    SliceCalibration sliceCalibration(final int view);

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
