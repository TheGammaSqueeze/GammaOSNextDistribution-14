#ifndef HIDL_GENERATED_ANDROID_HARDWARE_HEALTH_V2_0_IHEALTH_H
#define HIDL_GENERATED_ANDROID_HARDWARE_HEALTH_V2_0_IHEALTH_H

#include <android/hardware/health/1.0/types.h>
#include <android/hardware/health/2.0/IHealthInfoCallback.h>
#include <android/hardware/health/2.0/types.h>
#include <android/hidl/base/1.0/IBase.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace health {
namespace V2_0 {

/**
 * IHealth manages health info and posts events on registered callbacks.
 */
struct IHealth : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.health@2.0::IHealth"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Register a callback for any health info events.
     *
     * Registering a new callback must not unregister the old one; the old
     * callback remains registered until one of the following happens:
     * - A client explicitly calls {@link unregisterCallback} to unregister it.
     * - The client process that hosts the callback dies.
     *
     * @param callback the callback to register.
     * @return result SUCCESS if successful,
     *                UNKNOWN for other errors.
     */
    virtual ::android::hardware::Return<::android::hardware::health::V2_0::Result> registerCallback(const ::android::sp<::android::hardware::health::V2_0::IHealthInfoCallback>& callback) = 0;

    /**
     * Explicitly unregister a callback that is previously registered through
     * {@link registerCallback}.
     *
     * @param callback the callback to unregister
     * @return result SUCCESS if successful,
     *                NOT_FOUND if callback is not registered previously,
     *                UNKNOWN for other errors.
     */
    virtual ::android::hardware::Return<::android::hardware::health::V2_0::Result> unregisterCallback(const ::android::sp<::android::hardware::health::V2_0::IHealthInfoCallback>& callback) = 0;

    /**
     * Schedule update.
     *
     * When update() is called, the service must notify all registered callbacks
     * with the most recent health info.
     *
     * @return result SUCCESS if successful,
     *                CALLBACK_DIED if any registered callback is dead,
     *                UNKNOWN for other errors.
     */
    virtual ::android::hardware::Return<::android::hardware::health::V2_0::Result> update() = 0;

    /**
     * Return callback for getChargeCounter
     */
    using getChargeCounter_cb = std::function<void(::android::hardware::health::V2_0::Result result, int32_t value)>;
    /**
     * Get battery capacity in microampere-hours(µAh).
     *
     * @return result SUCCESS if successful,
     *                NOT_SUPPORTED if this property is not supported
     *                 (e.g. the file that stores this property does not exist),
     *                UNKNOWN for other errors.
     * @return value battery capacity, or 0 if not successful.
     */
    virtual ::android::hardware::Return<void> getChargeCounter(getChargeCounter_cb _hidl_cb) = 0;

    /**
     * Return callback for getCurrentNow
     */
    using getCurrentNow_cb = std::function<void(::android::hardware::health::V2_0::Result result, int32_t value)>;
    /**
     * Get instantaneous battery current in microamperes(µA).
     *
     * Positive values indicate net current entering the battery from a charge
     * source, negative values indicate net current discharging from the
     * battery.
     *
     * @return result SUCCESS if successful,
     *                NOT_SUPPORTED if this property is not supported
     *                 (e.g. the file that stores this property does not exist),
     *                UNKNOWN for other errors.
     * @return value instantaneous battery current, or 0 if not
     *               successful.
     */
    virtual ::android::hardware::Return<void> getCurrentNow(getCurrentNow_cb _hidl_cb) = 0;

    /**
     * Return callback for getCurrentAverage
     */
    using getCurrentAverage_cb = std::function<void(::android::hardware::health::V2_0::Result result, int32_t value)>;
    /**
     * Get average battery current in microamperes(µA).
     *
     * Positive values indicate net current entering the battery from a charge
     * source, negative values indicate net current discharging from the
     * battery. The time period over which the average is computed may depend on
     * the fuel gauge hardware and its configuration.
     *
     * @return result SUCCESS if successful,
     *                NOT_SUPPORTED if this property is not supported
     *                 (e.g. the file that stores this property does not exist),
     *                UNKNOWN for other errors.
     * @return value average battery current, or 0 if not successful.
     */
    virtual ::android::hardware::Return<void> getCurrentAverage(getCurrentAverage_cb _hidl_cb) = 0;

