#ifndef AIDL_GENERATED_ANDROID_OS_I_CLIENT_CALLBACK_H_
#define AIDL_GENERATED_ANDROID_OS_I_CLIENT_CALLBACK_H_

#include <binder/IBinder.h>
#include <binder/IInterface.h>
#include <binder/Status.h>
#include <utils/StrongPointer.h>

namespace android {

namespace os {

class IClientCallback : public ::android::IInterface {
public:
  DECLARE_META_INTERFACE(ClientCallback)
  virtual ::android::binder::Status onClients(const ::android::sp<::android::IBinder>& registered, bool hasClients) = 0;
};  // class IClientCallback

class IClientCallbackDefault : public IClientCallback {
public:
  ::android::IBinder* onAsBinder() override {
    return nullptr;
  }
  ::android::binder::Status onClients(const ::android::sp<::android::IBinder>&, bool) override {
    return ::android::binder::Status::fromStatusT(::android::UNKNOWN_TRANSACTION);
  }
};  // class IClientCallbackDefault

}  // namespace os

}  // namespace android

#endif  // AIDL_GENERATED_ANDROID_OS_I_CLIENT_CALLBACK_H_
