package org.micromanager.lightsheetmanager.gui.frames;

import net.miginfocom.swing.MigLayout;
import org.micromanager.PositionList;
import org.micromanager.lightsheetmanager.gui.data.Icons;
import org.micromanager.lightsheetmanager.gui.components.Button;
import org.micromanager.lightsheetmanager.gui.components.CheckBox;
import org.micromanager.lightsheetmanager.gui.components.Panel;
import org.micromanager.lightsheetmanager.gui.components.Spinner;
import org.micromanager.internal.utils.WindowPositioning;
import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.gui.utils.DialogUtils;
import org.micromanager.lightsheetmanager.model.XYZGrid;

import javax.swing.JFrame;
import javax.swing.JLabel;
import java.util.Objects;

public class XYZGridFrame extends JFrame {

    private Button btnEditPositionList_;
    private Button btnComputeGrid_;
    private Button btnRunOverviewAcq_;

    private JLabel lblXStart_;
    private JLabel lblYStart_;
    private JLabel lblZStart_;

    private JLabel lblXStop_;
    private JLabel lblYStop_;
    private JLabel lblZStop_;

    private JLabel lblXDelta_;
    private JLabel lblYDelta_;
    private JLabel lblZDelta_;

    private JLabel lblXCount_;
    private JLabel lblYCount_;
    private JLabel lblZCount_;

    private CheckBox cbxUseX_;
    private CheckBox cbxUseY_;
    private CheckBox cbxUseZ_;

    private Spinner spnXStart_;
    private Spinner spnXStop_;
    private Spinner spnXDelta_;

    private Spinner spnYStart_;
    private Spinner spnYStop_;
    private Spinner spnYDelta_;

    private Spinner spnZStart_;
    private Spinner spnZStop_;
    private Spinner spnZDelta_;

    private Spinner spnOverlapYZ_;
    private CheckBox cbxClearPositions_;

    private JLabel lblXCountValue_;
    private JLabel lblYCountValue_;
    private JLabel lblZCountValue_;

    private LightSheetManager model_;

    public XYZGridFrame(final LightSheetManager model) {
        model_ = Objects.requireNonNull(model);
        WindowPositioning.setUpBoundsMemory(this, this.getClass(), this.getClass().getSimpleName());
        createUserInterface();
        createEventHandlers();
        loadFromSettings();
    }

