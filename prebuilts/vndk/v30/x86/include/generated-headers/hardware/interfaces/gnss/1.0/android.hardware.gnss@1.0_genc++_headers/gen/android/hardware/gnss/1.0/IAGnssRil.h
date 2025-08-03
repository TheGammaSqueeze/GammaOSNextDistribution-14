#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_IAGNSSRIL_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_IAGNSSRIL_H

#include <android/hardware/gnss/1.0/IAGnssRilCallback.h>
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
 * Extended interface for AGNSS RIL support. An Assisted GNSS Radio Interface
 * Layer interface allows the GNSS chipset to request radio interface layer
 * information from Android platform. Examples of such information are reference
 * location, unique subscriber ID, phone number string and network availability changes.
 */
struct IAGnssRil : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.gnss@1.0::IAGnssRil"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    enum class SetIDType : uint8_t;
    enum class NetworkType : uint8_t;
    enum class AGnssRefLocationType : uint8_t;
    struct AGnssRefLocationCellID;
    struct AGnssRefLocation;

    enum class SetIDType : uint8_t {
        NONE = 0,
        IMSI = 1,
        MSISDM = 2,
    };

    enum class NetworkType : uint8_t {
        MOBILE = 0,
        WIFI = 1,
        MMS = 2,
        SUPL = 3,
        DUN = 4,
        HIPRI = 5,
        WIMAX = 6,
    };

    enum class AGnssRefLocationType : uint8_t {
        GSM_CELLID = 1,
        UMTS_CELLID = 2,
        LTE_CELLID = 4,
    };

    /**
     * CellID for 2G, 3G and LTE, used in AGNSS.
     */
    struct AGnssRefLocationCellID final {
        ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType type __attribute__ ((aligned(1)));
        /**
         * Mobile Country Code.
         */
        uint16_t mcc __attribute__ ((aligned(2)));
        /**
         * Mobile Network Code .
         */
        uint16_t mnc __attribute__ ((aligned(2)));
        /**
         * Location Area Code in 2G, 3G and LTE. In 3G lac is discarded. In LTE,
         * lac is populated with tac, to ensure that we don't break old clients that
         * might rely in the old (wrong) behavior.
         */
        uint16_t lac __attribute__ ((aligned(2)));
        /**
         * Cell id in 2G. Utran Cell id in 3G. Cell Global Id EUTRA in LTE.
         */
        uint32_t cid __attribute__ ((aligned(4)));
        /**
         * Tracking Area Code in LTE.
         */
        uint16_t tac __attribute__ ((aligned(2)));
        /**
         * Physical Cell id in LTE (not used in 2G and 3G)
         */
        uint16_t pcid __attribute__ ((aligned(2)));
    };

    static_assert(offsetof(::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationCellID, type) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationCellID, mcc) == 2, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationCellID, mnc) == 4, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationCellID, lac) == 6, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationCellID, cid) == 8, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationCellID, tac) == 12, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationCellID, pcid) == 14, "wrong offset");
    static_assert(sizeof(::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationCellID) == 16, "wrong size");
    static_assert(__alignof(::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationCellID) == 4, "wrong alignment");

    /**
     * Represents ref locations
     */
    struct AGnssRefLocation final {
        ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType type __attribute__ ((aligned(1)));
        ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationCellID cellID __attribute__ ((aligned(4)));
    };

    static_assert(offsetof(::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocation, type) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocation, cellID) == 4, "wrong offset");
    static_assert(sizeof(::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocation) == 20, "wrong size");
    static_assert(__alignof(::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocation) == 4, "wrong alignment");

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
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IAGnssRil>> castFrom(const ::android::sp<::android::hardware::gnss::V1_0::IAGnssRil>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IAGnssRil>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::gnss::V1_0::IAGnssRil::SetIDType o);
static inline void PrintTo(::android::hardware::gnss::V1_0::IAGnssRil::SetIDType o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::IAGnssRil::SetIDType lhs, const ::android::hardware::gnss::V1_0::IAGnssRil::SetIDType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::gnss::V1_0::IAGnssRil::SetIDType rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::IAGnssRil::SetIDType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::IAGnssRil::SetIDType lhs, const ::android::hardware::gnss::V1_0::IAGnssRil::SetIDType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::gnss::V1_0::IAGnssRil::SetIDType rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::IAGnssRil::SetIDType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::gnss::V1_0::IAGnssRil::SetIDType e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::gnss::V1_0::IAGnssRil::SetIDType e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::gnss::V1_0::IAGnssRil::NetworkType o);
static inline void PrintTo(::android::hardware::gnss::V1_0::IAGnssRil::NetworkType o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType lhs, const ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType lhs, const ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType o);
static inline void PrintTo(::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType lhs, const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType lhs, const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationCellID& o);
static inline void PrintTo(const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationCellID& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationCellID& lhs, const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationCellID& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationCellID& lhs, const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationCellID& rhs);

