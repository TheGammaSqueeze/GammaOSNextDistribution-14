#pragma once

#include "aidl/android/hardware/usb/IUsb.h"

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
namespace usb {
class BnUsb : public ::ndk::BnCInterface<IUsb> {
public:
  BnUsb();
  virtual ~BnUsb();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IUsbDelegator : public BnUsb {
public:
  explicit IUsbDelegator(const std::shared_ptr<IUsb> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IUsb::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus enableContaminantPresenceDetection(const std::string& in_portName, bool in_enable, int64_t in_transactionId) override {
    return _impl->enableContaminantPresenceDetection(in_portName, in_enable, in_transactionId);
  }
  ::ndk::ScopedAStatus enableUsbData(const std::string& in_portName, bool in_enable, int64_t in_transactionId) override {
    return _impl->enableUsbData(in_portName, in_enable, in_transactionId);
  }
  ::ndk::ScopedAStatus enableUsbDataWhileDocked(const std::string& in_portName, int64_t in_transactionId) override {
    return _impl->enableUsbDataWhileDocked(in_portName, in_transactionId);
  }
  ::ndk::ScopedAStatus queryPortStatus(int64_t in_transactionId) override {
    return _impl->queryPortStatus(in_transactionId);
  }
  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::usb::IUsbCallback>& in_callback) override {
    return _impl->setCallback(in_callback);
  }
  ::ndk::ScopedAStatus switchRole(const std::string& in_portName, const ::aidl::android::hardware::usb::PortRole& in_role, int64_t in_transactionId) override {
    return _impl->switchRole(in_portName, in_role, in_transactionId);
  }
  ::ndk::ScopedAStatus limitPowerTransfer(const std::string& in_portName, bool in_limit, int64_t in_transactionId) override {
    return _impl->limitPowerTransfer(in_portName, in_limit, in_transactionId);
  }
  ::ndk::ScopedAStatus resetUsbPort(const std::string& in_portName, int64_t in_transactionId) override {
    return _impl->resetUsbPort(in_portName, in_transactionId);
  }
protected:
private:
  std::shared_ptr<IUsb> _impl;
};

}  // namespace usb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
