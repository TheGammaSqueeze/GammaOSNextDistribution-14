#ifndef HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_0_BNHWSOUNDTRIGGERHWCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_0_BNHWSOUNDTRIGGERHWCALLBACK_H

#include <android/hardware/soundtrigger/2.0/IHwSoundTriggerHwCallback.h>

namespace android {
namespace hardware {
namespace soundtrigger {
namespace V2_0 {

struct BnHwSoundTriggerHwCallback : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwSoundTriggerHwCallback(const ::android::sp<ISoundTriggerHwCallback> &_hidl_impl);
    explicit BnHwSoundTriggerHwCallback(const ::android::sp<ISoundTriggerHwCallback> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwSoundTriggerHwCallback();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef ISoundTriggerHwCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<ISoundTriggerHwCallback> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback follow.
    static ::android::status_t _hidl_recognitionCallback(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_phraseRecognitionCallback(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_soundModelCallback(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::soundtrigger::V2_0::ISoundTriggerHwCallback follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<ISoundTriggerHwCallback> _hidl_mImpl;
};

}  // namespace V2_0
}  // namespace soundtrigger
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_0_BNHWSOUNDTRIGGERHWCALLBACK_H
