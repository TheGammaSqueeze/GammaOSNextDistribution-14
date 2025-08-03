#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FINGERPRINT_V2_2_BNHWBIOMETRICSFINGERPRINTCLIENTCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FINGERPRINT_V2_2_BNHWBIOMETRICSFINGERPRINTCLIENTCALLBACK_H

#include <android/hardware/biometrics/fingerprint/2.2/IHwBiometricsFingerprintClientCallback.h>

namespace android {
namespace hardware {
namespace biometrics {
namespace fingerprint {
namespace V2_2 {

struct BnHwBiometricsFingerprintClientCallback : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwBiometricsFingerprintClientCallback(const ::android::sp<IBiometricsFingerprintClientCallback> &_hidl_impl);
    explicit BnHwBiometricsFingerprintClientCallback(const ::android::sp<IBiometricsFingerprintClientCallback> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwBiometricsFingerprintClientCallback();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IBiometricsFingerprintClientCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IBiometricsFingerprintClientCallback> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::biometrics::fingerprint::V2_2::IBiometricsFingerprintClientCallback follow.
    static ::android::status_t _hidl_onAcquired_2_2(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::biometrics::fingerprint::V2_1::IBiometricsFingerprintClientCallback follow.

    // Methods from ::android::hardware::biometrics::fingerprint::V2_2::IBiometricsFingerprintClientCallback follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IBiometricsFingerprintClientCallback> _hidl_mImpl;
};

}  // namespace V2_2
}  // namespace fingerprint
}  // namespace biometrics
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FINGERPRINT_V2_2_BNHWBIOMETRICSFINGERPRINTCLIENTCALLBACK_H
