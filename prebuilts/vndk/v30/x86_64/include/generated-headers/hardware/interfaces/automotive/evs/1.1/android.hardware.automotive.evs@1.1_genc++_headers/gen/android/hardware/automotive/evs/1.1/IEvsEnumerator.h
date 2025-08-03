#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_IEVSENUMERATOR_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_IEVSENUMERATOR_H

#include <android/hardware/automotive/evs/1.0/IEvsEnumerator.h>
#include <android/hardware/automotive/evs/1.1/IEvsCamera.h>
#include <android/hardware/automotive/evs/1.1/IEvsDisplay.h>
#include <android/hardware/automotive/evs/1.1/IEvsUltrasonicsArray.h>
#include <android/hardware/automotive/evs/1.1/types.h>
#include <android/hardware/camera/device/3.2/types.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace automotive {
namespace evs {
namespace V1_1 {

/**
 * Provides the mechanism for EVS camera and ultrasonics array discovery
 */
struct IEvsEnumerator : public ::android::hardware::automotive::evs::V1_0::IEvsEnumerator {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.automotive.evs@1.1::IEvsEnumerator"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Return callback for getCameraList
     */
    using getCameraList_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::automotive::evs::V1_0::CameraDesc>& cameras)>;
    /**
     * Returns a list of all EVS cameras available to the system
     *
     * @return cameras A list of cameras availale for EVS service.
     */
    virtual ::android::hardware::Return<void> getCameraList(getCameraList_cb _hidl_cb) = 0;

    /**
     * Get the IEvsCamera associated with a cameraId from a CameraDesc
     *
     * Given a camera's unique cameraId from CameraDesc, returns the
     * IEvsCamera interface associated with the specified camera. When
     * done using the camera, the caller may release it by calling closeCamera().
     *
     * @param  cameraId  A unique identifier of the camera.
     * @return carCamera EvsCamera object associated with a given cameraId.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::automotive::evs::V1_0::IEvsCamera>> openCamera(const ::android::hardware::hidl_string& cameraId) = 0;

    /**
     * Return the specified IEvsCamera interface as no longer in use
     *
     * When the IEvsCamera object is no longer required, it must be released.
     * NOTE: Video streaming must be cleanly stopped before making this call.
     *
     * @param  carCamera EvsCamera object to be closed.
     */
    virtual ::android::hardware::Return<void> closeCamera(const ::android::sp<::android::hardware::automotive::evs::V1_0::IEvsCamera>& carCamera) = 0;

    /**
     * Get exclusive access to IEvsDisplay for the system
     *
     * There can be at most one EVS display object for the system and this function
     * requests access to it. If the EVS display is not available or is already in use,
     * the old instance shall be closed and give the new caller exclusive
     * access.
     * When done using the display, the caller may release it by calling closeDisplay().
     *
     * @return display EvsDisplay object to be used.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::automotive::evs::V1_0::IEvsDisplay>> openDisplay() = 0;

    /**
     * Return the specified IEvsDisplay interface as no longer in use
     *
     * When the IEvsDisplay object is no longer required, it must be released.
     * NOTE: All buffers must have been returned to the display before making this call.
     *
     * @param  display EvsDisplay object to be closed.
     */
    virtual ::android::hardware::Return<void> closeDisplay(const ::android::sp<::android::hardware::automotive::evs::V1_0::IEvsDisplay>& display) = 0;

    /**
     * This call requests the current state of the display
     *
     * If there is no open display, this returns DisplayState::NOT_OPEN. otherwise, it returns
     * the actual state of the active display.  This call is replicated on the IEvsEnumerator
     * interface in order to allow secondary clients to monitor the state of the EVS display
     * without acquiring exclusive ownership of the display.
     *
     * @return state Current DisplayState of this Display.
     */
    virtual ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::DisplayState> getDisplayState() = 0;

    /**
     * Return callback for getCameraList_1_1
     */
    using getCameraList_1_1_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::automotive::evs::V1_1::CameraDesc>& cameras)>;
    /**
     * Returns a list of all EVS cameras available to the system
     *
     * @return cameras A list of cameras availale for EVS service.
     */
    virtual ::android::hardware::Return<void> getCameraList_1_1(getCameraList_1_1_cb _hidl_cb) = 0;

