#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_2_BPHWWIFINANIFACE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_2_BPHWWIFINANIFACE_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/wifi/1.2/IHwWifiNanIface.h>

namespace android {
namespace hardware {
namespace wifi {
namespace V1_2 {

struct BpHwWifiNanIface : public ::android::hardware::BpInterface<IWifiNanIface>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwWifiNanIface(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IWifiNanIface Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::wifi::V1_2::IWifiNanIface follow.
    static ::android::hardware::Return<void>  _hidl_registerEventCallback_1_2(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::wifi::V1_2::IWifiNanIfaceEventCallback>& callback, registerEventCallback_1_2_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_enableRequest_1_2(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanEnableRequest& msg1, const ::android::hardware::wifi::V1_2::NanConfigRequestSupplemental& msg2, enableRequest_1_2_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_configRequest_1_2(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanConfigRequest& msg1, const ::android::hardware::wifi::V1_2::NanConfigRequestSupplemental& msg2, configRequest_1_2_cb _hidl_cb);

    // Methods from ::android::hardware::wifi::V1_0::IWifiIface follow.
    ::android::hardware::Return<void> getType(getType_cb _hidl_cb) override;
    ::android::hardware::Return<void> getName(getName_cb _hidl_cb) override;

    // Methods from ::android::hardware::wifi::V1_0::IWifiNanIface follow.
    ::android::hardware::Return<void> registerEventCallback(const ::android::sp<::android::hardware::wifi::V1_0::IWifiNanIfaceEventCallback>& callback, registerEventCallback_cb _hidl_cb) override;
    ::android::hardware::Return<void> getCapabilitiesRequest(uint16_t cmdId, getCapabilitiesRequest_cb _hidl_cb) override;
    ::android::hardware::Return<void> enableRequest(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanEnableRequest& msg, enableRequest_cb _hidl_cb) override;
    ::android::hardware::Return<void> configRequest(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanConfigRequest& msg, configRequest_cb _hidl_cb) override;
    ::android::hardware::Return<void> disableRequest(uint16_t cmdId, disableRequest_cb _hidl_cb) override;
    ::android::hardware::Return<void> startPublishRequest(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanPublishRequest& msg, startPublishRequest_cb _hidl_cb) override;
    ::android::hardware::Return<void> stopPublishRequest(uint16_t cmdId, uint8_t sessionId, stopPublishRequest_cb _hidl_cb) override;
    ::android::hardware::Return<void> startSubscribeRequest(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanSubscribeRequest& msg, startSubscribeRequest_cb _hidl_cb) override;
    ::android::hardware::Return<void> stopSubscribeRequest(uint16_t cmdId, uint8_t sessionId, stopSubscribeRequest_cb _hidl_cb) override;
    ::android::hardware::Return<void> transmitFollowupRequest(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanTransmitFollowupRequest& msg, transmitFollowupRequest_cb _hidl_cb) override;
    ::android::hardware::Return<void> createDataInterfaceRequest(uint16_t cmdId, const ::android::hardware::hidl_string& ifaceName, createDataInterfaceRequest_cb _hidl_cb) override;
    ::android::hardware::Return<void> deleteDataInterfaceRequest(uint16_t cmdId, const ::android::hardware::hidl_string& ifaceName, deleteDataInterfaceRequest_cb _hidl_cb) override;
    ::android::hardware::Return<void> initiateDataPathRequest(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanInitiateDataPathRequest& msg, initiateDataPathRequest_cb _hidl_cb) override;
    ::android::hardware::Return<void> respondToDataPathIndicationRequest(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanRespondToDataPathIndicationRequest& msg, respondToDataPathIndicationRequest_cb _hidl_cb) override;
    ::android::hardware::Return<void> terminateDataPathRequest(uint16_t cmdId, uint32_t ndpInstanceId, terminateDataPathRequest_cb _hidl_cb) override;

    // Methods from ::android::hardware::wifi::V1_2::IWifiNanIface follow.
    ::android::hardware::Return<void> registerEventCallback_1_2(const ::android::sp<::android::hardware::wifi::V1_2::IWifiNanIfaceEventCallback>& callback, registerEventCallback_1_2_cb _hidl_cb) override;
    ::android::hardware::Return<void> enableRequest_1_2(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanEnableRequest& msg1, const ::android::hardware::wifi::V1_2::NanConfigRequestSupplemental& msg2, enableRequest_1_2_cb _hidl_cb) override;
    ::android::hardware::Return<void> configRequest_1_2(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanConfigRequest& msg1, const ::android::hardware::wifi::V1_2::NanConfigRequestSupplemental& msg2, configRequest_1_2_cb _hidl_cb) override;

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

}  // namespace V1_2
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_2_BPHWWIFINANIFACE_H
