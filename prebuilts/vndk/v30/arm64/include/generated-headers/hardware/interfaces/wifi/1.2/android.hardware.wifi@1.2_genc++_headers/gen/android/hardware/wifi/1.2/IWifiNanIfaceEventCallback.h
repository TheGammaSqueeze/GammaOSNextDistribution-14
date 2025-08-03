#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_2_IWIFINANIFACEEVENTCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_2_IWIFINANIFACEEVENTCALLBACK_H

#include <android/hardware/wifi/1.0/IWifiNanIfaceEventCallback.h>
#include <android/hardware/wifi/1.2/types.h>

#include <android/hidl/manager/1.0/IServiceNotification.h>

#include <hidl/HidlSupport.h>
#include <hidl/MQDescriptor.h>
#include <hidl/Status.h>
#include <utils/NativeHandle.h>
#include <utils/misc.h>

namespace android {
namespace hardware {
namespace wifi {
namespace V1_2 {

/**
 * NAN Response and Asynchronous Event Callbacks.
 *
 * References to "NAN Spec" are to the Wi-Fi Alliance "Wi-Fi Neighbor Awareness
 * Networking (NAN) Technical Specification".
 */
struct IWifiNanIfaceEventCallback : public ::android::hardware::wifi::V1_0::IWifiNanIfaceEventCallback {
    /**
     * Type tag for use in template logic that indicates this is a 'pure' class.
     */
    typedef ::android::hardware::details::i_tag _hidl_tag;

    /**
     * Fully qualified interface name: "android.hardware.wifi@1.2::IWifiNanIfaceEventCallback"
     */
    static const char* descriptor;

    /**
     * Returns whether this object's implementation is outside of the current process.
     */
    virtual bool isRemote() const override { return false; }

    /**
     * Notify callbacks are asynchronous callbacks - but in response to |IWifiNanIface| method calls.
     * Each method will receive a notify callback to return results (on success) or failure status.
     *
     *
     * Asynchronous callback invoked in response to a capability request
     * |IWifiNanIface.getCapabilitiesRequest|.
     *
     * @param cmdId command Id corresponding to the original request.
     * @param status WifiNanStatus of the operation. Possible status codes are:
     *        |NanStatusType.SUCCESS|
     * @param capabilities Capability data.
     */
    virtual ::android::hardware::Return<void> notifyCapabilitiesResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status, const ::android::hardware::wifi::V1_0::NanCapabilities& capabilities) = 0;

    /**
     * Asynchronous callback invoked in response to an enable request |IWifiNanIface.enableRequest|.
     *
     * @param cmdId command Id corresponding to the original request.
     * @param status WifiNanStatus of the operation. Possible status codes are:
     *        |NanStatusType.SUCCESS|
     *        |NanStatusType.ALREADY_ENABLED|
     *        |NanStatusType.INVALID_ARGS|
     *        |NanStatusType.INTERNAL_FAILURE|
     *        |NanStatusType.PROTOCOL_FAILURE|
     *        |NanStatusType.NAN_NOT_ALLOWED|
     */
    virtual ::android::hardware::Return<void> notifyEnableResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) = 0;

    /**
     * Asynchronous callback invoked in response to a config request |IWifiNanIface.configRequest|.
     *
     * @param cmdId command Id corresponding to the original request.
     * @param status WifiNanStatus of the operation. Possible status codes are:
     *        |NanStatusType.SUCCESS|
     *        |NanStatusType.INVALID_ARGS|
     *        |NanStatusType.INTERNAL_FAILURE|
     *        |NanStatusType.PROTOCOL_FAILURE|
     */
    virtual ::android::hardware::Return<void> notifyConfigResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) = 0;

    /**
     * Asynchronous callback invoked in response to a disable request |IWifiNanIface.disableRequest|.
     *
     * @param cmdId command Id corresponding to the original request.
     * @param status WifiNanStatus of the operation. Possible status codes are:
     *        |NanStatusType.SUCCESS|
     *        |NanStatusType.PROTOCOL_FAILURE|
     */
    virtual ::android::hardware::Return<void> notifyDisableResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) = 0;

