#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_6_ICAMERADEVICE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_6_ICAMERADEVICE_H

#include <android/hardware/camera/device/3.5/ICameraDevice.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace V3_6 {

/**
 * Camera device interface
 *
 * Supports the android.hardware.Camera API, and the android.hardware.camera2
 * API at LIMITED or better hardware level.
 *
 * ICameraDevice.open() must return @3.2::ICameraDeviceSession or
 * @3.5::ICameraDeviceSession or @3.6::ICameraDeviceSession.
 */
struct ICameraDevice : public ::android::hardware::camera::device::V3_5::ICameraDevice {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.camera.device@3.6::ICameraDevice"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Return callback for getResourceCost
     */
    using getResourceCost_cb = std::function<void(::android::hardware::camera::common::V1_0::Status status, const ::android::hardware::camera::common::V1_0::CameraResourceCost& resourceCost)>;
    /**
     * Get camera device resource cost information.
     *
     * @return status Status code for the operation, one of:
     *     OK:
     *         On success
     *     INTERNAL_ERROR:
     *         An unexpected internal camera HAL error occurred, and the
     *         resource cost is not available.
     *     CAMERA_DISCONNECTED:
     *         An external camera device has been disconnected, and is no longer
     *         available. This camera device interface is now stale, and a new
     *         instance must be acquired if the device is reconnected. All
     *         subsequent calls on this interface must return
     *         CAMERA_DISCONNECTED.
     * @return resourceCost
     *     The resources required to open this camera device, or unspecified
     *     values if status is not OK.
     */
    virtual ::android::hardware::Return<void> getResourceCost(getResourceCost_cb _hidl_cb) = 0;

    /**
     * Return callback for getCameraCharacteristics
     */
    using getCameraCharacteristics_cb = std::function<void(::android::hardware::camera::common::V1_0::Status status, const ::android::hardware::hidl_vec<uint8_t>& cameraCharacteristics)>;
    /**
     * getCameraCharacteristics:
     *
     * Return the static camera information for this camera device. This
     * information may not change between consecutive calls.
     *
     * When an external camera is disconnected, its camera id becomes
     * invalid. Calling this method with this invalid camera id must result in
     * ILLEGAL_ARGUMENT; this may happen even before the device status callback
     * is invoked by the HAL.
     *
     * @return status Status code for the operation, one of:
     *     OK:
     *         On a successful open of the camera device.
     *     INTERNAL_ERROR:
     *         The camera device cannot be opened due to an internal
     *         error.
     *     CAMERA_DISCONNECTED:
     *         An external camera device has been disconnected, and is no longer
     *         available. This camera device interface is now stale, and a new
     *         instance must be acquired if the device is reconnected. All
     *         subsequent calls on this interface must return
     *         CAMERA_DISCONNECTED.
     *
     * @return cameraCharacteristics
     *     The static metadata for this camera device, or an empty metadata
     *     structure if status is not OK.
     *
     */
    virtual ::android::hardware::Return<void> getCameraCharacteristics(getCameraCharacteristics_cb _hidl_cb) = 0;

    /**
     * setTorchMode:
     *
     * Turn on or off the torch mode of the flash unit associated with this
     * camera device. If the operation is successful, HAL must notify the
     * framework torch state by invoking
     * ICameraProviderCallback::torchModeStatusChange() with the new state.
     *
     * An active camera session has a higher priority accessing the flash
     * unit. When there are any resource conflicts, such as when open() is
     * called to fully activate a camera device, the provider must notify the
     * framework through ICameraProviderCallback::torchModeStatusChange() that
     * the torch mode has been turned off and the torch mode state has become
     * TORCH_MODE_STATUS_NOT_AVAILABLE. When resources to turn on torch mode
     * become available again, the provider must notify the framework through
     * ICameraProviderCallback::torchModeStatusChange() that the torch mode
     * state has become TORCH_MODE_STATUS_AVAILABLE_OFF for set_torch_mode() to
     * be called.
     *
     * When the client calls setTorchMode() to turn on the torch mode of a flash
     * unit, if the HAL cannot keep multiple torch modes on simultaneously, the
     * HAL must turn off the torch mode(s) that were turned on by previous
     * setTorchMode() calls and notify the framework that the torch mode state
     * of those flash unit(s) has become TORCH_MODE_STATUS_AVAILABLE_OFF.
     *
     * @param torchMode The new mode to set the device flash unit to.
     *
     * @return status Status code for the operation, one of:
     *     OK:
     *         On a successful change to the torch state
     *     INTERNAL_ERROR:
     *         The flash unit cannot be operated due to an unexpected internal
     *         error.
     *     ILLEGAL_ARGUMENT:
     *         The camera ID is unknown.
     *     CAMERA_IN_USE:
     *         This camera device has been opened, so the torch cannot be
     *         controlled until it is closed.
     *     MAX_CAMERAS_IN_USE:
     *         Due to other camera devices being open, or due to other
     *         resource constraints, the torch cannot be controlled currently.
     *     METHOD_NOT_SUPPORTED:
     *         This provider does not support direct operation of flashlight
     *         torch mode. The framework must open the camera device and turn
     *         the torch on through the device interface.
     *     OPERATION_NOT_SUPPORTED:
     *         This camera device does not have a flash unit. This can
     *         be returned if and only if android.flash.info.available is
     *         false.
     *     CAMERA_DISCONNECTED:
     *         An external camera device has been disconnected, and is no longer
     *         available. This camera device interface is now stale, and a new
     *         instance must be acquired if the device is reconnected. All
     *         subsequent calls on this interface must return
     *         CAMERA_DISCONNECTED.
     *
     */
    virtual ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> setTorchMode(::android::hardware::camera::common::V1_0::TorchMode mode) = 0;

