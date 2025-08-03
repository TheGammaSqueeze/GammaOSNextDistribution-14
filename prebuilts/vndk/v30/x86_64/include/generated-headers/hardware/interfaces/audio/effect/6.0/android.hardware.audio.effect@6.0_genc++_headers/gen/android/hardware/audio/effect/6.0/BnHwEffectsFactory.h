#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V6_0_BNHWEFFECTSFACTORY_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V6_0_BNHWEFFECTSFACTORY_H

#include <android/hardware/audio/effect/6.0/IHwEffectsFactory.h>

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V6_0 {

struct BnHwEffectsFactory : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwEffectsFactory(const ::android::sp<IEffectsFactory> &_hidl_impl);
    explicit BnHwEffectsFactory(const ::android::sp<IEffectsFactory> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwEffectsFactory();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IEffectsFactory Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IEffectsFactory> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::audio::effect::V6_0::IEffectsFactory follow.
    static ::android::status_t _hidl_getAllDescriptors(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_getDescriptor(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_createEffect(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::audio::effect::V6_0::IEffectsFactory follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IEffectsFactory> _hidl_mImpl;
};

}  // namespace V6_0
}  // namespace effect
}  // namespace audio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V6_0_BNHWEFFECTSFACTORY_H
