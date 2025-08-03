#ifndef HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_IFRONTEND_H
#define HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_IFRONTEND_H

#include <android/hardware/tv/tuner/1.0/IFrontendCallback.h>
#include <android/hardware/tv/tuner/1.0/types.h>
#include <android/hidl/base/1.0/IBase.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace tv {
namespace tuner {
namespace V1_0 {

/**
 * A Tuner Frontend is used to tune to a frequency and lock signal.
 *
 * IFrontend provides a bit stream to the Tuner Demux interface.
 */
struct IFrontend : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.tv.tuner@1.0::IFrontend"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Set the frontend callback.
     *
     * IFrontendCallback is used by the client to receive events from the Frontend.
     * Only one callback per IFrontend instance is supported. The callback
     * will be replaced if it's set again.
     *
     * @param callback Callback object to pass Frontend events to the system.
     *        The previously registered callback must be replaced with this one.
     *        It can be null.
     * @return result Result status of the operation.
     *         SUCCESS if successful,
     *         INVALID_STATE if callback can't be set at current stage,
     *         UNKNOWN_ERROR if callback setting failed for other reasons.
     */
    virtual ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> setCallback(const ::android::sp<::android::hardware::tv::tuner::V1_0::IFrontendCallback>& callback) = 0;

    /**
     * Tunes the frontend to using the settings given.
     *
     * This locks the frontend to a frequency by providing signal
     * delivery information. If previous tuning isn't completed, this call MUST
     * stop previous tuning, and start a new tuning.
     * Tune is an async call, with LOCKED or NO_SIGNAL events sent via callback.
     *
     * @param settings Signal delivery information the frontend uses to
     * search and lock the signal.
     *
     * @return result Result status of the operation.
     *         SUCCESS if successful,
     *         INVALID_STATE if tuning can't be applied at current stage,
     *         UNKNOWN_ERROR if tuning failed for other reasons.
     */
    virtual ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> tune(const ::android::hardware::tv::tuner::V1_0::FrontendSettings& settings) = 0;

    /**
     * Stops a previous tuning.
     *
     * If the method completes successfully the frontend is no longer tuned and no data
     * will be sent to attached demuxes.
     *
     * @return result Result status of the operation.
     *         SUCCESS if successfully stop tuning.
     *         UNKNOWN_ERROR if failed for other reasons.
     */
    virtual ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> stopTune() = 0;

    /**
     * Releases the Frontend instance
     *
     * Associated resources are released.  close may be called more than once.
     * Calls to any other method after this will return an error
     *
     * @return result Result status of the operation.
     *         SUCCESS if successful,
     *         UNKNOWN_ERROR if failed for other reasons.
     */
    virtual ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> close() = 0;

    /**
     * Scan the frontend to use the settings given.
     *
     * This uses the frontend to start a scan from signal delivery information.
     * If previous scan isn't completed, this call MUST stop previous scan,
     * and start a new scan.
     * Scan is an async call, with FrontendScanMessage sent via callback.
     *
     * @param settings Signal delivery information which the frontend uses to
     * scan the signal.
     * @param type the type which the frontend uses to scan the signal.
     *
     * @return result Result status of the operation.
     *         SUCCESS if successful,
     *         INVALID_STATE if tuning can't be applied at current stage,
     *         UNKNOWN_ERROR if tuning failed for other reasons.
     */
    virtual ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> scan(const ::android::hardware::tv::tuner::V1_0::FrontendSettings& settings, ::android::hardware::tv::tuner::V1_0::FrontendScanType type) = 0;

    /**
     * Stops a previous scanning.
     *
     * If the method completes successfully, the frontend stop previous
     * scanning.
     *
     * @return result Result status of the operation.
     *         SUCCESS if successfully stop tuning.
     *         UNKNOWN_ERROR if failed for other reasons.
     */
    virtual ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> stopScan() = 0;

    /**
     * Return callback for getStatus
     */
    using getStatus_cb = std::function<void(::android::hardware::tv::tuner::V1_0::Result result, const ::android::hardware::hidl_vec<::android::hardware::tv::tuner::V1_0::FrontendStatus>& statuses)>;
    /**
     * Gets the statuses of the frontend.
     *
     * This retrieve the statuses of the frontend for given status types.
     *
     * @param statusTypes an array of status type which the caller request.
     *
     * @return result Result status of the operation.
     *         SUCCESS if successful,
     *         INVALID_STATE if tuning can't be applied at current stage,
     *         UNKNOWN_ERROR if tuning failed for other reasons.
     * @return statuses an array of statuses which response the caller's
     *         request.
     */
    virtual ::android::hardware::Return<void> getStatus(const ::android::hardware::hidl_vec<::android::hardware::tv::tuner::V1_0::FrontendStatusType>& statusTypes, getStatus_cb _hidl_cb) = 0;

    /**
     * Sets Low-Noise Block downconverter (LNB) for satellite frontend.
     *
     * This assigns a hardware LNB resource to the satellite frontend. It can be
     * called multiple times to update LNB assignment. The LNB resource must be
     * released when the frontend is closed.
     *
     * @param lnbId the Id of assigned LNB resource.
     *
     * @return result Result status of the operation.
     *         SUCCESS if successful,
     *         INVALID_STATE if the frontend can't be set with a LNB, such as
     *         cable frontend.
     *         UNKNOWN_ERROR if failed for other reasons.
     */
    virtual ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> setLnb(uint32_t lnbId) = 0;

    /**
     * Enable or Disable Low Noise Amplifier (LNA).
     *
     * @param bEnable true if activate LNA module; false if deactivate LNA
     *
     * @return result Result status of the operation.
     *         SUCCESS if successful,
     *         INVALID_STATE if the frontend doesn't support LNA.
     *         UNKNOWN_ERROR if failed for other reasons.
     */
    virtual ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> setLna(bool bEnable) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::tv::tuner::V1_0::IFrontend>> castFrom(const ::android::sp<::android::hardware::tv::tuner::V1_0::IFrontend>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::tv::tuner::V1_0::IFrontend>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IFrontend> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IFrontend> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IFrontend> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IFrontend> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IFrontend> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IFrontend> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IFrontend> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IFrontend> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::tv::tuner::V1_0::IFrontend>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::tv::tuner::V1_0::IFrontend>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::tv::tuner::V1_0::IFrontend::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_0
}  // namespace tuner
}  // namespace tv
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_IFRONTEND_H
