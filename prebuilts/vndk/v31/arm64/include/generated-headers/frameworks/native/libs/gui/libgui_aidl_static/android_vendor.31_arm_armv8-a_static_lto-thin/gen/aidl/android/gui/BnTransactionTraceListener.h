#pragma once

#include <binder/IInterface.h>
#include <android/gui/ITransactionTraceListener.h>

namespace android {

namespace gui {

class BnTransactionTraceListener : public ::android::BnInterface<ITransactionTraceListener> {
public:
  static constexpr uint32_t TRANSACTION_onToggled = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  explicit BnTransactionTraceListener();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnTransactionTraceListener

}  // namespace gui

}  // namespace android
