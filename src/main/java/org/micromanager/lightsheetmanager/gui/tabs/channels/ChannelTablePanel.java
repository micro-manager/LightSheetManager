package org.micromanager.lightsheetmanager.gui.tabs.channels;

import org.micromanager.lightsheetmanager.api.AcquisitionSettings;
import org.micromanager.lightsheetmanager.api.internal.ScapeAcquisitionSettings;
import org.micromanager.lightsheetmanager.gui.components.Button;
import org.micromanager.lightsheetmanager.gui.components.CheckBox;
import org.micromanager.lightsheetmanager.gui.components.ComboBox;
import org.micromanager.lightsheetmanager.gui.components.Panel;
import org.micromanager.lightsheetmanager.LightSheetManager;
import org.micromanager.lightsheetmanager.api.data.ChannelMode;
import org.micromanager.lightsheetmanager.api.data.GeometryType;
import org.micromanager.lightsheetmanager.gui.components.SettingsListener;
import org.micromanager.lightsheetmanager.gui.utils.DialogUtils;
import org.micromanager.lightsheetmanager.model.channels.ChannelSpec;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.util.Objects;

/**
 * This panel contains the ChannelTable and controls.
 */
public class ChannelTablePanel extends Panel implements SettingsListener {

    private JLabel lblChannelGroup_;
    private JLabel lblChangeChannel_;

    private Button btnAddChannel_;
    private Button btnRemoveChannel_;
    private Button btnRefresh_;

    private ComboBox<String> cmbChannelGroup_;
    private ComboBox<ChannelMode> cmbChannelMode_;

    // The last mode actually written to the settings, used to put the combo back when a mode is
    // refused. Tracked here rather than read back from the settings, which lag the builder.
    private ChannelMode lastChannelMode_;

    private final ChannelTable table_;
    private final LightSheetManager model_;

    public ChannelTablePanel(final LightSheetManager model, final CheckBox checkBox) {
        super(checkBox);
        model_ = Objects.requireNonNull(model);
        table_ = new ChannelTable(model_);
        createUserInterface();
        createEventHandlers();
        model.userSettings().addChangeListener(this);
    }

    /**
     * Returns true when the geometry cannot use per-volume hardware channel switching.
     * <p>
     * The controller clocks that channel counter from the view select signal, which does not
     * alternate on a single view geometry, so the mode is only usable with two views.
     */
    private boolean isVolumeHwRefused() {
        return model_.devices().adapter().geometry() == GeometryType.SCAPE;
    }

    private void createUserInterface() {
        lblChannelGroup_ = new JLabel("Channel Group:");
        lblChangeChannel_ = new JLabel("Channel Mode:");

        btnAddChannel_ = new Button("Add", 74, 24);
        btnRemoveChannel_ = new Button("Remove", 74, 24);
        btnRefresh_ = new Button("Refresh", 74, 24);

        btnAddChannel_.setToolTipText("Add a new channel to the table.");
        btnRemoveChannel_.setToolTipText("Remove the currently selected channel from the table.");
        btnRefresh_.setToolTipText("Refresh the channel panel with the latest configuration group settings.");

        final String[] groupLabels = table_.getChannelGroups();
        cmbChannelGroup_ = new ComboBox<>(groupLabels,
                model_.acquisitions().settings().channels().group(),
                120, 22);

        lastChannelMode_ = model_.acquisitions().settings().channels().mode();
        if (lastChannelMode_ == ChannelMode.VOLUME_HW && isVolumeHwRefused()) {
            // Settings loaded from an earlier build could hold a mode that is refused below.
            lastChannelMode_ = ChannelMode.VOLUME;
        }
        cmbChannelMode_ = new ComboBox<>(ChannelMode.values(), lastChannelMode_, 140, 22);
        cmbChannelGroup_.setToolTipText(
                "Micro-Manager configuration group the channel presets come from.");
        cmbChannelMode_.setToolTipText("How often the channel changes, and whether the "
                + "software or the controller switches it.");

        add(lblChannelGroup_, "split 2");
        add(cmbChannelGroup_, "wrap");
        add(table_, "wrap");
        add(btnAddChannel_, "split 3");
        add(btnRemoveChannel_, "");
        add(btnRefresh_, "wrap");
        add(lblChangeChannel_, "split 2");
        add(cmbChannelMode_, "");
    }

