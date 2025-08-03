#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_ISUPPLICANTP2PIFACECALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_ISUPPLICANTP2PIFACECALLBACK_H

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
 * Callback Interface exposed by the supplicant service
 * for each P2P mode interface (ISupplicantP2pIface).
 *
 * Clients need to host an instance of this HIDL interface object and
 * pass a reference of the object to the supplicant via the
 * corresponding |ISupplicantP2pIface.registerCallback| method.
 */
struct ISupplicantP2pIfaceCallback : public ::android::hidl::base::V1_0::IBase {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.wifi.supplicant@1.0::ISupplicantP2pIfaceCallback"
     */
    static const char* descriptor;

    // Forward declaration for forward reference support:
    enum class WpsDevPasswordId : uint16_t;
    enum class P2pStatusCode : uint32_t;
    enum class P2pProvDiscStatusCode : uint8_t;

    /**
     * WPS Device Password ID
     */
    enum class WpsDevPasswordId : uint16_t {
        DEFAULT = 0 /* 0x0000 */,
        USER_SPECIFIED = 1 /* 0x0001 */,
        MACHINE_SPECIFIED = 2 /* 0x0002 */,
        REKEY = 3 /* 0x0003 */,
        PUSHBUTTON = 4 /* 0x0004 */,
        REGISTRAR_SPECIFIED = 5 /* 0x0005 */,
        NFC_CONNECTION_HANDOVER = 7 /* 0x0007 */,
        P2PS_DEFAULT = 8 /* 0x0008 */,
    };

    /**
     * Status codes for P2P operations.
     */
    enum class P2pStatusCode : uint32_t {
        SUCCESS = 0u,
        FAIL_INFO_CURRENTLY_UNAVAILABLE = 1u,
        FAIL_INCOMPATIBLE_PARAMS = 2u,
        FAIL_LIMIT_REACHED = 3u,
        FAIL_INVALID_PARAMS = 4u,
        FAIL_UNABLE_TO_ACCOMMODATE = 5u,
        FAIL_PREV_PROTOCOL_ERROR = 6u,
        FAIL_NO_COMMON_CHANNELS = 7u,
        FAIL_UNKNOWN_GROUP = 8u,
        FAIL_BOTH_GO_INTENT_15 = 9u,
        FAIL_INCOMPATIBLE_PROV_METHOD = 10u,
        FAIL_REJECTED_BY_USER = 11u,
        SUCCESS_DEFERRED = 12u,
    };

    /**
     * Status codes for P2P discovery.
     */
    enum class P2pProvDiscStatusCode : uint8_t {
        SUCCESS = 0,
        TIMEOUT = 1,
        REJECTED = 2,
        TIMEOUT_JOIN = 3,
        INFO_UNAVAILABLE = 4,
    };

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Used to indicate that a new network has been added.
     *
     * @param id Network ID allocated to the corresponding network.
     */
    virtual ::android::hardware::Return<void> onNetworkAdded(uint32_t id) = 0;

    /**
     * Used to indicate that a network has been removed.
     *
     * @param id Network ID allocated to the corresponding network.
     */
    virtual ::android::hardware::Return<void> onNetworkRemoved(uint32_t id) = 0;

    /**
     * Used to indicate that a P2P device has been found.
     *
     * @param srcAddress MAC address of the device found. This must either
     *        be the P2P device address or the P2P interface address.
     * @param p2pDeviceAddress P2P device address.
     * @param primaryDeviceType Type of device. Refer to section B.1 of Wifi P2P
     *        Technical specification v1.2.
     * @param deviceName Name of the device.
     * @param configMethods Mask of WPS configuration methods supported by the
     *        device.
     * @param deviceCapabilities Refer to section 4.1.4 of Wifi P2P Technical
     *        specification v1.2.
     * @param groupCapabilites Refer to section 4.1.4 of Wifi P2P Technical
     *        specification v1.2.
     * @param wfdDeviceInfo WFD device info as described in section 5.1.2 of WFD
     *        technical specification v1.0.0.
     */
    virtual ::android::hardware::Return<void> onDeviceFound(const ::android::hardware::hidl_array<uint8_t, 6>& srcAddress, const ::android::hardware::hidl_array<uint8_t, 6>& p2pDeviceAddress, const ::android::hardware::hidl_array<uint8_t, 8>& primaryDeviceType, const ::android::hardware::hidl_string& deviceName, ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods> configMethods, uint8_t deviceCapabilities, ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask> groupCapabilities, const ::android::hardware::hidl_array<uint8_t, 6>& wfdDeviceInfo) = 0;

