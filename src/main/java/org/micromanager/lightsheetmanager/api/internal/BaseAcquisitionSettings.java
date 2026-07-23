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
