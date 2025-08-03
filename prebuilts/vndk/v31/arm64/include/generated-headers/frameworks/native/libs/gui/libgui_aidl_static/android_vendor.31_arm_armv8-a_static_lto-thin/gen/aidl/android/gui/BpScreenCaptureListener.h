#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/gui/IScreenCaptureListener.h>

namespace android {

namespace gui {

class BpScreenCaptureListener : public ::android::BpInterface<IScreenCaptureListener> {
public:
  explicit BpScreenCaptureListener(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpScreenCaptureListener() = default;
  ::android::binder::Status onScreenCaptureCompleted(const ::android::gui::ScreenCaptureResults& captureResults) override;
};  // class BpScreenCaptureListener

}  // namespace gui

}  // namespace android
