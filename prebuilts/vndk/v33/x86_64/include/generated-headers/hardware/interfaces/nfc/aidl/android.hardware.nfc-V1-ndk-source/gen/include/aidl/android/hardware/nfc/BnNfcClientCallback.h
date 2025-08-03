#pragma once

#include "aidl/android/hardware/nfc/INfcClientCallback.h"

#include <android/binder_ibinder.h>
#include <cassert>

#ifndef __BIONIC__
#ifndef __assert2
#define __assert2(a,b,c,d) ((void)0)
#endif
#endif

namespace aidl {
namespace android {
namespace hardware {
namespace nfc {
class BnNfcClientCallback : public ::ndk::BnCInterface<INfcClientCallback> {
public:
  BnNfcClientCallback();
  virtual ~BnNfcClientCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class INfcClientCallbackDelegator : public BnNfcClientCallback {
public:
  explicit INfcClientCallbackDelegator(const std::shared_ptr<INfcClientCallback> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != INfcClientCallback::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus sendData(const std::vector<uint8_t>& in_data) override {
    return _impl->sendData(in_data);
  }
  ::ndk::ScopedAStatus sendEvent(::aidl::android::hardware::nfc::NfcEvent in_event, ::aidl::android::hardware::nfc::NfcStatus in_status) override {
    return _impl->sendEvent(in_event, in_status);
  }
protected:
private:
  std::shared_ptr<INfcClientCallback> _impl;
};

}  // namespace nfc
}  // namespace hardware
}  // namespace android
}  // namespace aidl
