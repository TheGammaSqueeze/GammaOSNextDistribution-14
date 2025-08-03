#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/os/IServiceCallback.h>

namespace android {

namespace os {

class BpServiceCallback : public ::android::BpInterface<IServiceCallback> {
public:
  explicit BpServiceCallback(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpServiceCallback() = default;
  ::android::binder::Status onRegistration(const ::std::string& name, const ::android::sp<::android::IBinder>& binder) override;
};  // class BpServiceCallback

}  // namespace os

}  // namespace android
