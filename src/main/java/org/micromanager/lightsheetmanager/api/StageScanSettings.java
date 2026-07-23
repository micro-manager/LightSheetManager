package org.micromanager.lightsheetmanager.api;

/**
 * Settings for ASI stage scanning operations.
 * <p>
 * This object defines settings for the {@code SCAN MODULE} firmware module.
 * <p>
 * <a href="https://asiimaging.com/docs/scan_module">ASI Scan Module Documentation</a>
 */
public interface StageScanSettings {

    /**
     * Returns a builder initialized with the current settings.
     *
     * @return a builder to create a modified copy of these settings
     */
    Builder copyBuilder();

    /**
     * Returns true if stage scanning is enabled.
     *
     * @return true if stage scanning is enabled
     */
    boolean enabled();

    /**
     * Returns the acceleration factor.
     *
     * @return the acceleration factor
     */
    double accelerationFactor();

    /**
     * Returns the overshoot distance.
     *
     * @return the overshoot distance in microns
     */
    int overshootDistance();

    /**
     * Returns stage speed during the retrace move as a percentage of the max speed.
     *
     * @return the speed of the retrace move as a percentage of the max speed
     */
    double retraceSpeed();

    /**
     * Returns the scan angle in degrees.
     *
     * @return the scan angle in degrees
     */
    double firstViewAngle();

    /**
     * Returns true if the scan should return to the starting position.
     *
     * @return true if the scan should return to the starting position
     */
    boolean returnToStart();

    /**
     * Returns true if the scan should start from the current position.
     *
     * @return true if the scan should start from the current position
     */
    boolean fromCurrentPosition();

    /**
     * Returns true if the scan should be negative.
     *
     * @return true if the scan should be negative
     */
    boolean fromNegativeDirection();

    interface Builder {

        /**
         * Enable or disable stage scanning.
         *
         * @param state true to enable stage scanning
         * @return {@code this} builder
         */
        Builder enabled(final boolean state);

        /**
         * Sets the acceleration factor.
         * <p>
         * The default value is 1.0.
         *
         * @param factor the acceleration factor
         * @return {@code this} builder
         */
        Builder accelerationFactor(final double factor);

        /**
         * Sets the overshoot distance.
         * <p>
         * The default value is 0.
         *
         * @param distance the overshoot distance
         * @return {@code this} builder
         */
        Builder overshootDistance(final int distance);

        /**
         * Sets the retrace speed as a percentage of the maximum stage speed.
         * <p>
         * The default value is 67%.
         *
         * @param speed the retrace speed (1-100%)
         * @return {@code this} builder
         */
        Builder retraceSpeed(final double speed);

        /**
         * Sets the scan angle of the first view.
         * <p>
         * The default value is 45 degrees.
         *
         * @param angle the angle in degrees
         * @return {@code this} builder
         */
        Builder firstViewAngle(final double angle);

        /**
         * Sets whether the stage returns to the start position after scanning.
         * <p>
         * The default value is false, the stage does not return to the original position.
         *
         * @param state true to return to start
         * @return {@code this} builder
         */
        Builder returnToStart(final boolean state);

        /**
         * Sets whether the scan starts from the current stage position.
         * <p>
         * The default value is false.
         *
         * @param state true to start from current position
         * @return {@code this} builder
         */
        Builder fromCurrentPosition(final boolean state);

        /**
         * Sets whether the scan direction is negative or positive.
         *
         * @param state true for negative direction, false for positive
         * @return {@code this} builder
         */
        Builder fromNegativeDirection(final boolean state);

        /**
         * Creates a new {@link StageScanSettings} instance based on the current configuration.
         *
         * @return a new immutable settings instance
         */
        StageScanSettings build();
    }

}
