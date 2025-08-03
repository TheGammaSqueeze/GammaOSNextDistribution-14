#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_HOSTAPD_V1_1_IHOSTAPD_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_HOSTAPD_V1_1_IHOSTAPD_H

#include <android/hardware/wifi/hostapd/1.0/IHostapd.h>
#include <android/hardware/wifi/hostapd/1.0/types.h>
#include <android/hardware/wifi/hostapd/1.1/IHostapdCallback.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace wifi {
namespace hostapd {
namespace V1_1 {

/**
 * Top-level object for managing SoftAPs.
 */
struct IHostapd : public ::android::hardware::wifi::hostapd::V1_0::IHostapd {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.wifi.hostapd@1.1::IHostapd"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    struct AcsChannelRange;
    struct ChannelParams;
    struct IfaceParams;

    /**
     * Parameters to specify the channel range for ACS.
     */
    struct AcsChannelRange final {
        /**
         * Channel number (IEEE 802.11) at the start of the range.
         */
        uint32_t start __attribute__ ((aligned(4)));
        /**
         * Channel number (IEEE 802.11) at the end of the range.
         */
        uint32_t end __attribute__ ((aligned(4)));
    };

    static_assert(offsetof(::android::hardware::wifi::hostapd::V1_1::IHostapd::AcsChannelRange, start) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::hostapd::V1_1::IHostapd::AcsChannelRange, end) == 4, "wrong offset");
    static_assert(sizeof(::android::hardware::wifi::hostapd::V1_1::IHostapd::AcsChannelRange) == 8, "wrong size");
    static_assert(__alignof(::android::hardware::wifi::hostapd::V1_1::IHostapd::AcsChannelRange) == 4, "wrong alignment");

    /**
     * Parameters to control the channel selection for the interface.
     */
    struct ChannelParams final {
        /**
         * This option can be used to specify the channels selected by ACS.
         * If this is an empty list, all channels allowed in selected HW mode
         * are specified implicitly.
         * Note: channels may be overridden by firmware.
         * Note: this option is ignored if ACS is disabled.
         */
        ::android::hardware::hidl_vec<::android::hardware::wifi::hostapd::V1_1::IHostapd::AcsChannelRange> acsChannelRanges __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::wifi::hostapd::V1_1::IHostapd::ChannelParams, acsChannelRanges) == 0, "wrong offset");
    static_assert(sizeof(::android::hardware::wifi::hostapd::V1_1::IHostapd::ChannelParams) == 16, "wrong size");
    static_assert(__alignof(::android::hardware::wifi::hostapd::V1_1::IHostapd::ChannelParams) == 8, "wrong alignment");

    /**
     * Parameters to use for setting up the access point interface.
     */
    struct IfaceParams final {
        /**
         * Baseline information as defined in HAL 1.0.
         */
        ::android::hardware::wifi::hostapd::V1_0::IHostapd::IfaceParams V1_0 __attribute__ ((aligned(8)));
        /**
         * Additional Channel params for the interface
         */
        ::android::hardware::wifi::hostapd::V1_1::IHostapd::ChannelParams channelParams __attribute__ ((aligned(8)));
    };

    static_assert(offsetof(::android::hardware::wifi::hostapd::V1_1::IHostapd::IfaceParams, V1_0) == 0, "wrong offset");
    static_assert(offsetof(::android::hardware::wifi::hostapd::V1_1::IHostapd::IfaceParams, channelParams) == 32, "wrong offset");
    static_assert(sizeof(::android::hardware::wifi::hostapd::V1_1::IHostapd::IfaceParams) == 48, "wrong size");
    static_assert(__alignof(::android::hardware::wifi::hostapd::V1_1::IHostapd::IfaceParams) == 8, "wrong alignment");

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Return callback for addAccessPoint
     */
    using addAccessPoint_cb = std::function<void(const ::android::hardware::wifi::hostapd::V1_0::HostapdStatus& status)>;
    /**
     * Adds a new access point for hostapd to control.
     *
     * This should trigger the setup of an access point with the specified
     * interface and network params.
     *
     * @param ifaceParams AccessPoint Params for the access point.
     * @param nwParams Network Params for the access point.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |HostapdStatusCode.SUCCESS|,
     *         |HostapdStatusCode.FAILURE_ARGS_INVALID|,
     *         |HostapdStatusCode.FAILURE_UNKNOWN|,
     *         |HostapdStatusCode.FAILURE_IFACE_EXISTS|
     */
    virtual ::android::hardware::Return<void> addAccessPoint(const ::android::hardware::wifi::hostapd::V1_0::IHostapd::IfaceParams& ifaceParams, const ::android::hardware::wifi::hostapd::V1_0::IHostapd::NetworkParams& nwParams, addAccessPoint_cb _hidl_cb) = 0;

