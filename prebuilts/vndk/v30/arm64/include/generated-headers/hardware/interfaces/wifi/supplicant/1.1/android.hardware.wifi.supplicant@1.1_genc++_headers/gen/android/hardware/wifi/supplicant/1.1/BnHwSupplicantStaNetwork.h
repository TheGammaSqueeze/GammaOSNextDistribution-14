#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_1_BNHWSUPPLICANTSTANETWORK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_1_BNHWSUPPLICANTSTANETWORK_H

#include <android/hardware/wifi/supplicant/1.1/IHwSupplicantStaNetwork.h>

namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace V1_1 {

struct BnHwSupplicantStaNetwork : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwSupplicantStaNetwork(const ::android::sp<ISupplicantStaNetwork> &_hidl_impl);
    explicit BnHwSupplicantStaNetwork(const ::android::sp<ISupplicantStaNetwork> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwSupplicantStaNetwork();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef ISupplicantStaNetwork Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<ISupplicantStaNetwork> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::wifi::supplicant::V1_1::ISupplicantStaNetwork follow.
    static ::android::status_t _hidl_setEapEncryptedImsiIdentity(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_sendNetworkEapIdentityResponse_1_1(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::wifi::supplicant::V1_0::ISupplicantNetwork follow.

    // Methods from ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork follow.

    // Methods from ::android::hardware::wifi::supplicant::V1_1::ISupplicantStaNetwork follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<ISupplicantStaNetwork> _hidl_mImpl;
};

}  // namespace V1_1
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_1_BNHWSUPPLICANTSTANETWORK_H
