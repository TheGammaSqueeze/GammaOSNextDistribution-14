#pragma once

#include "aidl/android/hardware/wifi/hostapd/IHostapd.h"

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
class BnHostapd : public ::ndk::BnCInterface<IHostapd> {
public:
  BnHostapd();
  virtual ~BnHostapd();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IHostapdDelegator : public BnHostapd {
public:
  explicit IHostapdDelegator(const std::shared_ptr<IHostapd> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IHostapd::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus addAccessPoint(const ::aidl::android::hardware::wifi::hostapd::IfaceParams& in_ifaceParams, const ::aidl::android::hardware::wifi::hostapd::NetworkParams& in_nwParams) override {
    return _impl->addAccessPoint(in_ifaceParams, in_nwParams);
  }
  ::ndk::ScopedAStatus forceClientDisconnect(const std::string& in_ifaceName, const std::vector<uint8_t>& in_clientAddress, ::aidl::android::hardware::wifi::hostapd::Ieee80211ReasonCode in_reasonCode) override {
    return _impl->forceClientDisconnect(in_ifaceName, in_clientAddress, in_reasonCode);
  }
  ::ndk::ScopedAStatus registerCallback(const std::shared_ptr<::aidl::android::hardware::wifi::hostapd::IHostapdCallback>& in_callback) override {
    return _impl->registerCallback(in_callback);
  }
  ::ndk::ScopedAStatus removeAccessPoint(const std::string& in_ifaceName) override {
    return _impl->removeAccessPoint(in_ifaceName);
  }
  ::ndk::ScopedAStatus setDebugParams(::aidl::android::hardware::wifi::hostapd::DebugLevel in_level) override {
    return _impl->setDebugParams(in_level);
  }
  ::ndk::ScopedAStatus terminate() override {
    return _impl->terminate();
  }
protected:
private:
  std::shared_ptr<IHostapd> _impl;
};

}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
