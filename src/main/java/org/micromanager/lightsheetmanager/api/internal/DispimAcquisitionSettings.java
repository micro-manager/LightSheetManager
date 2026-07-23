package org.micromanager.lightsheetmanager.api.internal;

import org.micromanager.lightsheetmanager.api.AcquisitionSettingsDispim;
import org.micromanager.lightsheetmanager.api.SheetCalibration;
import org.micromanager.lightsheetmanager.api.SliceCalibration;
import org.micromanager.lightsheetmanager.api.SliceSettings;
import org.micromanager.lightsheetmanager.api.SliceSettingsLightSheet;
import org.micromanager.lightsheetmanager.api.StageScanSettings;
import org.micromanager.lightsheetmanager.api.TimingSettings;
import org.micromanager.lightsheetmanager.api.VolumeSettings;

import java.util.Arrays;
import java.util.Objects;

public class DispimAcquisitionSettings extends BaseAcquisitionSettings implements AcquisitionSettingsDispim {

    private final TimingSettings timing_;
    private final VolumeSettings volume_;
    private final SliceSettings slice_;
    private final SliceSettingsLightSheet sliceLS_;
    private final StageScanSettings stageScan_;
    private final SheetCalibration[] sheetCalibrations_;
    private final SliceCalibration[] sliceCalibrations_;

    private final boolean useHardwareTimePoints_;
    private final boolean useAdvancedTiming_;

    private final double liveScanPeriod_;

    private DispimAcquisitionSettings(Builder builder) {
        super(builder);
        timing_ = builder.timingBuilder().build();
        volume_ = builder.volumeBuilder().build();
        slice_ = builder.sliceBuilder().build();
        sliceLS_ = builder.sliceLSBuilder().build();
        stageScan_ = builder.stageScanBuilder().build();
        sheetCalibrations_ = new DefaultSheetCalibration[2];
        sliceCalibrations_ = new DefaultSliceCalibration[2]; // TODO: populate with numViews instead of magic number
        for (int i = 0; i < 2; i++) {
            sheetCalibrations_[i] = builder.shcb_[i].build();
            sliceCalibrations_[i] = builder.slcb_[i].build();
        }
        useHardwareTimePoints_ = builder.useHardwareTimePoints_;
        useAdvancedTiming_ = builder.useAdvancedTiming_;
        liveScanPeriod_= builder.liveScanPeriod_;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(DispimAcquisitionSettings settings) {
        Objects.requireNonNull(settings, "Cannot copy from null settings");
        return new Builder(settings);
    }

    @Override
    public Builder copyBuilder() {
        return new Builder(this);
    }

    @Override
    public TimingSettings timing() {
        return timing_;
    }

    @Override
    public VolumeSettings volume() {
        return volume_;
    }

    @Override
    public SliceSettings slice() {
        return slice_;
    }

    @Override
    public SliceSettingsLightSheet sliceLS() {
        return sliceLS_;
    }

    @Override
    public StageScanSettings stageScan() {
        return stageScan_;
    }

    @Override
    public SheetCalibration sheetCalibration(final int view) {
        return sheetCalibrations_[view-1];
    }

    @Override
    public SliceCalibration sliceCalibration(final int view) {
        return sliceCalibrations_[view-1];
    }

    @Override
    public boolean isUsingHardwareTimePoints() {
        return useHardwareTimePoints_;
    }

    @Override
    public boolean isUsingAdvancedTiming() {
        return useAdvancedTiming_;
    }

    @Override
    public double liveScanPeriod() {
        return liveScanPeriod_;
    }

    // TODO: finish this
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        DispimAcquisitionSettings other = (DispimAcquisitionSettings) obj;
        return Objects.equals(channels(), other.channels()) &&
                Objects.equals(timing_, other.timing_) &&
                Objects.equals(volume_, other.volume_) &&
                Objects.equals(slice_, other.slice_) &&
                Objects.equals(sliceLS_, other.sliceLS_) &&
                Objects.equals(stageScan_, other.stageScan_) &&
                // Objects.equals(sheetCalibration_, other.sheetCalibration_) &&
                // Objects.equals(sliceCalibration_, other.sliceCalibration_) &&
                acquisitionMode() == other.acquisitionMode() &&
                cameraMode() == other.cameraMode() &&
                Arrays.equals(imagingCameraOrder(), other.imagingCameraOrder()) &&
                isUsingTimePoints() == other.isUsingTimePoints() &&
                isUsingMultiplePositions() == other.isUsingMultiplePositions() &&
                useHardwareTimePoints_ == other.useHardwareTimePoints_ &&
                useAdvancedTiming_ == other.useAdvancedTiming_ &&
                numTimePoints() == other.numTimePoints() &&
                Double.compare(other.timePointInterval(), timePointInterval()) == 0 &&
                postMoveDelay() == other.postMoveDelay();
    }

