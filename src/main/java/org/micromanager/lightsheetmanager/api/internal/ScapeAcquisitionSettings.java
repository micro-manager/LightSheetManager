package org.micromanager.lightsheetmanager.api.internal;

import org.micromanager.lightsheetmanager.api.AcquisitionSettingsScape;
import org.micromanager.lightsheetmanager.api.SheetCalibration;
import org.micromanager.lightsheetmanager.api.SliceCalibration;
import org.micromanager.lightsheetmanager.api.SliceSettings;
import org.micromanager.lightsheetmanager.api.StageScanSettings;
import org.micromanager.lightsheetmanager.api.TimingSettings;
import org.micromanager.lightsheetmanager.api.data.CameraMode;

import java.util.Arrays;
import java.util.Objects;

public class ScapeAcquisitionSettings extends BaseAcquisitionSettings implements AcquisitionSettingsScape {

    private final TimingSettings timing;
    private final SliceSettings slice;
    private final StageScanSettings stageScan;
    private final SheetCalibration sheetCalibration;
    private final SliceCalibration sliceCalibration;

    private final boolean useHardwareTimePoints;
    private final boolean useAdvancedTiming;

    private ScapeAcquisitionSettings(Builder builder) {
        super(builder);
        timing = builder.timingBuilder().build();
        slice = builder.sliceBuilder().build();
        stageScan = builder.stageScanBuilder().build();
        sheetCalibration = builder.sheetCalibrationBuilder().build();
        sliceCalibration = builder.sliceCalibrationBuilder().build();
        useHardwareTimePoints = builder.useHardwareTimePoints;
        useAdvancedTiming = builder.useAdvancedTiming;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns why SCAPE cannot use the camera mode, or null when it can.
     *
     * @param mode the camera mode
     * @return the problem, or null
     */
    public static String cameraModeProblem(final CameraMode mode) {
        if (mode == CameraMode.VIRTUAL_SLIT) {
            return "SCAPE does not support the \"" + CameraMode.VIRTUAL_SLIT + "\" camera trigger mode";
        }
        return null;
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
        return timing;
    }

    @Override
    public SliceSettings slice() {
        return slice;
    }

    @Override
    public StageScanSettings stageScan() {
        return stageScan;
    }

    @Override
    public SheetCalibration sheetCalibration() {
        return sheetCalibration;
    }

    @Override
    public SliceCalibration sliceCalibration() {
        return sliceCalibration;
    }

    @Override
    public boolean isUsingHardwareTimePoints() {
        return useHardwareTimePoints;
    }

    @Override
    public boolean isUsingAdvancedTiming() {
        return useAdvancedTiming;
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
                Objects.equals(timing, other.timing) &&
                Objects.equals(volume(), other.volume()) &&
                Objects.equals(slice, other.slice) &&
                Objects.equals(stageScan, other.stageScan) &&
                Objects.equals(sheetCalibration, other.sheetCalibration) &&
                Objects.equals(sliceCalibration, other.sliceCalibration) &&
                acquisitionMode() == other.acquisitionMode() &&
                cameraMode() == other.cameraMode() &&
                Arrays.equals(imagingCameraOrder(), other.imagingCameraOrder()) &&
                isUsingTimePoints() == other.isUsingTimePoints() &&
                isUsingSeparateTimePoints() == other.isUsingSeparateTimePoints() &&
                isUsingMultiplePositions() == other.isUsingMultiplePositions() &&
                useHardwareTimePoints == other.useHardwareTimePoints &&
                useAdvancedTiming == other.useAdvancedTiming &&
                numTimePoints() == other.numTimePoints() &&
                Double.compare(other.timePointIntervalSec(), timePointIntervalSec()) == 0 &&
                postMoveDelay() == other.postMoveDelay();
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                channels(),
                timing,
                volume(),
                slice,
                stageScan,
                sheetCalibration,
                sliceCalibration,
                acquisitionMode(),
                cameraMode(),
                Arrays.hashCode(imagingCameraOrder()),
                isUsingTimePoints(),
                isUsingSeparateTimePoints(),
                isUsingMultiplePositions(),
                useHardwareTimePoints,
                useAdvancedTiming,
                numTimePoints(),
                timePointIntervalSec(),
                postMoveDelay()
        );
    }

    // TODO: finish this, and maybe use pretty printing? or just rely on JSON conversion?
    @Override
    public String toString() {
        return String.format("%s[channels=%s, timing=%s, volume=%s, slice=%s]",
                getClass().getSimpleName(), channels(), timing, volume(), slice);
    }

    public static class Builder
            extends BaseAcquisitionSettings.Builder<Builder>
            implements AcquisitionSettingsScape.Builder<Builder> {

        private TimingSettings.Builder timingBuilder = DefaultTimingSettings.builder();
        private SliceSettings.Builder sliceBuilder = DefaultSliceSettings.builder();
        private StageScanSettings.Builder stageScanBuilder = DefaultStageScanSettings.builder();
        private SheetCalibration.Builder sheetCalibBuilder = DefaultSheetCalibration.builder();
        private SliceCalibration.Builder sliceCalibBuilder = DefaultSliceCalibration.builder();

        private boolean useHardwareTimePoints = false;
        private boolean useAdvancedTiming = false;

        private Builder() {
        }

        public Builder(final ScapeAcquisitionSettings settings) {
            super(settings);
            timingBuilder = settings.timing().copyBuilder();
            sliceBuilder = settings.slice().copyBuilder();
            stageScanBuilder = settings.stageScan().copyBuilder();
            sheetCalibBuilder = settings.sheetCalibration().copyBuilder();
            sliceCalibBuilder = settings.sliceCalibration().copyBuilder();
            useHardwareTimePoints = settings.isUsingHardwareTimePoints();
            useAdvancedTiming =  settings.isUsingAdvancedTiming();
        }

        /**
         * Sets the camera mode.
         *
         * @param mode the camera mode
         * @return {@code this} builder
         * @throws IllegalArgumentException if the mode is virtual slit, which SCAPE does not support
         */
        @Override
        public Builder cameraMode(final CameraMode mode) {
            final String problem = cameraModeProblem(mode);
            if (problem != null) {
                throw new IllegalArgumentException(problem);
            }
            return super.cameraMode(mode);
        }

        @Override
        public Builder useHardwareTimePoints(final boolean state) {
            useHardwareTimePoints = state;
            return this;
        }

        @Override
        public Builder useAdvancedTiming(final boolean state) {
            useAdvancedTiming = state;
            return this;
        }

        // getters for sub-builders
        public TimingSettings.Builder timingBuilder() {
            return timingBuilder;
        }

        public SliceSettings.Builder sliceBuilder() {
            return sliceBuilder;
        }

        @Override
        public StageScanSettings.Builder stageScanBuilder() {
            return stageScanBuilder;
        }

        public SheetCalibration.Builder sheetCalibrationBuilder() {
            return sheetCalibBuilder;
        }

        public SliceCalibration.Builder sliceCalibrationBuilder() {
            return sliceCalibBuilder;
        }

        public void timingBuilder(DefaultTimingSettings.Builder builder) {
            timingBuilder = builder;
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
                    getClass().getSimpleName(), timingBuilder);
        }

    }

}
