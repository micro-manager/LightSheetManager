package org.micromanager.lightsheetmanager.model;

import org.micromanager.Studio;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.lightsheetmanager.api.DataSink;

import java.util.Objects;

public class DataStorage implements DataSink {

    /**
     * Easier to convert to a String.
     */
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

    private final Studio studio_;
    private DataStorage.SaveMode saveMode_;

    public DataStorage(final Studio studio) {
        studio_ = Objects.requireNonNull(studio);
        saveMode_ = SaveMode.SINGLEPLANE_TIFF_SERIES;
    }

    @Override
    public void putImage(final Image image) {
    }

}
