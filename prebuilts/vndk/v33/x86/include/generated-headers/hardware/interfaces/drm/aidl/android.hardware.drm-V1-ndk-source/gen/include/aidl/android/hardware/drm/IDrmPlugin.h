#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/drm/DrmMetricGroup.h>
#include <aidl/android/hardware/drm/HdcpLevels.h>
#include <aidl/android/hardware/drm/IDrmPluginListener.h>
#include <aidl/android/hardware/drm/KeyRequest.h>
#include <aidl/android/hardware/drm/KeySetId.h>
#include <aidl/android/hardware/drm/KeyType.h>
#include <aidl/android/hardware/drm/KeyValue.h>
#include <aidl/android/hardware/drm/LogMessage.h>
#include <aidl/android/hardware/drm/NumberOfSessions.h>
#include <aidl/android/hardware/drm/OfflineLicenseState.h>
#include <aidl/android/hardware/drm/OpaqueData.h>
#include <aidl/android/hardware/drm/ProvideProvisionResponseResult.h>
#include <aidl/android/hardware/drm/ProvisionRequest.h>
#include <aidl/android/hardware/drm/SecureStop.h>
#include <aidl/android/hardware/drm/SecureStopId.h>
#include <aidl/android/hardware/drm/SecurityLevel.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace drm {
class IDrmPlugin : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IDrmPlugin();
  virtual ~IDrmPlugin();

  static const int32_t version = 1;
  static inline const std::string hash = "7b4b0a0f36a7a6bb22d2016375e4a9d4a033592f";
  static constexpr uint32_t TRANSACTION_closeSession = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_decrypt = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_encrypt = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_getHdcpLevels = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_getKeyRequest = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_getLogMessages = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_getMetrics = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_getNumberOfSessions = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_getOfflineLicenseKeySetIds = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_getOfflineLicenseState = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_getPropertyByteArray = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_getPropertyString = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_getProvisionRequest = FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_getSecureStop = FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_getSecureStopIds = FIRST_CALL_TRANSACTION + 14;
  static constexpr uint32_t TRANSACTION_getSecureStops = FIRST_CALL_TRANSACTION + 15;
  static constexpr uint32_t TRANSACTION_getSecurityLevel = FIRST_CALL_TRANSACTION + 16;
  static constexpr uint32_t TRANSACTION_openSession = FIRST_CALL_TRANSACTION + 17;
  static constexpr uint32_t TRANSACTION_provideKeyResponse = FIRST_CALL_TRANSACTION + 18;
  static constexpr uint32_t TRANSACTION_provideProvisionResponse = FIRST_CALL_TRANSACTION + 19;
  static constexpr uint32_t TRANSACTION_queryKeyStatus = FIRST_CALL_TRANSACTION + 20;
  static constexpr uint32_t TRANSACTION_releaseAllSecureStops = FIRST_CALL_TRANSACTION + 21;
  static constexpr uint32_t TRANSACTION_releaseSecureStop = FIRST_CALL_TRANSACTION + 22;
  static constexpr uint32_t TRANSACTION_releaseSecureStops = FIRST_CALL_TRANSACTION + 23;
  static constexpr uint32_t TRANSACTION_removeAllSecureStops = FIRST_CALL_TRANSACTION + 24;
  static constexpr uint32_t TRANSACTION_removeKeys = FIRST_CALL_TRANSACTION + 25;
  static constexpr uint32_t TRANSACTION_removeOfflineLicense = FIRST_CALL_TRANSACTION + 26;
  static constexpr uint32_t TRANSACTION_removeSecureStop = FIRST_CALL_TRANSACTION + 27;
  static constexpr uint32_t TRANSACTION_requiresSecureDecoder = FIRST_CALL_TRANSACTION + 28;
  static constexpr uint32_t TRANSACTION_restoreKeys = FIRST_CALL_TRANSACTION + 29;
  static constexpr uint32_t TRANSACTION_setCipherAlgorithm = FIRST_CALL_TRANSACTION + 30;
  static constexpr uint32_t TRANSACTION_setListener = FIRST_CALL_TRANSACTION + 31;
  static constexpr uint32_t TRANSACTION_setMacAlgorithm = FIRST_CALL_TRANSACTION + 32;
  static constexpr uint32_t TRANSACTION_setPlaybackId = FIRST_CALL_TRANSACTION + 33;
  static constexpr uint32_t TRANSACTION_setPropertyByteArray = FIRST_CALL_TRANSACTION + 34;
  static constexpr uint32_t TRANSACTION_setPropertyString = FIRST_CALL_TRANSACTION + 35;
  static constexpr uint32_t TRANSACTION_sign = FIRST_CALL_TRANSACTION + 36;
  static constexpr uint32_t TRANSACTION_signRSA = FIRST_CALL_TRANSACTION + 37;
  static constexpr uint32_t TRANSACTION_verify = FIRST_CALL_TRANSACTION + 38;

  static std::shared_ptr<IDrmPlugin> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IDrmPlugin>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IDrmPlugin>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IDrmPlugin>& impl);
  static const std::shared_ptr<IDrmPlugin>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus closeSession(const std::vector<uint8_t>& in_sessionId) = 0;
  virtual ::ndk::ScopedAStatus decrypt(const std::vector<uint8_t>& in_sessionId, const std::vector<uint8_t>& in_keyId, const std::vector<uint8_t>& in_input, const std::vector<uint8_t>& in_iv, std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus encrypt(const std::vector<uint8_t>& in_sessionId, const std::vector<uint8_t>& in_keyId, const std::vector<uint8_t>& in_input, const std::vector<uint8_t>& in_iv, std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getHdcpLevels(::aidl::android::hardware::drm::HdcpLevels* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getKeyRequest(const std::vector<uint8_t>& in_scope, const std::vector<uint8_t>& in_initData, const std::string& in_mimeType, ::aidl::android::hardware::drm::KeyType in_keyType, const std::vector<::aidl::android::hardware::drm::KeyValue>& in_optionalParameters, ::aidl::android::hardware::drm::KeyRequest* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getLogMessages(std::vector<::aidl::android::hardware::drm::LogMessage>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getMetrics(std::vector<::aidl::android::hardware::drm::DrmMetricGroup>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getNumberOfSessions(::aidl::android::hardware::drm::NumberOfSessions* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getOfflineLicenseKeySetIds(std::vector<::aidl::android::hardware::drm::KeySetId>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getOfflineLicenseState(const ::aidl::android::hardware::drm::KeySetId& in_keySetId, ::aidl::android::hardware::drm::OfflineLicenseState* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getPropertyByteArray(const std::string& in_propertyName, std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getPropertyString(const std::string& in_propertyName, std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getProvisionRequest(const std::string& in_certificateType, const std::string& in_certificateAuthority, ::aidl::android::hardware::drm::ProvisionRequest* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getSecureStop(const ::aidl::android::hardware::drm::SecureStopId& in_secureStopId, ::aidl::android::hardware::drm::SecureStop* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getSecureStopIds(std::vector<::aidl::android::hardware::drm::SecureStopId>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getSecureStops(std::vector<::aidl::android::hardware::drm::SecureStop>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getSecurityLevel(const std::vector<uint8_t>& in_sessionId, ::aidl::android::hardware::drm::SecurityLevel* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus openSession(::aidl::android::hardware::drm::SecurityLevel in_securityLevel, std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus provideKeyResponse(const std::vector<uint8_t>& in_scope, const std::vector<uint8_t>& in_response, ::aidl::android::hardware::drm::KeySetId* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus provideProvisionResponse(const std::vector<uint8_t>& in_response, ::aidl::android::hardware::drm::ProvideProvisionResponseResult* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus queryKeyStatus(const std::vector<uint8_t>& in_sessionId, std::vector<::aidl::android::hardware::drm::KeyValue>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus releaseAllSecureStops() = 0;
  virtual ::ndk::ScopedAStatus releaseSecureStop(const ::aidl::android::hardware::drm::SecureStopId& in_secureStopId) = 0;
  virtual ::ndk::ScopedAStatus releaseSecureStops(const ::aidl::android::hardware::drm::OpaqueData& in_ssRelease) = 0;
  virtual ::ndk::ScopedAStatus removeAllSecureStops() = 0;
  virtual ::ndk::ScopedAStatus removeKeys(const std::vector<uint8_t>& in_sessionId) = 0;
  virtual ::ndk::ScopedAStatus removeOfflineLicense(const ::aidl::android::hardware::drm::KeySetId& in_keySetId) = 0;
  virtual ::ndk::ScopedAStatus removeSecureStop(const ::aidl::android::hardware::drm::SecureStopId& in_secureStopId) = 0;
  virtual ::ndk::ScopedAStatus requiresSecureDecoder(const std::string& in_mime, ::aidl::android::hardware::drm::SecurityLevel in_level, bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus restoreKeys(const std::vector<uint8_t>& in_sessionId, const ::aidl::android::hardware::drm::KeySetId& in_keySetId) = 0;
  virtual ::ndk::ScopedAStatus setCipherAlgorithm(const std::vector<uint8_t>& in_sessionId, const std::string& in_algorithm) = 0;
  virtual ::ndk::ScopedAStatus setListener(const std::shared_ptr<::aidl::android::hardware::drm::IDrmPluginListener>& in_listener) = 0;
  virtual ::ndk::ScopedAStatus setMacAlgorithm(const std::vector<uint8_t>& in_sessionId, const std::string& in_algorithm) = 0;
  virtual ::ndk::ScopedAStatus setPlaybackId(const std::vector<uint8_t>& in_sessionId, const std::string& in_playbackId) = 0;
  virtual ::ndk::ScopedAStatus setPropertyByteArray(const std::string& in_propertyName, const std::vector<uint8_t>& in_value) = 0;
  virtual ::ndk::ScopedAStatus setPropertyString(const std::string& in_propertyName, const std::string& in_value) = 0;
  virtual ::ndk::ScopedAStatus sign(const std::vector<uint8_t>& in_sessionId, const std::vector<uint8_t>& in_keyId, const std::vector<uint8_t>& in_message, std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus signRSA(const std::vector<uint8_t>& in_sessionId, const std::string& in_algorithm, const std::vector<uint8_t>& in_message, const std::vector<uint8_t>& in_wrappedkey, std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus verify(const std::vector<uint8_t>& in_sessionId, const std::vector<uint8_t>& in_keyId, const std::vector<uint8_t>& in_message, const std::vector<uint8_t>& in_signature, bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IDrmPlugin> default_impl;
};
class IDrmPluginDefault : public IDrmPlugin {
public:
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
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
