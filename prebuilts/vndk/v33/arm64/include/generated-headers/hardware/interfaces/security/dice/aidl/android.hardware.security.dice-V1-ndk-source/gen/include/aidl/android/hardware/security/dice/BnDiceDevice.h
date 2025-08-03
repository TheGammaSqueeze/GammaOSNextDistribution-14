#pragma once

#include "aidl/android/hardware/security/dice/IDiceDevice.h"

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
namespace security {
namespace dice {
class BnDiceDevice : public ::ndk::BnCInterface<IDiceDevice> {
public:
  BnDiceDevice();
  virtual ~BnDiceDevice();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IDiceDeviceDelegator : public BnDiceDevice {
public:
  explicit IDiceDeviceDelegator(const std::shared_ptr<IDiceDevice> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IDiceDevice::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus sign(const std::vector<::aidl::android::hardware::security::dice::InputValues>& in_id, const std::vector<uint8_t>& in_payload, ::aidl::android::hardware::security::dice::Signature* _aidl_return) override {
    return _impl->sign(in_id, in_payload, _aidl_return);
  }
  ::ndk::ScopedAStatus getAttestationChain(const std::vector<::aidl::android::hardware::security::dice::InputValues>& in_inputValues, ::aidl::android::hardware::security::dice::Bcc* _aidl_return) override {
    return _impl->getAttestationChain(in_inputValues, _aidl_return);
  }
  ::ndk::ScopedAStatus derive(const std::vector<::aidl::android::hardware::security::dice::InputValues>& in_inputValues, ::aidl::android::hardware::security::dice::BccHandover* _aidl_return) override {
    return _impl->derive(in_inputValues, _aidl_return);
  }
  ::ndk::ScopedAStatus demote(const std::vector<::aidl::android::hardware::security::dice::InputValues>& in_inputValues) override {
    return _impl->demote(in_inputValues);
  }
protected:
private:
  std::shared_ptr<IDiceDevice> _impl;
};

}  // namespace dice
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
