#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_TYPES_H

#include <android/hardware/automotive/evs/1.0/types.h>
#include <android/hardware/camera/device/3.2/types.h>
#include <android/hardware/graphics/common/1.2/types.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace automotive {
namespace evs {
namespace V1_1 {

// Forward declaration for forward reference support:
struct CameraDesc;
struct BufferDesc;
enum class EvsEventType : uint32_t;
struct EvsEventDesc;
enum class CameraParam : uint32_t;
struct UltrasonicsArrayDesc;
struct RotationQuat;
struct Translation;
struct SensorPose;
struct UltrasonicSensor;
struct UltrasonicsDataFrameDesc;

/**
 * Structure describing the basic properties of an EVS camera, extended from its
 * v1.0 declaration.
 *
 * The HAL is responsible for filling out this structure for each
 * EVS camera in the system.
 */
struct CameraDesc final {
    ::android::hardware::automotive::evs::V1_0::CameraDesc v1 __attribute__ ((aligned(8)));
    /**
     * Store camera metadata such as lens characteristics.
     */
    ::android::hardware::hidl_vec<uint8_t> metadata __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::automotive::evs::V1_1::CameraDesc, v1) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_1::CameraDesc, metadata) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::evs::V1_1::CameraDesc) == 40, "wrong size");
static_assert(__alignof(::android::hardware::automotive::evs::V1_1::CameraDesc) == 8, "wrong alignment");

/**
 * Structure representing an image buffer through our APIs
 *
 * In addition to the handle to the graphics memory, we need to retain
 * the properties of the buffer for easy reference and reconstruction of
 * an ANativeWindowBuffer object on the remote side of API calls.
 * (Not least because OpenGL expect an ANativeWindowBuffer* for us as a
 * texture via eglCreateImageKHR().
 */
struct BufferDesc final {
    /**
     * HIDL counterpart of `AHardwareBuffer_Desc`.  Please see
     * hardware/interfaces/graphics/common/1.2/types.hal for more details.
     */
    ::android::hardware::graphics::common::V1_2::HardwareBuffer buffer __attribute__ ((aligned(8)));
    /**
     * The size of a pixel in the units of bytes
     */
    uint32_t pixelSize __attribute__ ((aligned(4)));
    /**
     * Opaque value from driver
     */
    uint32_t bufferId __attribute__ ((aligned(4)));
    /**
     * Unique identifier of the physical camera device that produces this buffer.
     */
    ::android::hardware::hidl_string deviceId __attribute__ ((aligned(8)));
    /**
     * Time that this buffer is being filled.
     */
    int64_t timestamp __attribute__ ((aligned(8)));
    /**
     * Frame metadata.  This is opaque to EVS manager.
     */
    ::android::hardware::hidl_vec<uint8_t> metadata __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::automotive::evs::V1_1::BufferDesc, buffer) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_1::BufferDesc, pixelSize) == 56, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_1::BufferDesc, bufferId) == 60, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_1::BufferDesc, deviceId) == 64, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_1::BufferDesc, timestamp) == 80, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_1::BufferDesc, metadata) == 88, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::evs::V1_1::BufferDesc) == 104, "wrong size");
static_assert(__alignof(::android::hardware::automotive::evs::V1_1::BufferDesc) == 8, "wrong alignment");

/**
 * Types of informative streaming events
 */
enum class EvsEventType : uint32_t {
    /**
     * Video stream is started
     */
    STREAM_STARTED = 0u,
    /**
     * Video stream is stopped
     */
    STREAM_STOPPED = 1u /* ::android::hardware::automotive::evs::V1_1::EvsEventType.STREAM_STARTED implicitly + 1 */,
    /**
     * Video frame is dropped
     */
    FRAME_DROPPED = 2u /* ::android::hardware::automotive::evs::V1_1::EvsEventType.STREAM_STOPPED implicitly + 1 */,
    /**
     * Timeout happens
     */
    TIMEOUT = 3u /* ::android::hardware::automotive::evs::V1_1::EvsEventType.FRAME_DROPPED implicitly + 1 */,
    /**
     * Camera parameter is changed; payload contains a changed parameter ID and
     * its value
     */
    PARAMETER_CHANGED = 4u /* ::android::hardware::automotive::evs::V1_1::EvsEventType.TIMEOUT implicitly + 1 */,
    /**
     * Master role has become available
     */
    MASTER_RELEASED = 5u /* ::android::hardware::automotive::evs::V1_1::EvsEventType.PARAMETER_CHANGED implicitly + 1 */,
    /**
     * Any other erroneous streaming events
     */
    STREAM_ERROR = 6u /* ::android::hardware::automotive::evs::V1_1::EvsEventType.MASTER_RELEASED implicitly + 1 */,
};

/**
 * Structure that describes informative events occurred during EVS is streaming
 */
struct EvsEventDesc final {
    /**
     * Type of an informative event
     */
    ::android::hardware::automotive::evs::V1_1::EvsEventType aType __attribute__ ((aligned(4)));
    /**
     * Device identifier
     */
    ::android::hardware::hidl_string deviceId __attribute__ ((aligned(8)));
    /**
     * Possible additional information
     */
    ::android::hardware::hidl_array<uint32_t, 4> payload __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::automotive::evs::V1_1::EvsEventDesc, aType) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_1::EvsEventDesc, deviceId) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_1::EvsEventDesc, payload) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::evs::V1_1::EvsEventDesc) == 40, "wrong size");
