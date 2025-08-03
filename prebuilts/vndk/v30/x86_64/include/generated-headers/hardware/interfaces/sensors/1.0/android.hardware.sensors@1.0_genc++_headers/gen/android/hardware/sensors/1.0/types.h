#ifndef HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace sensors {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class Result : int32_t;
enum class OperationMode : int32_t;
enum class SensorType : int32_t;
enum class SensorFlagBits : uint32_t;
enum class SensorFlagShift : uint8_t;
struct SensorInfo;
enum class SensorStatus : int8_t;
struct Vec3;
struct Vec4;
struct Uncal;
struct HeartRate;
enum class MetaDataEventType : uint32_t;
struct MetaData;
struct DynamicSensorInfo;
enum class AdditionalInfoType : uint32_t;
struct AdditionalInfo;
union EventPayload;
struct Event;
enum class RateLevel : int32_t;
enum class SharedMemType : int32_t;
enum class SharedMemFormat : int32_t;
enum class SensorsEventFormatOffset : uint16_t;
struct SharedMemInfo;

/**
 * Please see the Sensors section of source.android.com for an
 * introduction to and detailed descriptions of Android sensor types:
 * http://source.android.com/devices/sensors/index.html
 *
 *
 * Type enumerating various result codes returned from ISensors methods
 */
enum class Result : int32_t {
    OK = 0,
    PERMISSION_DENIED = -1 /* -1 */,
    NO_MEMORY = -12 /* -12 */,
    BAD_VALUE = -22 /* -22 */,
    INVALID_OPERATION = -38 /* -38 */,
};

/**
 * Sensor HAL modes used in setOperationMode method
 */
enum class OperationMode : int32_t {
    NORMAL = 0,
    DATA_INJECTION = 1,
};

/**
 * Sensor type
 *
 * Each sensor has a type which defines what this sensor measures and how
 * measures are reported. See the Base sensors and Composite sensors lists
 * for complete descriptions:
 * http://source.android.com/devices/sensors/base_triggers.html
 * http://source.android.com/devices/sensors/composite_sensors.html
 *
 * Device manufacturers (OEMs) can define their own sensor types, for
 * their private use by applications or services provided by them. Such
 * sensor types are specific to an OEM and can't be exposed in the SDK.
 * These types must start at SensorType::DEVICE_PRIVATE_BASE.
 *
 * All sensors defined outside of the device private range must correspond to
 * a type defined in this file, and must satisfy the characteristics listed in
 * the description of the sensor type.
 *
 * Each sensor also has a "typeAsString".
 *  - string type of sensors defined in this file is overridden by Android to
 *    values defined in Android API with "android.sensor." prefix.
 *    Example: for an accelerometer,
 *      type = SensorType::Acclerometer
 *      typeAsString = "" (will be replace by "android.sensor.accelerometer" by
 *                         Android frameowrk)
 *  - string type of sensors inside of the device private range MUST be prefixed
 *    by the sensor provider's or OEM reverse domain name. In particular, they
 *    cannot use the "android.sensor." prefix.
 *
 * When android introduces a new sensor type that can replace an OEM-defined
 * sensor type, the OEM must use the official sensor type and stringType on
 * versions of the HAL that support this new official sensor type.
 *
 * Example (made up): Suppose Google's Glass team wants to surface a sensor
 * detecting that Glass is on a head.
 *  - Such a sensor is not officially supported in android KitKat
 *  - Glass devices launching on KitKat can implement a sensor with
 *      type = 0x10001
 *      typeAsString = "com.google.glass.onheaddetector"
 *  - In L android release, if android decides to define
 *    SensorType::ON_HEAD_DETECTOR and STRING_SensorType::ON_HEAD_DETECTOR,
 *    those types should replace the Glass-team-specific types in all future
 *    launches.
 *  - When launching Glass on the L release, Google should now use the official
 *    type (SensorType::ON_HEAD_DETECTOR) and stringType.
 *  - This way, all applications can now use this sensor.
 *
 *
 * Wake up sensors.
 * Each sensor may have either or both a wake-up and a non-wake variant.
 * When registered in batch mode, wake-up sensors will wake up the AP when
 * their FIFOs are full or when the batch timeout expires. A separate FIFO has
 * to be maintained for wake up sensors and non wake up sensors. The non
 * wake-up sensors need to overwrite their FIFOs when they are full till the AP
 * wakes up and the wake-up sensors will wake-up the AP when their FIFOs are
 * full or when the batch timeout expires without losing events.
 * Wake-up and non wake-up variants of each sensor can be activated at
 * different rates independently of each other.
 *
 * Note: Proximity sensor and significant motion sensor which were defined in
 * previous releases are also wake-up sensors and must be treated as such.
 * Wake-up one-shot sensors like SIGNIFICANT_MOTION cannot be batched, hence
 * the text about batch above doesn't apply to them. See the definitions of
 * SensorType::PROXIMITY and SensorType::SIGNIFICANT_MOTION for more info.
 *
 * Set SENSOR_FLAG_WAKE_UP flag for all wake-up sensors.
 *
 * For example, A device can have two sensors both of SensorType::ACCELEROMETER
 * and one of them can be a wake_up sensor (with SENSOR_FLAG_WAKE_UP flag set)
 * and the other can be a regular non wake_up sensor. Both of these sensors
 * must be activated/deactivated independently of the other.
 */
enum class SensorType : int32_t {
    /**
     * META_DATA is a special event type used to populate the MetaData
     * structure. It doesn't correspond to a physical sensor. Events of this
     * type exist only inside the HAL, their primary purpose is to signal the
     * completion of a flush request.
     */
    META_DATA = 0,
    /**
     * ACCELEROMETER
     * reporting-mode: continuous
     *
     * All values are in SI units (m/s^2) and measure the acceleration of the
     * device minus the acceleration due to gravity.
     *
     * Implement the non-wake-up version of this sensor and implement the
     * wake-up version if the system possesses a wake up fifo.
     */
    ACCELEROMETER = 1,
    /**
     * MAGNETIC_FIELD
     * reporting-mode: continuous
     *
     * All values are in micro-Tesla (uT) and measure the geomagnetic
     * field in the X, Y and Z axis.
     *
     * Implement the non-wake-up version of this sensor and implement the
     * wake-up version if the system possesses a wake up fifo.
     */
    MAGNETIC_FIELD = 2,
    /**
     * ORIENTATION
     * reporting-mode: continuous
     *
     * All values are angles in degrees.
     *
     * Orientation sensors return sensor events for all 3 axes at a constant
     * rate defined by setDelay().
     *
     * Implement the non-wake-up version of this sensor and implement the
     * wake-up version if the system possesses a wake up fifo.
     */
    ORIENTATION = 3,
    /**
     * GYROSCOPE
     * reporting-mode: continuous
     *
     * All values are in radians/second and measure the rate of rotation
     * around the X, Y and Z axis.
     *
     * Implement the non-wake-up version of this sensor and implement the
     * wake-up version if the system possesses a wake up fifo.
     */
    GYROSCOPE = 4,
    /**
     * LIGHT
     * reporting-mode: on-change
     *
     * The light sensor value is returned in SI lux units.
     *
     * Both wake-up and non wake-up versions are useful.
     */
    LIGHT = 5,
    /**
     * PRESSURE
     * reporting-mode: continuous
     *
     * The pressure sensor return the athmospheric pressure in hectopascal (hPa)
     *
     * Implement the non-wake-up version of this sensor and implement the
     * wake-up version if the system possesses a wake up fifo.
     */
    PRESSURE = 6,
    /**
     * TEMPERATURE is deprecated in the HAL
     */
    TEMPERATURE = 7,
    /**
     * PROXIMITY
     * reporting-mode: on-change
     *
     * The proximity sensor which turns the screen off and back on during calls
     * is the wake-up proximity sensor. Implement wake-up proximity sensor
     * before implementing a non wake-up proximity sensor. For the wake-up
     * proximity sensor set the flag SENSOR_FLAG_WAKE_UP.
     * The value corresponds to the distance to the nearest object in
     * centimeters.
     */
    PROXIMITY = 8,
    /**
     * GRAVITY
     * reporting-mode: continuous
     *
     * A gravity output indicates the direction of and magnitude of gravity in
     * the devices's coordinates.
     *
     * Implement the non-wake-up version of this sensor and implement the
     * wake-up version if the system possesses a wake up fifo.
     */
    GRAVITY = 9,
    /**
     * LINEAR_ACCELERATION
     * reporting-mode: continuous
     *
     * Indicates the linear acceleration of the device in device coordinates,
     * not including gravity.
     *
     * Implement the non-wake-up version of this sensor and implement the
     * wake-up version if the system possesses a wake up fifo.
     */
    LINEAR_ACCELERATION = 10,
    /**
     * ROTATION_VECTOR
     * reporting-mode: continuous
     *
     * The rotation vector symbolizes the orientation of the device relative to
     * the East-North-Up coordinates frame.
     *
     * Implement the non-wake-up version of this sensor and implement the
     * wake-up version if the system possesses a wake up fifo.
     */
    ROTATION_VECTOR = 11,
    /**
     * RELATIVE_HUMIDITY
     * reporting-mode: on-change
     *
     * A relative humidity sensor measures relative ambient air humidity and
     * returns a value in percent.
     *
     * Both wake-up and non wake-up versions are useful.
     */
    RELATIVE_HUMIDITY = 12,
    /**
     * AMBIENT_TEMPERATURE
     * reporting-mode: on-change
     *
     * The ambient (room) temperature in degree Celsius.
     *
     * Both wake-up and non wake-up versions are useful.
     */
    AMBIENT_TEMPERATURE = 13,
    /**
     * MAGNETIC_FIELD_UNCALIBRATED
     * reporting-mode: continuous
     *
     * Similar to MAGNETIC_FIELD, but the hard iron calibration is
     * reported separately instead of being included in the measurement.
     *
     * Implement the non-wake-up version of this sensor and implement the
     * wake-up version if the system possesses a wake up fifo.
     */
    MAGNETIC_FIELD_UNCALIBRATED = 14,
    /**
     * GAME_ROTATION_VECTOR
     * reporting-mode: continuous
     *
     * Similar to ROTATION_VECTOR, but not using the geomagnetic
     * field.
     *
     * Implement the non-wake-up version of this sensor and implement the
     * wake-up version if the system possesses a wake up fifo.
     */
    GAME_ROTATION_VECTOR = 15,
    /**
     * GYROSCOPE_UNCALIBRATED
     * reporting-mode: continuous
     *
     * All values are in radians/second and measure the rate of rotation
     * around the X, Y and Z axis.
     *
     * Implement the non-wake-up version of this sensor and implement the
     * wake-up version if the system possesses a wake up fifo.
     */
    GYROSCOPE_UNCALIBRATED = 16,
    /**
     * SIGNIFICANT_MOTION
     * reporting-mode: one-shot
     *
     * A sensor of this type triggers an event each time significant motion
     * is detected and automatically disables itself.
     * For Significant Motion sensor to be useful, it must be defined as a
     * wake-up sensor. (set SENSOR_FLAG_WAKE_UP). Implement the wake-up
     * significant motion sensor. A non wake-up version is not useful.
     * The only allowed value to return is 1.0.
     */
    SIGNIFICANT_MOTION = 17,
    /**
     * STEP_DETECTOR
     * reporting-mode: special
     *
     * A sensor of this type triggers an event each time a step is taken
     * by the user. The only allowed value to return is 1.0 and an event
     * is generated for each step.
     *
     * Both wake-up and non wake-up versions are useful.
     */
    STEP_DETECTOR = 18,
    /**
     * STEP_COUNTER
     * reporting-mode: on-change
     *
     * A sensor of this type returns the number of steps taken by the user since
     * the last reboot while activated. The value is returned as a uint64_t and
     * is reset to zero only on a system / android reboot.
     *
     * Implement the non-wake-up version of this sensor and implement the
     * wake-up version if the system possesses a wake up fifo.
     */
    STEP_COUNTER = 19,
    /**
     * GEOMAGNETIC_ROTATION_VECTOR
     * reporting-mode: continuous
     *
     *  Similar to ROTATION_VECTOR, but using a magnetometer instead
     *  of using a gyroscope.
     *
     * Implement the non-wake-up version of this sensor and implement the
     * wake-up version if the system possesses a wake up fifo.
     */
    GEOMAGNETIC_ROTATION_VECTOR = 20,
    /**
     * HEART_RATE
     * reporting-mode: on-change
     *
     *  A sensor of this type returns the current heart rate.
     *  The events contain the current heart rate in beats per minute (BPM) and
     *  the status of the sensor during the measurement. See "HeartRate" below
     *  for more details.
     *
     *  Because this sensor is on-change, events must be generated when and only
     *  when heart_rate.bpm or heart_rate.status have changed since the last
     *  event. In particular, upon the first activation, unless the device is
     *  known to not be on the body, the status field of the first event must be
     *  set to SensorStatus::UNRELIABLE. The event should be generated no faster
     *  than every period_ns passed to setDelay() or to batch().
     *  See the definition of the on-change reporting mode for more information.
     *
     *  SensorInfo.requiredPermission must be set to
     *  SENSOR_PERMISSION_BODY_SENSORS.
     *
     *  Both wake-up and non wake-up versions are useful.
     */
    HEART_RATE = 21,
    /**
     * WAKE_UP_TILT_DETECTOR
     * reporting-mode: special (setDelay has no impact)
     *
     * A sensor of this type generates an event each time a tilt event is
     * detected. A tilt event must be generated if the direction of the
     * 2-seconds window average gravity changed by at least 35 degrees since the
     * activation or the last trigger of the sensor.
     *
     *  reference_estimated_gravity = average of accelerometer measurements over
     *  the first 1 second after activation or the estimated gravity at the last
     *  trigger.
     *
     *  current_estimated_gravity = average of accelerometer measurements over
     *  the last 2 seconds.
     *
     *  trigger when
     *     angle(reference_estimated_gravity, current_estimated_gravity)
     *       > 35 degrees
     *
     * Large accelerations without a change in phone orientation must not
     * trigger a tilt event.
     * For example, a sharp turn or strong acceleration while driving a car
     * must not trigger a tilt event, even though the angle of the average
     * acceleration might vary by more than 35 degrees.
     *
     * Typically, this sensor is implemented with the help of only an
     * accelerometer. Other sensors can be used as well if they do not increase
     * the power consumption significantly. This is a low power sensor that
     * must allow the AP to go into suspend mode. Do not emulate this sensor
     * in the HAL.
     * Like other wake up sensors, the driver is expected to a hold a wake_lock
     * with a timeout of 200 ms while reporting this event. The only allowed
     * return value is 1.0.
     *
     * Implement only the wake-up version of this sensor.
     */
    TILT_DETECTOR = 22,
    /**
     * WAKE_GESTURE
     * reporting-mode: one-shot
     *
     * A sensor enabling waking up the device based on a device specific motion.
     *
     * When this sensor triggers, the device behaves as if the power button was
     * pressed, turning the screen on. This behavior (turning on the screen when
     * this sensor triggers) might be deactivated by the user in the device
     * settings. Changes in settings do not impact the behavior of the sensor:
     * only whether the framework turns the screen on when it triggers.
     *
     * The actual gesture to be detected is not specified, and can be chosen by
     * the manufacturer of the device.
     * This sensor must be low power, as it is likely to be activated 24/7.
     * The only allowed value to return is 1.0.
     *
     * Implement only the wake-up version of this sensor.
     */
    WAKE_GESTURE = 23,
    /**
     * GLANCE_GESTURE
     * reporting-mode: one-shot
     *
     * A sensor enabling briefly turning the screen on to enable the user to
     * glance content on screen based on a specific motion.  The device must
     * turn the screen off after a few moments.
     *
     * When this sensor triggers, the device turns the screen on momentarily
     * to allow the user to glance notifications or other content while the
     * device remains locked in a non-interactive state (dozing). This behavior
     * (briefly turning on the screen when this sensor triggers) might be
     * deactivated by the user in the device settings.
     * Changes in settings do not impact the behavior of the sensor: only
     * whether the framework briefly turns the screen on when it triggers.
     *
     * The actual gesture to be detected is not specified, and can be chosen by
     * the manufacturer of the device.
     * This sensor must be low power, as it is likely to be activated 24/7.
     * The only allowed value to return is 1.0.
     *
     * Implement only the wake-up version of this sensor.
     */
    GLANCE_GESTURE = 24,
    /**
     * PICK_UP_GESTURE
     * reporting-mode: one-shot
     *
     * A sensor of this type triggers when the device is picked up regardless of
     * wherever is was before (desk, pocket, bag). The only allowed return value
     * is 1.0. This sensor de-activates itself immediately after it triggers.
     *
     * Implement only the wake-up version of this sensor.
     */
    PICK_UP_GESTURE = 25,
    /**
     * WRIST_TILT_GESTURE
     * trigger-mode: special
     * wake-up sensor: yes
     *
     * A sensor of this type triggers an event each time a tilt of the
     * wrist-worn device is detected.
     *
     * This sensor must be low power, as it is likely to be activated 24/7.
     * The only allowed value to return is 1.0.
     *
     * Implement only the wake-up version of this sensor.
     */
    WRIST_TILT_GESTURE = 26,
    /**
     * DEVICE_ORIENTATION
     * reporting-mode: on-change
     *
     * The current orientation of the device. The value is reported in
     * the "scalar" element of the EventPayload in Event. The
     * only values that can be reported are (please refer to Android Sensor
     * Coordinate System to understand the X and Y axis direction with respect
     * to default orientation):
     *  - 0: device is in default orientation (Y axis is vertical and points up)
     *  - 1: device is rotated 90 degrees counter-clockwise from default
     *       orientation (X axis is vertical and points up)
     *  - 2: device is rotated 180 degrees from default orientation (Y axis is
     *       vertical and points down)
     *  - 3: device is rotated 90 degrees clockwise from default orientation
     *       (X axis is vertical and points down)
     *
     * Moving the device to an orientation where the Z axis is vertical (either
     * up or down) must not cause a new event to be reported.
     *
     * To improve the user experience of this sensor, it is recommended to
     * implement some physical (i.e., rotation angle) and temporal (i.e., delay)
     * hysteresis. In other words, minor or transient rotations must not cause
     * a new event to be reported.
     *
     * This is a low power sensor that intended to reduce interrupts of
     * application processor and thus allow it to go sleep. Use hardware
     * implementation based on low power consumption sensors, such as
     * accelerometer. Device must not emulate this sensor in the HAL.
     *
     * Both wake-up and non wake-up versions are useful.
     */
    DEVICE_ORIENTATION = 27,
    /**
     * POSE_6DOF
     * trigger-mode: continuous
     *
     * A sensor of this type returns the pose of the device.
     * Pose of the device is defined as the orientation of the device from a
     * Earth Centered Earth Fixed frame and the translation from an arbitrary
     * point at subscription.
     *
     * This sensor can be high power. It can use any and all of the following
     *           . Accelerometer
     *           . Gyroscope
     *           . Camera
     *           . Depth Camera
     *
     */
    POSE_6DOF = 28,
    /**
     * STATIONARY_DETECT
     * trigger mode: one shot
     *
     * A sensor of this type returns an event if the device is still/stationary
     * for a while. The period of time to monitor for stationarity must be
     * greater than 5 seconds. The latency must be less than 10 seconds.
     *
     * Stationarity here refers to absolute stationarity. eg: device on desk.
     *
     * The only allowed value to return is 1.0.
     */
    STATIONARY_DETECT = 29,
    /**
     * MOTION_DETECT
     * trigger mode: one shot
     *
     * A sensor of this type returns an event if the device is not still for
     * for a while. The period of time to monitor for stationarity must be
     * greater than 5 seconds. The latency must be less than 10 seconds.
     *
     * Motion here refers to any mechanism in which the device is causes to be
     * moved in its inertial frame. eg: Pickin up the device and walking with it
     * to a nearby room may trigger motion wherewas keeping the device on a
     * table on a smooth train moving at constant velocity may not trigger
     * motion.
     *
     * The only allowed value to return is 1.0.
     */
    MOTION_DETECT = 30,
    /**
     * HEART_BEAT
     * trigger mode: continuous
     *
     * A sensor of this type returns an event everytime a hear beat peak is
     * detected.
     *
     * Peak here ideally corresponds to the positive peak in the QRS complex of
     * and ECG signal.
     *
     * The sensor is not expected to be optimized for latency. As a guide, a
     * latency of up to 10 seconds is acceptable. However, the timestamp attached
     * to the event must be accuratly correspond to the time the peak occured.
     *
     * The sensor event contains a parameter for the confidence in the detection
     * of the peak where 0.0 represent no information at all, and 1.0 represents
     * certainty.
     */
    HEART_BEAT = 31,
    /**
     * DYNAMIC_SENSOR_META
     * trigger-mode: special
     * wake-up sensor: yes
     *
     * A sensor event of this type is received when a dynamic sensor is added to
     * or removed from the system. At most one sensor of this type can be
     * present in one sensor HAL implementation and presence of a sensor of this
     * type in sensor HAL implementation indicates that this sensor HAL supports
     * dynamic sensor feature. Operations, such as batch, activate and setDelay,
     * to this special purpose sensor must be treated as no-op and return
     * successful; flush() also has to generate flush complete event as if this
     * is a sensor that does not support batching.
     *
     * A dynamic sensor connection indicates connection of a physical device or
     * instantiation of a virtual sensor backed by algorithm; and a dynamic
     * sensor disconnection indicates the the opposite. A sensor event of
     * DYNAMIC_SENSOR_META type should be delivered regardless of
     * the activation status of the sensor in the event of dynamic sensor
     * connection and disconnection. In the sensor event, besides the common
     * data entries, "dynamic_sensor_meta", which includes fields for connection
     * status, handle of the sensor involved, pointer to sensor_t structure and
     * a uuid field, must be populated.
     *
     * At a dynamic sensor connection event, fields of sensor_t structure
     * referenced by a pointer in dynamic_sensor_meta must be filled as if it
     * was regular sensors. Sensor HAL is responsible for recovery of memory if
     * the corresponding data is dynamicially allocated. However, the the
     * pointer must be valid until the first activate call to the sensor
     * reported in this connection event. At a dynamic sensor disconnection,
     * the sensor_t pointer must be NULL.
     *
     * The sensor handle assigned to dynamic sensors must never be the same as
     * that of any regular static sensors, and must be unique until next boot.
     * In another word, if a handle h is used for a dynamic sensor A, that same
     * number cannot be used for the same dynamic sensor A or another dynamic
     * sensor B even after disconnection of A until reboot.
     *
     * The UUID field will be used for identifying the sensor in addition to
     * name, vendor and version and type. For physical sensors of the same
     * model, all sensors will have the same values in sensor_t, but the UUID
     * must be unique and persistent for each individual unit. An all zero
     * UUID indicates it is not possible to differentiate individual sensor
     * unit.
     *
     */
    DYNAMIC_SENSOR_META = 32,
    /**
     * ADDITIONAL_INFO
     * reporting-mode: N/A
     *
     * This sensor type is for delivering additional sensor information aside
     * from sensor event data.
     * Additional information may include sensor front-end group delay, internal
     * calibration parameters, noise level metrics, device internal temperature,
     * etc.
     *
     * This type will never bind to a sensor. In other words, no sensor in the
     * sensor list can have the type SENSOR_TYPE_ADDITIONAL_INFO. If a
     * sensor HAL supports sensor additional information feature, it reports
     * sensor_event_t with "sensor" field set to handle of the reporting sensor
     * and "type" field set to ADDITIONAL_INFO. Delivery of
     * additional information events is triggered under two conditions: an
     * enable activate() call or a flush() call to the corresponding sensor.
     * Besides, time varying parameters can update infrequently without being
     * triggered. Device is responsible to control update rate. The recommend
     * update rate is less than 1/1000 of sensor event rate or less than once
     * per minute in average.
     *
     * A single additional information report consists of multiple frames.
     * Sequences of these frames are ordered using timestamps, which means the
     * timestamps of sequential frames have to be at least 1 nanosecond apart
     * from each other. Each frame is a sensor_event_t delivered through the HAL
     * interface, with related data stored in the "additional_info" field, which
     * is of type additional_info_event_t.
     * The "type" field of additional_info_event_t denotes the nature of the
     * payload data (see additional_info_type_t).
     * The "serial" field is used to keep the sequence of payload data that
     * spans multiple frames. The first frame of the entire report is always of
     * type AINFO_BEGIN, and the last frame is always AINFO_END.
     *
     * If flush() was triggering the report, all additional information frames
     * must be delivered after flush complete event.
     */
    ADDITIONAL_INFO = 33,
    /**
     * LOW_LATENCY_OFFBODY_DETECT
     * trigger-mode: on-change
     * wake-up sensor: yes
     *
     * A sensor of this type is defined for devices that are supposed to be worn
     * by the user in the normal use case (such as a watch, wristband, etc) and
     * is not yet defined for other device.
     *
     * A sensor of this type triggers an event each time the wearable device
     * is removed from the body and each time it's put back onto the body.
     * It must be low-latency and be able to detect the on-body to off-body
     * transition within one second (event delivery time included),
     * and 3-second latency to determine the off-body to on-body transition
     * (event delivery time included).
     *
     * There are only two valid event values for the sensor to return :
     *    0.0 for off-body
     *    1.0 for on-body
     *
     */
    LOW_LATENCY_OFFBODY_DETECT = 34,
    /**
     * ACCELEROMETER_UNCALIBRATED
     * reporting-mode: continuous
     *
     * All values are in SI units (m/s^2) and measure the acceleration of the
     * device minus the acceleration due to gravity.
     *
     * Implement the non-wake-up version of this sensor and implement the
     * wake-up version if the system possesses a wake up fifo.
     */
    ACCELEROMETER_UNCALIBRATED = 35,
    /**
     * Base for device manufacturers private sensor types.
     * These sensor types can't be exposed in the SDK.
     */
    DEVICE_PRIVATE_BASE = 65536 /* 0x10000 */,
};

