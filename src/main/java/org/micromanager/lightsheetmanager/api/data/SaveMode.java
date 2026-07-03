package org.micromanager.lightsheetmanager.api.data;

import org.micromanager.data.Datastore;
import org.micromanager.lightsheetmanager.model.DataStorage;

public enum SaveMode {
    SINGLEPLANE_TIFF_SERIES("Single Plane TIFF"),
    MULTIPAGE_TIFF("Multi Page TIFF"),
    ND_TIFF("NDTiff");

    private final String text_;

    SaveMode(final String text) {
        text_ = text;
    }

    public static Datastore.SaveMode convert(final DataStorage.SaveMode mode) {
        switch (mode) {
            case ND_TIFF:
                return Datastore.SaveMode.ND_TIFF;
            case MULTIPAGE_TIFF:
                return Datastore.SaveMode.MULTIPAGE_TIFF;
            case SINGLEPLANE_TIFF_SERIES:
            default:
                return Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES;
        }
    }

    @Override
    public String toString() {
        return text_;
    }

}