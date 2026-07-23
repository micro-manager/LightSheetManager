package org.micromanager.lightsheetmanager.api.internal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import org.micromanager.lightsheetmanager.api.AcquisitionSettings;
import org.micromanager.lightsheetmanager.api.ChannelSettings;
import org.micromanager.lightsheetmanager.api.SheetCalibration;
import org.micromanager.lightsheetmanager.api.SliceCalibration;
import org.micromanager.lightsheetmanager.api.SliceSettings;
import org.micromanager.lightsheetmanager.api.StageScanSettings;
import org.micromanager.lightsheetmanager.api.TimingSettings;
import org.micromanager.lightsheetmanager.api.VolumeSettings;
import org.micromanager.lightsheetmanager.api.data.AcquisitionMode;
import org.micromanager.lightsheetmanager.api.data.CameraData;
import org.micromanager.lightsheetmanager.api.data.CameraMode;
import org.micromanager.lightsheetmanager.api.data.SaveMode;

/**
 * Base acquisition settings for all microscopes.
 */
public abstract class BaseAcquisitionSettings implements AcquisitionSettings {

    public abstract static class Builder<T extends Builder<T>> implements AcquisitionSettings.Builder<T> {

        private String saveDirectory_ = System.getProperty("user.home");
        private String saveNamePrefix_ = "Experiment";
        private boolean saveDuringAcq_ = false;
        private boolean demoMode_ = false;
        private SaveMode saveMode_ = SaveMode.ND_TIFF;
        private CameraMode cameraMode_ = CameraMode.EDGE;
        private CameraData[] imagingCameraOrder_ = {};
        private boolean useMultiplePositions_ = false;
        private int postMoveDelay_ = 0;
        private boolean useTimePoints_ = false;
        private int numTimePoints_ = 1;
        private double timePointInterval_ = 0.0;
        private AcquisitionMode acquisitionMode_ = AcquisitionMode.NO_SCAN;

        private DefaultAutofocusSettings.Builder afBuilder_ = DefaultAutofocusSettings.builder();
        private ChannelSettings.Builder channelBuilder_ = DefaultChannelSettings.builder();

        public Builder() {
        }

        public Builder(final AcquisitionSettings settings) {
            saveDirectory_ = settings.saveDirectory();
            saveNamePrefix_ = settings.saveNamePrefix();
            saveDuringAcq_ = settings.isSavingImagesDuringAcquisition();
            demoMode_ = settings.demoMode();
            saveMode_ = settings.saveMode();
            cameraMode_ = settings.cameraMode();
            imagingCameraOrder_ = settings.imagingCameraOrder();
            useMultiplePositions_ = settings.isUsingMultiplePositions();
            postMoveDelay_ = settings.postMoveDelay();
            useTimePoints_ = settings.isUsingTimePoints();
            numTimePoints_ = settings.numTimePoints();
            timePointInterval_ = settings.timePointInterval();
            acquisitionMode_ = settings.acquisitionMode();
            afBuilder_ = settings.autofocus().copyBuilder();
            channelBuilder_ = settings.channels().copyBuilder();
        }

        /**
         * Sets the save directory.
         *
         * @param directory the directory
         */
        @Override
        public T saveDirectory(final String directory) {
            saveDirectory_ = directory;
            return self();
        }

        /**
         * Sets the folder name.
         *
         * @param name the name of the folder
         */
        @Override
        public T saveNamePrefix(final String name) {
            saveNamePrefix_ = name;
            return self();
        }

        /**
         * Sets the plugin to save images during an acquisition.
         *
         * @param state true to save images during an acquisition
         */
        @Override
        public T saveImagesDuringAcquisition(final boolean state) {
            saveDuringAcq_ = state;
            return self();
        }

        /**
         * Sets the acquisition to demo mode.
         *
         * @param state true if in demo mode
         */
        @Override
        public T demoMode(final boolean state) {
            demoMode_ = state;
            return self();
        }

        /**
         * Sets the data saving mode.
         *
         * @param saveMode the save mode
         */
        @Override
        public T saveMode(final SaveMode saveMode) {
            saveMode_ = saveMode;
            return self();
        }

        /**
         * Sets the camera mode.
         *
         * @param mode the camera mode
         * @return {@code this} builder
         */
        @Override
        public T cameraMode(final CameraMode mode) {
            cameraMode_ = mode;
            return self();
        }

        /**
         * Sets the imaging camera order.
         *
         * @param order the imaging camera order
         * @return {@code this} builder
         */
        @Override
        public T imagingCameraOrder(final CameraData[] order) {
            imagingCameraOrder_ = order;
            return self();
        }

        /**
         * Sets the acquisition to use multiple positions.
         *
         * @param state true to use multiple positions
         * @return {@code this} builder
         */
        @Override
        public T useMultiplePositions(final boolean state) {
            useMultiplePositions_ = state;
            return self();
        }

