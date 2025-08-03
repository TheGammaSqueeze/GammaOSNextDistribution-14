#pragma once

#include "aidl/android/hardware/security/keymint/IRemotelyProvisionedComponent.h"

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
class BnRemotelyProvisionedComponent : public ::ndk::BnCInterface<IRemotelyProvisionedComponent> {
public:
  BnRemotelyProvisionedComponent();
  virtual ~BnRemotelyProvisionedComponent();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IRemotelyProvisionedComponentDelegator : public BnRemotelyProvisionedComponent {
public:
  explicit IRemotelyProvisionedComponentDelegator(const std::shared_ptr<IRemotelyProvisionedComponent> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IRemotelyProvisionedComponent::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus getHardwareInfo(::aidl::android::hardware::security::keymint::RpcHardwareInfo* _aidl_return) override {
    return _impl->getHardwareInfo(_aidl_return);
  }
  ::ndk::ScopedAStatus generateEcdsaP256KeyPair(bool in_testMode, ::aidl::android::hardware::security::keymint::MacedPublicKey* out_macedPublicKey, std::vector<uint8_t>* _aidl_return) override {
    return _impl->generateEcdsaP256KeyPair(in_testMode, out_macedPublicKey, _aidl_return);
  }
  ::ndk::ScopedAStatus generateCertificateRequest(bool in_testMode, const std::vector<::aidl::android::hardware::security::keymint::MacedPublicKey>& in_keysToSign, const std::vector<uint8_t>& in_endpointEncryptionCertChain, const std::vector<uint8_t>& in_challenge, ::aidl::android::hardware::security::keymint::DeviceInfo* out_deviceInfo, ::aidl::android::hardware::security::keymint::ProtectedData* out_protectedData, std::vector<uint8_t>* _aidl_return) override {
    return _impl->generateCertificateRequest(in_testMode, in_keysToSign, in_endpointEncryptionCertChain, in_challenge, out_deviceInfo, out_protectedData, _aidl_return);
  }
protected:
private:
  std::shared_ptr<IRemotelyProvisionedComponent> _impl;
};

}  // namespace keymint
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
