#pragma once

#include "aidl/android/hardware/wifi/hostapd/IHostapdCallback.h"

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
namespace hostapd {
class BnHostapdCallback : public ::ndk::BnCInterface<IHostapdCallback> {
public:
  BnHostapdCallback();
  virtual ~BnHostapdCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IHostapdCallbackDelegator : public BnHostapdCallback {
public:
  explicit IHostapdCallbackDelegator(const std::shared_ptr<IHostapdCallback> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IHostapdCallback::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus onApInstanceInfoChanged(const ::aidl::android::hardware::wifi::hostapd::ApInfo& in_apInfo) override {
    return _impl->onApInstanceInfoChanged(in_apInfo);
  }
  ::ndk::ScopedAStatus onConnectedClientsChanged(const ::aidl::android::hardware::wifi::hostapd::ClientInfo& in_clientInfo) override {
    return _impl->onConnectedClientsChanged(in_clientInfo);
  }
  ::ndk::ScopedAStatus onFailure(const std::string& in_ifaceName, const std::string& in_instanceName) override {
    return _impl->onFailure(in_ifaceName, in_instanceName);
  }
protected:
private:
  std::shared_ptr<IHostapdCallback> _impl;
};

}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
