#pragma once

#include <binder/IInterface.h>
#include <android/gui/ISurfaceComposer.h>
#include <android/gui/BnDisplayEventConnection.h>
#include <android/gui/BnFpsListener.h>
#include <android/gui/BnHdrLayerInfoListener.h>
#include <android/gui/BnRegionSamplingListener.h>
#include <android/gui/BnScreenCaptureListener.h>
#include <android/gui/BnSurfaceComposer.h>
#include <android/gui/BnSurfaceComposerClient.h>
#include <android/gui/BnTunnelModeEnabledListener.h>
#include <android/gui/BnWindowInfosListener.h>
#include <binder/Delegate.h>


namespace android {
namespace gui {
class BnSurfaceComposer : public ::android::BnInterface<ISurfaceComposer> {
public:
  static constexpr uint32_t TRANSACTION_bootFinished = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_createDisplayEventConnection = ::android::IBinder::FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_createConnection = ::android::IBinder::FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_createDisplay = ::android::IBinder::FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_destroyDisplay = ::android::IBinder::FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_getPhysicalDisplayIds = ::android::IBinder::FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_getPhysicalDisplayToken = ::android::IBinder::FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_getSupportedFrameTimestamps = ::android::IBinder::FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_setPowerMode = ::android::IBinder::FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_getDisplayStats = ::android::IBinder::FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_getDisplayState = ::android::IBinder::FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_getStaticDisplayInfo = ::android::IBinder::FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_getDynamicDisplayInfoFromId = ::android::IBinder::FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_getDynamicDisplayInfoFromToken = ::android::IBinder::FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_getDisplayNativePrimaries = ::android::IBinder::FIRST_CALL_TRANSACTION + 14;
  static constexpr uint32_t TRANSACTION_setActiveColorMode = ::android::IBinder::FIRST_CALL_TRANSACTION + 15;
  static constexpr uint32_t TRANSACTION_setBootDisplayMode = ::android::IBinder::FIRST_CALL_TRANSACTION + 16;
  static constexpr uint32_t TRANSACTION_clearBootDisplayMode = ::android::IBinder::FIRST_CALL_TRANSACTION + 17;
  static constexpr uint32_t TRANSACTION_getBootDisplayModeSupport = ::android::IBinder::FIRST_CALL_TRANSACTION + 18;
  static constexpr uint32_t TRANSACTION_getHdrConversionCapabilities = ::android::IBinder::FIRST_CALL_TRANSACTION + 19;
  static constexpr uint32_t TRANSACTION_setHdrConversionStrategy = ::android::IBinder::FIRST_CALL_TRANSACTION + 20;
  static constexpr uint32_t TRANSACTION_getHdrOutputConversionSupport = ::android::IBinder::FIRST_CALL_TRANSACTION + 21;
  static constexpr uint32_t TRANSACTION_setAutoLowLatencyMode = ::android::IBinder::FIRST_CALL_TRANSACTION + 22;
  static constexpr uint32_t TRANSACTION_setGameContentType = ::android::IBinder::FIRST_CALL_TRANSACTION + 23;
  static constexpr uint32_t TRANSACTION_captureDisplay = ::android::IBinder::FIRST_CALL_TRANSACTION + 24;
  static constexpr uint32_t TRANSACTION_captureDisplayById = ::android::IBinder::FIRST_CALL_TRANSACTION + 25;
  static constexpr uint32_t TRANSACTION_captureLayers = ::android::IBinder::FIRST_CALL_TRANSACTION + 26;
  static constexpr uint32_t TRANSACTION_clearAnimationFrameStats = ::android::IBinder::FIRST_CALL_TRANSACTION + 27;
  static constexpr uint32_t TRANSACTION_getAnimationFrameStats = ::android::IBinder::FIRST_CALL_TRANSACTION + 28;
  static constexpr uint32_t TRANSACTION_overrideHdrTypes = ::android::IBinder::FIRST_CALL_TRANSACTION + 29;
  static constexpr uint32_t TRANSACTION_onPullAtom = ::android::IBinder::FIRST_CALL_TRANSACTION + 30;
  static constexpr uint32_t TRANSACTION_getLayerDebugInfo = ::android::IBinder::FIRST_CALL_TRANSACTION + 31;
  static constexpr uint32_t TRANSACTION_getColorManagement = ::android::IBinder::FIRST_CALL_TRANSACTION + 32;
  static constexpr uint32_t TRANSACTION_getCompositionPreference = ::android::IBinder::FIRST_CALL_TRANSACTION + 33;
  static constexpr uint32_t TRANSACTION_getDisplayedContentSamplingAttributes = ::android::IBinder::FIRST_CALL_TRANSACTION + 34;
  static constexpr uint32_t TRANSACTION_setDisplayContentSamplingEnabled = ::android::IBinder::FIRST_CALL_TRANSACTION + 35;
  static constexpr uint32_t TRANSACTION_getDisplayedContentSample = ::android::IBinder::FIRST_CALL_TRANSACTION + 36;
  static constexpr uint32_t TRANSACTION_getProtectedContentSupport = ::android::IBinder::FIRST_CALL_TRANSACTION + 37;
  static constexpr uint32_t TRANSACTION_isWideColorDisplay = ::android::IBinder::FIRST_CALL_TRANSACTION + 38;
  static constexpr uint32_t TRANSACTION_addRegionSamplingListener = ::android::IBinder::FIRST_CALL_TRANSACTION + 39;
  static constexpr uint32_t TRANSACTION_removeRegionSamplingListener = ::android::IBinder::FIRST_CALL_TRANSACTION + 40;
  static constexpr uint32_t TRANSACTION_addFpsListener = ::android::IBinder::FIRST_CALL_TRANSACTION + 41;
  static constexpr uint32_t TRANSACTION_removeFpsListener = ::android::IBinder::FIRST_CALL_TRANSACTION + 42;
  static constexpr uint32_t TRANSACTION_addTunnelModeEnabledListener = ::android::IBinder::FIRST_CALL_TRANSACTION + 43;
  static constexpr uint32_t TRANSACTION_removeTunnelModeEnabledListener = ::android::IBinder::FIRST_CALL_TRANSACTION + 44;
  static constexpr uint32_t TRANSACTION_setDesiredDisplayModeSpecs = ::android::IBinder::FIRST_CALL_TRANSACTION + 45;
  static constexpr uint32_t TRANSACTION_getDesiredDisplayModeSpecs = ::android::IBinder::FIRST_CALL_TRANSACTION + 46;
  static constexpr uint32_t TRANSACTION_getDisplayBrightnessSupport = ::android::IBinder::FIRST_CALL_TRANSACTION + 47;
  static constexpr uint32_t TRANSACTION_setDisplayBrightness = ::android::IBinder::FIRST_CALL_TRANSACTION + 48;
  static constexpr uint32_t TRANSACTION_addHdrLayerInfoListener = ::android::IBinder::FIRST_CALL_TRANSACTION + 49;
  static constexpr uint32_t TRANSACTION_removeHdrLayerInfoListener = ::android::IBinder::FIRST_CALL_TRANSACTION + 50;
  static constexpr uint32_t TRANSACTION_notifyPowerBoost = ::android::IBinder::FIRST_CALL_TRANSACTION + 51;
  static constexpr uint32_t TRANSACTION_setGlobalShadowSettings = ::android::IBinder::FIRST_CALL_TRANSACTION + 52;
  static constexpr uint32_t TRANSACTION_getDisplayDecorationSupport = ::android::IBinder::FIRST_CALL_TRANSACTION + 53;
  static constexpr uint32_t TRANSACTION_setOverrideFrameRate = ::android::IBinder::FIRST_CALL_TRANSACTION + 54;
  static constexpr uint32_t TRANSACTION_getGpuContextPriority = ::android::IBinder::FIRST_CALL_TRANSACTION + 55;
  static constexpr uint32_t TRANSACTION_getMaxAcquiredBufferCount = ::android::IBinder::FIRST_CALL_TRANSACTION + 56;
  static constexpr uint32_t TRANSACTION_addWindowInfosListener = ::android::IBinder::FIRST_CALL_TRANSACTION + 57;
  static constexpr uint32_t TRANSACTION_removeWindowInfosListener = ::android::IBinder::FIRST_CALL_TRANSACTION + 58;
  static constexpr uint32_t TRANSACTION_getOverlaySupport = ::android::IBinder::FIRST_CALL_TRANSACTION + 59;
  explicit BnSurfaceComposer();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnSurfaceComposer

class ISurfaceComposerDelegator : public BnSurfaceComposer {
public:
  explicit ISurfaceComposerDelegator(const ::android::sp<ISurfaceComposer> &impl) : _aidl_delegate(impl) {}