enum class SensorFlagBits : uint32_t {
    /**
     * Whether this sensor wakes up the AP from suspend mode when data is
     * available.  Whenever sensor events are delivered from a wake_up sensor,
     * the driver needs to hold a wake_lock till the events are read by the
     * SensorService i.e till ISensors::poll() is called the next time.
     * Once poll is called again it means events have been read by the
     * SensorService, the driver can safely release the wake_lock. SensorService
     * will continue to hold a wake_lock till the app actually reads the events.
     */
    WAKE_UP = 1u,
    /**
     * Reporting modes for various sensors. Each sensor will have exactly one of
     * these modes set.
     * The least significant 2nd, 3rd and 4th bits are used to represent four
     * possible reporting modes.
     */
    CONTINUOUS_MODE = 0u,
    ON_CHANGE_MODE = 2u,
    ONE_SHOT_MODE = 4u,
    SPECIAL_REPORTING_MODE = 6u,
    /**
     * Set this flag if the sensor supports data_injection mode and allows data
     * to be injected from the SensorService. When in data_injection ONLY
     * sensors with this flag set are injected sensor data and only sensors with
     * this flag set are activated. Eg: Accelerometer and Step Counter sensors
     * can be set with this flag and SensorService will inject accelerometer
     * data and read the corresponding step counts.
     */
    DATA_INJECTION = 16u /* 0x10 */,
    /**
     * Set this flag if the sensor is a dynamically connected sensor. See
     * DynamicSensorInfo and DYNAMIC_SENSOR_META for details.
     */
    DYNAMIC_SENSOR = 32u /* 0x20 */,
    /**
     * Set this flag if sensor additional information is supported.
     * See ADDITIONAL_INFO and AdditionalInfo for details.
     */
    ADDITIONAL_INFO = 64u /* 0x40 */,
    /**
     * Set this flag if sensor suppor direct channel backed by ashmem.
     * See SharedMemType and registerDirectChannel for more details.
     */
    DIRECT_CHANNEL_ASHMEM = 1024u /* 0x400 */,
    /**
     * Set this flag if sensor suppor direct channel backed by gralloc HAL memory.
     * See SharedMemType and registerDirectChannel for more details.
     */
    DIRECT_CHANNEL_GRALLOC = 2048u /* 0x800 */,
    /**
     * Flags mask for reporting mode of sensor.
     */
    MASK_REPORTING_MODE = 14u /* 0xE */,
    /**
     * Flags mask for direct report maximum rate level support.
     * See RateLevel.
     */
    MASK_DIRECT_REPORT = 896u /* 0x380 */,
    /**
     * Flags mask for all direct channel support bits.
     * See SharedMemType.
     */
    MASK_DIRECT_CHANNEL = 3072u /* 0xC00 */,
};

enum class SensorFlagShift : uint8_t {
    REPORTING_MODE = 1,
    DATA_INJECTION = 4,
    DYNAMIC_SENSOR = 5,
    ADDITIONAL_INFO = 6,
    DIRECT_REPORT = 7,
    DIRECT_CHANNEL = 10,
};

struct SensorInfo final {
    /**
     * handle that identifies this sensors. This handle is used to reference
     * this sensor throughout the HAL API.
     */
    int32_t sensorHandle __attribute__ ((aligned(4)));
    /**
     * Name of this sensor.
     * All sensors of the same "type" must have a different "name".
     */
    ::android::hardware::hidl_string name __attribute__ ((aligned(8)));
    /**
     * vendor of the hardware part
     */
    ::android::hardware::hidl_string vendor __attribute__ ((aligned(8)));
    /**
     * version of the hardware part + driver. The value of this field
     * must increase when the driver is updated in a way that changes the
     * output of this sensor. This is important for fused sensors when the
     * fusion algorithm is updated.
     */
    int32_t version __attribute__ ((aligned(4)));
    /**
     * this sensor's type.
     */
    ::android::hardware::sensors::V1_0::SensorType type __attribute__ ((aligned(4)));
    /**
     * type of this sensor as a string.
     *
     * When defining an OEM specific sensor or sensor manufacturer specific
     * sensor, use your reserve domain name as a prefix.
     * e.g. com.google.glass.onheaddetector
     *
     * For sensors of known type defined in SensorType (value <
     * SensorType::DEVICE_PRIVATE_BASE), this can be an empty string.
     */
    ::android::hardware::hidl_string typeAsString __attribute__ ((aligned(8)));
    /**
     * maximum range of this sensor's value in SI units
     */
    float maxRange __attribute__ ((aligned(4)));
    /**
     * smallest difference between two values reported by this sensor
     */
    float resolution __attribute__ ((aligned(4)));
    /**
     * rough estimate of this sensor's power consumption in mA
     */
    float power __attribute__ ((aligned(4)));
    /**
     * this value depends on the reporting mode:
     *
     *   continuous: minimum sample period allowed in microseconds
     *   on-change : 0
     *   one-shot  :-1
     *   special   : 0, unless otherwise noted
     */
    int32_t minDelay __attribute__ ((aligned(4)));
    /**
     * number of events reserved for this sensor in the batch mode FIFO.
     * If there is a dedicated FIFO for this sensor, then this is the
     * size of this FIFO. If the FIFO is shared with other sensors,
     * this is the size reserved for that sensor and it can be zero.
     */
    uint32_t fifoReservedEventCount __attribute__ ((aligned(4)));
    /**
     * maximum number of events of this sensor that could be batched.
     * This is especially relevant when the FIFO is shared between
     * several sensors; this value is then set to the size of that FIFO.
     */
    uint32_t fifoMaxEventCount __attribute__ ((aligned(4)));
    /**
     * permission required to see this sensor, register to it and receive data.
     * Set to "" if no permission is required. Some sensor types like the
     * heart rate monitor have a mandatory require_permission.
     * For sensors that always require a specific permission, like the heart
     * rate monitor, the android framework might overwrite this string
     * automatically.
     */
    ::android::hardware::hidl_string requiredPermission __attribute__ ((aligned(8)));
    /**
     * This value is defined only for continuous mode and on-change sensors.
     * It is the delay between two sensor events corresponding to the lowest
     * frequency that this sensor supports. When lower frequencies are requested
     * through batch()/setDelay() the events will be generated at this frequency
     * instead.
     * It can be used by the framework or applications to estimate when the
     * batch FIFO may be full.
     *
     * NOTE: periodNs is in nanoseconds where as maxDelay/minDelay are in
     *       microseconds.
     *
     *       continuous, on-change: maximum sampling period allowed in
     *                              microseconds.
     *
     *          one-shot, special : 0
     */
    int32_t maxDelay __attribute__ ((aligned(4)));
    /**
     * Bitmask of SensorFlagBits
     */
    ::android::hardware::hidl_bitfield<::android::hardware::sensors::V1_0::SensorFlagBits> flags __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::sensors::V1_0::SensorInfo, sensorHandle) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::SensorInfo, name) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::SensorInfo, vendor) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::SensorInfo, version) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::SensorInfo, type) == 44, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::SensorInfo, typeAsString) == 48, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::SensorInfo, maxRange) == 64, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::SensorInfo, resolution) == 68, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::SensorInfo, power) == 72, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::SensorInfo, minDelay) == 76, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::SensorInfo, fifoReservedEventCount) == 80, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::SensorInfo, fifoMaxEventCount) == 84, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::SensorInfo, requiredPermission) == 88, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::SensorInfo, maxDelay) == 104, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::SensorInfo, flags) == 108, "wrong offset");
