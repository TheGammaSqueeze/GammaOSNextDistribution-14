#ifndef HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_SERVICE_V2_0_ICAMERASERVICE_H
#define HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_SERVICE_V2_0_ICAMERASERVICE_H

#include <android/frameworks/cameraservice/common/2.0/types.h>
#include <android/frameworks/cameraservice/device/2.0/ICameraDeviceCallback.h>
#include <android/frameworks/cameraservice/device/2.0/ICameraDeviceUser.h>
#include <android/frameworks/cameraservice/service/2.0/ICameraServiceListener.h>
#include <android/frameworks/cameraservice/service/2.0/types.h>
#include <android/hidl/base/1.0/IBase.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace frameworks {
namespace cameraservice {
namespace service {
namespace V2_0 {

struct ICameraService : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.frameworks.cameraservice.service@2.0::ICameraService"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Return callback for connectDevice
     */
    using connectDevice_cb = std::function<void(::android::frameworks::cameraservice::common::V2_0::Status status, const ::android::sp<::android::frameworks::cameraservice::device::V2_0::ICameraDeviceUser>& device)>;
    /**
     * connectDevice
     *
     * Return an ICameraDeviceUser interface for the requested cameraId.
     *
     * Note: The client must have camera permissions to call this method
     *       successfully.
     *
     * @param callback the ICameraDeviceCallback interface which will get called
     *        the cameraserver when capture is started, results are received
     *        etc.
     * @param cameraId the cameraId of the camera device to connect to.
     *
     * @return status Status code of the operation.
     * @return device ICameraDeviceUser interface to the camera device requested.
     */
    virtual ::android::hardware::Return<void> connectDevice(const ::android::sp<::android::frameworks::cameraservice::device::V2_0::ICameraDeviceCallback>& callback, const ::android::hardware::hidl_string& cameraId, connectDevice_cb _hidl_cb) = 0;

    /**
     * Return callback for addListener
     */
    using addListener_cb = std::function<void(::android::frameworks::cameraservice::common::V2_0::Status status, const ::android::hardware::hidl_vec<::android::frameworks::cameraservice::service::V2_0::CameraStatusAndId>& statuses)>;
    /**
     * Add listener for changes to camera device status.
     *
     * Also returns the set of currently-known camera IDs and state of each
     * device. Adding multiple listeners must result in the callbacks defined by
     * ICameraServiceListener being called on all of them, on change of device
     * status.
     *
     * @param listener the listener interface to be added. The cameraserver will
     *        call callbacks on this interface when a camera device's status
     *        changes.
     * @return status Status code of the operation
     * @return statuses a list of CameraStatusAndIds which store the deviceIds
     *         and their corresponding statuses.
     */
    virtual ::android::hardware::Return<void> addListener(const ::android::sp<::android::frameworks::cameraservice::service::V2_0::ICameraServiceListener>& listener, addListener_cb _hidl_cb) = 0;

    /**
     * Remove listener for changes to camera device status.
     *
     * @param listener the listener to be removed from receiving callbacks on
     *        changes to device state.
     * @return status Status code of the operation.
     */
    virtual ::android::hardware::Return<::android::frameworks::cameraservice::common::V2_0::Status> removeListener(const ::android::sp<::android::frameworks::cameraservice::service::V2_0::ICameraServiceListener>& listener) = 0;

    /**
     * Return callback for getCameraCharacteristics
     */
    using getCameraCharacteristics_cb = std::function<void(::android::frameworks::cameraservice::common::V2_0::Status status, const ::android::hardware::hidl_vec<uint8_t>& metadata)>;
    /**
     * Read the static camera metadata for a camera device.
     * @param cameraId the camera id of the camera device, whose metadata is
     *        being requested.
     * @return status the status code of the operation
     * @return metadata the static metadata of the camera device requested.
     */
    virtual ::android::hardware::Return<void> getCameraCharacteristics(const ::android::hardware::hidl_string& cameraId, getCameraCharacteristics_cb _hidl_cb) = 0;

    /**
     * Return callback for getCameraVendorTagSections
     */
    using getCameraVendorTagSections_cb = std::function<void(::android::frameworks::cameraservice::common::V2_0::Status status, const ::android::hardware::hidl_vec<::android::frameworks::cameraservice::common::V2_0::ProviderIdAndVendorTagSections>& providerIdAndVendorTagSections)>;
    /**
     * Read in the provider ids and corresponding vendor tag sections from the camera server.
     * Intended to be used by the native code of CameraMetadata to correctly
     * interpret camera metadata with vendor tags.
     *
     * Note: VendorTag caches may be created in process, by clients. A HIDL api
     *       is not provided for this.
     *
     * @return status the status code of the operation.
     * @return providerIdAndVendorTagSections the list of provider ids and corresponding
     *         vendor tag sections.
     */
    virtual ::android::hardware::Return<void> getCameraVendorTagSections(getCameraVendorTagSections_cb _hidl_cb) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::frameworks::cameraservice::service::V2_0::ICameraService>> castFrom(const ::android::sp<::android::frameworks::cameraservice::service::V2_0::ICameraService>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::frameworks::cameraservice::service::V2_0::ICameraService>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<ICameraService> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ICameraService> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ICameraService> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ICameraService> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<ICameraService> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ICameraService> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ICameraService> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ICameraService> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::frameworks::cameraservice::service::V2_0::ICameraService>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::frameworks::cameraservice::service::V2_0::ICameraService>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::frameworks::cameraservice::service::V2_0::ICameraService::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V2_0
}  // namespace service
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_SERVICE_V2_0_ICAMERASERVICE_H
