#ifndef HIDL_GENERATED_ANDROID_HARDWARE_KEYMASTER_V4_1_BSKEYMASTERDEVICE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_KEYMASTER_V4_1_BSKEYMASTERDEVICE_H

#include <android-base/macros.h>
#include <cutils/trace.h>
#include <future>
#include <android/hardware/keymaster/4.1/IKeymasterDevice.h>

#include <hidl/HidlPassthroughSupport.h>
#include <hidl/TaskRunner.h>
namespace android {
namespace hardware {
namespace keymaster {
namespace V4_1 {

struct BsKeymasterDevice : IKeymasterDevice, ::android::hardware::details::HidlInstrumentor {
    explicit BsKeymasterDevice(const ::android::sp<IKeymasterDevice> impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IKeymasterDevice Pure;

    typedef ::android::hardware::details::bs_tag _hidl_tag;

    // Methods from ::android::hardware::keymaster::V4_0::IKeymasterDevice follow.
    ::android::hardware::Return<void> getHardwareInfo(getHardwareInfo_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::getHardwareInfo::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "getHardwareInfo", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getHardwareInfo([&](const auto &_hidl_out_securityLevel, const auto &_hidl_out_keymasterName, const auto &_hidl_out_keymasterAuthorName) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_securityLevel);
                _hidl_args.push_back((void *)&_hidl_out_keymasterName);
                _hidl_args.push_back((void *)&_hidl_out_keymasterAuthorName);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "getHardwareInfo", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_securityLevel, _hidl_out_keymasterName, _hidl_out_keymasterAuthorName);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getHmacSharingParameters(getHmacSharingParameters_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::getHmacSharingParameters::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "getHmacSharingParameters", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getHmacSharingParameters([&](const auto &_hidl_out_error, const auto &_hidl_out_params) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_error);
                _hidl_args.push_back((void *)&_hidl_out_params);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "getHmacSharingParameters", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_error, _hidl_out_params);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> computeSharedHmac(const ::android::hardware::hidl_vec<::android::hardware::keymaster::V4_0::HmacSharingParameters>& params, computeSharedHmac_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::computeSharedHmac::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&params);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "computeSharedHmac", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->computeSharedHmac(params, [&](const auto &_hidl_out_error, const auto &_hidl_out_sharingCheck) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_error);
                _hidl_args.push_back((void *)&_hidl_out_sharingCheck);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "computeSharedHmac", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_error, _hidl_out_sharingCheck);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> verifyAuthorization(uint64_t operationHandle, const ::android::hardware::hidl_vec<::android::hardware::keymaster::V4_0::KeyParameter>& parametersToVerify, const ::android::hardware::keymaster::V4_0::HardwareAuthToken& authToken, verifyAuthorization_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::verifyAuthorization::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&operationHandle);
            _hidl_args.push_back((void *)&parametersToVerify);
            _hidl_args.push_back((void *)&authToken);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "verifyAuthorization", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->verifyAuthorization(operationHandle, parametersToVerify, authToken, [&](const auto &_hidl_out_error, const auto &_hidl_out_token) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_error);
                _hidl_args.push_back((void *)&_hidl_out_token);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "verifyAuthorization", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_error, _hidl_out_token);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::keymaster::V4_0::ErrorCode> addRngEntropy(const ::android::hardware::hidl_vec<uint8_t>& data) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::addRngEntropy::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&data);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "addRngEntropy", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->addRngEntropy(data);