static_assert(sizeof(::android::hardware::sensors::V1_0::SensorInfo) == 112, "wrong size");
static_assert(__alignof(::android::hardware::sensors::V1_0::SensorInfo) == 8, "wrong alignment");

enum class SensorStatus : int8_t {
    NO_CONTACT = -1 /* -1 */,
    UNRELIABLE = 0,
    ACCURACY_LOW = 1,
    ACCURACY_MEDIUM = 2,
    ACCURACY_HIGH = 3,
};

struct Vec3 final {
    float x __attribute__ ((aligned(4)));
    float y __attribute__ ((aligned(4)));
    float z __attribute__ ((aligned(4)));
    ::android::hardware::sensors::V1_0::SensorStatus status __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::sensors::V1_0::Vec3, x) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::Vec3, y) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::Vec3, z) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::Vec3, status) == 12, "wrong offset");
static_assert(sizeof(::android::hardware::sensors::V1_0::Vec3) == 16, "wrong size");
static_assert(__alignof(::android::hardware::sensors::V1_0::Vec3) == 4, "wrong alignment");

struct Vec4 final {
    float x __attribute__ ((aligned(4)));
    float y __attribute__ ((aligned(4)));
    float z __attribute__ ((aligned(4)));
    float w __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::sensors::V1_0::Vec4, x) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::Vec4, y) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::Vec4, z) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::Vec4, w) == 12, "wrong offset");
static_assert(sizeof(::android::hardware::sensors::V1_0::Vec4) == 16, "wrong size");
static_assert(__alignof(::android::hardware::sensors::V1_0::Vec4) == 4, "wrong alignment");

struct Uncal final {
    float x __attribute__ ((aligned(4)));
    float y __attribute__ ((aligned(4)));
    float z __attribute__ ((aligned(4)));
    float x_bias __attribute__ ((aligned(4)));
    float y_bias __attribute__ ((aligned(4)));
    float z_bias __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::sensors::V1_0::Uncal, x) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::Uncal, y) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::Uncal, z) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::Uncal, x_bias) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::Uncal, y_bias) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::Uncal, z_bias) == 20, "wrong offset");
static_assert(sizeof(::android::hardware::sensors::V1_0::Uncal) == 24, "wrong size");
static_assert(__alignof(::android::hardware::sensors::V1_0::Uncal) == 4, "wrong alignment");

struct HeartRate final {
    /**
     * Heart rate in beats per minute.
     * Set to 0 when status is SensorStatus::UNRELIABLE or
     * SensorStatus::NO_CONTACT
     */
    float bpm __attribute__ ((aligned(4)));
    /**
     * Status of the heart rate sensor for this reading.
     */
    ::android::hardware::sensors::V1_0::SensorStatus status __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::sensors::V1_0::HeartRate, bpm) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::HeartRate, status) == 4, "wrong offset");
static_assert(sizeof(::android::hardware::sensors::V1_0::HeartRate) == 8, "wrong size");
static_assert(__alignof(::android::hardware::sensors::V1_0::HeartRate) == 4, "wrong alignment");

enum class MetaDataEventType : uint32_t {
    META_DATA_FLUSH_COMPLETE = 1u,
};

struct MetaData final {
    ::android::hardware::sensors::V1_0::MetaDataEventType what __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::sensors::V1_0::MetaData, what) == 0, "wrong offset");
static_assert(sizeof(::android::hardware::sensors::V1_0::MetaData) == 4, "wrong size");
static_assert(__alignof(::android::hardware::sensors::V1_0::MetaData) == 4, "wrong alignment");

struct DynamicSensorInfo final {
    bool connected __attribute__ ((aligned(1)));
    int32_t sensorHandle __attribute__ ((aligned(4)));
    /**
     * UUID of a dynamic sensor (using RFC 4122 byte order)
     * For UUID 12345678-90AB-CDEF-1122-334455667788 the uuid field is
     * initialized as:
     *   {0x12, 0x34, 0x56, 0x78, 0x90, 0xAB, 0xCD, 0xEF, 0x11, ...}
     */
    ::android::hardware::hidl_array<uint8_t, 16> uuid __attribute__ ((aligned(1)));
};

static_assert(offsetof(::android::hardware::sensors::V1_0::DynamicSensorInfo, connected) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::DynamicSensorInfo, sensorHandle) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::DynamicSensorInfo, uuid) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::sensors::V1_0::DynamicSensorInfo) == 24, "wrong size");
static_assert(__alignof(::android::hardware::sensors::V1_0::DynamicSensorInfo) == 4, "wrong alignment");

enum class AdditionalInfoType : uint32_t {
    /**
     * Marks the beginning of additional information frames
     */
    AINFO_BEGIN = 0u,
    /**
     * Marks the end of additional information frames
     */
    AINFO_END = 1u,
    /**
     * Estimation of the delay that is not tracked by sensor timestamps. This
     * includes delay introduced by sensor front-end filtering, data transport,
     * etc.
     * float[2]: delay in seconds, standard deviation of estimated value
     */
    AINFO_UNTRACKED_DELAY = 65536u /* 0x10000 */,
    /**
     * float: Celsius temperature
     */
    AINFO_INTERNAL_TEMPERATURE = 65537u /* ::android::hardware::sensors::V1_0::AdditionalInfoType.AINFO_UNTRACKED_DELAY implicitly + 1 */,
    /**
     * First three rows of a homogeneous matrix, which represents calibration to
     * a three-element vector raw sensor reading.
     * float[12]: 3x4 matrix in row major order
     */
    AINFO_VEC3_CALIBRATION = 65538u /* ::android::hardware::sensors::V1_0::AdditionalInfoType.AINFO_INTERNAL_TEMPERATURE implicitly + 1 */,
    /**
     * Provides the orientation and location of the sensor element in terms of
     * the Android coordinate system. This data is given as a 3x4 matrix
     * consisting of a 3x3 rotation matrix (R) concatenated with a 3x1 location
     * vector (t). The rotation matrix provides the orientation of the Android
     * device coordinate frame relative to the local coordinate frame of the
     * sensor. Note that assuming the axes conventions of the sensor are the
     * same as Android, this is the inverse of the matrix applied to raw
     * samples read from the sensor to convert them into the Android
     * representation. The location vector represents the translation from the
     * origin of the Android sensor coordinate system to the geometric center
     * of the sensor, specified in millimeters (mm).
     *
     * float[12]: 3x4 matrix in row major order [R; t]
     *
     * Example:
     *     This raw buffer: {0, 1, 0, 0, -1, 0, 0, 10, 0, 0, 1, -2.5}
     *     Corresponds to this 3x4 matrix:
     *         0 1 0    0
     *        -1 0 0   10
     *         0 0 1 -2.5
     *     The sensor is oriented such that:
     *         - the device X axis corresponds to the sensor's local -Y axis
     *         - the device Y axis corresponds to the sensor's local X axis
     *         - the device Z axis and sensor's local Z axis are equivalent
     *     In other words, if viewing the origin of the Android coordinate
     *     system from the positive Z direction, the device coordinate frame is
     *     to be rotated 90 degrees counter-clockwise about the Z axis to align
     *     with the sensor's local coordinate frame. Equivalently, a vector in
     *     the Android coordinate frame may be multiplied with R to rotate it
     *     90 degrees clockwise (270 degrees counter-clockwise), yielding its
     *     representation in the sensor's coordinate frame.
     *     Relative to the origin of the Android coordinate system, the physical
     *     center of the sensor is located 10mm in the positive Y direction, and
     *     2.5mm in the negative Z direction.
     */
    AINFO_SENSOR_PLACEMENT = 65539u /* ::android::hardware::sensors::V1_0::AdditionalInfoType.AINFO_VEC3_CALIBRATION implicitly + 1 */,
    /**
     * float[2]: raw sample period in seconds,
     *           standard deviation of sampling period
     */
    AINFO_SAMPLING = 65540u /* ::android::hardware::sensors::V1_0::AdditionalInfoType.AINFO_SENSOR_PLACEMENT implicitly + 1 */,
    /**
     * int32_t: noise type
     * float[n]: parameters
     */
    AINFO_CHANNEL_NOISE = 131072u /* 0x20000 */,
    /**
     * float[3]: sample period, standard deviation of sample period,
     * quantization unit
     */
    AINFO_CHANNEL_SAMPLER = 131073u /* ::android::hardware::sensors::V1_0::AdditionalInfoType.AINFO_CHANNEL_NOISE implicitly + 1 */,
    /**
     * Represents a filter:
     *   \sum_j a_j y[n-j] == \sum_i b_i x[n-i]
     *
     * int32_t[3]: number of feedforward coeffients M,
     *             number of feedback coefficients N (for FIR filter, N = 1).
     *             bit mask that represents which element the filter is applied
     *             to. (bit 0==1 means this filter applies to vector element 0).
     * float[M+N]: filter coefficients (b0, b1, ..., b_{M-1}), then
     *             (a0, a1, ..., a_{N-1}), a0 is always 1.
     *
     * Multiple frames may be needed for higher number of taps.
     */
    AINFO_CHANNEL_FILTER = 131074u /* ::android::hardware::sensors::V1_0::AdditionalInfoType.AINFO_CHANNEL_SAMPLER implicitly + 1 */,
    /**
     * int32_t[2]: size in (row, column) ... 1st frame
     * float[n]: matrix element values in row major order.
     */
    AINFO_CHANNEL_LINEAR_TRANSFORM = 131075u /* ::android::hardware::sensors::V1_0::AdditionalInfoType.AINFO_CHANNEL_FILTER implicitly + 1 */,
    /**
     * int32_t[2]: extrapolate method, interpolate method
     * float[n]: mapping key points in pairs, (in, out)...
     *           (may be used to model saturation).
     */
    AINFO_CHANNEL_NONLINEAR_MAP = 131076u /* ::android::hardware::sensors::V1_0::AdditionalInfoType.AINFO_CHANNEL_LINEAR_TRANSFORM implicitly + 1 */,
    /**
     * int32_t: resample method (0-th order, 1st order...)
     * float[1]: resample ratio (upsampling if < 1.0, downsampling if > 1.0).
     */
    AINFO_CHANNEL_RESAMPLER = 131077u /* ::android::hardware::sensors::V1_0::AdditionalInfoType.AINFO_CHANNEL_NONLINEAR_MAP implicitly + 1 */,
    /**
     * Operation environment parameters section
     * Types in the following section is sent down (instead of reported from)
     * device as additional information to aid sensor operation. Data is sent
     * via injectSensorData() function to sensor handle -1 denoting all sensors
     * in device.
     *
     *
     * Local geomagnetic field information based on device geo location. This
     * type is primarily for for magnetic field calibration and rotation vector
     * sensor fusion.
     * float[3]: strength (uT), declination and inclination angle (rad).
     */
    AINFO_LOCAL_GEOMAGNETIC_FIELD = 196608u /* 0x30000 */,
    /**
     * Local gravitational acceleration strength at device geo location.
     * float: gravitational acceleration norm in m/s^2.
     */
    AINFO_LOCAL_GRAVITY = 196609u /* ::android::hardware::sensors::V1_0::AdditionalInfoType.AINFO_LOCAL_GEOMAGNETIC_FIELD implicitly + 1 */,
    /**
     * Device dock state.
     * int32_t: dock state following Android API Intent.EXTRA_DOCK_STATE
     * definition, undefined value is ignored.
     */
    AINFO_DOCK_STATE = 196610u /* ::android::hardware::sensors::V1_0::AdditionalInfoType.AINFO_LOCAL_GRAVITY implicitly + 1 */,
    /**
     * High performance mode hint. Device is able to use up more power and take
     * more reources to improve throughput and latency in high performance mode.
     * One possible use case is virtual reality, when sensor latency need to be
     * carefully controlled.
     * int32_t: 1 or 0, denote if device is in/out of high performance mode,
     *          other values is ignored.
     */
    AINFO_HIGH_PERFORMANCE_MODE = 196611u /* ::android::hardware::sensors::V1_0::AdditionalInfoType.AINFO_DOCK_STATE implicitly + 1 */,
    /**
     * Magnetic field calibration hint. Device is notified when manually
     * triggered magnetic field calibration procedure is started or stopped. The
     * calibration procedure is assumed timed out after 1 minute from start,
     * even if an explicit stop is not received.
     *
     * int32_t: 1 for start, 0 for stop, other value is ignored.
     */
    AINFO_MAGNETIC_FIELD_CALIBRATION = 196612u /* ::android::hardware::sensors::V1_0::AdditionalInfoType.AINFO_HIGH_PERFORMANCE_MODE implicitly + 1 */,
    /**
     * Custom information
     */
    AINFO_CUSTOM_START = 268435456u /* 0x10000000 */,
    /**
     * Debugging
     */
    AINFO_DEBUGGING_START = 1073741824u /* 0x40000000 */,
};

struct AdditionalInfo final {
    // Forward declaration for forward reference support:
    union Payload;

    union Payload final {
        ::android::hardware::hidl_array<int32_t, 14> data_int32 __attribute__ ((aligned(4)));
        ::android::hardware::hidl_array<float, 14> data_float __attribute__ ((aligned(4)));
    };

    static_assert(offsetof(::android::hardware::sensors::V1_0::AdditionalInfo::Payload, data_int32) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::sensors::V1_0::AdditionalInfo::Payload, data_float) == 0, "wrong offset");
    static_assert(sizeof(::android::hardware::sensors::V1_0::AdditionalInfo::Payload) == 56, "wrong size");
    static_assert(__alignof(::android::hardware::sensors::V1_0::AdditionalInfo::Payload) == 4, "wrong alignment");

    /**
     * type of payload data, see AdditionalInfoType
     */
    ::android::hardware::sensors::V1_0::AdditionalInfoType type __attribute__ ((aligned(4)));
    /**
     * sequence number of this frame for this type
     */
    int32_t serial __attribute__ ((aligned(4)));
    ::android::hardware::sensors::V1_0::AdditionalInfo::Payload u __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::sensors::V1_0::AdditionalInfo, type) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::AdditionalInfo, serial) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::AdditionalInfo, u) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::sensors::V1_0::AdditionalInfo) == 64, "wrong size");
