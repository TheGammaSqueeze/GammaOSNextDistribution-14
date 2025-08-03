#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/gui/ITransactionTraceListener.h>

namespace android {

namespace gui {

class BpTransactionTraceListener : public ::android::BpInterface<ITransactionTraceListener> {
public:
  explicit BpTransactionTraceListener(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpTransactionTraceListener() = default;
  ::android::binder::Status onToggled(bool enabled) override;
};  // class BpTransactionTraceListener

}  // namespace gui

}  // namespace android
