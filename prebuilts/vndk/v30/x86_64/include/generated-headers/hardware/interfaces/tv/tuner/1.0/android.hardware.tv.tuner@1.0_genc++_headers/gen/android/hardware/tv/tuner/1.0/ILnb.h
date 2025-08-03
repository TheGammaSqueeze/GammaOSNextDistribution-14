#ifndef HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_ILNB_H
#define HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_ILNB_H

#include <android/hardware/tv/tuner/1.0/ILnbCallback.h>
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
 * A Tuner LNB (low-noise block downconverter) is used by satellite frontend
 * to receive the microwave signal from the satellite, amplify it, and
 * downconvert the frequency to a lower frequency.
 */
struct ILnb : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.tv.tuner@1.0::ILnb"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Set the lnb callback.
     *
     * ILnbCallback is used by the client to receive events from the Lnb.
     * Only one callback per ILnb instance is supported. The callback
     * will be replaced if it's set again.
     *
     * @param callback Callback object to pass Lnb events to the system.
     *        The previously registered callback must be replaced with this one.
     *        It can be null.
     * @return result Result status of the operation.
     *         SUCCESS if successful,
     *         INVALID_STATE if callback can't be set at current stage,
     *         UNKNOWN_ERROR if callback setting failed for other reasons.
     */
    virtual ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> setCallback(const ::android::sp<::android::hardware::tv::tuner::V1_0::ILnbCallback>& callback) = 0;

    /**
     * Set the lnb's power voltage.
     *
     * @param voltage the power's voltage the Lnb to use.
     * @return result Result status of the operation.
     *         SUCCESS if successful,
     *         INVALID_ARGUMENT if the selected voltage isn't allowed,
     *         UNKNOWN_ERROR if failed for other reasons.
     */
    virtual ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> setVoltage(::android::hardware::tv::tuner::V1_0::LnbVoltage voltage) = 0;

    /**
     * Set the lnb's tone mode.
     *
     * @param tone the tone mode the Lnb to use.
     * @return result Result status of the operation.
     *         SUCCESS if successful,
     *         INVALID_ARGUMENT if the selected tone mode isn't allowed,
     *         UNKNOWN_ERROR if failed for other reasons.
     */
    virtual ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> setTone(::android::hardware::tv::tuner::V1_0::LnbTone tone) = 0;

    /**
     * Select the lnb's position.
     *
     * @param position the position the Lnb to use.
     * @return result Result status of the operation.
     *         SUCCESS if successful,
     *         INVALID_ARGUMENT if the selected position isn't allowed,
     *         UNKNOWN_ERROR if failed for other reasons.
     */
    virtual ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> setSatellitePosition(::android::hardware::tv::tuner::V1_0::LnbPosition position) = 0;

    /**
     *  Sends DiSEqC (Digital Satellite Equipment Control) message.
     *
     * Client sends DiSeqc message to DiSEqc to LNB. The response message from
     * the device comes back to the client through frontend's callback
     * onDiseqcMessage.
     *
     * @param diseqcMessage a byte array of data for DiSEqC message which is
     *        specified by EUTELSAT Bus Functional Specification Version 4.2.
     *
     * @return result Result status of the operation.
     *         SUCCESS if successful,
     *         INVALID_STATE if the frontend can't send DiSEqc Message, such as
     *         cable frontend.
     *         UNKNOWN_ERROR if failed for other reasons.
     */
    virtual ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> sendDiseqcMessage(const ::android::hardware::hidl_vec<uint8_t>& diseqcMessage) = 0;

    /**
     * Releases the LNB instance
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
    static ::android::hardware::Return<::android::sp<::android::hardware::tv::tuner::V1_0::ILnb>> castFrom(const ::android::sp<::android::hardware::tv::tuner::V1_0::ILnb>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::tv::tuner::V1_0::ILnb>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<ILnb> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ILnb> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ILnb> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ILnb> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<ILnb> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ILnb> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ILnb> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ILnb> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::tv::tuner::V1_0::ILnb>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::tv::tuner::V1_0::ILnb>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::tv::tuner::V1_0::ILnb::descriptor;
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


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_ILNB_H
