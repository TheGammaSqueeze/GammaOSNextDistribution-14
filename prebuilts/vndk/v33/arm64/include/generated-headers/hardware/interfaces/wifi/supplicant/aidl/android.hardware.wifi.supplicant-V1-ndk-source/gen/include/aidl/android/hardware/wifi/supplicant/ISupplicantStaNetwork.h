#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/wifi/supplicant/AuthAlgMask.h>
#include <aidl/android/hardware/wifi/supplicant/DppConnectionKeys.h>
#include <aidl/android/hardware/wifi/supplicant/EapMethod.h>
#include <aidl/android/hardware/wifi/supplicant/EapPhase2Method.h>
#include <aidl/android/hardware/wifi/supplicant/GroupCipherMask.h>
#include <aidl/android/hardware/wifi/supplicant/GroupMgmtCipherMask.h>
#include <aidl/android/hardware/wifi/supplicant/ISupplicantStaNetworkCallback.h>
#include <aidl/android/hardware/wifi/supplicant/IfaceType.h>
#include <aidl/android/hardware/wifi/supplicant/KeyMgmtMask.h>
#include <aidl/android/hardware/wifi/supplicant/NetworkResponseEapSimGsmAuthParams.h>
#include <aidl/android/hardware/wifi/supplicant/NetworkResponseEapSimUmtsAuthParams.h>
#include <aidl/android/hardware/wifi/supplicant/OcspType.h>
#include <aidl/android/hardware/wifi/supplicant/PairwiseCipherMask.h>
#include <aidl/android/hardware/wifi/supplicant/ProtoMask.h>
#include <aidl/android/hardware/wifi/supplicant/SaeH2eMode.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class ISupplicantStaNetwork : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ISupplicantStaNetwork();
  virtual ~ISupplicantStaNetwork();

  enum : int32_t { SSID_MAX_LEN_IN_BYTES = 32 };
  enum : int32_t { PSK_PASSPHRASE_MIN_LEN_IN_BYTES = 8 };
  enum : int32_t { PSK_PASSPHRASE_MAX_LEN_IN_BYTES = 63 };
  enum : int32_t { WEP_KEYS_MAX_NUM = 4 };
  enum : int32_t { WEP40_KEY_LEN_IN_BYTES = 5 };
  enum : int32_t { WEP104_KEY_LEN_IN_BYTES = 13 };
  static const int32_t version = 1;
  static inline const std::string hash = "5b8bcab6b43177dffdec5873e84205b04757cc9d";
  static constexpr uint32_t TRANSACTION_disable = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_enable = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_enableSaePkOnlyMode = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_enableSuiteBEapOpenSslCiphers = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_enableTlsSuiteBEapPhase1Param = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_getAuthAlg = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_getBssid = FIRST_CALL_TRANSACTION + 6;
  static constexpr uint32_t TRANSACTION_getEapAltSubjectMatch = FIRST_CALL_TRANSACTION + 7;
  static constexpr uint32_t TRANSACTION_getEapAnonymousIdentity = FIRST_CALL_TRANSACTION + 8;
  static constexpr uint32_t TRANSACTION_getEapCACert = FIRST_CALL_TRANSACTION + 9;
  static constexpr uint32_t TRANSACTION_getEapCAPath = FIRST_CALL_TRANSACTION + 10;
  static constexpr uint32_t TRANSACTION_getEapClientCert = FIRST_CALL_TRANSACTION + 11;
  static constexpr uint32_t TRANSACTION_getEapDomainSuffixMatch = FIRST_CALL_TRANSACTION + 12;
  static constexpr uint32_t TRANSACTION_getEapEngine = FIRST_CALL_TRANSACTION + 13;
  static constexpr uint32_t TRANSACTION_getEapEngineId = FIRST_CALL_TRANSACTION + 14;
  static constexpr uint32_t TRANSACTION_getEapIdentity = FIRST_CALL_TRANSACTION + 15;
  static constexpr uint32_t TRANSACTION_getEapMethod = FIRST_CALL_TRANSACTION + 16;
  static constexpr uint32_t TRANSACTION_getEapPassword = FIRST_CALL_TRANSACTION + 17;
  static constexpr uint32_t TRANSACTION_getEapPhase2Method = FIRST_CALL_TRANSACTION + 18;
  static constexpr uint32_t TRANSACTION_getEapPrivateKeyId = FIRST_CALL_TRANSACTION + 19;
  static constexpr uint32_t TRANSACTION_getEapSubjectMatch = FIRST_CALL_TRANSACTION + 20;
  static constexpr uint32_t TRANSACTION_getEdmg = FIRST_CALL_TRANSACTION + 21;
  static constexpr uint32_t TRANSACTION_getGroupCipher = FIRST_CALL_TRANSACTION + 22;
  static constexpr uint32_t TRANSACTION_getGroupMgmtCipher = FIRST_CALL_TRANSACTION + 23;
  static constexpr uint32_t TRANSACTION_getId = FIRST_CALL_TRANSACTION + 24;
  static constexpr uint32_t TRANSACTION_getIdStr = FIRST_CALL_TRANSACTION + 25;
  static constexpr uint32_t TRANSACTION_getInterfaceName = FIRST_CALL_TRANSACTION + 26;
  static constexpr uint32_t TRANSACTION_getKeyMgmt = FIRST_CALL_TRANSACTION + 27;
  static constexpr uint32_t TRANSACTION_getOcsp = FIRST_CALL_TRANSACTION + 28;
  static constexpr uint32_t TRANSACTION_getPairwiseCipher = FIRST_CALL_TRANSACTION + 29;
  static constexpr uint32_t TRANSACTION_getProto = FIRST_CALL_TRANSACTION + 30;
  static constexpr uint32_t TRANSACTION_getPsk = FIRST_CALL_TRANSACTION + 31;
  static constexpr uint32_t TRANSACTION_getPskPassphrase = FIRST_CALL_TRANSACTION + 32;
  static constexpr uint32_t TRANSACTION_getRequirePmf = FIRST_CALL_TRANSACTION + 33;
  static constexpr uint32_t TRANSACTION_getSaePassword = FIRST_CALL_TRANSACTION + 34;
  static constexpr uint32_t TRANSACTION_getSaePasswordId = FIRST_CALL_TRANSACTION + 35;
  static constexpr uint32_t TRANSACTION_getScanSsid = FIRST_CALL_TRANSACTION + 36;
  static constexpr uint32_t TRANSACTION_getSsid = FIRST_CALL_TRANSACTION + 37;
  static constexpr uint32_t TRANSACTION_getType = FIRST_CALL_TRANSACTION + 38;
  static constexpr uint32_t TRANSACTION_getWapiCertSuite = FIRST_CALL_TRANSACTION + 39;
  static constexpr uint32_t TRANSACTION_getWepKey = FIRST_CALL_TRANSACTION + 40;
  static constexpr uint32_t TRANSACTION_getWepTxKeyIdx = FIRST_CALL_TRANSACTION + 41;
  static constexpr uint32_t TRANSACTION_getWpsNfcConfigurationToken = FIRST_CALL_TRANSACTION + 42;
  static constexpr uint32_t TRANSACTION_registerCallback = FIRST_CALL_TRANSACTION + 43;
  static constexpr uint32_t TRANSACTION_select = FIRST_CALL_TRANSACTION + 44;
  static constexpr uint32_t TRANSACTION_sendNetworkEapIdentityResponse = FIRST_CALL_TRANSACTION + 45;
  static constexpr uint32_t TRANSACTION_sendNetworkEapSimGsmAuthFailure = FIRST_CALL_TRANSACTION + 46;
  static constexpr uint32_t TRANSACTION_sendNetworkEapSimGsmAuthResponse = FIRST_CALL_TRANSACTION + 47;
  static constexpr uint32_t TRANSACTION_sendNetworkEapSimUmtsAuthFailure = FIRST_CALL_TRANSACTION + 48;
  static constexpr uint32_t TRANSACTION_sendNetworkEapSimUmtsAuthResponse = FIRST_CALL_TRANSACTION + 49;
  static constexpr uint32_t TRANSACTION_sendNetworkEapSimUmtsAutsResponse = FIRST_CALL_TRANSACTION + 50;
  static constexpr uint32_t TRANSACTION_setAuthAlg = FIRST_CALL_TRANSACTION + 51;
  static constexpr uint32_t TRANSACTION_setBssid = FIRST_CALL_TRANSACTION + 52;
  static constexpr uint32_t TRANSACTION_setDppKeys = FIRST_CALL_TRANSACTION + 53;
  static constexpr uint32_t TRANSACTION_setEapAltSubjectMatch = FIRST_CALL_TRANSACTION + 54;
  static constexpr uint32_t TRANSACTION_setEapAnonymousIdentity = FIRST_CALL_TRANSACTION + 55;
  static constexpr uint32_t TRANSACTION_setEapCACert = FIRST_CALL_TRANSACTION + 56;
  static constexpr uint32_t TRANSACTION_setEapCAPath = FIRST_CALL_TRANSACTION + 57;
  static constexpr uint32_t TRANSACTION_setEapClientCert = FIRST_CALL_TRANSACTION + 58;
  static constexpr uint32_t TRANSACTION_setEapDomainSuffixMatch = FIRST_CALL_TRANSACTION + 59;
  static constexpr uint32_t TRANSACTION_setEapEncryptedImsiIdentity = FIRST_CALL_TRANSACTION + 60;
  static constexpr uint32_t TRANSACTION_setEapEngine = FIRST_CALL_TRANSACTION + 61;
  static constexpr uint32_t TRANSACTION_setEapEngineID = FIRST_CALL_TRANSACTION + 62;
  static constexpr uint32_t TRANSACTION_setEapErp = FIRST_CALL_TRANSACTION + 63;
  static constexpr uint32_t TRANSACTION_setEapIdentity = FIRST_CALL_TRANSACTION + 64;
  static constexpr uint32_t TRANSACTION_setEapMethod = FIRST_CALL_TRANSACTION + 65;
  static constexpr uint32_t TRANSACTION_setEapPassword = FIRST_CALL_TRANSACTION + 66;
  static constexpr uint32_t TRANSACTION_setEapPhase2Method = FIRST_CALL_TRANSACTION + 67;
  static constexpr uint32_t TRANSACTION_setEapPrivateKeyId = FIRST_CALL_TRANSACTION + 68;
  static constexpr uint32_t TRANSACTION_setEapSubjectMatch = FIRST_CALL_TRANSACTION + 69;
  static constexpr uint32_t TRANSACTION_setEdmg = FIRST_CALL_TRANSACTION + 70;
  static constexpr uint32_t TRANSACTION_setGroupCipher = FIRST_CALL_TRANSACTION + 71;
  static constexpr uint32_t TRANSACTION_setGroupMgmtCipher = FIRST_CALL_TRANSACTION + 72;
  static constexpr uint32_t TRANSACTION_setIdStr = FIRST_CALL_TRANSACTION + 73;
  static constexpr uint32_t TRANSACTION_setKeyMgmt = FIRST_CALL_TRANSACTION + 74;
  static constexpr uint32_t TRANSACTION_setOcsp = FIRST_CALL_TRANSACTION + 75;
  static constexpr uint32_t TRANSACTION_setPairwiseCipher = FIRST_CALL_TRANSACTION + 76;
  static constexpr uint32_t TRANSACTION_setPmkCache = FIRST_CALL_TRANSACTION + 77;
  static constexpr uint32_t TRANSACTION_setProactiveKeyCaching = FIRST_CALL_TRANSACTION + 78;
  static constexpr uint32_t TRANSACTION_setProto = FIRST_CALL_TRANSACTION + 79;
  static constexpr uint32_t TRANSACTION_setPsk = FIRST_CALL_TRANSACTION + 80;
  static constexpr uint32_t TRANSACTION_setPskPassphrase = FIRST_CALL_TRANSACTION + 81;
  static constexpr uint32_t TRANSACTION_setRequirePmf = FIRST_CALL_TRANSACTION + 82;
  static constexpr uint32_t TRANSACTION_setSaeH2eMode = FIRST_CALL_TRANSACTION + 83;
  static constexpr uint32_t TRANSACTION_setSaePassword = FIRST_CALL_TRANSACTION + 84;
  static constexpr uint32_t TRANSACTION_setSaePasswordId = FIRST_CALL_TRANSACTION + 85;
  static constexpr uint32_t TRANSACTION_setScanSsid = FIRST_CALL_TRANSACTION + 86;
  static constexpr uint32_t TRANSACTION_setSsid = FIRST_CALL_TRANSACTION + 87;
  static constexpr uint32_t TRANSACTION_setUpdateIdentifier = FIRST_CALL_TRANSACTION + 88;
  static constexpr uint32_t TRANSACTION_setWapiCertSuite = FIRST_CALL_TRANSACTION + 89;
  static constexpr uint32_t TRANSACTION_setWepKey = FIRST_CALL_TRANSACTION + 90;
  static constexpr uint32_t TRANSACTION_setWepTxKeyIdx = FIRST_CALL_TRANSACTION + 91;
  static constexpr uint32_t TRANSACTION_setRoamingConsortiumSelection = FIRST_CALL_TRANSACTION + 92;

  static std::shared_ptr<ISupplicantStaNetwork> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ISupplicantStaNetwork>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ISupplicantStaNetwork>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ISupplicantStaNetwork>& impl);
  static const std::shared_ptr<ISupplicantStaNetwork>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus disable() = 0;
  virtual ::ndk::ScopedAStatus enable(bool in_noConnect) = 0;
  virtual ::ndk::ScopedAStatus enableSaePkOnlyMode(bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus enableSuiteBEapOpenSslCiphers() = 0;
  virtual ::ndk::ScopedAStatus enableTlsSuiteBEapPhase1Param(bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus getAuthAlg(::aidl::android::hardware::wifi::supplicant::AuthAlgMask* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getBssid(std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getEapAltSubjectMatch(std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getEapAnonymousIdentity(std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getEapCACert(std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getEapCAPath(std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getEapClientCert(std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getEapDomainSuffixMatch(std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getEapEngine(bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getEapEngineId(std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getEapIdentity(std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getEapMethod(::aidl::android::hardware::wifi::supplicant::EapMethod* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getEapPassword(std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getEapPhase2Method(::aidl::android::hardware::wifi::supplicant::EapPhase2Method* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getEapPrivateKeyId(std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getEapSubjectMatch(std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getEdmg(bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getGroupCipher(::aidl::android::hardware::wifi::supplicant::GroupCipherMask* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getGroupMgmtCipher(::aidl::android::hardware::wifi::supplicant::GroupMgmtCipherMask* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getId(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getIdStr(std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceName(std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getKeyMgmt(::aidl::android::hardware::wifi::supplicant::KeyMgmtMask* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getOcsp(::aidl::android::hardware::wifi::supplicant::OcspType* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getPairwiseCipher(::aidl::android::hardware::wifi::supplicant::PairwiseCipherMask* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getProto(::aidl::android::hardware::wifi::supplicant::ProtoMask* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getPsk(std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getPskPassphrase(std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getRequirePmf(bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getSaePassword(std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getSaePasswordId(std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getScanSsid(bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getSsid(std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getType(::aidl::android::hardware::wifi::supplicant::IfaceType* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getWapiCertSuite(std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getWepKey(int32_t in_keyIdx, std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getWepTxKeyIdx(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getWpsNfcConfigurationToken(std::vector<uint8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus registerCallback(const std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantStaNetworkCallback>& in_callback) = 0;
  virtual ::ndk::ScopedAStatus select() = 0;
  virtual ::ndk::ScopedAStatus sendNetworkEapIdentityResponse(const std::vector<uint8_t>& in_identity, const std::vector<uint8_t>& in_encryptedIdentity) = 0;
  virtual ::ndk::ScopedAStatus sendNetworkEapSimGsmAuthFailure() = 0;
  virtual ::ndk::ScopedAStatus sendNetworkEapSimGsmAuthResponse(const std::vector<::aidl::android::hardware::wifi::supplicant::NetworkResponseEapSimGsmAuthParams>& in_params) = 0;
  virtual ::ndk::ScopedAStatus sendNetworkEapSimUmtsAuthFailure() = 0;
  virtual ::ndk::ScopedAStatus sendNetworkEapSimUmtsAuthResponse(const ::aidl::android::hardware::wifi::supplicant::NetworkResponseEapSimUmtsAuthParams& in_params) = 0;
  virtual ::ndk::ScopedAStatus sendNetworkEapSimUmtsAutsResponse(const std::vector<uint8_t>& in_auts) = 0;
  virtual ::ndk::ScopedAStatus setAuthAlg(::aidl::android::hardware::wifi::supplicant::AuthAlgMask in_authAlgMask) = 0;
  virtual ::ndk::ScopedAStatus setBssid(const std::vector<uint8_t>& in_bssid) = 0;
  virtual ::ndk::ScopedAStatus setDppKeys(const ::aidl::android::hardware::wifi::supplicant::DppConnectionKeys& in_keys) = 0;
  virtual ::ndk::ScopedAStatus setEapAltSubjectMatch(const std::string& in_match) = 0;
  virtual ::ndk::ScopedAStatus setEapAnonymousIdentity(const std::vector<uint8_t>& in_identity) = 0;
  virtual ::ndk::ScopedAStatus setEapCACert(const std::string& in_path) = 0;
  virtual ::ndk::ScopedAStatus setEapCAPath(const std::string& in_path) = 0;
  virtual ::ndk::ScopedAStatus setEapClientCert(const std::string& in_path) = 0;
  virtual ::ndk::ScopedAStatus setEapDomainSuffixMatch(const std::string& in_match) = 0;
  virtual ::ndk::ScopedAStatus setEapEncryptedImsiIdentity(const std::vector<uint8_t>& in_identity) = 0;
  virtual ::ndk::ScopedAStatus setEapEngine(bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus setEapEngineID(const std::string& in_id) = 0;
  virtual ::ndk::ScopedAStatus setEapErp(bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus setEapIdentity(const std::vector<uint8_t>& in_identity) = 0;
  virtual ::ndk::ScopedAStatus setEapMethod(::aidl::android::hardware::wifi::supplicant::EapMethod in_method) = 0;
  virtual ::ndk::ScopedAStatus setEapPassword(const std::vector<uint8_t>& in_password) = 0;
  virtual ::ndk::ScopedAStatus setEapPhase2Method(::aidl::android::hardware::wifi::supplicant::EapPhase2Method in_method) = 0;
  virtual ::ndk::ScopedAStatus setEapPrivateKeyId(const std::string& in_id) = 0;
  virtual ::ndk::ScopedAStatus setEapSubjectMatch(const std::string& in_match) = 0;
  virtual ::ndk::ScopedAStatus setEdmg(bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus setGroupCipher(::aidl::android::hardware::wifi::supplicant::GroupCipherMask in_groupCipherMask) = 0;
  virtual ::ndk::ScopedAStatus setGroupMgmtCipher(::aidl::android::hardware::wifi::supplicant::GroupMgmtCipherMask in_groupMgmtCipherMask) = 0;
  virtual ::ndk::ScopedAStatus setIdStr(const std::string& in_idStr) = 0;
  virtual ::ndk::ScopedAStatus setKeyMgmt(::aidl::android::hardware::wifi::supplicant::KeyMgmtMask in_keyMgmtMask) = 0;
  virtual ::ndk::ScopedAStatus setOcsp(::aidl::android::hardware::wifi::supplicant::OcspType in_ocspType) = 0;
  virtual ::ndk::ScopedAStatus setPairwiseCipher(::aidl::android::hardware::wifi::supplicant::PairwiseCipherMask in_pairwiseCipherMask) = 0;
  virtual ::ndk::ScopedAStatus setPmkCache(const std::vector<uint8_t>& in_serializedEntry) = 0;
  virtual ::ndk::ScopedAStatus setProactiveKeyCaching(bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus setProto(::aidl::android::hardware::wifi::supplicant::ProtoMask in_protoMask) = 0;
  virtual ::ndk::ScopedAStatus setPsk(const std::vector<uint8_t>& in_psk) = 0;
  virtual ::ndk::ScopedAStatus setPskPassphrase(const std::string& in_psk) = 0;
  virtual ::ndk::ScopedAStatus setRequirePmf(bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus setSaeH2eMode(::aidl::android::hardware::wifi::supplicant::SaeH2eMode in_mode) = 0;
  virtual ::ndk::ScopedAStatus setSaePassword(const std::string& in_saePassword) = 0;
  virtual ::ndk::ScopedAStatus setSaePasswordId(const std::string& in_saePasswordId) = 0;
  virtual ::ndk::ScopedAStatus setScanSsid(bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus setSsid(const std::vector<uint8_t>& in_ssid) = 0;
  virtual ::ndk::ScopedAStatus setUpdateIdentifier(int32_t in_id) = 0;
  virtual ::ndk::ScopedAStatus setWapiCertSuite(const std::string& in_suite) = 0;
  virtual ::ndk::ScopedAStatus setWepKey(int32_t in_keyIdx, const std::vector<uint8_t>& in_wepKey) = 0;
  virtual ::ndk::ScopedAStatus setWepTxKeyIdx(int32_t in_keyIdx) = 0;
  virtual ::ndk::ScopedAStatus setRoamingConsortiumSelection(const std::vector<uint8_t>& in_selectedRcoi) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ISupplicantStaNetwork> default_impl;
};
class ISupplicantStaNetworkDefault : public ISupplicantStaNetwork {
public:
  ::ndk::ScopedAStatus disable() override;
  ::ndk::ScopedAStatus enable(bool in_noConnect) override;
  ::ndk::ScopedAStatus enableSaePkOnlyMode(bool in_enable) override;
  ::ndk::ScopedAStatus enableSuiteBEapOpenSslCiphers() override;
  ::ndk::ScopedAStatus enableTlsSuiteBEapPhase1Param(bool in_enable) override;
  ::ndk::ScopedAStatus getAuthAlg(::aidl::android::hardware::wifi::supplicant::AuthAlgMask* _aidl_return) override;
  ::ndk::ScopedAStatus getBssid(std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getEapAltSubjectMatch(std::string* _aidl_return) override;
  ::ndk::ScopedAStatus getEapAnonymousIdentity(std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getEapCACert(std::string* _aidl_return) override;
  ::ndk::ScopedAStatus getEapCAPath(std::string* _aidl_return) override;
  ::ndk::ScopedAStatus getEapClientCert(std::string* _aidl_return) override;
  ::ndk::ScopedAStatus getEapDomainSuffixMatch(std::string* _aidl_return) override;
  ::ndk::ScopedAStatus getEapEngine(bool* _aidl_return) override;
  ::ndk::ScopedAStatus getEapEngineId(std::string* _aidl_return) override;
  ::ndk::ScopedAStatus getEapIdentity(std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getEapMethod(::aidl::android::hardware::wifi::supplicant::EapMethod* _aidl_return) override;
  ::ndk::ScopedAStatus getEapPassword(std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getEapPhase2Method(::aidl::android::hardware::wifi::supplicant::EapPhase2Method* _aidl_return) override;
  ::ndk::ScopedAStatus getEapPrivateKeyId(std::string* _aidl_return) override;
  ::ndk::ScopedAStatus getEapSubjectMatch(std::string* _aidl_return) override;
  ::ndk::ScopedAStatus getEdmg(bool* _aidl_return) override;
  ::ndk::ScopedAStatus getGroupCipher(::aidl::android::hardware::wifi::supplicant::GroupCipherMask* _aidl_return) override;
  ::ndk::ScopedAStatus getGroupMgmtCipher(::aidl::android::hardware::wifi::supplicant::GroupMgmtCipherMask* _aidl_return) override;
  ::ndk::ScopedAStatus getId(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getIdStr(std::string* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceName(std::string* _aidl_return) override;
  ::ndk::ScopedAStatus getKeyMgmt(::aidl::android::hardware::wifi::supplicant::KeyMgmtMask* _aidl_return) override;
  ::ndk::ScopedAStatus getOcsp(::aidl::android::hardware::wifi::supplicant::OcspType* _aidl_return) override;
  ::ndk::ScopedAStatus getPairwiseCipher(::aidl::android::hardware::wifi::supplicant::PairwiseCipherMask* _aidl_return) override;
  ::ndk::ScopedAStatus getProto(::aidl::android::hardware::wifi::supplicant::ProtoMask* _aidl_return) override;
  ::ndk::ScopedAStatus getPsk(std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getPskPassphrase(std::string* _aidl_return) override;
  ::ndk::ScopedAStatus getRequirePmf(bool* _aidl_return) override;
  ::ndk::ScopedAStatus getSaePassword(std::string* _aidl_return) override;
  ::ndk::ScopedAStatus getSaePasswordId(std::string* _aidl_return) override;
  ::ndk::ScopedAStatus getScanSsid(bool* _aidl_return) override;
  ::ndk::ScopedAStatus getSsid(std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getType(::aidl::android::hardware::wifi::supplicant::IfaceType* _aidl_return) override;
  ::ndk::ScopedAStatus getWapiCertSuite(std::string* _aidl_return) override;
  ::ndk::ScopedAStatus getWepKey(int32_t in_keyIdx, std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getWepTxKeyIdx(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getWpsNfcConfigurationToken(std::vector<uint8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus registerCallback(const std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantStaNetworkCallback>& in_callback) override;
  ::ndk::ScopedAStatus select() override;
  ::ndk::ScopedAStatus sendNetworkEapIdentityResponse(const std::vector<uint8_t>& in_identity, const std::vector<uint8_t>& in_encryptedIdentity) override;
  ::ndk::ScopedAStatus sendNetworkEapSimGsmAuthFailure() override;
  ::ndk::ScopedAStatus sendNetworkEapSimGsmAuthResponse(const std::vector<::aidl::android::hardware::wifi::supplicant::NetworkResponseEapSimGsmAuthParams>& in_params) override;
  ::ndk::ScopedAStatus sendNetworkEapSimUmtsAuthFailure() override;
  ::ndk::ScopedAStatus sendNetworkEapSimUmtsAuthResponse(const ::aidl::android::hardware::wifi::supplicant::NetworkResponseEapSimUmtsAuthParams& in_params) override;
  ::ndk::ScopedAStatus sendNetworkEapSimUmtsAutsResponse(const std::vector<uint8_t>& in_auts) override;
  ::ndk::ScopedAStatus setAuthAlg(::aidl::android::hardware::wifi::supplicant::AuthAlgMask in_authAlgMask) override;
  ::ndk::ScopedAStatus setBssid(const std::vector<uint8_t>& in_bssid) override;
  ::ndk::ScopedAStatus setDppKeys(const ::aidl::android::hardware::wifi::supplicant::DppConnectionKeys& in_keys) override;
  ::ndk::ScopedAStatus setEapAltSubjectMatch(const std::string& in_match) override;
  ::ndk::ScopedAStatus setEapAnonymousIdentity(const std::vector<uint8_t>& in_identity) override;
  ::ndk::ScopedAStatus setEapCACert(const std::string& in_path) override;
  ::ndk::ScopedAStatus setEapCAPath(const std::string& in_path) override;
  ::ndk::ScopedAStatus setEapClientCert(const std::string& in_path) override;
  ::ndk::ScopedAStatus setEapDomainSuffixMatch(const std::string& in_match) override;
  ::ndk::ScopedAStatus setEapEncryptedImsiIdentity(const std::vector<uint8_t>& in_identity) override;
  ::ndk::ScopedAStatus setEapEngine(bool in_enable) override;
  ::ndk::ScopedAStatus setEapEngineID(const std::string& in_id) override;
  ::ndk::ScopedAStatus setEapErp(bool in_enable) override;
  ::ndk::ScopedAStatus setEapIdentity(const std::vector<uint8_t>& in_identity) override;
  ::ndk::ScopedAStatus setEapMethod(::aidl::android::hardware::wifi::supplicant::EapMethod in_method) override;
  ::ndk::ScopedAStatus setEapPassword(const std::vector<uint8_t>& in_password) override;
  ::ndk::ScopedAStatus setEapPhase2Method(::aidl::android::hardware::wifi::supplicant::EapPhase2Method in_method) override;
  ::ndk::ScopedAStatus setEapPrivateKeyId(const std::string& in_id) override;
  ::ndk::ScopedAStatus setEapSubjectMatch(const std::string& in_match) override;
  ::ndk::ScopedAStatus setEdmg(bool in_enable) override;
  ::ndk::ScopedAStatus setGroupCipher(::aidl::android::hardware::wifi::supplicant::GroupCipherMask in_groupCipherMask) override;
  ::ndk::ScopedAStatus setGroupMgmtCipher(::aidl::android::hardware::wifi::supplicant::GroupMgmtCipherMask in_groupMgmtCipherMask) override;
  ::ndk::ScopedAStatus setIdStr(const std::string& in_idStr) override;
  ::ndk::ScopedAStatus setKeyMgmt(::aidl::android::hardware::wifi::supplicant::KeyMgmtMask in_keyMgmtMask) override;
  ::ndk::ScopedAStatus setOcsp(::aidl::android::hardware::wifi::supplicant::OcspType in_ocspType) override;
  ::ndk::ScopedAStatus setPairwiseCipher(::aidl::android::hardware::wifi::supplicant::PairwiseCipherMask in_pairwiseCipherMask) override;
  ::ndk::ScopedAStatus setPmkCache(const std::vector<uint8_t>& in_serializedEntry) override;
  ::ndk::ScopedAStatus setProactiveKeyCaching(bool in_enable) override;
  ::ndk::ScopedAStatus setProto(::aidl::android::hardware::wifi::supplicant::ProtoMask in_protoMask) override;
  ::ndk::ScopedAStatus setPsk(const std::vector<uint8_t>& in_psk) override;
  ::ndk::ScopedAStatus setPskPassphrase(const std::string& in_psk) override;
  ::ndk::ScopedAStatus setRequirePmf(bool in_enable) override;
  ::ndk::ScopedAStatus setSaeH2eMode(::aidl::android::hardware::wifi::supplicant::SaeH2eMode in_mode) override;
  ::ndk::ScopedAStatus setSaePassword(const std::string& in_saePassword) override;
  ::ndk::ScopedAStatus setSaePasswordId(const std::string& in_saePasswordId) override;
  ::ndk::ScopedAStatus setScanSsid(bool in_enable) override;
  ::ndk::ScopedAStatus setSsid(const std::vector<uint8_t>& in_ssid) override;
  ::ndk::ScopedAStatus setUpdateIdentifier(int32_t in_id) override;
  ::ndk::ScopedAStatus setWapiCertSuite(const std::string& in_suite) override;
  ::ndk::ScopedAStatus setWepKey(int32_t in_keyIdx, const std::vector<uint8_t>& in_wepKey) override;
  ::ndk::ScopedAStatus setWepTxKeyIdx(int32_t in_keyIdx) override;
  ::ndk::ScopedAStatus setRoamingConsortiumSelection(const std::vector<uint8_t>& in_selectedRcoi) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
