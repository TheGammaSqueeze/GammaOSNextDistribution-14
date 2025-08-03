#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_4_BPHWCAMERADEVICESESSION_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_4_BPHWCAMERADEVICESESSION_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/camera/device/3.4/IHwCameraDeviceSession.h>

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace V3_4 {

struct BpHwCameraDeviceSession : public ::android::hardware::BpInterface<ICameraDeviceSession>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwCameraDeviceSession(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ICameraDeviceSession Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::camera::device::V3_4::ICameraDeviceSession follow.
    static ::android::hardware::Return<void>  _hidl_configureStreams_3_4(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::camera::device::V3_4::StreamConfiguration& requestedConfiguration, configureStreams_3_4_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_processCaptureRequest_3_4(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_4::CaptureRequest>& requests, const ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_2::BufferCache>& cachesToRemove, processCaptureRequest_3_4_cb _hidl_cb);

    // Methods from ::android::hardware::camera::device::V3_2::ICameraDeviceSession follow.
    ::android::hardware::Return<void> constructDefaultRequestSettings(::android::hardware::camera::device::V3_2::RequestTemplate type, constructDefaultRequestSettings_cb _hidl_cb) override;
    ::android::hardware::Return<void> configureStreams(const ::android::hardware::camera::device::V3_2::StreamConfiguration& requestedConfiguration, configureStreams_cb _hidl_cb) override;
    ::android::hardware::Return<void> processCaptureRequest(const ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_2::CaptureRequest>& requests, const ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_2::BufferCache>& cachesToRemove, processCaptureRequest_cb _hidl_cb) override;
    ::android::hardware::Return<void> getCaptureRequestMetadataQueue(getCaptureRequestMetadataQueue_cb _hidl_cb) override;
    ::android::hardware::Return<void> getCaptureResultMetadataQueue(getCaptureResultMetadataQueue_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> flush() override;
    ::android::hardware::Return<void> close() override;

    // Methods from ::android::hardware::camera::device::V3_3::ICameraDeviceSession follow.
    ::android::hardware::Return<void> configureStreams_3_3(const ::android::hardware::camera::device::V3_2::StreamConfiguration& requestedConfiguration, configureStreams_3_3_cb _hidl_cb) override;

    // Methods from ::android::hardware::camera::device::V3_4::ICameraDeviceSession follow.
    ::android::hardware::Return<void> configureStreams_3_4(const ::android::hardware::camera::device::V3_4::StreamConfiguration& requestedConfiguration, configureStreams_3_4_cb _hidl_cb) override;
    ::android::hardware::Return<void> processCaptureRequest_3_4(const ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_4::CaptureRequest>& requests, const ::android::hardware::hidl_vec<::android::hardware::camera::device::V3_2::BufferCache>& cachesToRemove, processCaptureRequest_3_4_cb _hidl_cb) override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V3_4_BPHWCAMERADEVICESESSION_H