static_assert(__alignof(::android::hardware::automotive::evs::V1_1::EvsEventDesc) == 8, "wrong alignment");

/**
 * EVS Camera Parameter
 */
enum class CameraParam : uint32_t {
    /**
     * The brightness of image frames
     */
    BRIGHTNESS = 0u,
    /**
     * The contrast of image frames
     */
    CONTRAST = 1u /* ::android::hardware::automotive::evs::V1_1::CameraParam.BRIGHTNESS implicitly + 1 */,
    /**
     * Automatic gain/exposure control
     */
    AUTOGAIN = 2u /* ::android::hardware::automotive::evs::V1_1::CameraParam.CONTRAST implicitly + 1 */,
    /**
     * Gain control
     */
    GAIN = 3u /* ::android::hardware::automotive::evs::V1_1::CameraParam.AUTOGAIN implicitly + 1 */,
    /**
     * Automatic Whitebalance
     */
    AUTO_WHITE_BALANCE = 4u /* ::android::hardware::automotive::evs::V1_1::CameraParam.GAIN implicitly + 1 */,
    /**
     * Manual white balance setting as a color temperature in Kelvin.
     */
    WHITE_BALANCE_TEMPERATURE = 5u /* ::android::hardware::automotive::evs::V1_1::CameraParam.AUTO_WHITE_BALANCE implicitly + 1 */,
    /**
     * Image sharpness adjustment
     */
    SHARPNESS = 6u /* ::android::hardware::automotive::evs::V1_1::CameraParam.WHITE_BALANCE_TEMPERATURE implicitly + 1 */,
    /**
     * Auto Exposure Control modes; auto, manual, shutter priority, or
     * aperture priority.
     */
    AUTO_EXPOSURE = 7u /* ::android::hardware::automotive::evs::V1_1::CameraParam.SHARPNESS implicitly + 1 */,
    /**
     * Manual exposure time of the camera
     */
    ABSOLUTE_EXPOSURE = 8u /* ::android::hardware::automotive::evs::V1_1::CameraParam.AUTO_EXPOSURE implicitly + 1 */,
    /**
     * Set the focal point of the camera to the specified position.  This
     * parameter may not be effective when auto focus is enabled.
     */
    ABSOLUTE_FOCUS = 9u /* ::android::hardware::automotive::evs::V1_1::CameraParam.ABSOLUTE_EXPOSURE implicitly + 1 */,
    /**
     * Enables continuous automatic focus adjustments.
     */
    AUTO_FOCUS = 10u /* ::android::hardware::automotive::evs::V1_1::CameraParam.ABSOLUTE_FOCUS implicitly + 1 */,
    /**
     * Specify the objective lens focal length as an absolute value.
     */
    ABSOLUTE_ZOOM = 11u /* ::android::hardware::automotive::evs::V1_1::CameraParam.AUTO_FOCUS implicitly + 1 */,
};

/**
 * Structure identifies and describes an ultrasonics array in the car.
 *
 * A ultrasonics array represents a group of ultrasonic sensors within the
 * car. These may be sensors that are physically connected to the same hardware
 * control unit or represent a logical group of sensors like front and back.
 * The HAL is responsible for filling out this structure for each Ultrasonics
 * Array.
 */
struct UltrasonicsArrayDesc final {
    /**
     * Unique identifier for the ultrasonic array. This may be a path or name of the
     * physical control device or a string identifying a logical group of sensors forming an array
     * such as "front_array" and "back_array".
     */
    ::android::hardware::hidl_string ultrasonicsArrayId __attribute__ ((aligned(8)));
    /**
     * Maximum number of readings (points on waveform) provided per sensor in
     * each data frame. Used by client to pre-allocate required memory buffer for
     * incoming data.
     */
    uint32_t maxReadingsPerSensorCount __attribute__ ((aligned(4)));
    /**
     * Maximum number of receiver sensors in a data frame. Must be between 1
     * and sensorCount. Used by client to pre-allocate required memory buffer for
     * incoming data.
     */
    uint32_t maxReceiversCount __attribute__ ((aligned(4)));
    /**
     * The order of sensors specified should preferably be in clockwise order
     * around the car, starting from front left-most sensor.
     */
    ::android::hardware::hidl_vec<::android::hardware::automotive::evs::V1_1::UltrasonicSensor> sensors __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc, ultrasonicsArrayId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc, maxReadingsPerSensorCount) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc, maxReceiversCount) == 20, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc, sensors) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc) == 40, "wrong size");
static_assert(__alignof(::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc) == 8, "wrong alignment");

/**
 * Structure for rotation expressed as quaternions.
 * Convention used: Unit quaternion with hamilton convention.
 */
struct RotationQuat final {
    float x __attribute__ ((aligned(4)));
    float y __attribute__ ((aligned(4)));
    float z __attribute__ ((aligned(4)));
    float w __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::automotive::evs::V1_1::RotationQuat, x) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_1::RotationQuat, y) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_1::RotationQuat, z) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_1::RotationQuat, w) == 12, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::evs::V1_1::RotationQuat) == 16, "wrong size");
