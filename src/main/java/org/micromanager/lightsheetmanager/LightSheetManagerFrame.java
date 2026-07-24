package org.micromanager.lightsheetmanager;

import net.miginfocom.swing.MigLayout;

import org.micromanager.internal.utils.WindowPositioning;
import org.micromanager.lightsheetmanager.api.data.GeometryType;
import org.micromanager.lightsheetmanager.api.data.LightSheetType;
import org.micromanager.lightsheetmanager.gui.data.Icons;
import org.micromanager.lightsheetmanager.gui.tabs.TabPanel;
import org.micromanager.lightsheetmanager.gui.utils.WindowUtils;

import javax.swing.JFrame;
import javax.swing.JLabel;
import java.awt.Font;
import java.util.Objects;

/**
 * Main GUI frame.
 */
public class LightSheetManagerFrame extends JFrame {

    private TabPanel tabPanel_;
    private final LightSheetManager model_;

    public LightSheetManagerFrame(final LightSheetManager model, final boolean isLoaded) {
        model_ = Objects.requireNonNull(model);

        // save window position
        WindowPositioning.setUpBoundsMemory(this,
                this.getClass(), this.getClass().getSimpleName());

        // window setup
        setTitle(LightSheetManagerPlugin.menuName);
        setIconImage(Icons.MICROSCOPE.getImage());
        setResizable(false);

        if (isLoaded) {
            openDialogs(); // ask the user to change settings
            final GeometryType geometry = model_.devices().adapter().geometry();
            switch (geometry) {
                case DISPIM:
                    createUserInterface();
                    break;
                case SCAPE:
                    if (model_.devices().adapter().numImagingPaths() > 1) {
                        model_.setupErrorMessage("SCAPE geometry does not support multiple imaging paths. "
                                + " Use the \"SimultaneousCameras\" property to support multiple cameras.");
                        createErrorUserInterface();
                        return;
                    }
                    if (model_.devices().adapter().numIlluminationPaths() > 1) {
                        model_.setupErrorMessage("SCAPE geometry can only have a single illumination path.");
                        createErrorUserInterface();
                        return;
                    }
                    if (model_.devices().adapter().lightSheetType() == LightSheetType.SCANNED) {
                        model_.setupErrorMessage("Scanned light sheets are not implemented for SCAPE geometry, " +
                                "please contact the developers if you need this feature.");
                        createErrorUserInterface();
                        return;
                    }
                    createUserInterface();
                    // update after loading the settings and creating ui
                    model_.acquisitions().updateDurationLabels();
                    break;
                default:
                    model_.setupErrorMessage("Microscope geometry type " + geometry + " is not supported yet.");
                    createErrorUserInterface();
                    break;
            }
        } else {
            createErrorUserInterface();
        }
    }

    /**
     * The window that opens when the plugin encounters an error, it
     * displays the error message set during {@code model_.setup()}.
     */
    private void createErrorUserInterface() {
        // use MigLayout as the layout manager
        setLayout(new MigLayout(
                "insets 10 10 10 10",
                "[]10[]",
                "[]10[]"
        ));

        final JLabel lblTitle = new JLabel(LightSheetManagerPlugin.menuName);
        lblTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        final JLabel lblError = new JLabel(model_.setupErrorMessage());
        lblError.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));

        add(lblTitle, "wrap");
        add(lblError, "");
    }

    /**
     * The user interface for diSPIM or SCAPE.
     */
    private void createUserInterface() {
        // use MigLayout as the layout manager
        setLayout(new MigLayout(
                "insets 10 10 10 10",
                "[grow, fill]",
                "[]10[]"
        ));

        // main control area
        final int width = 920;
        final int height = 680;
        tabPanel_ = new TabPanel(model_, this, width, height);

        final JLabel lblTitle = new JLabel(LightSheetManagerPlugin.menuName);
        lblTitle.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));

        // restore the polling state from settings
        if (model_.pluginSettings().isPollingPositions()) {
            model_.positions().startPolling();
        }

        // TODO: make this better, put it in plugin class
        // window close event
        WindowUtils.registerWindowClosingEvent(this, event -> {
            if (tabPanel_ != null) {
                tabPanel_.getAcquisitionTab().getMultiPositionPanel().getXYZGridFrame().dispose();
            }
        });

        add(lblTitle, "wrap");
        add(tabPanel_, "wrap");
    }

    /**
     * Detect settings after the model is loaded,
     * ask to change settings with dialogs.
     */
    private void openDialogs() {
        model_.devices().checkDevices(this);
    }

}
