package org.micromanager.lightsheetmanager.api.internal;

import org.micromanager.lightsheetmanager.api.AutofocusSettings;
import org.micromanager.lightsheetmanager.api.data.AutofocusMode;
import org.micromanager.lightsheetmanager.api.data.AutofocusType;

import java.util.Objects;

public class DefaultAutofocusSettings implements AutofocusSettings {

    private final boolean enabled;
    private final int numImages;
    private final double stepSizeUm;
    private final boolean showImages;
    private final boolean showGraph;
    private final AutofocusMode mode;
    private final AutofocusType scoringMethod;
    private final String channel;

    private DefaultAutofocusSettings(Builder builder) {
        enabled =  builder.enabled;
        numImages = builder.numImages;
        stepSizeUm = builder.stepSizeUm;
        showImages = builder.showImages;
        showGraph = builder.showGraph;
        mode = builder.mode;
        scoringMethod = builder.scoringMethod;
        channel = builder.channel;
    }

    // Note: used by GSON library for deserialization
    private DefaultAutofocusSettings() {
        this(new Builder());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(AutofocusSettings settings) {
        Objects.requireNonNull(settings, "Cannot copy from null settings");
        return new Builder(settings);
    }

    @Override
    public Builder copyBuilder() {
        return new Builder(this);
    }

    @Override
    public boolean enabled() {
        return enabled;
    }

    /**
     * Returns the number of images used for autofocus routine.
     *
     * @return the number of images
     */
    @Override
    public int numImages() {
        return numImages;
    }

    /**
     * Returns the step size between images in microns.
     *
     * @return the step size in microns
     */
    @Override
    public double stepSizeUm() {
        return stepSizeUm;
    }

    /**
     * Returns the autofocus mode being used.
     *
     * @return the autofocus mode
     */
    @Override
    public AutofocusMode mode() {
        return mode;
    }

    /**
     * Returns {@code true} if showing images in the live view window.
     *
     * @return {@code true} if displaying images
     */
    public boolean showImages() {
        return showImages;
    }

    /**
     * Returns {@code true} if the graph will be displayed after the autofocus routine.
     *
     * @return {@code true} if displaying the graph
     */
    public boolean showGraph() {
        return showGraph;
    }

    /**
     * Returns the type of scoring algorithm used for autofocus.
     *
     * @return the type of scoring algorithm
     */
    @Override
    public AutofocusType scoringMethod() {
        return scoringMethod;
    }

    /**
     * Returns the channel autofocus is being run on.
     *
     * @return the autofocus channel
     */
    @Override
    public String channel() {
        return channel;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        DefaultAutofocusSettings other = (DefaultAutofocusSettings) obj;
        return enabled == other.enabled &&
                numImages == other.numImages &&
                Double.compare(stepSizeUm, other.stepSizeUm) == 0 &&
                showImages == other.showImages &&
                showGraph == other.showGraph &&
                mode == other.mode &&
                scoringMethod == other.scoringMethod &&
                channel.equals(other.channel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, numImages, stepSizeUm,
                showImages, showGraph, mode, scoringMethod, channel);
    }

    @Override
    public String toString() {
        return String.format("%s[enabled=%s, numImages=%s, stepSizeUm=%s, " +
                        "showImages=%s, showGraph=%s, mode=%s, scoringMethod=%s, channel=%s]",
                getClass().getSimpleName(),
                enabled, numImages, stepSizeUm, showImages, showGraph, mode, scoringMethod, channel);
    }

    public static class Builder implements AutofocusSettings.Builder {

        private boolean enabled = false;
        private int numImages = 10;
        private double stepSizeUm = 1.0;
        private boolean showImages = false;
        private boolean showGraph = false;
        private AutofocusMode mode = AutofocusMode.FIXED_PIEZO_SWEEP_SLICE;
        private AutofocusType scoringMethod = AutofocusType.VOLATH5;
        private String channel = "";

        private Builder() {
        }

        private Builder(final AutofocusSettings settings) {
            enabled = settings.enabled();
            numImages = settings.numImages();
            stepSizeUm = settings.stepSizeUm();
            showImages = settings.showImages();
            showGraph = settings.showGraph();
            mode = settings.mode();
            scoringMethod = settings.scoringMethod();
            channel = settings.channel();
        }

        @Override
        public Builder enabled(final boolean state) {
            enabled = state;
            return this;
        }

        /**
         * Sets the number of images to capture in the autofocus routine.
         *
         * @param numImages the number of images
         */
        @Override
        public Builder numImages(final int numImages) {
            this.numImages = numImages;
            return this;
        }

        /**
         * Sets the spacing between images in the autofocus routine.
         *
         * @param stepSize the step size in microns
         */
        @Override
        public Builder stepSizeUm(final double stepSize) {
            stepSizeUm = stepSize;
            return this;
        }

        /**
         * Set to {@code true} to show the images in the live view window.
         *
         * @param state {@code true} to show images
         */
        @Override
        public Builder showImages(boolean state) {
            showImages = state;
            return this;
        }

        /**
         * Set to {@code true} to show a graph of the data.
         *
         * @param state {@code true} to show the graph
         */
        @Override
        public Builder showGraph(boolean state) {
            showGraph = state;
            return this;
        }

        /**
         * Selects whether to fix the piezo or the sheet for an autofocus routine.
         *
         * @param mode the autofocus mode
         */
        @Override
        public Builder mode(final AutofocusMode mode) {
            this.mode = mode;
            return this;
        }

        /**
         * Sets the type of scoring algorithm to use when running autofocus.
         *
         * @param type the scoring algorithm
         */
        @Override
        public Builder scoringMethod(final AutofocusType type) {
            scoringMethod = type;
            return this;
        }

        /**
         * Set the channel to run the autofocus routine on.
         *
         * @param channel the channel to run autofocus on
         */
        @Override
        public Builder channel(final String channel) {
            this.channel = channel;
            return this;
        }

        /**
         * Creates an immutable instance of AutofocusSettings
         *
         * @return Immutable version of AutofocusSettings
         */
        @Override
        public DefaultAutofocusSettings build() {
            return new DefaultAutofocusSettings(this);
        }

    }

}
