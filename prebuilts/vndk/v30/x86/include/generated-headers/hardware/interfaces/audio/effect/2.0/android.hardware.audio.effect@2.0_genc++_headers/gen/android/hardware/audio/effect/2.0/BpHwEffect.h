#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V2_0_BPHWEFFECT_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V2_0_BPHWEFFECT_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/audio/effect/2.0/IHwEffect.h>

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V2_0 {

struct BpHwEffect : public ::android::hardware::BpInterface<IEffect>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwEffect(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IEffect Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::audio::effect::V2_0::IEffect follow.
    static ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result>  _hidl_init(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result>  _hidl_setConfig(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::audio::effect::V2_0::EffectConfig& config, const ::android::sp<::android::hardware::audio::effect::V2_0::IEffectBufferProviderCallback>& inputBufferProvider, const ::android::sp<::android::hardware::audio::effect::V2_0::IEffectBufferProviderCallback>& outputBufferProvider);
    static ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result>  _hidl_reset(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result>  _hidl_enable(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result>  _hidl_disable(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result>  _hidl_setDevice(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::audio::common::V2_0::AudioDevice device);
    static ::android::hardware::Return<void>  _hidl_setAndGetVolume(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint32_t>& volumes, setAndGetVolume_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result>  _hidl_volumeChangeNotification(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint32_t>& volumes);
    static ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result>  _hidl_setAudioMode(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::audio::common::V2_0::AudioMode mode);
    static ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result>  _hidl_setConfigReverse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::audio::effect::V2_0::EffectConfig& config, const ::android::sp<::android::hardware::audio::effect::V2_0::IEffectBufferProviderCallback>& inputBufferProvider, const ::android::sp<::android::hardware::audio::effect::V2_0::IEffectBufferProviderCallback>& outputBufferProvider);
    static ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result>  _hidl_setInputDevice(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::audio::common::V2_0::AudioDevice device);
    static ::android::hardware::Return<void>  _hidl_getConfig(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getConfig_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getConfigReverse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getConfigReverse_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getSupportedAuxChannelsConfigs(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t maxConfigs, getSupportedAuxChannelsConfigs_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getAuxChannelsConfig(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getAuxChannelsConfig_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result>  _hidl_setAuxChannelsConfig(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::audio::effect::V2_0::EffectAuxChannelsConfig& config);
    static ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result>  _hidl_setAudioSource(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::audio::common::V2_0::AudioSource source);
    static ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result>  _hidl_offload(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::audio::effect::V2_0::EffectOffloadParameter& param);
    static ::android::hardware::Return<void>  _hidl_getDescriptor(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getDescriptor_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_prepareForProcessing(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, prepareForProcessing_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result>  _hidl_setProcessBuffers(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::audio::effect::V2_0::AudioBuffer& inBuffer, const ::android::hardware::audio::effect::V2_0::AudioBuffer& outBuffer);
    static ::android::hardware::Return<void>  _hidl_command(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t commandId, const ::android::hardware::hidl_vec<uint8_t>& data, uint32_t resultMaxSize, command_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result>  _hidl_setParameter(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& parameter, const ::android::hardware::hidl_vec<uint8_t>& value);
    static ::android::hardware::Return<void>  _hidl_getParameter(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& parameter, uint32_t valueMaxSize, getParameter_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getSupportedConfigsForFeature(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t featureId, uint32_t maxConfigs, uint32_t configSize, getSupportedConfigsForFeature_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getCurrentConfigForFeature(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t featureId, uint32_t configSize, getCurrentConfigForFeature_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result>  _hidl_setCurrentConfigForFeature(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t featureId, const ::android::hardware::hidl_vec<uint8_t>& configData);
    static ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result>  _hidl_close(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);

    // Methods from ::android::hardware::audio::effect::V2_0::IEffect follow.
    ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> init() override;
    ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setConfig(const ::android::hardware::audio::effect::V2_0::EffectConfig& config, const ::android::sp<::android::hardware::audio::effect::V2_0::IEffectBufferProviderCallback>& inputBufferProvider, const ::android::sp<::android::hardware::audio::effect::V2_0::IEffectBufferProviderCallback>& outputBufferProvider) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> reset() override;
    ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> enable() override;
    ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> disable() override;
    ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setDevice(::android::hardware::audio::common::V2_0::AudioDevice device) override;
    ::android::hardware::Return<void> setAndGetVolume(const ::android::hardware::hidl_vec<uint32_t>& volumes, setAndGetVolume_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> volumeChangeNotification(const ::android::hardware::hidl_vec<uint32_t>& volumes) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setAudioMode(::android::hardware::audio::common::V2_0::AudioMode mode) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setConfigReverse(const ::android::hardware::audio::effect::V2_0::EffectConfig& config, const ::android::sp<::android::hardware::audio::effect::V2_0::IEffectBufferProviderCallback>& inputBufferProvider, const ::android::sp<::android::hardware::audio::effect::V2_0::IEffectBufferProviderCallback>& outputBufferProvider) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setInputDevice(::android::hardware::audio::common::V2_0::AudioDevice device) override;
    ::android::hardware::Return<void> getConfig(getConfig_cb _hidl_cb) override;
    ::android::hardware::Return<void> getConfigReverse(getConfigReverse_cb _hidl_cb) override;
    ::android::hardware::Return<void> getSupportedAuxChannelsConfigs(uint32_t maxConfigs, getSupportedAuxChannelsConfigs_cb _hidl_cb) override;
    ::android::hardware::Return<void> getAuxChannelsConfig(getAuxChannelsConfig_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setAuxChannelsConfig(const ::android::hardware::audio::effect::V2_0::EffectAuxChannelsConfig& config) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setAudioSource(::android::hardware::audio::common::V2_0::AudioSource source) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> offload(const ::android::hardware::audio::effect::V2_0::EffectOffloadParameter& param) override;
    ::android::hardware::Return<void> getDescriptor(getDescriptor_cb _hidl_cb) override;
    ::android::hardware::Return<void> prepareForProcessing(prepareForProcessing_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setProcessBuffers(const ::android::hardware::audio::effect::V2_0::AudioBuffer& inBuffer, const ::android::hardware::audio::effect::V2_0::AudioBuffer& outBuffer) override;
    ::android::hardware::Return<void> command(uint32_t commandId, const ::android::hardware::hidl_vec<uint8_t>& data, uint32_t resultMaxSize, command_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setParameter(const ::android::hardware::hidl_vec<uint8_t>& parameter, const ::android::hardware::hidl_vec<uint8_t>& value) override;
    ::android::hardware::Return<void> getParameter(const ::android::hardware::hidl_vec<uint8_t>& parameter, uint32_t valueMaxSize, getParameter_cb _hidl_cb) override;
    ::android::hardware::Return<void> getSupportedConfigsForFeature(uint32_t featureId, uint32_t maxConfigs, uint32_t configSize, getSupportedConfigsForFeature_cb _hidl_cb) override;
    ::android::hardware::Return<void> getCurrentConfigForFeature(uint32_t featureId, uint32_t configSize, getCurrentConfigForFeature_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> setCurrentConfigForFeature(uint32_t featureId, const ::android::hardware::hidl_vec<uint8_t>& configData) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V2_0::Result> close() override;

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

}  // namespace V2_0
}  // namespace effect
}  // namespace audio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V2_0_BPHWEFFECT_H
