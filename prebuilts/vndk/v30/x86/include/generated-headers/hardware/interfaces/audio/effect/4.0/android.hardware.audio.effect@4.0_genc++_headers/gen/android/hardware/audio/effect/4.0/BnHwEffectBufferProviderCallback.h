#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V4_0_BNHWEFFECTBUFFERPROVIDERCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V4_0_BNHWEFFECTBUFFERPROVIDERCALLBACK_H

#include <android/hardware/audio/effect/4.0/IHwEffectBufferProviderCallback.h>

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V4_0 {

struct BnHwEffectBufferProviderCallback : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwEffectBufferProviderCallback(const ::android::sp<IEffectBufferProviderCallback> &_hidl_impl);
    explicit BnHwEffectBufferProviderCallback(const ::android::sp<IEffectBufferProviderCallback> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwEffectBufferProviderCallback();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IEffectBufferProviderCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IEffectBufferProviderCallback> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::audio::effect::V4_0::IEffectBufferProviderCallback follow.
    static ::android::status_t _hidl_getBuffer(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_putBuffer(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::audio::effect::V4_0::IEffectBufferProviderCallback follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IEffectBufferProviderCallback> _hidl_mImpl;
};

}  // namespace V4_0
}  // namespace effect
}  // namespace audio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V4_0_BNHWEFFECTBUFFERPROVIDERCALLBACK_H
