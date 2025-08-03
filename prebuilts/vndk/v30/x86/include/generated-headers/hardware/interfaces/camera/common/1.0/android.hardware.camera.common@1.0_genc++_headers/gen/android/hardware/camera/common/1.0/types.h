#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_COMMON_V1_0_TYPES_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_COMMON_V1_0_TYPES_H

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace camera {
namespace common {
namespace V1_0 {

// Forward declaration for forward reference support:
enum class Status : uint32_t;
enum class TorchMode : uint32_t;
enum class CameraMetadataType : uint32_t;
struct VendorTag;
struct VendorTagSection;
enum class TagBoundaryId : uint32_t;
enum class CameraDeviceStatus : uint32_t;
enum class TorchModeStatus : uint32_t;
struct CameraResourceCost;

/**
 * Common enumeration and structure definitions for all HALs under
 * android.hardware.camera
 *
 *
 * Status codes for camera HAL method calls.
 *
 */
enum class Status : uint32_t {
    /**
     * Method call succeeded
     */
    OK = 0u,
    /**
     * One of the arguments to the method call is invalid. For example,
     * the camera ID is unknown.
     */
    ILLEGAL_ARGUMENT = 1u,
    /**
     * The specified camera device is already in use
     */
    CAMERA_IN_USE = 2u,
    /**
     * The HAL cannot support more simultaneous cameras in use.
     */
    MAX_CAMERAS_IN_USE = 3u,
    /**
     * This HAL does not support this method.
     */
    METHOD_NOT_SUPPORTED = 4u,
    /**
     * The specified camera device does not support this operation.
     */
    OPERATION_NOT_SUPPORTED = 5u,
    /**
     * This camera device is no longer connected or otherwise available for use
     */
    CAMERA_DISCONNECTED = 6u,
    /**
     * The HAL has encountered an internal error and cannot complete the
     * request.
     */
    INTERNAL_ERROR = 7u,
};

/**
 * Possible states that the flash unit on a closed camera device can be set to
 * via the ICameraProvider::setTorchMode() method.
 */
enum class TorchMode : uint32_t {
    OFF = 0u,
    ON = 1u,
};

/**
 * Camera metadata type - duplicated from VNDK libcamera_metadata for vendor tag
 * definitions.
 */
enum class CameraMetadataType : uint32_t {
    BYTE = 0u,
    INT32 = 1u,
    FLOAT = 2u,
    INT64 = 3u,
    DOUBLE = 4u,
    RATIONAL = 5u,
};

/**
 * A single vendor-unique metadata tag.
 * The full name of the tag is <sectionName>.<tagName>
 */
struct VendorTag final {
    uint32_t tagId __attribute__ ((aligned(4)));
    ::android::hardware::hidl_string tagName __attribute__ ((aligned(8)));
    ::android::hardware::camera::common::V1_0::CameraMetadataType tagType __attribute__ ((aligned(4)));
};

static_assert(offsetof(::android::hardware::camera::common::V1_0::VendorTag, tagId) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::camera::common::V1_0::VendorTag, tagName) == 8, "wrong offset");
static_assert(offsetof(::android::hardware::camera::common::V1_0::VendorTag, tagType) == 24, "wrong offset");
static_assert(sizeof(::android::hardware::camera::common::V1_0::VendorTag) == 32, "wrong size");
static_assert(__alignof(::android::hardware::camera::common::V1_0::VendorTag) == 8, "wrong alignment");

/**
 * A set of related vendor tags.
 */
struct VendorTagSection final {
    ::android::hardware::hidl_string sectionName __attribute__ ((aligned(8)));
    ::android::hardware::hidl_vec<::android::hardware::camera::common::V1_0::VendorTag> tags __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::camera::common::V1_0::VendorTagSection, sectionName) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::camera::common::V1_0::VendorTagSection, tags) == 16, "wrong offset");
static_assert(sizeof(::android::hardware::camera::common::V1_0::VendorTagSection) == 32, "wrong size");
static_assert(__alignof(::android::hardware::camera::common::V1_0::VendorTagSection) == 8, "wrong alignment");

enum class TagBoundaryId : uint32_t {
    AOSP = 0u /* 0x0 */,
    VENDOR = 2147483648u /* 0x80000000u */,
};

/**
 * CameraDeviceStatus
 *
 * The current status of a camera device, as sent by a camera provider HAL
 * through the ICameraProviderCallback::cameraDeviceStatusChange() call.
 *
 * At startup, the camera service must assume all internal camera devices listed
 * by ICameraProvider::getCameraIdList() are in the PRESENT state. The provider
 * must invoke ICameraProviderCallback::cameraDeviceStatusChange to inform the
 * service of any initially NOT_PRESENT internal devices, and of any PRESENT
 * external camera devices, as soon as the camera service has called
 * ICameraProvider::setCallback().
 *
 * Allowed state transitions:
 *      PRESENT            -> NOT_PRESENT
 *      NOT_PRESENT        -> ENUMERATING
 *      NOT_PRESENT        -> PRESENT
 *      ENUMERATING        -> PRESENT
 *      ENUMERATING        -> NOT_PRESENT
 */