    private void createEventHandlers() {

        // select channel group
        cmbChannelGroup_.registerListener(() -> {
            final String channelGroup = cmbChannelGroup_.getSelected();
            table_.updatePresetComboBoxes(channelGroup);
            // set the channel group to use when we get the channels
            model_.acquisitions().settingsBuilder().channelBuilder().group(channelGroup);
            model_.acquisitions().updateSettings();
            // update the table data model and refresh ui
            table_.getData().setChannels(channelGroup, model_.acquisitions().settings().channels().data());
            table_.getData().setChannelGroup(channelGroup);
            table_.refreshData();
            // a different group can select a different number of channels, which changes the duration
            model_.acquisitions().updateDurationLabels();
        });

        // add channel
        btnAddChannel_.registerListener(() -> {
            table_.getTableModel().addChannel();
            final ChannelSpec[] channels = table_.getData().getChannels();
            model_.acquisitions().settingsBuilder().channelBuilder().data(channels);
            model_.acquisitions().updateDurationLabels();
        });

        // remove channel
        btnRemoveChannel_.registerListener(() -> {
            final int row = table_.getTable().getSelectedRow();
            if (row != -1) { // is any row selected?
                table_.getTableModel().removeChannel(row);
                final ChannelSpec[] channels = table_.getData().getChannels();
                model_.acquisitions().settingsBuilder().channelBuilder().data(channels);
                model_.acquisitions().updateDurationLabels();
            }
        });

        // refresh channel table
        btnRefresh_.registerListener(() -> {
            final String channelGroup = model_.acquisitions().settings().channels().group();
            final String[] groups = table_.getChannelGroups();
            cmbChannelGroup_.removeAllItems();
            for (String group : groups) {
                cmbChannelGroup_.addItem(group);
                if (group.equals(channelGroup)) {
                    // the currently selected channel group still exists
                    cmbChannelGroup_.setSelectedItem(channelGroup);
                }
            }
            table_.updatePresetComboBoxes(channelGroup);
            cmbChannelGroup_.updateUI();
        });

        // select channel mode
        cmbChannelMode_.registerListener(() -> {
            final ChannelMode selected = cmbChannelMode_.getSelected();
            if (selected == ChannelMode.VOLUME_HW && isVolumeHwRefused()) {
                SwingUtilities.invokeLater(() -> {
                    DialogUtils.showErrorMessage(cmbChannelMode_, "Not Implemented",
                            "Not implemented in SCAPE, please contact ASI to request this feature.");
                    cmbChannelMode_.setSelected(lastChannelMode_);
                });
            } else {
                model_.acquisitions().settingsBuilder().channelBuilder().mode(selected);
                lastChannelMode_ = selected;
                // per slice and per volume give different totals, so the labels have to be redone
                model_.acquisitions().updateDurationLabels();
            }
        });

    }

    /**
     * Enable or disable items in the channel table panel.
     *
     * @param state enabled or disabled
     */
    public void setItemsEnabled(final boolean state) {
        lblChannelGroup_.setEnabled(state);
        cmbChannelGroup_.setEnabled(state);
        btnAddChannel_.setEnabled(state);
        btnRemoveChannel_.setEnabled(state);
        lblChangeChannel_.setEnabled(state);
        cmbChannelMode_.setEnabled(state);
        table_.setEnabled(state);
        table_.getTable().setEnabled(state);
        table_.setHeaderRowColor(state);
    }

    @Override
    public void onSettingsChanged(final AcquisitionSettings settings) {
        if (settings instanceof ScapeAcquisitionSettings) {
            var settingsScape = (ScapeAcquisitionSettings) settings;
            cmbChannelGroup_.setSelectedItem(settingsScape.channels().group());
            cmbChannelMode_.setSelected(settingsScape.channels().mode());
            setItemsEnabled(settingsScape.channels().enabled());
        }
    }
}
