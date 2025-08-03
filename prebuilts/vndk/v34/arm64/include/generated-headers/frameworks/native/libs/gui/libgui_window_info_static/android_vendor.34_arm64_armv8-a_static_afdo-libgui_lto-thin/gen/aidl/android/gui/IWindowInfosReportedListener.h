#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <binder/Trace.h>
#include <utils/StrongPointer.h>

namespace android {
namespace gui {
class IWindowInfosReportedListenerDelegator;

class IWindowInfosReportedListener : public ::android::IInterface {
public:
  typedef IWindowInfosReportedListenerDelegator DefaultDelegator;
  DECLARE_META_INTERFACE(WindowInfosReportedListener)
  virtual ::android::binder::Status onWindowInfosReported() = 0;
};  // class IWindowInfosReportedListener

class IWindowInfosReportedListenerDefault : public IWindowInfosReportedListener {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status onWindowInfosReported() override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class IWindowInfosReportedListenerDefault
}  // namespace gui
}  // namespace android
