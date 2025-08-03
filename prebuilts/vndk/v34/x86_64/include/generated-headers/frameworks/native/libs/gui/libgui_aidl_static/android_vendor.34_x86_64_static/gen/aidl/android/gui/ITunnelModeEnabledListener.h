#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <binder/Trace.h>
#include <utils/StrongPointer.h>

namespace android {
namespace gui {
class ITunnelModeEnabledListenerDelegator;

class ITunnelModeEnabledListener : public ::android::IInterface {
public:
  typedef ITunnelModeEnabledListenerDelegator DefaultDelegator;
  DECLARE_META_INTERFACE(TunnelModeEnabledListener)
  virtual ::android::binder::Status onTunnelModeEnabledChanged(bool enabled) = 0;
};  // class ITunnelModeEnabledListener

class ITunnelModeEnabledListenerDefault : public ITunnelModeEnabledListener {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status onTunnelModeEnabledChanged(bool /*enabled*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class ITunnelModeEnabledListenerDefault
}  // namespace gui
}  // namespace android
