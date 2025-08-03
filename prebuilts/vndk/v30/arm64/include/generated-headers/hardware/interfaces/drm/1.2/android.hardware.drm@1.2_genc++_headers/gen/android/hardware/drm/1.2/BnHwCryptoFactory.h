#ifndef HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_2_BNHWCRYPTOFACTORY_H
#define HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_2_BNHWCRYPTOFACTORY_H

#include <android/hardware/drm/1.2/IHwCryptoFactory.h>

namespace android {
namespace hardware {
namespace drm {
namespace V1_2 {

struct BnHwCryptoFactory : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwCryptoFactory(const ::android::sp<ICryptoFactory> &_hidl_impl);
    explicit BnHwCryptoFactory(const ::android::sp<ICryptoFactory> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwCryptoFactory();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef ICryptoFactory Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<ICryptoFactory> getImpl() { return _hidl_mImpl; }

private:
    // Methods from ::android::hardware::drm::V1_0::ICryptoFactory follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<ICryptoFactory> _hidl_mImpl;
};

}  // namespace V1_2
}  // namespace drm
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_2_BNHWCRYPTOFACTORY_H
