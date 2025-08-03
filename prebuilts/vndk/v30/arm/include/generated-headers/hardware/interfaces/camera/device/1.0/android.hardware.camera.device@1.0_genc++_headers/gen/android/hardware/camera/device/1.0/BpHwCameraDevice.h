#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V1_0_BPHWCAMERADEVICE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V1_0_BPHWCAMERADEVICE_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/camera/device/1.0/IHwCameraDevice.h>

namespace android {
namespace hardware {
namespace camera {
namespace device {
namespace V1_0 {

struct BpHwCameraDevice : public ::android::hardware::BpInterface<ICameraDevice>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwCameraDevice(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ICameraDevice Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::camera::device::V1_0::ICameraDevice follow.
    static ::android::hardware::Return<void>  _hidl_getResourceCost(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getResourceCost_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getCameraInfo(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getCameraInfo_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status>  _hidl_setTorchMode(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::camera::common::V1_0::TorchMode mode);
    static ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status>  _hidl_dumpState(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_handle& fd);
    static ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status>  _hidl_open(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::camera::device::V1_0::ICameraDeviceCallback>& callback);
    static ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status>  _hidl_setPreviewWindow(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::camera::device::V1_0::ICameraDevicePreviewCallback>& window);
    static ::android::hardware::Return<void>  _hidl_enableMsgType(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::hidl_bitfield<::android::hardware::camera::device::V1_0::FrameCallbackFlag> msgType);
    static ::android::hardware::Return<void>  _hidl_disableMsgType(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::hidl_bitfield<::android::hardware::camera::device::V1_0::FrameCallbackFlag> msgType);
    static ::android::hardware::Return<bool>  _hidl_msgTypeEnabled(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::hidl_bitfield<::android::hardware::camera::device::V1_0::FrameCallbackFlag> msgType);
    static ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status>  _hidl_startPreview(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_stopPreview(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<bool>  _hidl_previewEnabled(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status>  _hidl_storeMetaDataInBuffers(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, bool enable);
    static ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status>  _hidl_startRecording(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_stopRecording(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<bool>  _hidl_recordingEnabled(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_releaseRecordingFrame(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t memId, uint32_t bufferIndex);
    static ::android::hardware::Return<void>  _hidl_releaseRecordingFrameHandle(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t memId, uint32_t bufferIndex, const ::android::hardware::hidl_handle& frame);
    static ::android::hardware::Return<void>  _hidl_releaseRecordingFrameHandleBatch(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::camera::device::V1_0::VideoFrameMessage>& batch);
    static ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status>  _hidl_autoFocus(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status>  _hidl_cancelAutoFocus(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status>  _hidl_takePicture(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status>  _hidl_cancelPicture(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status>  _hidl_setParameters(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& params);
    static ::android::hardware::Return<void>  _hidl_getParameters(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getParameters_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status>  _hidl_sendCommand(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::camera::device::V1_0::CommandType cmd, int32_t arg1, int32_t arg2);
    static ::android::hardware::Return<void>  _hidl_close(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);

    // Methods from ::android::hardware::camera::device::V1_0::ICameraDevice follow.
    ::android::hardware::Return<void> getResourceCost(getResourceCost_cb _hidl_cb) override;
    ::android::hardware::Return<void> getCameraInfo(getCameraInfo_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> setTorchMode(::android::hardware::camera::common::V1_0::TorchMode mode) override;
    ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> dumpState(const ::android::hardware::hidl_handle& fd) override;
    ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> open(const ::android::sp<::android::hardware::camera::device::V1_0::ICameraDeviceCallback>& callback) override;
    ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> setPreviewWindow(const ::android::sp<::android::hardware::camera::device::V1_0::ICameraDevicePreviewCallback>& window) override;
    ::android::hardware::Return<void> enableMsgType(::android::hardware::hidl_bitfield<::android::hardware::camera::device::V1_0::FrameCallbackFlag> msgType) override;
    ::android::hardware::Return<void> disableMsgType(::android::hardware::hidl_bitfield<::android::hardware::camera::device::V1_0::FrameCallbackFlag> msgType) override;
    ::android::hardware::Return<bool> msgTypeEnabled(::android::hardware::hidl_bitfield<::android::hardware::camera::device::V1_0::FrameCallbackFlag> msgType) override;
    ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> startPreview() override;
    ::android::hardware::Return<void> stopPreview() override;
    ::android::hardware::Return<bool> previewEnabled() override;
    ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> storeMetaDataInBuffers(bool enable) override;
    ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> startRecording() override;
    ::android::hardware::Return<void> stopRecording() override;
    ::android::hardware::Return<bool> recordingEnabled() override;
    ::android::hardware::Return<void> releaseRecordingFrame(uint32_t memId, uint32_t bufferIndex) override;
    ::android::hardware::Return<void> releaseRecordingFrameHandle(uint32_t memId, uint32_t bufferIndex, const ::android::hardware::hidl_handle& frame) override;
    ::android::hardware::Return<void> releaseRecordingFrameHandleBatch(const ::android::hardware::hidl_vec<::android::hardware::camera::device::V1_0::VideoFrameMessage>& batch) override;
    ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> autoFocus() override;
    ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> cancelAutoFocus() override;
    ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> takePicture() override;
    ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> cancelPicture() override;
    ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> setParameters(const ::android::hardware::hidl_string& params) override;
    ::android::hardware::Return<void> getParameters(getParameters_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::camera::common::V1_0::Status> sendCommand(::android::hardware::camera::device::V1_0::CommandType cmd, int32_t arg1, int32_t arg2) override;
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

}  // namespace V1_0
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAMERA_DEVICE_V1_0_BPHWCAMERADEVICE_H