        /**
         * Sets the delay after a move when using multiple positions.
         *
         * @param postMoveDelay the delay in milliseconds
         * @return {@code this} builder
         */
        @Override
        public T postMoveDelay(final int postMoveDelay) {
            postMoveDelay_ = postMoveDelay;
            return self();
        }

        /**
         * Sets the acquisition to use time points.
         *
         * @param state true to use time points
         * @return {@code this} builder
         */
        @Override
        public T useTimePoints(final boolean state) {
            useTimePoints_ = state;
            return self();
        }

        /**
         * Sets the number of time points.
         *
         * @param numTimePoints the number of time points
         * @return {@code this} builder
         */
        @Override
        public T numTimePoints(final int numTimePoints) {
            numTimePoints_ = numTimePoints;
            return self();
        }

        /**
         * Sets the time point interval between time points in seconds.
         *
         * @param timePointInterval the time point interval in seconds
         * @return {@code this} builder
         */
        @Override
        public T timePointInterval(final double timePointInterval) {
            timePointInterval_ = timePointInterval;
            return self();
        }

        /**
         * Sets the acquisition mode.
         * <p>
         * If the mode is a stage scanning mode,
         * set the stage scanning flag to true.
         *
         * @param mode the acquisition mode
         * @return {@code this} builder
         */
        @Override
        public T acquisitionMode(final AcquisitionMode mode) {
            acquisitionMode_ = mode;
            final boolean scanEnabled = (mode == AcquisitionMode.STAGE_SCAN
                    || mode == AcquisitionMode.STAGE_SCAN_INTERLEAVED
                    || mode == AcquisitionMode.STAGE_SCAN_UNIDIRECTIONAL);
            stageScanBuilder().enabled(scanEnabled);
            return self();
        }

        /**
         * Returns the geometry-specific stage-scan sub-builder.
         * Bridges {@link #acquisitionMode} (base) to each concrete geometry's
         * own {@code StageScanSettings.Builder}, which is not itself hoisted yet.
         *
         * @return the stage-scan sub-builder
         */
        protected abstract StageScanSettings.Builder stageScanBuilder();

        @Override
        public DefaultAutofocusSettings.Builder autofocusBuilder() {
            return afBuilder_;
        }

        public ChannelSettings.Builder channelBuilder() {
            return channelBuilder_;
        }

        /**
         * Creates an immutable instance of DefaultAcquisitionSettings
         *
         * @return Immutable version of DefaultAcquisitionSettings
         */
        //@Override
        //public abstract AcquisitionSettings build();

        //public abstract T self();
    }

    /**
     * Creates a Builder populated with settings of this AcquisitionSettings instance.
     *
     * @return AcquisitionSettings.Builder pre-populated with settings of this instance.
     */
//    @Override
//    public AcquisitionSettings.Builder copyBuilder() {
//        return new DefaultAcquisitionSettings.Builder(
//                saveDirectory_, saveNamePrefix_, demoMode_
//        );
//    }

    private final String saveNamePrefix_;
    private final String saveDirectory_;
    private final boolean saveDuringAcq_;
    private final boolean demoMode_;
    private final SaveMode saveMode_;
    private final CameraMode cameraMode_;
    private final CameraData[] imagingCameraOrder_;
    private final boolean useMultiplePositions_;
    private final int postMoveDelay_;
    private final boolean useTimePoints_;
    private final int numTimePoints_;
    private final double timePointInterval_;
    private final AcquisitionMode acquisitionMode_;

    private final DefaultAutofocusSettings autofocus_;
    private final ChannelSettings channels_;

//    public DefaultAcquisitionSettings() {
//        saveNamePrefix_ = "";
//        saveDirectory_ = "";
//        demoMode_ = false;
//    }

    protected BaseAcquisitionSettings(Builder<?> builder) {
        saveDirectory_ = builder.saveDirectory_;
        saveNamePrefix_ = builder.saveNamePrefix_;
        saveDuringAcq_ = builder.saveDuringAcq_;
        demoMode_ = builder.demoMode_;
        saveMode_ = builder.saveMode_;
        cameraMode_ = builder.cameraMode_;
        imagingCameraOrder_ = builder.imagingCameraOrder_.clone();
        useMultiplePositions_ = builder.useMultiplePositions_;
        postMoveDelay_ = builder.postMoveDelay_;
        useTimePoints_ = builder.useTimePoints_;
        numTimePoints_ = builder.numTimePoints_;
        timePointInterval_ = builder.timePointInterval_;
        acquisitionMode_ = builder.acquisitionMode_;
        autofocus_ = builder.afBuilder_.build();
        channels_ = builder.channelBuilder_.build();
    }

    /**
     * Returns the save name prefix.
     *
     * @return the save name prefix.
     */
    @Override
    public String saveNamePrefix() {
        return saveNamePrefix_;
    }

    /**
     * Returns the save directory.
     *
     * @return the save directory.
     */
    @Override
    public String saveDirectory() {
        return saveDirectory_;
    }

    /**
     * Returns true if saving images during an acquisition.
     *
     * @return true if saving images during an acquisition.
     */
    @Override
    public boolean isSavingImagesDuringAcquisition() {
        return saveDuringAcq_;
    }

