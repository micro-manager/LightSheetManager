package org.micromanager.lightsheetmanager.api;

import org.micromanager.lightsheetmanager.api.data.AcquisitionMode;
import org.micromanager.lightsheetmanager.api.data.CameraData;
import org.micromanager.lightsheetmanager.api.data.CameraMode;
import org.micromanager.lightsheetmanager.api.data.SaveMode;
import org.micromanager.lightsheetmanager.api.internal.DefaultAutofocusSettings;

/**
 * Base acquisition settings for all microscopes.
 */
public interface AcquisitionSettings {

    interface Builder<T extends Builder<T>>  {

        /**
         * Sets the save directory.
         *
         * @param directory the directory
         */
        T saveDirectory(final String directory);

        /**
         * Sets the folder name.
         *
         * @param name the name of the folder
         */
        T saveNamePrefix(final String name);

        /**
         * Sets the plugin to save images during an acquisition.
         *
         * @param state true to save images during an acquisition
         */
        T saveImagesDuringAcquisition(final boolean state);

        /**
         * Sets the acquisition to demo mode.
         *
         * @param state true if in demo mode
         */
        T demoMode(final boolean state);

        /**
         * Sets the save mode for the acquisition.
         *
         * @param saveMode the save mode
         */
        T saveMode(final SaveMode saveMode);

        /**
         * Sets the camera mode.
         *
         * @param mode the camera mode
         * @return {@code this} builder
         */
        T cameraMode(final CameraMode mode);

        /**
         * Sets the imaging camera order.
         *
         * @param order the imaging camera order
         * @return {@code this} builder
         */
        T imagingCameraOrder(final CameraData[] order);

        /**
         * Sets the acquisition to use multiple positions.
         *
         * @param state true to use multiple positions
         * @return {@code this} builder
         */
        T useMultiplePositions(final boolean state);

        /**
         * Sets the delay after a move when using multiple positions.
         *
         * @param postMoveDelay the delay in milliseconds
         * @return {@code this} builder
         */
        T postMoveDelay(final int postMoveDelay);

        /**
         * Sets the acquisition to use time points.
         *
         * @param state true to use time points
         * @return {@code this} builder
         */
        T useTimePoints(final boolean state);

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
         * Returns the autofocus settings builder.
         *
         * @return the autofocus settings builder
         */
        DefaultAutofocusSettings.Builder autofocusBuilder();

        /**
         * Returns this builder instance, cast to the concrete subtype.
         * Used internally so that setter calls in the base builder return
         * the concrete builder type for method chaining.
         *
         * @return this builder, as {@code T}
         */
        T self();

        /**
         * Creates an immutable instance of DefaultAcquisitionSettings
         *
         * @return Immutable version of DefaultAcquisitionSettings
         */
        AcquisitionSettings build();
    }

    // TODO: impl
    /**
     * Returns a builder initialized with the current settings.
     *
     * @return a builder to create a modified copy of these settings
     */
    //Builder copyBuilder();

    /**
     * Returns the save name prefix.
     *
     * @return the save name prefix.
     */
    String saveNamePrefix();

    /**
     * Returns the save directory.
     *
     * @return the save directory.
     */
    String saveDirectory();

    /**
     * Returns true if saving images during an acquisition.
     *
     * @return true if saving images during an acquisition.
     */
    boolean isSavingImagesDuringAcquisition();

    /**
     * Returns true if using demo mode.
     *
     * @return true if using demo mode
     */
    boolean demoMode();

    /**
     * Returns the save mode of the acquisition.
     *
     * @return the save mode of the acquisition.
     */
    SaveMode saveMode();

    /**
     * Returns the camera mode.
     *
     * @return the camera mode.
     */
    CameraMode cameraMode();

    /**
     * Returns the imaging camera order.
     *
     * @return the imaging camera order
     */
    CameraData[] imagingCameraOrder();

    /**
     * Returns true if using multiple positions.
     *
     * @return true if using multiple positions.
     */
    boolean isUsingMultiplePositions();

    /**
     * Returns the post move delay in milliseconds.
     *
     * @return the post move delay in milliseconds.
     */
    int postMoveDelay();

    /**
     * Returns true if using time points.
     *
     * @return true if using time points.
     */
    boolean isUsingTimePoints();

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

    /**
     * Returns the acquisition mode.
     *
     * @return the acquisition mode.
     */
    AcquisitionMode acquisitionMode();

    /**
     * Returns the autofocus settings.
     *
     * @return the autofocus settings
     */
    DefaultAutofocusSettings autofocus();

    /**
     * Returns the immutable ChannelSettings instance.
     *
     * @return immutable ChannelSettings instance.
     */
    ChannelSettings channels();
}
