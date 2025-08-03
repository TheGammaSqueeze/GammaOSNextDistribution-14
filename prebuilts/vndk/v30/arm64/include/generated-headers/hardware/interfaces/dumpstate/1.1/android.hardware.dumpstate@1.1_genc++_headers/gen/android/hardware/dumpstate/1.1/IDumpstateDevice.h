#ifndef HIDL_GENERATED_ANDROID_HARDWARE_DUMPSTATE_V1_1_IDUMPSTATEDEVICE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_DUMPSTATE_V1_1_IDUMPSTATEDEVICE_H

#include <android/hardware/dumpstate/1.0/IDumpstateDevice.h>
#include <android/hardware/dumpstate/1.1/types.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace dumpstate {
namespace V1_1 {

struct IDumpstateDevice : public ::android::hardware::dumpstate::V1_0::IDumpstateDevice {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.dumpstate@1.1::IDumpstateDevice"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Dump device-specific state into the given file descriptors.
     *
     * One file descriptor must be passed to this method but two may be passed:
     * the first descriptor must be used to dump device-specific state in text
     * format, the second descriptor is optional and may be used to dump
     * device-specific state in binary format.
     *
     * @param h A native handle with one or two valid file descriptors.
     */
    virtual ::android::hardware::Return<void> dumpstateBoard(const ::android::hardware::hidl_handle& h) = 0;

    /**
     * Extension of dumpstateBoard which also accepts a mode parameter to limit dumped data.
     *
     * For an example of when this is relevant, consider a bug report being generated with
     * DumpstateMode::CONNECTIVITY - there is no reason to include camera or USB logs in this type
     * of report.
     *
     * The 1.0 version of #dumpstateBoard(handle) should just delegate to this new method and pass
     * DumpstateMode::DEFAULT and a timeout of 30,000ms (30 seconds).
     *
     * This method may still be called by the dumpstate routine even if getVerboseLoggingEnabled
     * returns false. In this case, it may include essential information but must not include
     * information that identifies the user.
     *
     * @param h A native handle with one or two valid file descriptors. The first FD is for text
     *     output, the second (if present) is for binary output.
     * @param mode A mode value to restrict dumped content.
     * @param timeoutMillis An approximate "budget" for how much time this call has been allotted.
     *     If execution runs longer than this, the IDumpstateDevice service may be killed and only
     *     partial information will be included in the report.
     * @return status A DumpstateStatus value indicating the final result.
     */
    virtual ::android::hardware::Return<::android::hardware::dumpstate::V1_1::DumpstateStatus> dumpstateBoard_1_1(const ::android::hardware::hidl_handle& h, ::android::hardware::dumpstate::V1_1::DumpstateMode mode, uint64_t timeoutMillis) = 0;

    /**
     * Turns verbose device vendor logging on or off.
     *
     * The setting should be persistent across reboots. Underlying implementations may need to start
     * vendor logging daemons, set system properties, or change logging masks, for example. Given
     * that many vendor logs contain significant amounts of private information and may come with
     * memory/storage/battery impacts, calling this method on a user build should only be done after
     * user consent has been obtained, e.g. from a toggle in developer settings.
     *
     * Even if verbose logging has been disabled, dumpstateBoard may still be called by the
     * dumpstate routine, and essential information that does not identify the user may be included.
     *
     * @param enable Whether to enable or disable verbose vendor logging.
     */
    virtual ::android::hardware::Return<void> setVerboseLoggingEnabled(bool enable) = 0;

    /**
     * Queries the current state of verbose device logging. Primarily for UI and informative
     * purposes.
     *
     * Even if verbose logging has been disabled, dumpstateBoard may still be called by the
     * dumpstate routine, and essential information that does not identify the user may be included.
     *
     * @return enabled Whether or not verbose vendor logging is currently enabled.
     */
    virtual ::android::hardware::Return<bool> getVerboseLoggingEnabled() = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::dumpstate::V1_1::IDumpstateDevice>> castFrom(const ::android::sp<::android::hardware::dumpstate::V1_1::IDumpstateDevice>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::dumpstate::V1_1::IDumpstateDevice>> castFrom(const ::android::sp<::android::hardware::dumpstate::V1_0::IDumpstateDevice>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::dumpstate::V1_1::IDumpstateDevice>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IDumpstateDevice> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IDumpstateDevice> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IDumpstateDevice> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IDumpstateDevice> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IDumpstateDevice> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IDumpstateDevice> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IDumpstateDevice> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IDumpstateDevice> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::dumpstate::V1_1::IDumpstateDevice>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::dumpstate::V1_1::IDumpstateDevice>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::dumpstate::V1_1::IDumpstateDevice::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_1
}  // namespace dumpstate
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_DUMPSTATE_V1_1_IDUMPSTATEDEVICE_H
