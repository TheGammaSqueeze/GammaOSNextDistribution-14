#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V6_0_BPHWSTREAM_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V6_0_BPHWSTREAM_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/audio/6.0/IHwStream.h>

namespace android {
namespace hardware {
namespace audio {
namespace V6_0 {

struct BpHwStream : public ::android::hardware::BpInterface<IStream>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwStream(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IStream Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::audio::V6_0::IStream follow.
    static ::android::hardware::Return<uint64_t>  _hidl_getFrameSize(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<uint64_t>  _hidl_getFrameCount(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<uint64_t>  _hidl_getBufferSize(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<uint32_t>  _hidl_getSampleRate(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_getSupportedSampleRates(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::audio::common::V6_0::AudioFormat format, getSupportedSampleRates_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::audio::V6_0::Result>  _hidl_setSampleRate(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t sampleRateHz);
    static ::android::hardware::Return<::android::hardware::hidl_bitfield<::android::hardware::audio::common::V6_0::AudioChannelMask>>  _hidl_getChannelMask(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_getSupportedChannelMasks(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::audio::common::V6_0::AudioFormat format, getSupportedChannelMasks_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::audio::V6_0::Result>  _hidl_setChannelMask(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V6_0::AudioChannelMask> mask);
    static ::android::hardware::Return<::android::hardware::audio::common::V6_0::AudioFormat>  _hidl_getFormat(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_getSupportedFormats(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getSupportedFormats_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::audio::V6_0::Result>  _hidl_setFormat(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::audio::common::V6_0::AudioFormat format);
    static ::android::hardware::Return<void>  _hidl_getAudioProperties(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getAudioProperties_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::audio::V6_0::Result>  _hidl_addEffect(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t effectId);
    static ::android::hardware::Return<::android::hardware::audio::V6_0::Result>  _hidl_removeEffect(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t effectId);
    static ::android::hardware::Return<::android::hardware::audio::V6_0::Result>  _hidl_standby(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_getDevices(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getDevices_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::audio::V6_0::Result>  _hidl_setDevices(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::audio::common::V6_0::DeviceAddress>& devices);
    static ::android::hardware::Return<::android::hardware::audio::V6_0::Result>  _hidl_setHwAvSync(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t hwAvSync);
    static ::android::hardware::Return<void>  _hidl_getParameters(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::audio::V6_0::ParameterValue>& context, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& keys, getParameters_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::audio::V6_0::Result>  _hidl_setParameters(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::audio::V6_0::ParameterValue>& context, const ::android::hardware::hidl_vec<::android::hardware::audio::V6_0::ParameterValue>& parameters);
    static ::android::hardware::Return<::android::hardware::audio::V6_0::Result>  _hidl_start(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::audio::V6_0::Result>  _hidl_stop(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_createMmapBuffer(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t minSizeFrames, createMmapBuffer_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getMmapPosition(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getMmapPosition_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::audio::V6_0::Result>  _hidl_close(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);

    // Methods from ::android::hardware::audio::V6_0::IStream follow.
    ::android::hardware::Return<uint64_t> getFrameSize() override;
    ::android::hardware::Return<uint64_t> getFrameCount() override;
    ::android::hardware::Return<uint64_t> getBufferSize() override;
    ::android::hardware::Return<uint32_t> getSampleRate() override;
    ::android::hardware::Return<void> getSupportedSampleRates(::android::hardware::audio::common::V6_0::AudioFormat format, getSupportedSampleRates_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::V6_0::Result> setSampleRate(uint32_t sampleRateHz) override;
    ::android::hardware::Return<::android::hardware::hidl_bitfield<::android::hardware::audio::common::V6_0::AudioChannelMask>> getChannelMask() override;
    ::android::hardware::Return<void> getSupportedChannelMasks(::android::hardware::audio::common::V6_0::AudioFormat format, getSupportedChannelMasks_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::V6_0::Result> setChannelMask(::android::hardware::hidl_bitfield<::android::hardware::audio::common::V6_0::AudioChannelMask> mask) override;
    ::android::hardware::Return<::android::hardware::audio::common::V6_0::AudioFormat> getFormat() override;
    ::android::hardware::Return<void> getSupportedFormats(getSupportedFormats_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::V6_0::Result> setFormat(::android::hardware::audio::common::V6_0::AudioFormat format) override;
    ::android::hardware::Return<void> getAudioProperties(getAudioProperties_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::V6_0::Result> addEffect(uint64_t effectId) override;
    ::android::hardware::Return<::android::hardware::audio::V6_0::Result> removeEffect(uint64_t effectId) override;
    ::android::hardware::Return<::android::hardware::audio::V6_0::Result> standby() override;
    ::android::hardware::Return<void> getDevices(getDevices_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::V6_0::Result> setDevices(const ::android::hardware::hidl_vec<::android::hardware::audio::common::V6_0::DeviceAddress>& devices) override;
    ::android::hardware::Return<::android::hardware::audio::V6_0::Result> setHwAvSync(uint32_t hwAvSync) override;
    ::android::hardware::Return<void> getParameters(const ::android::hardware::hidl_vec<::android::hardware::audio::V6_0::ParameterValue>& context, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& keys, getParameters_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::V6_0::Result> setParameters(const ::android::hardware::hidl_vec<::android::hardware::audio::V6_0::ParameterValue>& context, const ::android::hardware::hidl_vec<::android::hardware::audio::V6_0::ParameterValue>& parameters) override;
    ::android::hardware::Return<::android::hardware::audio::V6_0::Result> start() override;
    ::android::hardware::Return<::android::hardware::audio::V6_0::Result> stop() override;
    ::android::hardware::Return<void> createMmapBuffer(int32_t minSizeFrames, createMmapBuffer_cb _hidl_cb) override;
    ::android::hardware::Return<void> getMmapPosition(getMmapPosition_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::audio::V6_0::Result> close() override;

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
}  // namespace audio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V6_0_BPHWSTREAM_H
