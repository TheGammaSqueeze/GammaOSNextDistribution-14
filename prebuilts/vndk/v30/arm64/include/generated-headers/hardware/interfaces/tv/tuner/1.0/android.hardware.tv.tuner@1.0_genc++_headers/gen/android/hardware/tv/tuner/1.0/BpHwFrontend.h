#ifndef HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_BPHWFRONTEND_H
#define HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_BPHWFRONTEND_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/tv/tuner/1.0/IHwFrontend.h>

namespace android {
namespace hardware {
namespace tv {
namespace tuner {
namespace V1_0 {

struct BpHwFrontend : public ::android::hardware::BpInterface<IFrontend>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwFrontend(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IFrontend Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::tv::tuner::V1_0::IFrontend follow.
    static ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result>  _hidl_setCallback(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::tv::tuner::V1_0::IFrontendCallback>& callback);
    static ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result>  _hidl_tune(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::tv::tuner::V1_0::FrontendSettings& settings);
    static ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result>  _hidl_stopTune(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result>  _hidl_close(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result>  _hidl_scan(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::tv::tuner::V1_0::FrontendSettings& settings, ::android::hardware::tv::tuner::V1_0::FrontendScanType type);
    static ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result>  _hidl_stopScan(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_getStatus(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::tv::tuner::V1_0::FrontendStatusType>& statusTypes, getStatus_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result>  _hidl_setLnb(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t lnbId);
    static ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result>  _hidl_setLna(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, bool bEnable);

    // Methods from ::android::hardware::tv::tuner::V1_0::IFrontend follow.
    ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> setCallback(const ::android::sp<::android::hardware::tv::tuner::V1_0::IFrontendCallback>& callback) override;
    ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> tune(const ::android::hardware::tv::tuner::V1_0::FrontendSettings& settings) override;
    ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> stopTune() override;
    ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> close() override;
    ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> scan(const ::android::hardware::tv::tuner::V1_0::FrontendSettings& settings, ::android::hardware::tv::tuner::V1_0::FrontendScanType type) override;
    ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> stopScan() override;
    ::android::hardware::Return<void> getStatus(const ::android::hardware::hidl_vec<::android::hardware::tv::tuner::V1_0::FrontendStatusType>& statusTypes, getStatus_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> setLnb(uint32_t lnbId) override;
    ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> setLna(bool bEnable) override;

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
}  // namespace tuner
}  // namespace tv
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_BPHWFRONTEND_H
