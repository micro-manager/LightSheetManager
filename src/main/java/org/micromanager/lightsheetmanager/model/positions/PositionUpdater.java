package org.micromanager.lightsheetmanager.model.positions;

import mmcorej.DeviceType;
import org.micromanager.lightsheetmanager.LightSheetManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class PositionUpdater implements Publisher {

   // each poll reads every stage and galvo over serial, so do not poll faster than this
   private static final int MIN_POLLING_DELAY_MS = 500;

   // polling
   private volatile int pollingDelayMs_;
   private volatile boolean isPolling_;
   // one thread and one task, from the first start until shutdown(): starting and
   // stopping only set the flag above, so a second polling loop can never exist
   private final ScheduledExecutorService executor_;
   private final AtomicBoolean isTicking_; // true once the task is scheduled

   // data
   private final HashMap<String, Object> positions_;
   private final HashMap<String, ArrayList<Subscriber>> topics_;

   private final LightSheetManager model_;

   public PositionUpdater(final LightSheetManager model) {
       model_ = Objects.requireNonNull(model);
       positions_ = new HashMap<>();
       topics_ = new HashMap<>();
       pollingDelayMs_ = 500;
       isTicking_ = new AtomicBoolean(false);
       executor_ = Executors.newSingleThreadScheduledExecutor(runnable -> {
          final Thread thread = new Thread(runnable, "LSM position polling");
          thread.setDaemon(true); // must not keep Micro-Manager from exiting
          return thread;
       });
   }

   // call this after the devices are found
   public void setup() {
      final String[] devices = model_.devices().adapter().positionDevices();
      for (String device : devices) {
         positions_.put(device, 0.0); // TODO: how to init?
         topics_.put(device, new ArrayList<>());
      }
   }

   private void tick() {
      try {
         if (isPolling_) {
            updatePositions();
            updateSubscribers();
         }
      } catch (RuntimeException e) {
         model_.studio().logs().logError(e, "Position polling failed");
      } finally {
         // reschedule even after an error, so polling cannot stop while isPolling() is true
         if (!executor_.isShutdown()) {
            executor_.schedule(this::tick, pollingDelayMs_, TimeUnit.MILLISECONDS);
         }
      }
   }

   public void startPolling() {
      isPolling_ = true;
      // the thread starts on first use: close() is skipped when the plugin fails to load,
      // so a thread started any earlier could never be shut down
      if (isTicking_.compareAndSet(false, true)) {
         executor_.schedule(this::tick, 0, TimeUnit.MILLISECONDS);
      }
   }

   public void stopPolling() {
      isPolling_ = false;
   }

   public boolean isPolling() {
      return isPolling_;
   }

   /**
    * Stops polling and ends the polling thread. Call when the plugin closes.
    */
   public void shutdown() {
      isPolling_ = false;
      executor_.shutdown();
   }

   public void setPollingDelayMs(final int delayMs) {
      if (delayMs < MIN_POLLING_DELAY_MS) {
         throw new IllegalArgumentException("polling delay must be at least "
               + MIN_POLLING_DELAY_MS + " ms, got " + delayMs);
      }
      pollingDelayMs_ = delayMs;
   }

   public int getPollingDelayMs() {
      return pollingDelayMs_;
   }

   @Override
   public void register(Subscriber subscriber, String topic) {
      topics_.get(topic).add(subscriber);
   }

   /**
    * Update the position map.
    */
   public void updatePositions() {
      for (String device : positions_.keySet()) {
         final String deviceName = model_.devices().device(device).getDeviceName();
         final DeviceType deviceType = model_.devices().device(device).getDeviceType();
         try {
            if (deviceType == DeviceType.XYStageDevice) {
               positions_.put(device, model_.core().getXYStagePosition(deviceName));
            } else if (deviceType == DeviceType.StageDevice) {
               positions_.put(device, model_.core().getPosition(deviceName));
            } else if (deviceType == DeviceType.GalvoDevice) {
               positions_.put(device, model_.core().getGalvoPosition(deviceName));
            }
         } catch (Exception e) {
            model_.studio().logs().logError("Error in updatePositions()");
         }
      }
   }

   /**
    * Send messages to all subscribers.
    */
   private void updateSubscribers() {
      for (String topic : topics_.keySet()) {
         for (Subscriber sub : topics_.get(topic)) {
            sub.update(topic, positions_.get(topic));
         }
      }
   }

}
