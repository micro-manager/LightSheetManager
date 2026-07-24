package org.micromanager.lightsheetmanager.gui.tabs.setup;

import org.micromanager.lightsheetmanager.api.data.CameraMode;
import org.micromanager.lightsheetmanager.api.data.GeometryType;
import org.micromanager.lightsheetmanager.gui.components.Button;
import org.micromanager.lightsheetmanager.gui.components.ComboBox;
import org.micromanager.lightsheetmanager.gui.components.Panel;
import org.micromanager.lightsheetmanager.gui.components.ToggleButton;
import org.micromanager.lightsheetmanager.gui.data.Icons;
import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.model.devices.cameras.CameraBase;

import javax.swing.JLabel;
import java.util.Objects;


/**
 * Select which camera is being used.
 */
public class CameraPanel extends Panel {

    public static final String CONFIG_GROUP = "Path Select";

    private boolean isPreviewPressed = false;
    private boolean isLivePressed = false;

    private Button btnImagingPath_;
    private Button btnEpiPath_;
    private Button btnMultiPath_;

    private ToggleButton btnInvertedPath_;
    private ToggleButton btnLiveMode_;

    // path selection for SCAPE
    private ComboBox<String> cmbPreset_;

    private final LightSheetManager model_;

    public CameraPanel(final LightSheetManager model) {
        super("Cameras");
        model_ = Objects.requireNonNull(model);
        createUserInterface();
        createEventHandlers();
    }

    private void createUserInterface() {
        final GeometryType geometryType = model_.devices().adapter().geometry();

        setMigLayout(
                "",
                "[]5[]",
                "[]5[]"
        );

        btnImagingPath_ = new Button("Imaging", 80, 26);
        btnMultiPath_ = new Button("Multi", 80, 26);
        btnEpiPath_ = new Button("Epi", 80, 26);

        btnInvertedPath_ = new ToggleButton(
                "Preview", "Stop Preview",
                Icons.CAMERA, Icons.CANCEL, 165, 26
        );
        btnLiveMode_ = new ToggleButton(
                "Live", "Stop Live",
                Icons.CAMERA, Icons.CANCEL, 165, 26
        );

        // populate the combo box if the group exists
        String selected = "";
        String[] presets = {""};
        if (model_.core().isGroupDefined(CONFIG_GROUP)) {
            presets = model_.core().getAvailableConfigs(CONFIG_GROUP).toArray();
            try {
                selected = model_.core().getCurrentConfig(CONFIG_GROUP);
            } catch (Exception e) {
                // ignore => use default value
            }
        }
        cmbPreset_ = new ComboBox<>(presets, selected, 165, 26);

        switch (geometryType) {
            case DISPIM:
                add(btnImagingPath_, "");
                add(btnMultiPath_, "wrap");
                add(btnEpiPath_, "");
                add(btnInvertedPath_, "wrap");
                add(btnLiveMode_, "span 2");
                break;
            case SCAPE:
                add(btnInvertedPath_, "wrap");
                add(btnLiveMode_, "wrap");
                add(new JLabel("Path Preset:"), "wrap");
                add(cmbPreset_, "");
                break;
            default:
                break;
        }
    }

    private void createEventHandlers() {
        final GeometryType geometryType = model_.devices().adapter().geometry();

        switch (geometryType) {
            case DISPIM:
                btnImagingPath_.registerListener(() -> {
                });

                btnMultiPath_.registerListener(() -> {
                });

                btnEpiPath_.registerListener(() -> {
                });

                btnInvertedPath_.registerListener(() -> {
                });

                btnLiveMode_.registerListener(() -> {
                });
                break;
            case SCAPE:
                btnInvertedPath_.registerListener(() -> {
                    if (!btnInvertedPath_.isSelected()) {
                        model_.studio().live().setLiveModeOn(false);
                        return;
                    }
                    closeLiveModeWindow();
                    final CameraBase camera = model_.devices().device("PreviewCamera");
                    if (camera != null) {
                        try {
                            model_.studio().core().setCameraDevice(camera.getDeviceName());
                            camera.setTriggerMode(CameraMode.INTERNAL);
                        } catch (Exception ex) {
                            model_.studio().logs().showError("could not set camera to " + camera.getDeviceName());
                            return; // early exit
                        }
                        isPreviewPressed = true;
                        model_.studio().live().setLiveModeOn(true);
                    } else {
                        btnInvertedPath_.setState(false);
                        model_.studio().logs().showError(
                                "No device for \"PreviewCamera\" set in the device adapter.");
                    }
                });

                // live mode
                btnLiveMode_.registerListener(() -> {
                    if (!btnLiveMode_.isSelected()) {
                        model_.studio().live().setLiveModeOn(false);
                        return;
                    }
                    closeLiveModeWindow();
                    final CameraBase camera = model_.devices().firstImagingCamera();
                    if (camera != null) {
                        try {
                            model_.studio().core().setCameraDevice(camera.getDeviceName());
                            camera.setTriggerMode(CameraMode.INTERNAL);
                        } catch (Exception ex) {
                            model_.studio().logs().showError("could not set camera to " + camera.getDeviceName());
                            return; // early exit
                        }
                        isLivePressed = true;
                        model_.studio().live().setLiveModeOn(true);
                    } else {
                        // TODO: use correct name for camera, ImagingCamera1, etc
                        model_.studio().logs().showError(
                                "No device for \"ImagingCamera\" set in the device adapter.");
                    }
                });
                break;
            default:
                break;
        }

        // select path preset
        cmbPreset_.registerListener(() -> {
            final String selected = cmbPreset_.getSelected();
            try {
                model_.core().setConfig(CONFIG_GROUP, selected);
                model_.studio().app().refreshGUI();
            } catch (Exception e) {
                model_.studio().logs().showError("Failed to set configuration: " + e.getMessage());
            }
        });

    }

    /**
     * Closes the Live Mode window if it exists.
     */
    private void closeLiveModeWindow() {
        final boolean isLiveModeOn = model_.studio().live().isLiveModeOn();
        if (isLiveModeOn) {
            model_.studio().live().setLiveModeOn(false);
            // close the live mode window if it exists
            if (model_.studio().live().getDisplay() != null) {
                model_.studio().live().getDisplay().close();
            }
        }
    }

    // TODO: do we want to subscribe to events?
//    @Subscribe
//    public void liveModeListener(LiveModeEvent event) {
//        if (!model_.studio().live().isLiveModeOn()) {
//            if (isPreviewPressed) {
//                isPreviewPressed = false;
//                btnInvertedPath_.setState(false);
//            }
//            if (isLivePressed) {
//                isLivePressed = false;
//                btnLiveMode_.setState(false);;
//            }
//        }
//    }

}
