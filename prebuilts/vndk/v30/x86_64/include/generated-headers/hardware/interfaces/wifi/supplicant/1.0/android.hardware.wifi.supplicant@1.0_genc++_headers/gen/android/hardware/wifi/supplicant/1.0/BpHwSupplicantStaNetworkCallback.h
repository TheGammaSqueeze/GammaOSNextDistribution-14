#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_BPHWSUPPLICANTSTANETWORKCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_BPHWSUPPLICANTSTANETWORKCALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/wifi/supplicant/1.0/IHwSupplicantStaNetworkCallback.h>

namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace V1_0 {

struct BpHwSupplicantStaNetworkCallback : public ::android::hardware::BpInterface<ISupplicantStaNetworkCallback>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwSupplicantStaNetworkCallback(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ISupplicantStaNetworkCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetworkCallback follow.
    static ::android::hardware::Return<void>  _hidl_onNetworkEapSimGsmAuthRequest(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetworkCallback::NetworkRequestEapSimGsmAuthParams& params);
    static ::android::hardware::Return<void>  _hidl_onNetworkEapSimUmtsAuthRequest(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetworkCallback::NetworkRequestEapSimUmtsAuthParams& params);
    static ::android::hardware::Return<void>  _hidl_onNetworkEapIdentityRequest(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);

    // Methods from ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetworkCallback follow.
    ::android::hardware::Return<void> onNetworkEapSimGsmAuthRequest(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetworkCallback::NetworkRequestEapSimGsmAuthParams& params) override;
    ::android::hardware::Return<void> onNetworkEapSimUmtsAuthRequest(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetworkCallback::NetworkRequestEapSimUmtsAuthParams& params) override;
    ::android::hardware::Return<void> onNetworkEapIdentityRequest() override;

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
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_BPHWSUPPLICANTSTANETWORKCALLBACK_H
