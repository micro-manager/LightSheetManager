package org.micromanager.lightsheetmanager.model.acquisitions;

import org.micromanager.LogManager;
import org.micromanager.acqj.main.Acquisition;
import org.micromanager.data.Datastore;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Follows a run for the status line. The acquisition thread reports what happens, and the
 * interface thread asks where the run is.
 */
final class RunProgressTracker {

    private final Supplier<Datastore> datastore;

    // visibility: written on the acquisition thread, read on the edt by the status line
    private volatile Supplier<RunProgress> progress = RunProgress.none();

    /**
     * @param datastore the dataset in flight, or null with no dataset open
     */
    RunProgressTracker(final Supplier<Datastore> datastore) {
        this.datastore = Objects.requireNonNull(datastore);
    }

    /**
     * A snapshot of where the run in flight is, or of how the last run ended.
     */
    RunProgress now() {
        return progress.get();
    }

    /**
     * A run starts, so the line leaves the last run's end.
     */
    void start() {
        progress = RunProgress.starting();
    }

    /**
     * Publishes where the status line reads the progress from. Call this as each dataset starts
     * and between the datasets of a series; an engine that never does shows only Starting.
     */
    void show(final Supplier<RunProgress> source) {
        progress = Objects.requireNonNull(source);
    }

    /**
     * Keeps the progress as it is now, so the status line stops reading a complete dataset's
     * store before it is let go.
     */
    void freeze() {
        progress = progress.get();
    }

    Series series(final RunProgress.Shape shape, final RunProgress.Schedule schedule) {
        return new Series(shape, schedule);
    }

    /**
     * The images the dataset in flight holds so far, or 0 with no dataset open.
     */
    long imagesInDataset() {
        final Datastore store = datastore.get();
        if (store == null) {
            return 0L;
        }
        try {
            // -1 before the store has storage
            return Math.max(0L, store.getNumImages());
        } catch (RuntimeException e) {
            // a store closes with no lock, and a read landing in that must not fail the redraw or
            // the run's cleanup
            return 0L;
        }
    }

    /**
     * Leaves the status line on how the run ended. A failure also sets the abort flag, so a
     * failure wins over a stop.
     *
     * @param acquisition the run's acquisition, or null if it never had one
     * @param stopRequested true if a stop was requested
     */
    void end(final Acquisition acquisition, final boolean stopRequested) {
        final boolean failed = acquisition != null && keptAnException(acquisition);
        final boolean stopped = stopRequested
                || (acquisition != null && acquisition.isAbortRequested());
        progress = progress.get().ended(failed, stopped);
    }

    /**
     * Logs the images of a run that ended short, stopped or failed, since the status line keeps
     * them only until the next run starts.
     */
    void logEnd(final LogManager logs) {
        final RunProgress end = progress.get();
        final String stored = "stored " + end.images() + " of " + end.imagesExpected() + " images";
        switch (end.status()) {
            case SHORT:
                logs.logError("Acquisition finished short: " + stored);
                break;
            case FAILED:
                logs.logError("Acquisition failed: " + stored);
                break;
            case STOPPED:
                // the user's own choice, so not an error
                logs.logMessage("Acquisition stopped: " + stored);
                break;
            default:
                break;
        }
    }

    /**
     * Follows a series of separate time points: one dataset per time point, with a countdown
     * between them and the images of the datasets complete.
     */
    final class Series {
        private final RunProgress.Shape shape;
        private final RunProgress.Schedule schedule;
        // the images of the datasets complete so far, whole or not
        private long imagesComplete;

        private Series(final RunProgress.Shape shape, final RunProgress.Schedule schedule) {
            this.shape = shape;
            this.schedule = schedule;
        }

        /**
         * The dataset is not open yet, so the line counts down to it.
         */
        void awaiting(final int timeIndex) {
            show(new RunProgress.Source(shape, timeIndex, imagesComplete, () -> 0L, schedule));
        }

        /**
         * The dataset is open, so the line follows its count.
         */
        void acquiring(final int timeIndex) {
            show(new RunProgress.Source(shape, timeIndex, imagesComplete,
                    RunProgressTracker.this::imagesInDataset, schedule));
        }

        /**
         * Its count is final, so it joins the total and the line stops reading the store before
         * it is let go.
         */
        void datasetComplete() {
            imagesComplete += imagesInDataset();
            freeze();
        }
    }

    private static boolean keptAnException(final Acquisition acquisition) {
        try {
            acquisition.checkForExceptions();
            return false;
        } catch (Exception e) {
            return true;
        }
    }
}
