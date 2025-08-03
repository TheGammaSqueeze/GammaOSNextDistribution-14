#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V4_0_BNHWNOISESUPPRESSIONEFFECT_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V4_0_BNHWNOISESUPPRESSIONEFFECT_H

#include <android/hardware/audio/effect/4.0/IHwNoiseSuppressionEffect.h>

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V4_0 {

struct BnHwNoiseSuppressionEffect : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwNoiseSuppressionEffect(const ::android::sp<INoiseSuppressionEffect> &_hidl_impl);
    explicit BnHwNoiseSuppressionEffect(const ::android::sp<INoiseSuppressionEffect> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwNoiseSuppressionEffect();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef INoiseSuppressionEffect Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<INoiseSuppressionEffect> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::audio::effect::V4_0::INoiseSuppressionEffect follow.
    static ::android::status_t _hidl_setSuppressionLevel(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_getSuppressionLevel(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_setSuppressionType(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_getSuppressionType(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_setAllProperties(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_getAllProperties(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::audio::effect::V4_0::IEffect follow.

    // Methods from ::android::hardware::audio::effect::V4_0::INoiseSuppressionEffect follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<INoiseSuppressionEffect> _hidl_mImpl;
};

}  // namespace V4_0
}  // namespace effect
}  // namespace audio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V4_0_BNHWNOISESUPPRESSIONEFFECT_H