    // TODO: finish this
    @Override
    public int hashCode() {
        return Objects.hash(
                channels(),
                timing_,
                volume_,
                slice_,
                sliceLS_,
                stageScan_,
                // sheetCalibration_,
                // sliceCalibration_,
                acquisitionMode(),
                cameraMode(),
                Arrays.hashCode(imagingCameraOrder()),
                isUsingTimePoints(),
                isUsingMultiplePositions(),
                useHardwareTimePoints_,
                useAdvancedTiming_,
                numTimePoints(),
                timePointInterval(),
                postMoveDelay()
        );
    }

    // TODO: finish this, and maybe use pretty printing? or just rely on JSON conversion?
    @Override
    public String toString() {
        return String.format("%s[channels=%s, timing=%s, volume=%s, slice=%s, sliceLS=%s, stageScan=%s]",
                getClass().getSimpleName(), channels(), timing_, volume_, slice_, sliceLS_, stageScan_);
    }

    public static class Builder
            extends BaseAcquisitionSettings.Builder<Builder>
            implements AcquisitionSettingsDispim.Builder<Builder> {

        private TimingSettings.Builder timingBuilder_ = DefaultTimingSettings.builder();
        private VolumeSettings.Builder volumeBuilder_ = DefaultVolumeSettings.builder();
        private SliceSettings.Builder sliceBuilder_ = DefaultSliceSettings.builder();
        private SliceSettingsLightSheet.Builder ssbLS_ = DefaultSliceSettingsLS.builder(); // maybe this should be LightSheetSliceSettings? replace ssb_?
        private StageScanSettings.Builder stageScanBuilder_ = DefaultStageScanSettings.builder();
        private SheetCalibration.Builder[] shcb_ = new DefaultSheetCalibration.Builder[2];
        private SliceCalibration.Builder[] slcb_ = new DefaultSliceCalibration.Builder[2];

        private boolean useHardwareTimePoints_ = false;
        private boolean useAdvancedTiming_ = false;

        private double liveScanPeriod_ = 20.0; // TODO: this could go in user settings since it has to do with the live view

        private Builder() {
            for (int i = 0; i < 2; i++) {
                shcb_[i] = DefaultSheetCalibration.builder();
                slcb_[i] = DefaultSliceCalibration.builder();
            }
        }

        public Builder(final DispimAcquisitionSettings settings) {
            super(settings);
            timingBuilder_ = settings.timing().copyBuilder();
            volumeBuilder_ = settings.volume().copyBuilder();
            sliceBuilder_ = settings.slice().copyBuilder();
            ssbLS_ = settings.sliceLS_.copyBuilder();
            stageScanBuilder_ = settings.stageScan().copyBuilder();
            for (int i = 0; i < 2; i++) {
                slcb_[i] = settings.sliceCalibrations_[i].copyBuilder();
                shcb_[i] = settings.sheetCalibrations_[i].copyBuilder();
            }
            useHardwareTimePoints_ = settings.isUsingHardwareTimePoints();
            useAdvancedTiming_ =  settings.isUsingAdvancedTiming();
            liveScanPeriod_ = settings.liveScanPeriod();
        }

        @Override
        public Builder useHardwareTimePoints(final boolean state) {
            useHardwareTimePoints_ = state;
            return this;
        }

        @Override
        public Builder useAdvancedTiming(final boolean state) {
            useAdvancedTiming_ = state;
            return this;
        }

        @Override
        public Builder liveScanPeriod(double liveScanPeriod) {
            liveScanPeriod_ = liveScanPeriod;
            return this;
        }

        // getters for sub-builders
        public TimingSettings.Builder timingBuilder() {
            return timingBuilder_;
        }

        public VolumeSettings.Builder volumeBuilder() {
            return volumeBuilder_;
        }

        public SliceSettings.Builder sliceBuilder() {
            return sliceBuilder_;
        }

        public SliceSettingsLightSheet.Builder sliceLSBuilder() {
            return ssbLS_;
        }

        @Override
        public StageScanSettings.Builder stageScanBuilder() {
            return stageScanBuilder_;
        }

        public SheetCalibration.Builder sheetCalibrationBuilder(final int view) {
            return shcb_[view-1];
        }

        public SliceCalibration.Builder sliceCalibrationBuilder(final int view) {
            return slcb_[view-1];
        }

        public void timingBuilder(DefaultTimingSettings.Builder builder) {
            timingBuilder_ = builder;
        }

        public void volumeBuilder(DefaultVolumeSettings.Builder builder) {
            volumeBuilder_ = builder;
        }

        @Override
        public DispimAcquisitionSettings build() {
            return new DispimAcquisitionSettings(this);
        }

        @Override
        public Builder self() {
            return this;
        }

        // TODO: finish toString with rest of properties
        @Override
        public String toString() {
            return String.format("[timingBuilder_=%s]", timingBuilder_);
        }

    }

}
