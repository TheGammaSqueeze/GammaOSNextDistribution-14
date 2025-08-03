#pragma once

#include "aidl/android/hardware/radio/modem/IRadioModemIndication.h"

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
namespace radio {
namespace modem {
class BnRadioModemIndication : public ::ndk::BnCInterface<IRadioModemIndication> {
public:
  BnRadioModemIndication();
  virtual ~BnRadioModemIndication();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IRadioModemIndicationDelegator : public BnRadioModemIndication {
public:
  explicit IRadioModemIndicationDelegator(const std::shared_ptr<IRadioModemIndication> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IRadioModemIndication::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus hardwareConfigChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::vector<::aidl::android::hardware::radio::modem::HardwareConfig>& in_configs) override {
    return _impl->hardwareConfigChanged(in_type, in_configs);
  }
  ::ndk::ScopedAStatus modemReset(::aidl::android::hardware::radio::RadioIndicationType in_type, const std::string& in_reason) override {
    return _impl->modemReset(in_type, in_reason);
  }
  ::ndk::ScopedAStatus radioCapabilityIndication(::aidl::android::hardware::radio::RadioIndicationType in_type, const ::aidl::android::hardware::radio::modem::RadioCapability& in_rc) override {
    return _impl->radioCapabilityIndication(in_type, in_rc);
  }
  ::ndk::ScopedAStatus radioStateChanged(::aidl::android::hardware::radio::RadioIndicationType in_type, ::aidl::android::hardware::radio::modem::RadioState in_radioState) override {
    return _impl->radioStateChanged(in_type, in_radioState);
  }
  ::ndk::ScopedAStatus rilConnected(::aidl::android::hardware::radio::RadioIndicationType in_type) override {
    return _impl->rilConnected(in_type);
  }
protected:
private:
  std::shared_ptr<IRadioModemIndication> _impl;
};

}  // namespace modem
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
