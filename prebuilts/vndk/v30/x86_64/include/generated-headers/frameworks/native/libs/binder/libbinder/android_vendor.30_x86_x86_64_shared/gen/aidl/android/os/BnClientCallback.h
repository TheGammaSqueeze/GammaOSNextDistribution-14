#ifndef AIDL_GENERATED_ANDROID_OS_BN_CLIENT_CALLBACK_H_
#define AIDL_GENERATED_ANDROID_OS_BN_CLIENT_CALLBACK_H_

#include <binder/IInterface.h>
#include <android/os/IClientCallback.h>

namespace android {

namespace os {

class BnClientCallback : public ::android::BnInterface<IClientCallback> {
public:
  explicit BnClientCallback();
  ::android::status_t onTransact(uint32_t _aidl_code, const ::android::Parcel& _aidl_data, ::android::Parcel* _aidl_reply, uint32_t _aidl_flags) override;
};  // class BnClientCallback

}  // namespace os

}  // namespace android

#endif  // AIDL_GENERATED_ANDROID_OS_BN_CLIENT_CALLBACK_H_
