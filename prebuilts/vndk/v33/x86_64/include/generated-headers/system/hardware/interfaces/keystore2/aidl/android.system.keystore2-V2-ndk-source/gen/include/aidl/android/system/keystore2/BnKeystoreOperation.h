#pragma once

#include "aidl/android/system/keystore2/IKeystoreOperation.h"

#include <android/binder_ibinder.h>
#include <cassert>

#ifndef __BIONIC__
#ifndef __assert2
#define __assert2(a,b,c,d) ((void)0)
#endif
#endif

namespace aidl {
namespace android {
namespace system {
namespace keystore2 {
class BnKeystoreOperation : public ::ndk::BnCInterface<IKeystoreOperation> {
public:
  BnKeystoreOperation();
  virtual ~BnKeystoreOperation();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IKeystoreOperationDelegator : public BnKeystoreOperation {
public:
  explicit IKeystoreOperationDelegator(const std::shared_ptr<IKeystoreOperation> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IKeystoreOperation::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus updateAad(const std::vector<uint8_t>& in_aadInput) override {
    return _impl->updateAad(in_aadInput);
  }
  ::ndk::ScopedAStatus update(const std::vector<uint8_t>& in_input, std::optional<std::vector<uint8_t>>* _aidl_return) override {
    return _impl->update(in_input, _aidl_return);
  }
  ::ndk::ScopedAStatus finish(const std::optional<std::vector<uint8_t>>& in_input, const std::optional<std::vector<uint8_t>>& in_signature, std::optional<std::vector<uint8_t>>* _aidl_return) override {
    return _impl->finish(in_input, in_signature, _aidl_return);
  }
  ::ndk::ScopedAStatus abort() override {
    return _impl->abort();
  }
protected:
private:
  std::shared_ptr<IKeystoreOperation> _impl;
};

}  // namespace keystore2
}  // namespace system
}  // namespace android
}  // namespace aidl
