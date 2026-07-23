package org.micromanager.lightsheetmanager.model.acquisitions;

import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.acqj.internal.Engine;
import org.micromanager.acqj.main.AcqEngMetadata;
import org.micromanager.acqj.main.AcquisitionEvent;
import org.micromanager.acqj.util.AcquisitionEventIterator;
import org.micromanager.lightsheetmanager.api.AcquisitionSettings;
import org.micromanager.lightsheetmanager.model.channels.ChannelSpec;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.function.Function;

/**
 * Adapts {@code LightSheetManager} settings into {@code AcqEngJ} instructions.
 * <p>
 * This class translates {@link AcquisitionSettings} into lazy sequences
 * ({@link Iterator}s) of {@link AcquisitionEvent}s, based on the current
 * {@code LightSheetManager} configuration.
 */
public final class LightSheetEventAdapter {

    public static final String TIME_AXIS = "time";
    public static final String POSITION_AXIS = "position";
    public static final String CAMERA_AXIS = "channel";

    // TODO: put this in the channel iterator (should not be global)
    public static int currentChannelIndex_ = 0;
    public static boolean isUsingMultipleCameras = false;

    /**
     * This class should not be instantiated.
     */
    private LightSheetEventAdapter() {
        throw new AssertionError("Utility class; do not instantiate.");
    }

    public static Iterator<AcquisitionEvent> createTimelapseMultiChannelVolumeAcqEvents(
            AcquisitionEvent baseEvent, AcquisitionSettings settings,
            String[] cameraDeviceNames,
            Function<AcquisitionEvent, AcquisitionEvent> eventMonitor) {

        if (settings.numTimePoints() <= 1) {
            throw new RuntimeException("timelapse selected but only one timepoint");
        }
        Function<AcquisitionEvent, Iterator<AcquisitionEvent>> timelapse =
                timelapse(settings.numTimePoints(), settings.timePointInterval());

        if (settings.channels().count() == 1) {
            throw new RuntimeException("Expected multiple channels but only one found");
        }

        Function<AcquisitionEvent, Iterator<AcquisitionEvent>> channels =
                channels(settings.channels().used());

        Function<AcquisitionEvent, Iterator<AcquisitionEvent>> zStack =
                zStack(0, settings.volume().slicesPerView());

        Function<AcquisitionEvent, Iterator<AcquisitionEvent>> cameras = cameras(cameraDeviceNames);

        ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>> acqFunctions = new ArrayList<>();
        acqFunctions.add(timelapse);
        acqFunctions.add(channels);
        acqFunctions.add(cameras);
        acqFunctions.add(zStack);
        return new AcquisitionEventIterator(baseEvent, acqFunctions, eventMonitor);
    }

    public static Iterator<AcquisitionEvent> createTimelapseVolumeAcqEvents(
            AcquisitionEvent baseEvent, AcquisitionSettings settings,
            String[] cameraDeviceNames,
            Function<AcquisitionEvent, AcquisitionEvent> eventMonitor) {

        if (settings.numTimePoints() <= 1) {
            throw new RuntimeException("timelapse selected but only one timepoint");
        }
        Function<AcquisitionEvent, Iterator<AcquisitionEvent>> timelapse =
                timelapse(settings.numTimePoints(), null);

        Function<AcquisitionEvent, Iterator<AcquisitionEvent>> cameras = cameras(cameraDeviceNames);

        Function<AcquisitionEvent, Iterator<AcquisitionEvent>> zStack =
                zStack(0, settings.volume().slicesPerView());

        ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>> acqFunctions = new ArrayList<>();
        acqFunctions.add(timelapse);
        acqFunctions.add(cameras);
        acqFunctions.add(zStack);
        return new AcquisitionEventIterator(baseEvent, acqFunctions, eventMonitor);
    }

    /**
     *
     * @param interleaved true: do we want to do every channel at each z slice before moving to
     *                    the next z slice
     *                    false: do an entire volume in one channel, then the next one
     */
    public static Iterator<AcquisitionEvent> createMultiChannelVolumeAcqEvents(
            AcquisitionEvent baseEvent, AcquisitionSettings settings,
            String[] cameraDeviceNames,
            Function<AcquisitionEvent, AcquisitionEvent> eventMonitor, boolean interleaved) {

        Function<AcquisitionEvent, Iterator<AcquisitionEvent>> channels =
                channels(settings.channels().used());

        Function<AcquisitionEvent, Iterator<AcquisitionEvent>> zStack =
                zStack(0, settings.volume().slicesPerView());

        Function<AcquisitionEvent, Iterator<AcquisitionEvent>> cameras = cameras(cameraDeviceNames);

        ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>> acqFunctions = new ArrayList<>();
        if (interleaved) {
            acqFunctions.add(cameras);
            acqFunctions.add(zStack);
            acqFunctions.add(channels);
        } else {
            acqFunctions.add(channels);
            acqFunctions.add(cameras);
            acqFunctions.add(zStack);
        }
        return new AcquisitionEventIterator(baseEvent, acqFunctions, eventMonitor);
    }