static_assert(__alignof(::android::hardware::sensors::V1_0::AdditionalInfo) == 4, "wrong alignment");

/*
 * acceleration values are in meter per second per second (m/s^2)
 * magnetic vector values are in micro-Tesla (uT)
 * orientation values are in degrees
 * gyroscope values are in rad/s
 * temperature is in degrees centigrade (Celsius)
 * distance in centimeters
 * light in SI lux units
 * pressure in hectopascal (hPa)
 * relative humidity in percent
 */
union EventPayload final {
    /**
     * SensorType::ACCELEROMETER, SensorType::MAGNETIC_FIELD,
     * SensorType::ORIENTATION, SensorType::GYROSCOPE, SensorType::GRAVITY,
     * SensorType::LINEAR_ACCELERATION
     */
    ::android::hardware::sensors::V1_0::Vec3 vec3 __attribute__ ((aligned(4)));
    /**
     * SensorType::GAME_ROTATION_VECTOR
     */
    ::android::hardware::sensors::V1_0::Vec4 vec4 __attribute__ ((aligned(4)));
    /**
     * SensorType::MAGNETIC_FIELD_UNCALIBRATED,
     * SensorType::GYROSCOPE_UNCALIBRATED
     * SensorType::ACCELEROMETER_UNCALIBRATED
     */
    ::android::hardware::sensors::V1_0::Uncal uncal __attribute__ ((aligned(4)));
    /**
     * SensorType::META_DATA
     */
    ::android::hardware::sensors::V1_0::MetaData meta __attribute__ ((aligned(4)));
    /**
     * SensorType::DEVICE_ORIENTATION, SensorType::LIGHT, SensorType::PRESSURE,
     * SensorType::TEMPERATURE, SensorType::PROXIMITY,
     * SensorType::RELATIVE_HUMIDITY, SensorType::AMBIENT_TEMPERATURE,
     * SensorType::SIGNIFICANT_MOTION, SensorType::STEP_DETECTOR,
     * SensorType::TILT_DETECTOR, SensorType::WAKE_GESTURE,
     * SensorType::GLANCE_GESTURE, SensorType::PICK_UP_GESTURE,
     * SensorType::WRIST_TILT_GESTURE, SensorType::STATIONARY_DETECT,
     * SensorType::MOTION_DETECT, SensorType::HEART_BEAT,
     * SensorType::LOW_LATENCY_OFFBODY_DETECT
     */
    float scalar __attribute__ ((aligned(4)));
    /**
     * SensorType::STEP_COUNTER
     */
    uint64_t stepCount __attribute__ ((aligned(8)));
    /**
     * SensorType::HEART_RATE
     */
    ::android::hardware::sensors::V1_0::HeartRate heartRate __attribute__ ((aligned(4)));
    /**
     * SensorType::POSE_6DOF
     */
    ::android::hardware::hidl_array<float, 15> pose6DOF __attribute__ ((aligned(4)));
    /**
     * SensorType::DYNAMIC_SENSOR_META
     */
    ::android::hardware::sensors::V1_0::DynamicSensorInfo dynamic __attribute__ ((aligned(4)));
    /**
     * SensorType::ADDITIONAL_INFO
     */
    ::android::hardware::sensors::V1_0::AdditionalInfo additional __attribute__ ((aligned(4)));
    /**
     * The following sensors should use the data field:
     * - Undefined/custom sensor type >= SensorType::DEVICE_PRIVATE_BASE
     * - SensorType::ROTATION_VECTOR, SensorType::GEOMAGNETIC_ROTATION_VECTOR:
     *   - These are Vec4 types with an additional float accuracy field,
     *     where data[4] is the estimated heading accuracy in radians
     *     (-1 if unavailable, and invalid if not in the range (0, 2 * pi]).
     */
    ::android::hardware::hidl_array<float, 16> data __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::sensors::V1_0::EventPayload, vec3) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::EventPayload, vec4) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::EventPayload, uncal) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::EventPayload, meta) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::EventPayload, scalar) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::EventPayload, stepCount) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::EventPayload, heartRate) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::EventPayload, pose6DOF) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::EventPayload, dynamic) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::EventPayload, additional) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::EventPayload, data) == 0, "wrong offset");
static_assert(sizeof(::android::hardware::sensors::V1_0::EventPayload) == 64, "wrong size");
static_assert(__alignof(::android::hardware::sensors::V1_0::EventPayload) == 8, "wrong alignment");

struct Event final {
    /**
     * Time measured in nanoseconds, in "elapsedRealtimeNano()'s" timebase.
     */
    int64_t timestamp __attribute__ ((aligned(8)));
    /**
     * sensor identifier
     */
    int32_t sensorHandle __attribute__ ((aligned(4)));
    ::android::hardware::sensors::V1_0::SensorType sensorType __attribute__ ((aligned(4)));
    /**
     * Union discriminated on sensorType
     */
    ::android::hardware::sensors::V1_0::EventPayload u __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::sensors::V1_0::Event, timestamp) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::Event, sensorHandle) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::Event, sensorType) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::Event, u) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::sensors::V1_0::Event) == 80, "wrong size");
static_assert(__alignof(::android::hardware::sensors::V1_0::Event) == 8, "wrong alignment");

/**
 * Direct report rate level definition. Except for SENSOR_DIRECT_RATE_STOP, each
 * rate level covers the range (55%, 220%] * nominal report rate. For example,
 * if config direct report specify a rate level SENSOR_DIRECT_RATE_FAST, it is
 * legal for sensor hardware to report event at a rate greater than 110Hz, and
 * less or equal to 440Hz. Note that rate has to remain steady without variation
 * before new rate level is configured, i.e. if a sensor is configured to
 * SENSOR_DIRECT_RATE_FAST and starts to report event at 256Hz, it cannot
 * change rate to 128Hz after a few seconds of running even if 128Hz is also in
 * the legal range of SENSOR_DIRECT_RATE_FAST. Thus, it is recommended to
 * associate report rate with RateLvel statically for single sensor.
 */
enum class RateLevel : int32_t {
    STOP = 0,
    NORMAL = 1 /* ::android::hardware::sensors::V1_0::RateLevel.STOP implicitly + 1 */,
    FAST = 2 /* ::android::hardware::sensors::V1_0::RateLevel.NORMAL implicitly + 1 */,
    VERY_FAST = 3 /* ::android::hardware::sensors::V1_0::RateLevel.FAST implicitly + 1 */,
};

/**
 * Direct channel shared memory types. See struct SharedMemInfo.
 */
enum class SharedMemType : int32_t {
    ASHMEM = 1,
    GRALLOC = 2 /* ::android::hardware::sensors::V1_0::SharedMemType.ASHMEM implicitly + 1 */,
};

/**
 * Direct channel lock-free queue format, this defines how the shared memory is
 * interpreted by both sensor hardware and application.
 *
 * @see SharedMemInfo.
 */
enum class SharedMemFormat : int32_t {
    SENSORS_EVENT = 1,
};

enum class SensorsEventFormatOffset : uint16_t {
    SIZE_FIELD = 0 /* 0x0 */,
    REPORT_TOKEN = 4 /* 0x4 */,
    SENSOR_TYPE = 8 /* 0x8 */,
    ATOMIC_COUNTER = 12 /* 0xC */,
    TIMESTAMP = 16 /* 0x10 */,
    DATA = 24 /* 0x18 */,
    RESERVED = 88 /* 0x58 */,
    TOTAL_LENGTH = 104 /* 0x68 */,
};

/**
 * Shared memory information for a direct channel
 */
struct SharedMemInfo final {
    ::android::hardware::sensors::V1_0::SharedMemType type __attribute__ ((aligned(4)));
    ::android::hardware::sensors::V1_0::SharedMemFormat format __attribute__ ((aligned(4)));
    uint32_t size __attribute__ ((aligned(4)));
    ::android::hardware::hidl_handle memoryHandle __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::sensors::V1_0::SharedMemInfo, type) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::SharedMemInfo, format) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::SharedMemInfo, size) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V1_0::SharedMemInfo, memoryHandle) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::sensors::V1_0::SharedMemInfo) == 32, "wrong size");
static_assert(__alignof(::android::hardware::sensors::V1_0::SharedMemInfo) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::sensors::V1_0::Result o);
static inline void PrintTo(::android::hardware::sensors::V1_0::Result o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::sensors::V1_0::Result lhs, const ::android::hardware::sensors::V1_0::Result rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::sensors::V1_0::Result rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::sensors::V1_0::Result lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::sensors::V1_0::Result lhs, const ::android::hardware::sensors::V1_0::Result rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::sensors::V1_0::Result rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::sensors::V1_0::Result lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::sensors::V1_0::Result e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::sensors::V1_0::Result e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::sensors::V1_0::OperationMode o);
static inline void PrintTo(::android::hardware::sensors::V1_0::OperationMode o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::sensors::V1_0::OperationMode lhs, const ::android::hardware::sensors::V1_0::OperationMode rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::sensors::V1_0::OperationMode rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::sensors::V1_0::OperationMode lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::sensors::V1_0::OperationMode lhs, const ::android::hardware::sensors::V1_0::OperationMode rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::sensors::V1_0::OperationMode rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::sensors::V1_0::OperationMode lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::sensors::V1_0::OperationMode e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::sensors::V1_0::OperationMode e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::sensors::V1_0::SensorType o);
static inline void PrintTo(::android::hardware::sensors::V1_0::SensorType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::sensors::V1_0::SensorType lhs, const ::android::hardware::sensors::V1_0::SensorType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::sensors::V1_0::SensorType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::sensors::V1_0::SensorType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::sensors::V1_0::SensorType lhs, const ::android::hardware::sensors::V1_0::SensorType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::sensors::V1_0::SensorType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::sensors::V1_0::SensorType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::sensors::V1_0::SensorType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::sensors::V1_0::SensorType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::sensors::V1_0::SensorFlagBits o);
static inline void PrintTo(::android::hardware::sensors::V1_0::SensorFlagBits o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::sensors::V1_0::SensorFlagBits lhs, const ::android::hardware::sensors::V1_0::SensorFlagBits rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::sensors::V1_0::SensorFlagBits rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::sensors::V1_0::SensorFlagBits lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::sensors::V1_0::SensorFlagBits lhs, const ::android::hardware::sensors::V1_0::SensorFlagBits rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::sensors::V1_0::SensorFlagBits rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::sensors::V1_0::SensorFlagBits lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::sensors::V1_0::SensorFlagBits e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::sensors::V1_0::SensorFlagBits e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::sensors::V1_0::SensorFlagShift o);
static inline void PrintTo(::android::hardware::sensors::V1_0::SensorFlagShift o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::sensors::V1_0::SensorFlagShift lhs, const ::android::hardware::sensors::V1_0::SensorFlagShift rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::sensors::V1_0::SensorFlagShift rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::sensors::V1_0::SensorFlagShift lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::sensors::V1_0::SensorFlagShift lhs, const ::android::hardware::sensors::V1_0::SensorFlagShift rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::sensors::V1_0::SensorFlagShift rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::sensors::V1_0::SensorFlagShift lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::sensors::V1_0::SensorFlagShift e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::sensors::V1_0::SensorFlagShift e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::sensors::V1_0::SensorInfo& o);
static inline void PrintTo(const ::android::hardware::sensors::V1_0::SensorInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::sensors::V1_0::SensorInfo& lhs, const ::android::hardware::sensors::V1_0::SensorInfo& rhs);
static inline bool operator!=(const ::android::hardware::sensors::V1_0::SensorInfo& lhs, const ::android::hardware::sensors::V1_0::SensorInfo& rhs);

template<typename>
static inline std::string toString(int8_t o);
static inline std::string toString(::android::hardware::sensors::V1_0::SensorStatus o);
static inline void PrintTo(::android::hardware::sensors::V1_0::SensorStatus o, ::std::ostream* os);
constexpr int8_t operator|(const ::android::hardware::sensors::V1_0::SensorStatus lhs, const ::android::hardware::sensors::V1_0::SensorStatus rhs) {
    return static_cast<int8_t>(static_cast<int8_t>(lhs) | static_cast<int8_t>(rhs));
}
constexpr int8_t operator|(const int8_t lhs, const ::android::hardware::sensors::V1_0::SensorStatus rhs) {
    return static_cast<int8_t>(lhs | static_cast<int8_t>(rhs));
}
constexpr int8_t operator|(const ::android::hardware::sensors::V1_0::SensorStatus lhs, const int8_t rhs) {
    return static_cast<int8_t>(static_cast<int8_t>(lhs) | rhs);
}
constexpr int8_t operator&(const ::android::hardware::sensors::V1_0::SensorStatus lhs, const ::android::hardware::sensors::V1_0::SensorStatus rhs) {
    return static_cast<int8_t>(static_cast<int8_t>(lhs) & static_cast<int8_t>(rhs));
}
constexpr int8_t operator&(const int8_t lhs, const ::android::hardware::sensors::V1_0::SensorStatus rhs) {
    return static_cast<int8_t>(lhs & static_cast<int8_t>(rhs));
}
constexpr int8_t operator&(const ::android::hardware::sensors::V1_0::SensorStatus lhs, const int8_t rhs) {
    return static_cast<int8_t>(static_cast<int8_t>(lhs) & rhs);
}
constexpr int8_t &operator|=(int8_t& v, const ::android::hardware::sensors::V1_0::SensorStatus e) {
    v |= static_cast<int8_t>(e);
    return v;
}
constexpr int8_t &operator&=(int8_t& v, const ::android::hardware::sensors::V1_0::SensorStatus e) {
    v &= static_cast<int8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::sensors::V1_0::Vec3& o);
static inline void PrintTo(const ::android::hardware::sensors::V1_0::Vec3& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::sensors::V1_0::Vec3& lhs, const ::android::hardware::sensors::V1_0::Vec3& rhs);
static inline bool operator!=(const ::android::hardware::sensors::V1_0::Vec3& lhs, const ::android::hardware::sensors::V1_0::Vec3& rhs);

static inline std::string toString(const ::android::hardware::sensors::V1_0::Vec4& o);
static inline void PrintTo(const ::android::hardware::sensors::V1_0::Vec4& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::sensors::V1_0::Vec4& lhs, const ::android::hardware::sensors::V1_0::Vec4& rhs);
static inline bool operator!=(const ::android::hardware::sensors::V1_0::Vec4& lhs, const ::android::hardware::sensors::V1_0::Vec4& rhs);

static inline std::string toString(const ::android::hardware::sensors::V1_0::Uncal& o);
static inline void PrintTo(const ::android::hardware::sensors::V1_0::Uncal& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::sensors::V1_0::Uncal& lhs, const ::android::hardware::sensors::V1_0::Uncal& rhs);
static inline bool operator!=(const ::android::hardware::sensors::V1_0::Uncal& lhs, const ::android::hardware::sensors::V1_0::Uncal& rhs);

static inline std::string toString(const ::android::hardware::sensors::V1_0::HeartRate& o);
static inline void PrintTo(const ::android::hardware::sensors::V1_0::HeartRate& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::sensors::V1_0::HeartRate& lhs, const ::android::hardware::sensors::V1_0::HeartRate& rhs);
static inline bool operator!=(const ::android::hardware::sensors::V1_0::HeartRate& lhs, const ::android::hardware::sensors::V1_0::HeartRate& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::sensors::V1_0::MetaDataEventType o);
static inline void PrintTo(::android::hardware::sensors::V1_0::MetaDataEventType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::sensors::V1_0::MetaDataEventType lhs, const ::android::hardware::sensors::V1_0::MetaDataEventType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::sensors::V1_0::MetaDataEventType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::sensors::V1_0::MetaDataEventType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::sensors::V1_0::MetaDataEventType lhs, const ::android::hardware::sensors::V1_0::MetaDataEventType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::sensors::V1_0::MetaDataEventType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::sensors::V1_0::MetaDataEventType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::sensors::V1_0::MetaDataEventType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::sensors::V1_0::MetaDataEventType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::sensors::V1_0::MetaData& o);
static inline void PrintTo(const ::android::hardware::sensors::V1_0::MetaData& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::sensors::V1_0::MetaData& lhs, const ::android::hardware::sensors::V1_0::MetaData& rhs);
static inline bool operator!=(const ::android::hardware::sensors::V1_0::MetaData& lhs, const ::android::hardware::sensors::V1_0::MetaData& rhs);

