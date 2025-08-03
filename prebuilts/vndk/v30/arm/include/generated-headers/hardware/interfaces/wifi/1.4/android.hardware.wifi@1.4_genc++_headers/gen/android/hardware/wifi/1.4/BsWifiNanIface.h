#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_4_BSWIFINANIFACE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_4_BSWIFINANIFACE_H

#include <android-base/macros.h>
#include <cutils/trace.h>
#include <future>
#include <android/hardware/wifi/1.4/IWifiNanIface.h>

#include <hidl/HidlPassthroughSupport.h>
#include <hidl/TaskRunner.h>
namespace android {
namespace hardware {
namespace wifi {
namespace V1_4 {

struct BsWifiNanIface : IWifiNanIface, ::android::hardware::details::HidlInstrumentor {
    explicit BsWifiNanIface(const ::android::sp<IWifiNanIface> impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IWifiNanIface Pure;

    typedef ::android::hardware::details::bs_tag _hidl_tag;

    // Methods from ::android::hardware::wifi::V1_0::IWifiIface follow.
    ::android::hardware::Return<void> getType(getType_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::getType::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiIface", "getType", &_hidl_args);
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
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiIface", "getType", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_type);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getName(getName_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::getName::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiIface", "getName", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getName([&](const auto &_hidl_out_status, const auto &_hidl_out_name) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_name);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiIface", "getName", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_name);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }

    // Methods from ::android::hardware::wifi::V1_0::IWifiNanIface follow.
    ::android::hardware::Return<void> registerEventCallback(const ::android::sp<::android::hardware::wifi::V1_0::IWifiNanIfaceEventCallback>& callback, registerEventCallback_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::registerEventCallback::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&callback);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiNanIface", "registerEventCallback", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::sp<::android::hardware::wifi::V1_0::IWifiNanIfaceEventCallback> _hidl_wrapped_callback;
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
        auto _hidl_return = mImpl->registerEventCallback(_hidl_wrapped_callback, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiNanIface", "registerEventCallback", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getCapabilitiesRequest(uint16_t cmdId, getCapabilitiesRequest_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::getCapabilitiesRequest::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&cmdId);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiNanIface", "getCapabilitiesRequest", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getCapabilitiesRequest(cmdId, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiNanIface", "getCapabilitiesRequest", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> enableRequest(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanEnableRequest& msg, enableRequest_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::enableRequest::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&cmdId);
            _hidl_args.push_back((void *)&msg);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiNanIface", "enableRequest", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->enableRequest(cmdId, msg, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiNanIface", "enableRequest", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> configRequest(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanConfigRequest& msg, configRequest_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::configRequest::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&cmdId);
            _hidl_args.push_back((void *)&msg);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiNanIface", "configRequest", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->configRequest(cmdId, msg, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiNanIface", "configRequest", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> disableRequest(uint16_t cmdId, disableRequest_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::disableRequest::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&cmdId);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiNanIface", "disableRequest", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->disableRequest(cmdId, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiNanIface", "disableRequest", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> startPublishRequest(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanPublishRequest& msg, startPublishRequest_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::startPublishRequest::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&cmdId);
            _hidl_args.push_back((void *)&msg);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiNanIface", "startPublishRequest", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->startPublishRequest(cmdId, msg, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiNanIface", "startPublishRequest", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> stopPublishRequest(uint16_t cmdId, uint8_t sessionId, stopPublishRequest_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::stopPublishRequest::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&cmdId);
            _hidl_args.push_back((void *)&sessionId);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiNanIface", "stopPublishRequest", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->stopPublishRequest(cmdId, sessionId, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiNanIface", "stopPublishRequest", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> startSubscribeRequest(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanSubscribeRequest& msg, startSubscribeRequest_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::startSubscribeRequest::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&cmdId);
            _hidl_args.push_back((void *)&msg);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiNanIface", "startSubscribeRequest", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->startSubscribeRequest(cmdId, msg, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiNanIface", "startSubscribeRequest", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> stopSubscribeRequest(uint16_t cmdId, uint8_t sessionId, stopSubscribeRequest_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::stopSubscribeRequest::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&cmdId);
            _hidl_args.push_back((void *)&sessionId);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiNanIface", "stopSubscribeRequest", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->stopSubscribeRequest(cmdId, sessionId, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiNanIface", "stopSubscribeRequest", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> transmitFollowupRequest(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanTransmitFollowupRequest& msg, transmitFollowupRequest_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::transmitFollowupRequest::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&cmdId);
            _hidl_args.push_back((void *)&msg);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiNanIface", "transmitFollowupRequest", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->transmitFollowupRequest(cmdId, msg, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiNanIface", "transmitFollowupRequest", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> createDataInterfaceRequest(uint16_t cmdId, const ::android::hardware::hidl_string& ifaceName, createDataInterfaceRequest_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::createDataInterfaceRequest::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&cmdId);
            _hidl_args.push_back((void *)&ifaceName);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiNanIface", "createDataInterfaceRequest", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->createDataInterfaceRequest(cmdId, ifaceName, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiNanIface", "createDataInterfaceRequest", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> deleteDataInterfaceRequest(uint16_t cmdId, const ::android::hardware::hidl_string& ifaceName, deleteDataInterfaceRequest_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::deleteDataInterfaceRequest::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&cmdId);
            _hidl_args.push_back((void *)&ifaceName);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiNanIface", "deleteDataInterfaceRequest", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->deleteDataInterfaceRequest(cmdId, ifaceName, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiNanIface", "deleteDataInterfaceRequest", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> initiateDataPathRequest(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanInitiateDataPathRequest& msg, initiateDataPathRequest_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::initiateDataPathRequest::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&cmdId);
            _hidl_args.push_back((void *)&msg);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiNanIface", "initiateDataPathRequest", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->initiateDataPathRequest(cmdId, msg, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiNanIface", "initiateDataPathRequest", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> respondToDataPathIndicationRequest(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanRespondToDataPathIndicationRequest& msg, respondToDataPathIndicationRequest_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::respondToDataPathIndicationRequest::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&cmdId);
            _hidl_args.push_back((void *)&msg);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiNanIface", "respondToDataPathIndicationRequest", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->respondToDataPathIndicationRequest(cmdId, msg, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiNanIface", "respondToDataPathIndicationRequest", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> terminateDataPathRequest(uint16_t cmdId, uint32_t ndpInstanceId, terminateDataPathRequest_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::terminateDataPathRequest::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&cmdId);
            _hidl_args.push_back((void *)&ndpInstanceId);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiNanIface", "terminateDataPathRequest", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->terminateDataPathRequest(cmdId, ndpInstanceId, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiNanIface", "terminateDataPathRequest", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }

    // Methods from ::android::hardware::wifi::V1_2::IWifiNanIface follow.
    ::android::hardware::Return<void> registerEventCallback_1_2(const ::android::sp<::android::hardware::wifi::V1_2::IWifiNanIfaceEventCallback>& callback, registerEventCallback_1_2_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::registerEventCallback_1_2::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&callback);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.2", "IWifiNanIface", "registerEventCallback_1_2", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::sp<::android::hardware::wifi::V1_2::IWifiNanIfaceEventCallback> _hidl_wrapped_callback;
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
        auto _hidl_return = mImpl->registerEventCallback_1_2(_hidl_wrapped_callback, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.2", "IWifiNanIface", "registerEventCallback_1_2", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> enableRequest_1_2(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanEnableRequest& msg1, const ::android::hardware::wifi::V1_2::NanConfigRequestSupplemental& msg2, enableRequest_1_2_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::enableRequest_1_2::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&cmdId);
            _hidl_args.push_back((void *)&msg1);
            _hidl_args.push_back((void *)&msg2);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.2", "IWifiNanIface", "enableRequest_1_2", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->enableRequest_1_2(cmdId, msg1, msg2, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.2", "IWifiNanIface", "enableRequest_1_2", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> configRequest_1_2(uint16_t cmdId, const ::android::hardware::wifi::V1_0::NanConfigRequest& msg1, const ::android::hardware::wifi::V1_2::NanConfigRequestSupplemental& msg2, configRequest_1_2_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::configRequest_1_2::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&cmdId);
            _hidl_args.push_back((void *)&msg1);
            _hidl_args.push_back((void *)&msg2);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.2", "IWifiNanIface", "configRequest_1_2", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->configRequest_1_2(cmdId, msg1, msg2, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.2", "IWifiNanIface", "configRequest_1_2", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }

    // Methods from ::android::hardware::wifi::V1_4::IWifiNanIface follow.
    ::android::hardware::Return<void> enableRequest_1_4(uint16_t cmdId, const ::android::hardware::wifi::V1_4::NanEnableRequest& msg1, const ::android::hardware::wifi::V1_2::NanConfigRequestSupplemental& msg2, enableRequest_1_4_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::enableRequest_1_4::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&cmdId);
            _hidl_args.push_back((void *)&msg1);
            _hidl_args.push_back((void *)&msg2);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.4", "IWifiNanIface", "enableRequest_1_4", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->enableRequest_1_4(cmdId, msg1, msg2, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.4", "IWifiNanIface", "enableRequest_1_4", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> configRequest_1_4(uint16_t cmdId, const ::android::hardware::wifi::V1_4::NanConfigRequest& msg1, const ::android::hardware::wifi::V1_2::NanConfigRequestSupplemental& msg2, configRequest_1_4_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::configRequest_1_4::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&cmdId);
            _hidl_args.push_back((void *)&msg1);
            _hidl_args.push_back((void *)&msg2);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.4", "IWifiNanIface", "configRequest_1_4", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->configRequest_1_4(cmdId, msg1, msg2, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.4", "IWifiNanIface", "configRequest_1_4", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> interfaceChain(interfaceChain_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::interfaceChain::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::debug::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::interfaceDescriptor::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::getHashChain::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::linkToDeath::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::ping::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::getDebugInfo::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::notifySyspropsChanged::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiNanIface::unlinkToDeath::passthrough");
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
    const ::android::sp<IWifiNanIface> mImpl;
    ::android::hardware::details::TaskRunner mOnewayQueue;

    ::android::hardware::Return<void> addOnewayTask(std::function<void(void)>);

};

}  // namespace V1_4
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_4_BSWIFINANIFACE_H
