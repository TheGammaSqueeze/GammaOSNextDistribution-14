#pragma once

#include "aidl/android/hardware/wifi/supplicant/ISupplicantStaNetwork.h"

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
namespace wifi {
namespace supplicant {
class BnSupplicantStaNetwork : public ::ndk::BnCInterface<ISupplicantStaNetwork> {
public:
  BnSupplicantStaNetwork();
  virtual ~BnSupplicantStaNetwork();
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) final;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) final;
protected:
  ::ndk::SpAIBinder createBinder() override;
private:
};
class ISupplicantStaNetworkDelegator : public BnSupplicantStaNetwork {
public:
  explicit ISupplicantStaNetworkDelegator(const std::shared_ptr<ISupplicantStaNetwork> &impl) : _impl(impl) {
     int32_t _impl_ver = 0;
     if (!impl->getInterfaceVersion(&_impl_ver).isOk()) {;
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Delegator failed to get version of the implementation.");
     }
     if (_impl_ver != ISupplicantStaNetwork::version) {
        __assert2(__FILE__, __LINE__, __PRETTY_FUNCTION__, "Mismatched versions of delegator and implementation is not allowed.");
     }
  }

  ::ndk::ScopedAStatus disable() override {
    return _impl->disable();
  }
  ::ndk::ScopedAStatus enable(bool in_noConnect) override {
    return _impl->enable(in_noConnect);
  }
  ::ndk::ScopedAStatus enableSaePkOnlyMode(bool in_enable) override {
    return _impl->enableSaePkOnlyMode(in_enable);
  }
  ::ndk::ScopedAStatus enableSuiteBEapOpenSslCiphers() override {
    return _impl->enableSuiteBEapOpenSslCiphers();
  }
  ::ndk::ScopedAStatus enableTlsSuiteBEapPhase1Param(bool in_enable) override {
    return _impl->enableTlsSuiteBEapPhase1Param(in_enable);
  }
  ::ndk::ScopedAStatus getAuthAlg(::aidl::android::hardware::wifi::supplicant::AuthAlgMask* _aidl_return) override {
    return _impl->getAuthAlg(_aidl_return);
  }
  ::ndk::ScopedAStatus getBssid(std::vector<uint8_t>* _aidl_return) override {
    return _impl->getBssid(_aidl_return);
  }
  ::ndk::ScopedAStatus getEapAltSubjectMatch(std::string* _aidl_return) override {
    return _impl->getEapAltSubjectMatch(_aidl_return);
  }
  ::ndk::ScopedAStatus getEapAnonymousIdentity(std::vector<uint8_t>* _aidl_return) override {
    return _impl->getEapAnonymousIdentity(_aidl_return);
  }
  ::ndk::ScopedAStatus getEapCACert(std::string* _aidl_return) override {
    return _impl->getEapCACert(_aidl_return);
  }
  ::ndk::ScopedAStatus getEapCAPath(std::string* _aidl_return) override {
    return _impl->getEapCAPath(_aidl_return);
  }
  ::ndk::ScopedAStatus getEapClientCert(std::string* _aidl_return) override {
    return _impl->getEapClientCert(_aidl_return);
  }
  ::ndk::ScopedAStatus getEapDomainSuffixMatch(std::string* _aidl_return) override {
    return _impl->getEapDomainSuffixMatch(_aidl_return);
  }
  ::ndk::ScopedAStatus getEapEngine(bool* _aidl_return) override {
    return _impl->getEapEngine(_aidl_return);
  }
  ::ndk::ScopedAStatus getEapEngineId(std::string* _aidl_return) override {
    return _impl->getEapEngineId(_aidl_return);
  }
  ::ndk::ScopedAStatus getEapIdentity(std::vector<uint8_t>* _aidl_return) override {
    return _impl->getEapIdentity(_aidl_return);
  }
  ::ndk::ScopedAStatus getEapMethod(::aidl::android::hardware::wifi::supplicant::EapMethod* _aidl_return) override {
    return _impl->getEapMethod(_aidl_return);
  }
  ::ndk::ScopedAStatus getEapPassword(std::vector<uint8_t>* _aidl_return) override {
    return _impl->getEapPassword(_aidl_return);
  }
  ::ndk::ScopedAStatus getEapPhase2Method(::aidl::android::hardware::wifi::supplicant::EapPhase2Method* _aidl_return) override {
    return _impl->getEapPhase2Method(_aidl_return);
  }
  ::ndk::ScopedAStatus getEapPrivateKeyId(std::string* _aidl_return) override {
    return _impl->getEapPrivateKeyId(_aidl_return);
  }
  ::ndk::ScopedAStatus getEapSubjectMatch(std::string* _aidl_return) override {
    return _impl->getEapSubjectMatch(_aidl_return);
  }
  ::ndk::ScopedAStatus getEdmg(bool* _aidl_return) override {
    return _impl->getEdmg(_aidl_return);
  }
  ::ndk::ScopedAStatus getGroupCipher(::aidl::android::hardware::wifi::supplicant::GroupCipherMask* _aidl_return) override {
    return _impl->getGroupCipher(_aidl_return);
  }
  ::ndk::ScopedAStatus getGroupMgmtCipher(::aidl::android::hardware::wifi::supplicant::GroupMgmtCipherMask* _aidl_return) override {
    return _impl->getGroupMgmtCipher(_aidl_return);
  }
  ::ndk::ScopedAStatus getId(int32_t* _aidl_return) override {
    return _impl->getId(_aidl_return);
  }
  ::ndk::ScopedAStatus getIdStr(std::string* _aidl_return) override {
    return _impl->getIdStr(_aidl_return);
  }
  ::ndk::ScopedAStatus getInterfaceName(std::string* _aidl_return) override {
    return _impl->getInterfaceName(_aidl_return);
  }
  ::ndk::ScopedAStatus getKeyMgmt(::aidl::android::hardware::wifi::supplicant::KeyMgmtMask* _aidl_return) override {
    return _impl->getKeyMgmt(_aidl_return);
  }
  ::ndk::ScopedAStatus getOcsp(::aidl::android::hardware::wifi::supplicant::OcspType* _aidl_return) override {
    return _impl->getOcsp(_aidl_return);
  }
  ::ndk::ScopedAStatus getPairwiseCipher(::aidl::android::hardware::wifi::supplicant::PairwiseCipherMask* _aidl_return) override {
    return _impl->getPairwiseCipher(_aidl_return);
  }
  ::ndk::ScopedAStatus getProto(::aidl::android::hardware::wifi::supplicant::ProtoMask* _aidl_return) override {
    return _impl->getProto(_aidl_return);
  }
  ::ndk::ScopedAStatus getPsk(std::vector<uint8_t>* _aidl_return) override {
    return _impl->getPsk(_aidl_return);
  }
  ::ndk::ScopedAStatus getPskPassphrase(std::string* _aidl_return) override {
    return _impl->getPskPassphrase(_aidl_return);
  }
  ::ndk::ScopedAStatus getRequirePmf(bool* _aidl_return) override {
    return _impl->getRequirePmf(_aidl_return);
  }
  ::ndk::ScopedAStatus getSaePassword(std::string* _aidl_return) override {
    return _impl->getSaePassword(_aidl_return);
  }
  ::ndk::ScopedAStatus getSaePasswordId(std::string* _aidl_return) override {
    return _impl->getSaePasswordId(_aidl_return);
  }
  ::ndk::ScopedAStatus getScanSsid(bool* _aidl_return) override {
    return _impl->getScanSsid(_aidl_return);
  }
  ::ndk::ScopedAStatus getSsid(std::vector<uint8_t>* _aidl_return) override {
    return _impl->getSsid(_aidl_return);
  }
  ::ndk::ScopedAStatus getType(::aidl::android::hardware::wifi::supplicant::IfaceType* _aidl_return) override {
    return _impl->getType(_aidl_return);
  }
  ::ndk::ScopedAStatus getWapiCertSuite(std::string* _aidl_return) override {
    return _impl->getWapiCertSuite(_aidl_return);
  }
  ::ndk::ScopedAStatus getWepKey(int32_t in_keyIdx, std::vector<uint8_t>* _aidl_return) override {
    return _impl->getWepKey(in_keyIdx, _aidl_return);
  }
  ::ndk::ScopedAStatus getWepTxKeyIdx(int32_t* _aidl_return) override {
    return _impl->getWepTxKeyIdx(_aidl_return);
  }
  ::ndk::ScopedAStatus getWpsNfcConfigurationToken(std::vector<uint8_t>* _aidl_return) override {
    return _impl->getWpsNfcConfigurationToken(_aidl_return);
  }
  ::ndk::ScopedAStatus registerCallback(const std::shared_ptr<::aidl::android::hardware::wifi::supplicant::ISupplicantStaNetworkCallback>& in_callback) override {
    return _impl->registerCallback(in_callback);
  }
  ::ndk::ScopedAStatus select() override {
    return _impl->select();
  }
  ::ndk::ScopedAStatus sendNetworkEapIdentityResponse(const std::vector<uint8_t>& in_identity, const std::vector<uint8_t>& in_encryptedIdentity) override {
    return _impl->sendNetworkEapIdentityResponse(in_identity, in_encryptedIdentity);
  }
  ::ndk::ScopedAStatus sendNetworkEapSimGsmAuthFailure() override {
    return _impl->sendNetworkEapSimGsmAuthFailure();
  }
  ::ndk::ScopedAStatus sendNetworkEapSimGsmAuthResponse(const std::vector<::aidl::android::hardware::wifi::supplicant::NetworkResponseEapSimGsmAuthParams>& in_params) override {
    return _impl->sendNetworkEapSimGsmAuthResponse(in_params);
  }
  ::ndk::ScopedAStatus sendNetworkEapSimUmtsAuthFailure() override {
    return _impl->sendNetworkEapSimUmtsAuthFailure();
  }
  ::ndk::ScopedAStatus sendNetworkEapSimUmtsAuthResponse(const ::aidl::android::hardware::wifi::supplicant::NetworkResponseEapSimUmtsAuthParams& in_params) override {
    return _impl->sendNetworkEapSimUmtsAuthResponse(in_params);
  }
  ::ndk::ScopedAStatus sendNetworkEapSimUmtsAutsResponse(const std::vector<uint8_t>& in_auts) override {
    return _impl->sendNetworkEapSimUmtsAutsResponse(in_auts);
  }
  ::ndk::ScopedAStatus setAuthAlg(::aidl::android::hardware::wifi::supplicant::AuthAlgMask in_authAlgMask) override {
    return _impl->setAuthAlg(in_authAlgMask);
  }
  ::ndk::ScopedAStatus setBssid(const std::vector<uint8_t>& in_bssid) override {
    return _impl->setBssid(in_bssid);
  }
  ::ndk::ScopedAStatus setDppKeys(const ::aidl::android::hardware::wifi::supplicant::DppConnectionKeys& in_keys) override {
    return _impl->setDppKeys(in_keys);
  }
  ::ndk::ScopedAStatus setEapAltSubjectMatch(const std::string& in_match) override {
    return _impl->setEapAltSubjectMatch(in_match);
  }
  ::ndk::ScopedAStatus setEapAnonymousIdentity(const std::vector<uint8_t>& in_identity) override {
    return _impl->setEapAnonymousIdentity(in_identity);
  }
  ::ndk::ScopedAStatus setEapCACert(const std::string& in_path) override {
    return _impl->setEapCACert(in_path);
  }
  ::ndk::ScopedAStatus setEapCAPath(const std::string& in_path) override {
    return _impl->setEapCAPath(in_path);
  }
  ::ndk::ScopedAStatus setEapClientCert(const std::string& in_path) override {
    return _impl->setEapClientCert(in_path);
  }
  ::ndk::ScopedAStatus setEapDomainSuffixMatch(const std::string& in_match) override {
    return _impl->setEapDomainSuffixMatch(in_match);
  }
  ::ndk::ScopedAStatus setEapEncryptedImsiIdentity(const std::vector<uint8_t>& in_identity) override {
    return _impl->setEapEncryptedImsiIdentity(in_identity);
  }
  ::ndk::ScopedAStatus setEapEngine(bool in_enable) override {
    return _impl->setEapEngine(in_enable);
  }
  ::ndk::ScopedAStatus setEapEngineID(const std::string& in_id) override {
    return _impl->setEapEngineID(in_id);
  }
  ::ndk::ScopedAStatus setEapErp(bool in_enable) override {
    return _impl->setEapErp(in_enable);
  }
  ::ndk::ScopedAStatus setEapIdentity(const std::vector<uint8_t>& in_identity) override {
    return _impl->setEapIdentity(in_identity);
  }
  ::ndk::ScopedAStatus setEapMethod(::aidl::android::hardware::wifi::supplicant::EapMethod in_method) override {
    return _impl->setEapMethod(in_method);
  }
  ::ndk::ScopedAStatus setEapPassword(const std::vector<uint8_t>& in_password) override {
    return _impl->setEapPassword(in_password);
  }
  ::ndk::ScopedAStatus setEapPhase2Method(::aidl::android::hardware::wifi::supplicant::EapPhase2Method in_method) override {
    return _impl->setEapPhase2Method(in_method);
  }
  ::ndk::ScopedAStatus setEapPrivateKeyId(const std::string& in_id) override {
    return _impl->setEapPrivateKeyId(in_id);
  }
  ::ndk::ScopedAStatus setEapSubjectMatch(const std::string& in_match) override {
    return _impl->setEapSubjectMatch(in_match);
  }
  ::ndk::ScopedAStatus setEdmg(bool in_enable) override {
    return _impl->setEdmg(in_enable);
  }
  ::ndk::ScopedAStatus setGroupCipher(::aidl::android::hardware::wifi::supplicant::GroupCipherMask in_groupCipherMask) override {
    return _impl->setGroupCipher(in_groupCipherMask);
  }
  ::ndk::ScopedAStatus setGroupMgmtCipher(::aidl::android::hardware::wifi::supplicant::GroupMgmtCipherMask in_groupMgmtCipherMask) override {
    return _impl->setGroupMgmtCipher(in_groupMgmtCipherMask);
  }
  ::ndk::ScopedAStatus setIdStr(const std::string& in_idStr) override {
    return _impl->setIdStr(in_idStr);
  }
  ::ndk::ScopedAStatus setKeyMgmt(::aidl::android::hardware::wifi::supplicant::KeyMgmtMask in_keyMgmtMask) override {
    return _impl->setKeyMgmt(in_keyMgmtMask);
  }
  ::ndk::ScopedAStatus setOcsp(::aidl::android::hardware::wifi::supplicant::OcspType in_ocspType) override {
    return _impl->setOcsp(in_ocspType);
  }
  ::ndk::ScopedAStatus setPairwiseCipher(::aidl::android::hardware::wifi::supplicant::PairwiseCipherMask in_pairwiseCipherMask) override {
    return _impl->setPairwiseCipher(in_pairwiseCipherMask);
  }
  ::ndk::ScopedAStatus setPmkCache(const std::vector<uint8_t>& in_serializedEntry) override {
    return _impl->setPmkCache(in_serializedEntry);
  }
  ::ndk::ScopedAStatus setProactiveKeyCaching(bool in_enable) override {
    return _impl->setProactiveKeyCaching(in_enable);
  }
  ::ndk::ScopedAStatus setProto(::aidl::android::hardware::wifi::supplicant::ProtoMask in_protoMask) override {
    return _impl->setProto(in_protoMask);
  }
  ::ndk::ScopedAStatus setPsk(const std::vector<uint8_t>& in_psk) override {
    return _impl->setPsk(in_psk);
  }
  ::ndk::ScopedAStatus setPskPassphrase(const std::string& in_psk) override {
    return _impl->setPskPassphrase(in_psk);
  }
  ::ndk::ScopedAStatus setRequirePmf(bool in_enable) override {
    return _impl->setRequirePmf(in_enable);
  }
  ::ndk::ScopedAStatus setSaeH2eMode(::aidl::android::hardware::wifi::supplicant::SaeH2eMode in_mode) override {
    return _impl->setSaeH2eMode(in_mode);
  }
  ::ndk::ScopedAStatus setSaePassword(const std::string& in_saePassword) override {
    return _impl->setSaePassword(in_saePassword);
  }
  ::ndk::ScopedAStatus setSaePasswordId(const std::string& in_saePasswordId) override {
    return _impl->setSaePasswordId(in_saePasswordId);
  }
  ::ndk::ScopedAStatus setScanSsid(bool in_enable) override {
    return _impl->setScanSsid(in_enable);
  }
  ::ndk::ScopedAStatus setSsid(const std::vector<uint8_t>& in_ssid) override {
    return _impl->setSsid(in_ssid);
  }
  ::ndk::ScopedAStatus setUpdateIdentifier(int32_t in_id) override {
    return _impl->setUpdateIdentifier(in_id);
  }
  ::ndk::ScopedAStatus setWapiCertSuite(const std::string& in_suite) override {
    return _impl->setWapiCertSuite(in_suite);
  }
  ::ndk::ScopedAStatus setWepKey(int32_t in_keyIdx, const std::vector<uint8_t>& in_wepKey) override {
    return _impl->setWepKey(in_keyIdx, in_wepKey);
  }
  ::ndk::ScopedAStatus setWepTxKeyIdx(int32_t in_keyIdx) override {
    return _impl->setWepTxKeyIdx(in_keyIdx);
  }
  ::ndk::ScopedAStatus setRoamingConsortiumSelection(const std::vector<uint8_t>& in_selectedRcoi) override {
    return _impl->setRoamingConsortiumSelection(in_selectedRcoi);
  }
protected:
private:
  std::shared_ptr<ISupplicantStaNetwork> _impl;
};

}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
