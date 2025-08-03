#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_6_BPHWCAMERAOFFLINESESSION_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_6_BPHWCAMERAOFFLINESESSION_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/camera/device/3.6/IHwCameraOfflineSession.h>

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace V3_6 {

struct BpHwCameraOfflineSession : public ::android::hardware::BpInterface<ICameraOfflineSession>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwCameraOfflineSession(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ICameraOfflineSession Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::camera::device::V3_6::ICameraOfflineSession follow.
    static ::android::hardware::Return<void>  _hidl_setCallback(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::camera::device::V3_5::ICameraDeviceCallback>& cb);
    static ::android::hardware::Return<void>  _hidl_getCaptureResultMetadataQueue(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getCaptureResultMetadataQueue_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_close(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);

    // Methods from ::android::hardware::camera::device::V3_6::ICameraOfflineSession follow.
    ::android::hardware::Return<void> setCallback(const ::android::sp<::android::hardware::camera::device::V3_5::ICameraDeviceCallback>& cb) override;
    ::android::hardware::Return<void> getCaptureResultMetadataQueue(getCaptureResultMetadataQueue_cb _hidl_cb) override;
    ::android::hardware::Return<void> close() override;

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

}  // namespace V3_6
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_6_BPHWCAMERAOFFLINESESSION_H
