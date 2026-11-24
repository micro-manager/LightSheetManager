package org.micromanager.lightsheetmanager.api;

import org.micromanager.acqj.main.Acquisition;

import java.util.concurrent.Future;

public interface AcquisitionManager {

    /**
     * Return the acquisition currently being set up or running.
     *
     * @return the current acquisition, or null. Null until the run constructs it, after
     *         setup has finished, and again once the run finishes.
     */
    Acquisition current();

    /**
     * Request that an acquisition is run.
     *
     * <p>The future completes normally however the run ends: completed, stopped, failed with
     * an exception, or refused before it started. It tells when the request is over, not how
     * it went, and waiting on it blocks until then.
     *
     * @return a future that completes when the request is over
     */
    Future<?> requestRun();

    /**
     * Request that Micro-Manager's speed test is run, which streams the camera to the save
     * directory.
     *
     * <p>It is not an acquisition, so a stop request does not end it. The future completes
     * normally however it ends.
     *
     * @return a future that completes when the request is over
     */
    Future<?> requestSpeedTest();

    /**
     * Request that a test acquisition is run.
     *
     * <p>A test acquisition runs a single time point and does not save anything to disk. The
     * settings shown in the user interface are left unchanged.
     *
     * <p>The future completes normally however the run ends: completed, stopped, failed with
     * an exception, or refused before it started. It tells when the request is over, not how
     * it went, and waiting on it blocks until then.
     *
     * @return a future that completes when the request is over
     */
    Future<?> requestTestAcquisition();

    /**
     * Request the running acquisition to stop.
     */
    void requestStop();

    /**
     * Request the running acquisition to pause.
     */
    void requestPause();

    /**
     * Resume acquisition after it was paused
     */
    void requestResume();

}
