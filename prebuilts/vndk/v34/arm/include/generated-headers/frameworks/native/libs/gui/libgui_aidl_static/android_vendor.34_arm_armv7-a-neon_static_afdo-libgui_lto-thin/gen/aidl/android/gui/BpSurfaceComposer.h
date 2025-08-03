#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/gui/ISurfaceComposer.h>

namespace android {
namespace gui {
class BpSurfaceComposer : public ::android::BpInterface<ISurfaceComposer> {
public:
  explicit BpSurfaceComposer(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpSurfaceComposer() = default;
  ::android::binder::Status bootFinished() override;
  ::android::binder::Status createDisplayEventConnection(::android::gui::ISurfaceComposer::VsyncSource vsyncSource, ::android::gui::ISurfaceComposer::EventRegistration eventRegistration, const ::android::sp<::android::IBinder>& layerHandle, ::android::sp<::android::gui::IDisplayEventConnection>* _aidl_return) override;
  ::android::binder::Status createConnection(::android::sp<::android::gui::ISurfaceComposerClient>* _aidl_return) override;
  ::android::binder::Status createDisplay(const ::std::string& displayName, bool secure, float requestedRefreshRate, ::android::sp<::android::IBinder>* _aidl_return) override;
  ::android::binder::Status destroyDisplay(const ::android::sp<::android::IBinder>& display) override;
  ::android::binder::Status getPhysicalDisplayIds(::std::vector<int64_t>* _aidl_return) override;
  ::android::binder::Status getPhysicalDisplayToken(int64_t displayId, ::android::sp<::android::IBinder>* _aidl_return) override;
  ::android::binder::Status getSupportedFrameTimestamps(::std::vector<::android::gui::FrameEvent>* _aidl_return) override;
  ::android::binder::Status setPowerMode(const ::android::sp<::android::IBinder>& display, int32_t mode) override;
  ::android::binder::Status getDisplayStats(const ::android::sp<::android::IBinder>& display, ::android::gui::DisplayStatInfo* _aidl_return) override;
  ::android::binder::Status getDisplayState(const ::android::sp<::android::IBinder>& display, ::android::gui::DisplayState* _aidl_return) override;
  ::android::binder::Status getStaticDisplayInfo(int64_t displayId, ::android::gui::StaticDisplayInfo* _aidl_return) override;
  ::android::binder::Status getDynamicDisplayInfoFromId(int64_t displayId, ::android::gui::DynamicDisplayInfo* _aidl_return) override;
  ::android::binder::Status getDynamicDisplayInfoFromToken(const ::android::sp<::android::IBinder>& display, ::android::gui::DynamicDisplayInfo* _aidl_return) override;
  ::android::binder::Status getDisplayNativePrimaries(const ::android::sp<::android::IBinder>& display, ::android::gui::DisplayPrimaries* _aidl_return) override;
  ::android::binder::Status setActiveColorMode(const ::android::sp<::android::IBinder>& display, int32_t colorMode) override;
  ::android::binder::Status setBootDisplayMode(const ::android::sp<::android::IBinder>& display, int32_t displayModeId) override;
  ::android::binder::Status clearBootDisplayMode(const ::android::sp<::android::IBinder>& display) override;
  ::android::binder::Status getBootDisplayModeSupport(bool* _aidl_return) override;
  ::android::binder::Status getHdrConversionCapabilities(::std::vector<::android::gui::HdrConversionCapability>* _aidl_return) override;
  ::android::binder::Status setHdrConversionStrategy(const ::android::gui::HdrConversionStrategy& hdrConversionStrategy, int32_t* _aidl_return) override;
  ::android::binder::Status getHdrOutputConversionSupport(bool* _aidl_return) override;
  ::android::binder::Status setAutoLowLatencyMode(const ::android::sp<::android::IBinder>& display, bool on) override;
  ::android::binder::Status setGameContentType(const ::android::sp<::android::IBinder>& display, bool on) override;
  ::android::binder::Status captureDisplay(const ::android::gui::DisplayCaptureArgs& args, const ::android::sp<::android::gui::IScreenCaptureListener>& listener) override;
  ::android::binder::Status captureDisplayById(int64_t displayId, const ::android::sp<::android::gui::IScreenCaptureListener>& listener) override;
  ::android::binder::Status captureLayers(const ::android::gui::LayerCaptureArgs& args, const ::android::sp<::android::gui::IScreenCaptureListener>& listener) override;
  ::android::binder::Status clearAnimationFrameStats() override;
  ::android::binder::Status getAnimationFrameStats(::android::gui::FrameStats* _aidl_return) override;
  ::android::binder::Status overrideHdrTypes(const ::android::sp<::android::IBinder>& display, const ::std::vector<int32_t>& hdrTypes) override;
  ::android::binder::Status onPullAtom(int32_t atomId, ::android::gui::PullAtomData* _aidl_return) override;
  ::android::binder::Status getLayerDebugInfo(::std::vector<::android::gui::LayerDebugInfo>* _aidl_return) override;
  ::android::binder::Status getColorManagement(bool* _aidl_return) override;
  ::android::binder::Status getCompositionPreference(::android::gui::CompositionPreference* _aidl_return) override;
  ::android::binder::Status getDisplayedContentSamplingAttributes(const ::android::sp<::android::IBinder>& display, ::android::gui::ContentSamplingAttributes* _aidl_return) override;
  ::android::binder::Status setDisplayContentSamplingEnabled(const ::android::sp<::android::IBinder>& display, bool enable, int8_t componentMask, int64_t maxFrames) override;
  ::android::binder::Status getDisplayedContentSample(const ::android::sp<::android::IBinder>& display, int64_t maxFrames, int64_t timestamp, ::android::gui::DisplayedFrameStats* _aidl_return) override;
  ::android::binder::Status getProtectedContentSupport(bool* _aidl_return) override;
  ::android::binder::Status isWideColorDisplay(const ::android::sp<::android::IBinder>& token, bool* _aidl_return) override;
  ::android::binder::Status addRegionSamplingListener(const ::android::gui::ARect& samplingArea, const ::android::sp<::android::IBinder>& stopLayerHandle, const ::android::sp<::android::gui::IRegionSamplingListener>& listener) override;
  ::android::binder::Status removeRegionSamplingListener(const ::android::sp<::android::gui::IRegionSamplingListener>& listener) override;
  ::android::binder::Status addFpsListener(int32_t taskId, const ::android::sp<::android::gui::IFpsListener>& listener) override;
  ::android::binder::Status removeFpsListener(const ::android::sp<::android::gui::IFpsListener>& listener) override;
  ::android::binder::Status addTunnelModeEnabledListener(const ::android::sp<::android::gui::ITunnelModeEnabledListener>& listener) override;
  ::android::binder::Status removeTunnelModeEnabledListener(const ::android::sp<::android::gui::ITunnelModeEnabledListener>& listener) override;
  ::android::binder::Status setDesiredDisplayModeSpecs(const ::android::sp<::android::IBinder>& displayToken, const ::android::gui::DisplayModeSpecs& specs) override;
  ::android::binder::Status getDesiredDisplayModeSpecs(const ::android::sp<::android::IBinder>& displayToken, ::android::gui::DisplayModeSpecs* _aidl_return) override;
  ::android::binder::Status getDisplayBrightnessSupport(const ::android::sp<::android::IBinder>& displayToken, bool* _aidl_return) override;
  ::android::binder::Status setDisplayBrightness(const ::android::sp<::android::IBinder>& displayToken, const ::android::gui::DisplayBrightness& brightness) override;
  ::android::binder::Status addHdrLayerInfoListener(const ::android::sp<::android::IBinder>& displayToken, const ::android::sp<::android::gui::IHdrLayerInfoListener>& listener) override;
  ::android::binder::Status removeHdrLayerInfoListener(const ::android::sp<::android::IBinder>& displayToken, const ::android::sp<::android::gui::IHdrLayerInfoListener>& listener) override;
  ::android::binder::Status notifyPowerBoost(int32_t boostId) override;
  ::android::binder::Status setGlobalShadowSettings(const ::android::gui::Color& ambientColor, const ::android::gui::Color& spotColor, float lightPosY, float lightPosZ, float lightRadius) override;
  ::android::binder::Status getDisplayDecorationSupport(const ::android::sp<::android::IBinder>& displayToken, ::std::optional<::android::gui::DisplayDecorationSupport>* _aidl_return) override;
  ::android::binder::Status setOverrideFrameRate(int32_t uid, float frameRate) override;
  ::android::binder::Status getGpuContextPriority(int32_t* _aidl_return) override;
  ::android::binder::Status getMaxAcquiredBufferCount(int32_t* _aidl_return) override;
  ::android::binder::Status addWindowInfosListener(const ::android::sp<::android::gui::IWindowInfosListener>& windowInfosListener, ::android::gui::WindowInfosListenerInfo* _aidl_return) override;
  ::android::binder::Status removeWindowInfosListener(const ::android::sp<::android::gui::IWindowInfosListener>& windowInfosListener) override;
  ::android::binder::Status getOverlaySupport(::android::gui::OverlayProperties* _aidl_return) override;
};  // class BpSurfaceComposer
}  // namespace gui
}  // namespace android