    /**
     * Used to indicate that a P2P device has been lost.
     *
     * @param p2pDeviceAddress P2P device address.
     */
    virtual ::android::hardware::Return<void> onDeviceLost(const ::android::hardware::hidl_array<uint8_t, 6>& p2pDeviceAddress) = 0;

    /**
     * Used to indicate the termination of P2P find operation.
     */
    virtual ::android::hardware::Return<void> onFindStopped() = 0;

    /**
     * Used to indicate the reception of a P2P Group Owner negotiation request.
     *
     * @param srcAddress MAC address of the device that initiated the GO
     *        negotiation request.
     * @param passwordId Type of password.
     */
    virtual ::android::hardware::Return<void> onGoNegotiationRequest(const ::android::hardware::hidl_array<uint8_t, 6>& srcAddress, ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId passwordId) = 0;

    /**
     * Used to indicate the completion of a P2P Group Owner negotiation request.
     *
     * @param status Status of the GO negotiation.
     */
    virtual ::android::hardware::Return<void> onGoNegotiationCompleted(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode status) = 0;

    /**
     * Used to indicate a successful formation of a P2P group.
     */
    virtual ::android::hardware::Return<void> onGroupFormationSuccess() = 0;

    /**
     * Used to indicate a failure to form a P2P group.
     *
     * @param failureReason Failure reason string for debug purposes.
     */
    virtual ::android::hardware::Return<void> onGroupFormationFailure(const ::android::hardware::hidl_string& failureReason) = 0;

    /**
     * Used to indicate the start of a P2P group.
     *
     * @param groupIfName Interface name of the group. (For ex: p2p-p2p0-1)
     * @param isGo Whether this device is owner of the group.
     * @param ssid SSID of the group.
     * @param frequency Frequency on which this group is created.
     * @param psk PSK used to secure the group.
     * @param passphrase PSK passphrase used to secure the group.
     * @param goDeviceAddress MAC Address of the owner of this group.
     * @param isPersistent Whether this group is persisted or not.
     */
    virtual ::android::hardware::Return<void> onGroupStarted(const ::android::hardware::hidl_string& groupIfname, bool isGo, const ::android::hardware::hidl_vec<uint8_t>& ssid, uint32_t frequency, const ::android::hardware::hidl_array<uint8_t, 32>& psk, const ::android::hardware::hidl_string& passphrase, const ::android::hardware::hidl_array<uint8_t, 6>& goDeviceAddress, bool isPersistent) = 0;

    /**
     * Used to indicate the removal of a P2P group.
     *
     * @param groupIfName Interface name of the group. (For ex: p2p-p2p0-1)
     * @param isGo Whether this device is owner of the group.
     */
    virtual ::android::hardware::Return<void> onGroupRemoved(const ::android::hardware::hidl_string& groupIfname, bool isGo) = 0;

    /**
     * Used to indicate the reception of a P2P invitation.
     *
     * @param srcAddress MAC address of the device that sent the invitation.
     * @param goDeviceAddress MAC Address of the owner of this group.
     * @param Bssid Bssid of the group.
     * @param persistentNetworkId Persistent network Id of the group.
     * @param operatingFrequency Frequency on which the invitation was received.
     */
    virtual ::android::hardware::Return<void> onInvitationReceived(const ::android::hardware::hidl_array<uint8_t, 6>& srcAddress, const ::android::hardware::hidl_array<uint8_t, 6>& goDeviceAddress, const ::android::hardware::hidl_array<uint8_t, 6>& bssid, uint32_t persistentNetworkId, uint32_t operatingFrequency) = 0;

