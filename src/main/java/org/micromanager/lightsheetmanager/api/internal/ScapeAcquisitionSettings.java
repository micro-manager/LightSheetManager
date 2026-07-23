package org.micromanager.lightsheetmanager.api.internal;

import org.micromanager.lightsheetmanager.api.AcquisitionSettingsScape;
import org.micromanager.lightsheetmanager.api.SheetCalibration;
import org.micromanager.lightsheetmanager.api.SliceCalibration;
import org.micromanager.lightsheetmanager.api.SliceSettings;
import org.micromanager.lightsheetmanager.api.StageScanSettings;
import org.micromanager.lightsheetmanager.api.TimingSettings;
import org.micromanager.lightsheetmanager.api.VolumeSettings;
import org.micromanager.lightsheetmanager.api.data.AcquisitionMode;

import java.util.Arrays;
import java.util.Objects;

public class ScapeAcquisitionSettings extends BaseAcquisitionSettings implements AcquisitionSettingsScape {

    private final TimingSettings timing_;
    private final VolumeSettings volume_;
    private final SliceSettings slice_;
    private final StageScanSettings stageScan_;
    private final SheetCalibration sheetCalibration_;
    private final SliceCalibration sliceCalibration_;

    private final AcquisitionMode acquisitionMode_;

    private final boolean useHardwareTimePoints_;
    private final boolean useAdvancedTiming_;

    private ScapeAcquisitionSettings(Builder builder) {
        super(builder);
        timing_ = builder.timingBuilder().build();
        volume_ = builder.volumeBuilder().build();
        slice_ = builder.sliceBuilder().build();
        stageScan_ = builder.stageScanBuilder().build();
        sheetCalibration_ = builder.sheetCalibrationBuilder().build();
        sliceCalibration_ = builder.sliceCalibrationBuilder().build();
        acquisitionMode_ = builder.acquisitionMode_;
        useHardwareTimePoints_ = builder.useHardwareTimePoints_;
        useAdvancedTiming_ = builder.useAdvancedTiming_;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(ScapeAcquisitionSettings settings) {
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
    public StageScanSettings stageScan() {
        return stageScan_;
    }

    @Override
    public SheetCalibration sheetCalibration() {
        return sheetCalibration_;
    }

    @Override
    public SliceCalibration sliceCalibration() {
        return sliceCalibration_;
    }

    @Override
    public AcquisitionMode acquisitionMode() {
        return acquisitionMode_;
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
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        ScapeAcquisitionSettings other = (ScapeAcquisitionSettings) obj;
        return Objects.equals(channels(), other.channels()) &&
                Objects.equals(timing_, other.timing_) &&
                Objects.equals(volume_, other.volume_) &&
                Objects.equals(slice_, other.slice_) &&
                Objects.equals(stageScan_, other.stageScan_) &&
                Objects.equals(sheetCalibration_, other.sheetCalibration_) &&
                Objects.equals(sliceCalibration_, other.sliceCalibration_) &&
                acquisitionMode_ == other.acquisitionMode_ &&
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

    @Override
    public int hashCode() {
        return Objects.hash(
                channels(),
                timing_,
                volume_,
                slice_,
                stageScan_,
                sheetCalibration_,
                sliceCalibration_,
                acquisitionMode_,
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
        return String.format("%s[channels=%s, timing=%s, volume=%s, slice=%s]",
                getClass().getSimpleName(), channels(), timing_, volume_, slice_);
    }

    public static class Builder
            extends BaseAcquisitionSettings.Builder<Builder>
            implements AcquisitionSettingsScape.Builder<Builder> {

        private TimingSettings.Builder timingBuilder_ = DefaultTimingSettings.builder();
        private VolumeSettings.Builder volumeBuilder_ = DefaultVolumeSettings.builder();
        private SliceSettings.Builder sliceBuilder_ = DefaultSliceSettings.builder();
        private StageScanSettings.Builder stageScanBuilder_ = DefaultStageScanSettings.builder();
        private SheetCalibration.Builder sheetCalibBuilder_ = DefaultSheetCalibration.builder();
        private SliceCalibration.Builder sliceCalibBuilder_ = DefaultSliceCalibration.builder();

        private AcquisitionMode acquisitionMode_ = AcquisitionMode.NO_SCAN;

        private boolean useHardwareTimePoints_ = false;
        private boolean useAdvancedTiming_ = false;

        private Builder() {
        }

        public Builder(final ScapeAcquisitionSettings settings) {
            super(settings);
            timingBuilder_ = settings.timing().copyBuilder();
            volumeBuilder_ = settings.volume().copyBuilder();
            sliceBuilder_ = settings.slice().copyBuilder();
            stageScanBuilder_ = settings.stageScan().copyBuilder();
            sheetCalibBuilder_ = settings.sheetCalibration().copyBuilder();
            sliceCalibBuilder_ = settings.sliceCalibration().copyBuilder();
            acquisitionMode_ = settings.acquisitionMode();
            useHardwareTimePoints_ = settings.isUsingHardwareTimePoints();
            useAdvancedTiming_ =  settings.isUsingAdvancedTiming();
        }

        @Override
        public Builder acquisitionMode(final AcquisitionMode mode) {
            acquisitionMode_ = mode;
            final boolean scanEnabled = (mode == AcquisitionMode.STAGE_SCAN
                    || mode == AcquisitionMode.STAGE_SCAN_INTERLEAVED
                    || mode == AcquisitionMode.STAGE_SCAN_UNIDIRECTIONAL);
            stageScanBuilder_.enabled(scanEnabled);
            return this;
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

        public StageScanSettings.Builder stageScanBuilder() {
            return stageScanBuilder_;
        }

        public SheetCalibration.Builder sheetCalibrationBuilder() {
            return sheetCalibBuilder_;
        }

        public SliceCalibration.Builder sliceCalibrationBuilder() {
            return sliceCalibBuilder_;
        }

        public void timingBuilder(DefaultTimingSettings.Builder builder) {
            timingBuilder_ = builder;
        }

        public void volumeBuilder(DefaultVolumeSettings.Builder builder) {
            volumeBuilder_ = builder;
        }

        @Override
        public Builder self() {
            return this;
        }

        @Override
        public ScapeAcquisitionSettings build() {
            return new ScapeAcquisitionSettings(this);
        }

        // TODO: finish toString with rest of properties
        @Override
        public String toString() {
            return String.format("%s[timingBuilder=%s]",
                    getClass().getSimpleName(), timingBuilder_);
        }

    }

}
