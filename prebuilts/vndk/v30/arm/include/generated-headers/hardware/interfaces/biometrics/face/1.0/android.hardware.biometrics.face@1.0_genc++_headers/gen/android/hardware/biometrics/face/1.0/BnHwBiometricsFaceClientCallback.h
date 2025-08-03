#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FACE_V1_0_BNHWBIOMETRICSFACECLIENTCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FACE_V1_0_BNHWBIOMETRICSFACECLIENTCALLBACK_H

#include <android/hardware/biometrics/face/1.0/IHwBiometricsFaceClientCallback.h>

namespace android {
namespace hardware {
namespace biometrics {
namespace face {
namespace V1_0 {

struct BnHwBiometricsFaceClientCallback : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwBiometricsFaceClientCallback(const ::android::sp<IBiometricsFaceClientCallback> &_hidl_impl);
    explicit BnHwBiometricsFaceClientCallback(const ::android::sp<IBiometricsFaceClientCallback> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwBiometricsFaceClientCallback();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IBiometricsFaceClientCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IBiometricsFaceClientCallback> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::biometrics::face::V1_0::IBiometricsFaceClientCallback follow.
    static ::android::status_t _hidl_onEnrollResult(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_onAuthenticated(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_onAcquired(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_onError(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_onRemoved(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_onEnumerate(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_onLockoutChanged(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::biometrics::face::V1_0::IBiometricsFaceClientCallback follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IBiometricsFaceClientCallback> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace face
}  // namespace biometrics
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FACE_V1_0_BNHWBIOMETRICSFACECLIENTCALLBACK_H
