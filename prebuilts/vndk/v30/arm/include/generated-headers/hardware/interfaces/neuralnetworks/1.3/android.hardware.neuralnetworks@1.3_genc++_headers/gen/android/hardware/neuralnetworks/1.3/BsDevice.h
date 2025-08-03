#ifndef HIDL_GENERATED_ANDROID_HARDWARE_NEURALNETWORKS_V1_3_BSDEVICE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_NEURALNETWORKS_V1_3_BSDEVICE_H

#include <android-base/macros.h>
#include <cutils/trace.h>
#include <future>
#include <android/hardware/neuralnetworks/1.3/IDevice.h>

#include <hidl/HidlPassthroughSupport.h>
#include <hidl/TaskRunner.h>
namespace android {
namespace hardware {
namespace neuralnetworks {
namespace V1_3 {

struct BsDevice : IDevice, ::android::hardware::details::HidlInstrumentor {
    explicit BsDevice(const ::android::sp<IDevice> impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IDevice Pure;

    typedef ::android::hardware::details::bs_tag _hidl_tag;

    // Methods from ::android::hardware::neuralnetworks::V1_0::IDevice follow.
    ::android::hardware::Return<void> getCapabilities(getCapabilities_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::getCapabilities::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.neuralnetworks", "1.0", "IDevice", "getCapabilities", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getCapabilities([&](const auto &_hidl_out_status, const auto &_hidl_out_capabilities) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_capabilities);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.neuralnetworks", "1.0", "IDevice", "getCapabilities", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_capabilities);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getSupportedOperations(const ::android::hardware::neuralnetworks::V1_0::Model& model, getSupportedOperations_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::getSupportedOperations::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&model);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.neuralnetworks", "1.0", "IDevice", "getSupportedOperations", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getSupportedOperations(model, [&](const auto &_hidl_out_status, const auto &_hidl_out_supportedOperations) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_supportedOperations);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.neuralnetworks", "1.0", "IDevice", "getSupportedOperations", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_supportedOperations);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::neuralnetworks::V1_0::ErrorStatus> prepareModel(const ::android::hardware::neuralnetworks::V1_0::Model& model, const ::android::sp<::android::hardware::neuralnetworks::V1_0::IPreparedModelCallback>& callback) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::prepareModel::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&model);
            _hidl_args.push_back((void *)&callback);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.neuralnetworks", "1.0", "IDevice", "prepareModel", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::sp<::android::hardware::neuralnetworks::V1_0::IPreparedModelCallback> _hidl_wrapped_callback;
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
        auto _hidl_return = mImpl->prepareModel(model, _hidl_wrapped_callback);

        ::android::hardware::neuralnetworks::V1_0::ErrorStatus _hidl_out_status = _hidl_return;
        (void) _hidl_out_status;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_status);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.neuralnetworks", "1.0", "IDevice", "prepareModel", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::neuralnetworks::V1_0::DeviceStatus> getStatus() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::getStatus::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.neuralnetworks", "1.0", "IDevice", "getStatus", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getStatus();

        ::android::hardware::neuralnetworks::V1_0::DeviceStatus _hidl_out_status = _hidl_return;
        (void) _hidl_out_status;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_status);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.neuralnetworks", "1.0", "IDevice", "getStatus", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }

    // Methods from ::android::hardware::neuralnetworks::V1_1::IDevice follow.
    ::android::hardware::Return<void> getCapabilities_1_1(getCapabilities_1_1_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::getCapabilities_1_1::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.neuralnetworks", "1.1", "IDevice", "getCapabilities_1_1", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getCapabilities_1_1([&](const auto &_hidl_out_status, const auto &_hidl_out_capabilities) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_capabilities);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.neuralnetworks", "1.1", "IDevice", "getCapabilities_1_1", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_capabilities);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getSupportedOperations_1_1(const ::android::hardware::neuralnetworks::V1_1::Model& model, getSupportedOperations_1_1_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::getSupportedOperations_1_1::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&model);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.neuralnetworks", "1.1", "IDevice", "getSupportedOperations_1_1", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getSupportedOperations_1_1(model, [&](const auto &_hidl_out_status, const auto &_hidl_out_supportedOperations) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_supportedOperations);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.neuralnetworks", "1.1", "IDevice", "getSupportedOperations_1_1", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_supportedOperations);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::neuralnetworks::V1_0::ErrorStatus> prepareModel_1_1(const ::android::hardware::neuralnetworks::V1_1::Model& model, ::android::hardware::neuralnetworks::V1_1::ExecutionPreference preference, const ::android::sp<::android::hardware::neuralnetworks::V1_0::IPreparedModelCallback>& callback) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::prepareModel_1_1::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&model);
            _hidl_args.push_back((void *)&preference);
            _hidl_args.push_back((void *)&callback);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.neuralnetworks", "1.1", "IDevice", "prepareModel_1_1", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::sp<::android::hardware::neuralnetworks::V1_0::IPreparedModelCallback> _hidl_wrapped_callback;
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
        auto _hidl_return = mImpl->prepareModel_1_1(model, preference, _hidl_wrapped_callback);

        ::android::hardware::neuralnetworks::V1_0::ErrorStatus _hidl_out_status = _hidl_return;
        (void) _hidl_out_status;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_status);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.neuralnetworks", "1.1", "IDevice", "prepareModel_1_1", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }

    // Methods from ::android::hardware::neuralnetworks::V1_2::IDevice follow.
    ::android::hardware::Return<void> getVersionString(getVersionString_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::getVersionString::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.neuralnetworks", "1.2", "IDevice", "getVersionString", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getVersionString([&](const auto &_hidl_out_status, const auto &_hidl_out_version) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_version);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.neuralnetworks", "1.2", "IDevice", "getVersionString", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_version);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getType(getType_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::getType::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.neuralnetworks", "1.2", "IDevice", "getType", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getType([&](const auto &_hidl_out_status, const auto &_hidl_out_type) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_type);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.neuralnetworks", "1.2", "IDevice", "getType", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_type);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getCapabilities_1_2(getCapabilities_1_2_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::getCapabilities_1_2::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.neuralnetworks", "1.2", "IDevice", "getCapabilities_1_2", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getCapabilities_1_2([&](const auto &_hidl_out_status, const auto &_hidl_out_capabilities) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_capabilities);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.neuralnetworks", "1.2", "IDevice", "getCapabilities_1_2", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_capabilities);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getSupportedExtensions(getSupportedExtensions_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::getSupportedExtensions::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.neuralnetworks", "1.2", "IDevice", "getSupportedExtensions", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getSupportedExtensions([&](const auto &_hidl_out_status, const auto &_hidl_out_extensions) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_extensions);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.neuralnetworks", "1.2", "IDevice", "getSupportedExtensions", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_extensions);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getSupportedOperations_1_2(const ::android::hardware::neuralnetworks::V1_2::Model& model, getSupportedOperations_1_2_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::getSupportedOperations_1_2::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&model);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.neuralnetworks", "1.2", "IDevice", "getSupportedOperations_1_2", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getSupportedOperations_1_2(model, [&](const auto &_hidl_out_status, const auto &_hidl_out_supportedOperations) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_supportedOperations);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.neuralnetworks", "1.2", "IDevice", "getSupportedOperations_1_2", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_supportedOperations);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getNumberOfCacheFilesNeeded(getNumberOfCacheFilesNeeded_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::getNumberOfCacheFilesNeeded::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.neuralnetworks", "1.2", "IDevice", "getNumberOfCacheFilesNeeded", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getNumberOfCacheFilesNeeded([&](const auto &_hidl_out_status, const auto &_hidl_out_numModelCache, const auto &_hidl_out_numDataCache) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_numModelCache);
                _hidl_args.push_back((void *)&_hidl_out_numDataCache);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.neuralnetworks", "1.2", "IDevice", "getNumberOfCacheFilesNeeded", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_numModelCache, _hidl_out_numDataCache);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::neuralnetworks::V1_0::ErrorStatus> prepareModel_1_2(const ::android::hardware::neuralnetworks::V1_2::Model& model, ::android::hardware::neuralnetworks::V1_1::ExecutionPreference preference, const ::android::hardware::hidl_vec<::android::hardware::hidl_handle>& modelCache, const ::android::hardware::hidl_vec<::android::hardware::hidl_handle>& dataCache, const ::android::hardware::hidl_array<uint8_t, 32 /* Constant:BYTE_SIZE_OF_CACHE_TOKEN */>& token, const ::android::sp<::android::hardware::neuralnetworks::V1_2::IPreparedModelCallback>& callback) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::prepareModel_1_2::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&model);
            _hidl_args.push_back((void *)&preference);
            _hidl_args.push_back((void *)&modelCache);
            _hidl_args.push_back((void *)&dataCache);
            _hidl_args.push_back((void *)&token);
            _hidl_args.push_back((void *)&callback);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.neuralnetworks", "1.2", "IDevice", "prepareModel_1_2", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::sp<::android::hardware::neuralnetworks::V1_2::IPreparedModelCallback> _hidl_wrapped_callback;
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
        auto _hidl_return = mImpl->prepareModel_1_2(model, preference, modelCache, dataCache, token, _hidl_wrapped_callback);

        ::android::hardware::neuralnetworks::V1_0::ErrorStatus _hidl_out_status = _hidl_return;
        (void) _hidl_out_status;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_status);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.neuralnetworks", "1.2", "IDevice", "prepareModel_1_2", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::neuralnetworks::V1_0::ErrorStatus> prepareModelFromCache(const ::android::hardware::hidl_vec<::android::hardware::hidl_handle>& modelCache, const ::android::hardware::hidl_vec<::android::hardware::hidl_handle>& dataCache, const ::android::hardware::hidl_array<uint8_t, 32 /* Constant:BYTE_SIZE_OF_CACHE_TOKEN */>& token, const ::android::sp<::android::hardware::neuralnetworks::V1_2::IPreparedModelCallback>& callback) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::prepareModelFromCache::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&modelCache);
            _hidl_args.push_back((void *)&dataCache);
            _hidl_args.push_back((void *)&token);
            _hidl_args.push_back((void *)&callback);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.neuralnetworks", "1.2", "IDevice", "prepareModelFromCache", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::sp<::android::hardware::neuralnetworks::V1_2::IPreparedModelCallback> _hidl_wrapped_callback;
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
        auto _hidl_return = mImpl->prepareModelFromCache(modelCache, dataCache, token, _hidl_wrapped_callback);

        ::android::hardware::neuralnetworks::V1_0::ErrorStatus _hidl_out_status = _hidl_return;
        (void) _hidl_out_status;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_status);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.neuralnetworks", "1.2", "IDevice", "prepareModelFromCache", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }

    // Methods from ::android::hardware::neuralnetworks::V1_3::IDevice follow.
    ::android::hardware::Return<void> getCapabilities_1_3(getCapabilities_1_3_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::getCapabilities_1_3::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.neuralnetworks", "1.3", "IDevice", "getCapabilities_1_3", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getCapabilities_1_3([&](const auto &_hidl_out_status, const auto &_hidl_out_capabilities) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_capabilities);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.neuralnetworks", "1.3", "IDevice", "getCapabilities_1_3", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_capabilities);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getSupportedOperations_1_3(const ::android::hardware::neuralnetworks::V1_3::Model& model, getSupportedOperations_1_3_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::getSupportedOperations_1_3::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&model);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.neuralnetworks", "1.3", "IDevice", "getSupportedOperations_1_3", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getSupportedOperations_1_3(model, [&](const auto &_hidl_out_status, const auto &_hidl_out_supportedOperations) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_supportedOperations);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.neuralnetworks", "1.3", "IDevice", "getSupportedOperations_1_3", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_supportedOperations);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::neuralnetworks::V1_3::ErrorStatus> prepareModel_1_3(const ::android::hardware::neuralnetworks::V1_3::Model& model, ::android::hardware::neuralnetworks::V1_1::ExecutionPreference preference, ::android::hardware::neuralnetworks::V1_3::Priority priority, const ::android::hardware::neuralnetworks::V1_3::OptionalTimePoint& deadline, const ::android::hardware::hidl_vec<::android::hardware::hidl_handle>& modelCache, const ::android::hardware::hidl_vec<::android::hardware::hidl_handle>& dataCache, const ::android::hardware::hidl_array<uint8_t, 32 /* Constant:BYTE_SIZE_OF_CACHE_TOKEN */>& token, const ::android::sp<::android::hardware::neuralnetworks::V1_3::IPreparedModelCallback>& callback) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::prepareModel_1_3::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&model);
            _hidl_args.push_back((void *)&preference);
            _hidl_args.push_back((void *)&priority);
            _hidl_args.push_back((void *)&deadline);
            _hidl_args.push_back((void *)&modelCache);
            _hidl_args.push_back((void *)&dataCache);
            _hidl_args.push_back((void *)&token);
            _hidl_args.push_back((void *)&callback);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.neuralnetworks", "1.3", "IDevice", "prepareModel_1_3", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::sp<::android::hardware::neuralnetworks::V1_3::IPreparedModelCallback> _hidl_wrapped_callback;
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
        auto _hidl_return = mImpl->prepareModel_1_3(model, preference, priority, deadline, modelCache, dataCache, token, _hidl_wrapped_callback);

        ::android::hardware::neuralnetworks::V1_3::ErrorStatus _hidl_out_status = _hidl_return;
        (void) _hidl_out_status;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_status);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.neuralnetworks", "1.3", "IDevice", "prepareModel_1_3", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::neuralnetworks::V1_3::ErrorStatus> prepareModelFromCache_1_3(const ::android::hardware::neuralnetworks::V1_3::OptionalTimePoint& deadline, const ::android::hardware::hidl_vec<::android::hardware::hidl_handle>& modelCache, const ::android::hardware::hidl_vec<::android::hardware::hidl_handle>& dataCache, const ::android::hardware::hidl_array<uint8_t, 32 /* Constant:BYTE_SIZE_OF_CACHE_TOKEN */>& token, const ::android::sp<::android::hardware::neuralnetworks::V1_3::IPreparedModelCallback>& callback) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::prepareModelFromCache_1_3::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&deadline);
            _hidl_args.push_back((void *)&modelCache);
            _hidl_args.push_back((void *)&dataCache);
            _hidl_args.push_back((void *)&token);
            _hidl_args.push_back((void *)&callback);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.neuralnetworks", "1.3", "IDevice", "prepareModelFromCache_1_3", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::sp<::android::hardware::neuralnetworks::V1_3::IPreparedModelCallback> _hidl_wrapped_callback;
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
        auto _hidl_return = mImpl->prepareModelFromCache_1_3(deadline, modelCache, dataCache, token, _hidl_wrapped_callback);

        ::android::hardware::neuralnetworks::V1_3::ErrorStatus _hidl_out_status = _hidl_return;
        (void) _hidl_out_status;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_status);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.neuralnetworks", "1.3", "IDevice", "prepareModelFromCache_1_3", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> allocate(const ::android::hardware::neuralnetworks::V1_3::BufferDesc& desc, const ::android::hardware::hidl_vec<::android::sp<::android::hardware::neuralnetworks::V1_3::IPreparedModel>>& preparedModels, const ::android::hardware::hidl_vec<::android::hardware::neuralnetworks::V1_3::BufferRole>& inputRoles, const ::android::hardware::hidl_vec<::android::hardware::neuralnetworks::V1_3::BufferRole>& outputRoles, allocate_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::allocate::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&desc);
            _hidl_args.push_back((void *)&preparedModels);
            _hidl_args.push_back((void *)&inputRoles);
            _hidl_args.push_back((void *)&outputRoles);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.neuralnetworks", "1.3", "IDevice", "allocate", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->allocate(desc, preparedModels, inputRoles, outputRoles, [&](const auto &_hidl_out_status, const auto &_hidl_out_buffer, const auto &_hidl_out_token) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_buffer);
                _hidl_args.push_back((void *)&_hidl_out_token);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.neuralnetworks", "1.3", "IDevice", "allocate", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            ::android::sp<::android::hardware::neuralnetworks::V1_3::IBuffer> _hidl_wrapped__hidl_out_buffer;
            if (_hidl_out_buffer != nullptr && !_hidl_out_buffer->isRemote()) {
                _hidl_wrapped__hidl_out_buffer = ::android::hardware::details::wrapPassthrough(_hidl_out_buffer);
                if (_hidl_wrapped__hidl_out_buffer == nullptr) {
                    _hidl_error = ::android::hardware::Status::fromExceptionCode(
                            ::android::hardware::Status::EX_TRANSACTION_FAILED,
                            "Cannot wrap passthrough interface.");
                }
            } else {
                _hidl_wrapped__hidl_out_buffer = _hidl_out_buffer;
            }

            _hidl_cb(_hidl_out_status, _hidl_wrapped__hidl_out_buffer, _hidl_out_token);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> interfaceChain(interfaceChain_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::interfaceChain::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::debug::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::interfaceDescriptor::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::getHashChain::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::linkToDeath::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::ping::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::getDebugInfo::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::notifySyspropsChanged::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IDevice::unlinkToDeath::passthrough");
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
    const ::android::sp<IDevice> mImpl;
    ::android::hardware::details::TaskRunner mOnewayQueue;

    ::android::hardware::Return<void> addOnewayTask(std::function<void(void)>);

};

}  // namespace V1_3
}  // namespace neuralnetworks
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_NEURALNETWORKS_V1_3_BSDEVICE_H
