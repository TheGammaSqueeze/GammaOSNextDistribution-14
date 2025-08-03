#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/gui/ITunnelModeEnabledListener.h>

namespace android {

namespace gui {

class BpTunnelModeEnabledListener : public ::android::BpInterface<ITunnelModeEnabledListener> {
public:
  explicit BpTunnelModeEnabledListener(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpTunnelModeEnabledListener() = default;
  ::android::binder::Status onTunnelModeEnabledChanged(bool enabled) override;
};  // class BpTunnelModeEnabledListener

}  // namespace gui

}  // namespace android
