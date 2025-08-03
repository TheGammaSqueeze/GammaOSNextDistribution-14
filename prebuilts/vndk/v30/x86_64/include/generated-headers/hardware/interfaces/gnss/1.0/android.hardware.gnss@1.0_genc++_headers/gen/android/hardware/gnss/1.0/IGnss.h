#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_IGNSS_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_IGNSS_H

#include <android/hardware/gnss/1.0/IAGnss.h>
#include <android/hardware/gnss/1.0/IAGnssRil.h>
#include <android/hardware/gnss/1.0/IGnssBatching.h>
#include <android/hardware/gnss/1.0/IGnssCallback.h>
#include <android/hardware/gnss/1.0/IGnssConfiguration.h>
#include <android/hardware/gnss/1.0/IGnssDebug.h>
#include <android/hardware/gnss/1.0/IGnssGeofencing.h>
#include <android/hardware/gnss/1.0/IGnssMeasurement.h>
#include <android/hardware/gnss/1.0/IGnssNavigationMessage.h>
#include <android/hardware/gnss/1.0/IGnssNi.h>
#include <android/hardware/gnss/1.0/IGnssXtra.h>
#include <android/hardware/gnss/1.0/types.h>
#include <android/hidl/base/1.0/IBase.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V1_0 {

/**
 * Represents the standard GNSS (Global Navigation Satellite System) interface.
 */