    /**
     * Used to indicate the result of the P2P invitation request.
     *
     * @param Bssid Bssid of the group.
     * @param status Status of the invitation.
     */
    virtual ::android::hardware::Return<void> onInvitationResult(const ::android::hardware::hidl_array<uint8_t, 6>& bssid, ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode status) = 0;

    /**
     * Used to indicate the completion of a P2P provision discovery request.
     *
     * @param p2pDeviceAddress P2P device address.
     * @param isRequest Whether we received or sent the provision discovery.
     * @param status Status of the provision discovery.
     * @param configMethods Mask of WPS configuration methods supported.
     * @param generatedPin 8 digit pin generated.
     */
    virtual ::android::hardware::Return<void> onProvisionDiscoveryCompleted(const ::android::hardware::hidl_array<uint8_t, 6>& p2pDeviceAddress, bool isRequest, ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode status, ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods> configMethods, const ::android::hardware::hidl_string& generatedPin) = 0;

    /**
     * Used to indicate the reception of a P2P service discovery response.
     *
     * @param srcAddress MAC address of the device that sent the service discovery.
     * @param updateIndicator Service update indicator. Refer to section 3.1.3 of
     *        Wifi P2P Technical specification v1.2.
     * @parm tlvs Refer to section 3.1.3.1 of Wifi P2P Technical specification v1.2.
     */
    virtual ::android::hardware::Return<void> onServiceDiscoveryResponse(const ::android::hardware::hidl_array<uint8_t, 6>& srcAddress, uint16_t updateIndicator, const ::android::hardware::hidl_vec<uint8_t>& tlvs) = 0;

    /**
     * Used to indicate when a STA device is connected to this device.
     *
     * @param srcAddress MAC address of the device that was authorized.
     * @param p2pDeviceAddress P2P device address.
     */
    virtual ::android::hardware::Return<void> onStaAuthorized(const ::android::hardware::hidl_array<uint8_t, 6>& srcAddress, const ::android::hardware::hidl_array<uint8_t, 6>& p2pDeviceAddress) = 0;

    /**
     * Used to indicate when a STA device is disconnected from this device.
     *
     * @param srcAddress MAC address of the device that was deauthorized.
     * @param p2pDeviceAddress P2P device address.
     */
    virtual ::android::hardware::Return<void> onStaDeauthorized(const ::android::hardware::hidl_array<uint8_t, 6>& srcAddress, const ::android::hardware::hidl_array<uint8_t, 6>& p2pDeviceAddress) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback>> castFrom(const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<ISupplicantP2pIfaceCallback> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ISupplicantP2pIfaceCallback> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<ISupplicantP2pIfaceCallback> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ISupplicantP2pIfaceCallback> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<ISupplicantP2pIfaceCallback> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ISupplicantP2pIfaceCallback> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<ISupplicantP2pIfaceCallback> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<ISupplicantP2pIfaceCallback> getService(bool getStub) { return getService("default", getStub); }
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
static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId o, ::std::ostream* os);
constexpr uint16_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const uint16_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId rhs) {
    return static_cast<uint16_t>(lhs | static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) | rhs);
}
constexpr uint16_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const uint16_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId rhs) {
    return static_cast<uint16_t>(lhs & static_cast<uint16_t>(rhs));
}
constexpr uint16_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId lhs, const uint16_t rhs) {
    return static_cast<uint16_t>(static_cast<uint16_t>(lhs) & rhs);
}
constexpr uint16_t &operator|=(uint16_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId e) {
    v |= static_cast<uint16_t>(e);
    return v;
}
constexpr uint16_t &operator&=(uint16_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId e) {
    v &= static_cast<uint16_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint32_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode o, ::std::ostream* os);
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode rhs) {
    return static_cast<uint32_t>(lhs | static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) | rhs);
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const uint32_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode rhs) {
    return static_cast<uint32_t>(lhs & static_cast<uint32_t>(rhs));
}
constexpr uint32_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode lhs, const uint32_t rhs) {
    return static_cast<uint32_t>(static_cast<uint32_t>(lhs) & rhs);
}
constexpr uint32_t &operator|=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode e) {
    v |= static_cast<uint32_t>(e);
    return v;
}
constexpr uint32_t &operator&=(uint32_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode e) {
    v &= static_cast<uint32_t>(e);
    return v;
}

