#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_2_BNHWSUPPLICANTSTAIFACECALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_2_BNHWSUPPLICANTSTAIFACECALLBACK_H

#include <android/hardware/wifi/supplicant/1.2/IHwSupplicantStaIfaceCallback.h>

namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace V1_2 {

struct BnHwSupplicantStaIfaceCallback : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwSupplicantStaIfaceCallback(const ::android::sp<ISupplicantStaIfaceCallback> &_hidl_impl);
    explicit BnHwSupplicantStaIfaceCallback(const ::android::sp<ISupplicantStaIfaceCallback> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwSupplicantStaIfaceCallback();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef ISupplicantStaIfaceCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<ISupplicantStaIfaceCallback> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::wifi::supplicant::V1_2::ISupplicantStaIfaceCallback follow.
    static ::android::status_t _hidl_onDppSuccessConfigReceived(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_onDppSuccessConfigSent(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_onDppProgress(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_onDppFailure(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback follow.

    // Methods from ::android::hardware::wifi::supplicant::V1_1::ISupplicantStaIfaceCallback follow.

    // Methods from ::android::hardware::wifi::supplicant::V1_2::ISupplicantStaIfaceCallback follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<ISupplicantStaIfaceCallback> _hidl_mImpl;
};

}  // namespace V1_2
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_2_BNHWSUPPLICANTSTAIFACECALLBACK_H
