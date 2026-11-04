package org.micromanager.lightsheetmanager.model.acquisitions;

import java.text.NumberFormat;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Where a run is, as the status line of the acquisition tab shows it. It comes from the count of
 * stored images: a run acquires its time points in order and its positions in order inside each,
 * so the count says which one is in progress.
 *
 * <p>Immutable. The acquisition thread publishes a {@link Source} per dataset, and the interface
 * thread takes a snapshot from it when it redraws.
 */
public final class RunProgress implements Supplier<RunProgress> {

    /** The states of the status line. */
    public enum Status {
        /** no run, or the last one opened no dataset */
        NONE,
        /** no dataset open yet, so the run's size is not known */
        STARTING,
        /** a run is in progress */
        ACQUIRING,
        /** waiting for the next time point */
        WAITING,
        /** ended with every image */
        DONE,
        /** ended short of images, with no stop and no error */
        SHORT,
        /** stopped before every image was in */
        STOPPED,
        /** ended on an error */
        FAILED
    }

    private static final RunProgress NONE = new RunProgress(Status.NONE, null, 0, 0, 0L, 0L);
    private static final RunProgress STARTING =
            new RunProgress(Status.STARTING, null, 0, 0, 0L, 0L);
    private static final RunProgress FAILED_START =
            new RunProgress(Status.FAILED, null, 0, 0, 0L, 0L);

    private final Status status;
    // null until the run's size is known
    private final Shape shape;
    // from 1: where the run is or where it ended; position is 0 while waiting for a time point
    private final int timePoint;
    private final int position;
    private final long msToNextTimePoint;
    private final long images;

    private RunProgress(final Status status, final Shape shape, final int timePoint,
                        final int position, final long msToNextTimePoint, final long images) {
        this.status = status;
        this.shape = shape;
        this.timePoint = timePoint;
        this.position = position;
        this.msToNextTimePoint = msToNextTimePoint;
        this.images = images;
    }

    public static RunProgress none() {
        return NONE;
    }

    public static RunProgress starting() {
        return STARTING;
    }

    /**
     * The progress of a run in flight.
     *
     * @param firstTimePoint the time point the dataset starts at, from 0
     * @param imagesBefore the images of the datasets already complete
     * @param inDataset the images the dataset holds
     */
    static RunProgress during(final Shape shape, final int firstTimePoint, final long imagesBefore,
                              final long inDataset, final Schedule schedule) {
        final long perTimePoint = shape.imagesPerTimePoint();
        final long saved = Math.max(0L, inDataset);
        // the first time point not complete, or the last one once every image is in
        final int timeIndex = (int) Math.min(firstTimePoint + saved / perTimePoint,
                shape.numTimePoints - 1);
        // whole past the last time point, so the line stays on its last position
        final long inTimePoint = saved - (timeIndex - firstTimePoint) * perTimePoint;
        final int positionIndex =
                (int) Math.min(inTimePoint / shape.imagesPerPosition, shape.numPositions - 1);
        final long images = Math.max(0L, imagesBefore) + saved;
        // nothing of this time point yet: waiting for it if it is not due
        if (timeIndex > 0 && inTimePoint == 0) {
            final long ms = schedule.msUntil(timeIndex);
            if (ms > 0) {
                return new RunProgress(Status.WAITING, shape, timeIndex + 1, 0, ms, images);
            }
        }
        return new RunProgress(Status.ACQUIRING, shape, timeIndex + 1, positionIndex + 1, 0L,
                images);
    }

    /**
     * How the run ended, from where it was. A run that opened no dataset ended with nothing,
     * unless its start failed.
     */
    RunProgress ended(final boolean failed, final boolean stopped) {
        if (shape == null) {
            return failed ? FAILED_START : NONE;
        }
        final Status end;
        if (failed) {
            end = Status.FAILED;
        } else if (images >= imagesExpected()) {
            // every image is in, whenever the stop came
            end = Status.DONE;
        } else if (stopped) {
            end = Status.STOPPED;
        } else {
            end = Status.SHORT;
        }
        return new RunProgress(end, shape, timePoint, position, 0L, images);
    }

    /**
     * A snapshot never changes, so it is its own source.
     */
    @Override
    public RunProgress get() {
        return this;
    }

    public Status status() {
        return status;
    }

    /**
     * The images so far, over all the run's datasets.
     */
    public long images() {
        return images;
    }

    /**
     * The images the run is built for, or 0 until its size is known.
     */
    public long imagesExpected() {
        return shape == null ? 0L : shape.numTimePoints * shape.imagesPerTimePoint();
    }

    /**
     * The images so far against the images expected, or empty until the run's size is known.
     */
    public String describeImages() {
        final long expected = imagesExpected();
        return expected == 0 ? "" : count(images) + " of " + count(expected) + " images";
    }