template<typename>
static inline std::string toString(uint8_t o);
static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode o);
static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode o, ::std::ostream* os);
constexpr uint8_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const uint8_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode rhs) {
    return static_cast<uint8_t>(lhs | static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator|(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) | rhs);
}
constexpr uint8_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const uint8_t lhs, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode rhs) {
    return static_cast<uint8_t>(lhs & static_cast<uint8_t>(rhs));
}
constexpr uint8_t operator&(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode lhs, const uint8_t rhs) {
    return static_cast<uint8_t>(static_cast<uint8_t>(lhs) & rhs);
}
constexpr uint8_t &operator|=(uint8_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode e) {
    v |= static_cast<uint8_t>(e);
    return v;
}
constexpr uint8_t &operator&=(uint8_t& v, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode e) {
    v &= static_cast<uint8_t>(e);
    return v;
}

static inline std::string toString(const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback>& o);

//
// type header definitions for package
//

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId>(uint16_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::DEFAULT) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::DEFAULT)) {
        os += (first ? "" : " | ");
        os += "DEFAULT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::DEFAULT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::USER_SPECIFIED) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::USER_SPECIFIED)) {
        os += (first ? "" : " | ");
        os += "USER_SPECIFIED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::USER_SPECIFIED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::MACHINE_SPECIFIED) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::MACHINE_SPECIFIED)) {
        os += (first ? "" : " | ");
        os += "MACHINE_SPECIFIED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::MACHINE_SPECIFIED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::REKEY) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::REKEY)) {
        os += (first ? "" : " | ");
        os += "REKEY";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::REKEY;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::PUSHBUTTON) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::PUSHBUTTON)) {
        os += (first ? "" : " | ");
        os += "PUSHBUTTON";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::PUSHBUTTON;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::REGISTRAR_SPECIFIED) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::REGISTRAR_SPECIFIED)) {
        os += (first ? "" : " | ");
        os += "REGISTRAR_SPECIFIED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::REGISTRAR_SPECIFIED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::NFC_CONNECTION_HANDOVER) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::NFC_CONNECTION_HANDOVER)) {
        os += (first ? "" : " | ");
        os += "NFC_CONNECTION_HANDOVER";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::NFC_CONNECTION_HANDOVER;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::P2PS_DEFAULT) == static_cast<uint16_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::P2PS_DEFAULT)) {
        os += (first ? "" : " | ");
        os += "P2PS_DEFAULT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::P2PS_DEFAULT;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::DEFAULT) {
        return "DEFAULT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::USER_SPECIFIED) {
        return "USER_SPECIFIED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::MACHINE_SPECIFIED) {
        return "MACHINE_SPECIFIED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::REKEY) {
        return "REKEY";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::PUSHBUTTON) {
        return "PUSHBUTTON";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::REGISTRAR_SPECIFIED) {
        return "REGISTRAR_SPECIFIED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::NFC_CONNECTION_HANDOVER) {
        return "NFC_CONNECTION_HANDOVER";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::P2PS_DEFAULT) {
        return "P2PS_DEFAULT";
    }
    std::string os;
    os += toHexString(static_cast<uint16_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode>(uint32_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::SUCCESS) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::SUCCESS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_INFO_CURRENTLY_UNAVAILABLE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_INFO_CURRENTLY_UNAVAILABLE)) {
        os += (first ? "" : " | ");
        os += "FAIL_INFO_CURRENTLY_UNAVAILABLE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_INFO_CURRENTLY_UNAVAILABLE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_INCOMPATIBLE_PARAMS) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_INCOMPATIBLE_PARAMS)) {
        os += (first ? "" : " | ");
        os += "FAIL_INCOMPATIBLE_PARAMS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_INCOMPATIBLE_PARAMS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_LIMIT_REACHED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_LIMIT_REACHED)) {
        os += (first ? "" : " | ");
        os += "FAIL_LIMIT_REACHED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_LIMIT_REACHED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_INVALID_PARAMS) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_INVALID_PARAMS)) {
        os += (first ? "" : " | ");
        os += "FAIL_INVALID_PARAMS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_INVALID_PARAMS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_UNABLE_TO_ACCOMMODATE) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_UNABLE_TO_ACCOMMODATE)) {
        os += (first ? "" : " | ");
        os += "FAIL_UNABLE_TO_ACCOMMODATE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_UNABLE_TO_ACCOMMODATE;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_PREV_PROTOCOL_ERROR) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_PREV_PROTOCOL_ERROR)) {
        os += (first ? "" : " | ");
        os += "FAIL_PREV_PROTOCOL_ERROR";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_PREV_PROTOCOL_ERROR;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_NO_COMMON_CHANNELS) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_NO_COMMON_CHANNELS)) {
        os += (first ? "" : " | ");
        os += "FAIL_NO_COMMON_CHANNELS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_NO_COMMON_CHANNELS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_UNKNOWN_GROUP) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_UNKNOWN_GROUP)) {
        os += (first ? "" : " | ");
        os += "FAIL_UNKNOWN_GROUP";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_UNKNOWN_GROUP;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_BOTH_GO_INTENT_15) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_BOTH_GO_INTENT_15)) {
        os += (first ? "" : " | ");
        os += "FAIL_BOTH_GO_INTENT_15";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_BOTH_GO_INTENT_15;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_INCOMPATIBLE_PROV_METHOD) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_INCOMPATIBLE_PROV_METHOD)) {
        os += (first ? "" : " | ");
        os += "FAIL_INCOMPATIBLE_PROV_METHOD";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_INCOMPATIBLE_PROV_METHOD;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_REJECTED_BY_USER) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_REJECTED_BY_USER)) {
        os += (first ? "" : " | ");
        os += "FAIL_REJECTED_BY_USER";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_REJECTED_BY_USER;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::SUCCESS_DEFERRED) == static_cast<uint32_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::SUCCESS_DEFERRED)) {
        os += (first ? "" : " | ");
        os += "SUCCESS_DEFERRED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::SUCCESS_DEFERRED;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_INFO_CURRENTLY_UNAVAILABLE) {
        return "FAIL_INFO_CURRENTLY_UNAVAILABLE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_INCOMPATIBLE_PARAMS) {
        return "FAIL_INCOMPATIBLE_PARAMS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_LIMIT_REACHED) {
        return "FAIL_LIMIT_REACHED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_INVALID_PARAMS) {
        return "FAIL_INVALID_PARAMS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_UNABLE_TO_ACCOMMODATE) {
        return "FAIL_UNABLE_TO_ACCOMMODATE";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_PREV_PROTOCOL_ERROR) {
        return "FAIL_PREV_PROTOCOL_ERROR";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_NO_COMMON_CHANNELS) {
        return "FAIL_NO_COMMON_CHANNELS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_UNKNOWN_GROUP) {
        return "FAIL_UNKNOWN_GROUP";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_BOTH_GO_INTENT_15) {
        return "FAIL_BOTH_GO_INTENT_15";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_INCOMPATIBLE_PROV_METHOD) {
        return "FAIL_INCOMPATIBLE_PROV_METHOD";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_REJECTED_BY_USER) {
        return "FAIL_REJECTED_BY_USER";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::SUCCESS_DEFERRED) {
        return "SUCCESS_DEFERRED";
    }
    std::string os;
    os += toHexString(static_cast<uint32_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode o, ::std::ostream* os) {
    *os << toString(o);
}

