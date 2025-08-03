#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_SV_V1_0_BPHWSURROUNDVIEW2DSESSION_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_SV_V1_0_BPHWSURROUNDVIEW2DSESSION_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/automotive/sv/1.0/IHwSurroundView2dSession.h>

namespace android {
namespace hardware {
namespace automotive {
namespace sv {
namespace V1_0 {

struct BpHwSurroundView2dSession : public ::android::hardware::BpInterface<ISurroundView2dSession>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwSurroundView2dSession(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ISurroundView2dSession Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::automotive::sv::V1_0::ISurroundView2dSession follow.
    static ::android::hardware::Return<void>  _hidl_get2dMappingInfo(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, get2dMappingInfo_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::automotive::sv::V1_0::SvResult>  _hidl_set2dConfig(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::automotive::sv::V1_0::Sv2dConfig& sv2dConfig);
    static ::android::hardware::Return<void>  _hidl_get2dConfig(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, get2dConfig_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_projectCameraPoints(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::automotive::sv::V1_0::Point2dInt>& cameraPoints, const ::android::hardware::hidl_string& cameraId, projectCameraPoints_cb _hidl_cb);

    // Methods from ::android::hardware::automotive::sv::V1_0::ISurroundViewSession follow.
    ::android::hardware::Return<::android::hardware::automotive::sv::V1_0::SvResult> startStream(const ::android::sp<::android::hardware::automotive::sv::V1_0::ISurroundViewStream>& stream) override;
    ::android::hardware::Return<void> stopStream() override;
    ::android::hardware::Return<void> doneWithFrames(const ::android::hardware::automotive::sv::V1_0::SvFramesDesc& svFramesDesc) override;

    // Methods from ::android::hardware::automotive::sv::V1_0::ISurroundView2dSession follow.
    ::android::hardware::Return<void> get2dMappingInfo(get2dMappingInfo_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::automotive::sv::V1_0::SvResult> set2dConfig(const ::android::hardware::automotive::sv::V1_0::Sv2dConfig& sv2dConfig) override;
    ::android::hardware::Return<void> get2dConfig(get2dConfig_cb _hidl_cb) override;
    ::android::hardware::Return<void> projectCameraPoints(const ::android::hardware::hidl_vec<::android::hardware::automotive::sv::V1_0::Point2dInt>& cameraPoints, const ::android::hardware::hidl_string& cameraId, projectCameraPoints_cb _hidl_cb) override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_SV_V1_0_BPHWSURROUNDVIEW2DSESSION_H
