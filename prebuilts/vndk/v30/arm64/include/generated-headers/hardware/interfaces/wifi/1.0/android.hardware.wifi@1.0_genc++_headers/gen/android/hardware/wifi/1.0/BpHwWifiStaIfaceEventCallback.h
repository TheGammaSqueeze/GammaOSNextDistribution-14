#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_BPHWWIFISTAIFACEEVENTCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_BPHWWIFISTAIFACEEVENTCALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/wifi/1.0/IHwWifiStaIfaceEventCallback.h>

namespace android {
namespace hardware {
namespace wifi {
namespace V1_0 {

struct BpHwWifiStaIfaceEventCallback : public ::android::hardware::BpInterface<IWifiStaIfaceEventCallback>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwWifiStaIfaceEventCallback(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IWifiStaIfaceEventCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::wifi::V1_0::IWifiStaIfaceEventCallback follow.
    static ::android::hardware::Return<void>  _hidl_onBackgroundScanFailure(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t cmdId);
    static ::android::hardware::Return<void>  _hidl_onBackgroundFullScanResult(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t cmdId, uint32_t bucketsScanned, const ::android::hardware::wifi::V1_0::StaScanResult& result);
    static ::android::hardware::Return<void>  _hidl_onBackgroundScanResults(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t cmdId, const ::android::hardware::hidl_vec<::android::hardware::wifi::V1_0::StaScanData>& scanDatas);
    static ::android::hardware::Return<void>  _hidl_onRssiThresholdBreached(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t cmdId, const ::android::hardware::hidl_array<uint8_t, 6>& currBssid, int32_t currRssi);

    // Methods from ::android::hardware::wifi::V1_0::IWifiStaIfaceEventCallback follow.
    ::android::hardware::Return<void> onBackgroundScanFailure(uint32_t cmdId) override;
    ::android::hardware::Return<void> onBackgroundFullScanResult(uint32_t cmdId, uint32_t bucketsScanned, const ::android::hardware::wifi::V1_0::StaScanResult& result) override;
    ::android::hardware::Return<void> onBackgroundScanResults(uint32_t cmdId, const ::android::hardware::hidl_vec<::android::hardware::wifi::V1_0::StaScanData>& scanDatas) override;
    ::android::hardware::Return<void> onRssiThresholdBreached(uint32_t cmdId, const ::android::hardware::hidl_array<uint8_t, 6>& currBssid, int32_t currRssi) override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_BPHWWIFISTAIFACEEVENTCALLBACK_H
