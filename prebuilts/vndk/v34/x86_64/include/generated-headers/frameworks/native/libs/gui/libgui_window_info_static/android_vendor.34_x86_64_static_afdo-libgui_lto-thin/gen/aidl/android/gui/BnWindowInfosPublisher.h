#pragma once

#include <binder/IInterface.h>
#include <android/gui/IWindowInfosPublisher.h>
#include <android/gui/BnWindowInfosPublisher.h>
#include <binder/Delegate.h>


namespace android {
namespace gui {
class BnWindowInfosPublisher : public ::android::BnInterface<IWindowInfosPublisher> {
public:
  static constexpr uint32_t TRANSACTION_ackWindowInfosReceived = ::android::IBinder::FIRST_CALL_TRANSACTION + 0;
  explicit BnWindowInfosPublisher();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnWindowInfosPublisher

class IWindowInfosPublisherDelegator : public BnWindowInfosPublisher {
public:
  explicit IWindowInfosPublisherDelegator(const ::android::sp<IWindowInfosPublisher> &impl) : _aidl_delegate(impl) {}

  ::android::sp<IWindowInfosPublisher> getImpl() { return _aidl_delegate; }
  ::android::binder::Status ackWindowInfosReceived(int64_t vsyncId, int64_t listenerId) override {
    return _aidl_delegate->ackWindowInfosReceived(vsyncId, listenerId);
  }
private:
  ::android::sp<IWindowInfosPublisher> _aidl_delegate;
};  // class IWindowInfosPublisherDelegator
}  // namespace gui
}  // namespace android
