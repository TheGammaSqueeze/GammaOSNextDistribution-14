#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V1_0_BPHWCAMERADEVICEPREVIEWCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V1_0_BPHWCAMERADEVICEPREVIEWCALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/camera/device/1.0/IHwCameraDevicePreviewCallback.h>

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace V1_0 {

struct BpHwCameraDevicePreviewCallback : public ::android::hardware::BpInterface<ICameraDevicePreviewCallback>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwCameraDevicePreviewCallback(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ICameraDevicePreviewCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::camera::device::V1_0::ICameraDevicePreviewCallback follow.
    static ::android::hardware::Return<void>  _hidl_dequeueBuffer(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, dequeueBuffer_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status>  _hidl_enqueueBuffer(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t bufferId);
    static ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status>  _hidl_cancelBuffer(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t bufferId);
    static ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status>  _hidl_setBufferCount(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t count);
    static ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status>  _hidl_setBuffersGeometry(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t w, uint32_t h, ::android::hardware::graphics::common::V1_0::PixelFormat format);
    static ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status>  _hidl_setCrop(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t left, int32_t top, int32_t right, int32_t bottom);
    static ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status>  _hidl_setUsage(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::graphics::common::V1_0::BufferUsage usage);
    static ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status>  _hidl_setSwapInterval(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t interval);
    static ::android::hardware::Return<void>  _hidl_getMinUndequeuedBufferCount(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getMinUndequeuedBufferCount_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status>  _hidl_setTimestamp(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int64_t timestamp);

    // Methods from ::android::hardware::camera::device::V1_0::ICameraDevicePreviewCallback follow.
    ::android::hardware::Return<void> dequeueBuffer(dequeueBuffer_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> enqueueBuffer(uint64_t bufferId) override;
    ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> cancelBuffer(uint64_t bufferId) override;
    ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> setBufferCount(uint32_t count) override;
    ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> setBuffersGeometry(uint32_t w, uint32_t h, ::android::hardware::graphics::common::V1_0::PixelFormat format) override;
    ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> setCrop(int32_t left, int32_t top, int32_t right, int32_t bottom) override;
    ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> setUsage(::android::hardware::graphics::common::V1_0::BufferUsage usage) override;
    ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> setSwapInterval(int32_t interval) override;
    ::android::hardware::Return<void> getMinUndequeuedBufferCount(getMinUndequeuedBufferCount_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> setTimestamp(int64_t timestamp) override;

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

}  // namespace V1_0
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V1_0_BPHWCAMERADEVICEPREVIEWCALLBACK_H
