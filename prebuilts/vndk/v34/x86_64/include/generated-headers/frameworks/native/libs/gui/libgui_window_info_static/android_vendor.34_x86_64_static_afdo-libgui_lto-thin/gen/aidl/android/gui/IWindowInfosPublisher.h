#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <binder/Trace.h>
#include <cstdint>
#include <utils/StrongPointer.h>

namespace android {
namespace gui {
class IWindowInfosPublisherDelegator;

class IWindowInfosPublisher : public ::android::IInterface {
public:
  typedef IWindowInfosPublisherDelegator DefaultDelegator;
  DECLARE_META_INTERFACE(WindowInfosPublisher)
  virtual ::android::binder::Status ackWindowInfosReceived(int64_t vsyncId, int64_t listenerId) = 0;
};  // class IWindowInfosPublisher

class IWindowInfosPublisherDefault : public IWindowInfosPublisher {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status ackWindowInfosReceived(int64_t /*vsyncId*/, int64_t /*listenerId*/) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class IWindowInfosPublisherDefault
}  // namespace gui
}  // namespace android
