#pragma once

#include <binder/IInterface.h>
#include <android/gui/IWindowInfosReportedListener.h>

namespace android {

namespace gui {

class BnWindowInfosReportedListener : public ::android::BnInterface<IWindowInfosReportedListener> {
public:
  static constexpr uint32_t TRANSACTION_onWindowInfosReported = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  explicit BnWindowInfosReportedListener();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnWindowInfosReportedListener

}  // namespace gui

}  // namespace android