static inline std::string toString(const ::android::hardware::sensors::V1_0::DynamicSensorInfo& o);
static inline void PrintTo(const ::android::hardware::sensors::V1_0::DynamicSensorInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::sensors::V1_0::DynamicSensorInfo& lhs, const ::android::hardware::sensors::V1_0::DynamicSensorInfo& rhs);
static inline bool operator!=(const ::android::hardware::sensors::V1_0::DynamicSensorInfo& lhs, const ::android::hardware::sensors::V1_0::DynamicSensorInfo& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::sensors::V1_0::AdditionalInfoType o);
static inline void PrintTo(::android::hardware::sensors::V1_0::AdditionalInfoType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::sensors::V1_0::AdditionalInfoType lhs, const ::android::hardware::sensors::V1_0::AdditionalInfoType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::sensors::V1_0::AdditionalInfoType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::sensors::V1_0::AdditionalInfoType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::sensors::V1_0::AdditionalInfoType lhs, const ::android::hardware::sensors::V1_0::AdditionalInfoType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::sensors::V1_0::AdditionalInfoType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::sensors::V1_0::AdditionalInfoType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::sensors::V1_0::AdditionalInfoType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::sensors::V1_0::AdditionalInfoType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::sensors::V1_0::AdditionalInfo::Payload& o);
static inline void PrintTo(const ::android::hardware::sensors::V1_0::AdditionalInfo::Payload& o, ::std::ostream*);
// operator== and operator!= are not generated for Payload

static inline std::string toString(const ::android::hardware::sensors::V1_0::AdditionalInfo& o);
static inline void PrintTo(const ::android::hardware::sensors::V1_0::AdditionalInfo& o, ::std::ostream*);
// operator== and operator!= are not generated for AdditionalInfo

static inline std::string toString(const ::android::hardware::sensors::V1_0::EventPayload& o);
static inline void PrintTo(const ::android::hardware::sensors::V1_0::EventPayload& o, ::std::ostream*);
// operator== and operator!= are not generated for EventPayload

static inline std::string toString(const ::android::hardware::sensors::V1_0::Event& o);
static inline void PrintTo(const ::android::hardware::sensors::V1_0::Event& o, ::std::ostream*);
// operator== and operator!= are not generated for Event

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::sensors::V1_0::RateLevel o);
static inline void PrintTo(::android::hardware::sensors::V1_0::RateLevel o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::sensors::V1_0::RateLevel lhs, const ::android::hardware::sensors::V1_0::RateLevel rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::sensors::V1_0::RateLevel rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::sensors::V1_0::RateLevel lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::sensors::V1_0::RateLevel lhs, const ::android::hardware::sensors::V1_0::RateLevel rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::sensors::V1_0::RateLevel rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::sensors::V1_0::RateLevel lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::sensors::V1_0::RateLevel e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::sensors::V1_0::RateLevel e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::sensors::V1_0::SharedMemType o);
static inline void PrintTo(::android::hardware::sensors::V1_0::SharedMemType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::sensors::V1_0::SharedMemType lhs, const ::android::hardware::sensors::V1_0::SharedMemType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::sensors::V1_0::SharedMemType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::sensors::V1_0::SharedMemType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::sensors::V1_0::SharedMemType lhs, const ::android::hardware::sensors::V1_0::SharedMemType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::sensors::V1_0::SharedMemType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::sensors::V1_0::SharedMemType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::sensors::V1_0::SharedMemType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::sensors::V1_0::SharedMemType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::sensors::V1_0::SharedMemFormat o);
static inline void PrintTo(::android::hardware::sensors::V1_0::SharedMemFormat o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::sensors::V1_0::SharedMemFormat lhs, const ::android::hardware::sensors::V1_0::SharedMemFormat rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::sensors::V1_0::SharedMemFormat rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::sensors::V1_0::SharedMemFormat lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::sensors::V1_0::SharedMemFormat lhs, const ::android::hardware::sensors::V1_0::SharedMemFormat rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::sensors::V1_0::SharedMemFormat rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::sensors::V1_0::SharedMemFormat lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::sensors::V1_0::SharedMemFormat e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::sensors::V1_0::SharedMemFormat e) {
    v &= static_cast<int32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint16_t o);
static inline std::string toString(::android::hardware::sensors::V1_0::SensorsEventFormatOffset o);
static inline void PrintTo(::android::hardware::sensors::V1_0::SensorsEventFormatOffset o, ::std::ostream* os);
constexpr uint16_t operator|(const ::android::hardware::sensors::V1_0::SensorsEventFormatOffset lhs, const ::android::hardware::sensors::V1_0::SensorsEventFormatOffset rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const uint16_t lhs, const ::android::hardware::sensors::V1_0::SensorsEventFormatOffset rhs) {
    return static_cast<uint16_t>(lhs | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const ::android::hardware::sensors::V1_0::SensorsEventFormatOffset lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | rhs);
}
constexpr uint16_t operator&(const ::android::hardware::sensors::V1_0::SensorsEventFormatOffset lhs, const ::android::hardware::sensors::V1_0::SensorsEventFormatOffset rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const uint16_t lhs, const ::android::hardware::sensors::V1_0::SensorsEventFormatOffset rhs) {
    return static_cast<uint16_t>(lhs & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const ::android::hardware::sensors::V1_0::SensorsEventFormatOffset lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & rhs);
}
constexpr uint16_t &operator|=(uint16_t& v, const ::android::hardware::sensors::V1_0::SensorsEventFormatOffset e) {
    v |= static_cast<uint16_t>(e);
    return v;
}
constexpr uint16_t &operator&=(uint16_t& v, const ::android::hardware::sensors::V1_0::SensorsEventFormatOffset e) {
    v &= static_cast<uint16_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::sensors::V1_0::SharedMemInfo& o);
