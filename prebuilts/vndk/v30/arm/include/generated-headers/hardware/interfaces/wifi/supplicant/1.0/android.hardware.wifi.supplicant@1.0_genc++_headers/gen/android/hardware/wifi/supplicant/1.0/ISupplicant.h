#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_ISUPPLICANT_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_ISUPPLICANT_H

#include <android/hardware/wifi/supplicant/1.0/ISupplicantCallback.h>
#include <android/hardware/wifi/supplicant/1.0/ISupplicantIface.h>
#include <android/hardware/wifi/supplicant/1.0/types.h>
#include <android/hidl/base/1.0/IBase.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace V1_0 {

/**
 * Interface exposed by the supplicant HIDL service registered
 * with the hardware service manager.
 * This is the root level object for any the supplicant interactions.
 */
struct ISupplicant : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.wifi.supplicant@1.0::ISupplicant"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    enum class DebugLevel : uint32_t;
    struct IfaceInfo;

    /**
     * Debug levels for the supplicant.
     * Only log messages with a level greater than the set level
     * (via |setDebugParams|) will be logged.
     */
    enum class DebugLevel : uint32_t {
        EXCESSIVE = 0u,
        MSGDUMP = 1u,
        DEBUG = 2u,
        INFO = 3u,
        WARNING = 4u,
        ERROR = 5u,
    };

    /**
     * Structure describing the type and name of an iface
     * controlled by the supplicant.
     */
    struct IfaceInfo final {
        /**
         * Type of the network interface.
         */
        ::android::hardware::wifi::supplicant::V1_0::IfaceType type __attribute__ ((aligned(4)));
        /**
         * Name of the network interface, e.g., wlan0
         */
        ::android::hardware::hidl_string name __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::wifi::supplicant::V1_0::ISupplicant::IfaceInfo, type) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::supplicant::V1_0::ISupplicant::IfaceInfo, name) == 8, "wrong offset");
    static_assert(sizeof(::android::hardware::wifi::supplicant::V1_0::ISupplicant::IfaceInfo) == 24, "wrong size");
    static_assert(__alignof(::android::hardware::wifi::supplicant::V1_0::ISupplicant::IfaceInfo) == 8, "wrong alignment");

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Return callback for getInterface
     */
    using getInterface_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status, const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantIface>& iface)>;
    /**
     * Gets a HIDL interface object for the interface corresponding to iface
     * name which the supplicant already controls.
     *
     * @param ifaceInfo Combination of the iface type and name retrieved
     *        using |listInterfaces|.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|,
     *         |SupplicantStatusCode.FAILURE_IFACE_UNKOWN|
     * @return iface HIDL interface object representing the interface if
     *         successful, null otherwise.
     */
    virtual ::android::hardware::Return<void> getInterface(const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::IfaceInfo& ifaceInfo, getInterface_cb _hidl_cb) = 0;

    /**
     * Return callback for listInterfaces
     */
    using listInterfaces_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status, const ::android::hardware::hidl_vec<::android::hardware::wifi::supplicant::V1_0::ISupplicant::IfaceInfo>& ifaces)>;
    /**
     * Retrieve a list of all the interfaces controlled by the supplicant.
     *
     * The corresponding |ISupplicantIface| object for any interface can be
     * retrieved using |getInterface| method.
     *
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     * @return ifaces List of all interfaces controlled by the supplicant.
     */
    virtual ::android::hardware::Return<void> listInterfaces(listInterfaces_cb _hidl_cb) = 0;

    /**
     * Return callback for registerCallback
     */
    using registerCallback_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Register for callbacks from the supplicant service.
     *
     * These callbacks are invoked for global events that are not specific
     * to any interface or network. Registration of multiple callback
     * objects is supported. These objects must be deleted when the corresponding
     * client process is dead.
     *
     * @param callback An instance of the |ISupplicantCallback| HIDL interface
     *        object.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> registerCallback(const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantCallback>& callback, registerCallback_cb _hidl_cb) = 0;

    /**
     * Return callback for setDebugParams
     */
    using setDebugParams_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Set debug parameters for the supplicant.
     *
     * @param level Debug logging level for the supplicant.
     *        (one of |DebugLevel| values).
     * @param timestamp Determines whether to show timestamps in logs or
     *        not.
     * @param showKeys Determines whether to show keys in debug logs or
     *        not.
     *        CAUTION: Do not set this param in production code!
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> setDebugParams(::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel level, bool showTimestamp, bool showKeys, setDebugParams_cb _hidl_cb) = 0;

    /**
     * Get the debug level set.
     *
     * @return level one of |DebugLevel| values.
     */
    virtual ::android::hardware::Return<::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel> getDebugLevel() = 0;

    /**
     * Get whether the timestamps are shown in the debug logs or not.
     *
     * @return enabled true if set, false otherwise.
     */
    virtual ::android::hardware::Return<bool> isDebugShowTimestampEnabled() = 0;

    /**
     * Get whether the keys are shown in the debug logs or not.
     *
     * @return enabled true if set, false otherwise.
     */
    virtual ::android::hardware::Return<bool> isDebugShowKeysEnabled() = 0;

    /**
     * Return callback for setConcurrencyPriority
     */
    using setConcurrencyPriority_cb = std::function<void(const ::android::hardware::wifi::supplicant::V1_0::SupplicantStatus& status)>;
    /**
     * Set concurrency priority.
     *
     * When both P2P and STA mode ifaces are active, this must be used
     * to prioritize either STA or P2P connection to resolve conflicts
     * arising during single channel concurrency.
     *
     * @param type The type of iface to prioritize.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |SupplicantStatusCode.SUCCESS|,
     *         |SupplicantStatusCode.FAILURE_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> setConcurrencyPriority(::android::hardware::wifi::supplicant::V1_0::IfaceType type, setConcurrencyPriority_cb _hidl_cb) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicant>> castFrom(const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicant>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicant>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<ISupplicant> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ISupplicant> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ISupplicant> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ISupplicant> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<ISupplicant> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ISupplicant> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ISupplicant> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ISupplicant> getService(bool getStub) { return getService("default", getStub); }
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
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::IfaceInfo& o);
static inline void PrintTo(const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::IfaceInfo& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::IfaceInfo& lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::IfaceInfo& rhs);
static inline bool operator!=(const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::IfaceInfo& lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::IfaceInfo& rhs);

static inline std::string toString(const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicant>& o);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::EXCESSIVE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::EXCESSIVE)) {
        os += (first ? "" : " | ");
        os += "EXCESSIVE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::EXCESSIVE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::MSGDUMP) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::MSGDUMP)) {
        os += (first ? "" : " | ");
        os += "MSGDUMP";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::MSGDUMP;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::DEBUG) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::DEBUG)) {
        os += (first ? "" : " | ");
        os += "DEBUG";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::DEBUG;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::INFO) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::INFO)) {
        os += (first ? "" : " | ");
        os += "INFO";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::INFO;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::WARNING) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::WARNING)) {
        os += (first ? "" : " | ");
        os += "WARNING";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::WARNING;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::ERROR) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::ERROR)) {
        os += (first ? "" : " | ");
        os += "ERROR";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::ERROR;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::EXCESSIVE) {
        return "EXCESSIVE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::MSGDUMP) {
        return "MSGDUMP";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::DEBUG) {
        return "DEBUG";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::INFO) {
        return "INFO";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::WARNING) {
        return "WARNING";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::ERROR) {
        return "ERROR";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::IfaceInfo& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::wifi::supplicant::V1_0::toString(o.type);
    os += ", .name = ";
    os += ::android::hardware::toString(o.name);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::IfaceInfo& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::IfaceInfo& lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::IfaceInfo& rhs) {
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.name != rhs.name) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::IfaceInfo& lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicant::IfaceInfo& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicant>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::wifi::supplicant::V1_0::ISupplicant::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_0
}  // namespace supplicant
}  // namespace wifi
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel, 6> hidl_enum_values<::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel> = {
    ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::EXCESSIVE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::MSGDUMP,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::DEBUG,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::INFO,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::WARNING,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicant::DebugLevel::ERROR,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_ISUPPLICANT_H
