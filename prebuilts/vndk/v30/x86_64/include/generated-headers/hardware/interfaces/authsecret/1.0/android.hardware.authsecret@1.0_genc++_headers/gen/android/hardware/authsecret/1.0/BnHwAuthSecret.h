#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTHSECRET_V1_0_BNHWAUTHSECRET_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTHSECRET_V1_0_BNHWAUTHSECRET_H

#include <android/hardware/authsecret/1.0/IHwAuthSecret.h>

namespace android {
namespace hardware {
namespace authsecret {
namespace V1_0 {

struct BnHwAuthSecret : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwAuthSecret(const ::android::sp<IAuthSecret> &_hidl_impl);
    explicit BnHwAuthSecret(const ::android::sp<IAuthSecret> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwAuthSecret();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IAuthSecret Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IAuthSecret> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::authsecret::V1_0::IAuthSecret follow.
    static ::android::status_t _hidl_primaryUserCredential(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::authsecret::V1_0::IAuthSecret follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IAuthSecret> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace authsecret
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTHSECRET_V1_0_BNHWAUTHSECRET_H
