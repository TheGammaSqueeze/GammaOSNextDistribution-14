#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V1_0_BPHWCAMERADEVICECALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V1_0_BPHWCAMERADEVICECALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/camera/device/1.0/IHwCameraDeviceCallback.h>

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace V1_0 {

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

    // Methods from ::android::hardware::camera::device::V1_0::ICameraDeviceCallback follow.
    static ::android::hardware::Return<void>  _hidl_notifyCallback(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::camera::device::V1_0::NotifyCallbackMsg msgType, int32_t ext1, int32_t ext2);
    static ::android::hardware::Return<uint32_t>  _hidl_registerMemory(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_handle& descriptor, uint32_t bufferSize, uint32_t bufferCount);
    static ::android::hardware::Return<void>  _hidl_unregisterMemory(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t memId);
    static ::android::hardware::Return<void>  _hidl_dataCallback(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::camera::device::V1_0::DataCallbackMsg msgType, uint32_t data, uint32_t bufferIndex, const ::android::hardware::camera::device::V1_0::CameraFrameMetadata& metadata);
    static ::android::hardware::Return<void>  _hidl_dataCallbackTimestamp(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::camera::device::V1_0::DataCallbackMsg msgType, uint32_t data, uint32_t bufferIndex, int64_t timestamp);
    static ::android::hardware::Return<void>  _hidl_handleCallbackTimestamp(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::camera::device::V1_0::DataCallbackMsg msgType, const ::android::hardware::hidl_handle& frameData, uint32_t data, uint32_t bufferIndex, int64_t timestamp);
    static ::android::hardware::Return<void>  _hidl_handleCallbackTimestampBatch(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::camera::device::V1_0::DataCallbackMsg msgType, const ::android::hardware::hidl_vec<::android::hardware::camera::device::V1_0::HandleTimestampMessage>& batch);

    // Methods from ::android::hardware::camera::device::V1_0::ICameraDeviceCallback follow.
    ::android::hardware::Return<void> notifyCallback(::android::hardware::camera::device::V1_0::NotifyCallbackMsg msgType, int32_t ext1, int32_t ext2) override;
    ::android::hardware::Return<uint32_t> registerMemory(const ::android::hardware::hidl_handle& descriptor, uint32_t bufferSize, uint32_t bufferCount) override;
    ::android::hardware::Return<void> unregisterMemory(uint32_t memId) override;
    ::android::hardware::Return<void> dataCallback(::android::hardware::camera::device::V1_0::DataCallbackMsg msgType, uint32_t data, uint32_t bufferIndex, const ::android::hardware::camera::device::V1_0::CameraFrameMetadata& metadata) override;
    ::android::hardware::Return<void> dataCallbackTimestamp(::android::hardware::camera::device::V1_0::DataCallbackMsg msgType, uint32_t data, uint32_t bufferIndex, int64_t timestamp) override;
    ::android::hardware::Return<void> handleCallbackTimestamp(::android::hardware::camera::device::V1_0::DataCallbackMsg msgType, const ::android::hardware::hidl_handle& frameData, uint32_t data, uint32_t bufferIndex, int64_t timestamp) override;
    ::android::hardware::Return<void> handleCallbackTimestampBatch(::android::hardware::camera::device::V1_0::DataCallbackMsg msgType, const ::android::hardware::hidl_vec<::android::hardware::camera::device::V1_0::HandleTimestampMessage>& batch) override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V1_0_BPHWCAMERADEVICECALLBACK_H