    private void createUserInterface() {
        setTitle("XYZ Grid");
        setIconImage(Icons.MICROSCOPE.getImage());
        setResizable(false);

        setLayout(new MigLayout(
                "insets 10 10 10 10",
                "[]10[]",
                "[]10[]"
        ));

        btnComputeGrid_ = new Button("Compute Grid", 160, 26);
        btnEditPositionList_ = new Button("Edit Position List...", 160, 26);
        btnRunOverviewAcq_ = new Button("Run Overview Acquisition", 160, 26);
        btnRunOverviewAcq_.setEnabled(false);

        cbxUseX_ = new CheckBox("Slices from stage coordinates", false);
        cbxUseY_ = new CheckBox("Grid in Y", false);
        cbxUseZ_ = new CheckBox("Grid in Z", false);

        // init all values to zero
        lblXCountValue_ = new JLabel("0");
        lblYCountValue_ = new JLabel("0");
        lblZCountValue_ = new JLabel("0");

        final Panel pnlX = new Panel(cbxUseX_);
        final Panel pnlY = new Panel(cbxUseY_);
        final Panel pnlZ = new Panel(cbxUseZ_);
        final Panel pnlButtons = new Panel();
        pnlX.setMigLayout(
               "insets 10 10 10 10",
               "[]10[]",
               "[]10[]"
        );
        pnlX.setMigLayout(
               "insets 10 10 10 10",
               "[]10[]",
               "[]10[]"
        );
        pnlX.setMigLayout(
               "insets 10 10 10 10",
               "[]10[]",
               "[]10[]"
        );

        // X
        lblXStart_ = new JLabel("X start [µm]:");
        lblXStop_ = new JLabel("X stop [µm]:");
        lblXDelta_ = new JLabel("X delta [µm]:");
        lblXCount_ = new JLabel("Slice count:");

        spnXStart_ = Spinner.createDoubleSpinner(0.0, -Double.MAX_VALUE, Double.MAX_VALUE, 100.0, 7);
        spnXStop_ = Spinner.createDoubleSpinner(0.0, -Double.MAX_VALUE, Double.MAX_VALUE, 100.0, 7);
        spnXDelta_ = Spinner.createDoubleSpinner(0.0, -Double.MAX_VALUE, Double.MAX_VALUE, 100.0, 7);

        // Y
        lblYStart_ = new JLabel("Y start [µm]:");
        lblYStop_ = new JLabel("Y stop [µm]:");
        lblYDelta_ = new JLabel("Y delta [µm]:");
        lblYCount_ = new JLabel("Y count:");

        spnYStart_ = Spinner.createDoubleSpinner(0.0, -Double.MAX_VALUE, Double.MAX_VALUE, 100.0, 7);
        spnYStop_ = Spinner.createDoubleSpinner(0.0, -Double.MAX_VALUE, Double.MAX_VALUE, 100.0, 7);
        spnYDelta_ = Spinner.createDoubleSpinner(0.0, -Double.MAX_VALUE, Double.MAX_VALUE, 100.0, 7);

        // Z
        lblZStart_ = new JLabel("Z start [µm]:");
        lblZStop_ = new JLabel("Z stop [µm]:");
        lblZDelta_ = new JLabel("Z delta [µm]:");
        lblZCount_ = new JLabel("Z count:");

        spnZStart_ = Spinner.createDoubleSpinner(0.0, -Double.MAX_VALUE, Double.MAX_VALUE, 100.0, 7);
        spnZStop_ = Spinner.createDoubleSpinner(0.0, -Double.MAX_VALUE, Double.MAX_VALUE, 100.0, 7);
        spnZDelta_ = Spinner.createDoubleSpinner(0.0,-Double.MAX_VALUE, Double.MAX_VALUE, 100.0, 7);

        final Panel pnlSettings = new Panel("Grid Settings");
        pnlSettings.setMigLayout(
               "insets 10 10 10 10",
               "[]10[]",
               "[]10[]"
        );

        final JLabel lblOverlap = new JLabel("Overlap (Y and Z) [%]:");
        spnOverlapYZ_ = Spinner.createIntegerSpinner(10, 0, 100, 1, 4);
        cbxClearPositions_ = new CheckBox("Clear position list if YZ unused", false);

        pnlX.add(lblXStart_, "");
        pnlX.add(spnXStart_, "wrap");
        pnlX.add(lblXStop_, "");
        pnlX.add(spnXStop_, "wrap");
        pnlX.add(lblXDelta_, "");
        pnlX.add(spnXDelta_, "wrap");
        pnlX.add(lblXCount_, "");
        pnlX.add(lblXCountValue_, "");

        pnlY.add(lblYStart_, "");
        pnlY.add(spnYStart_, "wrap");
        pnlY.add(lblYStop_, "");
        pnlY.add(spnYStop_, "wrap");
        pnlY.add(lblYDelta_, "");
        pnlY.add(spnYDelta_, "wrap");
        pnlY.add(lblYCount_, "");
        pnlY.add(lblYCountValue_, "");

        pnlZ.add(lblZStart_, "");
        pnlZ.add(spnZStart_, "wrap");
        pnlZ.add(lblZStop_, "");
        pnlZ.add(spnZStop_, "wrap");
        pnlZ.add(lblZDelta_, "");
        pnlZ.add(spnZDelta_, "wrap");
        pnlZ.add(lblZCount_, "");
        pnlZ.add(lblZCountValue_, "");

        pnlSettings.add(lblOverlap, "split 2");
        pnlSettings.add(spnOverlapYZ_, "wrap");
        pnlSettings.add(cbxClearPositions_, "wrap");

        pnlButtons.add(btnComputeGrid_, "wrap");
        pnlButtons.add(btnEditPositionList_, "wrap");
        pnlButtons.add(btnRunOverviewAcq_, "");

        add(pnlY, "growx");
        add(pnlZ, "growx, wrap");
        add(pnlX, "growx");
        add(pnlSettings, "wrap");
        add(pnlButtons, "");

        pack(); // fit window size to layout
        setIconImage(Icons.MICROSCOPE.getImage());
    }

