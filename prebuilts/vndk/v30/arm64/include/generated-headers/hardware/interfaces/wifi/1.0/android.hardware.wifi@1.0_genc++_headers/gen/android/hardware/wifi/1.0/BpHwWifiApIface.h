#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_BPHWWIFIAPIFACE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_BPHWWIFIAPIFACE_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/wifi/1.0/IHwWifiApIface.h>

namespace android {
namespace hardware {
namespace wifi {
namespace V1_0 {

struct BpHwWifiApIface : public ::android::hardware::BpInterface<IWifiApIface>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwWifiApIface(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IWifiApIface Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::wifi::V1_0::IWifiApIface follow.
    static ::android::hardware::Return<void>  _hidl_setCountryCode(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_array<int8_t, 2>& code, setCountryCode_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getValidFrequenciesForBand(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::wifi::V1_0::WifiBand band, getValidFrequenciesForBand_cb _hidl_cb);

    // Methods from ::android::hardware::wifi::V1_0::IWifiIface follow.
    ::android::hardware::Return<void> getType(getType_cb _hidl_cb) override;
    ::android::hardware::Return<void> getName(getName_cb _hidl_cb) override;

    // Methods from ::android::hardware::wifi::V1_0::IWifiApIface follow.
    ::android::hardware::Return<void> setCountryCode(const ::android::hardware::hidl_array<int8_t, 2>& code, setCountryCode_cb _hidl_cb) override;
    ::android::hardware::Return<void> getValidFrequenciesForBand(::android::hardware::wifi::V1_0::WifiBand band, getValidFrequenciesForBand_cb _hidl_cb) override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_BPHWWIFIAPIFACE_H
