#ifndef HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V2_1_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V2_1_TYPES_H

#include <android/hardware/sensors/1.0/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace sensors {
namespace V2_1 {

// Forward declaration for forward reference support:
enum class SensorType : int32_t;
struct Event;
struct SensorInfo;

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
    /**
     * HINGE_ANGLE
     * reporting-mode: on-change
     * wake-up sensor: yes
     *
     * A sensor of this type measures the angle, in degrees, between two
     * integral parts of the device. Movement of a hinge measured by this sensor
     * type is expected to alter the ways in which the user may interact with
     * the device, for example by unfolding or revealing a display.
     *
     * Sensor data is output using @1.0::EventPayload.scalar.
     *
     * Implement wake-up proximity sensor before implementing a non wake-up
     * proximity sensor.
     */
    HINGE_ANGLE = 36,
};

struct Event final {
    /**
     * Time measured in nanoseconds, in "elapsedRealtimeNano()'s" timebase.
     */
    int64_t timestamp __attribute__ ((aligned(8)));
    /**
     * sensor identifier
     */
    int32_t sensorHandle __attribute__ ((aligned(4)));
    ::android::hardware::sensors::V2_1::SensorType sensorType __attribute__ ((aligned(4)));
    /**
     * Union discriminated on sensorType
     */
    ::android::hardware::sensors::V1_0::EventPayload u __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::sensors::V2_1::Event, timestamp) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V2_1::Event, sensorHandle) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V2_1::Event, sensorType) == 12, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V2_1::Event, u) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::sensors::V2_1::Event) == 80, "wrong size");
static_assert(__alignof(::android::hardware::sensors::V2_1::Event) == 8, "wrong alignment");

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
    ::android::hardware::sensors::V2_1::SensorType type __attribute__ ((aligned(4)));
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

static_assert(offsetof(::android::hardware::sensors::V2_1::SensorInfo, sensorHandle) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V2_1::SensorInfo, name) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V2_1::SensorInfo, vendor) == 24, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V2_1::SensorInfo, version) == 40, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V2_1::SensorInfo, type) == 44, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V2_1::SensorInfo, typeAsString) == 48, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V2_1::SensorInfo, maxRange) == 64, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V2_1::SensorInfo, resolution) == 68, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V2_1::SensorInfo, power) == 72, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V2_1::SensorInfo, minDelay) == 76, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V2_1::SensorInfo, fifoReservedEventCount) == 80, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V2_1::SensorInfo, fifoMaxEventCount) == 84, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V2_1::SensorInfo, requiredPermission) == 88, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V2_1::SensorInfo, maxDelay) == 104, "wrong offset");
static_assert(offsetof(::android::hardware::sensors::V2_1::SensorInfo, flags) == 108, "wrong offset");
static_assert(sizeof(::android::hardware::sensors::V2_1::SensorInfo) == 112, "wrong size");
static_assert(__alignof(::android::hardware::sensors::V2_1::SensorInfo) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(int32_t o);
static inline std::string toString(::android::hardware::sensors::V2_1::SensorType o);
static inline void PrintTo(::android::hardware::sensors::V2_1::SensorType o, ::std::ostream* os);
constexpr int32_t operator|(const ::android::hardware::sensors::V2_1::SensorType lhs, const ::android::hardware::sensors::V2_1::SensorType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const int32_t lhs, const ::android::hardware::sensors::V2_1::SensorType rhs) {
    return static_cast<int32_t>(lhs | static_cast<int32_t>(rhs));
}
constexpr int32_t operator|(const ::android::hardware::sensors::V2_1::SensorType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) | rhs);
}
constexpr int32_t operator&(const ::android::hardware::sensors::V2_1::SensorType lhs, const ::android::hardware::sensors::V2_1::SensorType rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const int32_t lhs, const ::android::hardware::sensors::V2_1::SensorType rhs) {
    return static_cast<int32_t>(lhs & static_cast<int32_t>(rhs));
}
constexpr int32_t operator&(const ::android::hardware::sensors::V2_1::SensorType lhs, const int32_t rhs) {
    return static_cast<int32_t>(static_cast<int32_t>(lhs) & rhs);
}
constexpr int32_t &operator|=(int32_t& v, const ::android::hardware::sensors::V2_1::SensorType e) {
    v |= static_cast<int32_t>(e);
    return v;
}
constexpr int32_t &operator&=(int32_t& v, const ::android::hardware::sensors::V2_1::SensorType e) {
    v &= static_cast<int32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::sensors::V2_1::Event& o);
