#pragma once

#include <binder/IInterface.h>
#include <android/gui/IWindowInfosListener.h>

namespace android {

namespace gui {

class BnWindowInfosListener : public ::android::BnInterface<IWindowInfosListener> {
public:
  static constexpr uint32_t TRANSACTION_onWindowInfosChanged = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  explicit BnWindowInfosListener();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnWindowInfosListener

}  // namespace gui

}  // namespace android
