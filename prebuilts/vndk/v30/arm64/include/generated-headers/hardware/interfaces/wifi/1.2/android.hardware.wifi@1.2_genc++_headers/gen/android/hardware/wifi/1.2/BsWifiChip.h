#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_2_BSWIFICHIP_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_2_BSWIFICHIP_H

#include <android-base/macros.h>
#include <cutils/trace.h>
#include <future>
#include <android/hardware/wifi/1.2/IWifiChip.h>

#include <hidl/HidlPassthroughSupport.h>
#include <hidl/TaskRunner.h>
namespace android {
namespace hardware {
namespace wifi {
namespace V1_2 {

struct BsWifiChip : IWifiChip, ::android::hardware::details::HidlInstrumentor {
    explicit BsWifiChip(const ::android::sp<IWifiChip> impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IWifiChip Pure;

    typedef ::android::hardware::details::bs_tag _hidl_tag;

    // Methods from ::android::hardware::wifi::V1_0::IWifiChip follow.
    ::android::hardware::Return<void> getId(getId_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::getId::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "getId", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getId([&](const auto &_hidl_out_status, const auto &_hidl_out_id) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_id);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "getId", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_id);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> registerEventCallback(const ::android::sp<::android::hardware::wifi::V1_0::IWifiChipEventCallback>& callback, registerEventCallback_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::registerEventCallback::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&callback);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "registerEventCallback", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::sp<::android::hardware::wifi::V1_0::IWifiChipEventCallback> _hidl_wrapped_callback;
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
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "registerEventCallback", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getCapabilities(getCapabilities_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::getCapabilities::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "getCapabilities", &_hidl_args);
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
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "getCapabilities", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_capabilities);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getAvailableModes(getAvailableModes_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::getAvailableModes::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "getAvailableModes", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getAvailableModes([&](const auto &_hidl_out_status, const auto &_hidl_out_modes) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_modes);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "getAvailableModes", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_modes);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> configureChip(uint32_t modeId, configureChip_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::configureChip::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&modeId);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "configureChip", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->configureChip(modeId, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "configureChip", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getMode(getMode_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::getMode::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "getMode", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getMode([&](const auto &_hidl_out_status, const auto &_hidl_out_modeId) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_modeId);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "getMode", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_modeId);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> requestChipDebugInfo(requestChipDebugInfo_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::requestChipDebugInfo::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "requestChipDebugInfo", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->requestChipDebugInfo([&](const auto &_hidl_out_status, const auto &_hidl_out_chipDebugInfo) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_chipDebugInfo);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "requestChipDebugInfo", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_chipDebugInfo);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> requestDriverDebugDump(requestDriverDebugDump_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::requestDriverDebugDump::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "requestDriverDebugDump", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->requestDriverDebugDump([&](const auto &_hidl_out_status, const auto &_hidl_out_blob) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_blob);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "requestDriverDebugDump", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_blob);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> requestFirmwareDebugDump(requestFirmwareDebugDump_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::requestFirmwareDebugDump::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "requestFirmwareDebugDump", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->requestFirmwareDebugDump([&](const auto &_hidl_out_status, const auto &_hidl_out_blob) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_blob);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "requestFirmwareDebugDump", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_blob);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> createApIface(createApIface_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::createApIface::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "createApIface", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->createApIface([&](const auto &_hidl_out_status, const auto &_hidl_out_iface) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_iface);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "createApIface", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            ::android::sp<::android::hardware::wifi::V1_0::IWifiApIface> _hidl_wrapped__hidl_out_iface;
            if (_hidl_out_iface != nullptr && !_hidl_out_iface->isRemote()) {
                _hidl_wrapped__hidl_out_iface = ::android::hardware::details::wrapPassthrough(_hidl_out_iface);
                if (_hidl_wrapped__hidl_out_iface == nullptr) {
                    _hidl_error = ::android::hardware::Status::fromExceptionCode(
                            ::android::hardware::Status::EX_TRANSACTION_FAILED,
                            "Cannot wrap passthrough interface.");
                }
            } else {
                _hidl_wrapped__hidl_out_iface = _hidl_out_iface;
            }

            _hidl_cb(_hidl_out_status, _hidl_wrapped__hidl_out_iface);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getApIfaceNames(getApIfaceNames_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::getApIfaceNames::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "getApIfaceNames", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getApIfaceNames([&](const auto &_hidl_out_status, const auto &_hidl_out_ifnames) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_ifnames);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "getApIfaceNames", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_ifnames);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getApIface(const ::android::hardware::hidl_string& ifname, getApIface_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::getApIface::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&ifname);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "getApIface", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getApIface(ifname, [&](const auto &_hidl_out_status, const auto &_hidl_out_iface) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_iface);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "getApIface", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            ::android::sp<::android::hardware::wifi::V1_0::IWifiApIface> _hidl_wrapped__hidl_out_iface;
            if (_hidl_out_iface != nullptr && !_hidl_out_iface->isRemote()) {
                _hidl_wrapped__hidl_out_iface = ::android::hardware::details::wrapPassthrough(_hidl_out_iface);
                if (_hidl_wrapped__hidl_out_iface == nullptr) {
                    _hidl_error = ::android::hardware::Status::fromExceptionCode(
                            ::android::hardware::Status::EX_TRANSACTION_FAILED,
                            "Cannot wrap passthrough interface.");
                }
            } else {
                _hidl_wrapped__hidl_out_iface = _hidl_out_iface;
            }

            _hidl_cb(_hidl_out_status, _hidl_wrapped__hidl_out_iface);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> removeApIface(const ::android::hardware::hidl_string& ifname, removeApIface_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::removeApIface::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&ifname);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "removeApIface", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->removeApIface(ifname, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "removeApIface", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> createNanIface(createNanIface_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::createNanIface::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "createNanIface", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->createNanIface([&](const auto &_hidl_out_status, const auto &_hidl_out_iface) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_iface);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "createNanIface", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            ::android::sp<::android::hardware::wifi::V1_0::IWifiNanIface> _hidl_wrapped__hidl_out_iface;
            if (_hidl_out_iface != nullptr && !_hidl_out_iface->isRemote()) {
                _hidl_wrapped__hidl_out_iface = ::android::hardware::details::wrapPassthrough(_hidl_out_iface);
                if (_hidl_wrapped__hidl_out_iface == nullptr) {
                    _hidl_error = ::android::hardware::Status::fromExceptionCode(
                            ::android::hardware::Status::EX_TRANSACTION_FAILED,
                            "Cannot wrap passthrough interface.");
                }
            } else {
                _hidl_wrapped__hidl_out_iface = _hidl_out_iface;
            }

            _hidl_cb(_hidl_out_status, _hidl_wrapped__hidl_out_iface);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getNanIfaceNames(getNanIfaceNames_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::getNanIfaceNames::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "getNanIfaceNames", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getNanIfaceNames([&](const auto &_hidl_out_status, const auto &_hidl_out_ifnames) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_ifnames);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "getNanIfaceNames", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_ifnames);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getNanIface(const ::android::hardware::hidl_string& ifname, getNanIface_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::getNanIface::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&ifname);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "getNanIface", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getNanIface(ifname, [&](const auto &_hidl_out_status, const auto &_hidl_out_iface) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_iface);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "getNanIface", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            ::android::sp<::android::hardware::wifi::V1_0::IWifiNanIface> _hidl_wrapped__hidl_out_iface;
            if (_hidl_out_iface != nullptr && !_hidl_out_iface->isRemote()) {
                _hidl_wrapped__hidl_out_iface = ::android::hardware::details::wrapPassthrough(_hidl_out_iface);
                if (_hidl_wrapped__hidl_out_iface == nullptr) {
                    _hidl_error = ::android::hardware::Status::fromExceptionCode(
                            ::android::hardware::Status::EX_TRANSACTION_FAILED,
                            "Cannot wrap passthrough interface.");
                }
            } else {
                _hidl_wrapped__hidl_out_iface = _hidl_out_iface;
            }

            _hidl_cb(_hidl_out_status, _hidl_wrapped__hidl_out_iface);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> removeNanIface(const ::android::hardware::hidl_string& ifname, removeNanIface_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::removeNanIface::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&ifname);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "removeNanIface", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->removeNanIface(ifname, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "removeNanIface", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> createP2pIface(createP2pIface_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::createP2pIface::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "createP2pIface", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->createP2pIface([&](const auto &_hidl_out_status, const auto &_hidl_out_iface) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_iface);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "createP2pIface", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            ::android::sp<::android::hardware::wifi::V1_0::IWifiP2pIface> _hidl_wrapped__hidl_out_iface;
            if (_hidl_out_iface != nullptr && !_hidl_out_iface->isRemote()) {
                _hidl_wrapped__hidl_out_iface = ::android::hardware::details::wrapPassthrough(_hidl_out_iface);
                if (_hidl_wrapped__hidl_out_iface == nullptr) {
                    _hidl_error = ::android::hardware::Status::fromExceptionCode(
                            ::android::hardware::Status::EX_TRANSACTION_FAILED,
                            "Cannot wrap passthrough interface.");
                }
            } else {
                _hidl_wrapped__hidl_out_iface = _hidl_out_iface;
            }

            _hidl_cb(_hidl_out_status, _hidl_wrapped__hidl_out_iface);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getP2pIfaceNames(getP2pIfaceNames_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::getP2pIfaceNames::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "getP2pIfaceNames", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getP2pIfaceNames([&](const auto &_hidl_out_status, const auto &_hidl_out_ifnames) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_ifnames);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "getP2pIfaceNames", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_ifnames);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getP2pIface(const ::android::hardware::hidl_string& ifname, getP2pIface_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::getP2pIface::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&ifname);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "getP2pIface", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getP2pIface(ifname, [&](const auto &_hidl_out_status, const auto &_hidl_out_iface) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_iface);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "getP2pIface", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            ::android::sp<::android::hardware::wifi::V1_0::IWifiP2pIface> _hidl_wrapped__hidl_out_iface;
            if (_hidl_out_iface != nullptr && !_hidl_out_iface->isRemote()) {
                _hidl_wrapped__hidl_out_iface = ::android::hardware::details::wrapPassthrough(_hidl_out_iface);
                if (_hidl_wrapped__hidl_out_iface == nullptr) {
                    _hidl_error = ::android::hardware::Status::fromExceptionCode(
                            ::android::hardware::Status::EX_TRANSACTION_FAILED,
                            "Cannot wrap passthrough interface.");
                }
            } else {
                _hidl_wrapped__hidl_out_iface = _hidl_out_iface;
            }

            _hidl_cb(_hidl_out_status, _hidl_wrapped__hidl_out_iface);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> removeP2pIface(const ::android::hardware::hidl_string& ifname, removeP2pIface_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::removeP2pIface::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&ifname);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "removeP2pIface", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->removeP2pIface(ifname, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "removeP2pIface", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> createStaIface(createStaIface_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::createStaIface::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "createStaIface", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->createStaIface([&](const auto &_hidl_out_status, const auto &_hidl_out_iface) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_iface);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "createStaIface", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            ::android::sp<::android::hardware::wifi::V1_0::IWifiStaIface> _hidl_wrapped__hidl_out_iface;
            if (_hidl_out_iface != nullptr && !_hidl_out_iface->isRemote()) {
                _hidl_wrapped__hidl_out_iface = ::android::hardware::details::wrapPassthrough(_hidl_out_iface);
                if (_hidl_wrapped__hidl_out_iface == nullptr) {
                    _hidl_error = ::android::hardware::Status::fromExceptionCode(
                            ::android::hardware::Status::EX_TRANSACTION_FAILED,
                            "Cannot wrap passthrough interface.");
                }
            } else {
                _hidl_wrapped__hidl_out_iface = _hidl_out_iface;
            }

            _hidl_cb(_hidl_out_status, _hidl_wrapped__hidl_out_iface);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getStaIfaceNames(getStaIfaceNames_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::getStaIfaceNames::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "getStaIfaceNames", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getStaIfaceNames([&](const auto &_hidl_out_status, const auto &_hidl_out_ifnames) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_ifnames);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "getStaIfaceNames", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_ifnames);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getStaIface(const ::android::hardware::hidl_string& ifname, getStaIface_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::getStaIface::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&ifname);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "getStaIface", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getStaIface(ifname, [&](const auto &_hidl_out_status, const auto &_hidl_out_iface) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_iface);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "getStaIface", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            ::android::sp<::android::hardware::wifi::V1_0::IWifiStaIface> _hidl_wrapped__hidl_out_iface;
            if (_hidl_out_iface != nullptr && !_hidl_out_iface->isRemote()) {
                _hidl_wrapped__hidl_out_iface = ::android::hardware::details::wrapPassthrough(_hidl_out_iface);
                if (_hidl_wrapped__hidl_out_iface == nullptr) {
                    _hidl_error = ::android::hardware::Status::fromExceptionCode(
                            ::android::hardware::Status::EX_TRANSACTION_FAILED,
                            "Cannot wrap passthrough interface.");
                }
            } else {
                _hidl_wrapped__hidl_out_iface = _hidl_out_iface;
            }

            _hidl_cb(_hidl_out_status, _hidl_wrapped__hidl_out_iface);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> removeStaIface(const ::android::hardware::hidl_string& ifname, removeStaIface_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::removeStaIface::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&ifname);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "removeStaIface", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->removeStaIface(ifname, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "removeStaIface", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> createRttController(const ::android::sp<::android::hardware::wifi::V1_0::IWifiIface>& boundIface, createRttController_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::createRttController::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&boundIface);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "createRttController", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::sp<::android::hardware::wifi::V1_0::IWifiIface> _hidl_wrapped_boundIface;
        if (boundIface != nullptr && !boundIface->isRemote()) {
            _hidl_wrapped_boundIface = ::android::hardware::details::wrapPassthrough(boundIface);
            if (_hidl_wrapped_boundIface == nullptr) {
                return ::android::hardware::Status::fromExceptionCode(
                        ::android::hardware::Status::EX_TRANSACTION_FAILED,
                        "Cannot wrap passthrough interface.");
            }
        } else {
            _hidl_wrapped_boundIface = boundIface;
        }

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->createRttController(_hidl_wrapped_boundIface, [&](const auto &_hidl_out_status, const auto &_hidl_out_rtt) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_rtt);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "createRttController", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            ::android::sp<::android::hardware::wifi::V1_0::IWifiRttController> _hidl_wrapped__hidl_out_rtt;
            if (_hidl_out_rtt != nullptr && !_hidl_out_rtt->isRemote()) {
                _hidl_wrapped__hidl_out_rtt = ::android::hardware::details::wrapPassthrough(_hidl_out_rtt);
                if (_hidl_wrapped__hidl_out_rtt == nullptr) {
                    _hidl_error = ::android::hardware::Status::fromExceptionCode(
                            ::android::hardware::Status::EX_TRANSACTION_FAILED,
                            "Cannot wrap passthrough interface.");
                }
            } else {
                _hidl_wrapped__hidl_out_rtt = _hidl_out_rtt;
            }

            _hidl_cb(_hidl_out_status, _hidl_wrapped__hidl_out_rtt);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getDebugRingBuffersStatus(getDebugRingBuffersStatus_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::getDebugRingBuffersStatus::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "getDebugRingBuffersStatus", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getDebugRingBuffersStatus([&](const auto &_hidl_out_status, const auto &_hidl_out_ringBuffers) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_ringBuffers);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "getDebugRingBuffersStatus", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_ringBuffers);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> startLoggingToDebugRingBuffer(const ::android::hardware::hidl_string& ringName, ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel verboseLevel, uint32_t maxIntervalInSec, uint32_t minDataSizeInBytes, startLoggingToDebugRingBuffer_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::startLoggingToDebugRingBuffer::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&ringName);
            _hidl_args.push_back((void *)&verboseLevel);
            _hidl_args.push_back((void *)&maxIntervalInSec);
            _hidl_args.push_back((void *)&minDataSizeInBytes);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "startLoggingToDebugRingBuffer", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->startLoggingToDebugRingBuffer(ringName, verboseLevel, maxIntervalInSec, minDataSizeInBytes, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "startLoggingToDebugRingBuffer", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> forceDumpToDebugRingBuffer(const ::android::hardware::hidl_string& ringName, forceDumpToDebugRingBuffer_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::forceDumpToDebugRingBuffer::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&ringName);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "forceDumpToDebugRingBuffer", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->forceDumpToDebugRingBuffer(ringName, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "forceDumpToDebugRingBuffer", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> stopLoggingToDebugRingBuffer(stopLoggingToDebugRingBuffer_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::stopLoggingToDebugRingBuffer::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "stopLoggingToDebugRingBuffer", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->stopLoggingToDebugRingBuffer([&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "stopLoggingToDebugRingBuffer", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> getDebugHostWakeReasonStats(getDebugHostWakeReasonStats_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::getDebugHostWakeReasonStats::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "getDebugHostWakeReasonStats", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->getDebugHostWakeReasonStats([&](const auto &_hidl_out_status, const auto &_hidl_out_stats) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                _hidl_args.push_back((void *)&_hidl_out_stats);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "getDebugHostWakeReasonStats", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status, _hidl_out_stats);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> enableDebugErrorAlerts(bool enable, enableDebugErrorAlerts_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::enableDebugErrorAlerts::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&enable);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.0", "IWifiChip", "enableDebugErrorAlerts", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->enableDebugErrorAlerts(enable, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.0", "IWifiChip", "enableDebugErrorAlerts", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }

    // Methods from ::android::hardware::wifi::V1_1::IWifiChip follow.
    ::android::hardware::Return<void> selectTxPowerScenario(::android::hardware::wifi::V1_1::IWifiChip::TxPowerScenario scenario, selectTxPowerScenario_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::selectTxPowerScenario::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&scenario);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.1", "IWifiChip", "selectTxPowerScenario", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->selectTxPowerScenario(scenario, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.1", "IWifiChip", "selectTxPowerScenario", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> resetTxPowerScenario(resetTxPowerScenario_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::resetTxPowerScenario::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.1", "IWifiChip", "resetTxPowerScenario", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->resetTxPowerScenario([&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.1", "IWifiChip", "resetTxPowerScenario", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }

    // Methods from ::android::hardware::wifi::V1_2::IWifiChip follow.
    ::android::hardware::Return<void> selectTxPowerScenario_1_2(::android::hardware::wifi::V1_2::IWifiChip::TxPowerScenario scenario, selectTxPowerScenario_1_2_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::selectTxPowerScenario_1_2::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&scenario);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.2", "IWifiChip", "selectTxPowerScenario_1_2", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = mImpl->selectTxPowerScenario_1_2(scenario, [&](const auto &_hidl_out_status) {
            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                _hidl_args.push_back((void *)&_hidl_out_status);
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.2", "IWifiChip", "selectTxPowerScenario_1_2", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

            _hidl_cb(_hidl_out_status);
        });

        if (!_hidl_error.isOk()) return _hidl_error;
        return _hidl_return;
    }
    ::android::hardware::Return<void> registerEventCallback_1_2(const ::android::sp<::android::hardware::wifi::V1_2::IWifiChipEventCallback>& callback, registerEventCallback_1_2_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::registerEventCallback_1_2::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&callback);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi", "1.2", "IWifiChip", "registerEventCallback_1_2", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::sp<::android::hardware::wifi::V1_2::IWifiChipEventCallback> _hidl_wrapped_callback;
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
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi", "1.2", "IWifiChip", "registerEventCallback_1_2", &_hidl_args);
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::interfaceChain::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::debug::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::interfaceDescriptor::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::getHashChain::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::linkToDeath::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::ping::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::getDebugInfo::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::notifySyspropsChanged::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::IWifiChip::unlinkToDeath::passthrough");
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
    const ::android::sp<IWifiChip> mImpl;
    ::android::hardware::details::TaskRunner mOnewayQueue;

    ::android::hardware::Return<void> addOnewayTask(std::function<void(void)>);

};

}  // namespace V1_2
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_2_BSWIFICHIP_H
