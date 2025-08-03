#ifndef HIDL_GENERATED_ANDROID_HARDWARE_POWER_STATS_V1_0_IPOWERSTATS_H
#define HIDL_GENERATED_ANDROID_HARDWARE_POWER_STATS_V1_0_IPOWERSTATS_H

#include <android/hardware/power/stats/1.0/types.h>
#include <android/hidl/base/1.0/IBase.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace power {
namespace stats {
namespace V1_0 {

struct IPowerStats : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.power.stats@1.0::IPowerStats"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Return callback for getRailInfo
     */
    using getRailInfo_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::power::stats::V1_0::RailInfo>& rails, ::android::hardware::power::stats::V1_0::Status status)>;
    /**
     * Rail information:
     * Reports information related to the rails being monitored.
     *
     * @return rails Information about monitored rails.
     * @return status SUCCESS on success or NOT_SUPPORTED if
     *     feature is not enabled or FILESYSTEM_ERROR on filesystem nodes
     *     access error.
     */
    virtual ::android::hardware::Return<void> getRailInfo(getRailInfo_cb _hidl_cb) = 0;

    /**
     * Return callback for getEnergyData
     */
    using getEnergyData_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::power::stats::V1_0::EnergyData>& data, ::android::hardware::power::stats::V1_0::Status status)>;
    /**
     * Rail level energy measurements for low frequency clients:
     * Reports accumulated energy since boot on each rail.
     *
     * @param railIndices Indices of rails for which data is required.
     *     To get data for all rails pass an empty vector. Rail name to
     *     index mapping can be queried from getRailInfo() API.
     * @return data Energy values since boot for all requested rails.
     * @return status SUCCESS on success or NOT_SUPPORTED if
     *     feature is not enabled or FILESYSTEM_ERROR on filesystem nodes
     *     access error.
     */
    virtual ::android::hardware::Return<void> getEnergyData(const ::android::hardware::hidl_vec<uint32_t>& railIndices, getEnergyData_cb _hidl_cb) = 0;

    /**
     * Return callback for streamEnergyData
     */
    using streamEnergyData_cb = std::function<void(const ::android::hardware::MQDescriptorSync<::android::hardware::power::stats::V1_0::EnergyData>& mqDesc, uint32_t numSamples, uint32_t railsPerSample, ::android::hardware::power::stats::V1_0::Status status)>;
    /**
     * Stream rail level power measurements for high frequency clients:
     * Streams accumulated energy since boot on each rail. This API is
     * asynchronous.
     *
     * @param timeMs Time(in ms) for which energyData should be streamed
     * @param samplingRate Frequency(in Hz) at which samples should be
     *     captured. If the requested sampling rate is not supported then
     *     SUCCESS is returned and numSamples are reported back according
     *     to the supported sampling rate.
     * @return mqDesc Blocking Synchronous Fast Message Queue descriptor - One
     *     writer(power.stats HAL) and one reader are supported. Data is
     *     present in the following format in the queue:
     *     +-----------------------+       <--
     *     | EnergyData for rail 1 |         |
     *     +-----------------------+         |
     *     | EnergyData for rail 2 |         |
     *     +-----------------------+         |
     *     |          .            |         |-- 1st Sample
     *     |          .            |         |
     *     |          .            |         |
     *     +-----------------------+         |
     *     | EnergyData for rail n |         |
     *     +-----------------------+       <--
     *     |          .            |
     *     |          .            |
     *     |          .            |
     *     +-----------------------+       <--
     *     | EnergyData for rail 1 |         |
     *     +-----------------------+         |
     *     | EnergyData for rail 2 |         |
     *     +-----------------------+         |
     *     |          .            |         |-- kth Sample
     *     |          .            |         |
     *     |          .            |         |
     *     +-----------------------+         |
     *     | EnergyData for rail n |         |
     *     +-----------------------+       <--
     *
     *     where,
     *     n = railsPerSample
     *     k = numSamples
     *
     * @return numSamples Number of samples which will be generated in timeMs.
     * @return railsPerSample Number of rails measured per sample.
     * @return status SUCCESS on success or FILESYSTEM_ERROR on filesystem
     *     nodes access or NOT_SUPPORTED if feature is not enabled or
     *     INSUFFICIENT_RESOURCES if there are not enough resources.
     */
    virtual ::android::hardware::Return<void> streamEnergyData(uint32_t timeMs, uint32_t samplingRate, streamEnergyData_cb _hidl_cb) = 0;

    /**
     * Return callback for getPowerEntityInfo
     */
    using getPowerEntityInfo_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::power::stats::V1_0::PowerEntityInfo>& powerEntityInfos, ::android::hardware::power::stats::V1_0::Status status)>;
    /**
     * PowerEntity information:
     * Reports information related to all supported PowerEntity(s) for which
     * data is available. A PowerEntity is defined as a platform subsystem,
     * peripheral, or power domain that impacts the total device power
     * consumption.
     *
     * @return powerEntityInfos List of information on each PowerEntity
     * @return status SUCCESS on success, NOT_SUPPORTED if feature is not
     *     enabled, FILESYSTEM_ERROR if there was an error accessing the
     *     filesystem.
     */
    virtual ::android::hardware::Return<void> getPowerEntityInfo(getPowerEntityInfo_cb _hidl_cb) = 0;

    /**
     * Return callback for getPowerEntityStateInfo
     */
    using getPowerEntityStateInfo_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::power::stats::V1_0::PowerEntityStateSpace>& powerEntityStateSpaces, ::android::hardware::power::stats::V1_0::Status status)>;
    /**
     * PowerEntity state information:
     * Reports the set of power states for which the specified
     * PowerEntity(s) provide residency data.
     *
     * @param powerEntityIds collection of IDs of PowerEntity(s) for which
     *     state information is requested. PowerEntity name to ID mapping may
     *     be queried from getPowerEntityInfo(). To get state space
     *     information for all PowerEntity(s) pass an empty vector.
     *
     * @return powerEntityStateSpaces PowerEntity state space information for
     *     each specified PowerEntity that provides state space information.
     * @return status SUCCESS on success, NOT_SUPPORTED if feature is not
     *     enabled, FILESYSTEM_ERROR if there was an error accessing the
     *     filesystem, INVALID_INPUT if any requested PowerEntity(s) do not
     *     provide state space information and there was not a filesystem error.
     */
    virtual ::android::hardware::Return<void> getPowerEntityStateInfo(const ::android::hardware::hidl_vec<uint32_t>& powerEntityIds, getPowerEntityStateInfo_cb _hidl_cb) = 0;

    /**
     * Return callback for getPowerEntityStateResidencyData
     */
    using getPowerEntityStateResidencyData_cb = std::function<void(const ::android::hardware::hidl_vec<::android::hardware::power::stats::V1_0::PowerEntityStateResidencyResult>& stateResidencyResults, ::android::hardware::power::stats::V1_0::Status status)>;
    /**
     * PowerEntity residencies for low frequency clients:
     * Reports accumulated residency data for each specified PowerEntity.
     * Each PowerEntity may reside in one of multiple states. It may also
     * transition to another state. Residency data is an accumulation of time
     * that a specified PowerEntity resided in each of its possible states,
     * the number of times that each state was entered, and a timestamp
     * corresponding to the last time that state was entered. Data is
     * accumulated starting from the last time the PowerEntity was reset.
     *
     * @param powerEntityId collection of IDs of PowerEntity(s) for which
     *     residency data is requested. PowerEntity name to ID mapping may
     *     be queried from getPowerEntityInfo(). To get state residency
     *     data for all PowerEntity(s) pass an empty vector.
     * @return stateResidencyResults state residency data for each specified
     *     PowerEntity that provides state residency data.
     * @return status SUCCESS on success, NOT_SUPPORTED if feature is not
     *     enabled, FILESYSTEM_ERROR if there was an error accessing the
     *     filesystem, INVALID_INPUT if any requested PowerEntity(s) do not
     *     provide state residency data and there was not a filesystem error.
     */
    virtual ::android::hardware::Return<void> getPowerEntityStateResidencyData(const ::android::hardware::hidl_vec<uint32_t>& powerEntityIds, getPowerEntityStateResidencyData_cb _hidl_cb) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::power::stats::V1_0::IPowerStats>> castFrom(const ::android::sp<::android::hardware::power::stats::V1_0::IPowerStats>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::power::stats::V1_0::IPowerStats>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IPowerStats> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IPowerStats> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IPowerStats> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IPowerStats> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IPowerStats> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IPowerStats> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IPowerStats> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IPowerStats> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::power::stats::V1_0::IPowerStats>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::power::stats::V1_0::IPowerStats>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::power::stats::V1_0::IPowerStats::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_0
}  // namespace stats
}  // namespace power
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_POWER_STATS_V1_0_IPOWERSTATS_H
