#ifndef HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_1_BPHWSOUNDTRIGGERHWCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_1_BPHWSOUNDTRIGGERHWCALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/soundtrigger/2.1/IHwSoundTriggerHwCallback.h>

namespace android {
namespace hardware {
namespace soundtrigger {
namespace V2_1 {

struct BpHwSoundTriggerHwCallback : public ::android::hardware::BpInterface<ISoundTriggerHwCallback>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwSoundTriggerHwCallback(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ISoundTriggerHwCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::soundtrigger::V2_1::ISoundTriggerHwCallback follow.
    static ::android::hardware::Return<void>  _hidl_recognitionCallback_2_1(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::soundtrigger::V2_1::ISoundTriggerHwCallback::RecognitionEvent& event, int32_t cookie);
    static ::android::hardware::Return<void>  _hidl_phraseRecognitionCallback_2_1(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::soundtrigger::V2_1::ISoundTriggerHwCallback::PhraseRecognitionEvent& event, int32_t cookie);
    static ::android::hardware::Return<void>  _hidl_soundModelCallback_2_1(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::soundtrigger::V2_1::ISoundTriggerHwCallback::ModelEvent& event, int32_t cookie);

    // Methods from ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback follow.
    ::android::hardware::Return<void> recognitionCallback(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::RecognitionEvent& event, int32_t cookie) override;
    ::android::hardware::Return<void> phraseRecognitionCallback(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::PhraseRecognitionEvent& event, int32_t cookie) override;
    ::android::hardware::Return<void> soundModelCallback(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback::ModelEvent& event, int32_t cookie) override;

    // Methods from ::android::hardware::soundtrigger::V2_1::ISoundTriggerHwCallback follow.
    ::android::hardware::Return<void> recognitionCallback_2_1(const ::android::hardware::soundtrigger::V2_1::ISoundTriggerHwCallback::RecognitionEvent& event, int32_t cookie) override;
    ::android::hardware::Return<void> phraseRecognitionCallback_2_1(const ::android::hardware::soundtrigger::V2_1::ISoundTriggerHwCallback::PhraseRecognitionEvent& event, int32_t cookie) override;
    ::android::hardware::Return<void> soundModelCallback_2_1(const ::android::hardware::soundtrigger::V2_1::ISoundTriggerHwCallback::ModelEvent& event, int32_t cookie) override;

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

}  // namespace V2_1
}  // namespace soundtrigger
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_1_BPHWSOUNDTRIGGERHWCALLBACK_H
