package org.micromanager.lightsheetmanager.model;

import org.micromanager.Studio;
import org.micromanager.data.Image;
import org.micromanager.lightsheetmanager.api.DataSink;
import org.micromanager.lightsheetmanager.api.data.SaveMode;

import java.util.Objects;

public class DataStorage implements DataSink {

    private final Studio studio_;
    private SaveMode saveMode_;

    public DataStorage(final Studio studio) {
        studio_ = Objects.requireNonNull(studio);
        saveMode_ = SaveMode.SINGLEPLANE_TIFF_SERIES;
    }

    @Override
    public void putImage(final Image image) {
    }

}
