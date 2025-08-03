package android.frameworks.sensorservice;

import android.hardware.sensors.ISensors.RateLevel;

/**
 * The interface represents a direct channel created by
 * ISensorManager.createSharedMemoryDirectChannel() and
 * ISensorMangaer.createHardwareBufferDirectChannel().
 */
@VintfStability
interface IDirectReportChannel {
    /**
     * Configure direct report on channel
     *
     * Configure sensor direct report on a direct channel: set rate to value
     * other than STOP so that sensor event can be directly written into the
     * shared memory region used for creating the channel; set rate to STOP will
     * stop the sensor direct report.
     *
     * To stop all active sensor direct report configured to a channel, set
     * sensorHandle to -1 and rate to STOP.
     *
     * @param  sensorHandle handle of the sensor to operate on. If it is -1
     *                      and rate is STOP, the call must stop of all active
     *                      sensor direct report.
     * @param  rate         rate level value to set on the specified sensor. Values
     *                      are defined in android.hardware.sensors.ISensors.RateLevel.
     *
     * @return out token   the token used to distinguish sensor events from
     *                     multiple different sensors of the same type in a
     *                     single direct channel, or 0 if: (1) no such token
     *                     may be returned or (2) error (in which case result
     *                     must be value other than OK).
     *
     * @throws ServiceSpecificException with the following ISensorManager::RESULT_* values
     *                     BAD_VALUE if parameter is invalid (for example,
     *                     rate level is not supported by sensor, etc);
     *                     INVALID_OPERATION if functionality is not supported.
     */
    int configure(in int sensorHandle, in RateLevel rate);
}
