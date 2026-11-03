package org.micromanager.lightsheetmanager.gui.tabs.channels;

import org.micromanager.lightsheetmanager.model.channels.ChannelSpec;
import org.micromanager.lightsheetmanager.model.channels.ChannelTableData;

import javax.swing.table.AbstractTableModel;
import java.util.Objects;

public class ChannelTableModel extends AbstractTableModel {

    // column constants
    private static final int COLUMN_USE = 0;
    private static final int COLUMN_PRESET = 1;
    private static final int COLUMN_OFFSET = 2;

    /**Column names for the channels table. */
    private final String[] columnNames_ = {
            "Use",
            "Preset",
            "Offset"
    };

    private final ChannelTableData data_;

    /** Runs after any accepted cell edit, to commit the table into the settings. */
    private final Runnable onEdit_;

    public ChannelTableModel(final ChannelTableData tableData, final Runnable onEdit) {
        data_ = Objects.requireNonNull(tableData);
        onEdit_ = Objects.requireNonNull(onEdit);
    }

    public void addChannel() {
        final int rowIndex = data_.getNumChannels();
        data_.addChannel();
        fireTableRowsInserted(rowIndex, rowIndex);
    }

    public void removeChannel(int rowIndex) {
        if (rowIndex != -1) {
           data_.removeChannel(rowIndex);
           fireTableRowsDeleted(rowIndex, rowIndex);
        }
    }

    @Override
    public int getRowCount() {
        return data_.getNumChannels();
    }

    @Override
    public int getColumnCount() {
        return columnNames_.length;
    }

    @Override
    public Class<?> getColumnClass(int c) {
        switch (c) {
            case COLUMN_USE:
                return Boolean.class;
            case COLUMN_PRESET:
                return String.class;
            case COLUMN_OFFSET:
                return Double.class;
            default:
                // Note: This will never happen if getColumnCount is correct.
                throw new IllegalArgumentException("Invalid column index: " + c);
        }
    }

    @Override
    public String getColumnName(int col) {
        return columnNames_[col];
    }

    @Override
    public boolean isCellEditable(int row, int col) {
        return true;
    }

    @Override
    public Object getValueAt(int row, int col) {
       final ChannelSpec channelSpec = data_.getChannelByIndex(row);
        switch (col) {
            case COLUMN_USE:
                return channelSpec.useChannel();
            case COLUMN_PRESET:
                return channelSpec.getName();
            case COLUMN_OFFSET:
                return channelSpec.getOffset();
            default:
                throw new IllegalArgumentException("Invalid column index: " + col);
        }
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
        final ChannelSpec channelSpec = data_.getChannelByIndex(row);
        switch (col) {
            case COLUMN_USE:
                if (value instanceof Boolean) {
                    channelSpec.setUsed((boolean) value);
                } else {
                   return; // early exit => wrong type
                }
                break;
            case COLUMN_PRESET:
                if (value instanceof String) {
                    channelSpec.setName((String) value);
                } else {
                   return; // early exit => wrong type
                }
                break;
            case COLUMN_OFFSET:
                if (value instanceof Double) {
                    channelSpec.setOffset((double) value);
                } else {
                   return; // early exit => wrong type
                }
                break;
            default:
                throw new IllegalArgumentException("Invalid column index: " + col);
        }

        // commit the edit; the wrong type cases above return before reaching this
        onEdit_.run();

        // update the table ui
        fireTableCellUpdated(row, col);
    }

}
