#pragma once

#include "aidl/android/hardware/drm/IDrmPlugin.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace drm {
class BpDrmPlugin : public ::ndk::BpCInterface<IDrmPlugin> {
public:
  explicit BpDrmPlugin(const ::ndk::SpAIBinder& binder);
  virtual ~BpDrmPlugin();

  ::ndk::ScopedAStatus closeSession(const std::vector<uint8_t>& in_sessionId) override;
  ::ndk::ScopedAStatus decrypt(const std::vector<uint8_t>& in_sessionId, const std::vector<uint8_t>& in_keyId, const std::vector<uint8_t>& in_input, const std::vector<uint8_t>& in_iv, std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus encrypt(const std::vector<uint8_t>& in_sessionId, const std::vector<uint8_t>& in_keyId, const std::vector<uint8_t>& in_input, const std::vector<uint8_t>& in_iv, std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getHdcpLevels(::aidl::android::hardware::drm::HdcpLevels* _aidl_return) override;
  ::ndk::ScopedAStatus getKeyRequest(const std::vector<uint8_t>& in_scope, const std::vector<uint8_t>& in_initData, const std::string& in_mimeType, ::aidl::android::hardware::drm::KeyType in_keyType, const std::vector<::aidl::android::hardware::drm::KeyValue>& in_optionalParameters, ::aidl::android::hardware::drm::KeyRequest* _aidl_return) override;
  ::ndk::ScopedAStatus getLogMessages(std::vector<::aidl::android::hardware::drm::LogMessage>* _aidl_return) override;
  ::ndk::ScopedAStatus getMetrics(std::vector<::aidl::android::hardware::drm::DrmMetricGroup>* _aidl_return) override;
  ::ndk::ScopedAStatus getNumberOfSessions(::aidl::android::hardware::drm::NumberOfSessions* _aidl_return) override;
  ::ndk::ScopedAStatus getOfflineLicenseKeySetIds(std::vector<::aidl::android::hardware::drm::KeySetId>* _aidl_return) override;
  ::ndk::ScopedAStatus getOfflineLicenseState(const ::aidl::android::hardware::drm::KeySetId& in_keySetId, ::aidl::android::hardware::drm::OfflineLicenseState* _aidl_return) override;
  ::ndk::ScopedAStatus getPropertyByteArray(const std::string& in_propertyName, std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getPropertyString(const std::string& in_propertyName, std::string* _aidl_return) override;
  ::ndk::ScopedAStatus getProvisionRequest(const std::string& in_certificateType, const std::string& in_certificateAuthority, ::aidl::android::hardware::drm::ProvisionRequest* _aidl_return) override;
  ::ndk::ScopedAStatus getSecureStop(const ::aidl::android::hardware::drm::SecureStopId& in_secureStopId, ::aidl::android::hardware::drm::SecureStop* _aidl_return) override;
  ::ndk::ScopedAStatus getSecureStopIds(std::vector<::aidl::android::hardware::drm::SecureStopId>* _aidl_return) override;
  ::ndk::ScopedAStatus getSecureStops(std::vector<::aidl::android::hardware::drm::SecureStop>* _aidl_return) override;
  ::ndk::ScopedAStatus getSecurityLevel(const std::vector<uint8_t>& in_sessionId, ::aidl::android::hardware::drm::SecurityLevel* _aidl_return) override;
  ::ndk::ScopedAStatus openSession(::aidl::android::hardware::drm::SecurityLevel in_securityLevel, std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus provideKeyResponse(const std::vector<uint8_t>& in_scope, const std::vector<uint8_t>& in_response, ::aidl::android::hardware::drm::KeySetId* _aidl_return) override;
  ::ndk::ScopedAStatus provideProvisionResponse(const std::vector<uint8_t>& in_response, ::aidl::android::hardware::drm::ProvideProvisionResponseResult* _aidl_return) override;
  ::ndk::ScopedAStatus queryKeyStatus(const std::vector<uint8_t>& in_sessionId, std::vector<::aidl::android::hardware::drm::KeyValue>* _aidl_return) override;
  ::ndk::ScopedAStatus releaseAllSecureStops() override;
  ::ndk::ScopedAStatus releaseSecureStop(const ::aidl::android::hardware::drm::SecureStopId& in_secureStopId) override;
  ::ndk::ScopedAStatus releaseSecureStops(const ::aidl::android::hardware::drm::OpaqueData& in_ssRelease) override;
  ::ndk::ScopedAStatus removeAllSecureStops() override;
  ::ndk::ScopedAStatus removeKeys(const std::vector<uint8_t>& in_sessionId) override;
  ::ndk::ScopedAStatus removeOfflineLicense(const ::aidl::android::hardware::drm::KeySetId& in_keySetId) override;
  ::ndk::ScopedAStatus removeSecureStop(const ::aidl::android::hardware::drm::SecureStopId& in_secureStopId) override;
  ::ndk::ScopedAStatus requiresSecureDecoder(const std::string& in_mime, ::aidl::android::hardware::drm::SecurityLevel in_level, bool* _aidl_return) override;
  ::ndk::ScopedAStatus restoreKeys(const std::vector<uint8_t>& in_sessionId, const ::aidl::android::hardware::drm::KeySetId& in_keySetId) override;
  ::ndk::ScopedAStatus setCipherAlgorithm(const std::vector<uint8_t>& in_sessionId, const std::string& in_algorithm) override;
  ::ndk::ScopedAStatus setListener(const std::shared_ptr<::aidl::android::hardware::drm::IDrmPluginListener>& in_listener) override;
  ::ndk::ScopedAStatus setMacAlgorithm(const std::vector<uint8_t>& in_sessionId, const std::string& in_algorithm) override;
  ::ndk::ScopedAStatus setPlaybackId(const std::vector<uint8_t>& in_sessionId, const std::string& in_playbackId) override;
  ::ndk::ScopedAStatus setPropertyByteArray(const std::string& in_propertyName, const std::vector<uint8_t>& in_value) override;
  ::ndk::ScopedAStatus setPropertyString(const std::string& in_propertyName, const std::string& in_value) override;
  ::ndk::ScopedAStatus sign(const std::vector<uint8_t>& in_sessionId, const std::vector<uint8_t>& in_keyId, const std::vector<uint8_t>& in_message, std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus signRSA(const std::vector<uint8_t>& in_sessionId, const std::string& in_algorithm, const std::vector<uint8_t>& in_message, const std::vector<uint8_t>& in_wrappedkey, std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus verify(const std::vector<uint8_t>& in_sessionId, const std::vector<uint8_t>& in_keyId, const std::vector<uint8_t>& in_message, const std::vector<uint8_t>& in_signature, bool* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
