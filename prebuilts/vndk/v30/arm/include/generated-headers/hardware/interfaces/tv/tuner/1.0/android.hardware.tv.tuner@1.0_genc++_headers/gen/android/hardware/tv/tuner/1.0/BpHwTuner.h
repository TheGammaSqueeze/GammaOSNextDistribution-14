#ifndef HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_BPHWTUNER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_BPHWTUNER_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/tv/tuner/1.0/IHwTuner.h>

namespace android {
namespace hardware {
namespace tv {
namespace tuner {
namespace V1_0 {

struct BpHwTuner : public ::android::hardware::BpInterface<ITuner>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwTuner(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ITuner Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::tv::tuner::V1_0::ITuner follow.
    static ::android::hardware::Return<void>  _hidl_getFrontendIds(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getFrontendIds_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_openFrontendById(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t frontendId, openFrontendById_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_openDemux(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, openDemux_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getDemuxCaps(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getDemuxCaps_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_openDescrambler(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, openDescrambler_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getFrontendInfo(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t frontendId, getFrontendInfo_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getLnbIds(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getLnbIds_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_openLnbById(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t lnbId, openLnbById_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_openLnbByName(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& lnbName, openLnbByName_cb _hidl_cb);

    // Methods from ::android::hardware::tv::tuner::V1_0::ITuner follow.
    ::android::hardware::Return<void> getFrontendIds(getFrontendIds_cb _hidl_cb) override;
    ::android::hardware::Return<void> openFrontendById(uint32_t frontendId, openFrontendById_cb _hidl_cb) override;
    ::android::hardware::Return<void> openDemux(openDemux_cb _hidl_cb) override;
    ::android::hardware::Return<void> getDemuxCaps(getDemuxCaps_cb _hidl_cb) override;
    ::android::hardware::Return<void> openDescrambler(openDescrambler_cb _hidl_cb) override;
    ::android::hardware::Return<void> getFrontendInfo(uint32_t frontendId, getFrontendInfo_cb _hidl_cb) override;
    ::android::hardware::Return<void> getLnbIds(getLnbIds_cb _hidl_cb) override;
    ::android::hardware::Return<void> openLnbById(uint32_t lnbId, openLnbById_cb _hidl_cb) override;
    ::android::hardware::Return<void> openLnbByName(const ::android::hardware::hidl_string& lnbName, openLnbByName_cb _hidl_cb) override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_BPHWTUNER_H