enum class CameraDeviceStatus : uint32_t {
    /**
     * The camera device is not currently connected, and trying to reference it
     * in provider method calls must return status code ILLEGAL_ARGUMENT.
     *
     */
    NOT_PRESENT = 0u,
    /**
     * The camera device is connected, and opening it is possible, as long as
     * sufficient resources are available.
     *
     * By default, the framework must assume all devices returned by
     * ICameraProvider::getCameraIdList() are in this state.
     */
    PRESENT = 1u,
    /**
     * The camera device is connected, but it is undergoing enumeration and
     * startup, and so opening the device must return CAMERA_IN_USE.
     *
     * Attempting to call ICameraProvider::getCameraCharacteristics() must
     * succeed, however.
     */
    ENUMERATING = 2u,
};

/**
 * TorchModeStatus:
 *
 * The current status of the torch mode on a given camera device, sent by a
 * camera provider HAL via the ICameraProviderCallback::TorchModeStatusChange()
 * call.
 *
 * The torch mode status of a camera device is applicable only when the camera
 * device is present. The camera service must not call
 * ICameraProvider::setTorchMode() to turn on torch mode of a camera device if
 * the camera device is not present. At camera service startup time, the
 * framework must assume torch modes are in the AVAILABLE_OFF state if the
 * camera device is present and the camera characteristics entry
 * android.flash.info.available is reported as true via
 * ICameraProvider::getCameraCharacteristics() call. The same is assumed for
 * external camera devices when they are initially connected.
 *
 * The camera service requires the following behaviors from the camera provider
 * HAL when a camera device's status changes:
 *
 *  1. A previously-disconnected camera device becomes connected. After
 *      ICameraProviderCallback::CameraDeviceStatusChange() is invoked to inform
 *      the camera service that the camera device is present, the framework must
 *      assume the camera device's torch mode is in AVAILABLE_OFF state if it
 *      has a flash unit. The camera provider HAL does not need to invoke
 *      ICameraProviderCallback::TorchModeStatusChange() unless the flash unit
 *      is unavailable to use by ICameraProvider::setTorchMode().
 *
 *  2. A previously-connected camera becomes disconnected. After
 *      ICameraProviderCallback::CameraDeviceStatusChange() is invoked to inform
 *      the camera service that the camera device is not present, the framework
 *      must not call ICameraProvider::setTorchMode() for the disconnected camera
 *      device until it is connected again. The camera provider HAL does not
 *      need to invoke ICameraProviderCallback::TorchModeStatusChange()
 *      separately to inform that the flash unit has become NOT_AVAILABLE.
 *
 *  3. openCameraDevice() or openCameraDeviceVersion() is called to open a
 *      camera device. The camera provider HAL must invoke
 *      ICameraProviderCallback::TorchModeStatusChange() for all flash units
 *      that have entered NOT_AVAILABLE state and can not be turned on by
 *      calling ICameraProvider::setTorchMode() due to this open() call.
 *      openCameraDevice() must not trigger AVAILABLE_OFF before NOT_AVAILABLE
 *      for all flash units that have become unavailable.
 *
 *  4. ICameraDevice.close() is called to close a camera device. The camera
 *      provider HAL must call ICameraProviderCallback::torchModeStatusChange()
 *      for all flash units that have now entered the AVAILABLE_OFF state and
 *      can be turned on by calling ICameraProvider::setTorchMode() again because
 *      of sufficient new camera resources being freed up by this close() call.
 *
 *  Note that the camera service calling ICameraProvider::setTorchMode()
 *  successfully must trigger AVAILABLE_OFF or AVAILABLE_ON callback for the
 *  given camera device. Additionally it must trigger AVAILABLE_OFF callbacks
 *  for other previously-on torch modes if HAL cannot keep multiple devices'
 *  flashlights on simultaneously.
 */
enum class TorchModeStatus : uint32_t {
    /**
     * The flash unit is no longer available and the torch mode can not be
     * turned on by calling setTorchMode(). If the torch mode was AVAILABLE_ON,
     * the flashlight must be turned off by the provider HAL before the provider
     * HAL calls torchModeStatusChange().
     */
    NOT_AVAILABLE = 0u,
    /**
     * A torch mode has become off and is available to be turned on via
     * ICameraProvider::setTorchMode(). This may happen in the following
     * cases:
     *   1. After the resources to turn on the torch mode have become available.
     *   2. After ICameraProvider::setTorchMode() is called to turn off the torch
     *      mode.
     *   3. After the camera service turned on the torch mode for some other
     *      camera device and the provider HAL had to turn off the torch modes
     *      of other camera device(s) that were previously on, due to lack of
     *      resources to keep them all on.
     */
    AVAILABLE_OFF = 1u,
    /**
     * A torch mode has become on and is available to be turned off via
     * ICameraProvider::setTorchMode(). This can happen only after
     * ICameraProvider::setTorchMode() has been called to turn on the torch mode.
     */
    AVAILABLE_ON = 2u,
};

/**
 * CameraResourceCost:
 *
 * Structure defining the abstract resource cost of opening a camera device,
 * and any usage conflicts between multiple camera devices.
 *
 * Obtainable via ICameraDevice::getResourceCost()
 */