static_assert(__alignof(::android::hardware::automotive::evs::V1_1::RotationQuat) == 4, "wrong alignment");

/**
 * Structure for translation with x, y and z units.
 */
struct Translation final {
    float x __attribute__ ((aligned(4)));
    float y __attribute__ ((aligned(4)));
    float z __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::automotive::evs::V1_1::Translation, x) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_1::Translation, y) == 4, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_1::Translation, z) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::evs::V1_1::Translation) == 12, "wrong size");
static_assert(__alignof(::android::hardware::automotive::evs::V1_1::Translation) == 4, "wrong alignment");

/**
 * Provides the orientation and location of a car sensor relative to the android automotive
 * coordinate system:
 * https://source.android.com/devices/sensors/sensor-types#auto_axes
 * The sensor pose defines the transformation to be applied to the android automotive axes to
 * obtain the sensor local axes.
 * The pose consists of rotation, (specified as a quaternions) and translation
 * (vector with x, y, z).
 * This rotation and translation applied to the sensor data in the sensor's local coordinate
 * system transform the data to the automotive coordinate system.
 * i.e   Pcar =  ( Rot * Psensor ) + Trans
 * Here Pcar is a point in automotive coordinate system and Psensor is a point in the sensor's
 * coordinate system.
 * Example:
 * For a sensor on the front bumper and on the left corner of the car with its X axis pointing to
 * the front, the sensor is located at (-2, 4, 0) meters w.r.t android automotive axes and the
 * sensor local axes has a rotation of 90 degrees counter-clockwise w.r.t android automotive axes
 * when viewing the car from top on the +Z axis side:
 *
 *      ↑X sensor
 *    Y←∘______
 *      |      |  front
 *      | car  |
 *      |  ↑Y  |
 *      |  ∘→X |  rear
 *      |______|
 *
 * For this example the rotation and translation will be:
 * Rotation = + 90 degrees around Z axis = (0.7071, 0, 0, 0.7071) as a unit quaternion.
 * Translation = (-2, 4, 0) in meters = (-2000, 4000, 0) in milli-meters.
 * Note: Every sensor type must specify its own pose.
 */
struct SensorPose final {
    /**
     * Rotation part of the sensor pose, expressed as a unit quaternion.
     */
    ::android::hardware::automotive::evs::V1_1::RotationQuat rotation __attribute__ ((aligned(4)));
    /**
     * Translation part of the sensor pose, in (x, y, z) format with milli-meter units.
     */
    ::android::hardware::automotive::evs::V1_1::Translation translation __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::automotive::evs::V1_1::SensorPose, rotation) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_1::SensorPose, translation) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::evs::V1_1::SensorPose) == 28, "wrong size");
static_assert(__alignof(::android::hardware::automotive::evs::V1_1::SensorPose) == 4, "wrong alignment");

/**
 * Structure that contains all information of an ultrasonic sensor.
 */
struct UltrasonicSensor final {
    /**
     * Pose provides the orientation and location of the ultrasonic sensor within the car.
     * The +Y axis points along the center of the beam spread the X axis to the right and the Z
     * axis in the up direction.
     */
    ::android::hardware::automotive::evs::V1_1::SensorPose pose __attribute__ ((aligned(4)));
    /**
     * Maximum range of the sensor in milli-metres.
     */
    float maxRange __attribute__ ((aligned(4)));
    /**
     * Half-angle of the angle of measurement of the sensor, relative to the
     * sensor’s x axis, in radians.
     */
    float angleOfMeasurement __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::automotive::evs::V1_1::UltrasonicSensor, pose) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_1::UltrasonicSensor, maxRange) == 28, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_1::UltrasonicSensor, angleOfMeasurement) == 32, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::evs::V1_1::UltrasonicSensor) == 36, "wrong size");
static_assert(__alignof(::android::hardware::automotive::evs::V1_1::UltrasonicSensor) == 4, "wrong alignment");

/**
 * Structure that describes the data frame received from an ultrasonics array.
 *
 * Each data frame returned consists of received waveform signals from a subset
 * of sensors in an array as indicated by the receiversIdList. The signal is
 * transmitted at a particular time instant indicated by timestampNs from a
 * subset of sensors in the array as provided in the transmittersIdList.
 */
