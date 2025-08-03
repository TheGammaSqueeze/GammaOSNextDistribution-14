#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V5_0_BPHWEFFECTSFACTORY_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V5_0_BPHWEFFECTSFACTORY_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/audio/effect/5.0/IHwEffectsFactory.h>

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V5_0 {

struct BpHwEffectsFactory : public ::android::hardware::BpInterface<IEffectsFactory>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwEffectsFactory(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IEffectsFactory Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::audio::effect::V5_0::IEffectsFactory follow.
    static ::android::hardware::Return<void>  _hidl_getAllDescriptors(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getAllDescriptors_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getDescriptor(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::audio::common::V5_0::Uuid& uid, getDescriptor_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_createEffect(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::audio::common::V5_0::Uuid& uid, int32_t session, int32_t ioHandle, createEffect_cb _hidl_cb);

    // Methods from ::android::hardware::audio::effect::V5_0::IEffectsFactory follow.
    ::android::hardware::Return<void> getAllDescriptors(getAllDescriptors_cb _hidl_cb) override;
    ::android::hardware::Return<void> getDescriptor(const ::android::hardware::audio::common::V5_0::Uuid& uid, getDescriptor_cb _hidl_cb) override;
    ::android::hardware::Return<void> createEffect(const ::android::hardware::audio::common::V5_0::Uuid& uid, int32_t session, int32_t ioHandle, createEffect_cb _hidl_cb) override;

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

}  // namespace V5_0
}  // namespace effect
}  // namespace audio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V5_0_BPHWEFFECTSFACTORY_H
