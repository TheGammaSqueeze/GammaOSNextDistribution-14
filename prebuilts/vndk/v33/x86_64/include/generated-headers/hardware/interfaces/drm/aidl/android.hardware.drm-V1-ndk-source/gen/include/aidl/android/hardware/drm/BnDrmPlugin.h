#pragma once

#include "aidl/android/hardware/drm/IDrmPlugin.h"

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
namespace drm {
class BnDrmPlugin : public ::ndk::BnCInterface<IDrmPlugin> {
public:
  BnDrmPlugin();
  virtual ~BnDrmPlugin();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class IDrmPluginDelegator : public BnDrmPlugin {
public:
  explicit IDrmPluginDelegator(const std::shared_ptr<IDrmPlugin> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != IDrmPlugin::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus closeSession(const std::vector<uint8_t>& in_sessionId) override {
    return _impl->closeSession(in_sessionId);
  }
  ::ndk::ScopedAStatus decrypt(const std::vector<uint8_t>& in_sessionId, const std::vector<uint8_t>& in_keyId, const std::vector<uint8_t>& in_input, const std::vector<uint8_t>& in_iv, std::vector<uint8_t>* _aidl_return) override {
    return _impl->decrypt(in_sessionId, in_keyId, in_input, in_iv, _aidl_return);
  }
  ::ndk::ScopedAStatus encrypt(const std::vector<uint8_t>& in_sessionId, const std::vector<uint8_t>& in_keyId, const std::vector<uint8_t>& in_input, const std::vector<uint8_t>& in_iv, std::vector<uint8_t>* _aidl_return) override {
    return _impl->encrypt(in_sessionId, in_keyId, in_input, in_iv, _aidl_return);
  }
  ::ndk::ScopedAStatus getHdcpLevels(::aidl::android::hardware::drm::HdcpLevels* _aidl_return) override {
    return _impl->getHdcpLevels(_aidl_return);
  }
  ::ndk::ScopedAStatus getKeyRequest(const std::vector<uint8_t>& in_scope, const std::vector<uint8_t>& in_initData, const std::string& in_mimeType, ::aidl::android::hardware::drm::KeyType in_keyType, const std::vector<::aidl::android::hardware::drm::KeyValue>& in_optionalParameters, ::aidl::android::hardware::drm::KeyRequest* _aidl_return) override {
    return _impl->getKeyRequest(in_scope, in_initData, in_mimeType, in_keyType, in_optionalParameters, _aidl_return);
  }
  ::ndk::ScopedAStatus getLogMessages(std::vector<::aidl::android::hardware::drm::LogMessage>* _aidl_return) override {
    return _impl->getLogMessages(_aidl_return);
  }
  ::ndk::ScopedAStatus getMetrics(std::vector<::aidl::android::hardware::drm::DrmMetricGroup>* _aidl_return) override {
    return _impl->getMetrics(_aidl_return);
  }
  ::ndk::ScopedAStatus getNumberOfSessions(::aidl::android::hardware::drm::NumberOfSessions* _aidl_return) override {
    return _impl->getNumberOfSessions(_aidl_return);
  }
  ::ndk::ScopedAStatus getOfflineLicenseKeySetIds(std::vector<::aidl::android::hardware::drm::KeySetId>* _aidl_return) override {
    return _impl->getOfflineLicenseKeySetIds(_aidl_return);
  }
  ::ndk::ScopedAStatus getOfflineLicenseState(const ::aidl::android::hardware::drm::KeySetId& in_keySetId, ::aidl::android::hardware::drm::OfflineLicenseState* _aidl_return) override {
    return _impl->getOfflineLicenseState(in_keySetId, _aidl_return);
  }
  ::ndk::ScopedAStatus getPropertyByteArray(const std::string& in_propertyName, std::vector<uint8_t>* _aidl_return) override {
    return _impl->getPropertyByteArray(in_propertyName, _aidl_return);
  }
  ::ndk::ScopedAStatus getPropertyString(const std::string& in_propertyName, std::string* _aidl_return) override {
    return _impl->getPropertyString(in_propertyName, _aidl_return);
  }
  ::ndk::ScopedAStatus getProvisionRequest(const std::string& in_certificateType, const std::string& in_certificateAuthority, ::aidl::android::hardware::drm::ProvisionRequest* _aidl_return) override {
    return _impl->getProvisionRequest(in_certificateType, in_certificateAuthority, _aidl_return);
  }
  ::ndk::ScopedAStatus getSecureStop(const ::aidl::android::hardware::drm::SecureStopId& in_secureStopId, ::aidl::android::hardware::drm::SecureStop* _aidl_return) override {
    return _impl->getSecureStop(in_secureStopId, _aidl_return);
  }
  ::ndk::ScopedAStatus getSecureStopIds(std::vector<::aidl::android::hardware::drm::SecureStopId>* _aidl_return) override {
    return _impl->getSecureStopIds(_aidl_return);
  }
  ::ndk::ScopedAStatus getSecureStops(std::vector<::aidl::android::hardware::drm::SecureStop>* _aidl_return) override {
    return _impl->getSecureStops(_aidl_return);
  }
  ::ndk::ScopedAStatus getSecurityLevel(const std::vector<uint8_t>& in_sessionId, ::aidl::android::hardware::drm::SecurityLevel* _aidl_return) override {
    return _impl->getSecurityLevel(in_sessionId, _aidl_return);
  }
  ::ndk::ScopedAStatus openSession(::aidl::android::hardware::drm::SecurityLevel in_securityLevel, std::vector<uint8_t>* _aidl_return) override {
    return _impl->openSession(in_securityLevel, _aidl_return);
  }
  ::ndk::ScopedAStatus provideKeyResponse(const std::vector<uint8_t>& in_scope, const std::vector<uint8_t>& in_response, ::aidl::android::hardware::drm::KeySetId* _aidl_return) override {
    return _impl->provideKeyResponse(in_scope, in_response, _aidl_return);
  }
  ::ndk::ScopedAStatus provideProvisionResponse(const std::vector<uint8_t>& in_response, ::aidl::android::hardware::drm::ProvideProvisionResponseResult* _aidl_return) override {
    return _impl->provideProvisionResponse(in_response, _aidl_return);
  }
  ::ndk::ScopedAStatus queryKeyStatus(const std::vector<uint8_t>& in_sessionId, std::vector<::aidl::android::hardware::drm::KeyValue>* _aidl_return) override {
    return _impl->queryKeyStatus(in_sessionId, _aidl_return);
  }
  ::ndk::ScopedAStatus releaseAllSecureStops() override {
    return _impl->releaseAllSecureStops();
  }
  ::ndk::ScopedAStatus releaseSecureStop(const ::aidl::android::hardware::drm::SecureStopId& in_secureStopId) override {
    return _impl->releaseSecureStop(in_secureStopId);
  }
  ::ndk::ScopedAStatus releaseSecureStops(const ::aidl::android::hardware::drm::OpaqueData& in_ssRelease) override {
    return _impl->releaseSecureStops(in_ssRelease);
  }
  ::ndk::ScopedAStatus removeAllSecureStops() override {
    return _impl->removeAllSecureStops();
  }
  ::ndk::ScopedAStatus removeKeys(const std::vector<uint8_t>& in_sessionId) override {
    return _impl->removeKeys(in_sessionId);
  }
  ::ndk::ScopedAStatus removeOfflineLicense(const ::aidl::android::hardware::drm::KeySetId& in_keySetId) override {
    return _impl->removeOfflineLicense(in_keySetId);
  }
  ::ndk::ScopedAStatus removeSecureStop(const ::aidl::android::hardware::drm::SecureStopId& in_secureStopId) override {
    return _impl->removeSecureStop(in_secureStopId);
  }
  ::ndk::ScopedAStatus requiresSecureDecoder(const std::string& in_mime, ::aidl::android::hardware::drm::SecurityLevel in_level, bool* _aidl_return) override {
    return _impl->requiresSecureDecoder(in_mime, in_level, _aidl_return);
  }
  ::ndk::ScopedAStatus restoreKeys(const std::vector<uint8_t>& in_sessionId, const ::aidl::android::hardware::drm::KeySetId& in_keySetId) override {
    return _impl->restoreKeys(in_sessionId, in_keySetId);
  }
  ::ndk::ScopedAStatus setCipherAlgorithm(const std::vector<uint8_t>& in_sessionId, const std::string& in_algorithm) override {
    return _impl->setCipherAlgorithm(in_sessionId, in_algorithm);
  }
  ::ndk::ScopedAStatus setListener(const std::shared_ptr<::aidl::android::hardware::drm::IDrmPluginListener>& in_listener) override {
    return _impl->setListener(in_listener);
  }
  ::ndk::ScopedAStatus setMacAlgorithm(const std::vector<uint8_t>& in_sessionId, const std::string& in_algorithm) override {
    return _impl->setMacAlgorithm(in_sessionId, in_algorithm);
  }
  ::ndk::ScopedAStatus setPlaybackId(const std::vector<uint8_t>& in_sessionId, const std::string& in_playbackId) override {
    return _impl->setPlaybackId(in_sessionId, in_playbackId);
  }
  ::ndk::ScopedAStatus setPropertyByteArray(const std::string& in_propertyName, const std::vector<uint8_t>& in_value) override {
    return _impl->setPropertyByteArray(in_propertyName, in_value);
  }
  ::ndk::ScopedAStatus setPropertyString(const std::string& in_propertyName, const std::string& in_value) override {
    return _impl->setPropertyString(in_propertyName, in_value);
  }
  ::ndk::ScopedAStatus sign(const std::vector<uint8_t>& in_sessionId, const std::vector<uint8_t>& in_keyId, const std::vector<uint8_t>& in_message, std::vector<uint8_t>* _aidl_return) override {
    return _impl->sign(in_sessionId, in_keyId, in_message, _aidl_return);
  }
  ::ndk::ScopedAStatus signRSA(const std::vector<uint8_t>& in_sessionId, const std::string& in_algorithm, const std::vector<uint8_t>& in_message, const std::vector<uint8_t>& in_wrappedkey, std::vector<uint8_t>* _aidl_return) override {
    return _impl->signRSA(in_sessionId, in_algorithm, in_message, in_wrappedkey, _aidl_return);
  }
  ::ndk::ScopedAStatus verify(const std::vector<uint8_t>& in_sessionId, const std::vector<uint8_t>& in_keyId, const std::vector<uint8_t>& in_message, const std::vector<uint8_t>& in_signature, bool* _aidl_return) override {
    return _impl->verify(in_sessionId, in_keyId, in_message, in_signature, _aidl_return);
  }
protected:
private:
  std::shared_ptr<IDrmPlugin> _impl;
};

}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
