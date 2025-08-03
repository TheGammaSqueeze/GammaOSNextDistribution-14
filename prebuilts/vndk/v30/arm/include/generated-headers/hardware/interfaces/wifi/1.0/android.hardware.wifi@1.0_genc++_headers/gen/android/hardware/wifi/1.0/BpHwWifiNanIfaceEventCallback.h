#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_BPHWWIFINANIFACEEVENTCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_BPHWWIFINANIFACEEVENTCALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/wifi/1.0/IHwWifiNanIfaceEventCallback.h>

namespace android {
namespace hardware {
namespace wifi {
namespace V1_0 {

struct BpHwWifiNanIfaceEventCallback : public ::android::hardware::BpInterface<IWifiNanIfaceEventCallback>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwWifiNanIfaceEventCallback(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IWifiNanIfaceEventCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::wifi::V1_0::IWifiNanIfaceEventCallback follow.
    static ::android::hardware::Return<void>  _hidl_notifyCapabilitiesResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status, const ::android::hardware::wifi::V1_0::NanCapabilities& capabilities);
    static ::android::hardware::Return<void>  _hidl_notifyEnableResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status);
    static ::android::hardware::Return<void>  _hidl_notifyConfigResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status);
    static ::android::hardware::Return<void>  _hidl_notifyDisableResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status);
    static ::android::hardware::Return<void>  _hidl_notifyStartPublishResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status, uint8_t sessionId);
    static ::android::hardware::Return<void>  _hidl_notifyStopPublishResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status);
    static ::android::hardware::Return<void>  _hidl_notifyStartSubscribeResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status, uint8_t sessionId);
    static ::android::hardware::Return<void>  _hidl_notifyStopSubscribeResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status);
    static ::android::hardware::Return<void>  _hidl_notifyTransmitFollowupResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status);
    static ::android::hardware::Return<void>  _hidl_notifyCreateDataInterfaceResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status);
    static ::android::hardware::Return<void>  _hidl_notifyDeleteDataInterfaceResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status);
    static ::android::hardware::Return<void>  _hidl_notifyInitiateDataPathResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status, uint32_t ndpInstanceId);
    static ::android::hardware::Return<void>  _hidl_notifyRespondToDataPathIndicationResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status);
    static ::android::hardware::Return<void>  _hidl_notifyTerminateDataPathResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status);
    static ::android::hardware::Return<void>  _hidl_eventClusterEvent(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::wifi::V1_0::NanClusterEventInd& event);
    static ::android::hardware::Return<void>  _hidl_eventDisabled(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::wifi::V1_0::WifiNanStatus& status);
    static ::android::hardware::Return<void>  _hidl_eventPublishTerminated(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint8_t sessionId, const ::android::hardware::wifi::V1_0::WifiNanStatus& status);
    static ::android::hardware::Return<void>  _hidl_eventSubscribeTerminated(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint8_t sessionId, const ::android::hardware::wifi::V1_0::WifiNanStatus& status);
    static ::android::hardware::Return<void>  _hidl_eventMatch(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::wifi::V1_0::NanMatchInd& event);
    static ::android::hardware::Return<void>  _hidl_eventMatchExpired(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint8_t discoverySessionId, uint32_t peerId);
    static ::android::hardware::Return<void>  _hidl_eventFollowupReceived(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::wifi::V1_0::NanFollowupReceivedInd& event);
    static ::android::hardware::Return<void>  _hidl_eventTransmitFollowup(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status);
    static ::android::hardware::Return<void>  _hidl_eventDataPathRequest(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::wifi::V1_0::NanDataPathRequestInd& event);
    static ::android::hardware::Return<void>  _hidl_eventDataPathConfirm(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::wifi::V1_0::NanDataPathConfirmInd& event);
    static ::android::hardware::Return<void>  _hidl_eventDataPathTerminated(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t ndpInstanceId);

    // Methods from ::android::hardware::wifi::V1_0::IWifiNanIfaceEventCallback follow.
    ::android::hardware::Return<void> notifyCapabilitiesResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status, const ::android::hardware::wifi::V1_0::NanCapabilities& capabilities) override;
    ::android::hardware::Return<void> notifyEnableResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) override;
    ::android::hardware::Return<void> notifyConfigResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) override;
    ::android::hardware::Return<void> notifyDisableResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) override;
    ::android::hardware::Return<void> notifyStartPublishResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status, uint8_t sessionId) override;
    ::android::hardware::Return<void> notifyStopPublishResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) override;
    ::android::hardware::Return<void> notifyStartSubscribeResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status, uint8_t sessionId) override;
    ::android::hardware::Return<void> notifyStopSubscribeResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) override;
    ::android::hardware::Return<void> notifyTransmitFollowupResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) override;
    ::android::hardware::Return<void> notifyCreateDataInterfaceResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) override;
    ::android::hardware::Return<void> notifyDeleteDataInterfaceResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) override;
    ::android::hardware::Return<void> notifyInitiateDataPathResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status, uint32_t ndpInstanceId) override;
    ::android::hardware::Return<void> notifyRespondToDataPathIndicationResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) override;
    ::android::hardware::Return<void> notifyTerminateDataPathResponse(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) override;
    ::android::hardware::Return<void> eventClusterEvent(const ::android::hardware::wifi::V1_0::NanClusterEventInd& event) override;
    ::android::hardware::Return<void> eventDisabled(const ::android::hardware::wifi::V1_0::WifiNanStatus& status) override;
    ::android::hardware::Return<void> eventPublishTerminated(uint8_t sessionId, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) override;
    ::android::hardware::Return<void> eventSubscribeTerminated(uint8_t sessionId, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) override;
    ::android::hardware::Return<void> eventMatch(const ::android::hardware::wifi::V1_0::NanMatchInd& event) override;
    ::android::hardware::Return<void> eventMatchExpired(uint8_t discoverySessionId, uint32_t peerId) override;
    ::android::hardware::Return<void> eventFollowupReceived(const ::android::hardware::wifi::V1_0::NanFollowupReceivedInd& event) override;
    ::android::hardware::Return<void> eventTransmitFollowup(uint16_t id, const ::android::hardware::wifi::V1_0::WifiNanStatus& status) override;
    ::android::hardware::Return<void> eventDataPathRequest(const ::android::hardware::wifi::V1_0::NanDataPathRequestInd& event) override;
    ::android::hardware::Return<void> eventDataPathConfirm(const ::android::hardware::wifi::V1_0::NanDataPathConfirmInd& event) override;
    ::android::hardware::Return<void> eventDataPathTerminated(uint32_t ndpInstanceId) override;

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> interfaceChain(interfaceChain_cb _hidl_cb) override;
    ::android::hardware::Return<void> debug(const ::android::hardware::hidl_handle& fd, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& options) override;
    ::android::hardware::Return<void> interfaceDescriptor(interfaceDescriptor_cb _hidl_cb) override;
    ::android::hardware::Return<void> getHashChain(getHashChain_cb _hidl_cb) override;
    ::android::hardware::Return<void> setHALInstrumentation() override;
    ::android::hardware::Return<bool> linkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient, uint64_t cookie) override;
    ::android::hardware::Return<void> ping() override;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb) override;
    ::android::hardware::Return<void> notifySyspropsChanged() override;
    ::android::hardware::Return<bool> unlinkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient) override;

private:
    std::mutex _hidl_mMutex;
    std::vector<::android::sp<::android::hardware::hidl_binder_death_recipient>> _hidl_mDeathRecipients;
};

}  // namespace V1_0
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_BPHWWIFINANIFACEEVENTCALLBACK_H