    /**
     * Asynchronous callback invoked to notify the status of the start publish request
     * |IWifiNanIface.startPublishRequest|.
     *
     * @param cmdId command Id corresponding to the original request.
     * @param status WifiNanStatus of the operation. Possible status codes are:
     *        |NanStatusType.SUCCESS|
     *        |NanStatusType.INVALID_ARGS|
     *        |NanStatusType.PROTOCOL_FAILURE|
     *        |NanStatusType.NO_RESOURCES_AVAILABLE|
     *        |NanStatusType.INVALID_SESSION_ID|
     * @param sessionId ID of the new publish session (if successfully created).
     */
    virtual ::android::hardware::Return<void> notifyStartPublishResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status, uint8_t sessionId) = 0;

    /**
     * Asynchronous callback invoked in response to a stop publish request
     * |IWifiNanIface.stopPublishRequest|.
     *
     * @param cmdId command Id corresponding to the original request.
     * @param status WifiNanStatus of the operation. Possible status codes are:
     *        |NanStatusType.SUCCESS|
     *        |NanStatusType.INVALID_SESSION_ID|
     *        |NanStatusType.INTERNAL_FAILURE|
     */
    virtual ::android::hardware::Return<void> notifyStopPublishResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) = 0;

    /**
     * Asynchronous callback invoked to notify the status of the start subscribe request
     * |IWifiNanIface.startSubscribeRequest|.
     *
     * @param cmdId command Id corresponding to the original request.
     * @param status WifiNanStatus of the operation. Possible status codes are:
     *        |NanStatusType.SUCCESS|
     *        |NanStatusType.INVALID_ARGS|
     *        |NanStatusType.PROTOCOL_FAILURE|
     *        |NanStatusType.NO_RESOURCES_AVAILABLE|
     *        |NanStatusType.INVALID_SESSION_ID|
     * @param sessionId ID of the new subscribe session (if successfully created).
     */
    virtual ::android::hardware::Return<void> notifyStartSubscribeResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status, uint8_t sessionId) = 0;

    /**
     * Asynchronous callback invoked in response to a stop subscribe request
     * |IWifiNanIface.stopSubscribeRequest|.
     *
     * @param cmdId command Id corresponding to the original request.
     * @param status WifiNanStatus of the operation. Possible status codes are:
     *        |NanStatusType.SUCCESS|
     *        |NanStatusType.INVALID_SESSION_ID|
     *        |NanStatusType.INTERNAL_FAILURE|
     */
    virtual ::android::hardware::Return<void> notifyStopSubscribeResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) = 0;

    /**
     * Asynchronous callback invoked in response to a transmit followup request
     * |IWifiNanIface.transmitFollowupRequest|.
     *
     * @param cmdId command Id corresponding to the original request.
     * @param status WifiNanStatus of the operation. Possible status codes are:
     *        |NanStatusType.SUCCESS|
     *        |NanStatusType.INVALID_ARGS|
     *        |NanStatusType.INTERNAL_FAILURE|
     *        |NanStatusType.INVALID_SESSION_ID|
     *        |NanStatusType.INVALID_PEER_ID|
     *        |NanStatusType.FOLLOWUP_TX_QUEUE_FULL|
     */
    virtual ::android::hardware::Return<void> notifyTransmitFollowupResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) = 0;

    /**
     * Asynchronous callback invoked in response to a create data interface request
     * |IWifiNanIface.createDataInterfaceRequest|.
     *
     * @param cmdId command Id corresponding to the original request.
     * @param status WifiNanStatus of the operation. Possible status codes are:
     *        |NanStatusType.SUCCESS|
     *        |NanStatusType.INVALID_ARGS|
     *        |NanStatusType.INTERNAL_FAILURE|
     */
    virtual ::android::hardware::Return<void> notifyCreateDataInterfaceResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) = 0;

    /**
     * Asynchronous callback invoked in response to a delete data interface request
     * |IWifiNanIface.deleteDataInterfaceRequest|.
     *
     * @param cmdId command Id corresponding to the original request.
     * @param status WifiNanStatus of the operation. Possible status codes are:
     *        |NanStatusType.SUCCESS|
     *        |NanStatusType.INVALID_ARGS|
     *        |NanStatusType.INTERNAL_FAILURE|
     */
    virtual ::android::hardware::Return<void> notifyDeleteDataInterfaceResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) = 0;

    /**
     * Asynchronous callback invoked in response to an initiate data path request
     * |IWifiNanIface.initiateDataPathRequest|.
     *
     * @param cmdId command Id corresponding to the original request.
     * @param status WifiNanStatus of the operation. Possible status codes are:
     *        |NanStatusType.SUCCESS|
     *        |NanStatusType.INVALID_ARGS|
     *        |NanStatusType.INTERNAL_FAILURE|
     *        |NanStatusType.PROTOCOL_FAILURE|
     *        |NanStatusType.INVALID_PEER_ID|
     * @param ndpInstanceId ID of the new data path being negotiated (on successful status).
     */
    virtual ::android::hardware::Return<void> notifyInitiateDataPathResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status, uint32_t ndpInstanceId) = 0;

    /**
     * Asynchronous callback invoked in response to a respond to data path indication request
     * |IWifiNanIface.respondToDataPathIndicationRequest|.
     *
     * @param cmdId command Id corresponding to the original request.
     * @param status WifiNanStatus of the operation. Possible status codes are:
     *        |NanStatusType.SUCCESS|
     *        |NanStatusType.INVALID_ARGS|
     *        |NanStatusType.INTERNAL_FAILURE|
     *        |NanStatusType.PROTOCOL_FAILURE|
     *        |NanStatusType.INVALID_NDP_ID|
     */
    virtual ::android::hardware::Return<void> notifyRespondToDataPathIndicationResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) = 0;

    /**
     * Asynchronous callback invoked in response to a terminate data path request
     * |IWifiNanIface.terminateDataPathRequest|.
     *
     * @param cmdId command Id corresponding to the original request.
     * @param status WifiNanStatus of the operation. Possible status codes are:
     *        |NanStatusType.SUCCESS|
     *        |NanStatusType.INVALID_ARGS|
     *        |NanStatusType.INTERNAL_FAILURE|
     *        |NanStatusType.PROTOCOL_FAILURE|
     *        |NanStatusType.INVALID_NDP_ID|
     */
    virtual ::android::hardware::Return<void> notifyTerminateDataPathResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) = 0;

    /**
     * Callbacks for the various asynchornous NAN Events.
     *
     *
     * Asynchronous callback indicating that a cluster event has been received.
     *
     * @param event: NanClusterEventInd containing event details.
     */
    virtual ::android::hardware::Return<void> eventClusterEvent(const ::android::hardware::wifi::V1_0::NanClusterEventInd& event) = 0;

    /**
     * Asynchronous callback indicating that a NAN has been disabled.
     *
     * @param status: WifiNanStatus describing the reason for the disable event.
     *                Possible status codes are:
     *                |NanStatusType.SUCCESS|
     *                |NanStatusType.UNSUPPORTED_CONCURRENCY_NAN_DISABLED|
     */
    virtual ::android::hardware::Return<void> eventDisabled(const ::android::hardware::wifi::V1_0::WifiNanStatus& status) = 0;

    /**
     * Asynchronous callback indicating that an active publish session has terminated.
     *
     * @param sessionId: The discovery session ID of the terminated session.
     * @param status: WifiNanStatus describing the reason for the session termination.
     *                Possible status codes are:
     *                |NanStatusType.SUCCESS|
     */
    virtual ::android::hardware::Return<void> eventPublishTerminated(uint8_t sessionId, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) = 0;

    /**
     * Asynchronous callback indicating that an active subscribe session has terminated.
     *
     * @param sessionId: The discovery session ID of the terminated session.
     * @param status: WifiNanStatus describing the reason for the session termination.
     *                 Possible status codes are:
     *                |NanStatusType.SUCCESS|
     */
    virtual ::android::hardware::Return<void> eventSubscribeTerminated(uint8_t sessionId, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) = 0;

    /**
     * Asynchronous callback indicating that a match has occurred: i.e. a service has been
     * discovered.
     *
     * @param event: NanMatchInd containing event details.
     */
    virtual ::android::hardware::Return<void> eventMatch(const ::android::hardware::wifi::V1_0::NanMatchInd& event) = 0;

    /**
     * Asynchronous callback indicating that a previously discovered match (service) has expired.
     *
     * @param discoverySessionId: The discovery session ID of the expired match.
     * @param peerId: The peer ID of the expired match.
     */
    virtual ::android::hardware::Return<void> eventMatchExpired(uint8_t discoverySessionId, uint32_t peerId) = 0;

    /**
     * Asynchronous callback indicating that a followup message has been received from a peer.
     *
     * @param event: NanFollowupReceivedInd containing event details.
     */
    virtual ::android::hardware::Return<void> eventFollowupReceived(const ::android::hardware::wifi::V1_0::NanFollowupReceivedInd& event) = 0;

    /**
     * Asynchronous callback providing status on a completed followup message transmit operation.
     *
     * @param cmdId command Id corresponding to the original |transmitFollowupRequest| request.
     * @param status WifiNanStatus of the operation.  Possible status codes are:
     *               |NanStatusType.SUCCESS|
     *               |NanStatusType.NO_OTA_ACK|
     *               |NanStatusType.PROTOCOL_FAILURE|
     */
    virtual ::android::hardware::Return<void> eventTransmitFollowup(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) = 0;

    /**
     * Asynchronous callback indicating a data-path (NDP) setup has been requested by an Initiator
     * peer (received by the intended Respodner).
     *
     * @param event: NanDataPathRequestInd containing event details.
     */
    virtual ::android::hardware::Return<void> eventDataPathRequest(const ::android::hardware::wifi::V1_0::NanDataPathRequestInd& event) = 0;

    /**
     * Asynchronous callback indicating a data-path (NDP) setup has been completed: received by
     * both Initiator and Responder.
     *
     * @param event: NanDataPathConfirmInd containing event details.
     */
    virtual ::android::hardware::Return<void> eventDataPathConfirm(const ::android::hardware::wifi::V1_0::NanDataPathConfirmInd& event) = 0;

    /**
     * Asynchronous callback indicating a list of data-paths (NDP) have been terminated: received by
     * both Initiator and Responder.
     *
     * @param ndpInstanceId: data-path ID of the terminated data-path.
     */
    virtual ::android::hardware::Return<void> eventDataPathTerminated(uint32_t ndpInstanceId) = 0;

    /**
     * Asynchronous callback indicating a data-path (NDP) setup has been completed: received by
     * both Initiator and Responder.
     *
     * Note: supersedes the @1.0::IWifiNanIfaceEventCallback.eventDataPathConfirm() method which is
     * deprecated as of HAL version 1.2.
     *
     * @param event: NanDataPathConfirmInd containing event details.
     */
    virtual ::android::hardware::Return<void> eventDataPathConfirm_1_2(const ::android::hardware::wifi::V1_2::NanDataPathConfirmInd& event) = 0;

    /**
     * Asynchronous callback indicating a data-path (NDP) schedule has been updated (e.g. channels
     * have been changed).
     *
     * @param event: NanDataPathScheduleUpdateInd containing event details.
     */
    virtual ::android::hardware::Return<void> eventDataPathScheduleUpdate(const ::android::hardware::wifi::V1_2::NanDataPathScheduleUpdateInd& event) = 0;

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
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::V1_2::IWifiNanIfaceEventCallback>> castFrom(const ::android::sp<::android::hardware::wifi::V1_2::IWifiNanIfaceEventCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::V1_2::IWifiNanIfaceEventCallback>> castFrom(const ::android::sp<::android::hardware::wifi::V1_0::IWifiNanIfaceEventCallback>& parent, bool emitError = false);
    /**
     * This performs a checked cast based on what the underlying implementation actually is.
     */
    static ::android::hardware::Return<::android::sp<::android::hardware::wifi::V1_2::IWifiNanIfaceEventCallback>> castFrom(const ::android::sp<::android::hidl::base::V1_0::IBase>& parent, bool emitError = false);

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
    static ::android::sp<IWifiNanIfaceEventCallback> tryGetService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IWifiNanIfaceEventCallback> tryGetService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return tryGetService(str, getStub); }
    /**
     * Deprecated. See tryGetService(std::string, bool)
     */
    static ::android::sp<IWifiNanIfaceEventCallback> tryGetService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return tryGetService(str, getStub); }
    /**
     * Calls tryGetService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IWifiNanIfaceEventCallback> tryGetService(bool getStub) { return tryGetService("default", getStub); }
    /**
     * This gets the service of this type with the specified instance name. If the
     * service is not in the VINTF manifest on a Trebilized device, this will return
     * nullptr. If the service is not available, this will wait for the service to
     * become available. If the service is a lazy service, this will start the service
     * and return when it becomes available. If getStub is true, this will try to
     * return an unwrapped passthrough implementation in the same process. This is
     * useful when getting an implementation from the same partition/compilation group.
     */
    static ::android::sp<IWifiNanIfaceEventCallback> getService(const std::string &serviceName="default", bool getStub=false);
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IWifiNanIfaceEventCallback> getService(const char serviceName[], bool getStub=false)  { std::string str(serviceName ? serviceName : "");      return getService(str, getStub); }
    /**
     * Deprecated. See getService(std::string, bool)
     */
    static ::android::sp<IWifiNanIfaceEventCallback> getService(const ::android::hardware::hidl_string& serviceName, bool getStub=false)  { std::string str(serviceName.c_str());      return getService(str, getStub); }
    /**
     * Calls getService("default", bool). This is the recommended instance name for singleton services.
     */
    static ::android::sp<IWifiNanIfaceEventCallback> getService(bool getStub) { return getService("default", getStub); }
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

static inline std::string toString(const ::android::sp<::android::hardware::wifi::V1_2::IWifiNanIfaceEventCallback>& o);

//
// type header definitions for package
//

static inline std::string toString(const ::android::sp<::android::hardware::wifi::V1_2::IWifiNanIfaceEventCallback>& o) {
    std::string os = "[class or subclass of ";
    os += ::android::hardware::wifi::V1_2::IWifiNanIfaceEventCallback::descriptor;
    os += "]";
    os += o->isRemote() ? "@remote" : "@local";
    return os;
}


}  // namespace V1_2
}  // namespace wifi
}  // namespace hardware
}  // namespace android

//
// global type declarations for package
//


#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_2_IWIFINANIFACEEVENTCALLBACK_H
