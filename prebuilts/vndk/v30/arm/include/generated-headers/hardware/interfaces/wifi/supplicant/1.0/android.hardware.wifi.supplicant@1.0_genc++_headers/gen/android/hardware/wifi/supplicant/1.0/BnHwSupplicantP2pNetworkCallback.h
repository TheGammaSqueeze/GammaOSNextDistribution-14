#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_BNHWSUPPLICANTP2PNETWORKCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_BNHWSUPPLICANTP2PNETWORKCALLBACK_H

#include <android/hardware/wifi/supplicant/1.0/IHwSupplicantP2pNetworkCallback.h>

namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace V1_0 {

struct BnHwSupplicantP2pNetworkCallback : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwSupplicantP2pNetworkCallback(const ::android::sp<ISupplicantP2pNetworkCallback> &_hidl_impl);
    explicit BnHwSupplicantP2pNetworkCallback(const ::android::sp<ISupplicantP2pNetworkCallback> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwSupplicantP2pNetworkCallback();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef ISupplicantP2pNetworkCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<ISupplicantP2pNetworkCallback> getImpl() { return _hidl_mImpl; }

private:
    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<ISupplicantP2pNetworkCallback> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_BNHWSUPPLICANTP2PNETWORKCALLBACK_H
