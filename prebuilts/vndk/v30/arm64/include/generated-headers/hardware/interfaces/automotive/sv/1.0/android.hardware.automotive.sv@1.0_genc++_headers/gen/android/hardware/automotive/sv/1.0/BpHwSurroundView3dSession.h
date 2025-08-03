#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_SV_V1_0_BPHWSURROUNDVIEW3DSESSION_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_SV_V1_0_BPHWSURROUNDVIEW3DSESSION_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/automotive/sv/1.0/IHwSurroundView3dSession.h>

namespace android {
namespace hardware {
namespace automotive {
namespace sv {
namespace V1_0 {

struct BpHwSurroundView3dSession : public ::android::hardware::BpInterface<ISurroundView3dSession>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwSurroundView3dSession(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ISurroundView3dSession Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::automotive::sv::V1_0::ISurroundView3dSession follow.
    static ::android::hardware::Return<::android::hardware::automotive::sv::V1_0::SvResult>  _hidl_setViews(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::automotive::sv::V1_0::View3d>& views);
    static ::android::hardware::Return<::android::hardware::automotive::sv::V1_0::SvResult>  _hidl_set3dConfig(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::automotive::sv::V1_0::Sv3dConfig& sv3dConfig);
    static ::android::hardware::Return<void>  _hidl_get3dConfig(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, get3dConfig_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::automotive::sv::V1_0::SvResult>  _hidl_updateOverlays(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::automotive::sv::V1_0::OverlaysData& overlaysData);
    static ::android::hardware::Return<void>  _hidl_projectCameraPointsTo3dSurface(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::automotive::sv::V1_0::Point2dInt>& cameraPoints, const ::android::hardware::hidl_string& cameraId, projectCameraPointsTo3dSurface_cb _hidl_cb);

    // Methods from ::android::hardware::automotive::sv::V1_0::ISurroundViewSession follow.
    ::android::hardware::Return<::android::hardware::automotive::sv::V1_0::SvResult> startStream(const ::android::sp<::android::hardware::automotive::sv::V1_0::ISurroundViewStream>& stream) override;
    ::android::hardware::Return<void> stopStream() override;
    ::android::hardware::Return<void> doneWithFrames(const ::android::hardware::automotive::sv::V1_0::SvFramesDesc& svFramesDesc) override;

    // Methods from ::android::hardware::automotive::sv::V1_0::ISurroundView3dSession follow.
    ::android::hardware::Return<::android::hardware::automotive::sv::V1_0::SvResult> setViews(const ::android::hardware::hidl_vec<::android::hardware::automotive::sv::V1_0::View3d>& views) override;
    ::android::hardware::Return<::android::hardware::automotive::sv::V1_0::SvResult> set3dConfig(const ::android::hardware::automotive::sv::V1_0::Sv3dConfig& sv3dConfig) override;
    ::android::hardware::Return<void> get3dConfig(get3dConfig_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::automotive::sv::V1_0::SvResult> updateOverlays(const ::android::hardware::automotive::sv::V1_0::OverlaysData& overlaysData) override;
    ::android::hardware::Return<void> projectCameraPointsTo3dSurface(const ::android::hardware::hidl_vec<::android::hardware::automotive::sv::V1_0::Point2dInt>& cameraPoints, const ::android::hardware::hidl_string& cameraId, projectCameraPointsTo3dSurface_cb _hidl_cb) override;

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
}  // namespace sv
}  // namespace automotive
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_SV_V1_0_BPHWSURROUNDVIEW3DSESSION_H
