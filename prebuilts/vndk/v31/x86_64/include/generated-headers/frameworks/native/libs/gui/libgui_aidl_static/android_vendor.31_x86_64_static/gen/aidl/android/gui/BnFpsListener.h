#pragma once

#include <binder/IInterface.h>
#include <android/gui/IFpsListener.h>

namespace android {

namespace gui {

class BnFpsListener : public ::android::BnInterface<IFpsListener> {
public:
  static constexpr uint32_t TRANSACTION_onFpsReported = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  explicit BnFpsListener();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnFpsListener

}  // namespace gui

}  // namespace android
