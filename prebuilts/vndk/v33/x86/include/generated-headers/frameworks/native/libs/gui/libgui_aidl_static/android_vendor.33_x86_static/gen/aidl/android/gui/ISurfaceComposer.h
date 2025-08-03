#pragma once

#include <android/gui/DisplayBrightness.h>
#include <android/gui/DisplayStatInfo.h>
#include <android/gui/DisplayState.h>
#include <android/gui/IHdrLayerInfoListener.h>
#include <android/gui/IScreenCaptureListener.h>
#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <cstdint>
#include <gui/DisplayCaptureArgs.h>
#include <gui/LayerCaptureArgs.h>
#include <string>
#include <utils/StrongPointer.h>
#include <vector>

namespace android {
namespace gui {
class ISurfaceComposer : public ::android::IInterface {
public:
  DECLARE_META_INTERFACE(SurfaceComposer)
  virtual ::android::binder::Status createDisplay(const ::std::string& displayName, bool secure, ::android::sp<::android::IBinder>* _aidl_return) = 0;
  virtual ::android::binder::Status destroyDisplay(const ::android::sp<::android::IBinder>& display) = 0;
  virtual ::android::binder::Status getPhysicalDisplayIds(::std::vector<int64_t>* _aidl_return) = 0;
  virtual ::android::binder::Status getPrimaryPhysicalDisplayId(int64_t* _aidl_return) = 0;
  virtual ::android::binder::Status getPhysicalDisplayToken(int64_t displayId, ::android::sp<::android::IBinder>* _aidl_return) = 0;
  virtual ::android::binder::Status setPowerMode(const ::android::sp<::android::IBinder>& display, int32_t mode) = 0;
  virtual ::android::binder::Status getDisplayStats(const ::android::sp<::android::IBinder>& display, ::android::gui::DisplayStatInfo* _aidl_return) = 0;
  virtual ::android::binder::Status getDisplayState(const ::android::sp<::android::IBinder>& display, ::android::gui::DisplayState* _aidl_return) = 0;
  virtual ::android::binder::Status clearBootDisplayMode(const ::android::sp<::android::IBinder>& display) = 0;
  virtual ::android::binder::Status getBootDisplayModeSupport(bool* _aidl_return) = 0;
  virtual ::android::binder::Status setAutoLowLatencyMode(const ::android::sp<::android::IBinder>& display, bool on) = 0;
  virtual ::android::binder::Status setGameContentType(const ::android::sp<::android::IBinder>& display, bool on) = 0;
  virtual ::android::binder::Status captureDisplay(const ::android::gui::DisplayCaptureArgs& args, const ::android::sp<::android::gui::IScreenCaptureListener>& listener) = 0;
  virtual ::android::binder::Status captureDisplayById(int64_t displayId, const ::android::sp<::android::gui::IScreenCaptureListener>& listener) = 0;
  virtual ::android::binder::Status captureLayers(const ::android::gui::LayerCaptureArgs& args, const ::android::sp<::android::gui::IScreenCaptureListener>& listener) = 0;
  virtual ::android::binder::Status isWideColorDisplay(const ::android::sp<::android::IBinder>& token, bool* _aidl_return) = 0;
  virtual ::android::binder::Status getDisplayBrightnessSupport(const ::android::sp<::android::IBinder>& displayToken, bool* _aidl_return) = 0;
  virtual ::android::binder::Status setDisplayBrightness(const ::android::sp<::android::IBinder>& displayToken, const ::android::gui::DisplayBrightness& brightness) = 0;
  virtual ::android::binder::Status addHdrLayerInfoListener(const ::android::sp<::android::IBinder>& displayToken, const ::android::sp<::android::gui::IHdrLayerInfoListener>& listener) = 0;
  virtual ::android::binder::Status removeHdrLayerInfoListener(const ::android::sp<::android::IBinder>& displayToken, const ::android::sp<::android::gui::IHdrLayerInfoListener>& listener) = 0;
  virtual ::android::binder::Status notifyPowerBoost(int32_t boostId) = 0;
};  // class ISurfaceComposer

class ISurfaceComposerDefault : public ISurfaceComposer {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status createDisplay(const ::std::string& /*displayName*/, bool /*secure*/, ::android::sp<::android::IBinder>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status destroyDisplay(const ::android::sp<::android::IBinder>& /*display*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status getPhysicalDisplayIds(::std::vector<int64_t>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status getPrimaryPhysicalDisplayId(int64_t* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status getPhysicalDisplayToken(int64_t /*displayId*/, ::android::sp<::android::IBinder>* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status setPowerMode(const ::android::sp<::android::IBinder>& /*display*/, int32_t /*mode*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status getDisplayStats(const ::android::sp<::android::IBinder>& /*display*/, ::android::gui::DisplayStatInfo* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status getDisplayState(const ::android::sp<::android::IBinder>& /*display*/, ::android::gui::DisplayState* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status clearBootDisplayMode(const ::android::sp<::android::IBinder>& /*display*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status getBootDisplayModeSupport(bool* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status setAutoLowLatencyMode(const ::android::sp<::android::IBinder>& /*display*/, bool /*on*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status setGameContentType(const ::android::sp<::android::IBinder>& /*display*/, bool /*on*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status captureDisplay(const ::android::gui::DisplayCaptureArgs& /*args*/, const ::android::sp<::android::gui::IScreenCaptureListener>& /*listener*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status captureDisplayById(int64_t /*displayId*/, const ::android::sp<::android::gui::IScreenCaptureListener>& /*listener*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status captureLayers(const ::android::gui::LayerCaptureArgs& /*args*/, const ::android::sp<::android::gui::IScreenCaptureListener>& /*listener*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status isWideColorDisplay(const ::android::sp<::android::IBinder>& /*token*/, bool* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status getDisplayBrightnessSupport(const ::android::sp<::android::IBinder>& /*displayToken*/, bool* /*_aidl_return*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status setDisplayBrightness(const ::android::sp<::android::IBinder>& /*displayToken*/, const ::android::gui::DisplayBrightness& /*brightness*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status addHdrLayerInfoListener(const ::android::sp<::android::IBinder>& /*displayToken*/, const ::android::sp<::android::gui::IHdrLayerInfoListener>& /*listener*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status removeHdrLayerInfoListener(const ::android::sp<::android::IBinder>& /*displayToken*/, const ::android::sp<::android::gui::IHdrLayerInfoListener>& /*listener*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
  ::android::binder::Status notifyPowerBoost(int32_t /*boostId*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class ISurfaceComposerDefault
}  // namespace gui
}  // namespace android
