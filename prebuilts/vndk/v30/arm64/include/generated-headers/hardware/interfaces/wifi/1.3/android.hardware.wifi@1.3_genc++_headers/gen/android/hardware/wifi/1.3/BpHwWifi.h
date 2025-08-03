#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_3_BPHWWIFI_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_3_BPHWWIFI_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/wifi/1.3/IHwWifi.h>

namespace android {
namespace hardware {
namespace wifi {
namespace V1_3 {

struct BpHwWifi : public ::android::hardware::BpInterface<IWifi>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwWifi(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IWifi Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;


    // Methods from ::android::hardware::wifi::V1_0::IWifi follow.
    ::android::hardware::Return<void> registerEventCallback(const ::android::sp<::android::hardware::wifi::V1_0::IWifiEventCallback>& callback, registerEventCallback_cb _hidl_cb) override;
    ::android::hardware::Return<bool> isStarted() override;
    ::android::hardware::Return<void> start(start_cb _hidl_cb) override;
    ::android::hardware::Return<void> stop(stop_cb _hidl_cb) override;
    ::android::hardware::Return<void> getChipIds(getChipIds_cb _hidl_cb) override;
    ::android::hardware::Return<void> getChip(uint32_t chipId, getChip_cb _hidl_cb) override;

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

}  // namespace V1_3
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_3_BPHWWIFI_H