        ::android::hardware::keymaster::V4_0::ErrorCode _hidl_out_error = _hidl_return;
        (void) _hidl_out_error;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_error);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "addRngEntropy", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> generateKey(const ::android::hardware::hidl_vec<::android::hardware::keymaster::V4_0::KeyParameter>& keyParams, generateKey_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::generateKey::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&keyParams);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "generateKey", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->generateKey(keyParams, [&](const auto &_hidl_out_error, const auto &_hidl_out_keyBlob, const auto &_hidl_out_keyCharacteristics) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_error);
                _hidl_args.push_back((void *)&_hidl_out_keyBlob);
                _hidl_args.push_back((void *)&_hidl_out_keyCharacteristics);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "generateKey", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_error, _hidl_out_keyBlob, _hidl_out_keyCharacteristics);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> importKey(const ::android::hardware::hidl_vec<::android::hardware::keymaster::V4_0::KeyParameter>& keyParams, ::android::hardware::keymaster::V4_0::KeyFormat keyFormat, const ::android::hardware::hidl_vec<uint8_t>& keyData, importKey_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::importKey::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&keyParams);
            _hidl_args.push_back((void *)&keyFormat);
            _hidl_args.push_back((void *)&keyData);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "importKey", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->importKey(keyParams, keyFormat, keyData, [&](const auto &_hidl_out_error, const auto &_hidl_out_keyBlob, const auto &_hidl_out_keyCharacteristics) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_error);
                _hidl_args.push_back((void *)&_hidl_out_keyBlob);
                _hidl_args.push_back((void *)&_hidl_out_keyCharacteristics);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "importKey", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_error, _hidl_out_keyBlob, _hidl_out_keyCharacteristics);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> importWrappedKey(const ::android::hardware::hidl_vec<uint8_t>& wrappedKeyData, const ::android::hardware::hidl_vec<uint8_t>& wrappingKeyBlob, const ::android::hardware::hidl_vec<uint8_t>& maskingKey, const ::android::hardware::hidl_vec<::android::hardware::keymaster::V4_0::KeyParameter>& unwrappingParams, uint64_t passwordSid, uint64_t biometricSid, importWrappedKey_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::importWrappedKey::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&wrappedKeyData);
            _hidl_args.push_back((void *)&wrappingKeyBlob);
            _hidl_args.push_back((void *)&maskingKey);
            _hidl_args.push_back((void *)&unwrappingParams);
            _hidl_args.push_back((void *)&passwordSid);
            _hidl_args.push_back((void *)&biometricSid);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "importWrappedKey", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->importWrappedKey(wrappedKeyData, wrappingKeyBlob, maskingKey, unwrappingParams, passwordSid, biometricSid, [&](const auto &_hidl_out_error, const auto &_hidl_out_keyBlob, const auto &_hidl_out_keyCharacteristics) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_error);
                _hidl_args.push_back((void *)&_hidl_out_keyBlob);
                _hidl_args.push_back((void *)&_hidl_out_keyCharacteristics);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "importWrappedKey", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_error, _hidl_out_keyBlob, _hidl_out_keyCharacteristics);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getKeyCharacteristics(const ::android::hardware::hidl_vec<uint8_t>& keyBlob, const ::android::hardware::hidl_vec<uint8_t>& clientId, const ::android::hardware::hidl_vec<uint8_t>& appData, getKeyCharacteristics_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::getKeyCharacteristics::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&keyBlob);
            _hidl_args.push_back((void *)&clientId);
            _hidl_args.push_back((void *)&appData);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "getKeyCharacteristics", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getKeyCharacteristics(keyBlob, clientId, appData, [&](const auto &_hidl_out_error, const auto &_hidl_out_keyCharacteristics) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_error);
                _hidl_args.push_back((void *)&_hidl_out_keyCharacteristics);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "getKeyCharacteristics", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_error, _hidl_out_keyCharacteristics);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> exportKey(::android::hardware::keymaster::V4_0::KeyFormat keyFormat, const ::android::hardware::hidl_vec<uint8_t>& keyBlob, const ::android::hardware::hidl_vec<uint8_t>& clientId, const ::android::hardware::hidl_vec<uint8_t>& appData, exportKey_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::exportKey::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&keyFormat);
            _hidl_args.push_back((void *)&keyBlob);
            _hidl_args.push_back((void *)&clientId);
            _hidl_args.push_back((void *)&appData);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "exportKey", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->exportKey(keyFormat, keyBlob, clientId, appData, [&](const auto &_hidl_out_error, const auto &_hidl_out_keyMaterial) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_error);
                _hidl_args.push_back((void *)&_hidl_out_keyMaterial);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "exportKey", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_error, _hidl_out_keyMaterial);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> attestKey(const ::android::hardware::hidl_vec<uint8_t>& keyToAttest, const ::android::hardware::hidl_vec<::android::hardware::keymaster::V4_0::KeyParameter>& attestParams, attestKey_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::attestKey::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&keyToAttest);
            _hidl_args.push_back((void *)&attestParams);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "attestKey", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->attestKey(keyToAttest, attestParams, [&](const auto &_hidl_out_error, const auto &_hidl_out_certChain) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_error);
                _hidl_args.push_back((void *)&_hidl_out_certChain);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "attestKey", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_error, _hidl_out_certChain);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> upgradeKey(const ::android::hardware::hidl_vec<uint8_t>& keyBlobToUpgrade, const ::android::hardware::hidl_vec<::android::hardware::keymaster::V4_0::KeyParameter>& upgradeParams, upgradeKey_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::upgradeKey::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&keyBlobToUpgrade);
            _hidl_args.push_back((void *)&upgradeParams);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "upgradeKey", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->upgradeKey(keyBlobToUpgrade, upgradeParams, [&](const auto &_hidl_out_error, const auto &_hidl_out_upgradedKeyBlob) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_error);
                _hidl_args.push_back((void *)&_hidl_out_upgradedKeyBlob);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "upgradeKey", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_error, _hidl_out_upgradedKeyBlob);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::keymaster::V4_0::ErrorCode> deleteKey(const ::android::hardware::hidl_vec<uint8_t>& keyBlob) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::deleteKey::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&keyBlob);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "deleteKey", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->deleteKey(keyBlob);

        ::android::hardware::keymaster::V4_0::ErrorCode _hidl_out_error = _hidl_return;
        (void) _hidl_out_error;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_error);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "deleteKey", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::keymaster::V4_0::ErrorCode> deleteAllKeys() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::deleteAllKeys::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "deleteAllKeys", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->deleteAllKeys();

        ::android::hardware::keymaster::V4_0::ErrorCode _hidl_out_error = _hidl_return;
        (void) _hidl_out_error;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_error);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "deleteAllKeys", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::keymaster::V4_0::ErrorCode> destroyAttestationIds() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::destroyAttestationIds::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "destroyAttestationIds", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->destroyAttestationIds();

        ::android::hardware::keymaster::V4_0::ErrorCode _hidl_out_error = _hidl_return;
        (void) _hidl_out_error;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_error);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "destroyAttestationIds", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> begin(::android::hardware::keymaster::V4_0::KeyPurpose purpose, const ::android::hardware::hidl_vec<uint8_t>& keyBlob, const ::android::hardware::hidl_vec<::android::hardware::keymaster::V4_0::KeyParameter>& inParams, const ::android::hardware::keymaster::V4_0::HardwareAuthToken& authToken, begin_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::begin::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&purpose);
            _hidl_args.push_back((void *)&keyBlob);
            _hidl_args.push_back((void *)&inParams);
            _hidl_args.push_back((void *)&authToken);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "begin", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->begin(purpose, keyBlob, inParams, authToken, [&](const auto &_hidl_out_error, const auto &_hidl_out_outParams, const auto &_hidl_out_operationHandle) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_error);
                _hidl_args.push_back((void *)&_hidl_out_outParams);
                _hidl_args.push_back((void *)&_hidl_out_operationHandle);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "begin", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_error, _hidl_out_outParams, _hidl_out_operationHandle);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> update(uint64_t operationHandle, const ::android::hardware::hidl_vec<::android::hardware::keymaster::V4_0::KeyParameter>& inParams, const ::android::hardware::hidl_vec<uint8_t>& input, const ::android::hardware::keymaster::V4_0::HardwareAuthToken& authToken, const ::android::hardware::keymaster::V4_0::VerificationToken& verificationToken, update_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::update::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&operationHandle);
            _hidl_args.push_back((void *)&inParams);
            _hidl_args.push_back((void *)&input);
            _hidl_args.push_back((void *)&authToken);
            _hidl_args.push_back((void *)&verificationToken);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "update", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->update(operationHandle, inParams, input, authToken, verificationToken, [&](const auto &_hidl_out_error, const auto &_hidl_out_inputConsumed, const auto &_hidl_out_outParams, const auto &_hidl_out_output) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_error);
                _hidl_args.push_back((void *)&_hidl_out_inputConsumed);
                _hidl_args.push_back((void *)&_hidl_out_outParams);
                _hidl_args.push_back((void *)&_hidl_out_output);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "update", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_error, _hidl_out_inputConsumed, _hidl_out_outParams, _hidl_out_output);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> finish(uint64_t operationHandle, const ::android::hardware::hidl_vec<::android::hardware::keymaster::V4_0::KeyParameter>& inParams, const ::android::hardware::hidl_vec<uint8_t>& input, const ::android::hardware::hidl_vec<uint8_t>& signature, const ::android::hardware::keymaster::V4_0::HardwareAuthToken& authToken, const ::android::hardware::keymaster::V4_0::VerificationToken& verificationToken, finish_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::finish::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&operationHandle);
            _hidl_args.push_back((void *)&inParams);
            _hidl_args.push_back((void *)&input);
            _hidl_args.push_back((void *)&signature);
            _hidl_args.push_back((void *)&authToken);
            _hidl_args.push_back((void *)&verificationToken);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "finish", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->finish(operationHandle, inParams, input, signature, authToken, verificationToken, [&](const auto &_hidl_out_error, const auto &_hidl_out_outParams, const auto &_hidl_out_output) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_error);
                _hidl_args.push_back((void *)&_hidl_out_outParams);
                _hidl_args.push_back((void *)&_hidl_out_output);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "finish", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_error, _hidl_out_outParams, _hidl_out_output);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::keymaster::V4_0::ErrorCode> abort(uint64_t operationHandle) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::abort::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&operationHandle);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "abort", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->abort(operationHandle);

        ::android::hardware::keymaster::V4_0::ErrorCode _hidl_out_error = _hidl_return;
        (void) _hidl_out_error;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_error);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.keymaster", "4.0", "IKeymasterDevice", "abort", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }

    // Methods from ::android::hardware::keymaster::V4_1::IKeymasterDevice follow.
    ::android::hardware::Return<::android::hardware::keymaster::V4_1::ErrorCode> deviceLocked(bool passwordOnly, const ::android::hardware::keymaster::V4_0::VerificationToken& verificationToken) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::deviceLocked::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&passwordOnly);
            _hidl_args.push_back((void *)&verificationToken);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.keymaster", "4.1", "IKeymasterDevice", "deviceLocked", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->deviceLocked(passwordOnly, verificationToken);

        ::android::hardware::keymaster::V4_1::ErrorCode _hidl_out_error = _hidl_return;
        (void) _hidl_out_error;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_error);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.keymaster", "4.1", "IKeymasterDevice", "deviceLocked", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<::android::hardware::keymaster::V4_1::ErrorCode> earlyBootEnded() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::earlyBootEnded::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.keymaster", "4.1", "IKeymasterDevice", "earlyBootEnded", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->earlyBootEnded();

        ::android::hardware::keymaster::V4_1::ErrorCode _hidl_out_error = _hidl_return;
        (void) _hidl_out_error;
        atrace_end(ATRACE_TAG_HAL);
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&_hidl_out_error);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.keymaster", "4.1", "IKeymasterDevice", "earlyBootEnded", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> interfaceChain(interfaceChain_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::interfaceChain::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::debug::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::interfaceDescriptor::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::getHashChain::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::linkToDeath::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::ping::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::getDebugInfo::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::notifySyspropsChanged::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IKeymasterDevice::unlinkToDeath::passthrough");
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
    const ::android::sp<IKeymasterDevice> mImpl;
    ::android::hardware::details::TaskRunner mOnewayQueue;

    ::android::hardware::Return<void> addOnewayTask(std::function<void(void)>);

};

}  // namespace V4_1
}  // namespace keymaster
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_KEYMASTER_V4_1_BSKEYMASTERDEVICE_H
