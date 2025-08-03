#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_4_BPHWCAMERADEVICECALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_4_BPHWCAMERADEVICECALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/camera/device/3.4/IHwCameraDeviceCallback.h>

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace V3_4 {

struct BpHwCameraDeviceCallback : public ::android::hardware::BpInterface<ICameraDeviceCallback>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwCameraDeviceCallback(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ICameraDeviceCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::camera::device::V3_4::ICameraDeviceCallback follow.
    static ::android::hardware::Return<void>  _hidl_processCaptureResult_3_4(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_4::CaptureResult>& results);

    // Methods from ::android::hardware::camera::device::V3_2::ICameraDeviceCallback follow.
    ::android::hardware::Return<void> processCaptureResult(const ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_2::CaptureResult>& results) override;
    ::android::hardware::Return<void> notify(const ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_2::NotifyMsg>& msgs) override;

    // Methods from ::android::hardware::camera::device::V3_4::ICameraDeviceCallback follow.
    ::android::hardware::Return<void> processCaptureResult_3_4(const ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_4::CaptureResult>& results) override;

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> interfaceChain(interfaceChain_cb _hidl_cb) override;
    ::android::hardware::Return<void> debug(const ::android::hardware::hidl_handle& fd, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& options) override;
    ::android::hardware::Return<void> interfaceDescriptor(interfaceDescriptor_cb _hidl_cb) override;
    ::android::hardware::Return<void> getHashChain(getHashChain_cb _hidl_cb) override;
    ::android::hardware::Return<void> setHALInstrumentation() override;
    ::android::hardware::Return<bool> linkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient, uint64_t cookie) override;
    ::android::hardware::Return<void> ping() override;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb) override;
    ::android::hardware::Return<void> notifySyspropsChanged() override;
    ::android::hardware::Return<bool> unlinkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient) override;

private:
    std::mutex _hidl_mMutex;
    std::vector<::android::sp<::android::hardware::hidl_binder_death_recipient>> _hidl_mDeathRecipients;
};

}  // namespace V3_4
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_4_BPHWCAMERADEVICECALLBACK_H
