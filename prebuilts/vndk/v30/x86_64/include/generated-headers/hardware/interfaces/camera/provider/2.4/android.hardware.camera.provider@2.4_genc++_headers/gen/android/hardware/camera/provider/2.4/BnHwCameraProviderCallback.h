#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_PROVIDER_V2_4_BNHWCAMERAPROVIDERCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_PROVIDER_V2_4_BNHWCAMERAPROVIDERCALLBACK_H

#include <android/hardware/camera/provider/2.4/IHwCameraProviderCallback.h>

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace V2_4 {

struct BnHwCameraProviderCallback : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwCameraProviderCallback(const ::android::sp<ICameraProviderCallback> &_hidl_impl);
    explicit BnHwCameraProviderCallback(const ::android::sp<ICameraProviderCallback> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwCameraProviderCallback();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef ICameraProviderCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<ICameraProviderCallback> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::camera::provider::V2_4::ICameraProviderCallback follow.
    static ::android::status_t _hidl_cameraDeviceStatusChange(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_torchModeStatusChange(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::camera::provider::V2_4::ICameraProviderCallback follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<ICameraProviderCallback> _hidl_mImpl;
};

}  // namespace V2_4
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_PROVIDER_V2_4_BNHWCAMERAPROVIDERCALLBACK_H
