#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V5_0_BSSTREAMIN_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V5_0_BSSTREAMIN_H

#include <android-base/macros.h>
#include <cutils/trace.h>
#include <future>
#include <android/hardware/audio/5.0/IStreamIn.h>

#include <hidl/HidlPassthroughSupport.h>
#include <hidl/TaskRunner.h>
namespace android {
namespace hardware {
namespace audio {
namespace V5_0 {

struct BsStreamIn : IStreamIn, ::android::hardware::details::HidlInstrumentor {
    explicit BsStreamIn(const ::android::sp<IStreamIn> impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IStreamIn Pure;

    typedef ::android::hardware::details::bs_tag _hidl_tag;

    // Methods from ::android::hardware::audio::V5_0::IStream follow.
    ::android::hardware::Return<uint64_t> getFrameSize() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::getFrameSize::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "getFrameSize", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getFrameSize();

        uint64_t _hidl_out_frameSize = _hidl_return;
        (void) _hidl_out_frameSize;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_frameSize);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "getFrameSize", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<uint64_t> getFrameCount() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::getFrameCount::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "getFrameCount", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getFrameCount();

        uint64_t _hidl_out_count = _hidl_return;
        (void) _hidl_out_count;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_count);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "getFrameCount", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<uint64_t> getBufferSize() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::getBufferSize::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "getBufferSize", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getBufferSize();

        uint64_t _hidl_out_bufferSize = _hidl_return;
        (void) _hidl_out_bufferSize;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_bufferSize);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "getBufferSize", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<uint32_t> getSampleRate() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::getSampleRate::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "getSampleRate", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getSampleRate();

