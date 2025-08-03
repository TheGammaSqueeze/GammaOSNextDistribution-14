#ifndef HIDL_GENERATED_ANDROID_SYSTEM_NET_NETD_V1_1_INETD_H
#define HIDL_GENERATED_ANDROID_SYSTEM_NET_NETD_V1_1_INETD_H

#include <android/system/net/netd/1.0/INetd.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace system {
namespace net {
namespace netd {
namespace V1_1 {

/**
 * This is the root of the HAL module and is the interface returned when
 * loading an implementation of the INetd HAL.
 */
struct INetd : public ::android::system::net::netd::V1_0::INetd {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.system.net.netd@1.1::INetd"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Return callback for createOemNetwork
     */
    using createOemNetwork_cb = std::function<void(uint64_t networkHandle, uint32_t packetMark, ::android::system::net::netd::V1_0::INetd::StatusCode status)>;
    /**
     * Creates a physical network to be used for interfaces managed by the OEM
     *
     * @return networkHandle a handle to the OEM network. Zero implies
     *             no networks are available and created
     * @return packetMark The packet mark that vendor network stack configuration code must use when
     *             routing packets to this network. No applications may use this mark. They must
     *             instead pass the networkHandle to the android_set*network LL-NDK APIs.
     * @return status operation status
     */
    virtual ::android::hardware::Return<void> createOemNetwork(createOemNetwork_cb _hidl_cb) = 0;

    /**
     * Destroys the specified network previously created using createOemNetwork()
     *
     * @return status operation status
     */
    virtual ::android::hardware::Return<::android::system::net::netd::V1_0::INetd::StatusCode> destroyOemNetwork(uint64_t networkHandle) = 0;

    /**
     * Add route to a specified OEM network
     * Either both, or one of the ifname and nexthop must be specified.
     *
     * @param networkHandle Handle to the OEM network previously returned from
     *                      @1.0::createOemNetwork.
     * @param ifname        Interface name specified by the route, or an empty
     *                      string for a route that does not specify an
     *                      interface.
     *                      For e.g. "dummy0"
     * @param destination   The destination prefix of the route in CIDR notation.
     *                      For e.g. 192.0.2.0/24 or 2001:db8:1::/48.
     * @param nexthop       IP address of the gateway for the route, or an empty
     *                      string for a directly-connected route. If non-empty,
     *                      must be of the same address family as the
     *                      destination.
     *                      For e.g. 10.0.0.1 or 2001:db8:1::cafe.
     */
    virtual ::android::hardware::Return<::android::system::net::netd::V1_0::INetd::StatusCode> addRouteToOemNetwork(uint64_t networkHandle, const ::android::hardware::hidl_string& ifname, const ::android::hardware::hidl_string& destination, const ::android::hardware::hidl_string& nexthop) = 0;

    /**
     * Remove route from a specified OEM network.
     * Either both, or one of the ifname and nexthop must be specified.
     *
     * @param networkHandle Handle to the OEM network previously returned from
     *                      @1.0::createOemNetwork.
     * @param ifname        Interface name specified by the route, or an empty
     *                      string for a route that does not specify an
     *                      interface.
     *                      For e.g. "dummy0"
     * @param destination   The destination prefix of the route in CIDR notation.
     *                      For e.g. 192.0.2.0/24 or 2001:db8:1::/48.
     * @param nexthop       IP address of the gateway for the route, or an empty
     *                      string for a directly-connected route. If non-empty,
     *                      must be of the same address family as the
     *                      destination.
     *                      For e.g. 10.0.0.1 or 2001:db8:1::cafe.
     */
    virtual ::android::hardware::Return<::android::system::net::netd::V1_0::INetd::StatusCode> removeRouteFromOemNetwork(uint64_t networkHandle, const ::android::hardware::hidl_string& ifname, const ::android::hardware::hidl_string& destination, const ::android::hardware::hidl_string& nexthop) = 0;

    /**
     * Add interface to a specified OEM network
     *
     * @param networkHandle Handle to the OEM network previously returned from
     *                      @1.0::createOemNetwork.
     * @param ifname        Interface name to add to the OEM network.
     *                      For e.g. "dummy0".
     */
    virtual ::android::hardware::Return<::android::system::net::netd::V1_0::INetd::StatusCode> addInterfaceToOemNetwork(uint64_t networkHandle, const ::android::hardware::hidl_string& ifname) = 0;

    /**
     * Remove interface from a specified OEM network.
     *
     * @param networkHandle Handle to the OEM network previously returned from
     *                      @1.0::createOemNetwork.
     * @param ifname        Interface name to remove from the OEM network.
     *                      For e.g. "dummy0".
     */
    virtual ::android::hardware::Return<::android::system::net::netd::V1_0::INetd::StatusCode> removeInterfaceFromOemNetwork(uint64_t networkHandle, const ::android::hardware::hidl_string& ifname) = 0;

    /**
     * Enable IP forwarding on the system. Client must disable forwarding when
     * it's no longer needed.
     *
     * @param enable    bool to enable or disable forwarding.
     */
    virtual ::android::hardware::Return<::android::system::net::netd::V1_0::INetd::StatusCode> setIpForwardEnable(bool enable) = 0;

    /**
     * Enables forwarding between two interfaces, one of which must be in an
     * OEM network.
     *
     * @param inputIfName  Input interface. For e.g. "dummy0".
     * @param outputIfName Output interface. For e.g. "rmnet_data7".
     * @param enable       bool to enable or disable forwarding between the
     *                     two interfaces.
     */
    virtual ::android::hardware::Return<::android::system::net::netd::V1_0::INetd::StatusCode> setForwardingBetweenInterfaces(const ::android::hardware::hidl_string& inputIfName, const ::android::hardware::hidl_string& outputIfName, bool enable) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::system::net::netd::V1_1::INetd>> castFrom(const ::android::sp<::android::system::net::netd::V1_1::INetd>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::system::net::netd::V1_1::INetd>> castFrom(const ::android::sp<::android::system::net::netd::V1_0::INetd>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::system::net::netd::V1_1::INetd>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<INetd> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<INetd> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<INetd> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<INetd> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<INetd> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<INetd> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<INetd> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<INetd> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::system::net::netd::V1_1::INetd>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::system::net::netd::V1_1::INetd>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::system::net::netd::V1_1::INetd::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_1
}  // namespace netd
}  // namespace net
}  // namespace system
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_SYSTEM_NET_NETD_V1_1_INETD_H