static inline void PrintTo(const ::android::hardware::sensors::V1_0::SharedMemInfo& o, ::std::ostream*);
// operator== and operator!= are not generated for SharedMemInfo

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::sensors::V1_0::Result>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::sensors::V1_0::Result> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::sensors::V1_0::Result::OK) == static_cast<int32_t>(::android::hardware::sensors::V1_0::Result::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::Result::OK;
    }
    if ((o & ::android::hardware::sensors::V1_0::Result::PERMISSION_DENIED) == static_cast<int32_t>(::android::hardware::sensors::V1_0::Result::PERMISSION_DENIED)) {
        os += (first ? "" : " | ");
        os += "PERMISSION_DENIED";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::Result::PERMISSION_DENIED;
    }
    if ((o & ::android::hardware::sensors::V1_0::Result::NO_MEMORY) == static_cast<int32_t>(::android::hardware::sensors::V1_0::Result::NO_MEMORY)) {
        os += (first ? "" : " | ");
        os += "NO_MEMORY";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::Result::NO_MEMORY;
    }
    if ((o & ::android::hardware::sensors::V1_0::Result::BAD_VALUE) == static_cast<int32_t>(::android::hardware::sensors::V1_0::Result::BAD_VALUE)) {
        os += (first ? "" : " | ");
        os += "BAD_VALUE";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::Result::BAD_VALUE;
    }
    if ((o & ::android::hardware::sensors::V1_0::Result::INVALID_OPERATION) == static_cast<int32_t>(::android::hardware::sensors::V1_0::Result::INVALID_OPERATION)) {
        os += (first ? "" : " | ");
        os += "INVALID_OPERATION";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::Result::INVALID_OPERATION;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::sensors::V1_0::Result o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::sensors::V1_0::Result::OK) {
        return "OK";
    }
    if (o == ::android::hardware::sensors::V1_0::Result::PERMISSION_DENIED) {
        return "PERMISSION_DENIED";
    }
    if (o == ::android::hardware::sensors::V1_0::Result::NO_MEMORY) {
        return "NO_MEMORY";
    }
    if (o == ::android::hardware::sensors::V1_0::Result::BAD_VALUE) {
        return "BAD_VALUE";
    }
    if (o == ::android::hardware::sensors::V1_0::Result::INVALID_OPERATION) {
        return "INVALID_OPERATION";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::sensors::V1_0::Result o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::sensors::V1_0::OperationMode>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::sensors::V1_0::OperationMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::sensors::V1_0::OperationMode::NORMAL) == static_cast<int32_t>(::android::hardware::sensors::V1_0::OperationMode::NORMAL)) {
        os += (first ? "" : " | ");
        os += "NORMAL";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::OperationMode::NORMAL;
    }
    if ((o & ::android::hardware::sensors::V1_0::OperationMode::DATA_INJECTION) == static_cast<int32_t>(::android::hardware::sensors::V1_0::OperationMode::DATA_INJECTION)) {
        os += (first ? "" : " | ");
        os += "DATA_INJECTION";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::OperationMode::DATA_INJECTION;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::sensors::V1_0::OperationMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::sensors::V1_0::OperationMode::NORMAL) {
        return "NORMAL";
    }
    if (o == ::android::hardware::sensors::V1_0::OperationMode::DATA_INJECTION) {
        return "DATA_INJECTION";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::sensors::V1_0::OperationMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::sensors::V1_0::SensorType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::sensors::V1_0::SensorType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::sensors::V1_0::SensorType::META_DATA) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::META_DATA)) {
        os += (first ? "" : " | ");
        os += "META_DATA";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::META_DATA;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::ACCELEROMETER) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::ACCELEROMETER)) {
        os += (first ? "" : " | ");
        os += "ACCELEROMETER";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::ACCELEROMETER;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::MAGNETIC_FIELD) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::MAGNETIC_FIELD)) {
        os += (first ? "" : " | ");
        os += "MAGNETIC_FIELD";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::MAGNETIC_FIELD;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::ORIENTATION) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::ORIENTATION)) {
        os += (first ? "" : " | ");
        os += "ORIENTATION";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::ORIENTATION;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::GYROSCOPE) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::GYROSCOPE)) {
        os += (first ? "" : " | ");
        os += "GYROSCOPE";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::GYROSCOPE;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::LIGHT) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::LIGHT)) {
        os += (first ? "" : " | ");
        os += "LIGHT";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::LIGHT;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::PRESSURE) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::PRESSURE)) {
        os += (first ? "" : " | ");
        os += "PRESSURE";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::PRESSURE;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::TEMPERATURE) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::TEMPERATURE)) {
        os += (first ? "" : " | ");
        os += "TEMPERATURE";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::TEMPERATURE;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::PROXIMITY) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::PROXIMITY)) {
        os += (first ? "" : " | ");
        os += "PROXIMITY";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::PROXIMITY;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::GRAVITY) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::GRAVITY)) {
        os += (first ? "" : " | ");
        os += "GRAVITY";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::GRAVITY;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::LINEAR_ACCELERATION) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::LINEAR_ACCELERATION)) {
        os += (first ? "" : " | ");
        os += "LINEAR_ACCELERATION";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::LINEAR_ACCELERATION;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::ROTATION_VECTOR) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::ROTATION_VECTOR)) {
        os += (first ? "" : " | ");
        os += "ROTATION_VECTOR";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::ROTATION_VECTOR;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::RELATIVE_HUMIDITY) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::RELATIVE_HUMIDITY)) {
        os += (first ? "" : " | ");
        os += "RELATIVE_HUMIDITY";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::RELATIVE_HUMIDITY;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::AMBIENT_TEMPERATURE) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::AMBIENT_TEMPERATURE)) {
        os += (first ? "" : " | ");
        os += "AMBIENT_TEMPERATURE";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::AMBIENT_TEMPERATURE;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::MAGNETIC_FIELD_UNCALIBRATED) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::MAGNETIC_FIELD_UNCALIBRATED)) {
        os += (first ? "" : " | ");
        os += "MAGNETIC_FIELD_UNCALIBRATED";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::MAGNETIC_FIELD_UNCALIBRATED;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::GAME_ROTATION_VECTOR) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::GAME_ROTATION_VECTOR)) {
        os += (first ? "" : " | ");
        os += "GAME_ROTATION_VECTOR";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::GAME_ROTATION_VECTOR;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::GYROSCOPE_UNCALIBRATED) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::GYROSCOPE_UNCALIBRATED)) {
        os += (first ? "" : " | ");
        os += "GYROSCOPE_UNCALIBRATED";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::GYROSCOPE_UNCALIBRATED;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::SIGNIFICANT_MOTION) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::SIGNIFICANT_MOTION)) {
        os += (first ? "" : " | ");
        os += "SIGNIFICANT_MOTION";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::SIGNIFICANT_MOTION;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::STEP_DETECTOR) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::STEP_DETECTOR)) {
        os += (first ? "" : " | ");
        os += "STEP_DETECTOR";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::STEP_DETECTOR;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::STEP_COUNTER) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::STEP_COUNTER)) {
        os += (first ? "" : " | ");
        os += "STEP_COUNTER";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::STEP_COUNTER;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::GEOMAGNETIC_ROTATION_VECTOR) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::GEOMAGNETIC_ROTATION_VECTOR)) {
        os += (first ? "" : " | ");
        os += "GEOMAGNETIC_ROTATION_VECTOR";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::GEOMAGNETIC_ROTATION_VECTOR;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::HEART_RATE) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::HEART_RATE)) {
        os += (first ? "" : " | ");
        os += "HEART_RATE";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::HEART_RATE;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::TILT_DETECTOR) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::TILT_DETECTOR)) {
        os += (first ? "" : " | ");
        os += "TILT_DETECTOR";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::TILT_DETECTOR;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::WAKE_GESTURE) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::WAKE_GESTURE)) {
        os += (first ? "" : " | ");
        os += "WAKE_GESTURE";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::WAKE_GESTURE;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::GLANCE_GESTURE) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::GLANCE_GESTURE)) {
        os += (first ? "" : " | ");
        os += "GLANCE_GESTURE";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::GLANCE_GESTURE;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::PICK_UP_GESTURE) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::PICK_UP_GESTURE)) {
        os += (first ? "" : " | ");
        os += "PICK_UP_GESTURE";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::PICK_UP_GESTURE;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::WRIST_TILT_GESTURE) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::WRIST_TILT_GESTURE)) {
        os += (first ? "" : " | ");
        os += "WRIST_TILT_GESTURE";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::WRIST_TILT_GESTURE;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::DEVICE_ORIENTATION) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::DEVICE_ORIENTATION)) {
        os += (first ? "" : " | ");
        os += "DEVICE_ORIENTATION";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::DEVICE_ORIENTATION;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::POSE_6DOF) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::POSE_6DOF)) {
        os += (first ? "" : " | ");
        os += "POSE_6DOF";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::POSE_6DOF;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::STATIONARY_DETECT) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::STATIONARY_DETECT)) {
        os += (first ? "" : " | ");
        os += "STATIONARY_DETECT";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::STATIONARY_DETECT;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::MOTION_DETECT) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::MOTION_DETECT)) {
        os += (first ? "" : " | ");
        os += "MOTION_DETECT";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::MOTION_DETECT;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::HEART_BEAT) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::HEART_BEAT)) {
        os += (first ? "" : " | ");
        os += "HEART_BEAT";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::HEART_BEAT;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::DYNAMIC_SENSOR_META) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::DYNAMIC_SENSOR_META)) {
        os += (first ? "" : " | ");
        os += "DYNAMIC_SENSOR_META";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::DYNAMIC_SENSOR_META;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::ADDITIONAL_INFO) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::ADDITIONAL_INFO)) {
        os += (first ? "" : " | ");
        os += "ADDITIONAL_INFO";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::ADDITIONAL_INFO;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::LOW_LATENCY_OFFBODY_DETECT) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::LOW_LATENCY_OFFBODY_DETECT)) {
        os += (first ? "" : " | ");
        os += "LOW_LATENCY_OFFBODY_DETECT";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::LOW_LATENCY_OFFBODY_DETECT;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::ACCELEROMETER_UNCALIBRATED) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::ACCELEROMETER_UNCALIBRATED)) {
        os += (first ? "" : " | ");
        os += "ACCELEROMETER_UNCALIBRATED";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::ACCELEROMETER_UNCALIBRATED;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorType::DEVICE_PRIVATE_BASE) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SensorType::DEVICE_PRIVATE_BASE)) {
        os += (first ? "" : " | ");
        os += "DEVICE_PRIVATE_BASE";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorType::DEVICE_PRIVATE_BASE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::sensors::V1_0::SensorType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::sensors::V1_0::SensorType::META_DATA) {
        return "META_DATA";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::ACCELEROMETER) {
        return "ACCELEROMETER";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::MAGNETIC_FIELD) {
        return "MAGNETIC_FIELD";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::ORIENTATION) {
        return "ORIENTATION";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::GYROSCOPE) {
        return "GYROSCOPE";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::LIGHT) {
        return "LIGHT";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::PRESSURE) {
        return "PRESSURE";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::TEMPERATURE) {
        return "TEMPERATURE";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::PROXIMITY) {
        return "PROXIMITY";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::GRAVITY) {
        return "GRAVITY";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::LINEAR_ACCELERATION) {
        return "LINEAR_ACCELERATION";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::ROTATION_VECTOR) {
        return "ROTATION_VECTOR";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::RELATIVE_HUMIDITY) {
        return "RELATIVE_HUMIDITY";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::AMBIENT_TEMPERATURE) {
        return "AMBIENT_TEMPERATURE";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::MAGNETIC_FIELD_UNCALIBRATED) {
        return "MAGNETIC_FIELD_UNCALIBRATED";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::GAME_ROTATION_VECTOR) {
        return "GAME_ROTATION_VECTOR";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::GYROSCOPE_UNCALIBRATED) {
        return "GYROSCOPE_UNCALIBRATED";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::SIGNIFICANT_MOTION) {
        return "SIGNIFICANT_MOTION";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::STEP_DETECTOR) {
        return "STEP_DETECTOR";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::STEP_COUNTER) {
        return "STEP_COUNTER";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::GEOMAGNETIC_ROTATION_VECTOR) {
        return "GEOMAGNETIC_ROTATION_VECTOR";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::HEART_RATE) {
        return "HEART_RATE";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::TILT_DETECTOR) {
        return "TILT_DETECTOR";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::WAKE_GESTURE) {
        return "WAKE_GESTURE";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::GLANCE_GESTURE) {
        return "GLANCE_GESTURE";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::PICK_UP_GESTURE) {
        return "PICK_UP_GESTURE";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::WRIST_TILT_GESTURE) {
        return "WRIST_TILT_GESTURE";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::DEVICE_ORIENTATION) {
        return "DEVICE_ORIENTATION";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::POSE_6DOF) {
        return "POSE_6DOF";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::STATIONARY_DETECT) {
        return "STATIONARY_DETECT";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::MOTION_DETECT) {
        return "MOTION_DETECT";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::HEART_BEAT) {
        return "HEART_BEAT";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::DYNAMIC_SENSOR_META) {
        return "DYNAMIC_SENSOR_META";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::ADDITIONAL_INFO) {
        return "ADDITIONAL_INFO";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::LOW_LATENCY_OFFBODY_DETECT) {
        return "LOW_LATENCY_OFFBODY_DETECT";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::ACCELEROMETER_UNCALIBRATED) {
        return "ACCELEROMETER_UNCALIBRATED";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorType::DEVICE_PRIVATE_BASE) {
        return "DEVICE_PRIVATE_BASE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::sensors::V1_0::SensorType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::sensors::V1_0::SensorFlagBits>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::sensors::V1_0::SensorFlagBits> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::sensors::V1_0::SensorFlagBits::WAKE_UP) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::SensorFlagBits::WAKE_UP)) {
        os += (first ? "" : " | ");
        os += "WAKE_UP";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorFlagBits::WAKE_UP;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorFlagBits::CONTINUOUS_MODE) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::SensorFlagBits::CONTINUOUS_MODE)) {
        os += (first ? "" : " | ");
        os += "CONTINUOUS_MODE";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorFlagBits::CONTINUOUS_MODE;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorFlagBits::ON_CHANGE_MODE) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::SensorFlagBits::ON_CHANGE_MODE)) {
        os += (first ? "" : " | ");
        os += "ON_CHANGE_MODE";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorFlagBits::ON_CHANGE_MODE;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorFlagBits::ONE_SHOT_MODE) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::SensorFlagBits::ONE_SHOT_MODE)) {
        os += (first ? "" : " | ");
        os += "ONE_SHOT_MODE";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorFlagBits::ONE_SHOT_MODE;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorFlagBits::SPECIAL_REPORTING_MODE) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::SensorFlagBits::SPECIAL_REPORTING_MODE)) {
        os += (first ? "" : " | ");
        os += "SPECIAL_REPORTING_MODE";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorFlagBits::SPECIAL_REPORTING_MODE;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorFlagBits::DATA_INJECTION) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::SensorFlagBits::DATA_INJECTION)) {
        os += (first ? "" : " | ");
        os += "DATA_INJECTION";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorFlagBits::DATA_INJECTION;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorFlagBits::DYNAMIC_SENSOR) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::SensorFlagBits::DYNAMIC_SENSOR)) {
        os += (first ? "" : " | ");
        os += "DYNAMIC_SENSOR";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorFlagBits::DYNAMIC_SENSOR;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorFlagBits::ADDITIONAL_INFO) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::SensorFlagBits::ADDITIONAL_INFO)) {
        os += (first ? "" : " | ");
        os += "ADDITIONAL_INFO";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorFlagBits::ADDITIONAL_INFO;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorFlagBits::DIRECT_CHANNEL_ASHMEM) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::SensorFlagBits::DIRECT_CHANNEL_ASHMEM)) {
        os += (first ? "" : " | ");
        os += "DIRECT_CHANNEL_ASHMEM";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorFlagBits::DIRECT_CHANNEL_ASHMEM;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorFlagBits::DIRECT_CHANNEL_GRALLOC) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::SensorFlagBits::DIRECT_CHANNEL_GRALLOC)) {
        os += (first ? "" : " | ");
        os += "DIRECT_CHANNEL_GRALLOC";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorFlagBits::DIRECT_CHANNEL_GRALLOC;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorFlagBits::MASK_REPORTING_MODE) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::SensorFlagBits::MASK_REPORTING_MODE)) {
        os += (first ? "" : " | ");
        os += "MASK_REPORTING_MODE";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorFlagBits::MASK_REPORTING_MODE;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorFlagBits::MASK_DIRECT_REPORT) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::SensorFlagBits::MASK_DIRECT_REPORT)) {
        os += (first ? "" : " | ");
        os += "MASK_DIRECT_REPORT";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorFlagBits::MASK_DIRECT_REPORT;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorFlagBits::MASK_DIRECT_CHANNEL) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::SensorFlagBits::MASK_DIRECT_CHANNEL)) {
        os += (first ? "" : " | ");
        os += "MASK_DIRECT_CHANNEL";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorFlagBits::MASK_DIRECT_CHANNEL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::sensors::V1_0::SensorFlagBits o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::sensors::V1_0::SensorFlagBits::WAKE_UP) {
        return "WAKE_UP";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorFlagBits::CONTINUOUS_MODE) {
        return "CONTINUOUS_MODE";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorFlagBits::ON_CHANGE_MODE) {
        return "ON_CHANGE_MODE";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorFlagBits::ONE_SHOT_MODE) {
        return "ONE_SHOT_MODE";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorFlagBits::SPECIAL_REPORTING_MODE) {
        return "SPECIAL_REPORTING_MODE";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorFlagBits::DATA_INJECTION) {
        return "DATA_INJECTION";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorFlagBits::DYNAMIC_SENSOR) {
        return "DYNAMIC_SENSOR";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorFlagBits::ADDITIONAL_INFO) {
        return "ADDITIONAL_INFO";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorFlagBits::DIRECT_CHANNEL_ASHMEM) {
        return "DIRECT_CHANNEL_ASHMEM";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorFlagBits::DIRECT_CHANNEL_GRALLOC) {
        return "DIRECT_CHANNEL_GRALLOC";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorFlagBits::MASK_REPORTING_MODE) {
        return "MASK_REPORTING_MODE";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorFlagBits::MASK_DIRECT_REPORT) {
        return "MASK_DIRECT_REPORT";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorFlagBits::MASK_DIRECT_CHANNEL) {
        return "MASK_DIRECT_CHANNEL";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::sensors::V1_0::SensorFlagBits o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::sensors::V1_0::SensorFlagShift>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::sensors::V1_0::SensorFlagShift> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::sensors::V1_0::SensorFlagShift::REPORTING_MODE) == static_cast<uint8_t>(::android::hardware::sensors::V1_0::SensorFlagShift::REPORTING_MODE)) {
        os += (first ? "" : " | ");
        os += "REPORTING_MODE";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorFlagShift::REPORTING_MODE;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorFlagShift::DATA_INJECTION) == static_cast<uint8_t>(::android::hardware::sensors::V1_0::SensorFlagShift::DATA_INJECTION)) {
        os += (first ? "" : " | ");
        os += "DATA_INJECTION";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorFlagShift::DATA_INJECTION;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorFlagShift::DYNAMIC_SENSOR) == static_cast<uint8_t>(::android::hardware::sensors::V1_0::SensorFlagShift::DYNAMIC_SENSOR)) {
        os += (first ? "" : " | ");
        os += "DYNAMIC_SENSOR";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorFlagShift::DYNAMIC_SENSOR;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorFlagShift::ADDITIONAL_INFO) == static_cast<uint8_t>(::android::hardware::sensors::V1_0::SensorFlagShift::ADDITIONAL_INFO)) {
        os += (first ? "" : " | ");
        os += "ADDITIONAL_INFO";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorFlagShift::ADDITIONAL_INFO;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorFlagShift::DIRECT_REPORT) == static_cast<uint8_t>(::android::hardware::sensors::V1_0::SensorFlagShift::DIRECT_REPORT)) {
        os += (first ? "" : " | ");
        os += "DIRECT_REPORT";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorFlagShift::DIRECT_REPORT;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorFlagShift::DIRECT_CHANNEL) == static_cast<uint8_t>(::android::hardware::sensors::V1_0::SensorFlagShift::DIRECT_CHANNEL)) {
        os += (first ? "" : " | ");
        os += "DIRECT_CHANNEL";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorFlagShift::DIRECT_CHANNEL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::sensors::V1_0::SensorFlagShift o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::sensors::V1_0::SensorFlagShift::REPORTING_MODE) {
        return "REPORTING_MODE";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorFlagShift::DATA_INJECTION) {
        return "DATA_INJECTION";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorFlagShift::DYNAMIC_SENSOR) {
        return "DYNAMIC_SENSOR";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorFlagShift::ADDITIONAL_INFO) {
        return "ADDITIONAL_INFO";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorFlagShift::DIRECT_REPORT) {
        return "DIRECT_REPORT";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorFlagShift::DIRECT_CHANNEL) {
        return "DIRECT_CHANNEL";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::sensors::V1_0::SensorFlagShift o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::sensors::V1_0::SensorInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".sensorHandle = ";
    os += ::android::hardware::toString(o.sensorHandle);
    os += ", .name = ";
    os += ::android::hardware::toString(o.name);
    os += ", .vendor = ";
    os += ::android::hardware::toString(o.vendor);
    os += ", .version = ";
    os += ::android::hardware::toString(o.version);
    os += ", .type = ";
    os += ::android::hardware::sensors::V1_0::toString(o.type);
    os += ", .typeAsString = ";
    os += ::android::hardware::toString(o.typeAsString);
    os += ", .maxRange = ";
    os += ::android::hardware::toString(o.maxRange);
    os += ", .resolution = ";
    os += ::android::hardware::toString(o.resolution);
    os += ", .power = ";
    os += ::android::hardware::toString(o.power);
    os += ", .minDelay = ";
    os += ::android::hardware::toString(o.minDelay);
    os += ", .fifoReservedEventCount = ";
    os += ::android::hardware::toString(o.fifoReservedEventCount);
    os += ", .fifoMaxEventCount = ";
    os += ::android::hardware::toString(o.fifoMaxEventCount);
    os += ", .requiredPermission = ";
    os += ::android::hardware::toString(o.requiredPermission);
    os += ", .maxDelay = ";
    os += ::android::hardware::toString(o.maxDelay);
    os += ", .flags = ";
    os += ::android::hardware::sensors::V1_0::toString<::android::hardware::sensors::V1_0::SensorFlagBits>(o.flags);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::sensors::V1_0::SensorInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::sensors::V1_0::SensorInfo& lhs, const ::android::hardware::sensors::V1_0::SensorInfo& rhs) {
    if (lhs.sensorHandle != rhs.sensorHandle) {
        return false;
    }
    if (lhs.name != rhs.name) {
        return false;
    }
    if (lhs.vendor != rhs.vendor) {
        return false;
    }
    if (lhs.version != rhs.version) {
        return false;
    }
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.typeAsString != rhs.typeAsString) {
        return false;
    }
    if (lhs.maxRange != rhs.maxRange) {
        return false;
    }
    if (lhs.resolution != rhs.resolution) {
        return false;
    }
    if (lhs.power != rhs.power) {
        return false;
    }
    if (lhs.minDelay != rhs.minDelay) {
        return false;
    }
    if (lhs.fifoReservedEventCount != rhs.fifoReservedEventCount) {
        return false;
    }
    if (lhs.fifoMaxEventCount != rhs.fifoMaxEventCount) {
        return false;
    }
    if (lhs.requiredPermission != rhs.requiredPermission) {
        return false;
    }
    if (lhs.maxDelay != rhs.maxDelay) {
        return false;
    }
    if (lhs.flags != rhs.flags) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::sensors::V1_0::SensorInfo& lhs, const ::android::hardware::sensors::V1_0::SensorInfo& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::sensors::V1_0::SensorStatus>(int8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::sensors::V1_0::SensorStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::sensors::V1_0::SensorStatus::NO_CONTACT) == static_cast<int8_t>(::android::hardware::sensors::V1_0::SensorStatus::NO_CONTACT)) {
        os += (first ? "" : " | ");
        os += "NO_CONTACT";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorStatus::NO_CONTACT;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorStatus::UNRELIABLE) == static_cast<int8_t>(::android::hardware::sensors::V1_0::SensorStatus::UNRELIABLE)) {
        os += (first ? "" : " | ");
        os += "UNRELIABLE";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorStatus::UNRELIABLE;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorStatus::ACCURACY_LOW) == static_cast<int8_t>(::android::hardware::sensors::V1_0::SensorStatus::ACCURACY_LOW)) {
        os += (first ? "" : " | ");
        os += "ACCURACY_LOW";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorStatus::ACCURACY_LOW;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorStatus::ACCURACY_MEDIUM) == static_cast<int8_t>(::android::hardware::sensors::V1_0::SensorStatus::ACCURACY_MEDIUM)) {
        os += (first ? "" : " | ");
        os += "ACCURACY_MEDIUM";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorStatus::ACCURACY_MEDIUM;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorStatus::ACCURACY_HIGH) == static_cast<int8_t>(::android::hardware::sensors::V1_0::SensorStatus::ACCURACY_HIGH)) {
        os += (first ? "" : " | ");
        os += "ACCURACY_HIGH";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorStatus::ACCURACY_HIGH;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::sensors::V1_0::SensorStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::sensors::V1_0::SensorStatus::NO_CONTACT) {
        return "NO_CONTACT";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorStatus::UNRELIABLE) {
        return "UNRELIABLE";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorStatus::ACCURACY_LOW) {
        return "ACCURACY_LOW";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorStatus::ACCURACY_MEDIUM) {
        return "ACCURACY_MEDIUM";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorStatus::ACCURACY_HIGH) {
        return "ACCURACY_HIGH";
    }
    std::string os;
    os += toHexString(static_cast<int8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::sensors::V1_0::SensorStatus o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::sensors::V1_0::Vec3& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".x = ";
    os += ::android::hardware::toString(o.x);
    os += ", .y = ";
    os += ::android::hardware::toString(o.y);
    os += ", .z = ";
    os += ::android::hardware::toString(o.z);
    os += ", .status = ";
    os += ::android::hardware::sensors::V1_0::toString(o.status);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::sensors::V1_0::Vec3& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::sensors::V1_0::Vec3& lhs, const ::android::hardware::sensors::V1_0::Vec3& rhs) {
    if (lhs.x != rhs.x) {
        return false;
    }
    if (lhs.y != rhs.y) {
        return false;
    }
    if (lhs.z != rhs.z) {
        return false;
    }
    if (lhs.status != rhs.status) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::sensors::V1_0::Vec3& lhs, const ::android::hardware::sensors::V1_0::Vec3& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::sensors::V1_0::Vec4& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".x = ";
    os += ::android::hardware::toString(o.x);
    os += ", .y = ";
    os += ::android::hardware::toString(o.y);
    os += ", .z = ";
    os += ::android::hardware::toString(o.z);
    os += ", .w = ";
    os += ::android::hardware::toString(o.w);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::sensors::V1_0::Vec4& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::sensors::V1_0::Vec4& lhs, const ::android::hardware::sensors::V1_0::Vec4& rhs) {
    if (lhs.x != rhs.x) {
        return false;
    }
    if (lhs.y != rhs.y) {
        return false;
    }
    if (lhs.z != rhs.z) {
        return false;
    }
    if (lhs.w != rhs.w) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::sensors::V1_0::Vec4& lhs, const ::android::hardware::sensors::V1_0::Vec4& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::sensors::V1_0::Uncal& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".x = ";
    os += ::android::hardware::toString(o.x);
    os += ", .y = ";
    os += ::android::hardware::toString(o.y);
    os += ", .z = ";
    os += ::android::hardware::toString(o.z);
    os += ", .x_bias = ";
    os += ::android::hardware::toString(o.x_bias);
    os += ", .y_bias = ";
    os += ::android::hardware::toString(o.y_bias);
    os += ", .z_bias = ";
    os += ::android::hardware::toString(o.z_bias);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::sensors::V1_0::Uncal& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::sensors::V1_0::Uncal& lhs, const ::android::hardware::sensors::V1_0::Uncal& rhs) {
    if (lhs.x != rhs.x) {
        return false;
    }
    if (lhs.y != rhs.y) {
        return false;
    }
    if (lhs.z != rhs.z) {
        return false;
    }
    if (lhs.x_bias != rhs.x_bias) {
        return false;
    }
    if (lhs.y_bias != rhs.y_bias) {
        return false;
    }
    if (lhs.z_bias != rhs.z_bias) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::sensors::V1_0::Uncal& lhs, const ::android::hardware::sensors::V1_0::Uncal& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::sensors::V1_0::HeartRate& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".bpm = ";
    os += ::android::hardware::toString(o.bpm);
    os += ", .status = ";
    os += ::android::hardware::sensors::V1_0::toString(o.status);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::sensors::V1_0::HeartRate& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::sensors::V1_0::HeartRate& lhs, const ::android::hardware::sensors::V1_0::HeartRate& rhs) {
    if (lhs.bpm != rhs.bpm) {
        return false;
    }
    if (lhs.status != rhs.status) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::sensors::V1_0::HeartRate& lhs, const ::android::hardware::sensors::V1_0::HeartRate& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::sensors::V1_0::MetaDataEventType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::sensors::V1_0::MetaDataEventType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::sensors::V1_0::MetaDataEventType::META_DATA_FLUSH_COMPLETE) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::MetaDataEventType::META_DATA_FLUSH_COMPLETE)) {
        os += (first ? "" : " | ");
        os += "META_DATA_FLUSH_COMPLETE";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::MetaDataEventType::META_DATA_FLUSH_COMPLETE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::sensors::V1_0::MetaDataEventType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::sensors::V1_0::MetaDataEventType::META_DATA_FLUSH_COMPLETE) {
        return "META_DATA_FLUSH_COMPLETE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::sensors::V1_0::MetaDataEventType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::sensors::V1_0::MetaData& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".what = ";
    os += ::android::hardware::sensors::V1_0::toString(o.what);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::sensors::V1_0::MetaData& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::sensors::V1_0::MetaData& lhs, const ::android::hardware::sensors::V1_0::MetaData& rhs) {
    if (lhs.what != rhs.what) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::sensors::V1_0::MetaData& lhs, const ::android::hardware::sensors::V1_0::MetaData& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::sensors::V1_0::DynamicSensorInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".connected = ";
    os += ::android::hardware::toString(o.connected);
    os += ", .sensorHandle = ";
    os += ::android::hardware::toString(o.sensorHandle);
    os += ", .uuid = ";
    os += ::android::hardware::toString(o.uuid);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::sensors::V1_0::DynamicSensorInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::sensors::V1_0::DynamicSensorInfo& lhs, const ::android::hardware::sensors::V1_0::DynamicSensorInfo& rhs) {
    if (lhs.connected != rhs.connected) {
        return false;
    }
    if (lhs.sensorHandle != rhs.sensorHandle) {
        return false;
    }
    if (lhs.uuid != rhs.uuid) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::sensors::V1_0::DynamicSensorInfo& lhs, const ::android::hardware::sensors::V1_0::DynamicSensorInfo& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::sensors::V1_0::AdditionalInfoType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::sensors::V1_0::AdditionalInfoType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_BEGIN) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_BEGIN)) {
        os += (first ? "" : " | ");
        os += "AINFO_BEGIN";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_BEGIN;
    }
    if ((o & ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_END) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_END)) {
        os += (first ? "" : " | ");
        os += "AINFO_END";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_END;
    }
    if ((o & ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_UNTRACKED_DELAY) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_UNTRACKED_DELAY)) {
        os += (first ? "" : " | ");
        os += "AINFO_UNTRACKED_DELAY";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_UNTRACKED_DELAY;
    }
    if ((o & ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_INTERNAL_TEMPERATURE) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_INTERNAL_TEMPERATURE)) {
        os += (first ? "" : " | ");
        os += "AINFO_INTERNAL_TEMPERATURE";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_INTERNAL_TEMPERATURE;
    }
    if ((o & ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_VEC3_CALIBRATION) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_VEC3_CALIBRATION)) {
        os += (first ? "" : " | ");
        os += "AINFO_VEC3_CALIBRATION";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_VEC3_CALIBRATION;
    }
    if ((o & ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_SENSOR_PLACEMENT) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_SENSOR_PLACEMENT)) {
        os += (first ? "" : " | ");
        os += "AINFO_SENSOR_PLACEMENT";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_SENSOR_PLACEMENT;
    }
    if ((o & ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_SAMPLING) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_SAMPLING)) {
        os += (first ? "" : " | ");
        os += "AINFO_SAMPLING";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_SAMPLING;
    }
    if ((o & ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_NOISE) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_NOISE)) {
        os += (first ? "" : " | ");
        os += "AINFO_CHANNEL_NOISE";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_NOISE;
    }
    if ((o & ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_SAMPLER) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_SAMPLER)) {
        os += (first ? "" : " | ");
        os += "AINFO_CHANNEL_SAMPLER";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_SAMPLER;
    }
    if ((o & ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_FILTER) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_FILTER)) {
        os += (first ? "" : " | ");
        os += "AINFO_CHANNEL_FILTER";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_FILTER;
    }
    if ((o & ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_LINEAR_TRANSFORM) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_LINEAR_TRANSFORM)) {
        os += (first ? "" : " | ");
        os += "AINFO_CHANNEL_LINEAR_TRANSFORM";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_LINEAR_TRANSFORM;
    }
    if ((o & ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_NONLINEAR_MAP) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_NONLINEAR_MAP)) {
        os += (first ? "" : " | ");
        os += "AINFO_CHANNEL_NONLINEAR_MAP";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_NONLINEAR_MAP;
    }
    if ((o & ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_RESAMPLER) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_RESAMPLER)) {
        os += (first ? "" : " | ");
        os += "AINFO_CHANNEL_RESAMPLER";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_RESAMPLER;
    }
    if ((o & ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_LOCAL_GEOMAGNETIC_FIELD) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_LOCAL_GEOMAGNETIC_FIELD)) {
        os += (first ? "" : " | ");
        os += "AINFO_LOCAL_GEOMAGNETIC_FIELD";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_LOCAL_GEOMAGNETIC_FIELD;
    }
    if ((o & ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_LOCAL_GRAVITY) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_LOCAL_GRAVITY)) {
        os += (first ? "" : " | ");
        os += "AINFO_LOCAL_GRAVITY";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_LOCAL_GRAVITY;
    }
    if ((o & ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_DOCK_STATE) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_DOCK_STATE)) {
        os += (first ? "" : " | ");
        os += "AINFO_DOCK_STATE";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_DOCK_STATE;
    }
    if ((o & ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_HIGH_PERFORMANCE_MODE) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_HIGH_PERFORMANCE_MODE)) {
        os += (first ? "" : " | ");
        os += "AINFO_HIGH_PERFORMANCE_MODE";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_HIGH_PERFORMANCE_MODE;
    }
    if ((o & ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_MAGNETIC_FIELD_CALIBRATION) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_MAGNETIC_FIELD_CALIBRATION)) {
        os += (first ? "" : " | ");
        os += "AINFO_MAGNETIC_FIELD_CALIBRATION";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_MAGNETIC_FIELD_CALIBRATION;
    }
    if ((o & ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CUSTOM_START) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CUSTOM_START)) {
        os += (first ? "" : " | ");
        os += "AINFO_CUSTOM_START";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CUSTOM_START;
    }
    if ((o & ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_DEBUGGING_START) == static_cast<uint32_t>(::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_DEBUGGING_START)) {
        os += (first ? "" : " | ");
        os += "AINFO_DEBUGGING_START";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_DEBUGGING_START;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::sensors::V1_0::AdditionalInfoType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_BEGIN) {
        return "AINFO_BEGIN";
    }
    if (o == ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_END) {
        return "AINFO_END";
    }
    if (o == ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_UNTRACKED_DELAY) {
        return "AINFO_UNTRACKED_DELAY";
    }
    if (o == ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_INTERNAL_TEMPERATURE) {
        return "AINFO_INTERNAL_TEMPERATURE";
    }
    if (o == ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_VEC3_CALIBRATION) {
        return "AINFO_VEC3_CALIBRATION";
    }
    if (o == ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_SENSOR_PLACEMENT) {
        return "AINFO_SENSOR_PLACEMENT";
    }
    if (o == ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_SAMPLING) {
        return "AINFO_SAMPLING";
    }
    if (o == ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_NOISE) {
        return "AINFO_CHANNEL_NOISE";
    }
    if (o == ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_SAMPLER) {
        return "AINFO_CHANNEL_SAMPLER";
    }
    if (o == ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_FILTER) {
        return "AINFO_CHANNEL_FILTER";
    }
    if (o == ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_LINEAR_TRANSFORM) {
        return "AINFO_CHANNEL_LINEAR_TRANSFORM";
    }
    if (o == ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_NONLINEAR_MAP) {
        return "AINFO_CHANNEL_NONLINEAR_MAP";
    }
    if (o == ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_RESAMPLER) {
        return "AINFO_CHANNEL_RESAMPLER";
    }
    if (o == ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_LOCAL_GEOMAGNETIC_FIELD) {
        return "AINFO_LOCAL_GEOMAGNETIC_FIELD";
    }
    if (o == ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_LOCAL_GRAVITY) {
        return "AINFO_LOCAL_GRAVITY";
    }
    if (o == ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_DOCK_STATE) {
        return "AINFO_DOCK_STATE";
    }
    if (o == ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_HIGH_PERFORMANCE_MODE) {
        return "AINFO_HIGH_PERFORMANCE_MODE";
    }
    if (o == ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_MAGNETIC_FIELD_CALIBRATION) {
        return "AINFO_MAGNETIC_FIELD_CALIBRATION";
    }
    if (o == ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CUSTOM_START) {
        return "AINFO_CUSTOM_START";
    }
    if (o == ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_DEBUGGING_START) {
        return "AINFO_DEBUGGING_START";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::sensors::V1_0::AdditionalInfoType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::sensors::V1_0::AdditionalInfo::Payload& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".data_int32 = ";
    os += ::android::hardware::toString(o.data_int32);
    os += ", .data_float = ";
    os += ::android::hardware::toString(o.data_float);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::sensors::V1_0::AdditionalInfo::Payload& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for Payload

static inline std::string toString(const ::android::hardware::sensors::V1_0::AdditionalInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::sensors::V1_0::toString(o.type);
    os += ", .serial = ";
    os += ::android::hardware::toString(o.serial);
    os += ", .u = ";
    os += ::android::hardware::sensors::V1_0::toString(o.u);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::sensors::V1_0::AdditionalInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for AdditionalInfo

static inline std::string toString(const ::android::hardware::sensors::V1_0::EventPayload& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".vec3 = ";
    os += ::android::hardware::sensors::V1_0::toString(o.vec3);
    os += ", .vec4 = ";
    os += ::android::hardware::sensors::V1_0::toString(o.vec4);
    os += ", .uncal = ";
    os += ::android::hardware::sensors::V1_0::toString(o.uncal);
    os += ", .meta = ";
    os += ::android::hardware::sensors::V1_0::toString(o.meta);
    os += ", .scalar = ";
    os += ::android::hardware::toString(o.scalar);
    os += ", .stepCount = ";
    os += ::android::hardware::toString(o.stepCount);
    os += ", .heartRate = ";
    os += ::android::hardware::sensors::V1_0::toString(o.heartRate);
    os += ", .pose6DOF = ";
    os += ::android::hardware::toString(o.pose6DOF);
    os += ", .dynamic = ";
    os += ::android::hardware::sensors::V1_0::toString(o.dynamic);
    os += ", .additional = ";
    os += ::android::hardware::sensors::V1_0::toString(o.additional);
    os += ", .data = ";
    os += ::android::hardware::toString(o.data);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::sensors::V1_0::EventPayload& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for EventPayload

static inline std::string toString(const ::android::hardware::sensors::V1_0::Event& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".timestamp = ";
    os += ::android::hardware::toString(o.timestamp);
    os += ", .sensorHandle = ";
    os += ::android::hardware::toString(o.sensorHandle);
    os += ", .sensorType = ";
    os += ::android::hardware::sensors::V1_0::toString(o.sensorType);
    os += ", .u = ";
    os += ::android::hardware::sensors::V1_0::toString(o.u);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::sensors::V1_0::Event& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for Event

template<>
inline std::string toString<::android::hardware::sensors::V1_0::RateLevel>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::sensors::V1_0::RateLevel> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::sensors::V1_0::RateLevel::STOP) == static_cast<int32_t>(::android::hardware::sensors::V1_0::RateLevel::STOP)) {
        os += (first ? "" : " | ");
        os += "STOP";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::RateLevel::STOP;
    }
    if ((o & ::android::hardware::sensors::V1_0::RateLevel::NORMAL) == static_cast<int32_t>(::android::hardware::sensors::V1_0::RateLevel::NORMAL)) {
        os += (first ? "" : " | ");
        os += "NORMAL";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::RateLevel::NORMAL;
    }
    if ((o & ::android::hardware::sensors::V1_0::RateLevel::FAST) == static_cast<int32_t>(::android::hardware::sensors::V1_0::RateLevel::FAST)) {
        os += (first ? "" : " | ");
        os += "FAST";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::RateLevel::FAST;
    }
    if ((o & ::android::hardware::sensors::V1_0::RateLevel::VERY_FAST) == static_cast<int32_t>(::android::hardware::sensors::V1_0::RateLevel::VERY_FAST)) {
        os += (first ? "" : " | ");
        os += "VERY_FAST";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::RateLevel::VERY_FAST;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::sensors::V1_0::RateLevel o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::sensors::V1_0::RateLevel::STOP) {
        return "STOP";
    }
    if (o == ::android::hardware::sensors::V1_0::RateLevel::NORMAL) {
        return "NORMAL";
    }
    if (o == ::android::hardware::sensors::V1_0::RateLevel::FAST) {
        return "FAST";
    }
    if (o == ::android::hardware::sensors::V1_0::RateLevel::VERY_FAST) {
        return "VERY_FAST";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::sensors::V1_0::RateLevel o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::sensors::V1_0::SharedMemType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::sensors::V1_0::SharedMemType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::sensors::V1_0::SharedMemType::ASHMEM) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SharedMemType::ASHMEM)) {
        os += (first ? "" : " | ");
        os += "ASHMEM";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SharedMemType::ASHMEM;
    }
    if ((o & ::android::hardware::sensors::V1_0::SharedMemType::GRALLOC) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SharedMemType::GRALLOC)) {
        os += (first ? "" : " | ");
        os += "GRALLOC";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SharedMemType::GRALLOC;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::sensors::V1_0::SharedMemType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::sensors::V1_0::SharedMemType::ASHMEM) {
        return "ASHMEM";
    }
    if (o == ::android::hardware::sensors::V1_0::SharedMemType::GRALLOC) {
        return "GRALLOC";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::sensors::V1_0::SharedMemType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::sensors::V1_0::SharedMemFormat>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::sensors::V1_0::SharedMemFormat> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::sensors::V1_0::SharedMemFormat::SENSORS_EVENT) == static_cast<int32_t>(::android::hardware::sensors::V1_0::SharedMemFormat::SENSORS_EVENT)) {
        os += (first ? "" : " | ");
        os += "SENSORS_EVENT";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SharedMemFormat::SENSORS_EVENT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::sensors::V1_0::SharedMemFormat o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::sensors::V1_0::SharedMemFormat::SENSORS_EVENT) {
        return "SENSORS_EVENT";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::sensors::V1_0::SharedMemFormat o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::sensors::V1_0::SensorsEventFormatOffset>(uint16_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::sensors::V1_0::SensorsEventFormatOffset> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::SIZE_FIELD) == static_cast<uint16_t>(::android::hardware::sensors::V1_0::SensorsEventFormatOffset::SIZE_FIELD)) {
        os += (first ? "" : " | ");
        os += "SIZE_FIELD";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::SIZE_FIELD;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::REPORT_TOKEN) == static_cast<uint16_t>(::android::hardware::sensors::V1_0::SensorsEventFormatOffset::REPORT_TOKEN)) {
        os += (first ? "" : " | ");
        os += "REPORT_TOKEN";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::REPORT_TOKEN;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::SENSOR_TYPE) == static_cast<uint16_t>(::android::hardware::sensors::V1_0::SensorsEventFormatOffset::SENSOR_TYPE)) {
        os += (first ? "" : " | ");
        os += "SENSOR_TYPE";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::SENSOR_TYPE;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::ATOMIC_COUNTER) == static_cast<uint16_t>(::android::hardware::sensors::V1_0::SensorsEventFormatOffset::ATOMIC_COUNTER)) {
        os += (first ? "" : " | ");
        os += "ATOMIC_COUNTER";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::ATOMIC_COUNTER;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::TIMESTAMP) == static_cast<uint16_t>(::android::hardware::sensors::V1_0::SensorsEventFormatOffset::TIMESTAMP)) {
        os += (first ? "" : " | ");
        os += "TIMESTAMP";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::TIMESTAMP;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::DATA) == static_cast<uint16_t>(::android::hardware::sensors::V1_0::SensorsEventFormatOffset::DATA)) {
        os += (first ? "" : " | ");
        os += "DATA";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::DATA;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::RESERVED) == static_cast<uint16_t>(::android::hardware::sensors::V1_0::SensorsEventFormatOffset::RESERVED)) {
        os += (first ? "" : " | ");
        os += "RESERVED";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::RESERVED;
    }
    if ((o & ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::TOTAL_LENGTH) == static_cast<uint16_t>(::android::hardware::sensors::V1_0::SensorsEventFormatOffset::TOTAL_LENGTH)) {
        os += (first ? "" : " | ");
        os += "TOTAL_LENGTH";
        first = false;
        flipped |= ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::TOTAL_LENGTH;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::sensors::V1_0::SensorsEventFormatOffset o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::SIZE_FIELD) {
        return "SIZE_FIELD";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::REPORT_TOKEN) {
        return "REPORT_TOKEN";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::SENSOR_TYPE) {
        return "SENSOR_TYPE";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::ATOMIC_COUNTER) {
        return "ATOMIC_COUNTER";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::TIMESTAMP) {
        return "TIMESTAMP";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::DATA) {
        return "DATA";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::RESERVED) {
        return "RESERVED";
    }
    if (o == ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::TOTAL_LENGTH) {
        return "TOTAL_LENGTH";
    }
    std::string os;
    os += toHexString(static_cast<uint16_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::sensors::V1_0::SensorsEventFormatOffset o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::sensors::V1_0::SharedMemInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::sensors::V1_0::toString(o.type);
    os += ", .format = ";
    os += ::android::hardware::sensors::V1_0::toString(o.format);
    os += ", .size = ";
    os += ::android::hardware::toString(o.size);
    os += ", .memoryHandle = ";
    os += ::android::hardware::toString(o.memoryHandle);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::sensors::V1_0::SharedMemInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for SharedMemInfo


}  // namespace V1_0
}  // namespace sensors
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::sensors::V1_0::Result, 5> hidl_enum_values<::android::hardware::sensors::V1_0::Result> = {
    ::android::hardware::sensors::V1_0::Result::OK,
    ::android::hardware::sensors::V1_0::Result::PERMISSION_DENIED,
    ::android::hardware::sensors::V1_0::Result::NO_MEMORY,
    ::android::hardware::sensors::V1_0::Result::BAD_VALUE,
    ::android::hardware::sensors::V1_0::Result::INVALID_OPERATION,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::sensors::V1_0::OperationMode, 2> hidl_enum_values<::android::hardware::sensors::V1_0::OperationMode> = {
    ::android::hardware::sensors::V1_0::OperationMode::NORMAL,
    ::android::hardware::sensors::V1_0::OperationMode::DATA_INJECTION,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::sensors::V1_0::SensorType, 37> hidl_enum_values<::android::hardware::sensors::V1_0::SensorType> = {
    ::android::hardware::sensors::V1_0::SensorType::META_DATA,
    ::android::hardware::sensors::V1_0::SensorType::ACCELEROMETER,
    ::android::hardware::sensors::V1_0::SensorType::MAGNETIC_FIELD,
    ::android::hardware::sensors::V1_0::SensorType::ORIENTATION,
    ::android::hardware::sensors::V1_0::SensorType::GYROSCOPE,
    ::android::hardware::sensors::V1_0::SensorType::LIGHT,
    ::android::hardware::sensors::V1_0::SensorType::PRESSURE,
    ::android::hardware::sensors::V1_0::SensorType::TEMPERATURE,
    ::android::hardware::sensors::V1_0::SensorType::PROXIMITY,
    ::android::hardware::sensors::V1_0::SensorType::GRAVITY,
    ::android::hardware::sensors::V1_0::SensorType::LINEAR_ACCELERATION,
    ::android::hardware::sensors::V1_0::SensorType::ROTATION_VECTOR,
    ::android::hardware::sensors::V1_0::SensorType::RELATIVE_HUMIDITY,
    ::android::hardware::sensors::V1_0::SensorType::AMBIENT_TEMPERATURE,
    ::android::hardware::sensors::V1_0::SensorType::MAGNETIC_FIELD_UNCALIBRATED,
    ::android::hardware::sensors::V1_0::SensorType::GAME_ROTATION_VECTOR,
    ::android::hardware::sensors::V1_0::SensorType::GYROSCOPE_UNCALIBRATED,
    ::android::hardware::sensors::V1_0::SensorType::SIGNIFICANT_MOTION,
    ::android::hardware::sensors::V1_0::SensorType::STEP_DETECTOR,
    ::android::hardware::sensors::V1_0::SensorType::STEP_COUNTER,
    ::android::hardware::sensors::V1_0::SensorType::GEOMAGNETIC_ROTATION_VECTOR,
    ::android::hardware::sensors::V1_0::SensorType::HEART_RATE,
    ::android::hardware::sensors::V1_0::SensorType::TILT_DETECTOR,
    ::android::hardware::sensors::V1_0::SensorType::WAKE_GESTURE,
    ::android::hardware::sensors::V1_0::SensorType::GLANCE_GESTURE,
    ::android::hardware::sensors::V1_0::SensorType::PICK_UP_GESTURE,
    ::android::hardware::sensors::V1_0::SensorType::WRIST_TILT_GESTURE,
    ::android::hardware::sensors::V1_0::SensorType::DEVICE_ORIENTATION,
    ::android::hardware::sensors::V1_0::SensorType::POSE_6DOF,
    ::android::hardware::sensors::V1_0::SensorType::STATIONARY_DETECT,
    ::android::hardware::sensors::V1_0::SensorType::MOTION_DETECT,
    ::android::hardware::sensors::V1_0::SensorType::HEART_BEAT,
    ::android::hardware::sensors::V1_0::SensorType::DYNAMIC_SENSOR_META,
    ::android::hardware::sensors::V1_0::SensorType::ADDITIONAL_INFO,
    ::android::hardware::sensors::V1_0::SensorType::LOW_LATENCY_OFFBODY_DETECT,
    ::android::hardware::sensors::V1_0::SensorType::ACCELEROMETER_UNCALIBRATED,
    ::android::hardware::sensors::V1_0::SensorType::DEVICE_PRIVATE_BASE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::sensors::V1_0::SensorFlagBits, 13> hidl_enum_values<::android::hardware::sensors::V1_0::SensorFlagBits> = {
    ::android::hardware::sensors::V1_0::SensorFlagBits::WAKE_UP,
    ::android::hardware::sensors::V1_0::SensorFlagBits::CONTINUOUS_MODE,
    ::android::hardware::sensors::V1_0::SensorFlagBits::ON_CHANGE_MODE,
    ::android::hardware::sensors::V1_0::SensorFlagBits::ONE_SHOT_MODE,
    ::android::hardware::sensors::V1_0::SensorFlagBits::SPECIAL_REPORTING_MODE,
    ::android::hardware::sensors::V1_0::SensorFlagBits::DATA_INJECTION,
    ::android::hardware::sensors::V1_0::SensorFlagBits::DYNAMIC_SENSOR,
    ::android::hardware::sensors::V1_0::SensorFlagBits::ADDITIONAL_INFO,
    ::android::hardware::sensors::V1_0::SensorFlagBits::DIRECT_CHANNEL_ASHMEM,
    ::android::hardware::sensors::V1_0::SensorFlagBits::DIRECT_CHANNEL_GRALLOC,
    ::android::hardware::sensors::V1_0::SensorFlagBits::MASK_REPORTING_MODE,
    ::android::hardware::sensors::V1_0::SensorFlagBits::MASK_DIRECT_REPORT,
    ::android::hardware::sensors::V1_0::SensorFlagBits::MASK_DIRECT_CHANNEL,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::sensors::V1_0::SensorFlagShift, 6> hidl_enum_values<::android::hardware::sensors::V1_0::SensorFlagShift> = {
    ::android::hardware::sensors::V1_0::SensorFlagShift::REPORTING_MODE,
    ::android::hardware::sensors::V1_0::SensorFlagShift::DATA_INJECTION,
    ::android::hardware::sensors::V1_0::SensorFlagShift::DYNAMIC_SENSOR,
    ::android::hardware::sensors::V1_0::SensorFlagShift::ADDITIONAL_INFO,
    ::android::hardware::sensors::V1_0::SensorFlagShift::DIRECT_REPORT,
    ::android::hardware::sensors::V1_0::SensorFlagShift::DIRECT_CHANNEL,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::sensors::V1_0::SensorStatus, 5> hidl_enum_values<::android::hardware::sensors::V1_0::SensorStatus> = {
    ::android::hardware::sensors::V1_0::SensorStatus::NO_CONTACT,
    ::android::hardware::sensors::V1_0::SensorStatus::UNRELIABLE,
    ::android::hardware::sensors::V1_0::SensorStatus::ACCURACY_LOW,
    ::android::hardware::sensors::V1_0::SensorStatus::ACCURACY_MEDIUM,
    ::android::hardware::sensors::V1_0::SensorStatus::ACCURACY_HIGH,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::sensors::V1_0::MetaDataEventType, 1> hidl_enum_values<::android::hardware::sensors::V1_0::MetaDataEventType> = {
    ::android::hardware::sensors::V1_0::MetaDataEventType::META_DATA_FLUSH_COMPLETE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::sensors::V1_0::AdditionalInfoType, 20> hidl_enum_values<::android::hardware::sensors::V1_0::AdditionalInfoType> = {
    ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_BEGIN,
    ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_END,
    ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_UNTRACKED_DELAY,
    ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_INTERNAL_TEMPERATURE,
    ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_VEC3_CALIBRATION,
    ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_SENSOR_PLACEMENT,
    ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_SAMPLING,
    ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_NOISE,
    ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_SAMPLER,
    ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_FILTER,
    ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_LINEAR_TRANSFORM,
    ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_NONLINEAR_MAP,
    ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CHANNEL_RESAMPLER,
    ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_LOCAL_GEOMAGNETIC_FIELD,
    ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_LOCAL_GRAVITY,
    ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_DOCK_STATE,
    ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_HIGH_PERFORMANCE_MODE,
    ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_MAGNETIC_FIELD_CALIBRATION,
    ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_CUSTOM_START,
    ::android::hardware::sensors::V1_0::AdditionalInfoType::AINFO_DEBUGGING_START,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::sensors::V1_0::RateLevel, 4> hidl_enum_values<::android::hardware::sensors::V1_0::RateLevel> = {
    ::android::hardware::sensors::V1_0::RateLevel::STOP,
    ::android::hardware::sensors::V1_0::RateLevel::NORMAL,
    ::android::hardware::sensors::V1_0::RateLevel::FAST,
    ::android::hardware::sensors::V1_0::RateLevel::VERY_FAST,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::sensors::V1_0::SharedMemType, 2> hidl_enum_values<::android::hardware::sensors::V1_0::SharedMemType> = {
    ::android::hardware::sensors::V1_0::SharedMemType::ASHMEM,
    ::android::hardware::sensors::V1_0::SharedMemType::GRALLOC,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::sensors::V1_0::SharedMemFormat, 1> hidl_enum_values<::android::hardware::sensors::V1_0::SharedMemFormat> = {
    ::android::hardware::sensors::V1_0::SharedMemFormat::SENSORS_EVENT,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android

namespace android {
namespace hardware {
namespace details {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template<> inline constexpr std::array<::android::hardware::sensors::V1_0::SensorsEventFormatOffset, 8> hidl_enum_values<::android::hardware::sensors::V1_0::SensorsEventFormatOffset> = {
    ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::SIZE_FIELD,
    ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::REPORT_TOKEN,
    ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::SENSOR_TYPE,
    ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::ATOMIC_COUNTER,
    ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::TIMESTAMP,
    ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::DATA,
    ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::RESERVED,
    ::android::hardware::sensors::V1_0::SensorsEventFormatOffset::TOTAL_LENGTH,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V1_0_TYPES_H
