#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V4_0_BPHWSTREAMOUT_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V4_0_BPHWSTREAMOUT_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/audio/4.0/IHwStreamOut.h>

namespace android {
namespace hardware {
namespace audio {
namespace V4_0 {

struct BpHwStreamOut : public ::android::hardware::BpInterface<IStreamOut>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwStreamOut(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IStreamOut Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::audio::V4_0::IStreamOut follow.
    static ::android::hardware::Return<uint32_t>  _hidl_getLatency(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::audio::V4_0::Result>  _hidl_setVolume(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, float left, float right);
    static ::android::hardware::Return<void>  _hidl_updateSourceMetadata(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::audio::V4_0::SourceMetadata& sourceMetadata);
    static ::android::hardware::Return<void>  _hidl_prepareForWriting(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t frameSize, uint32_t framesCount, prepareForWriting_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getRenderPosition(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getRenderPosition_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getNextWriteTimestamp(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getNextWriteTimestamp_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::audio::V4_0::Result>  _hidl_setCallback(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::audio::V4_0::IStreamOutCallback>& callback);
    static ::android::hardware::Return<::android::hardware::audio::V4_0::Result>  _hidl_clearCallback(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_supportsPauseAndResume(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, supportsPauseAndResume_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::audio::V4_0::Result>  _hidl_pause(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::audio::V4_0::Result>  _hidl_resume(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<bool>  _hidl_supportsDrain(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::audio::V4_0::Result>  _hidl_drain(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::audio::V4_0::AudioDrain type);
    static ::android::hardware::Return<::android::hardware::audio::V4_0::Result>  _hidl_flush(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_getPresentationPosition(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getPresentationPosition_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::audio::V4_0::Result>  _hidl_selectPresentation(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t presentationId, int32_t programId);

    // Methods from ::android::hardware::audio::V4_0::IStream follow.
    ::android::hardware::Return<uint64_t> getFrameSize() override;
    ::android::hardware::Return<uint64_t> getFrameCount() override;
    ::android::hardware::Return<uint64_t> getBufferSize() override;
    ::android::hardware::Return<uint32_t> getSampleRate() override;
    ::android::hardware::Return<void> getSupportedSampleRates(::android::hardware::audio::common::V4_0::AudioFormat format, getSupportedSampleRates_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setSampleRate(uint32_t sampleRateHz) override;
    ::android::hardware::Return<::android::hardware::hidl_bitfield<::android::hardware::audio::common::V4_0::AudioChannelMask>> getChannelMask() override;
    ::android::hardware::Return<void> getSupportedChannelMasks(::android::hardware::audio::common::V4_0::AudioFormat format, getSupportedChannelMasks_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setChannelMask(::android::hardware::hidl_bitfield<::android::hardware::audio::common::V4_0::AudioChannelMask> mask) override;
    ::android::hardware::Return<::android::hardware::audio::common::V4_0::AudioFormat> getFormat() override;
    ::android::hardware::Return<void> getSupportedFormats(getSupportedFormats_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setFormat(::android::hardware::audio::common::V4_0::AudioFormat format) override;
    ::android::hardware::Return<void> getAudioProperties(getAudioProperties_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::V4_0::Result> addEffect(uint64_t effectId) override;
    ::android::hardware::Return<::android::hardware::audio::V4_0::Result> removeEffect(uint64_t effectId) override;
    ::android::hardware::Return<::android::hardware::audio::V4_0::Result> standby() override;
    ::android::hardware::Return<void> getDevices(getDevices_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setDevices(const ::android::hardware::hidl_vec<::android::hardware::audio::V4_0::DeviceAddress>& devices) override;
    ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setHwAvSync(uint32_t hwAvSync) override;
    ::android::hardware::Return<void> getParameters(const ::android::hardware::hidl_vec<::android::hardware::audio::V4_0::ParameterValue>& context, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& keys, getParameters_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setParameters(const ::android::hardware::hidl_vec<::android::hardware::audio::V4_0::ParameterValue>& context, const ::android::hardware::hidl_vec<::android::hardware::audio::V4_0::ParameterValue>& parameters) override;
    ::android::hardware::Return<::android::hardware::audio::V4_0::Result> start() override;
    ::android::hardware::Return<::android::hardware::audio::V4_0::Result> stop() override;
    ::android::hardware::Return<void> createMmapBuffer(int32_t minSizeFrames, createMmapBuffer_cb _hidl_cb) override;
    ::android::hardware::Return<void> getMmapPosition(getMmapPosition_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::V4_0::Result> close() override;

    // Methods from ::android::hardware::audio::V4_0::IStreamOut follow.
    ::android::hardware::Return<uint32_t> getLatency() override;
    ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setVolume(float left, float right) override;
    ::android::hardware::Return<void> updateSourceMetadata(const ::android::hardware::audio::V4_0::SourceMetadata& sourceMetadata) override;
    ::android::hardware::Return<void> prepareForWriting(uint32_t frameSize, uint32_t framesCount, prepareForWriting_cb _hidl_cb) override;
    ::android::hardware::Return<void> getRenderPosition(getRenderPosition_cb _hidl_cb) override;
    ::android::hardware::Return<void> getNextWriteTimestamp(getNextWriteTimestamp_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::V4_0::Result> setCallback(const ::android::sp<::android::hardware::audio::V4_0::IStreamOutCallback>& callback) override;
    ::android::hardware::Return<::android::hardware::audio::V4_0::Result> clearCallback() override;
    ::android::hardware::Return<void> supportsPauseAndResume(supportsPauseAndResume_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::V4_0::Result> pause() override;
    ::android::hardware::Return<::android::hardware::audio::V4_0::Result> resume() override;
    ::android::hardware::Return<bool> supportsDrain() override;
    ::android::hardware::Return<::android::hardware::audio::V4_0::Result> drain(::android::hardware::audio::V4_0::AudioDrain type) override;
    ::android::hardware::Return<::android::hardware::audio::V4_0::Result> flush() override;
    ::android::hardware::Return<void> getPresentationPosition(getPresentationPosition_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::V4_0::Result> selectPresentation(int32_t presentationId, int32_t programId) override;

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

}  // namespace V4_0
}  // namespace audio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V4_0_BPHWSTREAMOUT_H
