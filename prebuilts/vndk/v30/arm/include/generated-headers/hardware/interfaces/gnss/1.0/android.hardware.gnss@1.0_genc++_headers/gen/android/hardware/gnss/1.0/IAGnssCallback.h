#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_IAGNSSCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_IAGNSSCALLBACK_H

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
 * Callback structure for the AGNSS interface.
 */
struct IAGnssCallback : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.gnss@1.0::IAGnssCallback"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    enum class AGnssType : uint8_t;
    enum class AGnssStatusValue : uint8_t;
    struct AGnssStatusIpV4;
    struct AGnssStatusIpV6;

    /**
     * AGNSS type
     */
    enum class AGnssType : uint8_t {
        TYPE_SUPL = 1,
        TYPE_C2K = 2,
    };

    enum class AGnssStatusValue : uint8_t {
        /**
         * GNSS requests data connection for AGNSS.
         */
        REQUEST_AGNSS_DATA_CONN = 1,
        /**
         * GNSS releases the AGNSS data connection.
         */
        RELEASE_AGNSS_DATA_CONN = 2,
        /**
         * AGNSS data connection initiated
         */
        AGNSS_DATA_CONNECTED = 3,
        /**
         * AGNSS data connection completed
         */
        AGNSS_DATA_CONN_DONE = 4,
        /**
         * AGNSS data connection failed
         */
        AGNSS_DATA_CONN_FAILED = 5,
    };

    /**
     * Represents the status of AGNSS augmented to support IPv4.
     */
    struct AGnssStatusIpV4 final {
        ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType type __attribute__ ((aligned(1)));
        ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue status __attribute__ ((aligned(1)));
        /**
         * 32-bit IPv4 address.
         */
        uint32_t ipV4Addr __attribute__ ((aligned(4)));
    };

    static_assert(offsetof(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV4, type) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV4, status) == 1, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV4, ipV4Addr) == 4, "wrong offset");
    static_assert(sizeof(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV4) == 8, "wrong size");
    static_assert(__alignof(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV4) == 4, "wrong alignment");

    /**
     * Represents the status of AGNSS augmented to support IPv6.
     */
    struct AGnssStatusIpV6 final {
        ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType type __attribute__ ((aligned(1)));
        ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue status __attribute__ ((aligned(1)));
        /**
         * 128-bit IPv6 address.
         */
        ::android::hardware::hidl_array<uint8_t, 16> ipV6Addr __attribute__ ((aligned(1)));
    };

    static_assert(offsetof(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV6, type) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV6, status) == 1, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV6, ipV6Addr) == 2, "wrong offset");
    static_assert(sizeof(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV6) == 18, "wrong size");
    static_assert(__alignof(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV6) == 1, "wrong alignment");

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Callback with AGNSS(IpV4) status information.
     *
     * @param status Will be of type AGnssStatusIpV4.
     */
    virtual ::android::hardware::Return<void> agnssStatusIpV4Cb(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV4& status) = 0;

    /**
     * Callback with AGNSS(IpV6) status information.
     *
     * @param status Will be of type AGnssStatusIpV6.
     */
    virtual ::android::hardware::Return<void> agnssStatusIpV6Cb(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV6& status) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IAGnssCallback>> castFrom(const ::android::sp<::android::hardware::gnss::V1_0::IAGnssCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IAGnssCallback>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IAGnssCallback> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IAGnssCallback> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IAGnssCallback> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IAGnssCallback> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IAGnssCallback> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IAGnssCallback> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IAGnssCallback> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IAGnssCallback> getService(bool getStub) { return getService("default", getStub); }
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
static inline std::string toString(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType o);
static inline void PrintTo(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType lhs, const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType lhs, const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue o);
static inline void PrintTo(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue lhs, const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue lhs, const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV4& o);
static inline void PrintTo(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV4& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV4& lhs, const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV4& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV4& lhs, const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV4& rhs);

static inline std::string toString(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV6& o);
static inline void PrintTo(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV6& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV6& lhs, const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV6& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV6& lhs, const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV6& rhs);

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V1_0::IAGnssCallback>& o);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType::TYPE_SUPL) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType::TYPE_SUPL)) {
        os += (first ? "" : " | ");
        os += "TYPE_SUPL";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType::TYPE_SUPL;
    }
    if ((o & ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType::TYPE_C2K) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType::TYPE_C2K)) {
        os += (first ? "" : " | ");
        os += "TYPE_C2K";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType::TYPE_C2K;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType::TYPE_SUPL) {
        return "TYPE_SUPL";
    }
    if (o == ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType::TYPE_C2K) {
        return "TYPE_C2K";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::REQUEST_AGNSS_DATA_CONN) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::REQUEST_AGNSS_DATA_CONN)) {
        os += (first ? "" : " | ");
        os += "REQUEST_AGNSS_DATA_CONN";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::REQUEST_AGNSS_DATA_CONN;
    }
    if ((o & ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::RELEASE_AGNSS_DATA_CONN) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::RELEASE_AGNSS_DATA_CONN)) {
        os += (first ? "" : " | ");
        os += "RELEASE_AGNSS_DATA_CONN";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::RELEASE_AGNSS_DATA_CONN;
    }
    if ((o & ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::AGNSS_DATA_CONNECTED) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::AGNSS_DATA_CONNECTED)) {
        os += (first ? "" : " | ");
        os += "AGNSS_DATA_CONNECTED";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::AGNSS_DATA_CONNECTED;
    }
    if ((o & ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::AGNSS_DATA_CONN_DONE) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::AGNSS_DATA_CONN_DONE)) {
        os += (first ? "" : " | ");
        os += "AGNSS_DATA_CONN_DONE";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::AGNSS_DATA_CONN_DONE;
    }
    if ((o & ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::AGNSS_DATA_CONN_FAILED) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::AGNSS_DATA_CONN_FAILED)) {
        os += (first ? "" : " | ");
        os += "AGNSS_DATA_CONN_FAILED";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::AGNSS_DATA_CONN_FAILED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::REQUEST_AGNSS_DATA_CONN) {
        return "REQUEST_AGNSS_DATA_CONN";
    }
    if (o == ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::RELEASE_AGNSS_DATA_CONN) {
        return "RELEASE_AGNSS_DATA_CONN";
    }
    if (o == ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::AGNSS_DATA_CONNECTED) {
        return "AGNSS_DATA_CONNECTED";
    }
    if (o == ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::AGNSS_DATA_CONN_DONE) {
        return "AGNSS_DATA_CONN_DONE";
    }
    if (o == ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::AGNSS_DATA_CONN_FAILED) {
        return "AGNSS_DATA_CONN_FAILED";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV4& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::gnss::V1_0::toString(o.type);
    os += ", .status = ";
    os += ::android::hardware::gnss::V1_0::toString(o.status);
    os += ", .ipV4Addr = ";
    os += ::android::hardware::toString(o.ipV4Addr);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV4& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV4& lhs, const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV4& rhs) {
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.status != rhs.status) {
        return false;
    }
    if (lhs.ipV4Addr != rhs.ipV4Addr) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV4& lhs, const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV4& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV6& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::gnss::V1_0::toString(o.type);
    os += ", .status = ";
    os += ::android::hardware::gnss::V1_0::toString(o.status);
    os += ", .ipV6Addr = ";
    os += ::android::hardware::toString(o.ipV6Addr);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV6& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV6& lhs, const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV6& rhs) {
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.status != rhs.status) {
        return false;
    }
    if (lhs.ipV6Addr != rhs.ipV6Addr) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV6& lhs, const ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusIpV6& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V1_0::IAGnssCallback>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::gnss::V1_0::IAGnssCallback::descriptor;
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
template<> inline constexpr std::array<::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType, 2> hidl_enum_values<::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType> = {
    ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType::TYPE_SUPL,
    ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssType::TYPE_C2K,
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
template<> inline constexpr std::array<::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue, 5> hidl_enum_values<::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue> = {
    ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::REQUEST_AGNSS_DATA_CONN,
    ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::RELEASE_AGNSS_DATA_CONN,
    ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::AGNSS_DATA_CONNECTED,
    ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::AGNSS_DATA_CONN_DONE,
    ::android::hardware::gnss::V1_0::IAGnssCallback::AGnssStatusValue::AGNSS_DATA_CONN_FAILED,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_IAGNSSCALLBACK_H
