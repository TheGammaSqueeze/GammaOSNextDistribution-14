#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V6_0_BPHWEFFECTBUFFERPROVIDERCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V6_0_BPHWEFFECTBUFFERPROVIDERCALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/audio/effect/6.0/IHwEffectBufferProviderCallback.h>

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V6_0 {

struct BpHwEffectBufferProviderCallback : public ::android::hardware::BpInterface<IEffectBufferProviderCallback>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwEffectBufferProviderCallback(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IEffectBufferProviderCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::audio::effect::V6_0::IEffectBufferProviderCallback follow.
    static ::android::hardware::Return<void>  _hidl_getBuffer(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getBuffer_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_putBuffer(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::audio::effect::V6_0::AudioBuffer& buffer);

    // Methods from ::android::hardware::audio::effect::V6_0::IEffectBufferProviderCallback follow.
    ::android::hardware::Return<void> getBuffer(getBuffer_cb _hidl_cb) override;
    ::android::hardware::Return<void> putBuffer(const ::android::hardware::audio::effect::V6_0::AudioBuffer& buffer) override;

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

}  // namespace V6_0
}  // namespace effect
}  // namespace audio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V6_0_BPHWEFFECTBUFFERPROVIDERCALLBACK_H
