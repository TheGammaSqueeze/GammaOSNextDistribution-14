#pragma once

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <utils/Errors.h>
#include <android/os/IInputConstants.h>

namespace android {

namespace os {

class BpInputConstants : public ::android::BpInterface<IInputConstants> {
public:
  explicit BpInputConstants(const ::android::sp<::android::IBinder>& _aidl_impl);
  virtual ~BpInputConstants() = default;
};  // class BpInputConstants

}  // namespace os

}  // namespace android
