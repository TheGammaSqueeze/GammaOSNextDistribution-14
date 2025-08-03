#ifndef HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_3_BNHWDRMFACTORY_H
#define HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_3_BNHWDRMFACTORY_H

#include <android/hardware/drm/1.3/IHwDrmFactory.h>

namespace android {
namespace hardware {
namespace drm {
namespace V1_3 {

struct BnHwDrmFactory : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwDrmFactory(const ::android::sp<IDrmFactory> &_hidl_impl);
    explicit BnHwDrmFactory(const ::android::sp<IDrmFactory> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwDrmFactory();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IDrmFactory Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IDrmFactory> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::drm::V1_3::IDrmFactory follow.
    static ::android::status_t _hidl_getSupportedCryptoSchemes(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::drm::V1_0::IDrmFactory follow.

    // Methods from ::android::hardware::drm::V1_2::IDrmFactory follow.

    // Methods from ::android::hardware::drm::V1_3::IDrmFactory follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IDrmFactory> _hidl_mImpl;
};

}  // namespace V1_3
}  // namespace drm
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_3_BNHWDRMFACTORY_H
