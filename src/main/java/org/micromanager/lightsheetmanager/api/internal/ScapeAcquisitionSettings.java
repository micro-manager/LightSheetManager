package org.micromanager.lightsheetmanager.api.internal;

import org.micromanager.lightsheetmanager.api.AcquisitionSettingsScape;
import org.micromanager.lightsheetmanager.api.SheetCalibration;
import org.micromanager.lightsheetmanager.api.SliceCalibration;
import org.micromanager.lightsheetmanager.api.SliceSettings;
import org.micromanager.lightsheetmanager.api.StageScanSettings;
import org.micromanager.lightsheetmanager.api.TimingSettings;
import org.micromanager.lightsheetmanager.api.VolumeSettings;
import org.micromanager.lightsheetmanager.api.data.AcquisitionMode;
import org.micromanager.lightsheetmanager.api.data.CameraData;
import org.micromanager.lightsheetmanager.api.data.CameraMode;

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

    private final CameraMode cameraMode_;
    private final CameraData[] imagingCameraOrder_;

    private final boolean useTimePoints_;
    private final boolean useMultiplePositions_;
    private final boolean useHardwareTimePoints_;
    private final boolean useAdvancedTiming_;

    private final int numTimePoints_;
    private final double timePointInterval_;
    private final int postMoveDelay_;

    private ScapeAcquisitionSettings(Builder builder) {
        super(builder);
        timing_ = builder.timingBuilder().build();
        volume_ = builder.volumeBuilder().build();
        slice_ = builder.sliceBuilder().build();
        stageScan_ = builder.stageScanBuilder().build();
        sheetCalibration_ = builder.sheetCalibrationBuilder().build();
        sliceCalibration_ = builder.sliceCalibrationBuilder().build();
        acquisitionMode_ = builder.acquisitionMode_;
        cameraMode_ = builder.cameraMode_;
        imagingCameraOrder_ = builder.imagingCameraOrder_.clone();
        useTimePoints_ = builder.useTimePoints_;
        useMultiplePositions_ = builder.useMultiplePositions_;
        useHardwareTimePoints_ = builder.useHardwareTimePoints_;
        useAdvancedTiming_ = builder.useAdvancedTiming_;
        numTimePoints_ = builder.numTimePoints_;
        timePointInterval_ = builder.timePointInterval_;
        postMoveDelay_ = builder.postMoveDelay_;
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
    public CameraMode cameraMode() {
        return cameraMode_;
    }

    @Override
    public CameraData[] imagingCameraOrder() {
        return imagingCameraOrder_;
    }

    @Override
    public boolean isUsingTimePoints() {
        return useTimePoints_;
    }

    @Override
    public boolean isUsingMultiplePositions() {
        return useMultiplePositions_;
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
    public int numTimePoints() {
        return numTimePoints_;
    }

    @Override
    public double timePointInterval() {
        return timePointInterval_;
    }

    @Override
    public int postMoveDelay() {
        return postMoveDelay_;
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
                cameraMode_ == other.cameraMode_ &&
                Arrays.equals(imagingCameraOrder_, other.imagingCameraOrder_) &&
                useTimePoints_ == other.useTimePoints_ &&
                useMultiplePositions_ == other.useMultiplePositions_ &&
                useHardwareTimePoints_ == other.useHardwareTimePoints_ &&
                useAdvancedTiming_ == other.useAdvancedTiming_ &&
                numTimePoints_ == other.numTimePoints_ &&
                Double.compare(other.timePointInterval_, timePointInterval_) == 0 &&
                postMoveDelay_ == other.postMoveDelay_;
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
                cameraMode_,
                Arrays.hashCode(imagingCameraOrder_),
                useTimePoints_,
                useMultiplePositions_,
                useHardwareTimePoints_,
                useAdvancedTiming_,
                numTimePoints_,
                timePointInterval_,
                postMoveDelay_
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

        private CameraMode cameraMode_ = CameraMode.EDGE;
        private CameraData[] imagingCameraOrder_ = {};

        private boolean useTimePoints_ = false;
        private boolean useMultiplePositions_ = false;
        private boolean useHardwareTimePoints_ = false;
        private boolean useAdvancedTiming_ = false;

        private int numTimePoints_ = 1;
        private double timePointInterval_ = 0.0;
        private int postMoveDelay_ = 0;

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
            cameraMode_ = settings.cameraMode();
            imagingCameraOrder_ = settings.imagingCameraOrder();
            useTimePoints_ = settings.isUsingTimePoints();
            useMultiplePositions_ = settings.isUsingMultiplePositions();
            useHardwareTimePoints_ = settings.isUsingHardwareTimePoints();
            useAdvancedTiming_ =  settings.isUsingAdvancedTiming();
            numTimePoints_ = settings.numTimePoints();
            timePointInterval_ = settings.timePointInterval();
            postMoveDelay_ = settings.postMoveDelay();
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
        public Builder cameraMode(final CameraMode mode) {
            cameraMode_ = mode;
            return this;
        }

        @Override
        public Builder imagingCameraOrder(final CameraData[] order) {
            imagingCameraOrder_ = order;
            return this;
        }

        @Override
        public Builder useTimePoints(final boolean state) {
            useTimePoints_ = state;
            return this;
        }

        @Override
        public Builder useMultiplePositions(final boolean state) {
            useMultiplePositions_ = state;
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

        @Override
        public Builder numTimePoints(final int numTimePoints) {
            numTimePoints_ = numTimePoints;
            return this;
        }

        @Override
        public Builder timePointInterval(final double timePointInterval) {
            timePointInterval_ = timePointInterval;
            return this;
        }

        @Override
        public Builder postMoveDelay(final int postMoveDelay) {
            postMoveDelay_ = postMoveDelay;
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
