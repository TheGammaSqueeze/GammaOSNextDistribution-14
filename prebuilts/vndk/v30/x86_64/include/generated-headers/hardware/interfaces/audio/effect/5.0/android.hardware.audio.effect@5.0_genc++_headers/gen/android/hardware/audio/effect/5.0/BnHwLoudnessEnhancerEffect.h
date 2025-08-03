#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V5_0_BNHWLOUDNESSENHANCEREFFECT_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V5_0_BNHWLOUDNESSENHANCEREFFECT_H

#include <android/hardware/audio/effect/5.0/IHwLoudnessEnhancerEffect.h>

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V5_0 {

struct BnHwLoudnessEnhancerEffect : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwLoudnessEnhancerEffect(const ::android::sp<ILoudnessEnhancerEffect> &_hidl_impl);
    explicit BnHwLoudnessEnhancerEffect(const ::android::sp<ILoudnessEnhancerEffect> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwLoudnessEnhancerEffect();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef ILoudnessEnhancerEffect Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<ILoudnessEnhancerEffect> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::audio::effect::V5_0::ILoudnessEnhancerEffect follow.
    static ::android::status_t _hidl_setTargetGain(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_getTargetGain(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::audio::effect::V5_0::IEffect follow.

    // Methods from ::android::hardware::audio::effect::V5_0::ILoudnessEnhancerEffect follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<ILoudnessEnhancerEffect> _hidl_mImpl;
};

}  // namespace V5_0
}  // namespace effect
}  // namespace audio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V5_0_BNHWLOUDNESSENHANCEREFFECT_H