    /**
     * Return callback for getCapacity
     */
    using getCapacity_cb = std::function<void(::android::hardware::health::V2_0::Result result, int32_t value)>;
    /**
     * Get remaining battery capacity percentage of total capacity
     * (with no fractional part).
     *
     * @return result SUCCESS if successful,
     *                NOT_SUPPORTED if this property is not supported
     *                 (e.g. the file that stores this property does not exist),
     *                UNKNOWN for other errors.
     * @return value remaining battery capacity, or 0 if not successful.
     */
    virtual ::android::hardware::Return<void> getCapacity(getCapacity_cb _hidl_cb) = 0;

    /**
     * Return callback for getEnergyCounter
     */
    using getEnergyCounter_cb = std::function<void(::android::hardware::health::V2_0::Result result, int64_t value)>;
    /**
     * Get battery remaining energy in nanowatt-hours.
     *
     * @return result SUCCESS if successful,
     *                NOT_SUPPORTED if this property is not supported,
     *                UNKNOWN for other errors.
     * @return value remaining energy, or 0 if not successful.
     */
    virtual ::android::hardware::Return<void> getEnergyCounter(getEnergyCounter_cb _hidl_cb) = 0;

    /**
     * Return callback for getChargeStatus
     */
    using getChargeStatus_cb = std::function<void(::android::hardware::health::V2_0::Result result, ::android::hardware::health::V1_0::BatteryStatus value)>;
    /**
     * Get battery charge status.
     *
     * @return result SUCCESS if successful,
     *                NOT_SUPPORTED if this property is not supported
     *                 (e.g. the file that stores this property does not exist),
     *                UNKNOWN other errors.
     * @return value charge status, or UNKNOWN if not successful.
     */
    virtual ::android::hardware::Return<void> getChargeStatus(getChargeStatus_cb _hidl_cb) = 0;

    /**
     * Return callback for getStorageInfo
     */
    using getStorageInfo_cb = std::function<void(::android::hardware::health::V2_0::Result result, const ::android::hardware::hidl_vec<::android::hardware::health::V2_0::StorageInfo>& value)>;
    /**
     * Get storage info.
     *
     * @return result SUCCESS if successful,
     *                NOT_SUPPORTED if this property is not supported,
     *                UNKNOWN other errors.
     * @return value vector of StorageInfo structs, to be ignored if result is not
     *               SUCCESS.
     */
    virtual ::android::hardware::Return<void> getStorageInfo(getStorageInfo_cb _hidl_cb) = 0;

    /**
     * Return callback for getDiskStats
     */
    using getDiskStats_cb = std::function<void(::android::hardware::health::V2_0::Result result, const ::android::hardware::hidl_vec<::android::hardware::health::V2_0::DiskStats>& value)>;
    /**
     * Gets disk statistics (number of reads/writes processed, number of I/O
     * operations in flight etc).
     *
     * @return result SUCCESS if successful,
     *                NOT_SUPPORTED if this property is not supported,
     *                UNKNOWN other errors.
     * @return value vector of disk statistics, to be ignored if result is not SUCCESS.
     *               The mapping is index 0->sda, 1->sdb and so on.
     */
    virtual ::android::hardware::Return<void> getDiskStats(getDiskStats_cb _hidl_cb) = 0;

    /**
     * Return callback for getHealthInfo
     */
    using getHealthInfo_cb = std::function<void(::android::hardware::health::V2_0::Result result, const ::android::hardware::health::V2_0::HealthInfo& value)>;
    /**
     * Get Health Information.
     *
     * @return result SUCCESS if successful,
     *                NOT_SUPPORTED if this API is not supported,
     *                UNKNOWN for other errors.
     * @return value  Health information, to be ignored if result is not
     *                SUCCESS.
     */
    virtual ::android::hardware::Return<void> getHealthInfo(getHealthInfo_cb _hidl_cb) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::health::V2_0::IHealth>> castFrom(const ::android::sp<::android::hardware::health::V2_0::IHealth>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::health::V2_0::IHealth>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IHealth> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IHealth> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IHealth> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IHealth> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IHealth> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IHealth> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IHealth> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IHealth> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::health::V2_0::IHealth>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::health::V2_0::IHealth>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::health::V2_0::IHealth::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V2_0
}  // namespace health
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_HEALTH_V2_0_IHEALTH_H