    /**
     * Return callback for removeAccessPoint
     */
    using removeAccessPoint_cb = std::function<void(const ::android::hardware::wifi::hostapd::V1_0::HostapdStatus& status)>;
    /**
     * Removes an existing access point from hostapd.
     *
     * This should bring down the access point previously setup on the
     * interface.
     *
     * @param ifaceName Name of the interface.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |HostapdStatusCode.SUCCESS|,
     *         |HostapdStatusCode.FAILURE_UNKNOWN|,
     *         |HostapdStatusCode.FAILURE_IFACE_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> removeAccessPoint(const ::android::hardware::hidl_string& ifaceName, removeAccessPoint_cb _hidl_cb) = 0;

    /**
     * Terminate the service.
     * This must de-register the service and clear all state. If this HAL
     * supports the lazy HAL protocol, then this may trigger daemon to exit and
     * wait to be restarted.
     */
    virtual ::android::hardware::Return<void> terminate() = 0;

    /**
     * Return callback for addAccessPoint_1_1
     */
    using addAccessPoint_1_1_cb = std::function<void(const ::android::hardware::wifi::hostapd::V1_0::HostapdStatus& status)>;
    /**
     * Adds a new access point for hostapd to control.
     *
     * This should trigger the setup of an access point with the specified
     * interface and network params.
     *
     * @param ifaceParams AccessPoint Params for the access point.
     * @param nwParams Network Params for the access point.
     * @return status Status of the operation.
     *         Possible status codes:
     *         |HostapdStatusCode.SUCCESS|,
     *         |HostapdStatusCode.FAILURE_ARGS_INVALID|,
     *         |HostapdStatusCode.FAILURE_UNKNOWN|,
     *         |HostapdStatusCode.FAILURE_IFACE_EXISTS|
     */
    virtual ::android::hardware::Return<void> addAccessPoint_1_1(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::IfaceParams& ifaceParams, const ::android::hardware::wifi::hostapd::V1_0::IHostapd::NetworkParams& nwParams, addAccessPoint_1_1_cb _hidl_cb) = 0;

