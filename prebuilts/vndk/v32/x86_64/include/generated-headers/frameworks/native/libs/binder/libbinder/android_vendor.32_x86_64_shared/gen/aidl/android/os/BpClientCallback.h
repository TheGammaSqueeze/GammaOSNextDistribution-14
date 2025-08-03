#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/os/IClientCallback.h>

namespace android {

namespace os {

class BpClientCallback : public ::android::BpInterface<IClientCallback> {
public:
  explicit BpClientCallback(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpClientCallback() = default;
  ::android::binder::Status onClients(const ::android::sp<::android::IBinder>& registered, bool hasClients) override;
};  // class BpClientCallback

}  // namespace os

}  // namespace android
