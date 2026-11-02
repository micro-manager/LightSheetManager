package org.micromanager.lightsheetmanager.api.internal;

import org.micromanager.lightsheetmanager.api.AcquisitionSettingsDispim;
import org.micromanager.lightsheetmanager.api.SheetCalibration;
import org.micromanager.lightsheetmanager.api.SliceCalibration;
import org.micromanager.lightsheetmanager.api.SliceSettings;
import org.micromanager.lightsheetmanager.api.SliceSettingsLightSheet;

import java.util.Arrays;
import java.util.Objects;

public class DispimAcquisitionSettings extends BaseAcquisitionSettings implements AcquisitionSettingsDispim {

    private final SliceSettings slice;
    private final SliceSettingsLightSheet sliceLS;
    private final SheetCalibration[] sheetCalibrations;
    private final SliceCalibration[] sliceCalibrations;

    private final boolean useHardwareTimePoints;
    private final boolean useAdvancedTiming;

    private final double liveScanPeriod;

    private DispimAcquisitionSettings(Builder builder) {
        super(builder);
        slice = builder.sliceBuilder().build();
        sliceLS = builder.sliceLSBuilder().build();
        sheetCalibrations = new DefaultSheetCalibration[2];
        sliceCalibrations = new DefaultSliceCalibration[2]; // TODO: populate with numViews instead of magic number
        for (int i = 0; i < 2; i++) {
            sheetCalibrations[i] = builder.shcb[i].build();
            sliceCalibrations[i] = builder.slcb[i].build();
        }
        useHardwareTimePoints = builder.useHardwareTimePoints;
        useAdvancedTiming = builder.useAdvancedTiming;
        liveScanPeriod = builder.liveScanPeriod;
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
    public SliceSettings slice() {
        return slice;
    }

    @Override
    public SliceSettingsLightSheet sliceLS() {
        return sliceLS;
    }

    @Override
    public SheetCalibration sheetCalibration(final int view) {
        return sheetCalibrations[view-1];
    }

    @Override
    public SliceCalibration sliceCalibration(final int view) {
        return sliceCalibrations[view-1];
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
    public double liveScanPeriod() {
        return liveScanPeriod;
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
                Objects.equals(autofocus(), other.autofocus()) &&
                Objects.equals(timing(), other.timing()) &&
                Objects.equals(volume(), other.volume()) &&
                Objects.equals(slice, other.slice) &&
                Objects.equals(sliceLS, other.sliceLS) &&
                Objects.equals(stageScan(), other.stageScan()) &&
                Arrays.equals(sheetCalibrations, other.sheetCalibrations) &&
                Arrays.equals(sliceCalibrations, other.sliceCalibrations) &&
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

    // TODO: finish this
    @Override
    public int hashCode() {
        return Objects.hash(
                channels(),
                autofocus(),
                timing(),
                volume(),
                slice,
                sliceLS,
                stageScan(),
                Arrays.hashCode(sheetCalibrations),
                Arrays.hashCode(sliceCalibrations),
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
        return String.format("%s[channels=%s, timing=%s, volume=%s, slice=%s, sliceLS=%s, stageScan=%s]",
                getClass().getSimpleName(), channels(), timing(), volume(), slice, sliceLS, stageScan());
    }

    public static class Builder
            extends BaseAcquisitionSettings.Builder<Builder>
            implements AcquisitionSettingsDispim.Builder<Builder> {

        private SliceSettings.Builder sliceBuilder = DefaultSliceSettings.builder();
        private SliceSettingsLightSheet.Builder ssbLS = DefaultSliceSettingsLS.builder(); // maybe this should be LightSheetSliceSettings? replace ssb_?
        private SheetCalibration.Builder[] shcb = new DefaultSheetCalibration.Builder[2];
        private SliceCalibration.Builder[] slcb = new DefaultSliceCalibration.Builder[2];

        private boolean useHardwareTimePoints = false;
        private boolean useAdvancedTiming = false;

        private double liveScanPeriod = 20.0; // TODO: this could go in user settings since it has to do with the live view

        private Builder() {
            for (int i = 0; i < 2; i++) {
                shcb[i] = DefaultSheetCalibration.builder();
                slcb[i] = DefaultSliceCalibration.builder();
            }
        }

        public Builder(final DispimAcquisitionSettings settings) {
            super(settings);
            sliceBuilder = settings.slice().copyBuilder();
            ssbLS = settings.sliceLS.copyBuilder();
            for (int i = 0; i < 2; i++) {
                slcb[i] = settings.sliceCalibrations[i].copyBuilder();
                shcb[i] = settings.sheetCalibrations[i].copyBuilder();
            }
            useHardwareTimePoints = settings.isUsingHardwareTimePoints();
            useAdvancedTiming =  settings.isUsingAdvancedTiming();
            liveScanPeriod = settings.liveScanPeriod();
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

        @Override
        public Builder liveScanPeriod(double liveScanPeriod) {
            this.liveScanPeriod = liveScanPeriod;
            return this;
        }

        // getters for sub-builders
        public SliceSettings.Builder sliceBuilder() {
            return sliceBuilder;
        }

        public SliceSettingsLightSheet.Builder sliceLSBuilder() {
            return ssbLS;
        }

        public SheetCalibration.Builder sheetCalibrationBuilder(final int view) {
            return shcb[view-1];
        }

        public SliceCalibration.Builder sliceCalibrationBuilder(final int view) {
            return slcb[view-1];
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
            return String.format("[timingBuilder_=%s]", timingBuilder());
        }

    }

}