    /**
     * Return callback for registerCallback
     */
    using registerCallback_cb = std::function<void(const ::android::hardware::wifi::hostapd::V1_0::HostapdStatus& status)>;
    /**
     * Register for callbacks from the hostapd service.
     *
     * These callbacks are invoked for global events that are not specific
     * to any interface or network. Registration of multiple callback
     * objects is supported. These objects must be deleted when the corresponding
     * client process is dead.
     *
     * @param callback An instance of the |IHostapdCallback| HIDL interface
     *     object.
     * @return status Status of the operation.
     *     Possible status codes:
     *     |HostapdStatusCode.SUCCESS|,
     *     |HostapdStatusCode.FAILURE_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> registerCallback(const ::android::sp<::android::hardware::wifi::hostapd::V1_1::IHostapdCallback>& callback, registerCallback_cb _hidl_cb) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::hostapd::V1_1::IHostapd>> castFrom(const ::android::sp<::android::hardware::wifi::hostapd::V1_1::IHostapd>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::hostapd::V1_1::IHostapd>> castFrom(const ::android::sp<::android::hardware::wifi::hostapd::V1_0::IHostapd>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::hostapd::V1_1::IHostapd>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IHostapd> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IHostapd> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IHostapd> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IHostapd> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IHostapd> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IHostapd> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IHostapd> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IHostapd> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::AcsChannelRange& o);
static inline void PrintTo(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::AcsChannelRange& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::AcsChannelRange& lhs, const ::android::hardware::wifi::hostapd::V1_1::IHostapd::AcsChannelRange& rhs);
static inline bool operator!=(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::AcsChannelRange& lhs, const ::android::hardware::wifi::hostapd::V1_1::IHostapd::AcsChannelRange& rhs);

static inline std::string toString(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::ChannelParams& o);
static inline void PrintTo(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::ChannelParams& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::ChannelParams& lhs, const ::android::hardware::wifi::hostapd::V1_1::IHostapd::ChannelParams& rhs);
static inline bool operator!=(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::ChannelParams& lhs, const ::android::hardware::wifi::hostapd::V1_1::IHostapd::ChannelParams& rhs);

static inline std::string toString(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::IfaceParams& o);
static inline void PrintTo(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::IfaceParams& o, ::std::ostream*);
static inline bool operator==(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::IfaceParams& lhs, const ::android::hardware::wifi::hostapd::V1_1::IHostapd::IfaceParams& rhs);
static inline bool operator!=(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::IfaceParams& lhs, const ::android::hardware::wifi::hostapd::V1_1::IHostapd::IfaceParams& rhs);

static inline std::string toString(const ::android::sp<::android::hardware::wifi::hostapd::V1_1::IHostapd>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::AcsChannelRange& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".start = ";
    os += ::android::hardware::toString(o.start);
    os += ", .end = ";
    os += ::android::hardware::toString(o.end);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::AcsChannelRange& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::AcsChannelRange& lhs, const ::android::hardware::wifi::hostapd::V1_1::IHostapd::AcsChannelRange& rhs) {
    if (lhs.start != rhs.start) {
        return false;
    }
    if (lhs.end != rhs.end) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::AcsChannelRange& lhs, const ::android::hardware::wifi::hostapd::V1_1::IHostapd::AcsChannelRange& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::ChannelParams& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".acsChannelRanges = ";
    os += ::android::hardware::toString(o.acsChannelRanges);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::ChannelParams& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::ChannelParams& lhs, const ::android::hardware::wifi::hostapd::V1_1::IHostapd::ChannelParams& rhs) {
    if (lhs.acsChannelRanges != rhs.acsChannelRanges) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::ChannelParams& lhs, const ::android::hardware::wifi::hostapd::V1_1::IHostapd::ChannelParams& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::IfaceParams& o) {
    using ::android::hardware::toString;
    std::string os;
    os += "{";
    os += ".V1_0 = ";
    os += ::android::hardware::wifi::hostapd::V1_0::toString(o.V1_0);
    os += ", .channelParams = ";
    os += ::android::hardware::wifi::hostapd::V1_1::toString(o.channelParams);
    os += "}"; return os;
}

static inline void PrintTo(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::IfaceParams& o, ::std::ostream* os) {
    *os << toString(o);
}

static inline bool operator==(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::IfaceParams& lhs, const ::android::hardware::wifi::hostapd::V1_1::IHostapd::IfaceParams& rhs) {
    if (lhs.V1_0 != rhs.V1_0) {
        return false;
    }
    if (lhs.channelParams != rhs.channelParams) {
        return false;
    }
    return true;
}

static inline bool operator!=(const ::android::hardware::wifi::hostapd::V1_1::IHostapd::IfaceParams& lhs, const ::android::hardware::wifi::hostapd::V1_1::IHostapd::IfaceParams& rhs){
    return !(lhs == rhs);
}

static inline std::string toString(const ::android::sp<::android::hardware::wifi::hostapd::V1_1::IHostapd>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::wifi::hostapd::V1_1::IHostapd::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_1
}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_HOSTAPD_V1_1_IHOSTAPD_H
