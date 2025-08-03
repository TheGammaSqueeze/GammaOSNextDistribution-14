#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_6_BNHWCAMERAOFFLINESESSION_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_6_BNHWCAMERAOFFLINESESSION_H

#include <android/hardware/camera/device/3.6/IHwCameraOfflineSession.h>

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace V3_6 {

struct BnHwCameraOfflineSession : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwCameraOfflineSession(const ::android::sp<ICameraOfflineSession> &_hidl_impl);
    explicit BnHwCameraOfflineSession(const ::android::sp<ICameraOfflineSession> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwCameraOfflineSession();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef ICameraOfflineSession Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<ICameraOfflineSession> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::camera::device::V3_6::ICameraOfflineSession follow.
    static ::android::status_t _hidl_setCallback(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_getCaptureResultMetadataQueue(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_close(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::camera::device::V3_6::ICameraOfflineSession follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<ICameraOfflineSession> _hidl_mImpl;
};

}  // namespace V3_6
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_6_BNHWCAMERAOFFLINESESSION_H