template<>
inline std::string toString<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode>(uint8_t o) {
    using ::android::hardware::details::toHexString;
    std::string os;
    ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode> flipped = 0;
    bool first = true;
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::SUCCESS) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::SUCCESS)) {
        os += (first ? "" : " | ");
        os += "SUCCESS";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::SUCCESS;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::TIMEOUT) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::TIMEOUT)) {
        os += (first ? "" : " | ");
        os += "TIMEOUT";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::TIMEOUT;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::REJECTED) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::REJECTED)) {
        os += (first ? "" : " | ");
        os += "REJECTED";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::REJECTED;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::TIMEOUT_JOIN) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::TIMEOUT_JOIN)) {
        os += (first ? "" : " | ");
        os += "TIMEOUT_JOIN";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::TIMEOUT_JOIN;
    }
    if ((o & ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::INFO_UNAVAILABLE) == static_cast<uint8_t>(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::INFO_UNAVAILABLE)) {
        os += (first ? "" : " | ");
        os += "INFO_UNAVAILABLE";
        first = false;
        flipped |= ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::INFO_UNAVAILABLE;
    }
    if (o != flipped) {
        os += (first ? "" : " | ");
        os += toHexString(o & (~flipped));
    }os += " (";
    os += toHexString(o);
    os += ")";
    return os;
}

