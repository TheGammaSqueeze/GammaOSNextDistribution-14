#pragma once

#include "aidl/android/hardware/wifi/supplicant/ISupplicantP2pNetwork.h"

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
class BnSupplicantP2pNetwork : public ::ndk::BnCInterface<ISupplicantP2pNetwork> {
public:
  BnSupplicantP2pNetwork();
  virtual ~BnSupplicantP2pNetwork();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ISupplicantP2pNetworkDelegator : public BnSupplicantP2pNetwork {
public:
  explicit ISupplicantP2pNetworkDelegator(const std::shared_ptr<ISupplicantP2pNetwork> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ISupplicantP2pNetwork::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus getBssid(std::vector<uint8_t>* _aidl_return) override {
    return _impl->getBssid(_aidl_return);
  }
  ::ndk::ScopedAStatus getClientList(std::vector<::aidl::android::hardware::wifi::supplicant::MacAddress>* _aidl_return) override {
    return _impl->getClientList(_aidl_return);
  }
  ::ndk::ScopedAStatus getId(int32_t* _aidl_return) override {
    return _impl->getId(_aidl_return);
  }
  ::ndk::ScopedAStatus getInterfaceName(std::string* _aidl_return) override {
    return _impl->getInterfaceName(_aidl_return);
  }
  ::ndk::ScopedAStatus getSsid(std::vector<uint8_t>* _aidl_return) override {
    return _impl->getSsid(_aidl_return);
  }
  ::ndk::ScopedAStatus getType(::aidl::android::hardware::wifi::supplicant::IfaceType* _aidl_return) override {
    return _impl->getType(_aidl_return);
  }
  ::ndk::ScopedAStatus isCurrent(bool* _aidl_return) override {
    return _impl->isCurrent(_aidl_return);
  }
  ::ndk::ScopedAStatus isGroupOwner(bool* _aidl_return) override {
    return _impl->isGroupOwner(_aidl_return);
  }
  ::ndk::ScopedAStatus isPersistent(bool* _aidl_return) override {
    return _impl->isPersistent(_aidl_return);
  }
  ::ndk::ScopedAStatus setClientList(const std::vector<::aidl::android::hardware::wifi::supplicant::MacAddress>& in_clients) override {
    return _impl->setClientList(in_clients);
  }
protected:
private:
  std::shared_ptr<ISupplicantP2pNetwork> _impl;
};

}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
