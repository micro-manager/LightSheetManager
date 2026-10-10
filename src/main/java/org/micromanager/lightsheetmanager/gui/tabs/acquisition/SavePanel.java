package org.micromanager.lightsheetmanager.gui.tabs.acquisition;

import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.LightSheetManagerFrame;
import org.micromanager.lightsheetmanager.api.AcquisitionSettings;
import org.micromanager.lightsheetmanager.api.data.SaveMode;
import org.micromanager.lightsheetmanager.api.internal.ScapeAcquisitionSettings;
import org.micromanager.lightsheetmanager.gui.components.Button;
import org.micromanager.lightsheetmanager.gui.components.CheckBox;
import org.micromanager.lightsheetmanager.gui.components.ComboBox;
import org.micromanager.lightsheetmanager.gui.components.Panel;
import org.micromanager.lightsheetmanager.gui.components.SettingsListener;
import org.micromanager.lightsheetmanager.gui.components.TextField;
import org.micromanager.lightsheetmanager.gui.data.Icons;
import org.micromanager.lightsheetmanager.gui.utils.DialogUtils;
import org.micromanager.lightsheetmanager.model.SettingsAdapter;
import org.micromanager.lightsheetmanager.model.utils.FileUtils;

import javax.swing.JLabel;
import java.awt.Color;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class SavePanel extends Panel implements SettingsListener {

    private TextField txtSaveDirectory_;
    private TextField txtSaveFileName_;

    private Button btnBrowse_;
    private Button btnOpen_;

    private ComboBox<SaveMode> cbxSaveMode_;
    private CheckBox cbxSaveWhileAcquiring_;
    private CheckBox cbxSeparateTimePoints_;

    private Button btnSaveSettings_;
    private Button btnLoadSettings_;
    private Button btnConvertSettings_;

    private final FileDialogs.FileType directorySelect_;
    private final FileDialogs.FileType jsonFileSave_;
    private final FileDialogs.FileType jsonFileLoad_;
    private final FileDialogs.FileType txtFileConvert_;

    private final LightSheetManager model_;
    private final LightSheetManagerFrame frame_;

    public SavePanel(final LightSheetManager model, final LightSheetManagerFrame frame) {
        super("Save Settings");
        model_ = Objects.requireNonNull(model);
        frame_ = Objects.requireNonNull(frame);

        directorySelect_ = new FileDialogs.FileType(
                "SAVE_DIRECTORY",
                "All Directories",
                "",
                false,
                ""
        );

        jsonFileSave_ = new FileDialogs.FileType(
                "SAVE_FILE",
                "JSON Files",
                "acq_settings.json",
                false,
                "json"
        );

        jsonFileLoad_ = new FileDialogs.FileType(
                "OPEN_FILE",
                "JSON Files",
                "acq_settings.json",
                false,
                "json"
        );

        // the 1.4 plugin writes its acquisition settings as JSON into AcqSettings.txt
        txtFileConvert_ = new FileDialogs.FileType(
                "CONVERT_FILE",
                "Text Files",
                "AcqSettings.txt",
                false,
                "txt"
        );

        createUserInterface();
        createEventHandlers();

        model.userSettings().addChangeListener(this);
    }

    public void createUserInterface() {
        setMigLayout(
                "",
                "",
                "[]5[]"
        );

        final ScapeAcquisitionSettings acqSettings = model_.acquisitions().settings();

        final JLabel lblSaveDirectory = new JLabel("Directory:");
        final JLabel lblSaveFileName = new JLabel("File Name:");
        final JLabel lblSaveMode = new JLabel("Save Mode:");

        txtSaveDirectory_ = new TextField();
        txtSaveDirectory_.setEditable(false);
        txtSaveDirectory_.setColumns(18);
        txtSaveDirectory_.setForeground(Color.WHITE);
        txtSaveDirectory_.setText(acqSettings.saveDirectory());

        txtSaveFileName_ = new TextField();
        txtSaveFileName_.setColumns(18);
        txtSaveFileName_.setForeground(Color.WHITE);
        txtSaveFileName_.setText(acqSettings.saveNamePrefix());

        btnBrowse_ = new Button("...", 26, 20);
        btnOpen_ = new Button(Icons.FOLDER, 26, 20);

        cbxSaveMode_ = new ComboBox<>(SaveMode.values(),
                acqSettings.saveMode(), 110, 20);

        cbxSaveWhileAcquiring_ = new CheckBox("Save images during acquisition",
                acqSettings.isSavingImagesDuringAcquisition());

        cbxSeparateTimePoints_ = new CheckBox("Separate file for each time point",
                acqSettings.isUsingSeparateTimePoints());
        cbxSeparateTimePoints_.setToolTipText("Write each time point to its own dataset, inside "
                + "a folder named after the file name. Only applies when Time Points is checked, "
                + "and requires \"Save images during acquisition\".");

        btnSaveSettings_ = new Button("Save", 60, 20);
        btnLoadSettings_ = new Button("Load", 60, 20);
        btnConvertSettings_ = new Button("Convert", 72, 20);

        txtSaveDirectory_.setToolTipText("The directory to save images to.");
        btnBrowse_.setToolTipText("Select the save directory with the file browser.");
        btnOpen_.setToolTipText("Open the file explorer to the save directory.");
        btnSaveSettings_.setToolTipText("Save the current acquisition settings to JSON.");
        btnLoadSettings_.setToolTipText("Load acquisitions settings from a JSON file.");
        btnConvertSettings_.setToolTipText("Convert \"AcqSettings.txt\" from the " +
                "Micro-Manager 1.4 plugin into Light Sheet Manager settings.");

        add(lblSaveDirectory, "");
        add(txtSaveDirectory_, "");
        add(btnBrowse_, "");
        add(btnOpen_, "wrap");
        add(lblSaveFileName, "");
        add(txtSaveFileName_, "span 3, wrap");
        add(lblSaveMode, "");
        add(cbxSaveMode_, "split 2, wrap");
        add(cbxSaveWhileAcquiring_, "span 2, wrap");
        add(cbxSeparateTimePoints_, "span 2, wrap");
        add(new JLabel("Acq Settings:"), "");
        add(btnSaveSettings_, "split 3, span 3");
        add(btnLoadSettings_, "");
        add(btnConvertSettings_, "");
    }

    public void createEventHandlers() {
        btnBrowse_.registerListener(() -> {
            final File result = FileDialogs.openDir(frame_,
                    "Please select the directory to save images to...",
                    directorySelect_
            );
            if (result != null) {
                model_.acquisitions().settingsBuilder().saveDirectory(result.toString());
                txtSaveDirectory_.setText(result.toString());
                validateSaveDirectory();
            }
        });

        // flag a saved directory that no longer exists as soon as the plugin opens
        validateSaveDirectory();

        // use the text field so we don't need to update settings
        btnOpen_.registerListener(
                () -> openDirectory(txtSaveDirectory_.getText()));

        cbxSaveWhileAcquiring_.registerListener(
                () -> model_.acquisitions().settingsBuilder()
                        .saveImagesDuringAcquisition(cbxSaveWhileAcquiring_.isSelected()));

        cbxSeparateTimePoints_.registerListener(() -> {
            final boolean selected = cbxSeparateTimePoints_.isSelected();
            model_.acquisitions().settingsBuilder().separateTimePoints(selected);
            if (selected) {
                // each time point's window is closed once the next time point starts, so the data
                // has to already be on disk. The engine refuses the combination as well, because
                // the api can turn saving off without going through this panel.
                forceSaveWhileAcquiring();
            }
        });

        txtSaveFileName_.registerFilenameValidationListener(isValid -> {
            if (isValid) {
                model_.acquisitions().settingsBuilder()
                        .saveNamePrefix(txtSaveFileName_.getText());
            }
        });

        cbxSaveMode_.registerListener(
                () -> model_.acquisitions().settingsBuilder()
                        .saveMode(cbxSaveMode_.getSelected()));

        btnSaveSettings_.registerListener(() -> {
            final File file = FileDialogs.save(frame_,
                    "Save the acquisition settings to JSON...", jsonFileSave_
            );
            if (file != null) {
                if (file.exists()) {
                    final boolean clickedYes = DialogUtils.showYesNoDialog(frame_, "Confirm Overwrite",
                            "The file already exists, would you like to overwrite the file?");
                    if (!clickedYes) {
                        return; // early exit => do not overwrite!
                    }
                }
                // update settings and save to file
                model_.acquisitions().updateSettings();
                FileUtils.writeStringToFile(file.toString(), model_.acquisitions().settings().toPrettyJson());
                model_.studio().logs().logMessage("Acquisition settings saved to: " + file);
            }
        });

        btnLoadSettings_.registerListener(() -> {
            final File file = FileDialogs.openFile(frame_,
                    "Load the acquisition settings from JSON...", jsonFileLoad_
            );
            if (file != null) {
                final String json = FileUtils.readFileToString(file.toString());
                model_.userSettings().loadFromJson(json, true);
                model_.studio().logs().logMessage("Acquisition settings loaded from: " + file);
            }
        });

        btnConvertSettings_.registerListener(() -> {
            final File file = FileDialogs.openFile(frame_,
                    "Convert AcqSettings.txt from the Micro-Manager 1.4 plugin...", txtFileConvert_
            );
            if (file != null) {
                final String json = FileUtils.readFileToString(file.toString());
                SettingsAdapter adapter = new SettingsAdapter(model_);
                adapter.convert(json);
                //System.out.println(json);
            }
        });
    }

    /**
     * Turns on "Save images during acquisition" if it is off.
     *
     * <p>Uses doClick and not setSelected: the listener registered on this check box is an
     * ActionListener, and setSelected fires item and change events but no ActionEvent, so the
     * settings builder would keep saving switched off while the box read checked. The 1.4 plugin
     * clicks the same box for the same reason.
     */
    private void forceSaveWhileAcquiring() {
        if (!cbxSaveWhileAcquiring_.isSelected()) {
            cbxSaveWhileAcquiring_.doClick();
        }
    }

    // Colors the save directory field and explains why in its tooltip when the path is unusable.
    // Only called where the value arrives from outside the field (plugin load, browse, settings
    // change); this touches the filesystem, and checking a dead network path can block the EDT.
    private void validateSaveDirectory() {
        final String path = txtSaveDirectory_.getText();
        final String problem;
        if (path == null || path.trim().isEmpty()) {
            problem = "The save directory is not set.";
        } else {
            final File directory = new File(path);
            if (!directory.exists()) {
                problem = "This directory does not exist: " + path;
            } else if (!directory.isDirectory()) {
                problem = "This is a file, not a directory: " + path;
            } else if (!directory.canWrite()) {
                problem = "This directory is not writable: " + path;
            } else {
                problem = null;
            }
        }
        txtSaveDirectory_.setValid(problem == null);
        txtSaveDirectory_.setToolTipText(
                problem == null ? "The directory to save images to." : problem);
    }

    // Opens the file explorer to the save directory
    private void openDirectory(final String path) {
        final File directory = new File(path);
        if (directory.exists()) {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                try {
                    desktop.open(directory);
                } catch (IOException e) {
                    model_.studio().logs().showError(
                            "Could not open the save directory:\n\n" + path);
                }
            } else {
                model_.studio().logs().showError(
                        "Opening a file explorer is not supported on this platform.");
            }
        } else {
            model_.studio().logs().showError("The save directory does not exist:\n\n" + path);
        }
    }

    @Override
    public void onSettingsChanged(final AcquisitionSettings settings) {
        txtSaveDirectory_.setText(settings.saveDirectory());
        validateSaveDirectory();
        txtSaveFileName_.setText(settings.saveNamePrefix());
        cbxSaveMode_.setSelected(settings.saveMode());
        cbxSaveWhileAcquiring_.setSelected(settings.isSavingImagesDuringAcquisition());
        // silent: a loaded profile must not click the save box on the user's behalf
        cbxSeparateTimePoints_.setSelected(settings.isUsingSeparateTimePoints(), false);
    }
}