static inline std::string toString(const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocation& o);
static inline void PrintTo(const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocation& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocation& lhs, const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocation& rhs);
static inline bool operator!=(const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocation& lhs, const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocation& rhs);

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V1_0::IAGnssRil>& o);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::gnss::V1_0::IAGnssRil::SetIDType>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IAGnssRil::SetIDType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V1_0::IAGnssRil::SetIDType::NONE) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IAGnssRil::SetIDType::NONE)) {
        os += (first ? "" : " | ");
        os += "NONE";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IAGnssRil::SetIDType::NONE;
    }
    if ((o & ::android::hardware::gnss::V1_0::IAGnssRil::SetIDType::IMSI) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IAGnssRil::SetIDType::IMSI)) {
        os += (first ? "" : " | ");
        os += "IMSI";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IAGnssRil::SetIDType::IMSI;
    }
    if ((o & ::android::hardware::gnss::V1_0::IAGnssRil::SetIDType::MSISDM) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IAGnssRil::SetIDType::MSISDM)) {
        os += (first ? "" : " | ");
        os += "MSISDM";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IAGnssRil::SetIDType::MSISDM;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V1_0::IAGnssRil::SetIDType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V1_0::IAGnssRil::SetIDType::NONE) {
        return "NONE";
    }
    if (o == ::android::hardware::gnss::V1_0::IAGnssRil::SetIDType::IMSI) {
        return "IMSI";
    }
    if (o == ::android::hardware::gnss::V1_0::IAGnssRil::SetIDType::MSISDM) {
        return "MSISDM";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V1_0::IAGnssRil::SetIDType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::gnss::V1_0::IAGnssRil::NetworkType>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IAGnssRil::NetworkType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::MOBILE) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::MOBILE)) {
        os += (first ? "" : " | ");
        os += "MOBILE";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::MOBILE;
    }
    if ((o & ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::WIFI) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::WIFI)) {
        os += (first ? "" : " | ");
        os += "WIFI";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::WIFI;
    }
    if ((o & ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::MMS) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::MMS)) {
        os += (first ? "" : " | ");
        os += "MMS";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::MMS;
    }
    if ((o & ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::SUPL) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::SUPL)) {
        os += (first ? "" : " | ");
        os += "SUPL";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::SUPL;
    }
    if ((o & ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::DUN) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::DUN)) {
        os += (first ? "" : " | ");
        os += "DUN";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::DUN;
    }
    if ((o & ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::HIPRI) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::HIPRI)) {
        os += (first ? "" : " | ");
        os += "HIPRI";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::HIPRI;
    }
    if ((o & ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::WIMAX) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::WIMAX)) {
        os += (first ? "" : " | ");
        os += "WIMAX";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::WIMAX;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V1_0::IAGnssRil::NetworkType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::MOBILE) {
        return "MOBILE";
    }
    if (o == ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::WIFI) {
        return "WIFI";
    }
    if (o == ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::MMS) {
        return "MMS";
    }
    if (o == ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::SUPL) {
        return "SUPL";
    }
    if (o == ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::DUN) {
        return "DUN";
    }
    if (o == ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::HIPRI) {
        return "HIPRI";
    }
    if (o == ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::WIMAX) {
        return "WIMAX";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V1_0::IAGnssRil::NetworkType o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType::GSM_CELLID) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType::GSM_CELLID)) {
        os += (first ? "" : " | ");
        os += "GSM_CELLID";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType::GSM_CELLID;
    }
    if ((o & ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType::UMTS_CELLID) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType::UMTS_CELLID)) {
        os += (first ? "" : " | ");
        os += "UMTS_CELLID";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType::UMTS_CELLID;
    }
    if ((o & ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType::LTE_CELLID) == static_cast<uint8_t>(::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType::LTE_CELLID)) {
        os += (first ? "" : " | ");
        os += "LTE_CELLID";
        first = false;
        flipped |= ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType::LTE_CELLID;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType::GSM_CELLID) {
        return "GSM_CELLID";
    }
    if (o == ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType::UMTS_CELLID) {
        return "UMTS_CELLID";
    }
    if (o == ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType::LTE_CELLID) {
        return "LTE_CELLID";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationCellID& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::gnss::V1_0::toString(o.type);
    os += ", .mcc = ";
    os += ::android::hardware::toString(o.mcc);
    os += ", .mnc = ";
    os += ::android::hardware::toString(o.mnc);
    os += ", .lac = ";
    os += ::android::hardware::toString(o.lac);
    os += ", .cid = ";
    os += ::android::hardware::toString(o.cid);
    os += ", .tac = ";
    os += ::android::hardware::toString(o.tac);
    os += ", .pcid = ";
    os += ::android::hardware::toString(o.pcid);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationCellID& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationCellID& lhs, const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationCellID& rhs) {
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.mcc != rhs.mcc) {
        return false;
    }
    if (lhs.mnc != rhs.mnc) {
        return false;
    }
    if (lhs.lac != rhs.lac) {
        return false;
    }
    if (lhs.cid != rhs.cid) {
        return false;
    }
    if (lhs.tac != rhs.tac) {
        return false;
    }
    if (lhs.pcid != rhs.pcid) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationCellID& lhs, const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationCellID& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocation& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".type = ";
    os += ::android::hardware::gnss::V1_0::toString(o.type);
    os += ", .cellID = ";
    os += ::android::hardware::gnss::V1_0::toString(o.cellID);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocation& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocation& lhs, const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocation& rhs) {
    if (lhs.type != rhs.type) {
        return false;
    }
    if (lhs.cellID != rhs.cellID) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocation& lhs, const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocation& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::sp<::android::hardware::gnss::V1_0::IAGnssRil>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::gnss::V1_0::IAGnssRil::descriptor;
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
template<> inline constexpr std::array<::android::hardware::gnss::V1_0::IAGnssRil::SetIDType, 3> hidl_enum_values<::android::hardware::gnss::V1_0::IAGnssRil::SetIDType> = {
    ::android::hardware::gnss::V1_0::IAGnssRil::SetIDType::NONE,
    ::android::hardware::gnss::V1_0::IAGnssRil::SetIDType::IMSI,
    ::android::hardware::gnss::V1_0::IAGnssRil::SetIDType::MSISDM,
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
template<> inline constexpr std::array<::android::hardware::gnss::V1_0::IAGnssRil::NetworkType, 7> hidl_enum_values<::android::hardware::gnss::V1_0::IAGnssRil::NetworkType> = {
    ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::MOBILE,
    ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::WIFI,
    ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::MMS,
    ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::SUPL,
    ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::DUN,
    ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::HIPRI,
    ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType::WIMAX,
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
template<> inline constexpr std::array<::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType, 3> hidl_enum_values<::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType> = {
    ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType::GSM_CELLID,
    ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType::UMTS_CELLID,
    ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocationType::LTE_CELLID,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_IAGNSSRIL_H
