#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_3_BSSUPPLICANTSTAIFACECALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_3_BSSUPPLICANTSTAIFACECALLBACK_H

#include <android-base/macros.h>
#include <cutils/trace.h>
#include <future>
#include <android/hardware/wifi/supplicant/1.3/ISupplicantStaIfaceCallback.h>

#include <hidl/HidlPassthroughSupport.h>
#include <hidl/TaskRunner.h>
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace V1_3 {

struct BsSupplicantStaIfaceCallback : ISupplicantStaIfaceCallback, ::android::hardware::details::HidlInstrumentor {
    explicit BsSupplicantStaIfaceCallback(const ::android::sp<ISupplicantStaIfaceCallback> impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ISupplicantStaIfaceCallback Pure;

    typedef ::android::hardware::details::bs_tag _hidl_tag;

    // Methods from ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback follow.
    ::android::hardware::Return<void> onNetworkAdded(uint32_t id) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onNetworkAdded::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&id);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onNetworkAdded", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , id] {
            mImpl->onNetworkAdded(id);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onNetworkAdded", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onNetworkRemoved(uint32_t id) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onNetworkRemoved::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&id);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onNetworkRemoved", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , id] {
            mImpl->onNetworkRemoved(id);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onNetworkRemoved", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onStateChanged(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State newState, const ::android::hardware::hidl_array<uint8_t, 6>& bssid, uint32_t id, const ::android::hardware::hidl_vec<uint8_t>& ssid) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onStateChanged::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&newState);
            _hidl_args.push_back((void *)&bssid);
            _hidl_args.push_back((void *)&id);
            _hidl_args.push_back((void *)&ssid);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onStateChanged", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , newState, bssid, id, ssid] {
            mImpl->onStateChanged(newState, bssid, id, ssid);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onStateChanged", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onAnqpQueryDone(const ::android::hardware::hidl_array<uint8_t, 6>& bssid, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::AnqpData& data, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::Hs20AnqpData& hs20Data) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onAnqpQueryDone::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&bssid);
            _hidl_args.push_back((void *)&data);
            _hidl_args.push_back((void *)&hs20Data);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onAnqpQueryDone", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , bssid, data, hs20Data] {
            mImpl->onAnqpQueryDone(bssid, data, hs20Data);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onAnqpQueryDone", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onHs20IconQueryDone(const ::android::hardware::hidl_array<uint8_t, 6>& bssid, const ::android::hardware::hidl_string& fileName, const ::android::hardware::hidl_vec<uint8_t>& data) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onHs20IconQueryDone::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&bssid);
            _hidl_args.push_back((void *)&fileName);
            _hidl_args.push_back((void *)&data);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onHs20IconQueryDone", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , bssid, fileName, data] {
            mImpl->onHs20IconQueryDone(bssid, fileName, data);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onHs20IconQueryDone", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onHs20SubscriptionRemediation(const ::android::hardware::hidl_array<uint8_t, 6>& bssid, ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::OsuMethod osuMethod, const ::android::hardware::hidl_string& url) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onHs20SubscriptionRemediation::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&bssid);
            _hidl_args.push_back((void *)&osuMethod);
            _hidl_args.push_back((void *)&url);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onHs20SubscriptionRemediation", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , bssid, osuMethod, url] {
            mImpl->onHs20SubscriptionRemediation(bssid, osuMethod, url);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onHs20SubscriptionRemediation", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onHs20DeauthImminentNotice(const ::android::hardware::hidl_array<uint8_t, 6>& bssid, uint32_t reasonCode, uint32_t reAuthDelayInSec, const ::android::hardware::hidl_string& url) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onHs20DeauthImminentNotice::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&bssid);
            _hidl_args.push_back((void *)&reasonCode);
            _hidl_args.push_back((void *)&reAuthDelayInSec);
            _hidl_args.push_back((void *)&url);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onHs20DeauthImminentNotice", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , bssid, reasonCode, reAuthDelayInSec, url] {
            mImpl->onHs20DeauthImminentNotice(bssid, reasonCode, reAuthDelayInSec, url);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onHs20DeauthImminentNotice", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onDisconnected(const ::android::hardware::hidl_array<uint8_t, 6>& bssid, bool locallyGenerated, ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::ReasonCode reasonCode) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onDisconnected::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&bssid);
            _hidl_args.push_back((void *)&locallyGenerated);
            _hidl_args.push_back((void *)&reasonCode);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onDisconnected", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , bssid, locallyGenerated, reasonCode] {
            mImpl->onDisconnected(bssid, locallyGenerated, reasonCode);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onDisconnected", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onAssociationRejected(const ::android::hardware::hidl_array<uint8_t, 6>& bssid, ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::StatusCode statusCode, bool timedOut) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onAssociationRejected::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&bssid);
            _hidl_args.push_back((void *)&statusCode);
            _hidl_args.push_back((void *)&timedOut);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onAssociationRejected", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , bssid, statusCode, timedOut] {
            mImpl->onAssociationRejected(bssid, statusCode, timedOut);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onAssociationRejected", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onAuthenticationTimeout(const ::android::hardware::hidl_array<uint8_t, 6>& bssid) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onAuthenticationTimeout::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&bssid);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onAuthenticationTimeout", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , bssid] {
            mImpl->onAuthenticationTimeout(bssid);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onAuthenticationTimeout", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onEapFailure() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onEapFailure::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onEapFailure", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        ] {
            mImpl->onEapFailure();

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onEapFailure", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onBssidChanged(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::BssidChangeReason reason, const ::android::hardware::hidl_array<uint8_t, 6>& bssid) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onBssidChanged::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&reason);
            _hidl_args.push_back((void *)&bssid);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onBssidChanged", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , reason, bssid] {
            mImpl->onBssidChanged(reason, bssid);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onBssidChanged", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onWpsEventSuccess() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onWpsEventSuccess::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onWpsEventSuccess", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        ] {
            mImpl->onWpsEventSuccess();

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onWpsEventSuccess", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onWpsEventFail(const ::android::hardware::hidl_array<uint8_t, 6>& bssid, ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsConfigError configError, ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::WpsErrorIndication errorInd) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onWpsEventFail::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&bssid);
            _hidl_args.push_back((void *)&configError);
            _hidl_args.push_back((void *)&errorInd);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onWpsEventFail", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , bssid, configError, errorInd] {
            mImpl->onWpsEventFail(bssid, configError, errorInd);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onWpsEventFail", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onWpsEventPbcOverlap() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onWpsEventPbcOverlap::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onWpsEventPbcOverlap", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        ] {
            mImpl->onWpsEventPbcOverlap();

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onWpsEventPbcOverlap", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onExtRadioWorkStart(uint32_t id) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onExtRadioWorkStart::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&id);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onExtRadioWorkStart", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , id] {
            mImpl->onExtRadioWorkStart(id);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onExtRadioWorkStart", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onExtRadioWorkTimeout(uint32_t id) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onExtRadioWorkTimeout::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&id);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onExtRadioWorkTimeout", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , id] {
            mImpl->onExtRadioWorkTimeout(id);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.0", "ISupplicantStaIfaceCallback", "onExtRadioWorkTimeout", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }

    // Methods from ::android::hardware::wifi::supplicant::V1_1::ISupplicantStaIfaceCallback follow.
    ::android::hardware::Return<void> onEapFailure_1_1(::android::hardware::wifi::supplicant::V1_1::ISupplicantStaIfaceCallback::EapErrorCode errorCode) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onEapFailure_1_1::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&errorCode);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.1", "ISupplicantStaIfaceCallback", "onEapFailure_1_1", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , errorCode] {
            mImpl->onEapFailure_1_1(errorCode);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.1", "ISupplicantStaIfaceCallback", "onEapFailure_1_1", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }

    // Methods from ::android::hardware::wifi::supplicant::V1_2::ISupplicantStaIfaceCallback follow.
    ::android::hardware::Return<void> onDppSuccessConfigReceived(const ::android::hardware::hidl_vec<uint8_t>& ssid, const ::android::hardware::hidl_string& password, const ::android::hardware::hidl_array<uint8_t, 32>& psk, ::android::hardware::wifi::supplicant::V1_2::DppAkm securityAkm) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onDppSuccessConfigReceived::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&ssid);
            _hidl_args.push_back((void *)&password);
            _hidl_args.push_back((void *)&psk);
            _hidl_args.push_back((void *)&securityAkm);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.2", "ISupplicantStaIfaceCallback", "onDppSuccessConfigReceived", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , ssid, password, psk, securityAkm] {
            mImpl->onDppSuccessConfigReceived(ssid, password, psk, securityAkm);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.2", "ISupplicantStaIfaceCallback", "onDppSuccessConfigReceived", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onDppSuccessConfigSent() override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onDppSuccessConfigSent::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.2", "ISupplicantStaIfaceCallback", "onDppSuccessConfigSent", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        ] {
            mImpl->onDppSuccessConfigSent();

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.2", "ISupplicantStaIfaceCallback", "onDppSuccessConfigSent", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onDppProgress(::android::hardware::wifi::supplicant::V1_2::DppProgressCode code) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onDppProgress::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&code);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.2", "ISupplicantStaIfaceCallback", "onDppProgress", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , code] {
            mImpl->onDppProgress(code);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.2", "ISupplicantStaIfaceCallback", "onDppProgress", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onDppFailure(::android::hardware::wifi::supplicant::V1_2::DppFailureCode code) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onDppFailure::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&code);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.2", "ISupplicantStaIfaceCallback", "onDppFailure", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , code] {
            mImpl->onDppFailure(code);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.2", "ISupplicantStaIfaceCallback", "onDppFailure", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }

    // Methods from ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback follow.
    ::android::hardware::Return<void> onPmkCacheAdded(int64_t expirationTimeInSec, const ::android::hardware::hidl_vec<uint8_t>& serializedEntry) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onPmkCacheAdded::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&expirationTimeInSec);
            _hidl_args.push_back((void *)&serializedEntry);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.3", "ISupplicantStaIfaceCallback", "onPmkCacheAdded", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , expirationTimeInSec, serializedEntry] {
            mImpl->onPmkCacheAdded(expirationTimeInSec, serializedEntry);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.3", "ISupplicantStaIfaceCallback", "onPmkCacheAdded", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onDppSuccess(::android::hardware::wifi::supplicant::V1_3::DppSuccessCode code) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onDppSuccess::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&code);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.3", "ISupplicantStaIfaceCallback", "onDppSuccess", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , code] {
            mImpl->onDppSuccess(code);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.3", "ISupplicantStaIfaceCallback", "onDppSuccess", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onDppProgress_1_3(::android::hardware::wifi::supplicant::V1_3::DppProgressCode code) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onDppProgress_1_3::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&code);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.3", "ISupplicantStaIfaceCallback", "onDppProgress_1_3", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , code] {
            mImpl->onDppProgress_1_3(code);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.3", "ISupplicantStaIfaceCallback", "onDppProgress_1_3", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onDppFailure_1_3(::android::hardware::wifi::supplicant::V1_3::DppFailureCode code, const ::android::hardware::hidl_string& ssid, const ::android::hardware::hidl_string& channelList, const ::android::hardware::hidl_vec<uint16_t>& bandList) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onDppFailure_1_3::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&code);
            _hidl_args.push_back((void *)&ssid);
            _hidl_args.push_back((void *)&channelList);
            _hidl_args.push_back((void *)&bandList);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.3", "ISupplicantStaIfaceCallback", "onDppFailure_1_3", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , code, ssid, channelList, bandList] {
            mImpl->onDppFailure_1_3(code, ssid, channelList, bandList);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.3", "ISupplicantStaIfaceCallback", "onDppFailure_1_3", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onBssTmHandlingDone(const ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback::BssTmData& tmData) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onBssTmHandlingDone::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&tmData);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.3", "ISupplicantStaIfaceCallback", "onBssTmHandlingDone", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , tmData] {
            mImpl->onBssTmHandlingDone(tmData);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.3", "ISupplicantStaIfaceCallback", "onBssTmHandlingDone", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onEapFailure_1_3(uint32_t errorCode) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onEapFailure_1_3::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&errorCode);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.3", "ISupplicantStaIfaceCallback", "onEapFailure_1_3", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , errorCode] {
            mImpl->onEapFailure_1_3(errorCode);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.3", "ISupplicantStaIfaceCallback", "onEapFailure_1_3", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }
    ::android::hardware::Return<void> onStateChanged_1_3(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback::State newState, const ::android::hardware::hidl_array<uint8_t, 6>& bssid, uint32_t id, const ::android::hardware::hidl_vec<uint8_t>& ssid, bool filsHlpSent) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::onStateChanged_1_3::passthrough");
        #ifdef __ANDROID_DEBUGGABLE__
        if (UNLIKELY(mEnableInstrumentation)) {
            std::vector<void *> _hidl_args;
            _hidl_args.push_back((void *)&newState);
            _hidl_args.push_back((void *)&bssid);
            _hidl_args.push_back((void *)&id);
            _hidl_args.push_back((void *)&ssid);
            _hidl_args.push_back((void *)&filsHlpSent);
            for (const auto &callback: mInstrumentationCallbacks) {
                callback(InstrumentationEvent::PASSTHROUGH_ENTRY, "android.hardware.wifi.supplicant", "1.3", "ISupplicantStaIfaceCallback", "onStateChanged_1_3", &_hidl_args);
            }
        }
        #endif // __ANDROID_DEBUGGABLE__

        ::android::hardware::Status _hidl_error = ::android::hardware::Status::ok();
        auto _hidl_return = addOnewayTask([mImpl = this->mImpl
        #ifdef __ANDROID_DEBUGGABLE__
        , mEnableInstrumentation = this->mEnableInstrumentation, mInstrumentationCallbacks = this->mInstrumentationCallbacks
        #endif // __ANDROID_DEBUGGABLE__
        , newState, bssid, id, ssid, filsHlpSent] {
            mImpl->onStateChanged_1_3(newState, bssid, id, ssid, filsHlpSent);

            atrace_end(ATRACE_TAG_HAL);
            #ifdef __ANDROID_DEBUGGABLE__
            if (UNLIKELY(mEnableInstrumentation)) {
                std::vector<void *> _hidl_args;
                for (const auto &callback: mInstrumentationCallbacks) {
                    callback(InstrumentationEvent::PASSTHROUGH_EXIT, "android.hardware.wifi.supplicant", "1.3", "ISupplicantStaIfaceCallback", "onStateChanged_1_3", &_hidl_args);
                }
            }
            #endif // __ANDROID_DEBUGGABLE__

        });
        return _hidl_return;
    }

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> interfaceChain(interfaceChain_cb _hidl_cb) override {
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::interfaceChain::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::debug::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::interfaceDescriptor::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::getHashChain::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::linkToDeath::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::ping::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::getDebugInfo::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::notifySyspropsChanged::passthrough");
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
        atrace_begin(ATRACE_TAG_HAL, "HIDL::ISupplicantStaIfaceCallback::unlinkToDeath::passthrough");
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
    const ::android::sp<ISupplicantStaIfaceCallback> mImpl;
    ::android::hardware::details::TaskRunner mOnewayQueue;

    ::android::hardware::Return<void> addOnewayTask(std::function<void(void)>);

};

}  // namespace V1_3
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_3_BSSUPPLICANTSTAIFACECALLBACK_H