struct CameraResourceCost final {
    /**
     * The total resource "cost" of using this camera, represented as an integer
     * value in the range [0, 100] where 100 represents total usage of the
     * shared resource that is the limiting bottleneck of the camera subsystem.
     * This may be a very rough estimate, and is used as a hint to the camera
     * service to determine when to disallow multiple applications from
     * simultaneously opening different cameras advertised by the camera
     * service.
     *
     * The camera service must be able to simultaneously open and use any
     * combination of camera devices exposed by the HAL where the sum of
     * the resource costs of these cameras is <= 100. For determining cost,
     * each camera device must be assumed to be configured and operating at
     * the maximally resource-consuming framerate and stream size settings
     * available in the configuration settings exposed for that device through
     * the camera metadata.
     *
     * The camera service may still attempt to simultaneously open combinations
     * of camera devices with a total resource cost > 100. This may succeed or
     * fail. If this succeeds, combinations of configurations that are not
     * supported due to resource constraints from having multiple open devices
     * must fail during the configure calls. If the total resource cost is <=
     * 100, open and configure must never fail for any stream configuration
     * settings or other device capabilities that would normally succeed for a
     * device when it is the only open camera device.
     *
     * This field may be used to determine whether background applications are
     * allowed to use this camera device while other applications are using
     * other camera devices. Note: multiple applications must never be allowed
     * by the camera service to simultaneously open the same camera device.
     *
     * Example use cases:
     *
     * Ex. 1: Camera Device 0 = Back Camera
     *        Camera Device 1 = Front Camera
     *   - Using both camera devices causes a large framerate slowdown due to
     *     limited ISP bandwidth.
     *
     *   Configuration:
     *
     *   Camera Device 0 - resourceCost = 51
     *                     conflicting_devices = empty
     *   Camera Device 1 - resourceCost = 51
     *                     conflicting_devices = empty
     *
     *   Result:
     *
     *   Since the sum of the resource costs is > 100, if a higher-priority
     *   application has either device open, no lower-priority applications must
     *   be allowed by the camera service to open either device. If a
     *   lower-priority application is using a device that a higher-priority
     *   subsequently attempts to open, the lower-priority application must be
     *   forced to disconnect the the device.
     *
     *   If the highest-priority application chooses, it may still attempt to
     *   open both devices (since these devices are not listed as conflicting in
     *   the conflicting_devices fields), but usage of these devices may fail in
     *   the open or configure calls.
     *
     * Ex. 2: Camera Device 0 = Left Back Camera
     *        Camera Device 1 = Right Back Camera
     *        Camera Device 2 = Combined stereo camera using both right and left
     *                          back camera sensors used by devices 0, and 1
     *        Camera Device 3 = Front Camera
     *   - Due to do hardware constraints, up to two cameras may be open at
     *     once. The combined stereo camera may never be used at the same time
     *     as either of the two back camera devices (device 0, 1), and typically
     *     requires too much bandwidth to use at the same time as the front
     *     camera (device 3).
     *
     *   Configuration:
     *
     *   Camera Device 0 - resourceCost = 50
     *                     conflicting_devices = { 2 }
     *   Camera Device 1 - resourceCost = 50
     *                     conflicting_devices = { 2 }
     *   Camera Device 2 - resourceCost = 100
     *                     conflicting_devices = { 0, 1 }
     *   Camera Device 3 - resourceCost = 50
     *                     conflicting_devices = empty
     *
     *   Result:
     *
     *   Based on the conflicting_devices fields, the camera service guarantees
     *   that the following sets of open devices must never be allowed: { 1, 2
     *   }, { 0, 2 }.
     *
     *   Based on the resourceCost fields, if a high-priority foreground
     *   application is using camera device 0, a background application would be
     *   allowed to open camera device 1 or 3 (but would be forced to disconnect
     *   it again if the foreground application opened another device).
     *
     *   The highest priority application may still attempt to simultaneously
     *   open devices 0, 2, and 3, but the HAL may fail in open or configure
     *   calls for this combination.
     *
     * Ex. 3: Camera Device 0 = Back Camera
     *        Camera Device 1 = Front Camera
     *        Camera Device 2 = Low-power Front Camera that uses the same sensor
     *                          as device 1, but only exposes image stream
     *                          resolutions that can be used in low-power mode
     *  - Using both front cameras (device 1, 2) at the same time is impossible
     *    due a shared physical sensor. Using the back and "high-power" front
     *    camera (device 1) may be impossible for some stream configurations due
     *    to hardware limitations, but the "low-power" front camera option may
     *    always be used as it has special dedicated hardware.
     *
     *   Configuration:
     *
     *   Camera Device 0 - resourceCost = 100
     *                     conflicting_devices = empty
     *   Camera Device 1 - resourceCost = 100
     *                     conflicting_devices = { 2 }
     *   Camera Device 2 - resourceCost = 0
     *                     conflicting_devices = { 1 }
     *   Result:
     *
     *   Based on the conflicting_devices fields, the camera service guarantees
     *   that the following sets of open devices must never be allowed:
     *   { 1, 2 }.
     *
     *   Based on the resourceCost fields, only the highest priority application
     *   may attempt to open both device 0 and 1 at the same time. If a
     *   higher-priority application is not using device 1 or 2, a low-priority
     *   background application may open device 2 (but must be forced to
     *   disconnect it if a higher-priority application subsequently opens
     *   device 1 or 2).
     */
    uint32_t resourceCost __attribute__ ((aligned(4)));
    /**
     * An array of camera device IDs indicating other devices that cannot be
     * simultaneously opened while this camera device is in use.
     *
     * This field is intended to be used to indicate that this camera device
     * is a composite of several other camera devices, or otherwise has
     * hardware dependencies that prohibit simultaneous usage. If there are no
     * dependencies, an empty list may be returned to indicate this.
     *
     * The camera service must never simultaneously open any of the devices
     * in this list while this camera device is open.
     *
     */
    ::android::hardware::hidl_vec<::android::hardware::hidl_string> conflictingDevices __attribute__ ((aligned(8)));
};