struct UltrasonicsDataFrameDesc final {
    /**
     * Timestamp of the start of the transmit signal for this data frame.
     * Timestamp unit is nanoseconds and is obtained from android elapsed realtime clock which is
     * the time since system was booted and includes deep sleep.
     * timeOfFlight readings are future-deltas to this timestamp.
     */
    uint64_t timestampNs __attribute__ ((aligned(8)));
    /**
     * Identifier of data frame. Used by implementation for managing multiple frames in flight.
     */
    uint32_t dataFrameId __attribute__ ((aligned(4)));
    /**
     * List of indexes of sensors in range [0, sensorCount - 1] that
     * transmitted the signal for this data frame.
     */
    ::android::hardware::hidl_vec<uint8_t> transmittersIdList __attribute__ ((aligned(8)));
    /**
     * List of indexes of sensors in range [0, sensorCount - 1] that received
     * the signal. The order of ids must match the order of the waveforms in the
     * waveformsData.
     * Size of list is upper bound by maxReceiversCount.
     */
    ::android::hardware::hidl_vec<uint8_t> receiversIdList __attribute__ ((aligned(8)));
    /**
     * List of the number of readings corresponding to each ultrasonics sensor in
     * the receiversIdList. Order of the readings count must match the order in
     * receiversIdList.
     * Size of list is upper bound by maxReadingsPerSensorCount.
     */
    ::android::hardware::hidl_vec<uint32_t> receiversReadingsCountList __attribute__ ((aligned(8)));
    /**
     * Shared memory object containing the waveforms data. Contains one waveform
     * for each sensor specified in receiversIdList, in order.
     * Each waveform is represented by a number of readings, which are sample
     * points on the waveform. The number of readings for each waveform is as
     * specified in the receiversReadingsCountList.
     * Each reading is a pair of time Of flight and resonance.
     * Time of flight (float): Time between transmit and receive signal in nanoseconds.
     * Resonance (float): Resonance at time on waveform in range [0.0, 1.0].
     *
     * The structure of shared memory (example with 2 waveforms, each with 2 readings):
     *
     * Byte: |   0    |  1-4  |  5-8  | 9-12  | 13-16 ||   17   |  18-21 | 22-25  | 26-29 | 30-33 |
     * Data: | RecId1 | TOF1  | RES1  | TOF2  | RES2  || RecId2 |  TOF1  |  RES1  | TOF2  | RES2  |
     *       |              Waveform1                 ||             Waveform2                    |
     * Here:
     * RecId : Receiver's Id. Order matches the receiversIdList, type uint8_t
     * TOF : Time of flight, type float (4 bytes)
     * RES : Resonance, type float (4 bytes)
     * Note: All readings and waveforms are contigious with no padding.
     */
    ::android::hardware::hidl_memory waveformsData __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::automotive::evs::V1_1::UltrasonicsDataFrameDesc, timestampNs) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_1::UltrasonicsDataFrameDesc, dataFrameId) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_1::UltrasonicsDataFrameDesc, transmittersIdList) == 16, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_1::UltrasonicsDataFrameDesc, receiversIdList) == 32, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_1::UltrasonicsDataFrameDesc, receiversReadingsCountList) == 48, "wrong offset");
static_assert(offsetof(::android::hardware::automotive::evs::V1_1::UltrasonicsDataFrameDesc, waveformsData) == 64, "wrong offset");
static_assert(sizeof(::android::hardware::automotive::evs::V1_1::UltrasonicsDataFrameDesc) == 104, "wrong size");
static_assert(__alignof(::android::hardware::automotive::evs::V1_1::UltrasonicsDataFrameDesc) == 8, "wrong alignment");

//
// type declarations for package
//

static inline std::string toString(const ::android::hardware::automotive::evs::V1_1::CameraDesc& o);
static inline void PrintTo(const ::android::hardware::automotive::evs::V1_1::CameraDesc& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::evs::V1_1::CameraDesc& lhs, const ::android::hardware::automotive::evs::V1_1::CameraDesc& rhs);
static inline bool operator!=(const ::android::hardware::automotive::evs::V1_1::CameraDesc& lhs, const ::android::hardware::automotive::evs::V1_1::CameraDesc& rhs);

static inline std::string toString(const ::android::hardware::automotive::evs::V1_1::BufferDesc& o);
static inline void PrintTo(const ::android::hardware::automotive::evs::V1_1::BufferDesc& o, ::std::ostream*);
// operator== and operator!= are not generated for BufferDesc

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::automotive::evs::V1_1::EvsEventType o);
static inline void PrintTo(::android::hardware::automotive::evs::V1_1::EvsEventType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::automotive::evs::V1_1::EvsEventType lhs, const ::android::hardware::automotive::evs::V1_1::EvsEventType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::automotive::evs::V1_1::EvsEventType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::automotive::evs::V1_1::EvsEventType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::automotive::evs::V1_1::EvsEventType lhs, const ::android::hardware::automotive::evs::V1_1::EvsEventType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::automotive::evs::V1_1::EvsEventType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::automotive::evs::V1_1::EvsEventType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::automotive::evs::V1_1::EvsEventType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::automotive::evs::V1_1::EvsEventType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::automotive::evs::V1_1::EvsEventDesc& o);
static inline void PrintTo(const ::android::hardware::automotive::evs::V1_1::EvsEventDesc& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::evs::V1_1::EvsEventDesc& lhs, const ::android::hardware::automotive::evs::V1_1::EvsEventDesc& rhs);
static inline bool operator!=(const ::android::hardware::automotive::evs::V1_1::EvsEventDesc& lhs, const ::android::hardware::automotive::evs::V1_1::EvsEventDesc& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::automotive::evs::V1_1::CameraParam o);
static inline void PrintTo(::android::hardware::automotive::evs::V1_1::CameraParam o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::automotive::evs::V1_1::CameraParam lhs, const ::android::hardware::automotive::evs::V1_1::CameraParam rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::automotive::evs::V1_1::CameraParam rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::automotive::evs::V1_1::CameraParam lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::automotive::evs::V1_1::CameraParam lhs, const ::android::hardware::automotive::evs::V1_1::CameraParam rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::automotive::evs::V1_1::CameraParam rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::automotive::evs::V1_1::CameraParam lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::automotive::evs::V1_1::CameraParam e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::automotive::evs::V1_1::CameraParam e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc& o);
static inline void PrintTo(const ::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc& lhs, const ::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc& rhs);
static inline bool operator!=(const ::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc& lhs, const ::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc& rhs);

