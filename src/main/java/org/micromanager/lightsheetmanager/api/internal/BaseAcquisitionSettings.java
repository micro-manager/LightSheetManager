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

    private static final String ILLEGAL_FILENAME_CHARS = "\\/:*?\"<>|";

    /**
     * Returns why the save name cannot be used as a Windows file name, or null when it can.
     *
     * @param name the save name
     * @return the problem, or null
     */
    public static String saveNamePrefixProblem(final String name) {
        if (name == null || name.isEmpty()) {
            return "The save name is empty";
        }
        final char last = name.charAt(name.length() - 1);
        if (last == ' ' || last == '.') {
            return "The save name \"" + name + "\" ends with a space or a period";
        }
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            // c < 32 rejects all ASCII control characters (0x00–0x1F)
            if (c < 32) {
                return "The save name contains a control character";
            }
            if (ILLEGAL_FILENAME_CHARS.indexOf(c) >= 0) {
                return "The save name \"" + name + "\" contains \"" + c
                        + "\", which Windows does not allow in file names";
            }
        }
        return null;
    }

    public abstract static class Builder<T extends Builder<T>> implements AcquisitionSettings.Builder<T> {

        private String saveDirectory = System.getProperty("user.home");
        private String saveNamePrefix = "Experiment";
        private boolean saveDuringAcq = false;
        private boolean demoMode = false;
        private SaveMode saveMode = SaveMode.ND_TIFF;
        private CameraMode cameraMode = CameraMode.EDGE;
        private CameraData[] imagingCameraOrder = {};
        private boolean useMultiplePositions = false;
        private int postMoveDelay = 0;
        private boolean useTimePoints = false;
        private boolean separateTimePoints = false;
        private int numTimePoints = 1;
        private double timePointIntervalSec = 0.0;
        private AcquisitionMode acquisitionMode = AcquisitionMode.NO_SCAN;

        private DefaultAutofocusSettings.Builder afBuilder = DefaultAutofocusSettings.builder();
        private ChannelSettings.Builder channelBuilder = DefaultChannelSettings.builder();
        private VolumeSettings.Builder volumeBuilder_ = DefaultVolumeSettings.builder();

        public Builder() {
        }

        public Builder(final AcquisitionSettings settings) {
            saveDirectory = settings.saveDirectory();
            saveNamePrefix = settings.saveNamePrefix();
            saveDuringAcq = settings.isSavingImagesDuringAcquisition();
            demoMode = settings.demoMode();
            saveMode = settings.saveMode();
            cameraMode = settings.cameraMode();
            imagingCameraOrder = settings.imagingCameraOrder();
            useMultiplePositions = settings.isUsingMultiplePositions();
            postMoveDelay = settings.postMoveDelay();
            useTimePoints = settings.isUsingTimePoints();
            separateTimePoints = settings.isUsingSeparateTimePoints();
            numTimePoints = settings.numTimePoints();
            timePointIntervalSec = settings.timePointIntervalSec();
            acquisitionMode = settings.acquisitionMode();
            afBuilder = settings.autofocus().copyBuilder();
            channelBuilder = settings.channels().copyBuilder();
            volumeBuilder_ = settings.volume().copyBuilder();
        }

        /**
         * Sets the save directory.
         *
         * @param directory the directory
         */
        @Override
        public T saveDirectory(final String directory) {
            saveDirectory = directory;
            return self();
        }

        /**
         * Sets the folder name.
         *
         * @param name the name of the folder
         * @throws IllegalArgumentException if the name cannot be used as a Windows file name
         */
        @Override
        public T saveNamePrefix(final String name) {
            final String problem = saveNamePrefixProblem(name);
            if (problem != null) {
                throw new IllegalArgumentException(problem);
            }
            saveNamePrefix = name;
            return self();
        }

        /**
         * Sets the plugin to save images during an acquisition.
         *
         * @param state true to save images during an acquisition
         */
        @Override
        public T saveImagesDuringAcquisition(final boolean state) {
            saveDuringAcq = state;
            return self();
        }

        /**
         * Sets the acquisition to demo mode.
         *
         * @param state true if in demo mode
         */
        @Override
        public T demoMode(final boolean state) {
            demoMode = state;
            return self();
        }

        /**
         * Sets the data saving mode.
         *
         * @param saveMode the save mode
         */
        @Override
        public T saveMode(final SaveMode saveMode) {
            this.saveMode = saveMode;
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
            cameraMode = mode;
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
            imagingCameraOrder = order;
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
            useMultiplePositions = state;
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
            this.postMoveDelay = postMoveDelay;
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
            useTimePoints = state;
            return self();
        }

        /**
         * Sets the acquisition to write each time point to its own dataset.
         *
         * @param state true to write one dataset per time point
         * @return {@code this} builder
         */
        @Override
        public T separateTimePoints(final boolean state) {
            separateTimePoints = state;
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
            this.numTimePoints = numTimePoints;
            return self();
        }

        /**
         * Sets the time point interval between time points in seconds.
         *
         * @param timePointIntervalSec the time point interval in seconds
         * @return {@code this} builder
         */
        @Override
        public T timePointIntervalSec(final double timePointIntervalSec) {
            this.timePointIntervalSec = timePointIntervalSec;
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
            acquisitionMode = mode;
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
            return afBuilder;
        }

        public ChannelSettings.Builder channelBuilder() {
            return channelBuilder;
        }

        public VolumeSettings.Builder volumeBuilder() {
            return volumeBuilder_;
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

    private final String saveNamePrefix;
    private final String saveDirectory;
    private final boolean saveDuringAcq;
    private final boolean demoMode;
    private final SaveMode saveMode;
    private final CameraMode cameraMode;
    private final CameraData[] imagingCameraOrder;
    private final boolean useMultiplePositions;
    private final int postMoveDelay;
    private final boolean useTimePoints;
    private final boolean separateTimePoints;
    private final int numTimePoints;
    private final double timePointIntervalSec;
    private final AcquisitionMode acquisitionMode;

    private final DefaultAutofocusSettings autofocus;
    private final ChannelSettings channels;
    private final VolumeSettings volume;

//    public DefaultAcquisitionSettings() {
//        saveNamePrefix_ = "";
//        saveDirectory_ = "";
//        demoMode_ = false;
//    }

    protected BaseAcquisitionSettings(Builder<?> builder) {
        saveDirectory = builder.saveDirectory;
        saveNamePrefix = builder.saveNamePrefix;
        saveDuringAcq = builder.saveDuringAcq;
        demoMode = builder.demoMode;
        saveMode = builder.saveMode;
        cameraMode = builder.cameraMode;
        imagingCameraOrder = builder.imagingCameraOrder.clone();
        useMultiplePositions = builder.useMultiplePositions;
        postMoveDelay = builder.postMoveDelay;
        useTimePoints = builder.useTimePoints;
        separateTimePoints = builder.separateTimePoints;
        numTimePoints = builder.numTimePoints;
        timePointIntervalSec = builder.timePointIntervalSec;
        acquisitionMode = builder.acquisitionMode;
        autofocus = builder.afBuilder.build();
        channels = builder.channelBuilder.build();
        volume = builder.volumeBuilder_.build();
    }

    /**
     * Returns the save name prefix.
     *
     * @return the save name prefix.
     */
    @Override
    public String saveNamePrefix() {
        return saveNamePrefix;
    }

    /**
     * Returns the save directory.
     *
     * @return the save directory.
     */
    @Override
    public String saveDirectory() {
        return saveDirectory;
    }

    /**
     * Returns true if saving images during an acquisition.
     *
     * @return true if saving images during an acquisition.
     */
    @Override
    public boolean isSavingImagesDuringAcquisition() {
        return saveDuringAcq;
    }

    /**
     * Returns true if using demo mode.
     *
     * @return true if using demo mode
     */
    @Override
    public boolean demoMode() {
        return demoMode;
    }

    /**
     * Returns the save mode.
     *
     * @return the save mode
     */
    @Override
    public SaveMode saveMode() {
        return saveMode;
    }

    /**
     * Returns the camera mode.
     *
     * @return the camera mode
     */
    @Override
    public CameraMode cameraMode() {
        return cameraMode;
    }

    /**
     * Returns the imaging camera order.
     *
     * @return the imaging camera order
     */
    @Override
    public CameraData[] imagingCameraOrder() {
        return imagingCameraOrder;
    }

    /**
     * Returns true if using multiple positions.
     *
     * @return true if using multiple positions.
     */
    @Override
    public boolean isUsingMultiplePositions() {
        return useMultiplePositions;
    }

    /**
     * Returns the post move delay in milliseconds.
     *
     * @return the post move delay in milliseconds.
     */
    @Override
    public int postMoveDelay() {
        return postMoveDelay;
    }

    /**
     * Returns true if using time points.
     *
     * @return true if using time points.
     */
    @Override
    public boolean isUsingTimePoints() {
        return useTimePoints;
    }

    /**
     * Returns true if each time point is written to its own dataset.
     *
     * <p>One dataset per time point, inside a series folder named after the save name. Requires
     * saving during the acquisition, because each time point's window is closed once the next
     * time point starts, and cannot be combined with hardware time points.
     *
     * @return true if each time point is written to its own dataset
     */
    @Override
    public boolean isUsingSeparateTimePoints() {
        return separateTimePoints;
    }

    /**
     * Returns the number of time points.
     *
     * @return the number of time points.
     */
    @Override
    public int numTimePoints() {
        return numTimePoints;
    }

    /**
     * Returns the time point interval in seconds.
     *
     * @return the time point interval in seconds.
     */
    @Override
    public double timePointIntervalSec() {
        return timePointIntervalSec;
    }

    /**
     * Returns the acquisition mode.
     *
     * @return the acquisition mode
     */
    @Override
    public AcquisitionMode acquisitionMode() {
        return acquisitionMode;
    }

    /**
     * Returns the autofocus settings.
     *
     * @return the autofocus settings
     */
    @Override
    public DefaultAutofocusSettings autofocus() {
        return autofocus;
    }

    /**
     * Returns the immutable ChannelSettings instance.
     *
     * @return immutable ChannelSettings instance.
     */
    @Override
    public ChannelSettings channels() {
        return channels;
    }

    /**
     * Returns the immutable VolumeSettings instance.
     *
     * @return immutable VolumeSettings instance.
     */
    @Override
    public VolumeSettings volume() {
        return volume;
    }

    @Override
    public String toString() {
        return String.format(
                "%s[saveDirectory=%s, saveNamePrefix=%s, saveDuringAcq=%s, demoMode=%s, saveMode=%s]",
                getClass().getSimpleName(), saveDirectory, saveNamePrefix, saveDuringAcq, demoMode, saveMode
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