static_assert(offsetof(::android::hardware::camera::common::V1_0::CameraResourceCost, resourceCost) == 0, "wrong offset");
static_assert(offsetof(::android::hardware::camera::common::V1_0::CameraResourceCost, conflictingDevices) == 8, "wrong offset");
static_assert(sizeof(::android::hardware::camera::common::V1_0::CameraResourceCost) == 24, "wrong size");
static_assert(__alignof(::android::hardware::camera::common::V1_0::CameraResourceCost) == 8, "wrong alignment");

//
// type declarations for package
//

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::common::V1_0::Status o);
static inline void PrintTo(::android::hardware::camera::common::V1_0::Status o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::common::V1_0::Status lhs, const ::android::hardware::camera::common::V1_0::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::common::V1_0::Status rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::common::V1_0::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::common::V1_0::Status lhs, const ::android::hardware::camera::common::V1_0::Status rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::common::V1_0::Status rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::common::V1_0::Status lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::common::V1_0::Status e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::common::V1_0::Status e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::common::V1_0::TorchMode o);
static inline void PrintTo(::android::hardware::camera::common::V1_0::TorchMode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::common::V1_0::TorchMode lhs, const ::android::hardware::camera::common::V1_0::TorchMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::common::V1_0::TorchMode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::common::V1_0::TorchMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::common::V1_0::TorchMode lhs, const ::android::hardware::camera::common::V1_0::TorchMode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::common::V1_0::TorchMode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::common::V1_0::TorchMode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::common::V1_0::TorchMode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::common::V1_0::TorchMode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::common::V1_0::CameraMetadataType o);
static inline void PrintTo(::android::hardware::camera::common::V1_0::CameraMetadataType o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::common::V1_0::CameraMetadataType lhs, const ::android::hardware::camera::common::V1_0::CameraMetadataType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::common::V1_0::CameraMetadataType rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::common::V1_0::CameraMetadataType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::common::V1_0::CameraMetadataType lhs, const ::android::hardware::camera::common::V1_0::CameraMetadataType rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::common::V1_0::CameraMetadataType rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::common::V1_0::CameraMetadataType lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::common::V1_0::CameraMetadataType e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::common::V1_0::CameraMetadataType e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::camera::common::V1_0::VendorTag& o);
static inline void PrintTo(const ::android::hardware::camera::common::V1_0::VendorTag& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::camera::common::V1_0::VendorTag& lhs, const ::android::hardware::camera::common::V1_0::VendorTag& rhs);
static inline bool operator!=(const ::android::hardware::camera::common::V1_0::VendorTag& lhs, const ::android::hardware::camera::common::V1_0::VendorTag& rhs);