static inline std::string toString(const ::android::hardware::automotive::evs::V1_1::RotationQuat& o);
static inline void PrintTo(const ::android::hardware::automotive::evs::V1_1::RotationQuat& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::evs::V1_1::RotationQuat& lhs, const ::android::hardware::automotive::evs::V1_1::RotationQuat& rhs);
static inline bool operator!=(const ::android::hardware::automotive::evs::V1_1::RotationQuat& lhs, const ::android::hardware::automotive::evs::V1_1::RotationQuat& rhs);

static inline std::string toString(const ::android::hardware::automotive::evs::V1_1::Translation& o);
static inline void PrintTo(const ::android::hardware::automotive::evs::V1_1::Translation& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::evs::V1_1::Translation& lhs, const ::android::hardware::automotive::evs::V1_1::Translation& rhs);
static inline bool operator!=(const ::android::hardware::automotive::evs::V1_1::Translation& lhs, const ::android::hardware::automotive::evs::V1_1::Translation& rhs);

static inline std::string toString(const ::android::hardware::automotive::evs::V1_1::SensorPose& o);
static inline void PrintTo(const ::android::hardware::automotive::evs::V1_1::SensorPose& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::evs::V1_1::SensorPose& lhs, const ::android::hardware::automotive::evs::V1_1::SensorPose& rhs);
static inline bool operator!=(const ::android::hardware::automotive::evs::V1_1::SensorPose& lhs, const ::android::hardware::automotive::evs::V1_1::SensorPose& rhs);

static inline std::string toString(const ::android::hardware::automotive::evs::V1_1::UltrasonicSensor& o);
static inline void PrintTo(const ::android::hardware::automotive::evs::V1_1::UltrasonicSensor& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::automotive::evs::V1_1::UltrasonicSensor& lhs, const ::android::hardware::automotive::evs::V1_1::UltrasonicSensor& rhs);
static inline bool operator!=(const ::android::hardware::automotive::evs::V1_1::UltrasonicSensor& lhs, const ::android::hardware::automotive::evs::V1_1::UltrasonicSensor& rhs);

