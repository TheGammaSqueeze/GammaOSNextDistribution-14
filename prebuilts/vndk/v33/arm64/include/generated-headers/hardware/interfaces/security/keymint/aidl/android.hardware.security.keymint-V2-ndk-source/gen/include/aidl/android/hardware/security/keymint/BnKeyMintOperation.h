#pragma once

#include "aidl/android/hardware/security/keymint/IKeyMintOperation.h"

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
namespace keymint {
class BnKeyMintOperation : public ::ndk::BnCInterface<IKeyMintOperation> {
public:
  BnKeyMintOperation();
  virtual ~BnKeyMintOperation();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IKeyMintOperationDelegator : public BnKeyMintOperation {
public:
  explicit IKeyMintOperationDelegator(const std::shared_ptr<IKeyMintOperation> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IKeyMintOperation::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus updateAad(const std::vector<uint8_t>& in_input, const std::optional<::aidl::android::hardware::security::keymint::HardwareAuthToken>& in_authToken, const std::optional<::aidl::android::hardware::security::secureclock::TimeStampToken>& in_timeStampToken) override {
    return _impl->updateAad(in_input, in_authToken, in_timeStampToken);
  }
  ::ndk::ScopedAStatus update(const std::vector<uint8_t>& in_input, const std::optional<::aidl::android::hardware::security::keymint::HardwareAuthToken>& in_authToken, const std::optional<::aidl::android::hardware::security::secureclock::TimeStampToken>& in_timeStampToken, std::vector<uint8_t>* _aidl_return) override {
    return _impl->update(in_input, in_authToken, in_timeStampToken, _aidl_return);
  }
  ::ndk::ScopedAStatus finish(const std::optional<std::vector<uint8_t>>& in_input, const std::optional<std::vector<uint8_t>>& in_signature, const std::optional<::aidl::android::hardware::security::keymint::HardwareAuthToken>& in_authToken, const std::optional<::aidl::android::hardware::security::secureclock::TimeStampToken>& in_timestampToken, const std::optional<std::vector<uint8_t>>& in_confirmationToken, std::vector<uint8_t>* _aidl_return) override {
    return _impl->finish(in_input, in_signature, in_authToken, in_timestampToken, in_confirmationToken, _aidl_return);
  }
  ::ndk::ScopedAStatus abort() override {
    return _impl->abort();
  }
protected:
private:
  std::shared_ptr<IKeyMintOperation> _impl;
};

}  // namespace keymint
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
