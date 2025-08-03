#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <utils/StrongPointer.h>

namespace android {

namespace gui {

class ITransactionTraceListener : public ::android::IInterface {
public:
  DECLARE_META_INTERFACE(TransactionTraceListener)
  virtual ::android::binder::Status onToggled(bool enabled) = 0;
};  // class ITransactionTraceListener

class ITransactionTraceListenerDefault : public ITransactionTraceListener {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status onToggled(bool) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class ITransactionTraceListenerDefault

}  // namespace gui

}  // namespace android
