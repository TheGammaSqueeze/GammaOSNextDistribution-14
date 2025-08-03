package android.frameworks.sensorservice;

import android.frameworks.sensorservice.IDirectReportChannel;
import android.frameworks.sensorservice.IEventQueue;
import android.frameworks.sensorservice.IEventQueueCallback;
import android.hardware.common.Ashmem;
import android.hardware.sensors.SensorInfo;
import android.hardware.sensors.SensorType;

/**
 * ISensorManager is an interface to manage sensors
 *
 * This file provides a set of functions that uses
 * ISensorManager to access and list hardware sensors.
 */
@VintfStability
interface ISensorManager {
    const int RESULT_NOT_EXIST =1;
    const int RESULT_NO_MEMORY = 2;
    const int RESULT_NO_INIT = 3;
    const int RESULT_PERMISSION_DENIED = 4;
    const int RESULT_BAD_VALUE = 5;
    const int RESULT_INVALID_OPERATION = 6;
    const int RESULT_UNKNOWN_ERROR = 7;

    /**
     * Create direct channel based on shared memory
     *
     * Create a direct channel of DIRECT_CHANNEL_ASHMEM type to be used
     * for configuring sensor direct report.
     *
     * The memory layout looks as follows. These offsets can be found in
     * android.hardware.sensors.ISensors::DIRECT_REPORT_SENSOR_EVENT_OFFSET_*.
     *   offset   type        name
     *  -----------------------------------
     *   0x0000  int32_t     size (SensorsEventFormatOffset::TOTAL_LENGTH)
     *   0x0004  int32_t     sensor report token
     *   0x0008  int32_t     type (see android.hardware.sensors::SensorType)
     *   0x000C  uint32_t    atomic counter
     *   0x0010  int64_t     timestamp (see android.hardware.sensors::Event)
     *   0x0018  float[16]/  data
     *           int64_t[8]
     *   0x0058  int32_t[4]  reserved (set to zero)
     *
     * @param mem     the shared memory to use, must be ashmem.
     * @param size    the intended size to be used. The following must be true:
     *                SensorsEventFormatOffset::TOTAL_LENGTH <= size <= mem.size
     *
     * @return        The created channel, or NULL if failure.
     * @throws ServiceSpecificException with the following ISensorManager::RESULT_* values
     *                RESULT_BAD_VALUE if size > mem.size();
     *                BAD_VALUE if size < TOTAL_LENGTH;
     *                NO_MEMORY, NO_INIT, BAD_VALUE for underlying errors;
     *                UNKNOWN_ERROR if the underlying error is not recognized;
     *                UNKNOWN_ERROR if the underlying call returns channelId = 0
     */
    IDirectReportChannel createAshmemDirectChannel(in Ashmem mem, in long size);

    /**
     * Create a sensor event queue.
     *
     * Create a sensor event queue with an IEventQueueCallback object.
     * Subsequently, one can enable sensors on the event queue so that sensor
     * events are passed via the specified callback.
     *
     * @param  callback the callback to call on events. Must not be null.
     * @return out queue    the event queue created. null on failure.
     * @throws ServiceSpecificException with the following ISensorManager::RESULT_* values
     *                  BAD_VALUE if callback is null,
     *                  or other Result values for any underlying errors.
     */
    IEventQueue createEventQueue(in IEventQueueCallback callback);

    /**
     * Create direct channel based on hardware buffer
     *
     * Create a direct channel of DIRECT_CHANNEL_GRALLOC type to be used
     * for configuring sensor direct report.
     *
     * @param buffer  file descriptor describing the gralloc buffer.
     * @param size    the intended size to be used, must be less than or equal
     *                to the size of the buffer.
     *
     * @return        The created channel, or NULL if failure.
     * @throws ServiceSpecificException with the following ISensorManager::RESULT_* values
     *                NO_MEMORY, NO_INIT, BAD_VALUE for underlying errors;
     *                UNKNOWN_ERROR if the underlying error is not recognized;
     *                UNKNOWN_ERROR if the underlying call returns channelId = 0
     */
    IDirectReportChannel createGrallocDirectChannel(in ParcelFileDescriptor buffer, in long size);

    /**
     * Get the default sensor of the specified type.
     *
     * @param type the type of default sensor to get
     * @return the default sensor for the given type, or undetermined
     *                value on failure.
     * @throws ServiceSpecificException with the following ISensorManager::RESULT_* values
     *                NOT_EXIST if no sensor of that type exists.
     */
    SensorInfo getDefaultSensor(in SensorType type);

    /**
     * Get the list of available sensors.
     *
     * @return the list of available sensors, or empty on failure
     * @throws ServiceSpecificException with the following ISensorManager::RESULT_* values
     *                UNKNOWN_ERROR on failure
     */
    SensorInfo[] getSensorList();
}