    /**
     * Return callback for open
     */
    using open_cb = std::function<void(::android::hardware::camera::common::V1_0::Status status, const ::android::sp<::android::hardware::camera::device::V3_2::ICameraDeviceSession>& session)>;
    /**
     * open:
     *
     * Power on and initialize this camera device for active use, returning a
     * session handle for active operations.
     *
     * @param callback Interface to invoke by the HAL for device asynchronous
     *     events. For HALs newer than version 3.2, HAL must use castFrom
     *     method to check the exact version of callback sent by camera service.
     *
     * @return status Status code for the operation, one of:
     *     OK:
     *         On a successful open of the camera device.
     *     INTERNAL_ERROR:
     *         The camera device cannot be opened due to an internal
     *         error.
     *     ILLEGAL_ARGUMENT:
     *         The callbacks handle is invalid (for example, it is null).
     *     CAMERA_IN_USE:
     *         This camera device is already open.
     *     MAX_CAMERAS_IN_USE:
     *         The maximal number of camera devices that can be
     *         opened concurrently were opened already.
     *     CAMERA_DISCONNECTED:
     *         This external camera device has been disconnected, and is no
     *         longer available. This interface is now stale, and a new instance
     *         must be acquired if the device is reconnected. All subsequent
     *         calls on this interface must return CAMERA_DISCONNECTED.
     * @return session The interface to the newly-opened camera session,
     *     or null if status is not OK.
     */
    virtual ::android::hardware::Return<void> open(const ::android::sp<::android::hardware::camera::device::V3_2::ICameraDeviceCallback>& callback, open_cb _hidl_cb) = 0;

    /**
     * dumpState:
     *
     * Print out debugging state for the camera device. This may be called by
     * the framework when the camera service is asked for a debug dump, which
     * happens when using the dumpsys tool, or when capturing a bugreport.
     *
     * The passed-in file descriptor can be used to write debugging text using
     * dprintf() or write(). The text must be in ASCII encoding only.
     *
     * In case this camera device has been disconnected, the dump must not fail,
     * but may simply print out 'Device disconnected' or equivalent.
     *
     * Performance requirements:
     *
     * This must be a non-blocking call. The HAL should return from this call
     * in 1ms, must return from this call in 10ms. This call must avoid
     * deadlocks, as it may be called at any point during camera operation.
     * Any synchronization primitives used (such as mutex locks or semaphores)
     * must be acquired with a timeout.
     */
    virtual ::android::hardware::Return<void> dumpState(const ::android::hardware::hidl_handle& fd) = 0;

