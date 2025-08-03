#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_BSGNSS_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_BSGNSS_H

#include <android-base/macros.h>
#include <cutils/trace.h>
#include <future>
#include <android/hardware/gnss/1.0/IGnss.h>

#include <hidl/HidlPassthroughSupport.h>
#include <hidl/TaskRunner.h>
namespace android {
namespace hardware {
namespace gnss {
namespace V1_0 {

struct BsGnss : IGnss, ::android::hardware::details::HidlInstrumentor {
    explicit BsGnss(const ::android::sp<IGnss> impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IGnss Pure;

    typedef ::android::hardware::details::bs_tag _hidl_tag;

    // Methods from ::android::hardware::gnss::V1_0::IGnss follow.
    ::android::hardware::Return<bool> setCallback(const ::android::sp<::android::hardware::gnss::V1_0::IGnssCallback>& callback) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::setCallback::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&callback);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.gnss", "1.0", "IGnss", "setCallback", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::sp<::android::hardware::gnss::V1_0::IGnssCallback> _hidl_wrapped_callback;
        if (callback != nullptr && !callback->isRemote()) {
            _hidl_wrapped_callback = ::android::hardware::details::wrapPassthrough(callback);
            if (_hidl_wrapped_callback == nullptr) {
                return ::android::hardware::Status::fromExceptionCode(
                        ::android::hardware::Status::EX_TRANSACTION_FAILED,
                        "Cannot wrap passthrough interface.");
            }
        } else {
            _hidl_wrapped_callback = callback;
        }

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->setCallback(_hidl_wrapped_callback);

        bool _hidl_out_success = _hidl_return;
        (void) _hidl_out_success;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_success);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.gnss", "1.0", "IGnss", "setCallback", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<bool> start() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::start::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.gnss", "1.0", "IGnss", "start", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->start();

        bool _hidl_out_success = _hidl_return;
        (void) _hidl_out_success;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_success);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.gnss", "1.0", "IGnss", "start", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<bool> stop() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::stop::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.gnss", "1.0", "IGnss", "stop", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->stop();

        bool _hidl_out_success = _hidl_return;
        (void) _hidl_out_success;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_success);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.gnss", "1.0", "IGnss", "stop", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> cleanup() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::cleanup::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.gnss", "1.0", "IGnss", "cleanup", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->cleanup();

        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.gnss", "1.0", "IGnss", "cleanup", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<bool> injectTime(int64_t timeMs, int64_t timeReferenceMs, int32_t uncertaintyMs) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::injectTime::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&timeMs);
            _hidl_args.push_back((void *)&timeReferenceMs);
            _hidl_args.push_back((void *)&uncertaintyMs);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.gnss", "1.0", "IGnss", "injectTime", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->injectTime(timeMs, timeReferenceMs, uncertaintyMs);

        bool _hidl_out_success = _hidl_return;
        (void) _hidl_out_success;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_success);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.gnss", "1.0", "IGnss", "injectTime", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<bool> injectLocation(double latitudeDegrees, double longitudeDegrees, float accuracyMeters) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::injectLocation::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&latitudeDegrees);
            _hidl_args.push_back((void *)&longitudeDegrees);
            _hidl_args.push_back((void *)&accuracyMeters);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.gnss", "1.0", "IGnss", "injectLocation", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->injectLocation(latitudeDegrees, longitudeDegrees, accuracyMeters);

