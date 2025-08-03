#pragma once

#include <binder/IInterface.h>
#include <android/gui/ISurfaceComposer.h>

namespace android {
namespace gui {
class BnSurfaceComposer : public ::android::BnInterface<ISurfaceComposer> {
public:
  static constexpr uint32_t TRANSACTION_createDisplay = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_destroyDisplay = ::android::IBinder::FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_getPhysicalDisplayIds = ::android::IBinder::FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_getPrimaryPhysicalDisplayId = ::android::IBinder::FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_getPhysicalDisplayToken = ::android::IBinder::FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_setPowerMode = ::android::IBinder::FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_getDisplayStats = ::android::IBinder::FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_getDisplayState = ::android::IBinder::FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_clearBootDisplayMode = ::android::IBinder::FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_getBootDisplayModeSupport = ::android::IBinder::FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_setAutoLowLatencyMode = ::android::IBinder::FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_setGameContentType = ::android::IBinder::FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_captureDisplay = ::android::IBinder::FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_captureDisplayById = ::android::IBinder::FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_captureLayers = ::android::IBinder::FIRST_CALL_TRANSACTION + 14;
  static constexpr uint32_t TRANSACTION_isWideColorDisplay = ::android::IBinder::FIRST_CALL_TRANSACTION + 15;
  static constexpr uint32_t TRANSACTION_getDisplayBrightnessSupport = ::android::IBinder::FIRST_CALL_TRANSACTION + 16;
  static constexpr uint32_t TRANSACTION_setDisplayBrightness = ::android::IBinder::FIRST_CALL_TRANSACTION + 17;
  static constexpr uint32_t TRANSACTION_addHdrLayerInfoListener = ::android::IBinder::FIRST_CALL_TRANSACTION + 18;
  static constexpr uint32_t TRANSACTION_removeHdrLayerInfoListener = ::android::IBinder::FIRST_CALL_TRANSACTION + 19;
  static constexpr uint32_t TRANSACTION_notifyPowerBoost = ::android::IBinder::FIRST_CALL_TRANSACTION + 20;
  explicit BnSurfaceComposer();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnSurfaceComposer

class ISurfaceComposerDelegator : public BnSurfaceComposer {
public:
  explicit ISurfaceComposerDelegator(::android::sp<ISurfaceComposer> &impl) : _aidl_delegate(impl) {}

  ::android::binder::Status createDisplay(const ::std::string& displayName, bool secure, ::android::sp<::android::IBinder>* _aidl_return) override {
    return _aidl_delegate->createDisplay(displayName, secure, _aidl_return);
  }
  ::android::binder::Status destroyDisplay(const ::android::sp<::android::IBinder>& display) override {
    return _aidl_delegate->destroyDisplay(display);
  }
  ::android::binder::Status getPhysicalDisplayIds(::std::vector<int64_t>* _aidl_return) override {
    return _aidl_delegate->getPhysicalDisplayIds(_aidl_return);
  }
  ::android::binder::Status getPrimaryPhysicalDisplayId(int64_t* _aidl_return) override {
    return _aidl_delegate->getPrimaryPhysicalDisplayId(_aidl_return);
  }
  ::android::binder::Status getPhysicalDisplayToken(int64_t displayId, ::android::sp<::android::IBinder>* _aidl_return) override {
    return _aidl_delegate->getPhysicalDisplayToken(displayId, _aidl_return);
  }
  ::android::binder::Status setPowerMode(const ::android::sp<::android::IBinder>& display, int32_t mode) override {
    return _aidl_delegate->setPowerMode(display, mode);
  }
  ::android::binder::Status getDisplayStats(const ::android::sp<::android::IBinder>& display, ::android::gui::DisplayStatInfo* _aidl_return) override {
    return _aidl_delegate->getDisplayStats(display, _aidl_return);
  }
  ::android::binder::Status getDisplayState(const ::android::sp<::android::IBinder>& display, ::android::gui::DisplayState* _aidl_return) override {
    return _aidl_delegate->getDisplayState(display, _aidl_return);
  }
  ::android::binder::Status clearBootDisplayMode(const ::android::sp<::android::IBinder>& display) override {
    return _aidl_delegate->clearBootDisplayMode(display);
  }
  ::android::binder::Status getBootDisplayModeSupport(bool* _aidl_return) override {
    return _aidl_delegate->getBootDisplayModeSupport(_aidl_return);
  }
  ::android::binder::Status setAutoLowLatencyMode(const ::android::sp<::android::IBinder>& display, bool on) override {
    return _aidl_delegate->setAutoLowLatencyMode(display, on);
  }
  ::android::binder::Status setGameContentType(const ::android::sp<::android::IBinder>& display, bool on) override {
    return _aidl_delegate->setGameContentType(display, on);
  }
  ::android::binder::Status captureDisplay(const ::android::gui::DisplayCaptureArgs& args, const ::android::sp<::android::gui::IScreenCaptureListener>& listener) override {
    return _aidl_delegate->captureDisplay(args, listener);
  }
  ::android::binder::Status captureDisplayById(int64_t displayId, const ::android::sp<::android::gui::IScreenCaptureListener>& listener) override {
    return _aidl_delegate->captureDisplayById(displayId, listener);
  }
  ::android::binder::Status captureLayers(const ::android::gui::LayerCaptureArgs& args, const ::android::sp<::android::gui::IScreenCaptureListener>& listener) override {
    return _aidl_delegate->captureLayers(args, listener);
  }
  ::android::binder::Status isWideColorDisplay(const ::android::sp<::android::IBinder>& token, bool* _aidl_return) override {
    return _aidl_delegate->isWideColorDisplay(token, _aidl_return);
  }
  ::android::binder::Status getDisplayBrightnessSupport(const ::android::sp<::android::IBinder>& displayToken, bool* _aidl_return) override {
    return _aidl_delegate->getDisplayBrightnessSupport(displayToken, _aidl_return);
  }
  ::android::binder::Status setDisplayBrightness(const ::android::sp<::android::IBinder>& displayToken, const ::android::gui::DisplayBrightness& brightness) override {
    return _aidl_delegate->setDisplayBrightness(displayToken, brightness);
  }
  ::android::binder::Status addHdrLayerInfoListener(const ::android::sp<::android::IBinder>& displayToken, const ::android::sp<::android::gui::IHdrLayerInfoListener>& listener) override {
    return _aidl_delegate->addHdrLayerInfoListener(displayToken, listener);
  }
  ::android::binder::Status removeHdrLayerInfoListener(const ::android::sp<::android::IBinder>& displayToken, const ::android::sp<::android::gui::IHdrLayerInfoListener>& listener) override {
    return _aidl_delegate->removeHdrLayerInfoListener(displayToken, listener);
  }
  ::android::binder::Status notifyPowerBoost(int32_t boostId) override {
    return _aidl_delegate->notifyPowerBoost(boostId);
  }
private:
  ::android::sp<ISurfaceComposer> _aidl_delegate;
};  // class ISurfaceComposerDelegator
}  // namespace gui
}  // namespace android
