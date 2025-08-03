#pragma once

#include <binder/IInterface.h>
#include <android/gui/ITunnelModeEnabledListener.h>

namespace android {

namespace gui {

class BnTunnelModeEnabledListener : public ::android::BnInterface<ITunnelModeEnabledListener> {
public:
  static constexpr uint32_t TRANSACTION_onTunnelModeEnabledChanged = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  explicit BnTunnelModeEnabledListener();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnTunnelModeEnabledListener

}  // namespace gui

}  // namespace android
