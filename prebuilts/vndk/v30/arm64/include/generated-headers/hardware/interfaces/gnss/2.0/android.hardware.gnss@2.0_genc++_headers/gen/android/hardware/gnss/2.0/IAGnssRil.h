#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_0_IAGNSSRIL_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_0_IAGNSSRIL_H

#include <android/hardware/gnss/1.0/IAGnssRil.h>
#include <android/hardware/gnss/2.0/types.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V2_0 {

/**
 * Extended interface for AGNSS RIL support. An Assisted GNSS Radio Interface
 * Layer interface allows the GNSS chipset to request radio interface layer
 * information from Android platform. Examples of such information are reference
 * location, unique subscriber ID, phone number string and network availability changes.
 */
struct IAGnssRil : public ::android::hardware::gnss::V1_0::IAGnssRil {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.gnss@2.0::IAGnssRil"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    enum class NetworkCapability : uint16_t;
    struct NetworkAttributes;

    /**
     * Flags to indicate capabilities of the network
     */
    enum class NetworkCapability : uint16_t {
        /**
         * Network is not metered.
         */
        NOT_METERED = 1 /* 1 << 0 */,
        /**
         * Network is not roaming.
         */
        NOT_ROAMING = 2 /* 1 << 1 */,
    };

    /**
     * Represents network connection status and capabilities.
     */
    struct NetworkAttributes final {
        /**
         * Network handle of the network for use with the NDK API.
         */
        uint64_t networkHandle __attribute__ ((aligned(8)));
        /**
         * True indicates that network connectivity exists and it is possible to
         * establish connections and pass data. If false, only the networkHandle field
         * is populated to indicate that this network has just disconnected.
         */
        bool isConnected __attribute__ ((aligned(1)));
        /**
         * A set of flags indicating the capabilities of this network.
         */
        ::android::hardware::hidl_bitfield<::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability> capabilities __attribute__ ((aligned(2)));
        /**
         * Telephony preferred Access Point Name to use for carrier data connection when
         * connected to a cellular network. Empty string, otherwise.
         */
        ::android::hardware::hidl_string apn __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::gnss::V2_0::IAGnssRil::NetworkAttributes, networkHandle) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_0::IAGnssRil::NetworkAttributes, isConnected) == 8, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_0::IAGnssRil::NetworkAttributes, capabilities) == 10, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V2_0::IAGnssRil::NetworkAttributes, apn) == 16, "wrong offset");
    static_assert(sizeof(::android::hardware::gnss::V2_0::IAGnssRil::NetworkAttributes) == 32, "wrong size");
    static_assert(__alignof(::android::hardware::gnss::V2_0::IAGnssRil::NetworkAttributes) == 8, "wrong alignment");

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Opens the AGNSS interface and provides the callback routines
     * to the implementation of this interface.
     *
     * @param callback Interface for AGnssRil callbacks.
     */
    virtual ::android::hardware::Return<void> setCallback(const ::android::sp<::android::hardware::gnss::V1_0::IAGnssRilCallback>& callback) = 0;

    /**
     * Sets the reference location.
     *
     * @param agnssReflocation AGNSS reference location CellID.
     */
    virtual ::android::hardware::Return<void> setRefLocation(const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocation& agnssReflocation) = 0;

    /**
     * Sets the SET ID.
     *
     * @param type Must be populated with either IMSI or MSISDN or NONE.
     * @param setid If type is IMSI then setid is populated with
     * a string representing the unique Subscriber ID, for example, the IMSI for
     * a GMS phone. If type is MSISDN, then setid must contain
     * the phone number string for line 1. For example, the MSISDN for a GSM phone.
     * If the type is NONE, then the string must be empty.
     *
     * @return success True if all parameters were valid and operation was
     * successful.
     */
    virtual ::android::hardware::Return<bool> setSetId(::android::hardware::gnss::V1_0::IAGnssRil::SetIDType type, const ::android::hardware::hidl_string& setid) = 0;

    /**
     * Notify GNSS of network status changes.
     *
     * @param connected Indicates whether network connectivity exists and
     * it is possible to establish connections and pass data.
     * @param type Indicates the kind of network, for eg. mobile, wifi etc.
     * @param roaming Indicates whether the device is currently roaming on
     * this network.
     *
     * @return success True is all parameters were valid and operation was
     * successful.
     */
    virtual ::android::hardware::Return<bool> updateNetworkState(bool connected, ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType type, bool roaming) = 0;

    /**
     * Notify GNSS of network status changes and current APN.
     *
     * @param available Indicates whether network connectivity is available.
     * @param apn String containing the telephony preferred Access Point Name.
     *
     * @return success True if all parameters were valid and the operation was
     * successful.
     */
    virtual ::android::hardware::Return<bool> updateNetworkAvailability(bool available, const ::android::hardware::hidl_string& apn) = 0;

    /**
     * Notifies GNSS of network status changes.
     *
     * The framework calls this method to update the GNSS HAL implementation of network
     * state changes. The methods updateNetworkState() and updateNetworkAvailability
     * in @1.0::IAGnssRil are deprecated and are not called by the framework.
     *
     * @param attributes Updated network attributes.
     *
     * @return success True if all parameters were valid and the operation was
     * successful.
     */
    virtual ::android::hardware::Return<bool> updateNetworkState_2_0(const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkAttributes& attributes) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V2_0::IAGnssRil>> castFrom(const ::android::sp<::android::hardware::gnss::V2_0::IAGnssRil>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V2_0::IAGnssRil>> castFrom(const ::android::sp<::android::hardware::gnss::V1_0::IAGnssRil>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V2_0::IAGnssRil>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IAGnssRil> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IAGnssRil> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IAGnssRil> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IAGnssRil> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IAGnssRil> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IAGnssRil> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IAGnssRil> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IAGnssRil> getService(bool getStub) { return getService("default", getStub); }
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
static inline std::string toString(uint16_t o);
static inline std::string toString(::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability o);
static inline void PrintTo(::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability o, ::std::ostream* os);
constexpr uint16_t operator|(const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability lhs, const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const uint16_t lhs, const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability rhs) {
    return static_cast<uint16_t>(lhs | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | rhs);
}
constexpr uint16_t operator&(const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability lhs, const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const uint16_t lhs, const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability rhs) {
    return static_cast<uint16_t>(lhs & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & rhs);
}
constexpr uint16_t &operator|=(uint16_t& v, const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability e) {
    v |= static_cast<uint16_t>(e);
    return v;
}
constexpr uint16_t &operator&=(uint16_t& v, const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability e) {
    v &= static_cast<uint16_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkAttributes& o);
static inline void PrintTo(const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkAttributes& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkAttributes& lhs, const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkAttributes& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkAttributes& lhs, const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkAttributes& rhs);

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V2_0::IAGnssRil>& o);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability>(uint16_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability::NOT_METERED) == static_cast<uint16_t>(::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability::NOT_METERED)) {
        os += (first ? "" : " | ");
        os += "NOT_METERED";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability::NOT_METERED;
    }
    if ((o & ::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability::NOT_ROAMING) == static_cast<uint16_t>(::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability::NOT_ROAMING)) {
        os += (first ? "" : " | ");
        os += "NOT_ROAMING";
        first = false;
        flipped |= ::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability::NOT_ROAMING;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability::NOT_METERED) {
        return "NOT_METERED";
    }
    if (o == ::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability::NOT_ROAMING) {
        return "NOT_ROAMING";
    }
    std::string os;
    os += toHexString(static_cast<uint16_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkAttributes& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".networkHandle = ";
    os += ::android::hardware::toString(o.networkHandle);
    os += ", .isConnected = ";
    os += ::android::hardware::toString(o.isConnected);
    os += ", .capabilities = ";
    os += ::android::hardware::gnss::V2_0::toString<::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability>(o.capabilities);
    os += ", .apn = ";
    os += ::android::hardware::toString(o.apn);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkAttributes& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkAttributes& lhs, const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkAttributes& rhs) {
    if (lhs.networkHandle != rhs.networkHandle) {
        return false;
    }
    if (lhs.isConnected != rhs.isConnected) {
        return false;
    }
    if (lhs.capabilities != rhs.capabilities) {
        return false;
    }
    if (lhs.apn != rhs.apn) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkAttributes& lhs, const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkAttributes& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V2_0::IAGnssRil>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::gnss::V2_0::IAGnssRil::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V2_0
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
template<> inline constexpr std::array<::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability, 2> hidl_enum_values<::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability> = {
    ::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability::NOT_METERED,
    ::android::hardware::gnss::V2_0::IAGnssRil::NetworkCapability::NOT_ROAMING,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_0_IAGNSSRIL_H
