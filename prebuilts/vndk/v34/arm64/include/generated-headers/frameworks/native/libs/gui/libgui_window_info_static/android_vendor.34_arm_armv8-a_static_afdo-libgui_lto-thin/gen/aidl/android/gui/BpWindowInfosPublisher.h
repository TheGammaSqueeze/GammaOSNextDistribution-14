#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/gui/IWindowInfosPublisher.h>

namespace android {
namespace gui {
class BpWindowInfosPublisher : public ::android::BpInterface<IWindowInfosPublisher> {
public:
  explicit BpWindowInfosPublisher(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpWindowInfosPublisher() = default;
  ::android::binder::Status ackWindowInfosReceived(int64_t vsyncId, int64_t listenerId) override;
};  // class BpWindowInfosPublisher
}  // namespace gui
}  // namespace android
