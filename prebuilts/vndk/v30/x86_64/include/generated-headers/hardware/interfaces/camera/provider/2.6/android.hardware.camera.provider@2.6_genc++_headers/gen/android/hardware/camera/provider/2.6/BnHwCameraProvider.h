#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_PROVIDER_V2_6_BNHWCAMERAPROVIDER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_PROVIDER_V2_6_BNHWCAMERAPROVIDER_H

#include <android/hardware/camera/provider/2.6/IHwCameraProvider.h>

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace V2_6 {

struct BnHwCameraProvider : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwCameraProvider(const ::android::sp<ICameraProvider> &_hidl_impl);
    explicit BnHwCameraProvider(const ::android::sp<ICameraProvider> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwCameraProvider();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef ICameraProvider Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<ICameraProvider> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::camera::provider::V2_6::ICameraProvider follow.
    static ::android::status_t _hidl_getConcurrentStreamingCameraIds(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_isConcurrentStreamCombinationSupported(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::camera::provider::V2_4::ICameraProvider follow.

    // Methods from ::android::hardware::camera::provider::V2_5::ICameraProvider follow.

    // Methods from ::android::hardware::camera::provider::V2_6::ICameraProvider follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<ICameraProvider> _hidl_mImpl;
};

}  // namespace V2_6
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_PROVIDER_V2_6_BNHWCAMERAPROVIDER_H