struct IGnss : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.gnss@1.0::IGnss"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    enum class GnssPositionMode : uint8_t;
    enum class GnssPositionRecurrence : uint32_t;
    enum class GnssAidingData : uint16_t;

    /**
     * Requested operational mode for GNSS operation.
     */
    enum class GnssPositionMode : uint8_t {
        /**
         * Mode for running GNSS standalone (no assistance).
         */
        STANDALONE = 0,
        /**
         * AGNSS MS-Based mode.
         */
        MS_BASED = 1,
        /**
         * AGNSS MS-Assisted mode. This mode is not maintained by the platform anymore.
         * It is strongly recommended to use MS_BASED instead.
         */
        MS_ASSISTED = 2,
    };

    /**
     * Requested recurrence mode for GNSS operation.
     */
    enum class GnssPositionRecurrence : uint32_t {
        /**
         * Receive GNSS fixes on a recurring basis at a specified period.
         */
        RECURRENCE_PERIODIC = 0u,
        /**
         * Request a single shot GNSS fix.
         */
        RECURRENCE_SINGLE = 1u,
    };

    /**
     * Flags used to specify which aiding data to delete when calling
     * deleteAidingData().
     */
    enum class GnssAidingData : uint16_t {
        DELETE_EPHEMERIS = 1 /* 0x0001 */,
        DELETE_ALMANAC = 2 /* 0x0002 */,
        DELETE_POSITION = 4 /* 0x0004 */,
        DELETE_TIME = 8 /* 0x0008 */,
        DELETE_IONO = 16 /* 0x0010 */,
        DELETE_UTC = 32 /* 0x0020 */,
        DELETE_HEALTH = 64 /* 0x0040 */,
        DELETE_SVDIR = 128 /* 0x0080 */,
        DELETE_SVSTEER = 256 /* 0x0100 */,
        DELETE_SADATA = 512 /* 0x0200 */,
        DELETE_RTI = 1024 /* 0x0400 */,
        DELETE_CELLDB_INFO = 32768 /* 0x8000 */,
        DELETE_ALL = 65535 /* 0xFFFF */,
    };

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Opens the interface and provides the callback routines to the implementation of this
     * interface.
     *
     * The framework calls this method to instruct the GPS engine to prepare for serving requests
     * from the framework. The GNSS HAL implementation must respond to all GNSS requests from the
     * framework upon successful return from this method until cleanup() method is called to
     * close this interface.
     *
     * @param callback Callback interface for IGnss.
     *
     * @return success Returns true on success.
     */
    virtual ::android::hardware::Return<bool> setCallback(const ::android::sp<::android::hardware::gnss::V1_0::IGnssCallback>& callback) = 0;

    /**
     * Starts a location output stream using the IGnssCallback
     * gnssLocationCb(), following the settings from the most recent call to
     * setPositionMode().
     *
     * This output must operate independently of any GNSS location batching
     * operations, see the IGnssBatching.hal for details.
     *
     * @return success Returns true on success.
     */
    virtual ::android::hardware::Return<bool> start() = 0;

    /**
     * Stops the location output stream.
     *
     * @return success Returns true on success.
     */
    virtual ::android::hardware::Return<bool> stop() = 0;

    /**
     * Closes the interface.
     *
     * The cleanup() method is called by the framework to tell the GNSS HAL implementation to
     * not expect any GNSS requests in the immediate future - e.g. this may be called when
     * location is disabled by a user setting or low battery conditions. The GNSS HAL
     * implementation must immediately stop responding to any existing requests until the
     * setCallback() method is called again and the requests are re-initiated by the framework.
     *
     * After this method is called, the GNSS HAL implementation may choose to modify GNSS hardware
     * states to save power. It is expected that when setCallback() method is called again to
     * reopen this interface, to serve requests, there may be some minor delays in GNSS response
     * requests as hardware readiness states are restored, not to exceed those that occur on normal
     * device boot up.
     */
    virtual ::android::hardware::Return<void> cleanup() = 0;

    /**
     * Injects the current time.
     *
     * @param timeMs This is the UTC time received from the NTP server, its value
     * is given in milliseconds since January 1, 1970.
     * @param timeReferenceMs The corresponding value of
     * SystemClock.elapsedRealtime() from the device when the NTP response was
     * received in milliseconds.
     * @param uncertaintyMs Uncertainty associated with the value represented by
     * time. Represented in milliseconds.
     *
     * @return success Returns true if the operation is successful.
     */
    virtual ::android::hardware::Return<bool> injectTime(int64_t timeMs, int64_t timeReferenceMs, int32_t uncertaintyMs) = 0;

    /**
     * Injects current location from another location provider (typically cell
     * ID).
     *
     * @param latitudeDegrees Measured in Degrees.
     * @param longitudeDegrees Measured in Degrees.
     * @param accuracyMeters Measured in meters.
     *
     * @return success Returns true if successful.
     */
    virtual ::android::hardware::Return<bool> injectLocation(double latitudeDegrees, double longitudeDegrees, float accuracyMeters) = 0;

    /**
     * Specifies that the next call to start will not use the
     * information defined in the flags. GnssAidingData value of DELETE_ALL is
     * passed for a cold start.
     *
     * @param aidingDataFlags Flags specifying the aiding data to be deleted.
     */
    virtual ::android::hardware::Return<void> deleteAidingData(::android::hardware::gnss::V1_0::IGnss::GnssAidingData aidingDataFlags) = 0;

    /**
     * Sets the GnssPositionMode parameter,its associated recurrence value,
     * the time between fixes,requested fix accuracy and time to first fix.
     *
     * @param mode  Parameter must be one of MS_BASED or STANDALONE.
     * It is allowed by the platform (and it is recommended) to fallback to
     * MS_BASED if MS_ASSISTED is passed in, and MS_BASED is supported.
     * @recurrence GNSS position recurrence value, either periodic or single.
     * @param minIntervalMs Represents the time between fixes in milliseconds.
     * @param preferredAccuracyMeters Represents the requested fix accuracy in meters.
     * @param preferredTimeMs Represents the requested time to first fix in milliseconds.
     *
     * @return success Returns true if successful.
     */
    virtual ::android::hardware::Return<bool> setPositionMode(::android::hardware::gnss::V1_0::IGnss::GnssPositionMode mode, ::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence recurrence, uint32_t minIntervalMs, uint32_t preferredAccuracyMeters, uint32_t preferredTimeMs) = 0;

    /**
     * This method returns the IAGnssRil Interface.
     *
     * @return aGnssRilIface Handle to the IAGnssRil interface.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IAGnssRil>> getExtensionAGnssRil() = 0;

    /**
     * This method returns the IGnssGeofencing Interface.
     *
     * @return gnssGeofencingIface Handle to the IGnssGeofencing interface.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssGeofencing>> getExtensionGnssGeofencing() = 0;

    /**
     * This method returns the IAGnss Interface.
     *
     * @return aGnssIface Handle to the IAGnss interface.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IAGnss>> getExtensionAGnss() = 0;

    /**
     * This method returns the IGnssNi interface.
     *
     * @return gnssNiIface Handle to the IGnssNi interface.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssNi>> getExtensionGnssNi() = 0;

    /**
     * This method returns the IGnssMeasurement interface.
     *
     * @return gnssMeasurementIface Handle to the IGnssMeasurement interface.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssMeasurement>> getExtensionGnssMeasurement() = 0;

    /**
     * This method returns the IGnssNavigationMessage interface.
     *
     * @return gnssNavigationIface gnssNavigationIface to the IGnssNavigationMessage interface.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssNavigationMessage>> getExtensionGnssNavigationMessage() = 0;

    /**
     * This method returns the IGnssXtra interface.
     *
     * @return xtraIface Handle to the IGnssXtra interface.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssXtra>> getExtensionXtra() = 0;

    /**
     * This method returns the IGnssConfiguration interface.
     *
     * @return gnssConfigIface Handle to the IGnssConfiguration interface.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssConfiguration>> getExtensionGnssConfiguration() = 0;

    /**
     * This method returns the IGnssDebug interface.
     *
     * @return debugIface Handle to the IGnssDebug interface.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssDebug>> getExtensionGnssDebug() = 0;

    /**
     * This method returns the IGnssBatching interface.
     *
     * @return batchingIface Handle to the IGnssBatching interface.
     */
    virtual ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssBatching>> getExtensionGnssBatching() = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnss>> castFrom(const ::android::sp<::android::hardware::gnss::V1_0::IGnss>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnss>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IGnss> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGnss> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IGnss> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGnss> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IGnss> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGnss> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IGnss> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IGnss> getService(bool getStub) { return getService("default", getStub); }
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

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::gnss::V1_0::IGnss::GnssPositionMode o);
static inline void PrintTo(::android::hardware::gnss::V1_0::IGnss::GnssPositionMode o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::IGnss::GnssPositionMode lhs, const ::android::hardware::gnss::V1_0::IGnss::GnssPositionMode rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::gnss::V1_0::IGnss::GnssPositionMode rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::IGnss::GnssPositionMode lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::IGnss::GnssPositionMode lhs, const ::android::hardware::gnss::V1_0::IGnss::GnssPositionMode rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::gnss::V1_0::IGnss::GnssPositionMode rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::IGnss::GnssPositionMode lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::gnss::V1_0::IGnss::GnssPositionMode e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::gnss::V1_0::IGnss::GnssPositionMode e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence o);
static inline void PrintTo(::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence lhs, const ::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence lhs, const ::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint16_t o);
static inline std::string toString(::android::hardware::gnss::V1_0::IGnss::GnssAidingData o);
static inline void PrintTo(::android::hardware::gnss::V1_0::IGnss::GnssAidingData o, ::std::ostream* os);
constexpr uint16_t operator|(const ::android::hardware::gnss::V1_0::IGnss::GnssAidingData lhs, const ::android::hardware::gnss::V1_0::IGnss::GnssAidingData rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const uint16_t lhs, const ::android::hardware::gnss::V1_0::IGnss::GnssAidingData rhs) {
    return static_cast<uint16_t>(lhs | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const ::android::hardware::gnss::V1_0::IGnss::GnssAidingData lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | rhs);
}
constexpr uint16_t operator&(const ::android::hardware::gnss::V1_0::IGnss::GnssAidingData lhs, const ::android::hardware::gnss::V1_0::IGnss::GnssAidingData rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const uint16_t lhs, const ::android::hardware::gnss::V1_0::IGnss::GnssAidingData rhs) {
    return static_cast<uint16_t>(lhs & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const ::android::hardware::gnss::V1_0::IGnss::GnssAidingData lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & rhs);
}
constexpr uint16_t &operator|=(uint16_t& v, const ::android::hardware::gnss::V1_0::IGnss::GnssAidingData e) {
    v |= static_cast<uint16_t>(e);
    return v;
}
constexpr uint16_t &operator&=(uint16_t& v, const ::android::hardware::gnss::V1_0::IGnss::GnssAidingData e) {
    v &= static_cast<uint16_t>(e);
    return v;
}

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V1_0::IGnss>& o);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::gnss::V1_0::IGnss::GnssPositionMode>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IGnss::GnssPositionMode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V1_0::IGnss::GnssPositionMode::STANDALONE) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IGnss::GnssPositionMode::STANDALONE)) {
        os += (first ? "" : " | ");
        os += "STANDALONE";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnss::GnssPositionMode::STANDALONE;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnss::GnssPositionMode::MS_BASED) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IGnss::GnssPositionMode::MS_BASED)) {
        os += (first ? "" : " | ");
        os += "MS_BASED";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnss::GnssPositionMode::MS_BASED;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnss::GnssPositionMode::MS_ASSISTED) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IGnss::GnssPositionMode::MS_ASSISTED)) {
        os += (first ? "" : " | ");
        os += "MS_ASSISTED";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnss::GnssPositionMode::MS_ASSISTED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V1_0::IGnss::GnssPositionMode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V1_0::IGnss::GnssPositionMode::STANDALONE) {
        return "STANDALONE";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnss::GnssPositionMode::MS_BASED) {
        return "MS_BASED";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnss::GnssPositionMode::MS_ASSISTED) {
        return "MS_ASSISTED";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V1_0::IGnss::GnssPositionMode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence::RECURRENCE_PERIODIC) == static_cast<uint32_t>(::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence::RECURRENCE_PERIODIC)) {
        os += (first ? "" : " | ");
        os += "RECURRENCE_PERIODIC";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence::RECURRENCE_PERIODIC;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence::RECURRENCE_SINGLE) == static_cast<uint32_t>(::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence::RECURRENCE_SINGLE)) {
        os += (first ? "" : " | ");
        os += "RECURRENCE_SINGLE";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence::RECURRENCE_SINGLE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence::RECURRENCE_PERIODIC) {
        return "RECURRENCE_PERIODIC";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence::RECURRENCE_SINGLE) {
        return "RECURRENCE_SINGLE";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::gnss::V1_0::IGnss::GnssAidingData>(uint16_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IGnss::GnssAidingData> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_EPHEMERIS) == static_cast<uint16_t>(::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_EPHEMERIS)) {
        os += (first ? "" : " | ");
        os += "DELETE_EPHEMERIS";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_EPHEMERIS;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_ALMANAC) == static_cast<uint16_t>(::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_ALMANAC)) {
        os += (first ? "" : " | ");
        os += "DELETE_ALMANAC";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_ALMANAC;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_POSITION) == static_cast<uint16_t>(::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_POSITION)) {
        os += (first ? "" : " | ");
        os += "DELETE_POSITION";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_POSITION;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_TIME) == static_cast<uint16_t>(::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_TIME)) {
        os += (first ? "" : " | ");
        os += "DELETE_TIME";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_TIME;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_IONO) == static_cast<uint16_t>(::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_IONO)) {
        os += (first ? "" : " | ");
        os += "DELETE_IONO";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_IONO;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_UTC) == static_cast<uint16_t>(::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_UTC)) {
        os += (first ? "" : " | ");
        os += "DELETE_UTC";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_UTC;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_HEALTH) == static_cast<uint16_t>(::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_HEALTH)) {
        os += (first ? "" : " | ");
        os += "DELETE_HEALTH";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_HEALTH;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_SVDIR) == static_cast<uint16_t>(::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_SVDIR)) {
        os += (first ? "" : " | ");
        os += "DELETE_SVDIR";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_SVDIR;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_SVSTEER) == static_cast<uint16_t>(::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_SVSTEER)) {
        os += (first ? "" : " | ");
        os += "DELETE_SVSTEER";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_SVSTEER;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_SADATA) == static_cast<uint16_t>(::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_SADATA)) {
        os += (first ? "" : " | ");
        os += "DELETE_SADATA";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_SADATA;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_RTI) == static_cast<uint16_t>(::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_RTI)) {
        os += (first ? "" : " | ");
        os += "DELETE_RTI";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_RTI;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_CELLDB_INFO) == static_cast<uint16_t>(::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_CELLDB_INFO)) {
        os += (first ? "" : " | ");
        os += "DELETE_CELLDB_INFO";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_CELLDB_INFO;
    }
    if ((o & ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_ALL) == static_cast<uint16_t>(::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_ALL)) {
        os += (first ? "" : " | ");
        os += "DELETE_ALL";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_ALL;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V1_0::IGnss::GnssAidingData o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_EPHEMERIS) {
        return "DELETE_EPHEMERIS";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_ALMANAC) {
        return "DELETE_ALMANAC";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_POSITION) {
        return "DELETE_POSITION";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_TIME) {
        return "DELETE_TIME";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_IONO) {
        return "DELETE_IONO";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_UTC) {
        return "DELETE_UTC";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_HEALTH) {
        return "DELETE_HEALTH";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_SVDIR) {
        return "DELETE_SVDIR";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_SVSTEER) {
        return "DELETE_SVSTEER";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_SADATA) {
        return "DELETE_SADATA";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_RTI) {
        return "DELETE_RTI";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_CELLDB_INFO) {
        return "DELETE_CELLDB_INFO";
    }
    if (o == ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_ALL) {
        return "DELETE_ALL";
    }
    std::string os;
    os += toHexString(static_cast<uint16_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V1_0::IGnss::GnssAidingData o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V1_0::IGnss>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::gnss::V1_0::IGnss::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_0
}  // namespace gnss
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
template<> inline constexpr std::array<::android::hardware::gnss::V1_0::IGnss::GnssPositionMode, 3> hidl_enum_values<::android::hardware::gnss::V1_0::IGnss::GnssPositionMode> = {
    ::android::hardware::gnss::V1_0::IGnss::GnssPositionMode::STANDALONE,
    ::android::hardware::gnss::V1_0::IGnss::GnssPositionMode::MS_BASED,
    ::android::hardware::gnss::V1_0::IGnss::GnssPositionMode::MS_ASSISTED,
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
template<> inline constexpr std::array<::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence, 2> hidl_enum_values<::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence> = {
    ::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence::RECURRENCE_PERIODIC,
    ::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence::RECURRENCE_SINGLE,
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
template<> inline constexpr std::array<::android::hardware::gnss::V1_0::IGnss::GnssAidingData, 13> hidl_enum_values<::android::hardware::gnss::V1_0::IGnss::GnssAidingData> = {
    ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_EPHEMERIS,
    ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_ALMANAC,
    ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_POSITION,
    ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_TIME,
    ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_IONO,
    ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_UTC,
    ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_HEALTH,
    ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_SVDIR,
    ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_SVSTEER,
    ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_SADATA,
    ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_RTI,
    ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_CELLDB_INFO,
    ::android::hardware::gnss::V1_0::IGnss::GnssAidingData::DELETE_ALL,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_IGNSS_H