    /**
     * Returns true if using demo mode.
     *
     * @return true if using demo mode
     */
    @Override
    public boolean demoMode() {
        return demoMode_;
    }

    /**
     * Returns the save mode.
     *
     * @return the save mode
     */
    @Override
    public SaveMode saveMode() {
        return saveMode_;
    }

    /**
     * Returns the camera mode.
     *
     * @return the camera mode
     */
    @Override
    public CameraMode cameraMode() {
        return cameraMode_;
    }

    /**
     * Returns the imaging camera order.
     *
     * @return the imaging camera order
     */
    @Override
    public CameraData[] imagingCameraOrder() {
        return imagingCameraOrder_;
    }

    /**
     * Returns true if using multiple positions.
     *
     * @return true if using multiple positions.
     */
    @Override
    public boolean isUsingMultiplePositions() {
        return useMultiplePositions_;
    }

    /**
     * Returns the post move delay in milliseconds.
     *
     * @return the post move delay in milliseconds.
     */
    @Override
    public int postMoveDelay() {
        return postMoveDelay_;
    }

    /**
     * Returns true if using time points.
     *
     * @return true if using time points.
     */
    @Override
    public boolean isUsingTimePoints() {
        return useTimePoints_;
    }

    /**
     * Returns the number of time points.
     *
     * @return the number of time points.
     */
    @Override
    public int numTimePoints() {
        return numTimePoints_;
    }

    /**
     * Returns the time point interval in seconds.
     *
     * @return the time point interval in seconds.
     */
    @Override
    public double timePointInterval() {
        return timePointInterval_;
    }

    /**
     * Returns the acquisition mode.
     *
     * @return the acquisition mode
     */
    @Override
    public AcquisitionMode acquisitionMode() {
        return acquisitionMode_;
    }

    /**
     * Returns the autofocus settings.
     *
     * @return the autofocus settings
     */
    @Override
    public DefaultAutofocusSettings autofocus() {
        return autofocus_;
    }

    /**
     * Returns the immutable ChannelSettings instance.
     *
     * @return immutable ChannelSettings instance.
     */
    @Override
    public ChannelSettings channels() {
        return channels_;
    }

    @Override
    public String toString() {
        return String.format(
                "%s[saveDirectory=%s, saveNamePrefix=%s, saveDuringAcq=%s, demoMode=%s, saveMode=%s]",
                getClass().getSimpleName(), saveDirectory_, saveNamePrefix_, saveDuringAcq_, demoMode_, saveMode_
        );
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public String toPrettyJson() {
        final Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }

    public static <T extends AcquisitionSettings> T fromJson(final String json, final Class<T> cls) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(ChannelSettings.class, (JsonDeserializer<ChannelSettings>)
                        (jsonElement, typeOfT, context) -> {
                            // This forces Gson to use the concrete implementation class
                            return context.deserialize(jsonElement, DefaultChannelSettings.class);
                        })
                .registerTypeAdapter(TimingSettings.class, (JsonDeserializer<TimingSettings>)
                        (jsonElement, typeOfT, context) -> {
                            // This forces Gson to use the concrete implementation class
                            return context.deserialize(jsonElement, DefaultTimingSettings.class);
                        })
                .registerTypeAdapter(VolumeSettings.class, (JsonDeserializer<VolumeSettings>)
                        (jsonElement, typeOfT, context) -> {
                            // This forces Gson to use the concrete implementation class
                            return context.deserialize(jsonElement, DefaultVolumeSettings.class);
                        })
                .registerTypeAdapter(StageScanSettings.class, (JsonDeserializer<StageScanSettings>)
                        (jsonElement, typeOfT, context) -> {
                            // This forces Gson to use the concrete implementation class
                            return context.deserialize(jsonElement, DefaultStageScanSettings.class);
                        })
                .registerTypeAdapter(SliceSettings.class, (JsonDeserializer<SliceSettings>)
                        (jsonElement, typeOfT, context) -> {
                            // This forces Gson to use the concrete implementation class
                            return context.deserialize(jsonElement, DefaultSliceSettings.class);
                        })
                .registerTypeAdapter(SheetCalibration.class, (JsonDeserializer<SheetCalibration>)
                        (jsonElement, typeOfT, context) -> {
                            // This forces Gson to use the concrete implementation class
                            return context.deserialize(jsonElement, DefaultSheetCalibration.class);
                        })
                .registerTypeAdapter(SliceCalibration.class, (JsonDeserializer<SliceCalibration>)
                        (jsonElement, typeOfT, context) -> {
                            // This forces Gson to use the concrete implementation class
                            return context.deserialize(jsonElement, DefaultSliceCalibration.class);
                        })
                .create();
        return gson.fromJson(json, cls);
    }

//    public static DefaultAcquisitionSettingsDISPIM fromJson(final String json) {
//        return new Gson().fromJson(json, DefaultAcquisitionSettingsDISPIM.class);
//    }

}
