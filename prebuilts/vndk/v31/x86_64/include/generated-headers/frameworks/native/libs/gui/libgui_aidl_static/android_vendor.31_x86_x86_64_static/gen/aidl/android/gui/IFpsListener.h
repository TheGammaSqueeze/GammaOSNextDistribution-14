#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <utils/StrongPointer.h>

namespace android {

namespace gui {

class IFpsListener : public ::android::IInterface {
public:
  DECLARE_META_INTERFACE(FpsListener)
  virtual ::android::binder::Status onFpsReported(float fps) = 0;
};  // class IFpsListener

class IFpsListenerDefault : public IFpsListener {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status onFpsReported(float) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class IFpsListenerDefault

}  // namespace gui

}  // namespace android
