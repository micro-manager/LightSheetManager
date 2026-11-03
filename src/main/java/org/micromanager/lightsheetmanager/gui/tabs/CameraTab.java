package org.micromanager.lightsheetmanager.gui.tabs;

import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.gui.components.Button;
import org.micromanager.lightsheetmanager.gui.components.ComboBox;
import org.micromanager.lightsheetmanager.gui.components.ListeningPanel;
import org.micromanager.lightsheetmanager.gui.components.Panel;
import org.micromanager.lightsheetmanager.gui.components.Spinner;
import org.micromanager.lightsheetmanager.model.devices.cameras.CameraBase;

import javax.swing.JLabel;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class CameraTab extends Panel implements ListeningPanel {

    private ComboBox<String> cmbBinning_;
    private JLabel lblBinningState_;
    private int[] binningFactors_ = new int[0];
    private boolean refreshingBinning_;

    private Button btnUnchangedROI_;
    private Button btnFullROI_;
    private Button btnHalfROI_;
    private Button btnQuarterROI_;
    private Button btnEigthROI_;
    private Button btnCustomROI_;
    private Button btnCurrentROI_;

    private Spinner spnOffsetX_;
    private Spinner spnOffsetY_;
    private Spinner spnWidth_;
    private Spinner spnHeight_;

    private final LightSheetManager model_;

    public CameraTab(final LightSheetManager model) {
        model_ = Objects.requireNonNull(model);
        createUserInterface();
        createEventHandlers();
        refreshBinning();
    }

    private void createUserInterface() {
        final JLabel lblTitle = new JLabel("Camera Settings");
        lblTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));

        final Panel pnlBinning = new Panel("Binning");

        cmbBinning_ = new ComboBox<>(new String[0], null, 80, 20);
        cmbBinning_.setToolTipText("Set the binning of every active imaging camera.");
        lblBinningState_ = new JLabel(" ");

        pnlBinning.add(new JLabel("Active imaging cameras:"), "wrap");
        pnlBinning.add(cmbBinning_, "wrap");
        pnlBinning.add(lblBinningState_, "");

        final Panel pnlROI = new Panel("Imaging ROI");

        final JLabel lblOffsetX = new JLabel("Offset X:");
        final JLabel lblOffsetY = new JLabel("Offset Y:");
        final JLabel lblWidth = new JLabel("Width:");
        final JLabel lblHeight = new JLabel("Height:");

        btnUnchangedROI_ = new Button("Unchanged", 140, 30);
        btnFullROI_ = new Button("Full", 70, 30);
        btnHalfROI_ = new Button("1/2", 70, 30);
        btnQuarterROI_ = new Button("1/4", 70, 30);
        btnEigthROI_ = new Button("1/8", 70, 30);
        btnCustomROI_ = new Button("Custom", 140, 30);
        btnCurrentROI_ = new Button("Get Current ROI", 140, 30);

        spnOffsetX_ = Spinner.createIntegerSpinner(0, 0, Integer.MAX_VALUE, 1, 6);
        spnOffsetY_ = Spinner.createIntegerSpinner(0, 0, Integer.MAX_VALUE, 1, 6);
        spnWidth_ = Spinner.createIntegerSpinner(0, 0, Integer.MAX_VALUE, 1, 6);
        spnHeight_ = Spinner.createIntegerSpinner(0, 0, Integer.MAX_VALUE, 1, 6);

        // these buttons write the roi to the cameras at once, including cameras that are acquiring
        final String notDuringRun = " Do not use while an acquisition is running.";
        btnFullROI_.setToolTipText(
                "Set every active imaging camera to its full sensor." + notDuringRun);
        btnHalfROI_.setToolTipText("Set every active imaging camera to a centered ROI of half "
                + "the sensor width and height." + notDuringRun);
        btnQuarterROI_.setToolTipText("Set every active imaging camera to a centered ROI of a "
                + "quarter of the sensor width and height." + notDuringRun);
        btnEigthROI_.setToolTipText("Set every active imaging camera to a centered ROI of an "
                + "eighth of the sensor width and height." + notDuringRun);
        btnCustomROI_.setToolTipText("Set every active imaging camera to the offset and size "
                + "below." + notDuringRun);
        btnCurrentROI_.setToolTipText(
                "Fill in the fields above from the first imaging camera's current ROI.");
        spnOffsetX_.setToolTipText("Left edge of the Custom ROI, in binned pixels.");
        spnOffsetY_.setToolTipText("Top edge of the Custom ROI, in binned pixels.");
        spnWidth_.setToolTipText("Width of the Custom ROI, in binned pixels.");
        spnHeight_.setToolTipText("Height of the Custom ROI, in binned pixels.");

        pnlROI.add(btnUnchangedROI_, "span 2, wrap");
        pnlROI.add(btnFullROI_, "");
        pnlROI.add(btnHalfROI_, "wrap");
        pnlROI.add(btnQuarterROI_, "");
        pnlROI.add(btnEigthROI_, "wrap");
        pnlROI.add(btnCustomROI_, "span 2, wrap");
        pnlROI.add(lblOffsetX, "");
        pnlROI.add(spnOffsetX_, "wrap");
        pnlROI.add(lblOffsetY, "");
        pnlROI.add(spnOffsetY_, "wrap");
        pnlROI.add(lblWidth, "");
        pnlROI.add(spnWidth_, "wrap");
        pnlROI.add(lblHeight, "");
        pnlROI.add(spnHeight_, "wrap");
        pnlROI.add(btnCurrentROI_, "span 2");

        add(lblTitle, "wrap");
        add(pnlBinning, "wrap");
        add(pnlROI, "wrap");
    }

    private void createEventHandlers() {
        // set the binning on every imaging camera
        cmbBinning_.registerListener(() -> {
            // the combo box also fires while it is refilled
            if (refreshingBinning_) {
                return;
            }
            final int index = cmbBinning_.getSelectedIndex();
            if (index >= 0 && index < binningFactors_.length) {
                applyBinning(binningFactors_[index]);
            }
        });

        // roi is full camera
        btnFullROI_.registerListener(() -> applyROI(this::binnedSensor));

        // roi 1/2
        btnHalfROI_.registerListener(() -> applyROI(c -> computeCenterRectangle(binnedSensor(c), 2)));

        // roi 1/4
        btnQuarterROI_.registerListener(() -> applyROI(c -> computeCenterRectangle(binnedSensor(c), 4)));

        // roi 1/8
        btnEigthROI_.registerListener(() -> applyROI(c -> computeCenterRectangle(binnedSensor(c), 8)));

        // set custom roi
        btnCustomROI_.registerListener(() -> applyROI(c -> customROI()));

        // populate spinner with current roi
        btnCurrentROI_.registerListener(() -> {
            try {
                final Rectangle roi = model_.devices().firstImagingCamera().getROI();
                spnOffsetX_.setValue(roi.x);
                spnOffsetY_.setValue(roi.y);
                spnWidth_.setValue(roi.width);
                spnHeight_.setValue(roi.height);
            } catch (Exception e) {
                model_.studio().logs().showError(
                        "No imaging camera available; check that a camera is assigned in the hardware "
                        + "configuration and set as Active on the Acquisition tab.");
            }
        });
    }

    /**
     * Sets the binning on every imaging camera, then checks that they still agree.
     */
    private void applyBinning(final int factor) {
        // the tabs are locked during a run from the Acquisition tab, but not during a script run
        if (model_.acquisitions().isAcquisitionRunning()) {
            model_.studio().logs().showError(
                    "The binning cannot be changed during an acquisition.");
            refreshBinning();
            return;
        }
        final Map<String, CameraBase> active = model_.devices().activeImagingCameras();
        if (active.isEmpty()) {
            model_.studio().logs().showError("No imaging camera available; check that a camera is "
                    + "assigned in the hardware configuration and set as Active on the "
                    + "Acquisition tab.");
            return;
        }
        // refuse before any camera is written, so the cameras are never left at two sizes
        for (final Map.Entry<String, CameraBase> entry : active.entrySet()) {
            if (entry.getValue().isAcquiring()) {
                model_.studio().logs().showError("The binning cannot be changed while "
                        + entry.getKey() + " is acquiring.");
                refreshBinning();
                return;
            }
        }

        final List<String> rejected = new ArrayList<>();
        for (final Map.Entry<String, CameraBase> entry : active.entrySet()) {
            if (!entry.getValue().setBinning(factor)) {
                rejected.add(entry.getKey());
            }
        }

        final String mismatch = CameraBase.describeFrameSizeMismatch(active);
        // the slice timing depends on the camera ROI, which a binning change can reset
        model_.acquisitions().updateDurationLabels();
        refreshBinning();
        if (rejected.isEmpty() && mismatch == null) {
            return;
        }

        final StringBuilder message = new StringBuilder();
        if (!rejected.isEmpty()) {
            message.append("Binning ").append(factor).append("x").append(factor)
                    .append(" was not set on: ").append(String.join(", ", rejected))
                    .append(".\n\nSee the CoreLog for the reason.\n\n");
        }
        if (mismatch != null) {
            message.append("The imaging cameras no longer agree on frame size: ")
                    .append(mismatch)
                    .append(".\n\nAcquisitions are blocked until they match.");
        }
        model_.studio().logs().showError(message.toString().trim());
    }

    /**
     * Fills the binning control with the factors every imaging camera offers, and selects the
     * one they all report, or nothing when they differ.
     */
    private void refreshBinning() {
        refreshingBinning_ = true;
        try {
            cmbBinning_.removeAllItems();
            final Map<String, CameraBase> active = model_.devices().activeImagingCameras();
            final CameraBase[] cameras = active.values().toArray(new CameraBase[0]);
            binningFactors_ = CameraBase.commonAllowedBinnings(cameras);
            for (final int factor : binningFactors_) {
                cmbBinning_.addItem(factor + "x" + factor);
            }
            // adding the first item selects it
            cmbBinning_.setSelectedIndex(-1);
            if (cameras.length == 0) {
                showBinningState("No imaging camera is active.", false);
            } else {
                final List<String> lines = new ArrayList<>();
                int agreed = -1;
                boolean allKnown = true;
                boolean agree = true;
                for (final Map.Entry<String, CameraBase> entry : active.entrySet()) {
                    final int binning = binningOf(entry.getValue());
                    if (binning < 1) {
                        allKnown = false;
                    } else if (agreed == -1) {
                        agreed = binning;
                    } else if (binning != agreed) {
                        agree = false;
                    }
                    lines.add(entry.getKey() + " = "
                            + (binning > 0 ? binning + "x" + binning : "unknown"));
                }
                if (binningFactors_.length == 0) {
                    lines.add("(no binning every camera offers)");
                }
                showBinningState(String.join("<br>", lines), allKnown && !agree);
                // an unreadable camera is never shown as agreeing
                if (allKnown && agree) {
                    for (int i = 0; i < binningFactors_.length; i++) {
                        if (binningFactors_[i] == agreed) {
                            cmbBinning_.setSelectedIndex(i);
                        }
                    }
                }
            }
            cmbBinning_.setEnabled(binningFactors_.length > 0);
        } catch (Exception e) {
            showBinningState("The camera binning could not be read; see the CoreLog.", false);
            cmbBinning_.setEnabled(false);
            model_.studio().logs().logError("could not read the camera binning: " + e.getMessage());
        } finally {
            refreshingBinning_ = false;
        }
    }

    /**
     * Shows the binning state one line per camera, in red when the cameras differ.
     */
    private void showBinningState(final String text, final boolean differ) {
        lblBinningState_.setText("<html>" + text + "</html>");
        lblBinningState_.setForeground(differ ? Color.RED : UIManager.getColor("Label.foreground"));
    }

    /**
     * Returns the camera's binning factor, or -1 if it cannot be read.
     */
    private static int binningOf(final CameraBase camera) {
        try {
            return camera.getBinning();
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * Applies a per-camera ROI to every imaging camera, then checks that they still agree.
     *
     * <p>The cameras must end up with the same frame size or the next acquisition kills the JVM, so
     * a partial apply is reported rather than swallowed: the vendor adapter rejects an out-of-range
     * ROI per camera, which is exactly how two cameras end up at different sizes. Failures are
     * collected and shown once, after every camera has been tried; the previous per-camera modal
     * dialog inside the loop froze the UI for seconds at a time.
     *
     * @param target computes the ROI to apply to a given camera, in binned pixels
     */
    private void applyROI(final Function<CameraBase, Rectangle> target) {
        final Map<String, CameraBase> cameras = model_.devices().activeImagingCameras();
        if (cameras.isEmpty()) {
            model_.studio().logs().showError("No imaging camera available; check that a camera is "
                    + "assigned in the hardware configuration and set as Active on the "
                    + "Acquisition tab.");
            return;
        }

        final List<String> rejected = new ArrayList<>();
        for (final Map.Entry<String, CameraBase> entry : cameras.entrySet()) {
            if (!entry.getValue().setROI(target.apply(entry.getValue()))) {
                rejected.add(entry.getKey());
            }
        }

        final String mismatch = CameraBase.describeFrameSizeMismatch(cameras);
        // the slice timing depends on the camera ROI
        model_.acquisitions().updateDurationLabels();
        if (rejected.isEmpty() && mismatch == null) {
            return; // every camera accepted the roi and they all agree
        }

        final StringBuilder message = new StringBuilder();
        if (!rejected.isEmpty()) {
            message.append("The requested ROI was rejected by: ")
                    .append(String.join(", ", rejected))
                    .append(".\n\nROI coordinates are in binned pixels, so the largest usable "
                            + "offset and size shrink as binning grows.\n\n");
        }
        if (mismatch != null) {
            message.append("The imaging cameras no longer agree on frame size: ")
                    .append(mismatch)
                    .append(".\n\nAcquisitions are blocked until they match, because cameras with "
                            + "different frame sizes crash Micro-Manager outright.");
        }
        model_.studio().logs().showError(message.toString().trim());
    }

    /**
     * Returns this camera's sensor size in binned pixels, i.e. the largest ROI it can be given.
     *
     * <p>{@code core.setROI()} takes binned coordinates while
     * {@link CameraBase#getResolution()} deliberately reports unbinned pixels, so the sensor has to
     * be scaled down before it is used as an ROI. Skipping this made every preset out of range at
     * binning above 1: a 2400 px sensor at 2x2 binning only addresses 1200.
     */
    private Rectangle binnedSensor(final CameraBase camera) {
        final Rectangle sensor = camera.getResolution();
        final int binning = Math.max(1, camera.getBinning());
        return new Rectangle(0, 0, sensor.width / binning, sensor.height / binning);
    }

    // Returns the custom ROI set by the spinners.
    private Rectangle customROI() {
        return new Rectangle(
                spnOffsetX_.getInt(), spnOffsetY_.getInt(),
                spnWidth_.getInt(), spnHeight_.getInt()
        );
    }

    // Computes the ROI based on a scaling factor.
    private Rectangle computeCenterRectangle(final Rectangle rect, final int scale) {
        if (scale < 1) {
            model_.studio().logs().showError("scale must be >= 1; could not compute ROI!");
            return rect;
        }
        if (rect.x != 0 || rect.y != 0) {
            model_.studio().logs().showError("position must be (0, 0); could not compute ROI!");
            return rect;
        }
        final int width = rect.width / scale;
        final int height = rect.height / scale;
        final int x = (rect.width - width) / 2;
        final int y = (rect.height - height) / 2;
        return new Rectangle(x, y, width, height);
    }

    @Override
    public void selected() {
        // the main window and the Device Property Browser can change the binning
        refreshBinning();
    }

    @Override
    public void unselected() {

    }
}