    public static Iterator<AcquisitionEvent> createVolumeAcqEvents(
            AcquisitionEvent baseEvent, AcquisitionSettings settings,
            String[] cameraDeviceNames,
            Function<AcquisitionEvent, AcquisitionEvent> eventMonitor) {

        Function<AcquisitionEvent, Iterator<AcquisitionEvent>> cameras = cameras(cameraDeviceNames);

        Function<AcquisitionEvent, Iterator<AcquisitionEvent>> zStack =
                zStack(0, settings.volume().slicesPerView());

        ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>> acqFunctions = new ArrayList<>();
        acqFunctions.add(cameras);
        acqFunctions.add(zStack);
        return new AcquisitionEventIterator(baseEvent, acqFunctions, eventMonitor);
    }

    public static Iterator<AcquisitionEvent> createChannelAcqEvents(
            AcquisitionEvent baseEvent, AcquisitionSettings settings,
            String[] cameraDeviceNames,
            Function<AcquisitionEvent, AcquisitionEvent> eventMonitor) {

        Function<AcquisitionEvent, Iterator<AcquisitionEvent>> channels =
                channels(settings.channels().used());

        Function<AcquisitionEvent, Iterator<AcquisitionEvent>> cameras = cameras(cameraDeviceNames);

        Function<AcquisitionEvent, Iterator<AcquisitionEvent>> zStack =
                zStack(0, settings.volume().slicesPerView());

        ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>> acqFunctions = new ArrayList<>();
        acqFunctions.add(channels);
        acqFunctions.add(cameras);
        acqFunctions.add(zStack);
        return new AcquisitionEventIterator(baseEvent, acqFunctions, eventMonitor);
    }

    public static Iterator<AcquisitionEvent> createAcqEvents(
            AcquisitionEvent baseEvent, AcquisitionSettings settings,
            String[] cameraDeviceNames,
            Function<AcquisitionEvent, AcquisitionEvent> eventMonitor) {

        Function<AcquisitionEvent, Iterator<AcquisitionEvent>> cameras = cameras(cameraDeviceNames);

        Function<AcquisitionEvent, Iterator<AcquisitionEvent>> zStack =
                zStack(0, settings.volume().slicesPerView());

        ArrayList<Function<AcquisitionEvent, Iterator<AcquisitionEvent>>> acqFunctions = new ArrayList<>();
        acqFunctions.add(cameras);
        acqFunctions.add(zStack);
        return new AcquisitionEventIterator(baseEvent, acqFunctions, eventMonitor);
    }

    public static Function<AcquisitionEvent, Iterator<AcquisitionEvent>> cameras(String[] cameraDeviceNames) {
        return (AcquisitionEvent event) -> new Iterator<>() {

            private int cameraIndex_ = 0;
            private final String[] cameraDeviceNames_ = cameraDeviceNames;

            @Override
            public boolean hasNext() {
                return cameraIndex_ < cameraDeviceNames_.length;
            }

            @Override
            public AcquisitionEvent next() {
                AcquisitionEvent cameraEvent = event.copy();
                cameraEvent.setCameraDeviceName(cameraDeviceNames_[cameraIndex_]);
                if (isUsingMultipleCameras) {
                    cameraEvent.setAxisPosition(CAMERA_AXIS, cameraIndex_
                            + (currentChannelIndex_ * cameraDeviceNames_.length));
                } else {
                    Object position = event.getAxisPosition(CAMERA_AXIS);
                    int baseIndex = 0;

                    if (position != null) {
                        try {
                            baseIndex = Integer.parseInt(position.toString());
                        } catch (NumberFormatException e) {
                            // ignore => number already assigned
                        }
                    }

                    cameraEvent.setAxisPosition(CAMERA_AXIS, baseIndex + cameraIndex_);
                }
                cameraIndex_++;
                return cameraEvent;
            }
        };
    }

