#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_IWIFINANIFACE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_IWIFINANIFACE_H

#include <android/hardware/wifi/1.0/IWifiIface.h>
#include <android/hardware/wifi/1.0/IWifiNanIfaceEventCallback.h>
#include <android/hardware/wifi/1.0/types.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace wifi {
namespace V1_0 {

/**
 * Interface used to represent a single NAN(Neighbour Aware Network) iface.
 *
 * References to "NAN Spec" are to the Wi-Fi Alliance "Wi-Fi Neighbor Awareness
 * Networking (NAN) Technical Specification".
 */
struct IWifiNanIface : public ::android::hardware::wifi::V1_0::IWifiIface {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.wifi@1.0::IWifiNanIface"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Return callback for getType
     */
    using getType_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status, ::android::hardware::wifi::V1_0::IfaceType type)>;
    /**
     * Get the type of this iface.
     *
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_IFACE_INVALID|
     * @return type One of the supported iface types.
     */
    virtual ::android::hardware::Return<void> getType(getType_cb _hidl_cb) = 0;

    /**
     * Return callback for getName
     */
    using getName_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status, const ::android::hardware::hidl_string& name)>;
    /**
     * Get the name of this iface.
     *
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_IFACE_INVALID|
     * @return name Name of the iface.
     */
    virtual ::android::hardware::Return<void> getName(getName_cb _hidl_cb) = 0;

    /**
     * Return callback for registerEventCallback
     */
    using registerEventCallback_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * Requests notifications of significant events on this iface. Multiple calls
     * to this must register multiple callbacks each of which must receive all
     * events.
     *
     * @param callback An instance of the |IWifiNanIfaceEventCallback| HIDL interface
     *        object.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_IFACE_INVALID|
     */
    virtual ::android::hardware::Return<void> registerEventCallback(const ::android::sp<::android::hardware::wifi::V1_0::IWifiNanIfaceEventCallback>& callback, registerEventCallback_cb _hidl_cb) = 0;

    /**
     * Return callback for getCapabilitiesRequest
     */
    using getCapabilitiesRequest_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * Get NAN capabilities. Asynchronous response is with
     * |IWifiNanIfaceEventCallback.notifyCapabilitiesResponse|.
     *
     * @param cmdId command Id to use for this invocation.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_IFACE_INVALID|,
     *         |WifiStatusCode.ERROR_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> getCapabilitiesRequest(uint16_t cmdId, getCapabilitiesRequest_cb _hidl_cb) = 0;

    /**
     * Return callback for enableRequest
     */
    using enableRequest_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * Enable NAN: configures and activates NAN clustering (does not start
     * a discovery session or set up data-interfaces or data-paths). Use the
     * |IWifiNanIface.configureRequest| method to change the configuration of an already enabled
     * NAN interface.
     * Asynchronous response is with |IWifiNanIfaceEventCallback.notifyEnableResponse|.
     *
     * @param cmdId command Id to use for this invocation.
     * @param msg Instance of |NanEnableRequest|.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_IFACE_INVALID|,
     *         |WifiStatusCode.ERROR_INVALID_ARGS|,
     *         |WifiStatusCode.ERROR_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> enableRequest(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanEnableRequest& msg, enableRequest_cb _hidl_cb) = 0;

    /**
     * Return callback for configRequest
     */
    using configRequest_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * Configure NAN: configures an existing NAN functionality (i.e. assumes
     * |IWifiNanIface.enableRequest| already submitted and succeeded).
     * Asynchronous response is with |IWifiNanIfaceEventCallback.notifyConfigResponse|.
     *
     * @param cmdId command Id to use for this invocation.
     * @param msg Instance of |NanConfigRequest|.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_IFACE_INVALID|,
     *         |WifiStatusCode.ERROR_INVALID_ARGS|,
     *         |WifiStatusCode.ERROR_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> configRequest(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanConfigRequest& msg, configRequest_cb _hidl_cb) = 0;

    /**
     * Return callback for disableRequest
     */
    using disableRequest_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * Disable NAN functionality.
     * Asynchronous response is with |IWifiNanIfaceEventCallback.notifyDisableResponse|.
     *
     * @param cmdId command Id to use for this invocation.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_IFACE_INVALID|,
     *         |WifiStatusCode.ERROR_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> disableRequest(uint16_t cmdId, disableRequest_cb _hidl_cb) = 0;

    /**
     * Return callback for startPublishRequest
     */
    using startPublishRequest_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * Publish request to start advertising a discovery service.
     * Asynchronous response is with |IWifiNanIfaceEventCallback.notifyStartPublishResponse|.
     *
     * @param cmdId command Id to use for this invocation.
     * @param msg Instance of |NanPublishRequest|.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_IFACE_INVALID|,
     *         |WifiStatusCode.ERROR_INVALID_ARGS|,
     *         |WifiStatusCode.ERROR_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> startPublishRequest(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanPublishRequest& msg, startPublishRequest_cb _hidl_cb) = 0;

    /**
     * Return callback for stopPublishRequest
     */
    using stopPublishRequest_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * Stop publishing a discovery service.
     * Asynchronous response is with |IWifiNanIfaceEventCallback.notifyStopPublishResponse|.
     *
     * @param cmdId command Id to use for this invocation.
     * @param sessionId ID of the publish discovery session to be stopped.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_IFACE_INVALID|,
     *         |WifiStatusCode.ERROR_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> stopPublishRequest(uint16_t cmdId, uint8_t sessionId, stopPublishRequest_cb _hidl_cb) = 0;

    /**
     * Return callback for startSubscribeRequest
     */
    using startSubscribeRequest_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * Subscribe request to start searching for a discovery service.
     * Asynchronous response is with |IWifiNanIfaceEventCallback.notifyStartSubscribeResponse|.
     *
     * @param cmdId command Id to use for this invocation.
     * @param msg Instance of |NanSubscribeRequest|.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_IFACE_INVALID|,
     *         |WifiStatusCode.ERROR_INVALID_ARGS|,
     *         |WifiStatusCode.ERROR_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> startSubscribeRequest(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanSubscribeRequest& msg, startSubscribeRequest_cb _hidl_cb) = 0;

    /**
     * Return callback for stopSubscribeRequest
     */
    using stopSubscribeRequest_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * Stop subscribing to a discovery service.
     * Asynchronous response is with |IWifiNanIfaceEventCallback.notifyStopSubscribeResponse|.
     *
     * @param cmdId command Id to use for this invocation.
     * @param sessionId ID of the subscribe discovery session to be stopped.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_IFACE_INVALID|,
     *         |WifiStatusCode.ERROR_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> stopSubscribeRequest(uint16_t cmdId, uint8_t sessionId, stopSubscribeRequest_cb _hidl_cb) = 0;

    /**
     * Return callback for transmitFollowupRequest
     */
    using transmitFollowupRequest_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * NAN transmit follow up message request.
     * Asynchronous response is with |IWifiNanIfaceEventCallback.notifyTransmitFollowupResponse|.
     *
     * @param cmdId command Id to use for this invocation.
     * @param msg Instance of |NanTransmitFollowupRequest|.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_IFACE_INVALID|,
     *         |WifiStatusCode.ERROR_INVALID_ARGS|,
     *         |WifiStatusCode.ERROR_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> transmitFollowupRequest(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanTransmitFollowupRequest& msg, transmitFollowupRequest_cb _hidl_cb) = 0;

    /**
     * Return callback for createDataInterfaceRequest
     */
    using createDataInterfaceRequest_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * Create a NAN Data Interface.
     * Asynchronous response is with |IWifiNanIfaceEventCallback.notifyCreateDataInterfaceResponse|.
     *
     * @param cmdId command Id to use for this invocation.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_IFACE_INVALID|,
     *         |WifiStatusCode.ERROR_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> createDataInterfaceRequest(uint16_t cmdId, const ::android::hardware::hidl_string& ifaceName, createDataInterfaceRequest_cb _hidl_cb) = 0;

    /**
     * Return callback for deleteDataInterfaceRequest
     */
    using deleteDataInterfaceRequest_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * Delete a NAN Data Interface.
     * Asynchronous response is with |IWifiNanIfaceEventCallback.notifyDeleteDataInterfaceResponse|.
     *
     * @param cmdId command Id to use for this invocation.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_IFACE_INVALID|,
     *         |WifiStatusCode.ERROR_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> deleteDataInterfaceRequest(uint16_t cmdId, const ::android::hardware::hidl_string& ifaceName, deleteDataInterfaceRequest_cb _hidl_cb) = 0;

    /**
     * Return callback for initiateDataPathRequest
     */
    using initiateDataPathRequest_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * Initiate a data-path (NDP) setup operation: Initiator.
     * Asynchronous response is with |IWifiNanIfaceEventCallback.notifyInitiateDataPathResponse|.
     *
     * @param cmdId command Id to use for this invocation.
     * @param msg Instance of |NanInitiateDataPathRequest|.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_IFACE_INVALID|,
     *         |WifiStatusCode.ERROR_INVALID_ARGS|,
     *         |WifiStatusCode.ERROR_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> initiateDataPathRequest(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanInitiateDataPathRequest& msg, initiateDataPathRequest_cb _hidl_cb) = 0;

    /**
     * Return callback for respondToDataPathIndicationRequest
     */
    using respondToDataPathIndicationRequest_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * Respond to a received data indication as part of a data-path (NDP) setup operation. An
     * indication is received by the Responder from the Initiator.
     * Asynchronous response is with
     * |IWifiNanIfaceEventCallback.notifyRespondToDataPathIndicationResponse|.
     *
     * @param cmdId command Id to use for this invocation.
     * @param msg Instance of |NanRespondToDataPathIndicationRequest|.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_IFACE_INVALID|,
     *         |WifiStatusCode.ERROR_INVALID_ARGS|,
     *         |WifiStatusCode.ERROR_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> respondToDataPathIndicationRequest(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanRespondToDataPathIndicationRequest& msg, respondToDataPathIndicationRequest_cb _hidl_cb) = 0;

    /**
     * Return callback for terminateDataPathRequest
     */
    using terminateDataPathRequest_cb = std::function<void(const ::android::hardware::wifi::V1_0::WifiStatus& status)>;
    /**
     * Data-path (NDP) termination request: executed by either Initiator or Responder.
     * Asynchronous response is with |IWifiNanIfaceEventCallback.notifyTerminateDataPathResponse|.
     *
     * @param cmdId command Id to use for this invocation.
     * @param ndpInstanceId Data-path instance ID to be terminated.
     * @return status WifiStatus of the operation.
     *         Possible status codes:
     *         |WifiStatusCode.SUCCESS|,
     *         |WifiStatusCode.ERROR_WIFI_IFACE_INVALID|,
     *         |WifiStatusCode.ERROR_UNKNOWN|
     */
    virtual ::android::hardware::Return<void> terminateDataPathRequest(uint16_t cmdId, uint32_t ndpInstanceId, terminateDataPathRequest_cb _hidl_cb) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::V1_0::IWifiNanIface>> castFrom(const ::android::sp<::android::hardware::wifi::V1_0::IWifiNanIface>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::V1_0::IWifiNanIface>> castFrom(const ::android::sp<::android::hardware::wifi::V1_0::IWifiIface>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::V1_0::IWifiNanIface>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IWifiNanIface> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IWifiNanIface> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IWifiNanIface> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IWifiNanIface> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IWifiNanIface> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IWifiNanIface> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IWifiNanIface> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IWifiNanIface> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::wifi::V1_0::IWifiNanIface>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::wifi::V1_0::IWifiNanIface>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::wifi::V1_0::IWifiNanIface::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_0
}  // namespace wifi
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_IWIFINANIFACE_H