static inline std::string toString(const ::android::hardware::automotive::evs::V1_1::UltrasonicsDataFrameDesc& o);
static inline void PrintTo(const ::android::hardware::automotive::evs::V1_1::UltrasonicsDataFrameDesc& o, ::std::ostream*);
// operator== and operator!= are not generated for UltrasonicsDataFrameDesc

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::automotive::evs::V1_1::CameraDesc& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".v1 = ";
    os += ::android::hardware::automotive::evs::V1_0::toString(o.v1);
    os += ", .metadata = ";
    os += ::android::hardware::toString(o.metadata);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::evs::V1_1::CameraDesc& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::evs::V1_1::CameraDesc& lhs, const ::android::hardware::automotive::evs::V1_1::CameraDesc& rhs) {
    if (lhs.v1 != rhs.v1) {
        return false;
    }
    if (lhs.metadata != rhs.metadata) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::evs::V1_1::CameraDesc& lhs, const ::android::hardware::automotive::evs::V1_1::CameraDesc& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::evs::V1_1::BufferDesc& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".buffer = ";
    os += ::android::hardware::graphics::common::V1_2::toString(o.buffer);
    os += ", .pixelSize = ";
    os += ::android::hardware::toString(o.pixelSize);
    os += ", .bufferId = ";
    os += ::android::hardware::toString(o.bufferId);
    os += ", .deviceId = ";
    os += ::android::hardware::toString(o.deviceId);
    os += ", .timestamp = ";
    os += ::android::hardware::toString(o.timestamp);
    os += ", .metadata = ";
    os += ::android::hardware::toString(o.metadata);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::evs::V1_1::BufferDesc& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for BufferDesc

template<>
inline std::string toString<::android::hardware::automotive::evs::V1_1::EvsEventType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::evs::V1_1::EvsEventType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::evs::V1_1::EvsEventType::STREAM_STARTED) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_1::EvsEventType::STREAM_STARTED)) {
        os += (first ? "" : " | ");
        os += "STREAM_STARTED";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_1::EvsEventType::STREAM_STARTED;
    }
    if ((o & ::android::hardware::automotive::evs::V1_1::EvsEventType::STREAM_STOPPED) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_1::EvsEventType::STREAM_STOPPED)) {
        os += (first ? "" : " | ");
        os += "STREAM_STOPPED";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_1::EvsEventType::STREAM_STOPPED;
    }
    if ((o & ::android::hardware::automotive::evs::V1_1::EvsEventType::FRAME_DROPPED) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_1::EvsEventType::FRAME_DROPPED)) {
        os += (first ? "" : " | ");
        os += "FRAME_DROPPED";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_1::EvsEventType::FRAME_DROPPED;
    }
    if ((o & ::android::hardware::automotive::evs::V1_1::EvsEventType::TIMEOUT) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_1::EvsEventType::TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "TIMEOUT";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_1::EvsEventType::TIMEOUT;
    }
    if ((o & ::android::hardware::automotive::evs::V1_1::EvsEventType::PARAMETER_CHANGED) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_1::EvsEventType::PARAMETER_CHANGED)) {
        os += (first ? "" : " | ");
        os += "PARAMETER_CHANGED";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_1::EvsEventType::PARAMETER_CHANGED;
    }
    if ((o & ::android::hardware::automotive::evs::V1_1::EvsEventType::MASTER_RELEASED) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_1::EvsEventType::MASTER_RELEASED)) {
        os += (first ? "" : " | ");
        os += "MASTER_RELEASED";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_1::EvsEventType::MASTER_RELEASED;
    }
    if ((o & ::android::hardware::automotive::evs::V1_1::EvsEventType::STREAM_ERROR) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_1::EvsEventType::STREAM_ERROR)) {
        os += (first ? "" : " | ");
        os += "STREAM_ERROR";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_1::EvsEventType::STREAM_ERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::evs::V1_1::EvsEventType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::evs::V1_1::EvsEventType::STREAM_STARTED) {
        return "STREAM_STARTED";
    }
    if (o == ::android::hardware::automotive::evs::V1_1::EvsEventType::STREAM_STOPPED) {
        return "STREAM_STOPPED";
    }
    if (o == ::android::hardware::automotive::evs::V1_1::EvsEventType::FRAME_DROPPED) {
        return "FRAME_DROPPED";
    }
    if (o == ::android::hardware::automotive::evs::V1_1::EvsEventType::TIMEOUT) {
        return "TIMEOUT";
    }
    if (o == ::android::hardware::automotive::evs::V1_1::EvsEventType::PARAMETER_CHANGED) {
        return "PARAMETER_CHANGED";
    }
    if (o == ::android::hardware::automotive::evs::V1_1::EvsEventType::MASTER_RELEASED) {
        return "MASTER_RELEASED";
    }
    if (o == ::android::hardware::automotive::evs::V1_1::EvsEventType::STREAM_ERROR) {
        return "STREAM_ERROR";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::evs::V1_1::EvsEventType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::automotive::evs::V1_1::EvsEventDesc& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".aType = ";
    os += ::android::hardware::automotive::evs::V1_1::toString(o.aType);
    os += ", .deviceId = ";
    os += ::android::hardware::toString(o.deviceId);
    os += ", .payload = ";
    os += ::android::hardware::toString(o.payload);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::evs::V1_1::EvsEventDesc& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::evs::V1_1::EvsEventDesc& lhs, const ::android::hardware::automotive::evs::V1_1::EvsEventDesc& rhs) {
    if (lhs.aType != rhs.aType) {
        return false;
    }
    if (lhs.deviceId != rhs.deviceId) {
        return false;
    }
    if (lhs.payload != rhs.payload) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::evs::V1_1::EvsEventDesc& lhs, const ::android::hardware::automotive::evs::V1_1::EvsEventDesc& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::automotive::evs::V1_1::CameraParam>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::automotive::evs::V1_1::CameraParam> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::automotive::evs::V1_1::CameraParam::BRIGHTNESS) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_1::CameraParam::BRIGHTNESS)) {
        os += (first ? "" : " | ");
        os += "BRIGHTNESS";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_1::CameraParam::BRIGHTNESS;
    }
    if ((o & ::android::hardware::automotive::evs::V1_1::CameraParam::CONTRAST) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_1::CameraParam::CONTRAST)) {
        os += (first ? "" : " | ");
        os += "CONTRAST";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_1::CameraParam::CONTRAST;
    }
    if ((o & ::android::hardware::automotive::evs::V1_1::CameraParam::AUTOGAIN) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_1::CameraParam::AUTOGAIN)) {
        os += (first ? "" : " | ");
        os += "AUTOGAIN";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_1::CameraParam::AUTOGAIN;
    }
    if ((o & ::android::hardware::automotive::evs::V1_1::CameraParam::GAIN) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_1::CameraParam::GAIN)) {
        os += (first ? "" : " | ");
        os += "GAIN";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_1::CameraParam::GAIN;
    }
    if ((o & ::android::hardware::automotive::evs::V1_1::CameraParam::AUTO_WHITE_BALANCE) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_1::CameraParam::AUTO_WHITE_BALANCE)) {
        os += (first ? "" : " | ");
        os += "AUTO_WHITE_BALANCE";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_1::CameraParam::AUTO_WHITE_BALANCE;
    }
    if ((o & ::android::hardware::automotive::evs::V1_1::CameraParam::WHITE_BALANCE_TEMPERATURE) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_1::CameraParam::WHITE_BALANCE_TEMPERATURE)) {
        os += (first ? "" : " | ");
        os += "WHITE_BALANCE_TEMPERATURE";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_1::CameraParam::WHITE_BALANCE_TEMPERATURE;
    }
    if ((o & ::android::hardware::automotive::evs::V1_1::CameraParam::SHARPNESS) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_1::CameraParam::SHARPNESS)) {
        os += (first ? "" : " | ");
        os += "SHARPNESS";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_1::CameraParam::SHARPNESS;
    }
    if ((o & ::android::hardware::automotive::evs::V1_1::CameraParam::AUTO_EXPOSURE) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_1::CameraParam::AUTO_EXPOSURE)) {
        os += (first ? "" : " | ");
        os += "AUTO_EXPOSURE";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_1::CameraParam::AUTO_EXPOSURE;
    }
    if ((o & ::android::hardware::automotive::evs::V1_1::CameraParam::ABSOLUTE_EXPOSURE) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_1::CameraParam::ABSOLUTE_EXPOSURE)) {
        os += (first ? "" : " | ");
        os += "ABSOLUTE_EXPOSURE";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_1::CameraParam::ABSOLUTE_EXPOSURE;
    }
    if ((o & ::android::hardware::automotive::evs::V1_1::CameraParam::ABSOLUTE_FOCUS) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_1::CameraParam::ABSOLUTE_FOCUS)) {
        os += (first ? "" : " | ");
        os += "ABSOLUTE_FOCUS";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_1::CameraParam::ABSOLUTE_FOCUS;
    }
    if ((o & ::android::hardware::automotive::evs::V1_1::CameraParam::AUTO_FOCUS) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_1::CameraParam::AUTO_FOCUS)) {
        os += (first ? "" : " | ");
        os += "AUTO_FOCUS";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_1::CameraParam::AUTO_FOCUS;
    }
    if ((o & ::android::hardware::automotive::evs::V1_1::CameraParam::ABSOLUTE_ZOOM) == static_cast<uint32_t>(::android::hardware::automotive::evs::V1_1::CameraParam::ABSOLUTE_ZOOM)) {
        os += (first ? "" : " | ");
        os += "ABSOLUTE_ZOOM";
        first = false;
        flipped |= ::android::hardware::automotive::evs::V1_1::CameraParam::ABSOLUTE_ZOOM;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::automotive::evs::V1_1::CameraParam o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::automotive::evs::V1_1::CameraParam::BRIGHTNESS) {
        return "BRIGHTNESS";
    }
    if (o == ::android::hardware::automotive::evs::V1_1::CameraParam::CONTRAST) {
        return "CONTRAST";
    }
    if (o == ::android::hardware::automotive::evs::V1_1::CameraParam::AUTOGAIN) {
        return "AUTOGAIN";
    }
    if (o == ::android::hardware::automotive::evs::V1_1::CameraParam::GAIN) {
        return "GAIN";
    }
    if (o == ::android::hardware::automotive::evs::V1_1::CameraParam::AUTO_WHITE_BALANCE) {
        return "AUTO_WHITE_BALANCE";
    }
    if (o == ::android::hardware::automotive::evs::V1_1::CameraParam::WHITE_BALANCE_TEMPERATURE) {
        return "WHITE_BALANCE_TEMPERATURE";
    }
    if (o == ::android::hardware::automotive::evs::V1_1::CameraParam::SHARPNESS) {
        return "SHARPNESS";
    }
    if (o == ::android::hardware::automotive::evs::V1_1::CameraParam::AUTO_EXPOSURE) {
        return "AUTO_EXPOSURE";
    }
    if (o == ::android::hardware::automotive::evs::V1_1::CameraParam::ABSOLUTE_EXPOSURE) {
        return "ABSOLUTE_EXPOSURE";
    }
    if (o == ::android::hardware::automotive::evs::V1_1::CameraParam::ABSOLUTE_FOCUS) {
        return "ABSOLUTE_FOCUS";
    }
    if (o == ::android::hardware::automotive::evs::V1_1::CameraParam::AUTO_FOCUS) {
        return "AUTO_FOCUS";
    }
    if (o == ::android::hardware::automotive::evs::V1_1::CameraParam::ABSOLUTE_ZOOM) {
        return "ABSOLUTE_ZOOM";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::automotive::evs::V1_1::CameraParam o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".ultrasonicsArrayId = ";
    os += ::android::hardware::toString(o.ultrasonicsArrayId);
    os += ", .maxReadingsPerSensorCount = ";
    os += ::android::hardware::toString(o.maxReadingsPerSensorCount);
    os += ", .maxReceiversCount = ";
    os += ::android::hardware::toString(o.maxReceiversCount);
    os += ", .sensors = ";
    os += ::android::hardware::toString(o.sensors);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc& lhs, const ::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc& rhs) {
    if (lhs.ultrasonicsArrayId != rhs.ultrasonicsArrayId) {
        return false;
    }
    if (lhs.maxReadingsPerSensorCount != rhs.maxReadingsPerSensorCount) {
        return false;
    }
    if (lhs.maxReceiversCount != rhs.maxReceiversCount) {
        return false;
    }
    if (lhs.sensors != rhs.sensors) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc& lhs, const ::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::evs::V1_1::RotationQuat& o) {
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

static inline void PrintTo(const ::android::hardware::automotive::evs::V1_1::RotationQuat& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::evs::V1_1::RotationQuat& lhs, const ::android::hardware::automotive::evs::V1_1::RotationQuat& rhs) {
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

static inline bool operator!=(const ::android::hardware::automotive::evs::V1_1::RotationQuat& lhs, const ::android::hardware::automotive::evs::V1_1::RotationQuat& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::evs::V1_1::Translation& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".x = ";
    os += ::android::hardware::toString(o.x);
    os += ", .y = ";
    os += ::android::hardware::toString(o.y);
    os += ", .z = ";
    os += ::android::hardware::toString(o.z);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::evs::V1_1::Translation& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::evs::V1_1::Translation& lhs, const ::android::hardware::automotive::evs::V1_1::Translation& rhs) {
    if (lhs.x != rhs.x) {
        return false;
    }
    if (lhs.y != rhs.y) {
        return false;
    }
    if (lhs.z != rhs.z) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::evs::V1_1::Translation& lhs, const ::android::hardware::automotive::evs::V1_1::Translation& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::evs::V1_1::SensorPose& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".rotation = ";
    os += ::android::hardware::automotive::evs::V1_1::toString(o.rotation);
    os += ", .translation = ";
    os += ::android::hardware::automotive::evs::V1_1::toString(o.translation);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::evs::V1_1::SensorPose& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::evs::V1_1::SensorPose& lhs, const ::android::hardware::automotive::evs::V1_1::SensorPose& rhs) {
    if (lhs.rotation != rhs.rotation) {
        return false;
    }
    if (lhs.translation != rhs.translation) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::evs::V1_1::SensorPose& lhs, const ::android::hardware::automotive::evs::V1_1::SensorPose& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::evs::V1_1::UltrasonicSensor& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".pose = ";
    os += ::android::hardware::automotive::evs::V1_1::toString(o.pose);
    os += ", .maxRange = ";
    os += ::android::hardware::toString(o.maxRange);
    os += ", .angleOfMeasurement = ";
    os += ::android::hardware::toString(o.angleOfMeasurement);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::evs::V1_1::UltrasonicSensor& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::automotive::evs::V1_1::UltrasonicSensor& lhs, const ::android::hardware::automotive::evs::V1_1::UltrasonicSensor& rhs) {
    if (lhs.pose != rhs.pose) {
        return false;
    }
    if (lhs.maxRange != rhs.maxRange) {
        return false;
    }
    if (lhs.angleOfMeasurement != rhs.angleOfMeasurement) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::automotive::evs::V1_1::UltrasonicSensor& lhs, const ::android::hardware::automotive::evs::V1_1::UltrasonicSensor& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::automotive::evs::V1_1::UltrasonicsDataFrameDesc& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".timestampNs = ";
    os += ::android::hardware::toString(o.timestampNs);
    os += ", .dataFrameId = ";
    os += ::android::hardware::toString(o.dataFrameId);
    os += ", .transmittersIdList = ";
    os += ::android::hardware::toString(o.transmittersIdList);
    os += ", .receiversIdList = ";
    os += ::android::hardware::toString(o.receiversIdList);
    os += ", .receiversReadingsCountList = ";
    os += ::android::hardware::toString(o.receiversReadingsCountList);
    os += ", .waveformsData = ";
    os += ::android::hardware::toString(o.waveformsData);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::automotive::evs::V1_1::UltrasonicsDataFrameDesc& o, ::std::ostream* os) {
    *os << toString(o);
}

// operator== and operator!= are not generated for UltrasonicsDataFrameDesc


}  // namespace V1_1
}  // namespace evs
}  // namespace automotive
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
template<> inline constexpr std::array<::android::hardware::automotive::evs::V1_1::EvsEventType, 7> hidl_enum_values<::android::hardware::automotive::evs::V1_1::EvsEventType> = {
    ::android::hardware::automotive::evs::V1_1::EvsEventType::STREAM_STARTED,
    ::android::hardware::automotive::evs::V1_1::EvsEventType::STREAM_STOPPED,
    ::android::hardware::automotive::evs::V1_1::EvsEventType::FRAME_DROPPED,
    ::android::hardware::automotive::evs::V1_1::EvsEventType::TIMEOUT,
    ::android::hardware::automotive::evs::V1_1::EvsEventType::PARAMETER_CHANGED,
    ::android::hardware::automotive::evs::V1_1::EvsEventType::MASTER_RELEASED,
    ::android::hardware::automotive::evs::V1_1::EvsEventType::STREAM_ERROR,
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
template<> inline constexpr std::array<::android::hardware::automotive::evs::V1_1::CameraParam, 12> hidl_enum_values<::android::hardware::automotive::evs::V1_1::CameraParam> = {
    ::android::hardware::automotive::evs::V1_1::CameraParam::BRIGHTNESS,
    ::android::hardware::automotive::evs::V1_1::CameraParam::CONTRAST,
    ::android::hardware::automotive::evs::V1_1::CameraParam::AUTOGAIN,
    ::android::hardware::automotive::evs::V1_1::CameraParam::GAIN,
    ::android::hardware::automotive::evs::V1_1::CameraParam::AUTO_WHITE_BALANCE,
    ::android::hardware::automotive::evs::V1_1::CameraParam::WHITE_BALANCE_TEMPERATURE,
    ::android::hardware::automotive::evs::V1_1::CameraParam::SHARPNESS,
    ::android::hardware::automotive::evs::V1_1::CameraParam::AUTO_EXPOSURE,
    ::android::hardware::automotive::evs::V1_1::CameraParam::ABSOLUTE_EXPOSURE,
    ::android::hardware::automotive::evs::V1_1::CameraParam::ABSOLUTE_FOCUS,
    ::android::hardware::automotive::evs::V1_1::CameraParam::AUTO_FOCUS,
    ::android::hardware::automotive::evs::V1_1::CameraParam::ABSOLUTE_ZOOM,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_TYPES_H
