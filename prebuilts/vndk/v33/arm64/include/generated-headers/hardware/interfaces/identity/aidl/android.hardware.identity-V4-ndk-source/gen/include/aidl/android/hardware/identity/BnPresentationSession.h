#pragma once

#include "aidl/android/hardware/identity/IPresentationSession.h"

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
namespace identity {
class BnPresentationSession : public ::ndk::BnCInterface<IPresentationSession> {
public:
  BnPresentationSession();
  virtual ~BnPresentationSession();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IPresentationSessionDelegator : public BnPresentationSession {
public:
  explicit IPresentationSessionDelegator(const std::shared_ptr<IPresentationSession> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IPresentationSession::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus getEphemeralKeyPair(std::vector<uint8_t>* _aidl_return) override {
    return _impl->getEphemeralKeyPair(_aidl_return);
  }
  ::ndk::ScopedAStatus getAuthChallenge(int64_t* _aidl_return) override {
    return _impl->getAuthChallenge(_aidl_return);
  }
  ::ndk::ScopedAStatus setReaderEphemeralPublicKey(const std::vector<uint8_t>& in_publicKey) override {
    return _impl->setReaderEphemeralPublicKey(in_publicKey);
  }
  ::ndk::ScopedAStatus setSessionTranscript(const std::vector<uint8_t>& in_sessionTranscript) override {
    return _impl->setSessionTranscript(in_sessionTranscript);
  }
  ::ndk::ScopedAStatus getCredential(const std::vector<uint8_t>& in_credentialData, std::shared_ptr<::aidl::android::hardware::identity::IIdentityCredential>* _aidl_return) override {
    return _impl->getCredential(in_credentialData, _aidl_return);
  }
protected:
private:
  std::shared_ptr<IPresentationSession> _impl;
};

}  // namespace identity
}  // namespace hardware
}  // namespace android
}  // namespace aidl
