#ifndef HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_BPHWDEMUX_H
#define HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_BPHWDEMUX_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/tv/tuner/1.0/IHwDemux.h>

namespace android {
namespace hardware {
namespace tv {
namespace tuner {
namespace V1_0 {

struct BpHwDemux : public ::android::hardware::BpInterface<IDemux>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwDemux(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IDemux Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::tv::tuner::V1_0::IDemux follow.
    static ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result>  _hidl_setFrontendDataSource(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t frontendId);
    static ::android::hardware::Return<void>  _hidl_openFilter(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::tv::tuner::V1_0::DemuxFilterType& type, uint32_t bufferSize, const ::android::sp<::android::hardware::tv::tuner::V1_0::IFilterCallback>& cb, openFilter_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_openTimeFilter(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, openTimeFilter_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getAvSyncHwId(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::tv::tuner::V1_0::IFilter>& filter, getAvSyncHwId_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getAvSyncTime(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t avSyncHwId, getAvSyncTime_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result>  _hidl_close(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_openDvr(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::tv::tuner::V1_0::DvrType type, uint32_t bufferSize, const ::android::sp<::android::hardware::tv::tuner::V1_0::IDvrCallback>& cb, openDvr_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result>  _hidl_connectCiCam(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t ciCamId);
    static ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result>  _hidl_disconnectCiCam(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);

    // Methods from ::android::hardware::tv::tuner::V1_0::IDemux follow.
    ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> setFrontendDataSource(uint32_t frontendId) override;
    ::android::hardware::Return<void> openFilter(const ::android::hardware::tv::tuner::V1_0::DemuxFilterType& type, uint32_t bufferSize, const ::android::sp<::android::hardware::tv::tuner::V1_0::IFilterCallback>& cb, openFilter_cb _hidl_cb) override;
    ::android::hardware::Return<void> openTimeFilter(openTimeFilter_cb _hidl_cb) override;
    ::android::hardware::Return<void> getAvSyncHwId(const ::android::sp<::android::hardware::tv::tuner::V1_0::IFilter>& filter, getAvSyncHwId_cb _hidl_cb) override;
    ::android::hardware::Return<void> getAvSyncTime(uint32_t avSyncHwId, getAvSyncTime_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> close() override;
    ::android::hardware::Return<void> openDvr(::android::hardware::tv::tuner::V1_0::DvrType type, uint32_t bufferSize, const ::android::sp<::android::hardware::tv::tuner::V1_0::IDvrCallback>& cb, openDvr_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> connectCiCam(uint32_t ciCamId) override;
    ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> disconnectCiCam() override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_BPHWDEMUX_H
