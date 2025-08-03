#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_0_BPHWEVSCAMERA_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_0_BPHWEVSCAMERA_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/automotive/evs/1.0/IHwEvsCamera.h>

namespace android {
namespace hardware {
namespace automotive {
namespace evs {
namespace V1_0 {

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

    // Methods from ::android::hardware::automotive::evs::V1_0::IEvsCamera follow.
    static ::android::hardware::Return<void>  _hidl_getCameraInfo(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getCameraInfo_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult>  _hidl_setMaxFramesInFlight(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t bufferCount);
    static ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult>  _hidl_startVideoStream(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::automotive::evs::V1_0::IEvsCameraStream>& receiver);
    static ::android::hardware::Return<void>  _hidl_doneWithFrame(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::automotive::evs::V1_0::BufferDesc& buffer);
    static ::android::hardware::Return<void>  _hidl_stopVideoStream(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<int32_t>  _hidl_getExtendedInfo(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t opaqueIdentifier);
    static ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult>  _hidl_setExtendedInfo(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t opaqueIdentifier, int32_t opaqueValue);

    // Methods from ::android::hardware::automotive::evs::V1_0::IEvsCamera follow.
    ::android::hardware::Return<void> getCameraInfo(getCameraInfo_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> setMaxFramesInFlight(uint32_t bufferCount) override;
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> startVideoStream(const ::android::sp<::android::hardware::automotive::evs::V1_0::IEvsCameraStream>& receiver) override;
    ::android::hardware::Return<void> doneWithFrame(const ::android::hardware::automotive::evs::V1_0::BufferDesc& buffer) override;
    ::android::hardware::Return<void> stopVideoStream() override;
    ::android::hardware::Return<int32_t> getExtendedInfo(uint32_t opaqueIdentifier) override;
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> setExtendedInfo(uint32_t opaqueIdentifier, int32_t opaqueValue) override;

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
}  // namespace evs
}  // namespace automotive
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_0_BPHWEVSCAMERA_H