    /**
     * Return callback for getPhysicalCameraCharacteristics
     */
    using getPhysicalCameraCharacteristics_cb = std::function<void(::android::hardware::camera::common::V1_0::Status status, const ::android::hardware::hidl_vec<uint8_t>& cameraCharacteristics)>;
    /**
     * getPhysicalCameraCharacteristics:
     *
     * Return the static camera information for a physical camera ID backing
     * this logical camera device. This information may not change between consecutive calls.
     *
     * Note that HAL must support this function for physical camera IDs that are
     * not exposed via ICameraProvider::getCameraIdList(). Calling
     * getCameraDeviceInterface_V3_x() on these camera IDs must return ILLEGAL_ARGUMENT.
     *
     * The characteristics of all cameras returned by
     * ICameraProvider::getCameraIdList() must be queried via
     * getCameraCharacteristics(). Calling getPhysicalCameraCharacteristics() on
     * those cameras must return ILLEGAL_ARGUMENT.
     *
     * @param physicalCameraId The physical camera id parsed from the logical
     *     camera's ANDROID_LOGICAL_MULTI_CAMERA_PHYSICAL_IDS static metadata
     *     key. The framework assumes that this ID is just the <id> part of fully
     *     qualified camera device name "device@<major>.<minor>/<type>/<id>". And
     *     the physical camera must be of the same version and type as the parent
     *     logical camera device.
     *
     * @return status Status code for the operation, one of:
     *     OK:
     *         On a successful query of the physical camera device characteristics
     *     INTERNAL_ERROR:
     *         The camera device cannot be opened due to an internal
     *         error.
     *     CAMERA_DISCONNECTED:
     *         An external camera device has been disconnected, and is no longer
     *         available. This camera device interface is now stale, and a new
     *         instance must be acquired if the device is reconnected. All
     *         subsequent calls on this interface must return
     *         CAMERA_DISCONNECTED.
     *     ILLEGAL_ARGUMENT:
     *         If the physicalCameraId is not a valid physical camera Id outside
     *         of ICameraProvider::getCameraIdList().
     *
     * @return cameraCharacteristics
     *     The static metadata for this logical camera device's physical device, or an empty
     *     metadata structure if status is not OK.
     *
     */
    virtual ::android::hardware::Return<void> getPhysicalCameraCharacteristics(const ::android::hardware::hidl_string& physicalCameraId, getPhysicalCameraCharacteristics_cb _hidl_cb) = 0;

    /**
     * Return callback for isStreamCombinationSupported
     */
    using isStreamCombinationSupported_cb = std::function<void(::android::hardware::camera::common::V1_0::Status status, bool queryStatus)>;
    /**
     * isStreamCombinationSupported:
     *
     * Check for device support of specific camera stream combination.
     *
     * The streamList must contain at least one output-capable stream, and may
     * not contain more than one input-capable stream.
     * In contrast to regular stream configuration the framework does not create
     * or initialize any actual streams. This means that Hal must not use or
     * consider the stream "id" value.
     *
     * ------------------------------------------------------------------------
     *
     * Preconditions:
     *
     * The framework can call this method at any time before, during and
     * after active session configuration. This means that calls must not
     * impact the performance of pending camera requests in any way. In
     * particular there must not be any glitches or delays during normal
     * camera streaming.
     *
     * Performance requirements:
     * This call is expected to be significantly faster than stream
     * configuration. In general HW and SW camera settings must not be
     * changed and there must not be a user-visible impact on camera performance.
     *
     * @return Status Status code for the operation, one of:
     *     OK:
     *          On successful stream combination query.
     *     METHOD_NOT_SUPPORTED:
     *          The camera device does not support stream combination query.
     *     INTERNAL_ERROR:
     *          The stream combination query cannot complete due to internal
     *          error.
     * @return true in case the stream combination is supported, false otherwise.
     *
     */
    virtual ::android::hardware::Return<void> isStreamCombinationSupported(const ::android::hardware::camera::device::V3_4::StreamConfiguration& streams, isStreamCombinationSupported_cb _hidl_cb) = 0;

