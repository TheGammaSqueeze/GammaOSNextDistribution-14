#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <gui/ScreenCaptureResults.h>
#include <utils/StrongPointer.h>

namespace android {

namespace gui {

class IScreenCaptureListener : public ::android::IInterface {
public:
  DECLARE_META_INTERFACE(ScreenCaptureListener)
  virtual ::android::binder::Status onScreenCaptureCompleted(const ::android::gui::ScreenCaptureResults& captureResults) = 0;
};  // class IScreenCaptureListener

class IScreenCaptureListenerDefault : public IScreenCaptureListener {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status onScreenCaptureCompleted(const ::android::gui::ScreenCaptureResults&) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class IScreenCaptureListenerDefault

}  // namespace gui

}  // namespace android