static inline void PrintTo(const ::android::hardware::sensors::V2_1::Event& o, ::std::ostream*);
// operator== and operator!= are not generated for Event

static inline std::string toString(const ::android::hardware::sensors::V2_1::SensorInfo& o);
static inline void PrintTo(const ::android::hardware::sensors::V2_1::SensorInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::sensors::V2_1::SensorInfo& lhs, const ::android::hardware::sensors::V2_1::SensorInfo& rhs);
static inline bool operator!=(const ::android::hardware::sensors::V2_1::SensorInfo& lhs, const ::android::hardware::sensors::V2_1::SensorInfo& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::sensors::V2_1::SensorType>(int32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::sensors::V2_1::SensorType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::sensors::V2_1::SensorType::META_DATA) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::META_DATA)) {
        os += (first ? "" : " | ");
        os += "META_DATA";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::META_DATA;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::ACCELEROMETER) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::ACCELEROMETER)) {
        os += (first ? "" : " | ");
        os += "ACCELEROMETER";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::ACCELEROMETER;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::MAGNETIC_FIELD) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::MAGNETIC_FIELD)) {
        os += (first ? "" : " | ");
        os += "MAGNETIC_FIELD";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::MAGNETIC_FIELD;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::ORIENTATION) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::ORIENTATION)) {
        os += (first ? "" : " | ");
        os += "ORIENTATION";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::ORIENTATION;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::GYROSCOPE) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::GYROSCOPE)) {
        os += (first ? "" : " | ");
        os += "GYROSCOPE";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::GYROSCOPE;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::LIGHT) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::LIGHT)) {
        os += (first ? "" : " | ");
        os += "LIGHT";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::LIGHT;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::PRESSURE) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::PRESSURE)) {
        os += (first ? "" : " | ");
        os += "PRESSURE";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::PRESSURE;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::TEMPERATURE) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::TEMPERATURE)) {
        os += (first ? "" : " | ");
        os += "TEMPERATURE";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::TEMPERATURE;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::PROXIMITY) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::PROXIMITY)) {
        os += (first ? "" : " | ");
        os += "PROXIMITY";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::PROXIMITY;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::GRAVITY) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::GRAVITY)) {
        os += (first ? "" : " | ");
        os += "GRAVITY";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::GRAVITY;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::LINEAR_ACCELERATION) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::LINEAR_ACCELERATION)) {
        os += (first ? "" : " | ");
        os += "LINEAR_ACCELERATION";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::LINEAR_ACCELERATION;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::ROTATION_VECTOR) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::ROTATION_VECTOR)) {
        os += (first ? "" : " | ");
        os += "ROTATION_VECTOR";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::ROTATION_VECTOR;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::RELATIVE_HUMIDITY) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::RELATIVE_HUMIDITY)) {
        os += (first ? "" : " | ");
        os += "RELATIVE_HUMIDITY";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::RELATIVE_HUMIDITY;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::AMBIENT_TEMPERATURE) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::AMBIENT_TEMPERATURE)) {
        os += (first ? "" : " | ");
        os += "AMBIENT_TEMPERATURE";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::AMBIENT_TEMPERATURE;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::MAGNETIC_FIELD_UNCALIBRATED) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::MAGNETIC_FIELD_UNCALIBRATED)) {
        os += (first ? "" : " | ");
        os += "MAGNETIC_FIELD_UNCALIBRATED";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::MAGNETIC_FIELD_UNCALIBRATED;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::GAME_ROTATION_VECTOR) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::GAME_ROTATION_VECTOR)) {
        os += (first ? "" : " | ");
        os += "GAME_ROTATION_VECTOR";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::GAME_ROTATION_VECTOR;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::GYROSCOPE_UNCALIBRATED) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::GYROSCOPE_UNCALIBRATED)) {
        os += (first ? "" : " | ");
        os += "GYROSCOPE_UNCALIBRATED";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::GYROSCOPE_UNCALIBRATED;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::SIGNIFICANT_MOTION) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::SIGNIFICANT_MOTION)) {
        os += (first ? "" : " | ");
        os += "SIGNIFICANT_MOTION";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::SIGNIFICANT_MOTION;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::STEP_DETECTOR) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::STEP_DETECTOR)) {
        os += (first ? "" : " | ");
        os += "STEP_DETECTOR";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::STEP_DETECTOR;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::STEP_COUNTER) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::STEP_COUNTER)) {
        os += (first ? "" : " | ");
        os += "STEP_COUNTER";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::STEP_COUNTER;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::GEOMAGNETIC_ROTATION_VECTOR) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::GEOMAGNETIC_ROTATION_VECTOR)) {
        os += (first ? "" : " | ");
        os += "GEOMAGNETIC_ROTATION_VECTOR";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::GEOMAGNETIC_ROTATION_VECTOR;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::HEART_RATE) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::HEART_RATE)) {
        os += (first ? "" : " | ");
        os += "HEART_RATE";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::HEART_RATE;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::TILT_DETECTOR) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::TILT_DETECTOR)) {
        os += (first ? "" : " | ");
        os += "TILT_DETECTOR";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::TILT_DETECTOR;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::WAKE_GESTURE) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::WAKE_GESTURE)) {
        os += (first ? "" : " | ");
        os += "WAKE_GESTURE";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::WAKE_GESTURE;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::GLANCE_GESTURE) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::GLANCE_GESTURE)) {
        os += (first ? "" : " | ");
        os += "GLANCE_GESTURE";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::GLANCE_GESTURE;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::PICK_UP_GESTURE) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::PICK_UP_GESTURE)) {
        os += (first ? "" : " | ");
        os += "PICK_UP_GESTURE";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::PICK_UP_GESTURE;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::WRIST_TILT_GESTURE) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::WRIST_TILT_GESTURE)) {
        os += (first ? "" : " | ");
        os += "WRIST_TILT_GESTURE";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::WRIST_TILT_GESTURE;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::DEVICE_ORIENTATION) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::DEVICE_ORIENTATION)) {
        os += (first ? "" : " | ");
        os += "DEVICE_ORIENTATION";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::DEVICE_ORIENTATION;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::POSE_6DOF) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::POSE_6DOF)) {
        os += (first ? "" : " | ");
        os += "POSE_6DOF";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::POSE_6DOF;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::STATIONARY_DETECT) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::STATIONARY_DETECT)) {
        os += (first ? "" : " | ");
        os += "STATIONARY_DETECT";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::STATIONARY_DETECT;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::MOTION_DETECT) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::MOTION_DETECT)) {
        os += (first ? "" : " | ");
        os += "MOTION_DETECT";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::MOTION_DETECT;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::HEART_BEAT) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::HEART_BEAT)) {
        os += (first ? "" : " | ");
        os += "HEART_BEAT";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::HEART_BEAT;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::DYNAMIC_SENSOR_META) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::DYNAMIC_SENSOR_META)) {
        os += (first ? "" : " | ");
        os += "DYNAMIC_SENSOR_META";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::DYNAMIC_SENSOR_META;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::ADDITIONAL_INFO) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::ADDITIONAL_INFO)) {
        os += (first ? "" : " | ");
        os += "ADDITIONAL_INFO";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::ADDITIONAL_INFO;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::LOW_LATENCY_OFFBODY_DETECT) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::LOW_LATENCY_OFFBODY_DETECT)) {
        os += (first ? "" : " | ");
        os += "LOW_LATENCY_OFFBODY_DETECT";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::LOW_LATENCY_OFFBODY_DETECT;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::ACCELEROMETER_UNCALIBRATED) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::ACCELEROMETER_UNCALIBRATED)) {
        os += (first ? "" : " | ");
        os += "ACCELEROMETER_UNCALIBRATED";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::ACCELEROMETER_UNCALIBRATED;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::DEVICE_PRIVATE_BASE) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::DEVICE_PRIVATE_BASE)) {
        os += (first ? "" : " | ");
        os += "DEVICE_PRIVATE_BASE";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::DEVICE_PRIVATE_BASE;
    }
    if ((o & ::android::hardware::sensors::V2_1::SensorType::HINGE_ANGLE) == static_cast<int32_t>(::android::hardware::sensors::V2_1::SensorType::HINGE_ANGLE)) {
        os += (first ? "" : " | ");
        os += "HINGE_ANGLE";
        first = false;
        flipped |= ::android::hardware::sensors::V2_1::SensorType::HINGE_ANGLE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::sensors::V2_1::SensorType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::sensors::V2_1::SensorType::META_DATA) {
        return "META_DATA";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::ACCELEROMETER) {
        return "ACCELEROMETER";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::MAGNETIC_FIELD) {
        return "MAGNETIC_FIELD";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::ORIENTATION) {
        return "ORIENTATION";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::GYROSCOPE) {
        return "GYROSCOPE";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::LIGHT) {
        return "LIGHT";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::PRESSURE) {
        return "PRESSURE";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::TEMPERATURE) {
        return "TEMPERATURE";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::PROXIMITY) {
        return "PROXIMITY";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::GRAVITY) {
        return "GRAVITY";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::LINEAR_ACCELERATION) {
        return "LINEAR_ACCELERATION";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::ROTATION_VECTOR) {
        return "ROTATION_VECTOR";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::RELATIVE_HUMIDITY) {
        return "RELATIVE_HUMIDITY";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::AMBIENT_TEMPERATURE) {
        return "AMBIENT_TEMPERATURE";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::MAGNETIC_FIELD_UNCALIBRATED) {
        return "MAGNETIC_FIELD_UNCALIBRATED";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::GAME_ROTATION_VECTOR) {
        return "GAME_ROTATION_VECTOR";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::GYROSCOPE_UNCALIBRATED) {
        return "GYROSCOPE_UNCALIBRATED";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::SIGNIFICANT_MOTION) {
        return "SIGNIFICANT_MOTION";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::STEP_DETECTOR) {
        return "STEP_DETECTOR";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::STEP_COUNTER) {
        return "STEP_COUNTER";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::GEOMAGNETIC_ROTATION_VECTOR) {
        return "GEOMAGNETIC_ROTATION_VECTOR";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::HEART_RATE) {
        return "HEART_RATE";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::TILT_DETECTOR) {
        return "TILT_DETECTOR";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::WAKE_GESTURE) {
        return "WAKE_GESTURE";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::GLANCE_GESTURE) {
        return "GLANCE_GESTURE";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::PICK_UP_GESTURE) {
        return "PICK_UP_GESTURE";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::WRIST_TILT_GESTURE) {
        return "WRIST_TILT_GESTURE";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::DEVICE_ORIENTATION) {
        return "DEVICE_ORIENTATION";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::POSE_6DOF) {
        return "POSE_6DOF";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::STATIONARY_DETECT) {
        return "STATIONARY_DETECT";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::MOTION_DETECT) {
        return "MOTION_DETECT";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::HEART_BEAT) {
        return "HEART_BEAT";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::DYNAMIC_SENSOR_META) {
        return "DYNAMIC_SENSOR_META";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::ADDITIONAL_INFO) {
        return "ADDITIONAL_INFO";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::LOW_LATENCY_OFFBODY_DETECT) {
        return "LOW_LATENCY_OFFBODY_DETECT";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::ACCELEROMETER_UNCALIBRATED) {
        return "ACCELEROMETER_UNCALIBRATED";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::DEVICE_PRIVATE_BASE) {
        return "DEVICE_PRIVATE_BASE";
    }
    if (o == ::android::hardware::sensors::V2_1::SensorType::HINGE_ANGLE) {
        return "HINGE_ANGLE";
    }
    std::string os;
    os += toHexString(static_cast<int32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::sensors::V2_1::SensorType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::sensors::V2_1::Event& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".timestamp = ";
    os += ::android::hardware::toString(o.timestamp);
    os += ", .sensorHandle = ";
    os += ::android::hardware::toString(o.sensorHandle);
    os += ", .sensorType = ";
    os += ::android::hardware::sensors::V2_1::toString(o.sensorType);
    os += ", .u = ";
    os += ::android::hardware::sensors::V1_0::toString(o.u);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::sensors::V2_1::Event& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for Event

static inline std::string toString(const ::android::hardware::sensors::V2_1::SensorInfo& o) {
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
    os += ::android::hardware::sensors::V2_1::toString(o.type);
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

static inline void PrintTo(const ::android::hardware::sensors::V2_1::SensorInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::sensors::V2_1::SensorInfo& lhs, const ::android::hardware::sensors::V2_1::SensorInfo& rhs) {
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

static inline bool operator!=(const ::android::hardware::sensors::V2_1::SensorInfo& lhs, const ::android::hardware::sensors::V2_1::SensorInfo& rhs){
    return !(lhs == rhs);
}


}  // namespace V2_1
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
template<> inline constexpr std::array<::android::hardware::sensors::V2_1::SensorType, 38> hidl_enum_values<::android::hardware::sensors::V2_1::SensorType> = {
    ::android::hardware::sensors::V2_1::SensorType::META_DATA,
    ::android::hardware::sensors::V2_1::SensorType::ACCELEROMETER,
    ::android::hardware::sensors::V2_1::SensorType::MAGNETIC_FIELD,
    ::android::hardware::sensors::V2_1::SensorType::ORIENTATION,
    ::android::hardware::sensors::V2_1::SensorType::GYROSCOPE,
    ::android::hardware::sensors::V2_1::SensorType::LIGHT,
    ::android::hardware::sensors::V2_1::SensorType::PRESSURE,
    ::android::hardware::sensors::V2_1::SensorType::TEMPERATURE,
    ::android::hardware::sensors::V2_1::SensorType::PROXIMITY,
    ::android::hardware::sensors::V2_1::SensorType::GRAVITY,
    ::android::hardware::sensors::V2_1::SensorType::LINEAR_ACCELERATION,
    ::android::hardware::sensors::V2_1::SensorType::ROTATION_VECTOR,
    ::android::hardware::sensors::V2_1::SensorType::RELATIVE_HUMIDITY,
    ::android::hardware::sensors::V2_1::SensorType::AMBIENT_TEMPERATURE,
    ::android::hardware::sensors::V2_1::SensorType::MAGNETIC_FIELD_UNCALIBRATED,
    ::android::hardware::sensors::V2_1::SensorType::GAME_ROTATION_VECTOR,
    ::android::hardware::sensors::V2_1::SensorType::GYROSCOPE_UNCALIBRATED,
    ::android::hardware::sensors::V2_1::SensorType::SIGNIFICANT_MOTION,
    ::android::hardware::sensors::V2_1::SensorType::STEP_DETECTOR,
    ::android::hardware::sensors::V2_1::SensorType::STEP_COUNTER,
    ::android::hardware::sensors::V2_1::SensorType::GEOMAGNETIC_ROTATION_VECTOR,
    ::android::hardware::sensors::V2_1::SensorType::HEART_RATE,
    ::android::hardware::sensors::V2_1::SensorType::TILT_DETECTOR,
    ::android::hardware::sensors::V2_1::SensorType::WAKE_GESTURE,
    ::android::hardware::sensors::V2_1::SensorType::GLANCE_GESTURE,
    ::android::hardware::sensors::V2_1::SensorType::PICK_UP_GESTURE,
    ::android::hardware::sensors::V2_1::SensorType::WRIST_TILT_GESTURE,
    ::android::hardware::sensors::V2_1::SensorType::DEVICE_ORIENTATION,
    ::android::hardware::sensors::V2_1::SensorType::POSE_6DOF,
    ::android::hardware::sensors::V2_1::SensorType::STATIONARY_DETECT,
    ::android::hardware::sensors::V2_1::SensorType::MOTION_DETECT,
    ::android::hardware::sensors::V2_1::SensorType::HEART_BEAT,
    ::android::hardware::sensors::V2_1::SensorType::DYNAMIC_SENSOR_META,
    ::android::hardware::sensors::V2_1::SensorType::ADDITIONAL_INFO,
    ::android::hardware::sensors::V2_1::SensorType::LOW_LATENCY_OFFBODY_DETECT,
    ::android::hardware::sensors::V2_1::SensorType::ACCELEROMETER_UNCALIBRATED,
    ::android::hardware::sensors::V2_1::SensorType::DEVICE_PRIVATE_BASE,
    ::android::hardware::sensors::V2_1::SensorType::HINGE_ANGLE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V2_1_TYPES_H