    /**
     * Return callback for interfaceChain
     */
    using interfaceChain_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& descriptors)>;
    /*
     * Provides run-time type information for this object.
     * For example, for the following interface definition:
     *     package android.hardware.foo@1.0;
     *     interface IParent {};
     *     interface IChild extends IParent {};
     * Calling interfaceChain on an IChild object must yield the following:
     *     ["android.hardware.foo@1.0::IChild",
     *      "android.hardware.foo@1.0::IParent"
     *      "android.hidl.base@1.0::IBase"]
     *
     * @return descriptors a vector of descriptors of the run-time type of the
     *         object.
     */
    virtual ::android::hardware::Return<void> interfaceChain(interfaceChain_cb _hidl_cb) override;

    /*
     * Emit diagnostic information to the given file.
     *
     * Optionally overriden.
     *
     * @param fd      File descriptor to dump data to.
     *                Must only be used for the duration of this call.
     * @param options Arguments for debugging.
     *                Must support empty for default debug information.
     */
    virtual ::android::hardware::Return<void> debug(const ::android::hardware::hidl_handle& fd, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& options) override;

    /**
     * Return callback for interfaceDescriptor
     */
    using interfaceDescriptor_cb = std::function<void(const ::android::hardware::hidl_string& descriptor)>;
    /*
     * Provides run-time type information for this object.
     * For example, for the following interface definition:
     *     package android.hardware.foo@1.0;
     *     interface IParent {};
     *     interface IChild extends IParent {};
     * Calling interfaceDescriptor on an IChild object must yield
     *     "android.hardware.foo@1.0::IChild"
     *
     * @return descriptor a descriptor of the run-time type of the
     *         object (the first element of the vector returned by
     *         interfaceChain())
     */
    virtual ::android::hardware::Return<void> interfaceDescriptor(interfaceDescriptor_cb _hidl_cb) override;

    /**
     * Return callback for getHashChain
     */
    using getHashChain_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::hidl_array<uint8_t, 32>>& hashchain)>;
    /*
     * Returns hashes of the source HAL files that define the interfaces of the
     * runtime type information on the object.
     * For example, for the following interface definition:
     *     package android.hardware.foo@1.0;
     *     interface IParent {};
     *     interface IChild extends IParent {};
     * Calling interfaceChain on an IChild object must yield the following:
     *     [(hash of IChild.hal),
     *      (hash of IParent.hal)
     *      (hash of IBase.hal)].
     *
     * SHA-256 is used as the hashing algorithm. Each hash has 32 bytes
     * according to SHA-256 standard.
     *
     * @return hashchain a vector of SHA-1 digests
     */
    virtual ::android::hardware::Return<void> getHashChain(getHashChain_cb _hidl_cb) override;

    /*
     * This method trigger the interface to enable/disable instrumentation based
     * on system property hal.instrumentation.enable.
     */
    virtual ::android::hardware::Return<void> setHALInstrumentation() override;

    /*
     * Registers a death recipient, to be called when the process hosting this
     * interface dies.
     *
     * @param recipient a hidl_death_recipient callback object
     * @param cookie a cookie that must be returned with the callback
     * @return success whether the death recipient was registered successfully.
     */
    virtual ::android::hardware::Return<bool> linkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient, uint64_t cookie) override;

    /*
     * Provides way to determine if interface is running without requesting
     * any functionality.
     */
    virtual ::android::hardware::Return<void> ping() override;

    /**
     * Return callback for getDebugInfo
     */
    using getDebugInfo_cb = std::function<void(const ::android::hidl::base::V1_0::DebugInfo& info)>;
    /*
     * Get debug information on references on this interface.
     * @return info debugging information. See comments of DebugInfo.
     */
    virtual ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb) override;

    /*
     * This method notifies the interface that one or more system properties
     * have changed. The default implementation calls
     * (C++)  report_sysprop_change() in libcutils or
     * (Java) android.os.SystemProperties.reportSyspropChanged,
     * which in turn calls a set of registered callbacks (eg to update trace
     * tags).
     */
    virtual ::android::hardware::Return<void> notifySyspropsChanged() override;

    /*
     * Unregisters the registered death recipient. If this service was registered
     * multiple times with the same exact death recipient, this unlinks the most
     * recently registered one.
     *
     * @param recipient a previously registered hidl_death_recipient callback
     * @return success whether the death recipient was unregistered successfully.
     */
    virtual ::android::hardware::Return<bool> unlinkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient) override;

    // cast static functions
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::camera::device::V3_6::ICameraDevice>> castFrom(const ::android::sp<::android::hardware::camera::device::V3_6::ICameraDevice>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::camera::device::V3_6::ICameraDevice>> castFrom(const ::android::sp<::android::hardware::camera::device::V3_5::ICameraDevice>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::camera::device::V3_6::ICameraDevice>> castFrom(const ::android::sp<::android::hardware::camera::device::V3_2::ICameraDevice>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::camera::device::V3_6::ICameraDevice>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

    // helper methods for interactions with the hwservicemanager
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is currently not available or not in the VINTF manifest on a Trebilized
     * device, this will return nullptr. This is useful when you don't want to block
     * during device boot. If getStub is true, this will try to return an unwrapped
     * passthrough implementation in the same process. This is useful when getting an
     * implementation from the same partition/compilation group.
     *
     * In general, prefer getService(std::string,bool)
     */
    static ::android::sp<ICameraDevice> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ICameraDevice> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ICameraDevice> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ICameraDevice> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<ICameraDevice> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ICameraDevice> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ICameraDevice> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ICameraDevice> getService(bool getStub) { return getService("default", getStub); }
    /**
     * Registers a service with the service manager. For Trebilized devices, the service
     * must also be in the VINTF manifest.
     */
    __attribute__ ((warn_unused_result))::android::status_t registerAsService(const std::string &serviceName="default");
    /**
     * Registers for notifications for when a service is registered.
     */
    static bool registerForNotifications(
            const std::string &serviceName,
            const ::android::sp<::android::hidl::manager::V1_0::IServiceNotification> &notification);
};

//
// type declarations for package
//

static inline std::string toString(const ::android::sp<::android::hardware::camera::device::V3_6::ICameraDevice>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::camera::device::V3_6::ICameraDevice>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::camera::device::V3_6::ICameraDevice::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V3_6
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_6_ICAMERADEVICE_H
