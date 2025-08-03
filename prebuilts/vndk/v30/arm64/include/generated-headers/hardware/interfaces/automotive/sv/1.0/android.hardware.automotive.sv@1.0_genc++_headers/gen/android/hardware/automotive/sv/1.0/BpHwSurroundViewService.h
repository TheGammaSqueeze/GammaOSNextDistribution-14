#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_SV_V1_0_BPHWSURROUNDVIEWSERVICE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_SV_V1_0_BPHWSURROUNDVIEWSERVICE_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/automotive/sv/1.0/IHwSurroundViewService.h>

namespace android {
namespace hardware {
namespace automotive {
namespace sv {
namespace V1_0 {

struct BpHwSurroundViewService : public ::android::hardware::BpInterface<ISurroundViewService>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwSurroundViewService(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ISurroundViewService Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::automotive::sv::V1_0::ISurroundViewService follow.
    static ::android::hardware::Return<void>  _hidl_getCameraIds(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getCameraIds_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_start2dSession(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, start2dSession_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::automotive::sv::V1_0::SvResult>  _hidl_stop2dSession(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::automotive::sv::V1_0::ISurroundView2dSession>& sv2dSession);
    static ::android::hardware::Return<void>  _hidl_start3dSession(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, start3dSession_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::automotive::sv::V1_0::SvResult>  _hidl_stop3dSession(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::automotive::sv::V1_0::ISurroundView3dSession>& sv3dSession);

    // Methods from ::android::hardware::automotive::sv::V1_0::ISurroundViewService follow.
    ::android::hardware::Return<void> getCameraIds(getCameraIds_cb _hidl_cb) override;
    ::android::hardware::Return<void> start2dSession(start2dSession_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::automotive::sv::V1_0::SvResult> stop2dSession(const ::android::sp<::android::hardware::automotive::sv::V1_0::ISurroundView2dSession>& sv2dSession) override;
    ::android::hardware::Return<void> start3dSession(start3dSession_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::automotive::sv::V1_0::SvResult> stop3dSession(const ::android::sp<::android::hardware::automotive::sv::V1_0::ISurroundView3dSession>& sv3dSession) override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_SV_V1_0_BPHWSURROUNDVIEWSERVICE_H
