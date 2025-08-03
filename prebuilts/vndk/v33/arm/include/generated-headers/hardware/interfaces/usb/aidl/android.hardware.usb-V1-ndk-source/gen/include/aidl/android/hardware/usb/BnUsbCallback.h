#pragma once

#include "aidl/android/hardware/usb/IUsbCallback.h"

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
class BnUsbCallback : public ::ndk::BnCInterface<IUsbCallback> {
public:
  BnUsbCallback();
  virtual ~BnUsbCallback();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IUsbCallbackDelegator : public BnUsbCallback {
public:
  explicit IUsbCallbackDelegator(const std::shared_ptr<IUsbCallback> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IUsbCallback::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus notifyPortStatusChange(const std::vector<::aidl::android::hardware::usb::PortStatus>& in_currentPortStatus, ::aidl::android::hardware::usb::Status in_retval) override {
    return _impl->notifyPortStatusChange(in_currentPortStatus, in_retval);
  }
  ::ndk::ScopedAStatus notifyRoleSwitchStatus(const std::string& in_portName, const ::aidl::android::hardware::usb::PortRole& in_newRole, ::aidl::android::hardware::usb::Status in_retval, int64_t in_transactionId) override {
    return _impl->notifyRoleSwitchStatus(in_portName, in_newRole, in_retval, in_transactionId);
  }
  ::ndk::ScopedAStatus notifyEnableUsbDataStatus(const std::string& in_portName, bool in_enable, ::aidl::android::hardware::usb::Status in_retval, int64_t in_transactionId) override {
    return _impl->notifyEnableUsbDataStatus(in_portName, in_enable, in_retval, in_transactionId);
  }
  ::ndk::ScopedAStatus notifyEnableUsbDataWhileDockedStatus(const std::string& in_portName, ::aidl::android::hardware::usb::Status in_retval, int64_t in_transactionId) override {
    return _impl->notifyEnableUsbDataWhileDockedStatus(in_portName, in_retval, in_transactionId);
  }
  ::ndk::ScopedAStatus notifyContaminantEnabledStatus(const std::string& in_portName, bool in_enable, ::aidl::android::hardware::usb::Status in_retval, int64_t in_transactionId) override {
    return _impl->notifyContaminantEnabledStatus(in_portName, in_enable, in_retval, in_transactionId);
  }
  ::ndk::ScopedAStatus notifyQueryPortStatus(const std::string& in_portName, ::aidl::android::hardware::usb::Status in_retval, int64_t in_transactionId) override {
    return _impl->notifyQueryPortStatus(in_portName, in_retval, in_transactionId);
  }
  ::ndk::ScopedAStatus notifyLimitPowerTransferStatus(const std::string& in_portName, bool in_limit, ::aidl::android::hardware::usb::Status in_retval, int64_t in_transactionId) override {
    return _impl->notifyLimitPowerTransferStatus(in_portName, in_limit, in_retval, in_transactionId);
  }
  ::ndk::ScopedAStatus notifyResetUsbPortStatus(const std::string& in_portName, ::aidl::android::hardware::usb::Status in_retval, int64_t in_transactionId) override {
    return _impl->notifyResetUsbPortStatus(in_portName, in_retval, in_transactionId);
  }
protected:
private:
  std::shared_ptr<IUsbCallback> _impl;
};

}  // namespace usb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
