#ifndef HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_BPHWLNB_H
#define HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_BPHWLNB_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/tv/tuner/1.0/IHwLnb.h>

namespace android {
namespace hardware {
namespace tv {
namespace tuner {
namespace V1_0 {

struct BpHwLnb : public ::android::hardware::BpInterface<ILnb>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwLnb(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ILnb Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::tv::tuner::V1_0::ILnb follow.
    static ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result>  _hidl_setCallback(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::tv::tuner::V1_0::ILnbCallback>& callback);
    static ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result>  _hidl_setVoltage(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::tv::tuner::V1_0::LnbVoltage voltage);
    static ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result>  _hidl_setTone(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::tv::tuner::V1_0::LnbTone tone);
    static ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result>  _hidl_setSatellitePosition(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::tv::tuner::V1_0::LnbPosition position);
    static ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result>  _hidl_sendDiseqcMessage(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& diseqcMessage);
    static ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result>  _hidl_close(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);

    // Methods from ::android::hardware::tv::tuner::V1_0::ILnb follow.
    ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> setCallback(const ::android::sp<::android::hardware::tv::tuner::V1_0::ILnbCallback>& callback) override;
    ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> setVoltage(::android::hardware::tv::tuner::V1_0::LnbVoltage voltage) override;
    ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> setTone(::android::hardware::tv::tuner::V1_0::LnbTone tone) override;
    ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> setSatellitePosition(::android::hardware::tv::tuner::V1_0::LnbPosition position) override;
    ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> sendDiseqcMessage(const ::android::hardware::hidl_vec<uint8_t>& diseqcMessage) override;
    ::android::hardware::Return<::android::hardware::tv::tuner::V1_0::Result> close() override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_BPHWLNB_H