static inline std::string toString(const ::android::hardware::camera::common::V1_0::VendorTagSection& o);
static inline void PrintTo(const ::android::hardware::camera::common::V1_0::VendorTagSection& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::camera::common::V1_0::VendorTagSection& lhs, const ::android::hardware::camera::common::V1_0::VendorTagSection& rhs);
static inline bool operator!=(const ::android::hardware::camera::common::V1_0::VendorTagSection& lhs, const ::android::hardware::camera::common::V1_0::VendorTagSection& rhs);

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::common::V1_0::TagBoundaryId o);
static inline void PrintTo(::android::hardware::camera::common::V1_0::TagBoundaryId o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::common::V1_0::TagBoundaryId lhs, const ::android::hardware::camera::common::V1_0::TagBoundaryId rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::common::V1_0::TagBoundaryId rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::common::V1_0::TagBoundaryId lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::common::V1_0::TagBoundaryId lhs, const ::android::hardware::camera::common::V1_0::TagBoundaryId rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::common::V1_0::TagBoundaryId rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::common::V1_0::TagBoundaryId lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::common::V1_0::TagBoundaryId e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::common::V1_0::TagBoundaryId e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::common::V1_0::CameraDeviceStatus o);
static inline void PrintTo(::android::hardware::camera::common::V1_0::CameraDeviceStatus o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::common::V1_0::CameraDeviceStatus lhs, const ::android::hardware::camera::common::V1_0::CameraDeviceStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::common::V1_0::CameraDeviceStatus rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::common::V1_0::CameraDeviceStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::common::V1_0::CameraDeviceStatus lhs, const ::android::hardware::camera::common::V1_0::CameraDeviceStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::common::V1_0::CameraDeviceStatus rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::common::V1_0::CameraDeviceStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::common::V1_0::CameraDeviceStatus e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::common::V1_0::CameraDeviceStatus e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::camera::common::V1_0::TorchModeStatus o);
static inline void PrintTo(::android::hardware::camera::common::V1_0::TorchModeStatus o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::camera::common::V1_0::TorchModeStatus lhs, const ::android::hardware::camera::common::V1_0::TorchModeStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::camera::common::V1_0::TorchModeStatus rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::camera::common::V1_0::TorchModeStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::camera::common::V1_0::TorchModeStatus lhs, const ::android::hardware::camera::common::V1_0::TorchModeStatus rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::camera::common::V1_0::TorchModeStatus rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::camera::common::V1_0::TorchModeStatus lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::camera::common::V1_0::TorchModeStatus e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::camera::common::V1_0::TorchModeStatus e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::camera::common::V1_0::CameraResourceCost& o);
static inline void PrintTo(const ::android::hardware::camera::common::V1_0::CameraResourceCost& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::camera::common::V1_0::CameraResourceCost& lhs, const ::android::hardware::camera::common::V1_0::CameraResourceCost& rhs);
static inline bool operator!=(const ::android::hardware::camera::common::V1_0::CameraResourceCost& lhs, const ::android::hardware::camera::common::V1_0::CameraResourceCost& rhs);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::camera::common::V1_0::Status>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::common::V1_0::Status> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::common::V1_0::Status::OK) == static_cast<uint32_t>(::android::hardware::camera::common::V1_0::Status::OK)) {
        os += (first ? "" : " | ");
        os += "OK";
        first = false;
        flipped |= ::android::hardware::camera::common::V1_0::Status::OK;
    }
    if ((o & ::android::hardware::camera::common::V1_0::Status::ILLEGAL_ARGUMENT) == static_cast<uint32_t>(::android::hardware::camera::common::V1_0::Status::ILLEGAL_ARGUMENT)) {
        os += (first ? "" : " | ");
        os += "ILLEGAL_ARGUMENT";
        first = false;
        flipped |= ::android::hardware::camera::common::V1_0::Status::ILLEGAL_ARGUMENT;
    }
    if ((o & ::android::hardware::camera::common::V1_0::Status::CAMERA_IN_USE) == static_cast<uint32_t>(::android::hardware::camera::common::V1_0::Status::CAMERA_IN_USE)) {
        os += (first ? "" : " | ");
        os += "CAMERA_IN_USE";
        first = false;
        flipped |= ::android::hardware::camera::common::V1_0::Status::CAMERA_IN_USE;
    }
    if ((o & ::android::hardware::camera::common::V1_0::Status::MAX_CAMERAS_IN_USE) == static_cast<uint32_t>(::android::hardware::camera::common::V1_0::Status::MAX_CAMERAS_IN_USE)) {
        os += (first ? "" : " | ");
        os += "MAX_CAMERAS_IN_USE";
        first = false;
        flipped |= ::android::hardware::camera::common::V1_0::Status::MAX_CAMERAS_IN_USE;
    }
    if ((o & ::android::hardware::camera::common::V1_0::Status::METHOD_NOT_SUPPORTED) == static_cast<uint32_t>(::android::hardware::camera::common::V1_0::Status::METHOD_NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "METHOD_NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::camera::common::V1_0::Status::METHOD_NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::camera::common::V1_0::Status::OPERATION_NOT_SUPPORTED) == static_cast<uint32_t>(::android::hardware::camera::common::V1_0::Status::OPERATION_NOT_SUPPORTED)) {
        os += (first ? "" : " | ");
        os += "OPERATION_NOT_SUPPORTED";
        first = false;
        flipped |= ::android::hardware::camera::common::V1_0::Status::OPERATION_NOT_SUPPORTED;
    }
    if ((o & ::android::hardware::camera::common::V1_0::Status::CAMERA_DISCONNECTED) == static_cast<uint32_t>(::android::hardware::camera::common::V1_0::Status::CAMERA_DISCONNECTED)) {
        os += (first ? "" : " | ");
        os += "CAMERA_DISCONNECTED";
        first = false;
        flipped |= ::android::hardware::camera::common::V1_0::Status::CAMERA_DISCONNECTED;
    }
    if ((o & ::android::hardware::camera::common::V1_0::Status::INTERNAL_ERROR) == static_cast<uint32_t>(::android::hardware::camera::common::V1_0::Status::INTERNAL_ERROR)) {
        os += (first ? "" : " | ");
        os += "INTERNAL_ERROR";
        first = false;
        flipped |= ::android::hardware::camera::common::V1_0::Status::INTERNAL_ERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::common::V1_0::Status o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::common::V1_0::Status::OK) {
        return "OK";
    }
    if (o == ::android::hardware::camera::common::V1_0::Status::ILLEGAL_ARGUMENT) {
        return "ILLEGAL_ARGUMENT";
    }
    if (o == ::android::hardware::camera::common::V1_0::Status::CAMERA_IN_USE) {
        return "CAMERA_IN_USE";
    }
    if (o == ::android::hardware::camera::common::V1_0::Status::MAX_CAMERAS_IN_USE) {
        return "MAX_CAMERAS_IN_USE";
    }
    if (o == ::android::hardware::camera::common::V1_0::Status::METHOD_NOT_SUPPORTED) {
        return "METHOD_NOT_SUPPORTED";
    }
    if (o == ::android::hardware::camera::common::V1_0::Status::OPERATION_NOT_SUPPORTED) {
        return "OPERATION_NOT_SUPPORTED";
    }
    if (o == ::android::hardware::camera::common::V1_0::Status::CAMERA_DISCONNECTED) {
        return "CAMERA_DISCONNECTED";
    }
    if (o == ::android::hardware::camera::common::V1_0::Status::INTERNAL_ERROR) {
        return "INTERNAL_ERROR";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::common::V1_0::Status o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::common::V1_0::TorchMode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::common::V1_0::TorchMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::common::V1_0::TorchMode::OFF) == static_cast<uint32_t>(::android::hardware::camera::common::V1_0::TorchMode::OFF)) {
        os += (first ? "" : " | ");
        os += "OFF";
        first = false;
        flipped |= ::android::hardware::camera::common::V1_0::TorchMode::OFF;
    }
    if ((o & ::android::hardware::camera::common::V1_0::TorchMode::ON) == static_cast<uint32_t>(::android::hardware::camera::common::V1_0::TorchMode::ON)) {
        os += (first ? "" : " | ");
        os += "ON";
        first = false;
        flipped |= ::android::hardware::camera::common::V1_0::TorchMode::ON;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::common::V1_0::TorchMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::common::V1_0::TorchMode::OFF) {
        return "OFF";
    }
    if (o == ::android::hardware::camera::common::V1_0::TorchMode::ON) {
        return "ON";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::common::V1_0::TorchMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::common::V1_0::CameraMetadataType>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::common::V1_0::CameraMetadataType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::common::V1_0::CameraMetadataType::BYTE) == static_cast<uint32_t>(::android::hardware::camera::common::V1_0::CameraMetadataType::BYTE)) {
        os += (first ? "" : " | ");
        os += "BYTE";
        first = false;
        flipped |= ::android::hardware::camera::common::V1_0::CameraMetadataType::BYTE;
    }
    if ((o & ::android::hardware::camera::common::V1_0::CameraMetadataType::INT32) == static_cast<uint32_t>(::android::hardware::camera::common::V1_0::CameraMetadataType::INT32)) {
        os += (first ? "" : " | ");
        os += "INT32";
        first = false;
        flipped |= ::android::hardware::camera::common::V1_0::CameraMetadataType::INT32;
    }
    if ((o & ::android::hardware::camera::common::V1_0::CameraMetadataType::FLOAT) == static_cast<uint32_t>(::android::hardware::camera::common::V1_0::CameraMetadataType::FLOAT)) {
        os += (first ? "" : " | ");
        os += "FLOAT";
        first = false;
        flipped |= ::android::hardware::camera::common::V1_0::CameraMetadataType::FLOAT;
    }
    if ((o & ::android::hardware::camera::common::V1_0::CameraMetadataType::INT64) == static_cast<uint32_t>(::android::hardware::camera::common::V1_0::CameraMetadataType::INT64)) {
        os += (first ? "" : " | ");
        os += "INT64";
        first = false;
        flipped |= ::android::hardware::camera::common::V1_0::CameraMetadataType::INT64;
    }
    if ((o & ::android::hardware::camera::common::V1_0::CameraMetadataType::DOUBLE) == static_cast<uint32_t>(::android::hardware::camera::common::V1_0::CameraMetadataType::DOUBLE)) {
        os += (first ? "" : " | ");
        os += "DOUBLE";
        first = false;
        flipped |= ::android::hardware::camera::common::V1_0::CameraMetadataType::DOUBLE;
    }
    if ((o & ::android::hardware::camera::common::V1_0::CameraMetadataType::RATIONAL) == static_cast<uint32_t>(::android::hardware::camera::common::V1_0::CameraMetadataType::RATIONAL)) {
        os += (first ? "" : " | ");
        os += "RATIONAL";
        first = false;
        flipped |= ::android::hardware::camera::common::V1_0::CameraMetadataType::RATIONAL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::common::V1_0::CameraMetadataType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::common::V1_0::CameraMetadataType::BYTE) {
        return "BYTE";
    }
    if (o == ::android::hardware::camera::common::V1_0::CameraMetadataType::INT32) {
        return "INT32";
    }
    if (o == ::android::hardware::camera::common::V1_0::CameraMetadataType::FLOAT) {
        return "FLOAT";
    }
    if (o == ::android::hardware::camera::common::V1_0::CameraMetadataType::INT64) {
        return "INT64";
    }
    if (o == ::android::hardware::camera::common::V1_0::CameraMetadataType::DOUBLE) {
        return "DOUBLE";
    }
    if (o == ::android::hardware::camera::common::V1_0::CameraMetadataType::RATIONAL) {
        return "RATIONAL";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::common::V1_0::CameraMetadataType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::camera::common::V1_0::VendorTag& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".tagId = ";
    os += ::android::hardware::toString(o.tagId);
    os += ", .tagName = ";
    os += ::android::hardware::toString(o.tagName);
    os += ", .tagType = ";
    os += ::android::hardware::camera::common::V1_0::toString(o.tagType);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::common::V1_0::VendorTag& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::camera::common::V1_0::VendorTag& lhs, const ::android::hardware::camera::common::V1_0::VendorTag& rhs) {
    if (lhs.tagId != rhs.tagId) {
        return false;
    }
    if (lhs.tagName != rhs.tagName) {
        return false;
    }
    if (lhs.tagType != rhs.tagType) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::camera::common::V1_0::VendorTag& lhs, const ::android::hardware::camera::common::V1_0::VendorTag& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::camera::common::V1_0::VendorTagSection& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".sectionName = ";
    os += ::android::hardware::toString(o.sectionName);
    os += ", .tags = ";
    os += ::android::hardware::toString(o.tags);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::common::V1_0::VendorTagSection& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::camera::common::V1_0::VendorTagSection& lhs, const ::android::hardware::camera::common::V1_0::VendorTagSection& rhs) {
    if (lhs.sectionName != rhs.sectionName) {
        return false;
    }
    if (lhs.tags != rhs.tags) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::camera::common::V1_0::VendorTagSection& lhs, const ::android::hardware::camera::common::V1_0::VendorTagSection& rhs){
    return !(lhs == rhs);
}

