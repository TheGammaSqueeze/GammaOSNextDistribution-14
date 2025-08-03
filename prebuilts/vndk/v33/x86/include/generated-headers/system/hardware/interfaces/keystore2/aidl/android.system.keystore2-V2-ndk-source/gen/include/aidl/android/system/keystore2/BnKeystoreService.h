#pragma once

#include "aidl/android/system/keystore2/IKeystoreService.h"

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
class BnKeystoreService : public ::ndk::BnCInterface<IKeystoreService> {
public:
  BnKeystoreService();
  virtual ~BnKeystoreService();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IKeystoreServiceDelegator : public BnKeystoreService {
public:
  explicit IKeystoreServiceDelegator(const std::shared_ptr<IKeystoreService> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IKeystoreService::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus getSecurityLevel(::aidl::android::hardware::security::keymint::SecurityLevel in_securityLevel, std::shared_ptr<::aidl::android::system::keystore2::IKeystoreSecurityLevel>* _aidl_return) override {
    return _impl->getSecurityLevel(in_securityLevel, _aidl_return);
  }
  ::ndk::ScopedAStatus getKeyEntry(const ::aidl::android::system::keystore2::KeyDescriptor& in_key, ::aidl::android::system::keystore2::KeyEntryResponse* _aidl_return) override {
    return _impl->getKeyEntry(in_key, _aidl_return);
  }
  ::ndk::ScopedAStatus updateSubcomponent(const ::aidl::android::system::keystore2::KeyDescriptor& in_key, const std::optional<std::vector<uint8_t>>& in_publicCert, const std::optional<std::vector<uint8_t>>& in_certificateChain) override {
    return _impl->updateSubcomponent(in_key, in_publicCert, in_certificateChain);
  }
  ::ndk::ScopedAStatus listEntries(::aidl::android::system::keystore2::Domain in_domain, int64_t in_nspace, std::vector<::aidl::android::system::keystore2::KeyDescriptor>* _aidl_return) override {
    return _impl->listEntries(in_domain, in_nspace, _aidl_return);
  }
  ::ndk::ScopedAStatus deleteKey(const ::aidl::android::system::keystore2::KeyDescriptor& in_key) override {
    return _impl->deleteKey(in_key);
  }
  ::ndk::ScopedAStatus grant(const ::aidl::android::system::keystore2::KeyDescriptor& in_key, int32_t in_granteeUid, int32_t in_accessVector, ::aidl::android::system::keystore2::KeyDescriptor* _aidl_return) override {
    return _impl->grant(in_key, in_granteeUid, in_accessVector, _aidl_return);
  }
  ::ndk::ScopedAStatus ungrant(const ::aidl::android::system::keystore2::KeyDescriptor& in_key, int32_t in_granteeUid) override {
    return _impl->ungrant(in_key, in_granteeUid);
  }
protected:
private:
  std::shared_ptr<IKeystoreService> _impl;
};

}  // namespace keystore2
}  // namespace system
}  // namespace android
}  // namespace aidl