static inline std::string toString(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode o) {
    using ::android::hardware::details::toHexString;
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::SUCCESS) {
        return "SUCCESS";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::TIMEOUT) {
        return "TIMEOUT";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::REJECTED) {
        return "REJECTED";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::TIMEOUT_JOIN) {
        return "TIMEOUT_JOIN";
    }
    if (o == ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::INFO_UNAVAILABLE) {
        return "INFO_UNAVAILABLE";
    }
    std::string os;
    os += toHexString(static_cast<uint8_t>(o));
    return os;
}

static inline void PrintTo(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode o, ::std::ostream* os) {
    *os << toString(o);
}

static inline std::string toString(const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::descriptor;
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId, 8> hidl_enum_values<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId> = {
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::DEFAULT,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::USER_SPECIFIED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::MACHINE_SPECIFIED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::REKEY,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::PUSHBUTTON,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::REGISTRAR_SPECIFIED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::NFC_CONNECTION_HANDOVER,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId::P2PS_DEFAULT,
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode, 13> hidl_enum_values<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode> = {
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::SUCCESS,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_INFO_CURRENTLY_UNAVAILABLE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_INCOMPATIBLE_PARAMS,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_LIMIT_REACHED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_INVALID_PARAMS,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_UNABLE_TO_ACCOMMODATE,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_PREV_PROTOCOL_ERROR,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_NO_COMMON_CHANNELS,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_UNKNOWN_GROUP,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_BOTH_GO_INTENT_15,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_INCOMPATIBLE_PROV_METHOD,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::FAIL_REJECTED_BY_USER,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode::SUCCESS_DEFERRED,
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
template<> inline constexpr std::array<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode, 5> hidl_enum_values<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode> = {
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::SUCCESS,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::TIMEOUT,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::REJECTED,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::TIMEOUT_JOIN,
    ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode::INFO_UNAVAILABLE,
};
#pragma clang diagnostic pop
}  // namespace details
}  // namespace hardware
}  // namespace android


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_ISUPPLICANTP2PIFACECALLBACK_H
