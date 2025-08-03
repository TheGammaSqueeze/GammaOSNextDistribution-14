#pragma once

#include "aidl/android/hardware/wifi/supplicant/ISupplicant.h"

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
class BnSupplicant : public ::ndk::BnCInterface<ISupplicant> {
public:
  BnSupplicant();
  virtual ~BnSupplicant();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ISupplicantDelegator : public BnSupplicant {
public:
  explicit ISupplicantDelegator(const std::shared_ptr<ISupplicant> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ISupplicant::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus addP2pInterface(const std::string& in_ifName, std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantP2pIface>* _aidl_return) override {
    return _impl->addP2pInterface(in_ifName, _aidl_return);
  }
  ::ndk::ScopedAStatus addStaInterface(const std::string& in_ifName, std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantStaIface>* _aidl_return) override {
    return _impl->addStaInterface(in_ifName, _aidl_return);
  }
  ::ndk::ScopedAStatus getDebugLevel(::aidl::android::hardware::wifi::supplicant::DebugLevel* _aidl_return) override {
    return _impl->getDebugLevel(_aidl_return);
  }
  ::ndk::ScopedAStatus getP2pInterface(const std::string& in_ifName, std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantP2pIface>* _aidl_return) override {
    return _impl->getP2pInterface(in_ifName, _aidl_return);
  }
  ::ndk::ScopedAStatus getStaInterface(const std::string& in_ifName, std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantStaIface>* _aidl_return) override {
    return _impl->getStaInterface(in_ifName, _aidl_return);
  }
  ::ndk::ScopedAStatus isDebugShowKeysEnabled(bool* _aidl_return) override {
    return _impl->isDebugShowKeysEnabled(_aidl_return);
  }
  ::ndk::ScopedAStatus isDebugShowTimestampEnabled(bool* _aidl_return) override {
    return _impl->isDebugShowTimestampEnabled(_aidl_return);
  }
  ::ndk::ScopedAStatus listInterfaces(std::vector<::aidl::android::hardware::wifi::supplicant::IfaceInfo>* _aidl_return) override {
    return _impl->listInterfaces(_aidl_return);
  }
  ::ndk::ScopedAStatus registerCallback(const std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantCallback>& in_callback) override {
    return _impl->registerCallback(in_callback);
  }
  ::ndk::ScopedAStatus removeInterface(const ::aidl::android::hardware::wifi::supplicant::IfaceInfo& in_ifaceInfo) override {
    return _impl->removeInterface(in_ifaceInfo);
  }
  ::ndk::ScopedAStatus setConcurrencyPriority(::aidl::android::hardware::wifi::supplicant::IfaceType in_type) override {
    return _impl->setConcurrencyPriority(in_type);
  }
  ::ndk::ScopedAStatus setDebugParams(::aidl::android::hardware::wifi::supplicant::DebugLevel in_level, bool in_showTimestamp, bool in_showKeys) override {
    return _impl->setDebugParams(in_level, in_showTimestamp, in_showKeys);
  }
  ::ndk::ScopedAStatus terminate() override {
    return _impl->terminate();
  }
protected:
private:
  std::shared_ptr<ISupplicant> _impl;
};

}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
