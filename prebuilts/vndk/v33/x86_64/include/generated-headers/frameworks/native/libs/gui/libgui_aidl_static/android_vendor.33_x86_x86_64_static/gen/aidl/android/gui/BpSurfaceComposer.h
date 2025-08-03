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
  ::android::binder::Status createDisplay(const ::std::string& displayName, bool secure, ::android::sp<::android::IBinder>* _aidl_return) override;
  ::android::binder::Status destroyDisplay(const ::android::sp<::android::IBinder>& display) override;
  ::android::binder::Status getPhysicalDisplayIds(::std::vector<int64_t>* _aidl_return) override;
  ::android::binder::Status getPrimaryPhysicalDisplayId(int64_t* _aidl_return) override;
  ::android::binder::Status getPhysicalDisplayToken(int64_t displayId, ::android::sp<::android::IBinder>* _aidl_return) override;
  ::android::binder::Status setPowerMode(const ::android::sp<::android::IBinder>& display, int32_t mode) override;
  ::android::binder::Status getDisplayStats(const ::android::sp<::android::IBinder>& display, ::android::gui::DisplayStatInfo* _aidl_return) override;
  ::android::binder::Status getDisplayState(const ::android::sp<::android::IBinder>& display, ::android::gui::DisplayState* _aidl_return) override;
  ::android::binder::Status clearBootDisplayMode(const ::android::sp<::android::IBinder>& display) override;
  ::android::binder::Status getBootDisplayModeSupport(bool* _aidl_return) override;
  ::android::binder::Status setAutoLowLatencyMode(const ::android::sp<::android::IBinder>& display, bool on) override;
  ::android::binder::Status setGameContentType(const ::android::sp<::android::IBinder>& display, bool on) override;
  ::android::binder::Status captureDisplay(const ::android::gui::DisplayCaptureArgs& args, const ::android::sp<::android::gui::IScreenCaptureListener>& listener) override;
  ::android::binder::Status captureDisplayById(int64_t displayId, const ::android::sp<::android::gui::IScreenCaptureListener>& listener) override;
  ::android::binder::Status captureLayers(const ::android::gui::LayerCaptureArgs& args, const ::android::sp<::android::gui::IScreenCaptureListener>& listener) override;
  ::android::binder::Status isWideColorDisplay(const ::android::sp<::android::IBinder>& token, bool* _aidl_return) override;
  ::android::binder::Status getDisplayBrightnessSupport(const ::android::sp<::android::IBinder>& displayToken, bool* _aidl_return) override;
  ::android::binder::Status setDisplayBrightness(const ::android::sp<::android::IBinder>& displayToken, const ::android::gui::DisplayBrightness& brightness) override;
  ::android::binder::Status addHdrLayerInfoListener(const ::android::sp<::android::IBinder>& displayToken, const ::android::sp<::android::gui::IHdrLayerInfoListener>& listener) override;
  ::android::binder::Status removeHdrLayerInfoListener(const ::android::sp<::android::IBinder>& displayToken, const ::android::sp<::android::gui::IHdrLayerInfoListener>& listener) override;
  ::android::binder::Status notifyPowerBoost(int32_t boostId) override;
};  // class BpSurfaceComposer
}  // namespace gui
}  // namespace android
