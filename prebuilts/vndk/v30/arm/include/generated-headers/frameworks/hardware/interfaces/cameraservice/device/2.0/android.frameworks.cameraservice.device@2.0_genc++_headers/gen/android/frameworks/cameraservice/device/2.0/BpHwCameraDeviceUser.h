#ifndef HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_DEVICE_V2_0_BPHWCAMERADEVICEUSER_H
#define HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_DEVICE_V2_0_BPHWCAMERADEVICEUSER_H

#include <hidl/HidlTransportSupport.h>

#include <android/frameworks/cameraservice/device/2.0/IHwCameraDeviceUser.h>

namespace android {
namespace frameworks {
namespace cameraservice {
namespace device {
namespace V2_0 {

struct BpHwCameraDeviceUser : public ::android::hardware::BpInterface<ICameraDeviceUser>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwCameraDeviceUser(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ICameraDeviceUser Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::frameworks::cameraservice::device::V2_0::ICameraDeviceUser follow.
    static ::android::hardware::Return<void>  _hidl_disconnect(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_getCaptureRequestMetadataQueue(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getCaptureRequestMetadataQueue_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getCaptureResultMetadataQueue(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getCaptureResultMetadataQueue_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_submitRequestList(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::frameworks::cameraservice::device::V2_0::CaptureRequest>& requestList, bool isRepeating, submitRequestList_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_cancelRepeatingRequest(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, cancelRepeatingRequest_cb _hidl_cb);
    static ::android::hardware::Return<::android::frameworks::cameraservice::common::V2_0::Status>  _hidl_beginConfigure(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::frameworks::cameraservice::common::V2_0::Status>  _hidl_endConfigure(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode operatingMode, const ::android::hardware::hidl_vec<uint8_t>& sessionParams);
    static ::android::hardware::Return<::android::frameworks::cameraservice::common::V2_0::Status>  _hidl_deleteStream(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t streamId);
    static ::android::hardware::Return<void>  _hidl_createStream(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration& outputConfiguration, createStream_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_createDefaultRequest(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::frameworks::cameraservice::device::V2_0::TemplateId templateId, createDefaultRequest_cb _hidl_cb);
    static ::android::hardware::Return<::android::frameworks::cameraservice::common::V2_0::Status>  _hidl_waitUntilIdle(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_flush(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, flush_cb _hidl_cb);
    static ::android::hardware::Return<::android::frameworks::cameraservice::common::V2_0::Status>  _hidl_updateOutputConfiguration(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t streamId, const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration& outputConfiguration);
    static ::android::hardware::Return<void>  _hidl_isSessionConfigurationSupported(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::frameworks::cameraservice::device::V2_0::SessionConfiguration& sessionConfiguration, isSessionConfigurationSupported_cb _hidl_cb);

    // Methods from ::android::frameworks::cameraservice::device::V2_0::ICameraDeviceUser follow.
    ::android::hardware::Return<void> disconnect() override;
    ::android::hardware::Return<void> getCaptureRequestMetadataQueue(getCaptureRequestMetadataQueue_cb _hidl_cb) override;
    ::android::hardware::Return<void> getCaptureResultMetadataQueue(getCaptureResultMetadataQueue_cb _hidl_cb) override;
    ::android::hardware::Return<void> submitRequestList(const ::android::hardware::hidl_vec<::android::frameworks::cameraservice::device::V2_0::CaptureRequest>& requestList, bool isRepeating, submitRequestList_cb _hidl_cb) override;
    ::android::hardware::Return<void> cancelRepeatingRequest(cancelRepeatingRequest_cb _hidl_cb) override;
    ::android::hardware::Return<::android::frameworks::cameraservice::common::V2_0::Status> beginConfigure() override;
    ::android::hardware::Return<::android::frameworks::cameraservice::common::V2_0::Status> endConfigure(::android::frameworks::cameraservice::device::V2_0::StreamConfigurationMode operatingMode, const ::android::hardware::hidl_vec<uint8_t>& sessionParams) override;
    ::android::hardware::Return<::android::frameworks::cameraservice::common::V2_0::Status> deleteStream(int32_t streamId) override;
    ::android::hardware::Return<void> createStream(const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration& outputConfiguration, createStream_cb _hidl_cb) override;
    ::android::hardware::Return<void> createDefaultRequest(::android::frameworks::cameraservice::device::V2_0::TemplateId templateId, createDefaultRequest_cb _hidl_cb) override;
    ::android::hardware::Return<::android::frameworks::cameraservice::common::V2_0::Status> waitUntilIdle() override;
    ::android::hardware::Return<void> flush(flush_cb _hidl_cb) override;
    ::android::hardware::Return<::android::frameworks::cameraservice::common::V2_0::Status> updateOutputConfiguration(int32_t streamId, const ::android::frameworks::cameraservice::device::V2_0::OutputConfiguration& outputConfiguration) override;
    ::android::hardware::Return<void> isSessionConfigurationSupported(const ::android::frameworks::cameraservice::device::V2_0::SessionConfiguration& sessionConfiguration, isSessionConfigurationSupported_cb _hidl_cb) override;

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

#endif  // HIDL_GENERATED_ANDROID_FRAMEWORKS_CAMERASERVICE_DEVICE_V2_0_BPHWCAMERADEVICEUSER_H
