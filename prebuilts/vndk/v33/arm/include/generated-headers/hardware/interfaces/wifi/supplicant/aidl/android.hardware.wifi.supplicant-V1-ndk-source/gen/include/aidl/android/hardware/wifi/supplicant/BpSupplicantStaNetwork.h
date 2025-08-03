#pragma once

#include "aidl/android/hardware/wifi/supplicant/ISupplicantStaNetwork.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
class BpSupplicantStaNetwork : public ::ndk::BpCInterface<ISupplicantStaNetwork> {
public:
  explicit BpSupplicantStaNetwork(const ::ndk::SpAIBinder& binder);
  virtual ~BpSupplicantStaNetwork();

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
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
