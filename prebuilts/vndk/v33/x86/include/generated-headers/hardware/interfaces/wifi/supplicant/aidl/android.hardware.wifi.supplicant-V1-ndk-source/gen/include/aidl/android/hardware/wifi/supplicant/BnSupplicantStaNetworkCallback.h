#pragma once

#include "aidl/android/hardware/wifi/supplicant/ISupplicantStaNetworkCallback.h"

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
namespace wifi {
namespace supplicant {
class BnSupplicantStaNetworkCallback : public ::ndk::BnCInterface<ISupplicantStaNetworkCallback> {
public:
  BnSupplicantStaNetworkCallback();
  virtual ~BnSupplicantStaNetworkCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ISupplicantStaNetworkCallbackDelegator : public BnSupplicantStaNetworkCallback {
public:
  explicit ISupplicantStaNetworkCallbackDelegator(const std::shared_ptr<ISupplicantStaNetworkCallback> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ISupplicantStaNetworkCallback::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus onNetworkEapIdentityRequest() override {
    return _impl->onNetworkEapIdentityRequest();
  }
  ::ndk::ScopedAStatus onNetworkEapSimGsmAuthRequest(const ::aidl::android::hardware::wifi::supplicant::NetworkRequestEapSimGsmAuthParams& in_params) override {
    return _impl->onNetworkEapSimGsmAuthRequest(in_params);
  }
  ::ndk::ScopedAStatus onNetworkEapSimUmtsAuthRequest(const ::aidl::android::hardware::wifi::supplicant::NetworkRequestEapSimUmtsAuthParams& in_params) override {
    return _impl->onNetworkEapSimUmtsAuthRequest(in_params);
  }
  ::ndk::ScopedAStatus onTransitionDisable(::aidl::android::hardware::wifi::supplicant::TransitionDisableIndication in_ind) override {
    return _impl->onTransitionDisable(in_ind);
  }
  ::ndk::ScopedAStatus onServerCertificateAvailable(int32_t in_depth, const std::vector<uint8_t>& in_subject, const std::vector<uint8_t>& in_certHash, const std::vector<uint8_t>& in_certBlob) override {
    return _impl->onServerCertificateAvailable(in_depth, in_subject, in_certHash, in_certBlob);
  }
protected:
private:
  std::shared_ptr<ISupplicantStaNetworkCallback> _impl;
};

}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
