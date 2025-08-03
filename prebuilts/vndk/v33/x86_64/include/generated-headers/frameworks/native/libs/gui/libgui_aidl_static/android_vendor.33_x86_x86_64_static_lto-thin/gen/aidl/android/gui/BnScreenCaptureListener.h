#pragma once

#include <binder/IInterface.h>
#include <android/gui/IScreenCaptureListener.h>

namespace android {
namespace gui {
class BnScreenCaptureListener : public ::android::BnInterface<IScreenCaptureListener> {
public:
  static constexpr uint32_t TRANSACTION_onScreenCaptureCompleted = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  explicit BnScreenCaptureListener();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnScreenCaptureListener

class IScreenCaptureListenerDelegator : public BnScreenCaptureListener {
public:
  explicit IScreenCaptureListenerDelegator(::android::sp<IScreenCaptureListener> &impl) : _aidl_delegate(impl) {}

  ::android::binder::Status onScreenCaptureCompleted(const ::android::gui::ScreenCaptureResults& captureResults) override {
    return _aidl_delegate->onScreenCaptureCompleted(captureResults);
  }
private:
  ::android::sp<IScreenCaptureListener> _aidl_delegate;
};  // class IScreenCaptureListenerDelegator
}  // namespace gui
}  // namespace android