        uint32_t _hidl_out_sampleRateHz = _hidl_return;
        (void) _hidl_out_sampleRateHz;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_sampleRateHz);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "getSampleRate", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getSupportedSampleRates(::android::hardware::audio::common::V5_0::AudioFormat format, getSupportedSampleRates_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::getSupportedSampleRates::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&format);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "getSupportedSampleRates", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getSupportedSampleRates(format, [&](const auto &_hidl_out_retval, const auto &_hidl_out_sampleRates) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_retval);
                _hidl_args.push_back((void *)&_hidl_out_sampleRates);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "getSupportedSampleRates", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_retval, _hidl_out_sampleRates);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::audio::V5_0::Result> setSampleRate(uint32_t sampleRateHz) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::setSampleRate::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&sampleRateHz);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "setSampleRate", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->setSampleRate(sampleRateHz);

        ::android::hardware::audio::V5_0::Result _hidl_out_retval = _hidl_return;
        (void) _hidl_out_retval;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_retval);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "setSampleRate", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::hidl_bitfield<::android::hardware::audio::common::V5_0::AudioChannelMask>> getChannelMask() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::getChannelMask::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "getChannelMask", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getChannelMask();

        ::android::hardware::hidl_bitfield<::android::hardware::audio::common::V5_0::AudioChannelMask> _hidl_out_mask = _hidl_return;
        (void) _hidl_out_mask;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_mask);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "getChannelMask", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getSupportedChannelMasks(::android::hardware::audio::common::V5_0::AudioFormat format, getSupportedChannelMasks_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::getSupportedChannelMasks::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&format);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "getSupportedChannelMasks", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getSupportedChannelMasks(format, [&](const auto &_hidl_out_retval, const auto &_hidl_out_masks) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_retval);
                _hidl_args.push_back((void *)&_hidl_out_masks);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "getSupportedChannelMasks", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_retval, _hidl_out_masks);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::audio::V5_0::Result> setChannelMask(::android::hardware::hidl_bitfield<::android::hardware::audio::common::V5_0::AudioChannelMask> mask) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::setChannelMask::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&mask);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "setChannelMask", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->setChannelMask(mask);

        ::android::hardware::audio::V5_0::Result _hidl_out_retval = _hidl_return;
        (void) _hidl_out_retval;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_retval);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "setChannelMask", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::audio::common::V5_0::AudioFormat> getFormat() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::getFormat::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "getFormat", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getFormat();

        ::android::hardware::audio::common::V5_0::AudioFormat _hidl_out_format = _hidl_return;
        (void) _hidl_out_format;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_format);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "getFormat", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getSupportedFormats(getSupportedFormats_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::getSupportedFormats::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "getSupportedFormats", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getSupportedFormats([&](const auto &_hidl_out_formats) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_formats);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "getSupportedFormats", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_formats);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::audio::V5_0::Result> setFormat(::android::hardware::audio::common::V5_0::AudioFormat format) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::setFormat::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&format);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "setFormat", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->setFormat(format);

        ::android::hardware::audio::V5_0::Result _hidl_out_retval = _hidl_return;
        (void) _hidl_out_retval;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_retval);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "setFormat", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getAudioProperties(getAudioProperties_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::getAudioProperties::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "getAudioProperties", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getAudioProperties([&](const auto &_hidl_out_sampleRateHz, const auto &_hidl_out_mask, const auto &_hidl_out_format) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_sampleRateHz);
                _hidl_args.push_back((void *)&_hidl_out_mask);
                _hidl_args.push_back((void *)&_hidl_out_format);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "getAudioProperties", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_sampleRateHz, _hidl_out_mask, _hidl_out_format);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::audio::V5_0::Result> addEffect(uint64_t effectId) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::addEffect::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&effectId);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "addEffect", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->addEffect(effectId);

        ::android::hardware::audio::V5_0::Result _hidl_out_retval = _hidl_return;
        (void) _hidl_out_retval;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_retval);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "addEffect", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::audio::V5_0::Result> removeEffect(uint64_t effectId) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::removeEffect::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&effectId);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "removeEffect", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->removeEffect(effectId);

        ::android::hardware::audio::V5_0::Result _hidl_out_retval = _hidl_return;
        (void) _hidl_out_retval;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_retval);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "removeEffect", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::audio::V5_0::Result> standby() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::standby::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "standby", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->standby();

        ::android::hardware::audio::V5_0::Result _hidl_out_retval = _hidl_return;
        (void) _hidl_out_retval;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_retval);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "standby", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getDevices(getDevices_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::getDevices::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "getDevices", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getDevices([&](const auto &_hidl_out_retval, const auto &_hidl_out_devices) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_retval);
                _hidl_args.push_back((void *)&_hidl_out_devices);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "getDevices", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_retval, _hidl_out_devices);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::audio::V5_0::Result> setDevices(const ::android::hardware::hidl_vec<::android::hardware::audio::common::V5_0::DeviceAddress>& devices) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::setDevices::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&devices);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "setDevices", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->setDevices(devices);

        ::android::hardware::audio::V5_0::Result _hidl_out_retval = _hidl_return;
        (void) _hidl_out_retval;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_retval);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "setDevices", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::audio::V5_0::Result> setHwAvSync(uint32_t hwAvSync) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::setHwAvSync::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&hwAvSync);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "setHwAvSync", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->setHwAvSync(hwAvSync);

        ::android::hardware::audio::V5_0::Result _hidl_out_retval = _hidl_return;
        (void) _hidl_out_retval;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_retval);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "setHwAvSync", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getParameters(const ::android::hardware::hidl_vec<::android::hardware::audio::V5_0::ParameterValue>& context, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& keys, getParameters_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::getParameters::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&context);
            _hidl_args.push_back((void *)&keys);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "getParameters", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getParameters(context, keys, [&](const auto &_hidl_out_retval, const auto &_hidl_out_parameters) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_retval);
                _hidl_args.push_back((void *)&_hidl_out_parameters);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "getParameters", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_retval, _hidl_out_parameters);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::audio::V5_0::Result> setParameters(const ::android::hardware::hidl_vec<::android::hardware::audio::V5_0::ParameterValue>& context, const ::android::hardware::hidl_vec<::android::hardware::audio::V5_0::ParameterValue>& parameters) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::setParameters::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&context);
            _hidl_args.push_back((void *)&parameters);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "setParameters", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->setParameters(context, parameters);

        ::android::hardware::audio::V5_0::Result _hidl_out_retval = _hidl_return;
        (void) _hidl_out_retval;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_retval);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "setParameters", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::audio::V5_0::Result> start() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::start::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "start", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->start();

        ::android::hardware::audio::V5_0::Result _hidl_out_retval = _hidl_return;
        (void) _hidl_out_retval;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_retval);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "start", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::audio::V5_0::Result> stop() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::stop::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "stop", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->stop();

        ::android::hardware::audio::V5_0::Result _hidl_out_retval = _hidl_return;
        (void) _hidl_out_retval;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_retval);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "stop", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> createMmapBuffer(int32_t minSizeFrames, createMmapBuffer_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::createMmapBuffer::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&minSizeFrames);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "createMmapBuffer", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->createMmapBuffer(minSizeFrames, [&](const auto &_hidl_out_retval, const auto &_hidl_out_info) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_retval);
                _hidl_args.push_back((void *)&_hidl_out_info);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "createMmapBuffer", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_retval, _hidl_out_info);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getMmapPosition(getMmapPosition_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::getMmapPosition::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "getMmapPosition", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getMmapPosition([&](const auto &_hidl_out_retval, const auto &_hidl_out_position) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_retval);
                _hidl_args.push_back((void *)&_hidl_out_position);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "getMmapPosition", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_retval, _hidl_out_position);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::audio::V5_0::Result> close() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::close::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStream", "close", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->close();

        ::android::hardware::audio::V5_0::Result _hidl_out_retval = _hidl_return;
        (void) _hidl_out_retval;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_retval);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStream", "close", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }

    // Methods from ::android::hardware::audio::V5_0::IStreamIn follow.
    ::android::hardware::Return<void> getAudioSource(getAudioSource_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::getAudioSource::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStreamIn", "getAudioSource", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getAudioSource([&](const auto &_hidl_out_retval, const auto &_hidl_out_source) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_retval);
                _hidl_args.push_back((void *)&_hidl_out_source);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStreamIn", "getAudioSource", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_retval, _hidl_out_source);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::audio::V5_0::Result> setGain(float gain) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::setGain::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&gain);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStreamIn", "setGain", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->setGain(gain);

        ::android::hardware::audio::V5_0::Result _hidl_out_retval = _hidl_return;
        (void) _hidl_out_retval;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_retval);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStreamIn", "setGain", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> updateSinkMetadata(const ::android::hardware::audio::common::V5_0::SinkMetadata& sinkMetadata) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::updateSinkMetadata::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&sinkMetadata);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStreamIn", "updateSinkMetadata", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->updateSinkMetadata(sinkMetadata);

        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStreamIn", "updateSinkMetadata", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> prepareForReading(uint32_t frameSize, uint32_t framesCount, prepareForReading_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::prepareForReading::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&frameSize);
            _hidl_args.push_back((void *)&framesCount);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStreamIn", "prepareForReading", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->prepareForReading(frameSize, framesCount, [&](const auto &_hidl_out_retval, const auto &_hidl_out_commandMQ, const auto &_hidl_out_dataMQ, const auto &_hidl_out_statusMQ, const auto &_hidl_out_threadInfo) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_retval);
                _hidl_args.push_back((void *)&_hidl_out_commandMQ);
                _hidl_args.push_back((void *)&_hidl_out_dataMQ);
                _hidl_args.push_back((void *)&_hidl_out_statusMQ);
                _hidl_args.push_back((void *)&_hidl_out_threadInfo);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStreamIn", "prepareForReading", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_retval, _hidl_out_commandMQ, _hidl_out_dataMQ, _hidl_out_statusMQ, _hidl_out_threadInfo);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<uint32_t> getInputFramesLost() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::getInputFramesLost::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStreamIn", "getInputFramesLost", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getInputFramesLost();

        uint32_t _hidl_out_framesLost = _hidl_return;
        (void) _hidl_out_framesLost;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_framesLost);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStreamIn", "getInputFramesLost", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getCapturePosition(getCapturePosition_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::getCapturePosition::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStreamIn", "getCapturePosition", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getCapturePosition([&](const auto &_hidl_out_retval, const auto &_hidl_out_frames, const auto &_hidl_out_time) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_retval);
                _hidl_args.push_back((void *)&_hidl_out_frames);
                _hidl_args.push_back((void *)&_hidl_out_time);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStreamIn", "getCapturePosition", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_retval, _hidl_out_frames, _hidl_out_time);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getActiveMicrophones(getActiveMicrophones_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::getActiveMicrophones::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStreamIn", "getActiveMicrophones", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getActiveMicrophones([&](const auto &_hidl_out_retval, const auto &_hidl_out_microphones) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_retval);
                _hidl_args.push_back((void *)&_hidl_out_microphones);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStreamIn", "getActiveMicrophones", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_retval, _hidl_out_microphones);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::audio::V5_0::Result> setMicrophoneDirection(::android::hardware::audio::V5_0::MicrophoneDirection direction) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::setMicrophoneDirection::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&direction);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStreamIn", "setMicrophoneDirection", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->setMicrophoneDirection(direction);

        ::android::hardware::audio::V5_0::Result _hidl_out_retval = _hidl_return;
        (void) _hidl_out_retval;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_retval);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStreamIn", "setMicrophoneDirection", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::audio::V5_0::Result> setMicrophoneFieldDimension(float zoom) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::setMicrophoneFieldDimension::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&zoom);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.audio", "5.0", "IStreamIn", "setMicrophoneFieldDimension", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->setMicrophoneFieldDimension(zoom);

        ::android::hardware::audio::V5_0::Result _hidl_out_retval = _hidl_return;
        (void) _hidl_out_retval;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_retval);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.audio", "5.0", "IStreamIn", "setMicrophoneFieldDimension", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> interfaceChain(interfaceChain_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::interfaceChain::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hidl.base", "1.0", "IBase", "interfaceChain", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->interfaceChain([&](const auto &_hidl_out_descriptors) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_descriptors);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hidl.base", "1.0", "IBase", "interfaceChain", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_descriptors);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> debug(const ::android::hardware::hidl_handle& fd, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& options) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::debug::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&fd);
            _hidl_args.push_back((void *)&options);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hidl.base", "1.0", "IBase", "debug", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->debug(fd, options);

        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hidl.base", "1.0", "IBase", "debug", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> interfaceDescriptor(interfaceDescriptor_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::interfaceDescriptor::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hidl.base", "1.0", "IBase", "interfaceDescriptor", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->interfaceDescriptor([&](const auto &_hidl_out_descriptor) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_descriptor);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hidl.base", "1.0", "IBase", "interfaceDescriptor", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_descriptor);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getHashChain(getHashChain_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::getHashChain::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hidl.base", "1.0", "IBase", "getHashChain", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getHashChain([&](const auto &_hidl_out_hashchain) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_hashchain);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hidl.base", "1.0", "IBase", "getHashChain", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_hashchain);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> setHALInstrumentation() override {
        configureInstrumentation();
        return ::android::hardware::Void();
    }

    ::android::hardware::Return<bool> linkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient, uint64_t cookie) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::linkToDeath::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&recipient);
            _hidl_args.push_back((void *)&cookie);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hidl.base", "1.0", "IBase", "linkToDeath", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->linkToDeath(recipient, cookie);

        bool _hidl_out_success = _hidl_return;
        (void) _hidl_out_success;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_success);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hidl.base", "1.0", "IBase", "linkToDeath", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> ping() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::ping::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hidl.base", "1.0", "IBase", "ping", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->ping();

        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hidl.base", "1.0", "IBase", "ping", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::getDebugInfo::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hidl.base", "1.0", "IBase", "getDebugInfo", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getDebugInfo([&](const auto &_hidl_out_info) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_info);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hidl.base", "1.0", "IBase", "getDebugInfo", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_info);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> notifySyspropsChanged() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::notifySyspropsChanged::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hidl.base", "1.0", "IBase", "notifySyspropsChanged", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        ] {
            mImpl->notifySyspropsChanged();

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hidl.base", "1.0", "IBase", "notifySyspropsChanged", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<bool> unlinkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IStreamIn::unlinkToDeath::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&recipient);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hidl.base", "1.0", "IBase", "unlinkToDeath", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->unlinkToDeath(recipient);

        bool _hidl_out_success = _hidl_return;
        (void) _hidl_out_success;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_success);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hidl.base", "1.0", "IBase", "unlinkToDeath", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }

private:
    const ::android::sp<IStreamIn> mImpl;
    ::android::hardware::details::TaskRunner mOnewayQueue;

    ::android::hardware::Return<void> addOnewayTask(std::function<void(void)>);

};

}  // namespace V5_0
}  // namespace audio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUDIO_V5_0_BSSTREAMIN_H
