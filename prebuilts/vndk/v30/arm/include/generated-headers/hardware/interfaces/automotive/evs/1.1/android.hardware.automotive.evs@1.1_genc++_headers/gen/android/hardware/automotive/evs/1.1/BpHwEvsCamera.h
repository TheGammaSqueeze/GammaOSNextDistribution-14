#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_BPHWEVSCAMERA_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_BPHWEVSCAMERA_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/automotive/evs/1.1/IHwEvsCamera.h>

namespace android {
namespace hardware {
namespace automotive {
namespace evs {
namespace V1_1 {

struct BpHwEvsCamera : public ::android::hardware::BpInterface<IEvsCamera>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwEvsCamera(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IEvsCamera Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::automotive::evs::V1_1::IEvsCamera follow.
    static ::android::hardware::Return<void>  _hidl_getCameraInfo_1_1(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getCameraInfo_1_1_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getPhysicalCameraInfo(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& deviceId, getPhysicalCameraInfo_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult>  _hidl_pauseVideoStream(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult>  _hidl_resumeVideoStream(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult>  _hidl_doneWithFrame_1_1(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::automotive::evs::V1_1::BufferDesc>& buffer);
    static ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult>  _hidl_setMaster(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult>  _hidl_forceMaster(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::automotive::evs::V1_0::IEvsDisplay>& display);
    static ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult>  _hidl_unsetMaster(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_getParameterList(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getParameterList_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getIntParameterRange(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::automotive::evs::V1_1::CameraParam id, getIntParameterRange_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setIntParameter(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::automotive::evs::V1_1::CameraParam id, int32_t value, setIntParameter_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getIntParameter(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::automotive::evs::V1_1::CameraParam id, getIntParameter_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getExtendedInfo_1_1(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t opaqueIdentifier, getExtendedInfo_1_1_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult>  _hidl_setExtendedInfo_1_1(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t opaqueIdentifier, const ::android::hardware::hidl_vec<uint8_t>& opaqueValue);
    static ::android::hardware::Return<void>  _hidl_importExternalBuffers(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::automotive::evs::V1_1::BufferDesc>& buffers, importExternalBuffers_cb _hidl_cb);

    // Methods from ::android::hardware::automotive::evs::V1_0::IEvsCamera follow.
    ::android::hardware::Return<void> getCameraInfo(getCameraInfo_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> setMaxFramesInFlight(uint32_t bufferCount) override;
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> startVideoStream(const ::android::sp<::android::hardware::automotive::evs::V1_0::IEvsCameraStream>& receiver) override;
    ::android::hardware::Return<void> doneWithFrame(const ::android::hardware::automotive::evs::V1_0::BufferDesc& buffer) override;
    ::android::hardware::Return<void> stopVideoStream() override;
    ::android::hardware::Return<int32_t> getExtendedInfo(uint32_t opaqueIdentifier) override;
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> setExtendedInfo(uint32_t opaqueIdentifier, int32_t opaqueValue) override;

    // Methods from ::android::hardware::automotive::evs::V1_1::IEvsCamera follow.
    ::android::hardware::Return<void> getCameraInfo_1_1(getCameraInfo_1_1_cb _hidl_cb) override;
    ::android::hardware::Return<void> getPhysicalCameraInfo(const ::android::hardware::hidl_string& deviceId, getPhysicalCameraInfo_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> pauseVideoStream() override;
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> resumeVideoStream() override;
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> doneWithFrame_1_1(const ::android::hardware::hidl_vec<::android::hardware::automotive::evs::V1_1::BufferDesc>& buffer) override;
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> setMaster() override;
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> forceMaster(const ::android::sp<::android::hardware::automotive::evs::V1_0::IEvsDisplay>& display) override;
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> unsetMaster() override;
    ::android::hardware::Return<void> getParameterList(getParameterList_cb _hidl_cb) override;
    ::android::hardware::Return<void> getIntParameterRange(::android::hardware::automotive::evs::V1_1::CameraParam id, getIntParameterRange_cb _hidl_cb) override;
    ::android::hardware::Return<void> setIntParameter(::android::hardware::automotive::evs::V1_1::CameraParam id, int32_t value, setIntParameter_cb _hidl_cb) override;
    ::android::hardware::Return<void> getIntParameter(::android::hardware::automotive::evs::V1_1::CameraParam id, getIntParameter_cb _hidl_cb) override;
    ::android::hardware::Return<void> getExtendedInfo_1_1(uint32_t opaqueIdentifier, getExtendedInfo_1_1_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> setExtendedInfo_1_1(uint32_t opaqueIdentifier, const ::android::hardware::hidl_vec<uint8_t>& opaqueValue) override;
    ::android::hardware::Return<void> importExternalBuffers(const ::android::hardware::hidl_vec<::android::hardware::automotive::evs::V1_1::BufferDesc>& buffers, importExternalBuffers_cb _hidl_cb) override;

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

}  // namespace V1_1
}  // namespace evs
}  // namespace automotive
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_BPHWEVSCAMERA_H