template<>
inline std::string toString<::android::hardware::camera::common::V1_0::TagBoundaryId>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::common::V1_0::TagBoundaryId> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::common::V1_0::TagBoundaryId::AOSP) == static_cast<uint32_t>(::android::hardware::camera::common::V1_0::TagBoundaryId::AOSP)) {
        os += (first ? "" : " | ");
        os += "AOSP";
        first = false;
        flipped |= ::android::hardware::camera::common::V1_0::TagBoundaryId::AOSP;
    }
    if ((o & ::android::hardware::camera::common::V1_0::TagBoundaryId::VENDOR) == static_cast<uint32_t>(::android::hardware::camera::common::V1_0::TagBoundaryId::VENDOR)) {
        os += (first ? "" : " | ");
        os += "VENDOR";
        first = false;
        flipped |= ::android::hardware::camera::common::V1_0::TagBoundaryId::VENDOR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::common::V1_0::TagBoundaryId o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::common::V1_0::TagBoundaryId::AOSP) {
        return "AOSP";
    }
    if (o == ::android::hardware::camera::common::V1_0::TagBoundaryId::VENDOR) {
        return "VENDOR";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::common::V1_0::TagBoundaryId o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::common::V1_0::CameraDeviceStatus>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::common::V1_0::CameraDeviceStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::common::V1_0::CameraDeviceStatus::NOT_PRESENT) == static_cast<uint32_t>(::android::hardware::camera::common::V1_0::CameraDeviceStatus::NOT_PRESENT)) {
        os += (first ? "" : " | ");
        os += "NOT_PRESENT";
        first = false;
        flipped |= ::android::hardware::camera::common::V1_0::CameraDeviceStatus::NOT_PRESENT;
    }
    if ((o & ::android::hardware::camera::common::V1_0::CameraDeviceStatus::PRESENT) == static_cast<uint32_t>(::android::hardware::camera::common::V1_0::CameraDeviceStatus::PRESENT)) {
        os += (first ? "" : " | ");
        os += "PRESENT";
        first = false;
        flipped |= ::android::hardware::camera::common::V1_0::CameraDeviceStatus::PRESENT;
    }
    if ((o & ::android::hardware::camera::common::V1_0::CameraDeviceStatus::ENUMERATING) == static_cast<uint32_t>(::android::hardware::camera::common::V1_0::CameraDeviceStatus::ENUMERATING)) {
        os += (first ? "" : " | ");
        os += "ENUMERATING";
        first = false;
        flipped |= ::android::hardware::camera::common::V1_0::CameraDeviceStatus::ENUMERATING;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::common::V1_0::CameraDeviceStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::common::V1_0::CameraDeviceStatus::NOT_PRESENT) {
        return "NOT_PRESENT";
    }
    if (o == ::android::hardware::camera::common::V1_0::CameraDeviceStatus::PRESENT) {
        return "PRESENT";
    }
    if (o == ::android::hardware::camera::common::V1_0::CameraDeviceStatus::ENUMERATING) {
        return "ENUMERATING";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::common::V1_0::CameraDeviceStatus o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::camera::common::V1_0::TorchModeStatus>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::camera::common::V1_0::TorchModeStatus> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::camera::common::V1_0::TorchModeStatus::NOT_AVAILABLE) == static_cast<uint32_t>(::android::hardware::camera::common::V1_0::TorchModeStatus::NOT_AVAILABLE)) {
        os += (first ? "" : " | ");
        os += "NOT_AVAILABLE";
        first = false;
        flipped |= ::android::hardware::camera::common::V1_0::TorchModeStatus::NOT_AVAILABLE;
    }
    if ((o & ::android::hardware::camera::common::V1_0::TorchModeStatus::AVAILABLE_OFF) == static_cast<uint32_t>(::android::hardware::camera::common::V1_0::TorchModeStatus::AVAILABLE_OFF)) {
        os += (first ? "" : " | ");
        os += "AVAILABLE_OFF";
        first = false;
        flipped |= ::android::hardware::camera::common::V1_0::TorchModeStatus::AVAILABLE_OFF;
    }
    if ((o & ::android::hardware::camera::common::V1_0::TorchModeStatus::AVAILABLE_ON) == static_cast<uint32_t>(::android::hardware::camera::common::V1_0::TorchModeStatus::AVAILABLE_ON)) {
        os += (first ? "" : " | ");
        os += "AVAILABLE_ON";
        first = false;
        flipped |= ::android::hardware::camera::common::V1_0::TorchModeStatus::AVAILABLE_ON;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::camera::common::V1_0::TorchModeStatus o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::camera::common::V1_0::TorchModeStatus::NOT_AVAILABLE) {
        return "NOT_AVAILABLE";
    }
    if (o == ::android::hardware::camera::common::V1_0::TorchModeStatus::AVAILABLE_OFF) {
        return "AVAILABLE_OFF";
    }
    if (o == ::android::hardware::camera::common::V1_0::TorchModeStatus::AVAILABLE_ON) {
        return "AVAILABLE_ON";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::camera::common::V1_0::TorchModeStatus o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::camera::common::V1_0::CameraResourceCost& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".resourceCost = ";
    os += ::android::hardware::toString(o.resourceCost);
    os += ", .conflictingDevices = ";
    os += ::android::hardware::toString(o.conflictingDevices);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::camera::common::V1_0::CameraResourceCost& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::camera::common::V1_0::CameraResourceCost& lhs, const ::android::hardware::camera::common::V1_0::CameraResourceCost& rhs) {
    if (lhs.resourceCost != rhs.resourceCost) {
        return false;
    }
    if (lhs.conflictingDevices != rhs.conflictingDevices) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::camera::common::V1_0::CameraResourceCost& lhs, const ::android::hardware::camera::common::V1_0::CameraResourceCost& rhs){
    return !(lhs == rhs);
}


}  // namespace V1_0
}  // namespace common
}  // namespace camera
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
template<> inline constexpr std::array<::android::hardware::camera::common::V1_0::Status, 8> hidl_enum_values<::android::hardware::camera::common::V1_0::Status> = {
    ::android::hardware::camera::common::V1_0::Status::OK,
    ::android::hardware::camera::common::V1_0::Status::ILLEGAL_ARGUMENT,
    ::android::hardware::camera::common::V1_0::Status::CAMERA_IN_USE,
    ::android::hardware::camera::common::V1_0::Status::MAX_CAMERAS_IN_USE,
    ::android::hardware::camera::common::V1_0::Status::METHOD_NOT_SUPPORTED,
    ::android::hardware::camera::common::V1_0::Status::OPERATION_NOT_SUPPORTED,
    ::android::hardware::camera::common::V1_0::Status::CAMERA_DISCONNECTED,
    ::android::hardware::camera::common::V1_0::Status::INTERNAL_ERROR,
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
template<> inline constexpr std::array<::android::hardware::camera::common::V1_0::TorchMode, 2> hidl_enum_values<::android::hardware::camera::common::V1_0::TorchMode> = {
    ::android::hardware::camera::common::V1_0::TorchMode::OFF,
    ::android::hardware::camera::common::V1_0::TorchMode::ON,
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
template<> inline constexpr std::array<::android::hardware::camera::common::V1_0::CameraMetadataType, 6> hidl_enum_values<::android::hardware::camera::common::V1_0::CameraMetadataType> = {
    ::android::hardware::camera::common::V1_0::CameraMetadataType::BYTE,
    ::android::hardware::camera::common::V1_0::CameraMetadataType::INT32,
    ::android::hardware::camera::common::V1_0::CameraMetadataType::FLOAT,
    ::android::hardware::camera::common::V1_0::CameraMetadataType::INT64,
    ::android::hardware::camera::common::V1_0::CameraMetadataType::DOUBLE,
    ::android::hardware::camera::common::V1_0::CameraMetadataType::RATIONAL,
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
template<> inline constexpr std::array<::android::hardware::camera::common::V1_0::TagBoundaryId, 2> hidl_enum_values<::android::hardware::camera::common::V1_0::TagBoundaryId> = {
    ::android::hardware::camera::common::V1_0::TagBoundaryId::AOSP,
    ::android::hardware::camera::common::V1_0::TagBoundaryId::VENDOR,
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
template<> inline constexpr std::array<::android::hardware::camera::common::V1_0::CameraDeviceStatus, 3> hidl_enum_values<::android::hardware::camera::common::V1_0::CameraDeviceStatus> = {
    ::android::hardware::camera::common::V1_0::CameraDeviceStatus::NOT_PRESENT,
    ::android::hardware::camera::common::V1_0::CameraDeviceStatus::PRESENT,
    ::android::hardware::camera::common::V1_0::CameraDeviceStatus::ENUMERATING,
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
template<> inline constexpr std::array<::android::hardware::camera::common::V1_0::TorchModeStatus, 3> hidl_enum_values<::android::hardware::camera::common::V1_0::TorchModeStatus> = {
    ::android::hardware::camera::common::V1_0::TorchModeStatus::NOT_AVAILABLE,
    ::android::hardware::camera::common::V1_0::TorchModeStatus::AVAILABLE_OFF,
    ::android::hardware::camera::common::V1_0::TorchModeStatus::AVAILABLE_ON,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_COMMON_V1_0_TYPES_H
