package org.micromanager.lightsheetmanager.api;

import org.micromanager.lightsheetmanager.api.data.AutofocusMode;
import org.micromanager.lightsheetmanager.api.data.AutofocusType;

/**
 * Autofocus settings.
 */
public interface AutofocusSettings {

    /**
     * Returns a builder initialized with the current settings.
     *
     * @return a builder to create a modified copy of these settings
     */
    Builder copyBuilder();

    /**
     * Returns true if autofocus is enabled.
     *
     * @return true if autofocus is enabled
     */
    boolean enabled();

    /**
     * Returns the number of images used in for autofocus routine.
     *
     * @return the number of images
     */
    int numImages();

    /**
     * Returns the step size between images in microns.
     *
     * @return the step size in microns
     */
    double stepSizeUm();

    /**
     * Returns the autofocus mode being used.
     *
     * @return the autofocus mode
     */
    AutofocusMode mode();

    /**
     * Returns the type of scoring algorithm used for autofocus.
     *
     * @return the type of scoring algorithm
     */
    AutofocusType scoringMethod();

    /**
     * Returns the channel autofocus is being run on.
     *
     * @return the autofocus channel
     */
    String channel();

    boolean showGraph();

    boolean showImages();

    interface Builder {

        /**
         * Enable or disable autofocus.
         *
         * @param state true to enable autofocus
         */
        Builder enabled(final boolean state);

        /**
         * Sets the number of images to capture in the autofocus routine.
         *
         * @param numImages the number of images
         */
        Builder numImages(final int numImages);

        /**
         * Sets the spacing between images in the autofocus routine.
         *
         * @param stepSize the step size in microns
         */
        Builder stepSizeUm(final double stepSize);

        /**
         * Set to {@code true} to show the images in the live view window.
         *
         * @param state {@code true} to show images
         */
        Builder showImages(final boolean state);

        /**
         * Set to {@code true} to show a graph of the data.
         *
         * @param state {@code true} to show the graph
         */
        Builder showGraph(final boolean state);

        /**
         * Selects whether to fix the piezo or the sheet for an autofocus routine.
         *
         * @param mode the autofocus mode
         */
        Builder mode(final AutofocusMode mode);

        /**
         * Sets the type of scoring algorithm to use when running autofocus.
         *
         * @param type the scoring algorithm
         */
        Builder scoringMethod(final AutofocusType type);

        /**
         * Set the channel to run the autofocus routine on.
         *
         * @param channel the channel to run autofocus on
         */
        Builder channel(final String channel);

        /**
         * Creates an immutable instance of AutofocusSettings
         *
         * @return Immutable version of AutofocusSettings
         */
        AutofocusSettings build();
    }

}
