package org.micromanager.lightsheetmanager.gui.tabs.acquisition;

import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.api.AcquisitionSettings;
import org.micromanager.lightsheetmanager.api.VolumeSettings;
import org.micromanager.lightsheetmanager.api.data.GeometryType;
import org.micromanager.lightsheetmanager.api.internal.DispimAcquisitionSettings;
import org.micromanager.lightsheetmanager.api.internal.ScapeAcquisitionSettings;
import org.micromanager.lightsheetmanager.gui.components.ComboBox;
import org.micromanager.lightsheetmanager.gui.components.Panel;
import org.micromanager.lightsheetmanager.gui.components.SettingsListener;
import org.micromanager.lightsheetmanager.gui.components.Spinner;

import javax.swing.JLabel;
import java.util.Objects;

public class VolumePanel extends Panel implements SettingsListener {

    private GeometryType geometryType_;

    private ComboBox<Integer> cmbNumViews_;
    private ComboBox<Integer> cmbFirstView_;

    private Spinner spnViewDelay_;
    private Spinner spnSliceStepSize_;
    private Spinner spnNumSlices_;

    private final LightSheetManager model_;

    public VolumePanel(final LightSheetManager model) {
        super("Volume Settings");
        model_ = Objects.requireNonNull(model);
        createUserInterface();
        createEventHandlers();
        model.userSettings().addChangeListener(this);
    }

    private void createUserInterface() {
        setMigLayout(
                "insets 10 10 10 10, fillx",
                "[grow, left] 10 [right]",
                "[]5[]"
        );

        setAbsoluteSize(275, 115);

        geometryType_ = model_.devices().adapter().geometry();

        // create labels for combo boxes
        final int numImagingPaths = model_.devices().adapter().numImagingPaths();
        Integer[] viewOptions = new Integer[numImagingPaths];
        for (int i = 0; i < numImagingPaths; i++) {
            viewOptions[i] = i + 1;
        }

        final JLabel lblNumViews = new JLabel("Number of views:");
        final JLabel lblFirstView = new JLabel("First view:");
        final JLabel lblViewDelay = new JLabel("Delay before view [ms]:");
        final JLabel lblSlicesPerView = new JLabel("Slices per view:");
        final JLabel lblSliceStepSize = new JLabel("Slice step size [µm]:");

        // if the number of sides has changed and the firstView or numViews is larger
        // than the number of sides, default to 1.
        final VolumeSettings volumeSettings = model_.acquisitions().settings().volume();
        int numViews = volumeSettings.numViews();
        int firstView = volumeSettings.firstView();
        if (numViews > viewOptions.length) {
            numViews = 1;
        }
        if (firstView > viewOptions.length) {
            firstView = 1;
        }

        cmbNumViews_ = new ComboBox<>(viewOptions, numViews, 60, 20);
        cmbFirstView_ = new ComboBox<>(viewOptions, firstView, 60, 20);

        spnViewDelay_ = Spinner.createDoubleSpinner(
                volumeSettings.delayBeforeView(),
                0.0, Double.MAX_VALUE, 0.25);
        spnSliceStepSize_ = Spinner.createDoubleSpinner(
                volumeSettings.sliceStepSize(),
                0.0, Double.MAX_VALUE, 0.1);
        spnNumSlices_ = Spinner.createIntegerSpinner(
                volumeSettings.slicesPerView(),
                1, Integer.MAX_VALUE, 1);

        switch (geometryType_) {
            case DISPIM:
                add(lblNumViews, "");
                add(cmbNumViews_, "wrap");
                add(lblFirstView, "");
                add(cmbFirstView_, "wrap");
                add(lblViewDelay, "");
                add(spnViewDelay_, "wrap");
                add(lblSlicesPerView, "");
                add(spnNumSlices_, "wrap");
                add(lblSliceStepSize, "");
                add(spnSliceStepSize_, "");
                break;
            case SCAPE:
                add(lblViewDelay, "");
                add(spnViewDelay_, "wrap");
                add(new JLabel("Number of slices:"), "");
                add(spnNumSlices_, "wrap");
                add(lblSliceStepSize, "");
                add(spnSliceStepSize_, "");
                break;
            default:
                break;
        }
    }

    private void createEventHandlers() {

        if (geometryType_ == GeometryType.DISPIM) {
            cmbNumViews_.registerListener(() -> {
                model_.acquisitions().settingsBuilder().volumeBuilder()
                        .numViews(cmbNumViews_.getSelected());
                model_.acquisitions().updateDurationLabels();
            });

            cmbFirstView_.registerListener(() -> {
                model_.acquisitions().settingsBuilder().volumeBuilder()
                        .firstView(cmbFirstView_.getSelected());
                model_.acquisitions().updateDurationLabels();
            });
        }

        spnViewDelay_.registerListener(() -> {
            model_.acquisitions().settingsBuilder().volumeBuilder()
                    .delayBeforeView(spnViewDelay_.getDouble());
            model_.acquisitions().updateDurationLabels();
        });

        spnNumSlices_.registerListener(() -> {
            model_.acquisitions().settingsBuilder().volumeBuilder()
                    .slicesPerView(spnNumSlices_.getInt());
            model_.acquisitions().updateDurationLabels();
        });

        spnSliceStepSize_.registerListener(() -> {
            model_.acquisitions().settingsBuilder().volumeBuilder()
                    .sliceStepSize(spnSliceStepSize_.getDouble());
            // needed only for stage scanning b/c acceleration time related to speed
            model_.acquisitions().updateDurationLabels();
        });
    }

    @Override
    public void onSettingsChanged(final AcquisitionSettings settings) {
        if (settings instanceof ScapeAcquisitionSettings) {
            var settingsScape = (ScapeAcquisitionSettings) settings;
            spnViewDelay_.setValue(settingsScape.volume().delayBeforeView());
            spnNumSlices_.setValue(settingsScape.volume().slicesPerView());
            spnSliceStepSize_.setValue(settingsScape.volume().sliceStepSize());
        } else if (settings instanceof DispimAcquisitionSettings) {
            var settingsDispim = (DispimAcquisitionSettings) settings;
            cmbNumViews_.setSelectedItem(settingsDispim.volume().numViews());
            cmbFirstView_.setSelectedItem(settingsDispim.volume().firstView());
            spnViewDelay_.setValue(settingsDispim.volume().delayBeforeView());
            spnNumSlices_.setValue(settingsDispim.volume().slicesPerView());
            spnSliceStepSize_.setValue(settingsDispim.volume().sliceStepSize());
        }
    }
}
