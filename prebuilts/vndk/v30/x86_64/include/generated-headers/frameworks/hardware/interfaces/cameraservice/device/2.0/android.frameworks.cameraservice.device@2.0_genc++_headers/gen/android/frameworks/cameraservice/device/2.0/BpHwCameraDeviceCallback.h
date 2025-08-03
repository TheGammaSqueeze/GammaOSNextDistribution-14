#ifndef HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_DEVICE_V2_0_BPHWCAMERADEVICECALLBACK_H
#define HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_DEVICE_V2_0_BPHWCAMERADEVICECALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/frameworks/cameraservice/device/2.0/IHwCameraDeviceCallback.h>

namespace android {
namespace frameworks {
namespace cameraservice {
namespace device {
namespace V2_0 {

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

    // Methods from ::android::frameworks::cameraservice::device::V2_0::ICameraDeviceCallback follow.
    static ::android::hardware::Return<void>  _hidl_onDeviceError(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::frameworks::cameraservice::device::V2_0::ErrorCode errorCode, const ::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras& resultExtras);
    static ::android::hardware::Return<void>  _hidl_onDeviceIdle(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_onCaptureStarted(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras& resultExtras, uint64_t timestamp);
    static ::android::hardware::Return<void>  _hidl_onResultReceived(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata& result, const ::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras& resultExtras, const ::android::hardware::hidl_vec<::android::frameworks::cameraservice::device::V2_0::PhysicalCaptureResultInfo>& physicalCaptureResultInfos);
    static ::android::hardware::Return<void>  _hidl_onRepeatingRequestError(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t lastFrameNumber, int32_t repeatingRequestId);

    // Methods from ::android::frameworks::cameraservice::device::V2_0::ICameraDeviceCallback follow.
    ::android::hardware::Return<void> onDeviceError(::android::frameworks::cameraservice::device::V2_0::ErrorCode errorCode, const ::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras& resultExtras) override;
    ::android::hardware::Return<void> onDeviceIdle() override;
    ::android::hardware::Return<void> onCaptureStarted(const ::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras& resultExtras, uint64_t timestamp) override;
    ::android::hardware::Return<void> onResultReceived(const ::android::frameworks::cameraservice::device::V2_0::FmqSizeOrMetadata& result, const ::android::frameworks::cameraservice::device::V2_0::CaptureResultExtras& resultExtras, const ::android::hardware::hidl_vec<::android::frameworks::cameraservice::device::V2_0::PhysicalCaptureResultInfo>& physicalCaptureResultInfos) override;
    ::android::hardware::Return<void> onRepeatingRequestError(uint64_t lastFrameNumber, int32_t repeatingRequestId) override;

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

}  // namespace V2_0
}  // namespace device
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_DEVICE_V2_0_BPHWCAMERADEVICECALLBACK_H