        bool _hidl_out_success = _hidl_return;
        (void) _hidl_out_success;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_success);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.gnss", "1.0", "IGnss", "injectLocation", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> deleteAidingData(::android::hardware::gnss::V1_0::IGnss::GnssAidingData aidingDataFlags) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::deleteAidingData::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&aidingDataFlags);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.gnss", "1.0", "IGnss", "deleteAidingData", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->deleteAidingData(aidingDataFlags);

        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.gnss", "1.0", "IGnss", "deleteAidingData", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<bool> setPositionMode(::android::hardware::gnss::V1_0::IGnss::GnssPositionMode mode, ::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence recurrence, uint32_t minIntervalMs, uint32_t preferredAccuracyMeters, uint32_t preferredTimeMs) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::setPositionMode::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&mode);
            _hidl_args.push_back((void *)&recurrence);
            _hidl_args.push_back((void *)&minIntervalMs);
            _hidl_args.push_back((void *)&preferredAccuracyMeters);
            _hidl_args.push_back((void *)&preferredTimeMs);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.gnss", "1.0", "IGnss", "setPositionMode", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->setPositionMode(mode, recurrence, minIntervalMs, preferredAccuracyMeters, preferredTimeMs);

        bool _hidl_out_success = _hidl_return;
        (void) _hidl_out_success;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_success);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.gnss", "1.0", "IGnss", "setPositionMode", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IAGnssRil>> getExtensionAGnssRil() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::getExtensionAGnssRil::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.gnss", "1.0", "IGnss", "getExtensionAGnssRil", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getExtensionAGnssRil();

        ::android::sp<::android::hardware::gnss::V1_0::IAGnssRil> _hidl_out_aGnssRilIface = _hidl_return;
        (void) _hidl_out_aGnssRilIface;
        ::android::sp<::android::hardware::gnss::V1_0::IAGnssRil> _hidl_wrapped__hidl_out_aGnssRilIface;
        if (_hidl_out_aGnssRilIface != nullptr && !_hidl_out_aGnssRilIface->isRemote()) {
            _hidl_wrapped__hidl_out_aGnssRilIface = ::android::hardware::details::wrapPassthrough(_hidl_out_aGnssRilIface);
            if (_hidl_wrapped__hidl_out_aGnssRilIface == nullptr) {
                _hidl_error = ::android::hardware::Status::fromExceptionCode(
                        ::android::hardware::Status::EX_TRANSACTION_FAILED,
                        "Cannot wrap passthrough interface.");
            }
        } else {
            _hidl_wrapped__hidl_out_aGnssRilIface = _hidl_out_aGnssRilIface;
        }

        _hidl_out_aGnssRilIface = _hidl_wrapped__hidl_out_aGnssRilIface;

        _hidl_return = _hidl_out_aGnssRilIface
        ;atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_aGnssRilIface);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.gnss", "1.0", "IGnss", "getExtensionAGnssRil", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssGeofencing>> getExtensionGnssGeofencing() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::getExtensionGnssGeofencing::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.gnss", "1.0", "IGnss", "getExtensionGnssGeofencing", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getExtensionGnssGeofencing();

        ::android::sp<::android::hardware::gnss::V1_0::IGnssGeofencing> _hidl_out_gnssGeofencingIface = _hidl_return;
        (void) _hidl_out_gnssGeofencingIface;
        ::android::sp<::android::hardware::gnss::V1_0::IGnssGeofencing> _hidl_wrapped__hidl_out_gnssGeofencingIface;
        if (_hidl_out_gnssGeofencingIface != nullptr && !_hidl_out_gnssGeofencingIface->isRemote()) {
            _hidl_wrapped__hidl_out_gnssGeofencingIface = ::android::hardware::details::wrapPassthrough(_hidl_out_gnssGeofencingIface);
            if (_hidl_wrapped__hidl_out_gnssGeofencingIface == nullptr) {
                _hidl_error = ::android::hardware::Status::fromExceptionCode(
                        ::android::hardware::Status::EX_TRANSACTION_FAILED,
                        "Cannot wrap passthrough interface.");
            }
        } else {
            _hidl_wrapped__hidl_out_gnssGeofencingIface = _hidl_out_gnssGeofencingIface;
        }

        _hidl_out_gnssGeofencingIface = _hidl_wrapped__hidl_out_gnssGeofencingIface;

        _hidl_return = _hidl_out_gnssGeofencingIface
        ;atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_gnssGeofencingIface);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.gnss", "1.0", "IGnss", "getExtensionGnssGeofencing", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IAGnss>> getExtensionAGnss() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::getExtensionAGnss::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.gnss", "1.0", "IGnss", "getExtensionAGnss", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getExtensionAGnss();

        ::android::sp<::android::hardware::gnss::V1_0::IAGnss> _hidl_out_aGnssIface = _hidl_return;
        (void) _hidl_out_aGnssIface;
        ::android::sp<::android::hardware::gnss::V1_0::IAGnss> _hidl_wrapped__hidl_out_aGnssIface;
        if (_hidl_out_aGnssIface != nullptr && !_hidl_out_aGnssIface->isRemote()) {
            _hidl_wrapped__hidl_out_aGnssIface = ::android::hardware::details::wrapPassthrough(_hidl_out_aGnssIface);
            if (_hidl_wrapped__hidl_out_aGnssIface == nullptr) {
                _hidl_error = ::android::hardware::Status::fromExceptionCode(
                        ::android::hardware::Status::EX_TRANSACTION_FAILED,
                        "Cannot wrap passthrough interface.");
            }
        } else {
            _hidl_wrapped__hidl_out_aGnssIface = _hidl_out_aGnssIface;
        }

        _hidl_out_aGnssIface = _hidl_wrapped__hidl_out_aGnssIface;

        _hidl_return = _hidl_out_aGnssIface
        ;atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_aGnssIface);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.gnss", "1.0", "IGnss", "getExtensionAGnss", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssNi>> getExtensionGnssNi() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::getExtensionGnssNi::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.gnss", "1.0", "IGnss", "getExtensionGnssNi", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getExtensionGnssNi();

        ::android::sp<::android::hardware::gnss::V1_0::IGnssNi> _hidl_out_gnssNiIface = _hidl_return;
        (void) _hidl_out_gnssNiIface;
        ::android::sp<::android::hardware::gnss::V1_0::IGnssNi> _hidl_wrapped__hidl_out_gnssNiIface;
        if (_hidl_out_gnssNiIface != nullptr && !_hidl_out_gnssNiIface->isRemote()) {
            _hidl_wrapped__hidl_out_gnssNiIface = ::android::hardware::details::wrapPassthrough(_hidl_out_gnssNiIface);
            if (_hidl_wrapped__hidl_out_gnssNiIface == nullptr) {
                _hidl_error = ::android::hardware::Status::fromExceptionCode(
                        ::android::hardware::Status::EX_TRANSACTION_FAILED,
                        "Cannot wrap passthrough interface.");
            }
        } else {
            _hidl_wrapped__hidl_out_gnssNiIface = _hidl_out_gnssNiIface;
        }

        _hidl_out_gnssNiIface = _hidl_wrapped__hidl_out_gnssNiIface;

        _hidl_return = _hidl_out_gnssNiIface
        ;atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_gnssNiIface);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.gnss", "1.0", "IGnss", "getExtensionGnssNi", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssMeasurement>> getExtensionGnssMeasurement() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::getExtensionGnssMeasurement::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.gnss", "1.0", "IGnss", "getExtensionGnssMeasurement", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getExtensionGnssMeasurement();

        ::android::sp<::android::hardware::gnss::V1_0::IGnssMeasurement> _hidl_out_gnssMeasurementIface = _hidl_return;
        (void) _hidl_out_gnssMeasurementIface;
        ::android::sp<::android::hardware::gnss::V1_0::IGnssMeasurement> _hidl_wrapped__hidl_out_gnssMeasurementIface;
        if (_hidl_out_gnssMeasurementIface != nullptr && !_hidl_out_gnssMeasurementIface->isRemote()) {
            _hidl_wrapped__hidl_out_gnssMeasurementIface = ::android::hardware::details::wrapPassthrough(_hidl_out_gnssMeasurementIface);
            if (_hidl_wrapped__hidl_out_gnssMeasurementIface == nullptr) {
                _hidl_error = ::android::hardware::Status::fromExceptionCode(
                        ::android::hardware::Status::EX_TRANSACTION_FAILED,
                        "Cannot wrap passthrough interface.");
            }
        } else {
            _hidl_wrapped__hidl_out_gnssMeasurementIface = _hidl_out_gnssMeasurementIface;
        }

        _hidl_out_gnssMeasurementIface = _hidl_wrapped__hidl_out_gnssMeasurementIface;

        _hidl_return = _hidl_out_gnssMeasurementIface
        ;atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_gnssMeasurementIface);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.gnss", "1.0", "IGnss", "getExtensionGnssMeasurement", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssNavigationMessage>> getExtensionGnssNavigationMessage() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::getExtensionGnssNavigationMessage::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.gnss", "1.0", "IGnss", "getExtensionGnssNavigationMessage", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getExtensionGnssNavigationMessage();

        ::android::sp<::android::hardware::gnss::V1_0::IGnssNavigationMessage> _hidl_out_gnssNavigationIface = _hidl_return;
        (void) _hidl_out_gnssNavigationIface;
        ::android::sp<::android::hardware::gnss::V1_0::IGnssNavigationMessage> _hidl_wrapped__hidl_out_gnssNavigationIface;
        if (_hidl_out_gnssNavigationIface != nullptr && !_hidl_out_gnssNavigationIface->isRemote()) {
            _hidl_wrapped__hidl_out_gnssNavigationIface = ::android::hardware::details::wrapPassthrough(_hidl_out_gnssNavigationIface);
            if (_hidl_wrapped__hidl_out_gnssNavigationIface == nullptr) {
                _hidl_error = ::android::hardware::Status::fromExceptionCode(
                        ::android::hardware::Status::EX_TRANSACTION_FAILED,
                        "Cannot wrap passthrough interface.");
            }
        } else {
            _hidl_wrapped__hidl_out_gnssNavigationIface = _hidl_out_gnssNavigationIface;
        }

        _hidl_out_gnssNavigationIface = _hidl_wrapped__hidl_out_gnssNavigationIface;

        _hidl_return = _hidl_out_gnssNavigationIface
        ;atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_gnssNavigationIface);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.gnss", "1.0", "IGnss", "getExtensionGnssNavigationMessage", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssXtra>> getExtensionXtra() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::getExtensionXtra::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.gnss", "1.0", "IGnss", "getExtensionXtra", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getExtensionXtra();

        ::android::sp<::android::hardware::gnss::V1_0::IGnssXtra> _hidl_out_xtraIface = _hidl_return;
        (void) _hidl_out_xtraIface;
        ::android::sp<::android::hardware::gnss::V1_0::IGnssXtra> _hidl_wrapped__hidl_out_xtraIface;
        if (_hidl_out_xtraIface != nullptr && !_hidl_out_xtraIface->isRemote()) {
            _hidl_wrapped__hidl_out_xtraIface = ::android::hardware::details::wrapPassthrough(_hidl_out_xtraIface);
            if (_hidl_wrapped__hidl_out_xtraIface == nullptr) {
                _hidl_error = ::android::hardware::Status::fromExceptionCode(
                        ::android::hardware::Status::EX_TRANSACTION_FAILED,
                        "Cannot wrap passthrough interface.");
            }
        } else {
            _hidl_wrapped__hidl_out_xtraIface = _hidl_out_xtraIface;
        }

        _hidl_out_xtraIface = _hidl_wrapped__hidl_out_xtraIface;

        _hidl_return = _hidl_out_xtraIface
        ;atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_xtraIface);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.gnss", "1.0", "IGnss", "getExtensionXtra", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssConfiguration>> getExtensionGnssConfiguration() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::getExtensionGnssConfiguration::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.gnss", "1.0", "IGnss", "getExtensionGnssConfiguration", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getExtensionGnssConfiguration();

        ::android::sp<::android::hardware::gnss::V1_0::IGnssConfiguration> _hidl_out_gnssConfigIface = _hidl_return;
        (void) _hidl_out_gnssConfigIface;
        ::android::sp<::android::hardware::gnss::V1_0::IGnssConfiguration> _hidl_wrapped__hidl_out_gnssConfigIface;
        if (_hidl_out_gnssConfigIface != nullptr && !_hidl_out_gnssConfigIface->isRemote()) {
            _hidl_wrapped__hidl_out_gnssConfigIface = ::android::hardware::details::wrapPassthrough(_hidl_out_gnssConfigIface);
            if (_hidl_wrapped__hidl_out_gnssConfigIface == nullptr) {
                _hidl_error = ::android::hardware::Status::fromExceptionCode(
                        ::android::hardware::Status::EX_TRANSACTION_FAILED,
                        "Cannot wrap passthrough interface.");
            }
        } else {
            _hidl_wrapped__hidl_out_gnssConfigIface = _hidl_out_gnssConfigIface;
        }

        _hidl_out_gnssConfigIface = _hidl_wrapped__hidl_out_gnssConfigIface;

        _hidl_return = _hidl_out_gnssConfigIface
        ;atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_gnssConfigIface);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.gnss", "1.0", "IGnss", "getExtensionGnssConfiguration", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssDebug>> getExtensionGnssDebug() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::getExtensionGnssDebug::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.gnss", "1.0", "IGnss", "getExtensionGnssDebug", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getExtensionGnssDebug();

        ::android::sp<::android::hardware::gnss::V1_0::IGnssDebug> _hidl_out_debugIface = _hidl_return;
        (void) _hidl_out_debugIface;
        ::android::sp<::android::hardware::gnss::V1_0::IGnssDebug> _hidl_wrapped__hidl_out_debugIface;
        if (_hidl_out_debugIface != nullptr && !_hidl_out_debugIface->isRemote()) {
            _hidl_wrapped__hidl_out_debugIface = ::android::hardware::details::wrapPassthrough(_hidl_out_debugIface);
            if (_hidl_wrapped__hidl_out_debugIface == nullptr) {
                _hidl_error = ::android::hardware::Status::fromExceptionCode(
                        ::android::hardware::Status::EX_TRANSACTION_FAILED,
                        "Cannot wrap passthrough interface.");
            }
        } else {
            _hidl_wrapped__hidl_out_debugIface = _hidl_out_debugIface;
        }

        _hidl_out_debugIface = _hidl_wrapped__hidl_out_debugIface;

        _hidl_return = _hidl_out_debugIface
        ;atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_debugIface);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.gnss", "1.0", "IGnss", "getExtensionGnssDebug", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssBatching>> getExtensionGnssBatching() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::getExtensionGnssBatching::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.gnss", "1.0", "IGnss", "getExtensionGnssBatching", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getExtensionGnssBatching();

        ::android::sp<::android::hardware::gnss::V1_0::IGnssBatching> _hidl_out_batchingIface = _hidl_return;
        (void) _hidl_out_batchingIface;
        ::android::sp<::android::hardware::gnss::V1_0::IGnssBatching> _hidl_wrapped__hidl_out_batchingIface;
        if (_hidl_out_batchingIface != nullptr && !_hidl_out_batchingIface->isRemote()) {
            _hidl_wrapped__hidl_out_batchingIface = ::android::hardware::details::wrapPassthrough(_hidl_out_batchingIface);
            if (_hidl_wrapped__hidl_out_batchingIface == nullptr) {
                _hidl_error = ::android::hardware::Status::fromExceptionCode(
                        ::android::hardware::Status::EX_TRANSACTION_FAILED,
                        "Cannot wrap passthrough interface.");
            }
        } else {
            _hidl_wrapped__hidl_out_batchingIface = _hidl_out_batchingIface;
        }

        _hidl_out_batchingIface = _hidl_wrapped__hidl_out_batchingIface;

        _hidl_return = _hidl_out_batchingIface
        ;atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_batchingIface);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.gnss", "1.0", "IGnss", "getExtensionGnssBatching", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> interfaceChain(interfaceChain_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::interfaceChain::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::debug::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::interfaceDescriptor::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::getHashChain::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::linkToDeath::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::ping::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::getDebugInfo::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::notifySyspropsChanged::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IGnss::unlinkToDeath::passthrough");
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
    const ::android::sp<IGnss> mImpl;
    ::android::hardware::details::TaskRunner mOnewayQueue;

    ::android::hardware::Return<void> addOnewayTask(std::function<void(void)>);

};

}  // namespace V1_0
}  // namespace gnss
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_BSGNSS_H