    public static Function<AcquisitionEvent, Iterator<AcquisitionEvent>> zStack(
            int startSliceIndex, int stopSliceIndex) {
        return (AcquisitionEvent event) -> new Iterator<>() {

            private int zIndex_ = startSliceIndex;

            @Override
            public boolean hasNext() {
                return zIndex_ < stopSliceIndex;
            }

            @Override
            public AcquisitionEvent next() {
                AcquisitionEvent sliceEvent = event.copy();
                sliceEvent.setAxisPosition(AcqEngMetadata.Z_AXIS, zIndex_);
                // System.out.println("Final Event Axes: " + sliceEvent.getAxesAsJSONString());
                // The tiger controller handles Z axis, so no need to add the actual Z position
                zIndex_++;
                return sliceEvent;
            }
        };
    }

    public static Function<AcquisitionEvent, Iterator<AcquisitionEvent>> timelapse(
            int numTimePoints, Double intervalMs) {
        return (AcquisitionEvent event) -> new Iterator<>() {

            int frameIndex_ = 0;

            @Override
            public boolean hasNext() {
                return frameIndex_ == 0 || frameIndex_ < numTimePoints;
            }

            @Override
            public AcquisitionEvent next() {
                AcquisitionEvent timePointEvent = event.copy();
                if (intervalMs != null) {
                    timePointEvent.setMinimumStartTime((long) (intervalMs * frameIndex_));
                }
                timePointEvent.setTimeIndex(frameIndex_);
                frameIndex_++;
                return timePointEvent;
            }
        };
    }

    /**
     * Make an iterator for events for each active channel
     *
     * @param channelList the list of channels to iterate over
     * @return
     */
    public static Function<AcquisitionEvent, Iterator<AcquisitionEvent>> channels(
            ChannelSpec[] channelList) {
        return (AcquisitionEvent event) -> new Iterator<>() {
            int index = 0;

            @Override
            public boolean hasNext() {
                return index < channelList.length;
            }

            @Override
            public AcquisitionEvent next() {
                AcquisitionEvent channelEvent = event.copy();
                channelEvent.setConfigGroup(channelList[index].getGroup());
                channelEvent.setConfigPreset(channelList[index].getName());
                channelEvent.setChannelName(Integer.toString(index));
                currentChannelIndex_ = index;

                double zPos;
                if (channelEvent.getZPosition() == null) {
                    try {
                        zPos = Engine.getCore().getPosition() + channelList[index].getOffset();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    zPos = channelEvent.getZPosition() + channelList[index].getOffset();
                }
                channelEvent.setZ(channelEvent.getZIndex(), zPos);

                // TODO: do channels have different exposures?
//               channelEvent.setExposure(channelList.get(index).exposure());
                index++;
                return channelEvent;
            }
        };
    }

    /**
     * Iterate over an arbitrary list of positions. Adds in position indices to
     * the axes that assume the order in the list provided correspond to the
     * desired indices
     *
     * @param positionList the list of positions or iterate over
     * @return
     */
    public static Function<AcquisitionEvent, Iterator<AcquisitionEvent>> positions(
            PositionList positionList) {
        return (AcquisitionEvent event) -> new Iterator<>() {
            int index = 0;

            @Override
            public boolean hasNext() {
                return index < positionList.getNumberOfPositions();
            }

            @Override
            public AcquisitionEvent next() {
                //System.out.println("called! " + index);
                AcquisitionEvent posEvent = event.copy();
                MultiStagePosition msp = positionList.getPosition(index);
                if (msp != null) {
                    posEvent.setX(msp.getX());
                    posEvent.setY(msp.getY());
                }
                posEvent.setAxisPosition(POSITION_AXIS, index);

                index++;
                return posEvent;
            }
        };
    }

//   /**
//    * Iterate over an arbitrary list of positions. Adds in position indices to
//    * the axes that assume the order in the list provided correspond to the
//    * desired indices
//    *
//    * @param positionList
//    * @return
//    */
//   public static Function<AcquisitionEvent, Iterator<AcquisitionEvent>> positions(
//           PositionList positionList) {
//      return (AcquisitionEvent event) -> {
//         Stream.Builder<AcquisitionEvent> builder = Stream.builder();
//         if (positionList == null) {
//            builder.accept(event);
//         } else {
//            for (int index = 0; index < positionList.getNumberOfPositions(); index++) {
//               AcquisitionEvent posEvent = event.copy();
//               MultiStagePosition msp = positionList.getPosition(index);
//               posEvent.setX(msp.getX());
//               posEvent.setY(msp.getY());
//               posEvent.setAxisPosition(POSITION_AXIS, index);
//               builder.accept(posEvent);
//            }
//         }
//         return builder.build().iterator();
//      };
//   }

}
