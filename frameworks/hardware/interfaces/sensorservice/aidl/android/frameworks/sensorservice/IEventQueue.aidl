package android.frameworks.sensorservice;

/**
 * An IEventQueue is an interface to manage an event queue created by
 * ISensorManager.
 */
@VintfStability
interface IEventQueue {
    /**
     * Disable the selected sensor.
     *
     * @param  sensorHandle the sensor to disable. Must be a sensor acquired from
     *                      the ISensorManager that creates this IEventQueue.
     * @throws ServiceSpecificException with the following ISensorManager::RESULT_* values
     *                     BAD_VALUE if parameter is invalid (for example,
     *                     rate level is not supported by sensor, etc);
     *                     INVALID_OPERATION if functionality is not supported.
     */
    void disableSensor(in int sensorHandle);

    /**
     * Enable the selected sensor with a specified sampling period and
     * max batch report latency. If enableSensor is called multiple times on the
     * same sensor, the previous calls must be overridden by the last call.
     *
     * @param  sensorHandle the sensor to enable. Must be a sensor acquired from
     *                      the ISensorManager that creates this IEventQueue.
     * @param  samplingPeriodUs
     *                      sampling period in microseconds.
     * @param  maxBatchReportLatencyUs
     *                      max batch report latency in microseconds.
     * @throws ServiceSpecificException with the following ISensorManager::RESULT_* values
     *                     BAD_VALUE if parameter is invalid (for example,
     *                     rate level is not supported by sensor, etc);
     *                     INVALID_OPERATION if functionality is not supported.
     *                     PERMISSION_DENIED if permissions is denied.
     */
    void enableSensor(in int sensorHandle, in int samplingPeriodUs,
        in long maxBatchReportLatencyUs);
}
