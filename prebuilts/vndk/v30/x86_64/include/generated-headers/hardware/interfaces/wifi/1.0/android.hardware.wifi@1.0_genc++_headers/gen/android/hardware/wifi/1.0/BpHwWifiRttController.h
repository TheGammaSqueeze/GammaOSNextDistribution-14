#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_BPHWWIFIRTTCONTROLLER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_BPHWWIFIRTTCONTROLLER_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/wifi/1.0/IHwWifiRttController.h>

namespace android {
namespace hardware {
namespace wifi {
namespace V1_0 {

struct BpHwWifiRttController : public ::android::hardware::BpInterface<IWifiRttController>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwWifiRttController(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IWifiRttController Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::wifi::V1_0::IWifiRttController follow.
    static ::android::hardware::Return<void>  _hidl_getBoundIface(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getBoundIface_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_registerEventCallback(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::wifi::V1_0::IWifiRttControllerEventCallback>& callback, registerEventCallback_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_rangeRequest(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t cmdId, const ::android::hardware::hidl_vec<::android::hardware::wifi::V1_0::RttConfig>& rttConfigs, rangeRequest_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_rangeCancel(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t cmdId, const ::android::hardware::hidl_vec<::android::hardware::hidl_array<uint8_t, 6>>& addrs, rangeCancel_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getCapabilities(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getCapabilities_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setLci(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t cmdId, const ::android::hardware::wifi::V1_0::RttLciInformation& lci, setLci_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setLcr(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t cmdId, const ::android::hardware::wifi::V1_0::RttLcrInformation& lcr, setLcr_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getResponderInfo(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getResponderInfo_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_enableResponder(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t cmdId, const ::android::hardware::wifi::V1_0::WifiChannelInfo& channelHint, uint32_t maxDurationSeconds, const ::android::hardware::wifi::V1_0::RttResponder& info, enableResponder_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_disableResponder(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t cmdId, disableResponder_cb _hidl_cb);

    // Methods from ::android::hardware::wifi::V1_0::IWifiRttController follow.
    ::android::hardware::Return<void> getBoundIface(getBoundIface_cb _hidl_cb) override;
    ::android::hardware::Return<void> registerEventCallback(const ::android::sp<::android::hardware::wifi::V1_0::IWifiRttControllerEventCallback>& callback, registerEventCallback_cb _hidl_cb) override;
    ::android::hardware::Return<void> rangeRequest(uint32_t cmdId, const ::android::hardware::hidl_vec<::android::hardware::wifi::V1_0::RttConfig>& rttConfigs, rangeRequest_cb _hidl_cb) override;
    ::android::hardware::Return<void> rangeCancel(uint32_t cmdId, const ::android::hardware::hidl_vec<::android::hardware::hidl_array<uint8_t, 6>>& addrs, rangeCancel_cb _hidl_cb) override;
    ::android::hardware::Return<void> getCapabilities(getCapabilities_cb _hidl_cb) override;
    ::android::hardware::Return<void> setLci(uint32_t cmdId, const ::android::hardware::wifi::V1_0::RttLciInformation& lci, setLci_cb _hidl_cb) override;
    ::android::hardware::Return<void> setLcr(uint32_t cmdId, const ::android::hardware::wifi::V1_0::RttLcrInformation& lcr, setLcr_cb _hidl_cb) override;
    ::android::hardware::Return<void> getResponderInfo(getResponderInfo_cb _hidl_cb) override;
    ::android::hardware::Return<void> enableResponder(uint32_t cmdId, const ::android::hardware::wifi::V1_0::WifiChannelInfo& channelHint, uint32_t maxDurationSeconds, const ::android::hardware::wifi::V1_0::RttResponder& info, enableResponder_cb _hidl_cb) override;
    ::android::hardware::Return<void> disableResponder(uint32_t cmdId, disableResponder_cb _hidl_cb) override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_BPHWWIFIRTTCONTROLLER_H