    /**
     * Gets the IEvsCamera associated with a cameraId from a CameraDesc
     *
     * Given a camera's unique cameraId from CameraDesc, returns the
     * IEvsCamera interface associated with the specified camera. When
     * done using the camera, the caller may release it by calling closeCamera().
     *
     * @param  cameraId  A unique identifier of the camera.
     * @param  streamCfg A stream configuration the client wants to use.
     * @return evsCamera EvsCamera object associated with a given cameraId.
     *                   Returned object would be null if a camera device does
     *                   not support a given stream configuration or is already
     *                   configured differently by another client.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::automotive::evs::V1_1::IEvsCamera>> openCamera_1_1(const ::android::hardware::hidl_string& cameraId, const ::android::hardware::camera::device::V3_2::Stream& streamCfg) = 0;

    /**
     * Tells whether this is EVS manager or HAL implementation.
     *
     * @return result False for EVS manager implementations and true for all others.
     */
    virtual ::android::hardware::Return<bool> isHardware() = 0;

    /**
     * Return callback for getDisplayIdList
     */
    using getDisplayIdList_cb = std::function<void(const ::android::hardware::hidl_vec<uint8_t>& displayIds)>;
    /**
     * Returns a list of all EVS displays available to the system
     *
     * @return displayIds Identifiers of available displays.
     */
    virtual ::android::hardware::Return<void> getDisplayIdList(getDisplayIdList_cb _hidl_cb) = 0;

    /**
     * Get exclusive access to IEvsDisplay for the system
     *
     * There can be more than one EVS display objects for the system and this function
     * requests access to the display identified by a given ID. If the target EVS display
     * is not available or is already in use the old instance shall be closed and give
     * the new caller exclusive access.
     * When done using the display, the caller may release it by calling closeDisplay().
     *
     * @param  id      Target display identifier.
     * @return display EvsDisplay object to be used.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::automotive::evs::V1_1::IEvsDisplay>> openDisplay_1_1(uint8_t id) = 0;

    /**
     * Return callback for getUltrasonicsArrayList
     */
    using getUltrasonicsArrayList_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::automotive::evs::V1_1::UltrasonicsArrayDesc>& ultrasonicsArrays)>;
    /**
     * Returns a list of all ultrasonics array available to the system.
     * Will return an empty vector if ultrasonics is not supported.
     *
     * @return ultrasonicsArrays A list of ultrasonics available for EVS service.
     */
    virtual ::android::hardware::Return<void> getUltrasonicsArrayList(getUltrasonicsArrayList_cb _hidl_cb) = 0;

    /**
     * Gets the IEvsUltrasonicsArray associated with a ultrasonicsArrayId from a
     * UltrasonicsDataDesc
     *
     * @param  ultrasonicsArrayId  A unique identifier of the ultrasonic array.
     * @return evsUltrasonicsArray IEvsUltrasonicsArray object associated with a
     *                             given ultrasonicsArrayId.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::automotive::evs::V1_1::IEvsUltrasonicsArray>> openUltrasonicsArray(const ::android::hardware::hidl_string& ultrasonicsArrayId) = 0;

    /**
     * Return the specified IEvsUltrasonicsArray interface as no longer in use
     *
     * When the IEvsUltrasonicsArray object is no longer required, it must be released.
     * NOTE: Data streaming must be cleanly stopped before making this call.
     *
     * @param  evsUltrasonicsArray EvsUltrasonics array object to be closed.
     */
    virtual ::android::hardware::Return<void> closeUltrasonicsArray(const ::android::sp<::android::hardware::automotive::evs::V1_1::IEvsUltrasonicsArray>& evsUltrasonicsArray) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::automotive::evs::V1_1::IEvsEnumerator>> castFrom(const ::android::sp<::android::hardware::automotive::evs::V1_1::IEvsEnumerator>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::automotive::evs::V1_1::IEvsEnumerator>> castFrom(const ::android::sp<::android::hardware::automotive::evs::V1_0::IEvsEnumerator>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::automotive::evs::V1_1::IEvsEnumerator>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IEvsEnumerator> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IEvsEnumerator> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IEvsEnumerator> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IEvsEnumerator> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IEvsEnumerator> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IEvsEnumerator> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IEvsEnumerator> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IEvsEnumerator> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::automotive::evs::V1_1::IEvsEnumerator>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::automotive::evs::V1_1::IEvsEnumerator>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::automotive::evs::V1_1::IEvsEnumerator::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_1
}  // namespace evs
}  // namespace automotive
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_IEVSENUMERATOR_H