    /**
     * The text of the status line.
     */
    public String describe() {
        switch (status) {
            case STARTING:
                return "Starting";
            case ACQUIRING:
                return "Acquiring" + where();
            case WAITING:
                return "Next time point (" + count(timePoint) + " of " + count(shape.numTimePoints)
                        + ") in " + count(msToNextTimePoint / 1000) + " s";
            case DONE:
                return "Finished with " + amount(shape.numTimePoints, "time point") + " and "
                        + amount(shape.numPositions, "position");
            case SHORT:
                return "Finished with " + amount(imagesExpected() - images, "image") + " missing";
            case STOPPED:
                return "Stopped" + whereItEnded();
            case FAILED:
                return shape == null ? "Failed to start" : "Failed" + whereItEnded();
            default:
                return "No acquisition in progress";
        }
    }

    // a count is named only when the run has more than one
    private String where() {
        final StringBuilder text = new StringBuilder();
        if (shape.numTimePoints > 1) {
            text.append(" time point ").append(count(timePoint))
                    .append(" of ").append(count(shape.numTimePoints));
        }
        if (shape.numPositions > 1) {
            if (shape.numTimePoints > 1) {
                text.append(';');
            }
            text.append(" position ").append(count(position))
                    .append(" of ").append(count(shape.numPositions));
        }
        return text.toString();
    }

    private String whereItEnded() {
        if (position == 0) {
            return " before time point " + count(timePoint) + " of " + count(shape.numTimePoints);
        }
        final String where = where();
        return where.isEmpty() ? "" : " at" + where;
    }

    private static String amount(final long n, final String noun) {
        return count(n) + " " + noun + (n == 1 ? "" : "s");
    }

    // a new instance each time: a NumberFormat is not thread safe
    private static String count(final long n) {
        return NumberFormat.getIntegerInstance().format(n);
    }

    /**
     * The size of a run.
     */
    static final class Shape {
        final int numTimePoints;
        final int numPositions;
        // slices x channels x cameras
        final int imagesPerPosition;

        Shape(final int numTimePoints, final int numPositions, final int imagesPerPosition) {
            this.numTimePoints = Math.max(1, numTimePoints);
            this.numPositions = Math.max(1, numPositions);
            this.imagesPerPosition = Math.max(1, imagesPerPosition);
        }

        long imagesPerTimePoint() {
            return (long) numPositions * imagesPerPosition;
        }
    }

    /**
     * When each time point of a run is due.
     */
    interface Schedule {
        /** Nothing waits: the controller paces the time points. */
        Schedule NONE = timeIndex -> 0L;

        /**
         * @param timeIndex the time point, from 0
         * @return the milliseconds until it is due, zero or less once it is due or with no start
         */
        long msUntil(int timeIndex);
    }

    /**
     * The schedule of a run in one dataset: time point t is due t intervals after the
     * acquisition's start time, on the wall clock AcqEngJ waits on.
     *
     * @param startMs the acquisition's start time, negative until the first image
     */
    static Schedule fromAcquisitionStart(final LongSupplier startMs, final double intervalMs,
                                         final LongSupplier nowMs) {
        return timeIndex -> {
            final long start = startMs.getAsLong();
            if (start < 0) {
                return 0L;
            }
            return start + (long) (timeIndex * intervalMs) - nowMs.getAsLong();
        };
    }

    /**
     * The schedule of a separate time point series: time point t is due t intervals after the
     * series started, on the monotonic clock the series waits on.
     */
    static Schedule fromSeriesStart(final long startNs, final long intervalMs,
                                    final LongSupplier nowNs) {
        return timeIndex -> (startNs + timeIndex * intervalMs * 1_000_000L - nowNs.getAsLong())
                / 1_000_000L;
    }

    /**
     * Reads a run's progress from the count of its current dataset.
     */
    static final class Source implements Supplier<RunProgress> {
        private final Shape shape;
        private final int firstTimePoint;
        private final long imagesBefore;
        private final LongSupplier inDataset;
        private final Schedule schedule;

        Source(final Shape shape, final int firstTimePoint, final long imagesBefore,
               final LongSupplier inDataset, final Schedule schedule) {
            this.shape = Objects.requireNonNull(shape);
            this.firstTimePoint = firstTimePoint;
            this.imagesBefore = imagesBefore;
            this.inDataset = Objects.requireNonNull(inDataset);
            this.schedule = Objects.requireNonNull(schedule);
        }

        // the count first: AcqEngJ sets its start time before the first image is stored, and a late
        // read of it costs one tick
        @Override
        public RunProgress get() {
            return during(shape, firstTimePoint, imagesBefore, inDataset.getAsLong(), schedule);
        }
    }
}