  ::android::sp<ISurfaceComposer> getImpl() { return _aidl_delegate; }
  ::android::binder::Status bootFinished() override {
    return _aidl_delegate->bootFinished();
  }
  ::android::binder::Status createDisplayEventConnection(::android::gui::ISurfaceComposer::VsyncSource vsyncSource, ::android::gui::ISurfaceComposer::EventRegistration eventRegistration, const ::android::sp<::android::IBinder>& layerHandle, ::android::sp<::android::gui::IDisplayEventConnection>* _aidl_return) override {
    auto _status = _aidl_delegate->createDisplayEventConnection(vsyncSource, eventRegistration, layerHandle, _aidl_return);
    if (*_aidl_return) {
      *_aidl_return = ::android::sp<::android::gui::IDisplayEventConnectionDelegator>::cast(delegate(*_aidl_return));
    }
    return _status;
  }
  ::android::binder::Status createConnection(::android::sp<::android::gui::ISurfaceComposerClient>* _aidl_return) override {
    auto _status = _aidl_delegate->createConnection(_aidl_return);
    if (*_aidl_return) {
      *_aidl_return = ::android::sp<::android::gui::ISurfaceComposerClientDelegator>::cast(delegate(*_aidl_return));
    }
    return _status;
  }
  ::android::binder::Status createDisplay(const ::std::string& displayName, bool secure, float requestedRefreshRate, ::android::sp<::android::IBinder>* _aidl_return) override {
    return _aidl_delegate->createDisplay(displayName, secure, requestedRefreshRate, _aidl_return);
  }
  ::android::binder::Status destroyDisplay(const ::android::sp<::android::IBinder>& display) override {
    return _aidl_delegate->destroyDisplay(display);
  }
  ::android::binder::Status getPhysicalDisplayIds(::std::vector<int64_t>* _aidl_return) override {
    return _aidl_delegate->getPhysicalDisplayIds(_aidl_return);
  }
  ::android::binder::Status getPhysicalDisplayToken(int64_t displayId, ::android::sp<::android::IBinder>* _aidl_return) override {
    return _aidl_delegate->getPhysicalDisplayToken(displayId, _aidl_return);
  }
  ::android::binder::Status getSupportedFrameTimestamps(::std::vector<::android::gui::FrameEvent>* _aidl_return) override {
    return _aidl_delegate->getSupportedFrameTimestamps(_aidl_return);
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
  ::android::binder::Status getStaticDisplayInfo(int64_t displayId, ::android::gui::StaticDisplayInfo* _aidl_return) override {
    return _aidl_delegate->getStaticDisplayInfo(displayId, _aidl_return);
  }
  ::android::binder::Status getDynamicDisplayInfoFromId(int64_t displayId, ::android::gui::DynamicDisplayInfo* _aidl_return) override {
    return _aidl_delegate->getDynamicDisplayInfoFromId(displayId, _aidl_return);
  }
  ::android::binder::Status getDynamicDisplayInfoFromToken(const ::android::sp<::android::IBinder>& display, ::android::gui::DynamicDisplayInfo* _aidl_return) override {
    return _aidl_delegate->getDynamicDisplayInfoFromToken(display, _aidl_return);
  }
  ::android::binder::Status getDisplayNativePrimaries(const ::android::sp<::android::IBinder>& display, ::android::gui::DisplayPrimaries* _aidl_return) override {
    return _aidl_delegate->getDisplayNativePrimaries(display, _aidl_return);
  }
  ::android::binder::Status setActiveColorMode(const ::android::sp<::android::IBinder>& display, int32_t colorMode) override {
    return _aidl_delegate->setActiveColorMode(display, colorMode);
  }
  ::android::binder::Status setBootDisplayMode(const ::android::sp<::android::IBinder>& display, int32_t displayModeId) override {
    return _aidl_delegate->setBootDisplayMode(display, displayModeId);
  }
  ::android::binder::Status clearBootDisplayMode(const ::android::sp<::android::IBinder>& display) override {
    return _aidl_delegate->clearBootDisplayMode(display);
  }
  ::android::binder::Status getBootDisplayModeSupport(bool* _aidl_return) override {
    return _aidl_delegate->getBootDisplayModeSupport(_aidl_return);
  }
  ::android::binder::Status getHdrConversionCapabilities(::std::vector<::android::gui::HdrConversionCapability>* _aidl_return) override {
    return _aidl_delegate->getHdrConversionCapabilities(_aidl_return);
  }
  ::android::binder::Status setHdrConversionStrategy(const ::android::gui::HdrConversionStrategy& hdrConversionStrategy, int32_t* _aidl_return) override {
    return _aidl_delegate->setHdrConversionStrategy(hdrConversionStrategy, _aidl_return);
  }
  ::android::binder::Status getHdrOutputConversionSupport(bool* _aidl_return) override {
    return _aidl_delegate->getHdrOutputConversionSupport(_aidl_return);
  }
  ::android::binder::Status setAutoLowLatencyMode(const ::android::sp<::android::IBinder>& display, bool on) override {
    return _aidl_delegate->setAutoLowLatencyMode(display, on);
  }
  ::android::binder::Status setGameContentType(const ::android::sp<::android::IBinder>& display, bool on) override {
    return _aidl_delegate->setGameContentType(display, on);
  }
  ::android::binder::Status captureDisplay(const ::android::gui::DisplayCaptureArgs& args, const ::android::sp<::android::gui::IScreenCaptureListener>& listener) override {
    ::android::sp<::android::gui::IScreenCaptureListenerDelegator> _listener;
    if (listener) {
      _listener = ::android::sp<::android::gui::IScreenCaptureListenerDelegator>::cast(delegate(listener));
    }
    return _aidl_delegate->captureDisplay(args, _listener);
  }
  ::android::binder::Status captureDisplayById(int64_t displayId, const ::android::sp<::android::gui::IScreenCaptureListener>& listener) override {
    ::android::sp<::android::gui::IScreenCaptureListenerDelegator> _listener;
    if (listener) {
      _listener = ::android::sp<::android::gui::IScreenCaptureListenerDelegator>::cast(delegate(listener));
    }
    return _aidl_delegate->captureDisplayById(displayId, _listener);
  }
  ::android::binder::Status captureLayers(const ::android::gui::LayerCaptureArgs& args, const ::android::sp<::android::gui::IScreenCaptureListener>& listener) override {
    ::android::sp<::android::gui::IScreenCaptureListenerDelegator> _listener;
    if (listener) {
      _listener = ::android::sp<::android::gui::IScreenCaptureListenerDelegator>::cast(delegate(listener));
    }
    return _aidl_delegate->captureLayers(args, _listener);
  }
  ::android::binder::Status clearAnimationFrameStats() override {
    return _aidl_delegate->clearAnimationFrameStats();
  }
  ::android::binder::Status getAnimationFrameStats(::android::gui::FrameStats* _aidl_return) override {
    return _aidl_delegate->getAnimationFrameStats(_aidl_return);
  }
  ::android::binder::Status overrideHdrTypes(const ::android::sp<::android::IBinder>& display, const ::std::vector<int32_t>& hdrTypes) override {
    return _aidl_delegate->overrideHdrTypes(display, hdrTypes);
  }
  ::android::binder::Status onPullAtom(int32_t atomId, ::android::gui::PullAtomData* _aidl_return) override {
    return _aidl_delegate->onPullAtom(atomId, _aidl_return);
  }
  ::android::binder::Status getLayerDebugInfo(::std::vector<::android::gui::LayerDebugInfo>* _aidl_return) override {
    return _aidl_delegate->getLayerDebugInfo(_aidl_return);
  }
  ::android::binder::Status getColorManagement(bool* _aidl_return) override {
    return _aidl_delegate->getColorManagement(_aidl_return);
  }
  ::android::binder::Status getCompositionPreference(::android::gui::CompositionPreference* _aidl_return) override {
    return _aidl_delegate->getCompositionPreference(_aidl_return);
  }
  ::android::binder::Status getDisplayedContentSamplingAttributes(const ::android::sp<::android::IBinder>& display, ::android::gui::ContentSamplingAttributes* _aidl_return) override {
    return _aidl_delegate->getDisplayedContentSamplingAttributes(display, _aidl_return);
  }
  ::android::binder::Status setDisplayContentSamplingEnabled(const ::android::sp<::android::IBinder>& display, bool enable, int8_t componentMask, int64_t maxFrames) override {
    return _aidl_delegate->setDisplayContentSamplingEnabled(display, enable, componentMask, maxFrames);
  }
  ::android::binder::Status getDisplayedContentSample(const ::android::sp<::android::IBinder>& display, int64_t maxFrames, int64_t timestamp, ::android::gui::DisplayedFrameStats* _aidl_return) override {
    return _aidl_delegate->getDisplayedContentSample(display, maxFrames, timestamp, _aidl_return);
  }
  ::android::binder::Status getProtectedContentSupport(bool* _aidl_return) override {
    return _aidl_delegate->getProtectedContentSupport(_aidl_return);
  }
  ::android::binder::Status isWideColorDisplay(const ::android::sp<::android::IBinder>& token, bool* _aidl_return) override {
    return _aidl_delegate->isWideColorDisplay(token, _aidl_return);
  }
  ::android::binder::Status addRegionSamplingListener(const ::android::gui::ARect& samplingArea, const ::android::sp<::android::IBinder>& stopLayerHandle, const ::android::sp<::android::gui::IRegionSamplingListener>& listener) override {
    ::android::sp<::android::gui::IRegionSamplingListenerDelegator> _listener;
    if (listener) {
      _listener = ::android::sp<::android::gui::IRegionSamplingListenerDelegator>::cast(delegate(listener));
    }
    return _aidl_delegate->addRegionSamplingListener(samplingArea, stopLayerHandle, _listener);
  }
  ::android::binder::Status removeRegionSamplingListener(const ::android::sp<::android::gui::IRegionSamplingListener>& listener) override {
    ::android::sp<::android::gui::IRegionSamplingListenerDelegator> _listener;
    if (listener) {
      _listener = ::android::sp<::android::gui::IRegionSamplingListenerDelegator>::cast(delegate(listener));
    }
    return _aidl_delegate->removeRegionSamplingListener(_listener);
  }
  ::android::binder::Status addFpsListener(int32_t taskId, const ::android::sp<::android::gui::IFpsListener>& listener) override {
    ::android::sp<::android::gui::IFpsListenerDelegator> _listener;
    if (listener) {
      _listener = ::android::sp<::android::gui::IFpsListenerDelegator>::cast(delegate(listener));
    }
    return _aidl_delegate->addFpsListener(taskId, _listener);
  }
  ::android::binder::Status removeFpsListener(const ::android::sp<::android::gui::IFpsListener>& listener) override {
    ::android::sp<::android::gui::IFpsListenerDelegator> _listener;
    if (listener) {
      _listener = ::android::sp<::android::gui::IFpsListenerDelegator>::cast(delegate(listener));
    }
    return _aidl_delegate->removeFpsListener(_listener);
  }
  ::android::binder::Status addTunnelModeEnabledListener(const ::android::sp<::android::gui::ITunnelModeEnabledListener>& listener) override {
    ::android::sp<::android::gui::ITunnelModeEnabledListenerDelegator> _listener;
    if (listener) {
      _listener = ::android::sp<::android::gui::ITunnelModeEnabledListenerDelegator>::cast(delegate(listener));
    }
    return _aidl_delegate->addTunnelModeEnabledListener(_listener);
  }
  ::android::binder::Status removeTunnelModeEnabledListener(const ::android::sp<::android::gui::ITunnelModeEnabledListener>& listener) override {
    ::android::sp<::android::gui::ITunnelModeEnabledListenerDelegator> _listener;
    if (listener) {
      _listener = ::android::sp<::android::gui::ITunnelModeEnabledListenerDelegator>::cast(delegate(listener));
    }
    return _aidl_delegate->removeTunnelModeEnabledListener(_listener);
  }
  ::android::binder::Status setDesiredDisplayModeSpecs(const ::android::sp<::android::IBinder>& displayToken, const ::android::gui::DisplayModeSpecs& specs) override {
    return _aidl_delegate->setDesiredDisplayModeSpecs(displayToken, specs);
  }
  ::android::binder::Status getDesiredDisplayModeSpecs(const ::android::sp<::android::IBinder>& displayToken, ::android::gui::DisplayModeSpecs* _aidl_return) override {
    return _aidl_delegate->getDesiredDisplayModeSpecs(displayToken, _aidl_return);
  }
  ::android::binder::Status getDisplayBrightnessSupport(const ::android::sp<::android::IBinder>& displayToken, bool* _aidl_return) override {
    return _aidl_delegate->getDisplayBrightnessSupport(displayToken, _aidl_return);
  }
  ::android::binder::Status setDisplayBrightness(const ::android::sp<::android::IBinder>& displayToken, const ::android::gui::DisplayBrightness& brightness) override {
    return _aidl_delegate->setDisplayBrightness(displayToken, brightness);
  }
  ::android::binder::Status addHdrLayerInfoListener(const ::android::sp<::android::IBinder>& displayToken, const ::android::sp<::android::gui::IHdrLayerInfoListener>& listener) override {
    ::android::sp<::android::gui::IHdrLayerInfoListenerDelegator> _listener;
    if (listener) {
      _listener = ::android::sp<::android::gui::IHdrLayerInfoListenerDelegator>::cast(delegate(listener));
    }
    return _aidl_delegate->addHdrLayerInfoListener(displayToken, _listener);
  }
  ::android::binder::Status removeHdrLayerInfoListener(const ::android::sp<::android::IBinder>& displayToken, const ::android::sp<::android::gui::IHdrLayerInfoListener>& listener) override {
    ::android::sp<::android::gui::IHdrLayerInfoListenerDelegator> _listener;
    if (listener) {
      _listener = ::android::sp<::android::gui::IHdrLayerInfoListenerDelegator>::cast(delegate(listener));
    }
    return _aidl_delegate->removeHdrLayerInfoListener(displayToken, _listener);
  }
  ::android::binder::Status notifyPowerBoost(int32_t boostId) override {
    return _aidl_delegate->notifyPowerBoost(boostId);
  }
  ::android::binder::Status setGlobalShadowSettings(const ::android::gui::Color& ambientColor, const ::android::gui::Color& spotColor, float lightPosY, float lightPosZ, float lightRadius) override {
    return _aidl_delegate->setGlobalShadowSettings(ambientColor, spotColor, lightPosY, lightPosZ, lightRadius);
  }
  ::android::binder::Status getDisplayDecorationSupport(const ::android::sp<::android::IBinder>& displayToken, ::std::optional<::android::gui::DisplayDecorationSupport>* _aidl_return) override {
    return _aidl_delegate->getDisplayDecorationSupport(displayToken, _aidl_return);
  }
  ::android::binder::Status setOverrideFrameRate(int32_t uid, float frameRate) override {
    return _aidl_delegate->setOverrideFrameRate(uid, frameRate);
  }
  ::android::binder::Status getGpuContextPriority(int32_t* _aidl_return) override {
    return _aidl_delegate->getGpuContextPriority(_aidl_return);
  }
  ::android::binder::Status getMaxAcquiredBufferCount(int32_t* _aidl_return) override {
    return _aidl_delegate->getMaxAcquiredBufferCount(_aidl_return);
  }
  ::android::binder::Status addWindowInfosListener(const ::android::sp<::android::gui::IWindowInfosListener>& windowInfosListener, ::android::gui::WindowInfosListenerInfo* _aidl_return) override {
    ::android::sp<::android::gui::IWindowInfosListenerDelegator> _windowInfosListener;
    if (windowInfosListener) {
      _windowInfosListener = ::android::sp<::android::gui::IWindowInfosListenerDelegator>::cast(delegate(windowInfosListener));
    }
    return _aidl_delegate->addWindowInfosListener(_windowInfosListener, _aidl_return);
  }
  ::android::binder::Status removeWindowInfosListener(const ::android::sp<::android::gui::IWindowInfosListener>& windowInfosListener) override {
    ::android::sp<::android::gui::IWindowInfosListenerDelegator> _windowInfosListener;
    if (windowInfosListener) {
      _windowInfosListener = ::android::sp<::android::gui::IWindowInfosListenerDelegator>::cast(delegate(windowInfosListener));
    }
    return _aidl_delegate->removeWindowInfosListener(_windowInfosListener);
  }
  ::android::binder::Status getOverlaySupport(::android::gui::OverlayProperties* _aidl_return) override {
    return _aidl_delegate->getOverlaySupport(_aidl_return);
  }
private:
  ::android::sp<ISurfaceComposer> _aidl_delegate;
};  // class ISurfaceComposerDelegator
}  // namespace gui
}  // namespace android