    private void createEventHandlers() {
        final XYZGrid grid = model_.pluginSettings().xyzGrid();

        // Check Boxes
        cbxUseX_.registerListener(() -> {
            final boolean selected = cbxUseX_.isSelected();
            grid.setUseX(selected);
            setEnabledX(selected);
        });

        cbxUseY_.registerListener(() -> {
            final boolean selected = cbxUseY_.isSelected();
            grid.setUseY(selected);
            setEnabledY(selected);
        });

        cbxUseZ_.registerListener(() -> {
            final boolean selected = cbxUseZ_.isSelected();
            grid.setUseZ(selected);
            setEnabledZ(selected);
        });

        cbxClearPositions_.registerListener(
                () -> grid.setClearYZ(cbxClearPositions_.isSelected()));

        // Spinners X
        spnXStart_.registerListener(() -> grid.setStartX(spnXStart_.getDouble()));
        spnXStop_.registerListener(() -> grid.setStopX(spnXStop_.getDouble()));
        spnXDelta_.registerListener(() -> grid.setDeltaX(spnXDelta_.getDouble()));

        // Spinners Y
        spnYStart_.registerListener(() -> grid.setStartY(spnYStart_.getDouble()));
        spnYStop_.registerListener(() -> grid.setStopY(spnYStop_.getDouble()));
        spnYDelta_.registerListener(() -> grid.setDeltaY(spnYDelta_.getDouble()));

        // Spinners Z
        spnZStart_.registerListener(() -> grid.setStartZ(spnZStart_.getDouble()));
        spnZStop_.registerListener(() -> grid.setStopZ(spnZStop_.getDouble()));
        spnZDelta_.registerListener(() -> grid.setDeltaZ(spnZDelta_.getDouble()));

        // Overlap
        spnOverlapYZ_.registerListener(() -> grid.setOverlapYZ(spnOverlapYZ_.getInt()));

        // compute XYZ grid
        btnComputeGrid_.registerListener(() -> {
            final PositionList positionList = model_.studio().positions().getPositionList();
            if (positionList.getNumberOfPositions() != 0) {
                final boolean result = DialogUtils.showYesNoDialog(this, "Warning",
                        "Do you want to overwrite the existing position list?");
                if (!result) {
                    return; // early exit => do not overwrite
                }
            }
            // no positions in list
            grid.computeGrid(model_);
            loadFromSettings();
        });

        btnEditPositionList_.registerListener(
                () -> model_.studio().app().showPositionList());
        btnRunOverviewAcq_.registerListener(
                () -> model_.studio().logs().showError("Not implemented yet!")); // TODO: !!!
    }

    private void loadFromSettings() {
        final XYZGrid grid = model_.pluginSettings().xyzGrid();

        cbxUseX_.setSelected(grid.getUseX());
        cbxUseY_.setSelected(grid.getUseY());
        cbxUseZ_.setSelected(grid.getUseZ());
        cbxClearPositions_.setSelected(grid.getClearYZ());

        spnXStart_.setValue(grid.getStartX());
        spnYStart_.setValue(grid.getStartY());
        spnZStart_.setValue(grid.getStartZ());

        spnXStop_.setValue(grid.getStopX());
        spnYStop_.setValue(grid.getStopY());
        spnZStop_.setValue(grid.getStopZ());

        spnXDelta_.setValue(grid.getDeltaX());
        spnYDelta_.setValue(grid.getDeltaY());
        spnZDelta_.setValue(grid.getDeltaZ());

        spnOverlapYZ_.setValue(grid.getOverlapYZ());

        // enable/disable based on loaded settings
        setEnabledX(grid.getUseX());
        setEnabledY(grid.getUseY());
        setEnabledZ(grid.getUseZ());
    }

    private void setEnabledX(final boolean state) {
        lblXStart_.setEnabled(state);
        lblXStop_.setEnabled(state);
        lblXDelta_.setEnabled(state);
        spnXStart_.setEnabled(state);
        spnXStop_.setEnabled(state);
        spnXDelta_.setEnabled(state);
        lblXCount_.setEnabled(state);
        lblXCountValue_.setEnabled(state);
    }

    private void setEnabledY(final boolean state) {
        lblYStart_.setEnabled(state);
        lblYStop_.setEnabled(state);
        lblYDelta_.setEnabled(state);
        spnYStart_.setEnabled(state);
        spnYStop_.setEnabled(state);
        spnYDelta_.setEnabled(state);
        lblYCount_.setEnabled(state);
        lblYCountValue_.setEnabled(state);
    }

    private void setEnabledZ(final boolean state) {
        lblZStart_.setEnabled(state);
        lblZStop_.setEnabled(state);
        lblZDelta_.setEnabled(state);
        spnZStart_.setEnabled(state);
        spnZStop_.setEnabled(state);
        spnZDelta_.setEnabled(state);
        lblZCount_.setEnabled(state);
        lblZCountValue_.setEnabled(state);
    }
}
