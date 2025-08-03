#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_4_BPHWWIFICHIPEVENTCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_4_BPHWWIFICHIPEVENTCALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/wifi/1.4/IHwWifiChipEventCallback.h>

namespace android {
namespace hardware {
namespace wifi {
namespace V1_4 {

struct BpHwWifiChipEventCallback : public ::android::hardware::BpInterface<IWifiChipEventCallback>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwWifiChipEventCallback(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IWifiChipEventCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::wifi::V1_4::IWifiChipEventCallback follow.
    static ::android::hardware::Return<void>  _hidl_onRadioModeChange_1_4(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::wifi::V1_4::IWifiChipEventCallback::RadioModeInfo>& radioModeInfos);

    // Methods from ::android::hardware::wifi::V1_0::IWifiChipEventCallback follow.
    ::android::hardware::Return<void> onChipReconfigured(uint32_t modeId) override;
    ::android::hardware::Return<void> onChipReconfigureFailure(const ::android::hardware::wifi::V1_0::WifiStatus& status) override;
    ::android::hardware::Return<void> onIfaceAdded(::android::hardware::wifi::V1_0::IfaceType type, const ::android::hardware::hidl_string& name) override;
    ::android::hardware::Return<void> onIfaceRemoved(::android::hardware::wifi::V1_0::IfaceType type, const ::android::hardware::hidl_string& name) override;
    ::android::hardware::Return<void> onDebugRingBufferDataAvailable(const ::android::hardware::wifi::V1_0::WifiDebugRingBufferStatus& status, const ::android::hardware::hidl_vec<uint8_t>& data) override;
    ::android::hardware::Return<void> onDebugErrorAlert(int32_t errorCode, const ::android::hardware::hidl_vec<uint8_t>& debugData) override;

    // Methods from ::android::hardware::wifi::V1_2::IWifiChipEventCallback follow.
    ::android::hardware::Return<void> onRadioModeChange(const ::android::hardware::hidl_vec<::android::hardware::wifi::V1_2::IWifiChipEventCallback::RadioModeInfo>& radioModeInfos) override;

    // Methods from ::android::hardware::wifi::V1_4::IWifiChipEventCallback follow.
    ::android::hardware::Return<void> onRadioModeChange_1_4(const ::android::hardware::hidl_vec<::android::hardware::wifi::V1_4::IWifiChipEventCallback::RadioModeInfo>& radioModeInfos) override;

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

}  // namespace V1_4
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_4_BPHWWIFICHIPEVENTCALLBACK_H
