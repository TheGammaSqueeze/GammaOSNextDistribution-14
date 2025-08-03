#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V6_0_BPHWVISUALIZEREFFECT_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V6_0_BPHWVISUALIZEREFFECT_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/audio/effect/6.0/IHwVisualizerEffect.h>

namespace android {
namespace hardware {
namespace audio {
namespace effect {
namespace V6_0 {

struct BpHwVisualizerEffect : public ::android::hardware::BpInterface<IVisualizerEffect>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwVisualizerEffect(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IVisualizerEffect Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::audio::effect::V6_0::IVisualizerEffect follow.
    static ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result>  _hidl_setCaptureSize(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint16_t captureSize);
    static ::android::hardware::Return<void>  _hidl_getCaptureSize(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getCaptureSize_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result>  _hidl_setScalingMode(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::audio::effect::V6_0::IVisualizerEffect::ScalingMode scalingMode);
    static ::android::hardware::Return<void>  _hidl_getScalingMode(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getScalingMode_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result>  _hidl_setLatency(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t latencyMs);
    static ::android::hardware::Return<void>  _hidl_getLatency(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getLatency_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result>  _hidl_setMeasurementMode(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::audio::effect::V6_0::IVisualizerEffect::MeasurementMode measurementMode);
    static ::android::hardware::Return<void>  _hidl_getMeasurementMode(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getMeasurementMode_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_capture(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, capture_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_measure(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, measure_cb _hidl_cb);

    // Methods from ::android::hardware::audio::effect::V6_0::IEffect follow.
    ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result> init() override;
    ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result> setConfig(const ::android::hardware::audio::effect::V6_0::EffectConfig& config, const ::android::sp<::android::hardware::audio::effect::V6_0::IEffectBufferProviderCallback>& inputBufferProvider, const ::android::sp<::android::hardware::audio::effect::V6_0::IEffectBufferProviderCallback>& outputBufferProvider) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result> reset() override;
    ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result> enable() override;
    ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result> disable() override;
    ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result> setDevice(::android::hardware::hidl_bitfield<::android::hardware::audio::common::V6_0::AudioDevice> device) override;
    ::android::hardware::Return<void> setAndGetVolume(const ::android::hardware::hidl_vec<uint32_t>& volumes, setAndGetVolume_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result> volumeChangeNotification(const ::android::hardware::hidl_vec<uint32_t>& volumes) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result> setAudioMode(::android::hardware::audio::common::V6_0::AudioMode mode) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result> setConfigReverse(const ::android::hardware::audio::effect::V6_0::EffectConfig& config, const ::android::sp<::android::hardware::audio::effect::V6_0::IEffectBufferProviderCallback>& inputBufferProvider, const ::android::sp<::android::hardware::audio::effect::V6_0::IEffectBufferProviderCallback>& outputBufferProvider) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result> setInputDevice(::android::hardware::hidl_bitfield<::android::hardware::audio::common::V6_0::AudioDevice> device) override;
    ::android::hardware::Return<void> getConfig(getConfig_cb _hidl_cb) override;
    ::android::hardware::Return<void> getConfigReverse(getConfigReverse_cb _hidl_cb) override;
    ::android::hardware::Return<void> getSupportedAuxChannelsConfigs(uint32_t maxConfigs, getSupportedAuxChannelsConfigs_cb _hidl_cb) override;
    ::android::hardware::Return<void> getAuxChannelsConfig(getAuxChannelsConfig_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result> setAuxChannelsConfig(const ::android::hardware::audio::effect::V6_0::EffectAuxChannelsConfig& config) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result> setAudioSource(::android::hardware::audio::common::V6_0::AudioSource source) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result> offload(const ::android::hardware::audio::effect::V6_0::EffectOffloadParameter& param) override;
    ::android::hardware::Return<void> getDescriptor(getDescriptor_cb _hidl_cb) override;
    ::android::hardware::Return<void> prepareForProcessing(prepareForProcessing_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result> setProcessBuffers(const ::android::hardware::audio::effect::V6_0::AudioBuffer& inBuffer, const ::android::hardware::audio::effect::V6_0::AudioBuffer& outBuffer) override;
    ::android::hardware::Return<void> command(uint32_t commandId, const ::android::hardware::hidl_vec<uint8_t>& data, uint32_t resultMaxSize, command_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result> setParameter(const ::android::hardware::hidl_vec<uint8_t>& parameter, const ::android::hardware::hidl_vec<uint8_t>& value) override;
    ::android::hardware::Return<void> getParameter(const ::android::hardware::hidl_vec<uint8_t>& parameter, uint32_t valueMaxSize, getParameter_cb _hidl_cb) override;
    ::android::hardware::Return<void> getSupportedConfigsForFeature(uint32_t featureId, uint32_t maxConfigs, uint32_t configSize, getSupportedConfigsForFeature_cb _hidl_cb) override;
    ::android::hardware::Return<void> getCurrentConfigForFeature(uint32_t featureId, uint32_t configSize, getCurrentConfigForFeature_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result> setCurrentConfigForFeature(uint32_t featureId, const ::android::hardware::hidl_vec<uint8_t>& configData) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result> close() override;

    // Methods from ::android::hardware::audio::effect::V6_0::IVisualizerEffect follow.
    ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result> setCaptureSize(uint16_t captureSize) override;
    ::android::hardware::Return<void> getCaptureSize(getCaptureSize_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result> setScalingMode(::android::hardware::audio::effect::V6_0::IVisualizerEffect::ScalingMode scalingMode) override;
    ::android::hardware::Return<void> getScalingMode(getScalingMode_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result> setLatency(uint32_t latencyMs) override;
    ::android::hardware::Return<void> getLatency(getLatency_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::effect::V6_0::Result> setMeasurementMode(::android::hardware::audio::effect::V6_0::IVisualizerEffect::MeasurementMode measurementMode) override;
    ::android::hardware::Return<void> getMeasurementMode(getMeasurementMode_cb _hidl_cb) override;
    ::android::hardware::Return<void> capture(capture_cb _hidl_cb) override;
    ::android::hardware::Return<void> measure(measure_cb _hidl_cb) override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_EFFECT_V6_0_BPHWVISUALIZEREFFECT_H
