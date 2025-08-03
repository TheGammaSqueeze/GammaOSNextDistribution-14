#ifndef HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_1_BPHWSOUNDTRIGGERHW_H
#define HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_1_BPHWSOUNDTRIGGERHW_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/soundtrigger/2.1/IHwSoundTriggerHw.h>

namespace android {
namespace hardware {
namespace soundtrigger {
namespace V2_1 {

struct BpHwSoundTriggerHw : public ::android::hardware::BpInterface<ISoundTriggerHw>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwSoundTriggerHw(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ISoundTriggerHw Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::soundtrigger::V2_1::ISoundTriggerHw follow.
    static ::android::hardware::Return<void>  _hidl_loadSoundModel_2_1(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::soundtrigger::V2_1::ISoundTriggerHw::SoundModel& soundModel, const ::android::sp<::android::hardware::soundtrigger::V2_1::ISoundTriggerHwCallback>& callback, int32_t cookie, loadSoundModel_2_1_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_loadPhraseSoundModel_2_1(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::soundtrigger::V2_1::ISoundTriggerHw::PhraseSoundModel& soundModel, const ::android::sp<::android::hardware::soundtrigger::V2_1::ISoundTriggerHwCallback>& callback, int32_t cookie, loadPhraseSoundModel_2_1_cb _hidl_cb);
    static ::android::hardware::Return<int32_t>  _hidl_startRecognition_2_1(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t modelHandle, const ::android::hardware::soundtrigger::V2_1::ISoundTriggerHw::RecognitionConfig& config, const ::android::sp<::android::hardware::soundtrigger::V2_1::ISoundTriggerHwCallback>& callback, int32_t cookie);

    // Methods from ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw follow.
    ::android::hardware::Return<void> getProperties(getProperties_cb _hidl_cb) override;
    ::android::hardware::Return<void> loadSoundModel(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::SoundModel& soundModel, const ::android::sp<::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback>& callback, int32_t cookie, loadSoundModel_cb _hidl_cb) override;
    ::android::hardware::Return<void> loadPhraseSoundModel(const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::PhraseSoundModel& soundModel, const ::android::sp<::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback>& callback, int32_t cookie, loadPhraseSoundModel_cb _hidl_cb) override;
    ::android::hardware::Return<int32_t> unloadSoundModel(int32_t modelHandle) override;
    ::android::hardware::Return<int32_t> startRecognition(int32_t modelHandle, const ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw::RecognitionConfig& config, const ::android::sp<::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback>& callback, int32_t cookie) override;
    ::android::hardware::Return<int32_t> stopRecognition(int32_t modelHandle) override;
    ::android::hardware::Return<int32_t> stopAllRecognitions() override;

    // Methods from ::android::hardware::soundtrigger::V2_1::ISoundTriggerHw follow.
    ::android::hardware::Return<void> loadSoundModel_2_1(const ::android::hardware::soundtrigger::V2_1::ISoundTriggerHw::SoundModel& soundModel, const ::android::sp<::android::hardware::soundtrigger::V2_1::ISoundTriggerHwCallback>& callback, int32_t cookie, loadSoundModel_2_1_cb _hidl_cb) override;
    ::android::hardware::Return<void> loadPhraseSoundModel_2_1(const ::android::hardware::soundtrigger::V2_1::ISoundTriggerHw::PhraseSoundModel& soundModel, const ::android::sp<::android::hardware::soundtrigger::V2_1::ISoundTriggerHwCallback>& callback, int32_t cookie, loadPhraseSoundModel_2_1_cb _hidl_cb) override;
    ::android::hardware::Return<int32_t> startRecognition_2_1(int32_t modelHandle, const ::android::hardware::soundtrigger::V2_1::ISoundTriggerHw::RecognitionConfig& config, const ::android::sp<::android::hardware::soundtrigger::V2_1::ISoundTriggerHwCallback>& callback, int32_t cookie) override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_1_BPHWSOUNDTRIGGERHW_H
