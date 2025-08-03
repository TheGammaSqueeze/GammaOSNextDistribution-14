/*
 * WPA Supplicant - Sta network Aidl interface
 * Copyright (c) 2021, Google Inc. All rights reserved.
 *
 * This software may be distributed under the terms of the BSD license.
 * See README for more details.
 */

#include "aidl_manager.h"
#include "aidl_return_util.h"
#include "misc_utils.h"
#include "sta_network.h"

extern "C"
{
#include "wps_supplicant.h"
#include "crypto/tls.h"
}

namespace {
using aidl::android::hardware::wifi::supplicant::AuthAlgMask;
using aidl::android::hardware::wifi::supplicant::EapMethod;
using aidl::android::hardware::wifi::supplicant::EapPhase2Method;
using aidl::android::hardware::wifi::supplicant::GroupCipherMask;
using aidl::android::hardware::wifi::supplicant::GroupMgmtCipherMask;
using aidl::android::hardware::wifi::supplicant::ISupplicantStaNetwork;
using aidl::android::hardware::wifi::supplicant::KeyMgmtMask;
using aidl::android::hardware::wifi::supplicant::PairwiseCipherMask;
using aidl::android::hardware::wifi::supplicant::ProtoMask;

constexpr uint8_t kZeroBssid[6] = {0, 0, 0, 0, 0, 0};

constexpr uint32_t kAllowedKeyMgmtMask =
	(static_cast<uint32_t>(KeyMgmtMask::NONE) |
	 static_cast<uint32_t>(KeyMgmtMask::WPA_PSK) |
	 static_cast<uint32_t>(KeyMgmtMask::WPA_EAP) |
	 static_cast<uint32_t>(KeyMgmtMask::IEEE8021X) |
	 static_cast<uint32_t>(KeyMgmtMask::FT_EAP) |
	 static_cast<uint32_t>(KeyMgmtMask::FT_PSK) |
	 static_cast<uint32_t>(KeyMgmtMask::OSEN) |
	 static_cast<uint32_t>(KeyMgmtMask::SAE) |
	 static_cast<uint32_t>(KeyMgmtMask::SUITE_B_192) |
	 static_cast<uint32_t>(KeyMgmtMask::OWE) |
	 static_cast<uint32_t>(KeyMgmtMask::WPA_PSK_SHA256) |
	 static_cast<uint32_t>(KeyMgmtMask::WPA_EAP_SHA256) |
	 static_cast<uint32_t>(KeyMgmtMask::WAPI_PSK) |
	 static_cast<uint32_t>(KeyMgmtMask::WAPI_CERT) |
	 static_cast<uint32_t>(KeyMgmtMask::FILS_SHA256) |
	 static_cast<uint32_t>(KeyMgmtMask::FILS_SHA384) |
	 static_cast<uint32_t>(KeyMgmtMask::DPP));
constexpr uint32_t kAllowedProtoMask =
	(static_cast<uint32_t>(ProtoMask::WPA) |
	 static_cast<uint32_t>(ProtoMask::RSN) |
	 static_cast<uint32_t>(ProtoMask::OSEN) |
	 static_cast<uint32_t>(ProtoMask::WAPI));
constexpr uint32_t kAllowedAuthAlgMask =
	(static_cast<uint32_t>(AuthAlgMask::OPEN) |
	 static_cast<uint32_t>(AuthAlgMask::SHARED) |
	 static_cast<uint32_t>(AuthAlgMask::LEAP) |
	 static_cast<uint32_t>(AuthAlgMask::SAE));
constexpr uint32_t kAllowedGroupCipherMask =
	(static_cast<uint32_t>(GroupCipherMask::WEP40) |
	 static_cast<uint32_t>(GroupCipherMask::WEP104) |
	 static_cast<uint32_t>(GroupCipherMask::TKIP) |
	 static_cast<uint32_t>(GroupCipherMask::CCMP) |
	 static_cast<uint32_t>(
	 GroupCipherMask::GTK_NOT_USED) |
	 static_cast<uint32_t>(GroupCipherMask::GCMP_256) |
	 static_cast<uint32_t>(GroupCipherMask::SMS4) |
	 static_cast<uint32_t>(GroupCipherMask::GCMP_128));
constexpr uint32_t kAllowedPairwisewCipherMask =
	(static_cast<uint32_t>(PairwiseCipherMask::NONE) |
	 static_cast<uint32_t>(PairwiseCipherMask::TKIP) |
	 static_cast<uint32_t>(PairwiseCipherMask::CCMP) |
	 static_cast<uint32_t>(
	 PairwiseCipherMask::GCMP_256) |
	 static_cast<uint32_t>(
	 PairwiseCipherMask::SMS4) |
	 static_cast<uint32_t>(PairwiseCipherMask::GCMP_128));
constexpr uint32_t kAllowedGroupMgmtCipherMask =
	(static_cast<uint32_t>(
			GroupMgmtCipherMask::BIP_GMAC_128) |
	 static_cast<uint32_t>(
			 GroupMgmtCipherMask::BIP_GMAC_256) |
	 static_cast<uint32_t>(
			 GroupMgmtCipherMask::BIP_CMAC_256));

constexpr uint32_t kEapMethodMax =
	static_cast<uint32_t>(EapMethod::WFA_UNAUTH_TLS) + 1;
constexpr char const *kEapMethodStrings[kEapMethodMax] = {
	"PEAP", "TLS", "TTLS", "PWD", "SIM", "AKA", "AKA'", "WFA-UNAUTH-TLS"};
constexpr uint32_t kEapPhase2MethodMax =
	static_cast<uint32_t>(EapPhase2Method::AKA_PRIME) + 1;
constexpr char const *kEapPhase2MethodStrings[kEapPhase2MethodMax] = {
	"", "PAP", "MSCHAP", "MSCHAPV2", "GTC", "SIM", "AKA", "AKA'"};
constexpr char kEapPhase2AuthPrefix[] = "auth=";
constexpr char kEapPhase2AuthEapPrefix[] = "autheap=";
constexpr char kNetworkEapSimGsmAuthResponse[] = "GSM-AUTH";
constexpr char kNetworkEapSimUmtsAuthResponse[] = "UMTS-AUTH";
constexpr char kNetworkEapSimUmtsAutsResponse[] = "UMTS-AUTS";
constexpr char kNetworkEapSimGsmAuthFailure[] = "GSM-FAIL";
constexpr char kNetworkEapSimUmtsAuthFailure[] = "UMTS-FAIL";

#ifdef CONFIG_WAPI_INTERFACE
std::string dummyWapiCertSuite;
std::vector<uint8_t> dummyWapiPsk;
#endif /* CONFIG_WAPI_INTERFACE */
}  // namespace

namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
using aidl_return_util::validateAndCall;
using misc_utils::createStatus;
using misc_utils::createStatusWithMsg;

StaNetwork::StaNetwork(
	struct wpa_global *wpa_global, const char ifname[], int network_id)
	: wpa_global_(wpa_global),
	  ifname_(ifname),
	  network_id_(network_id),
	  is_valid_(true)
{
	tlsFlags = 0;
}

void StaNetwork::invalidate() { is_valid_ = false; }
bool StaNetwork::isValid()
{
	return (is_valid_ && (retrieveNetworkPtr() != nullptr));
}

::ndk::ScopedAStatus StaNetwork::getId(
	int32_t* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getIdInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getInterfaceName(
	std::string* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getInterfaceNameInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getType(
	IfaceType* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getTypeInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::registerCallback(
	const std::shared_ptr<ISupplicantStaNetworkCallback>& in_callback)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::registerCallbackInternal, in_callback);
}

::ndk::ScopedAStatus StaNetwork::setSsid(
	const std::vector<uint8_t>& in_ssid)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setSsidInternal, in_ssid);
}

::ndk::ScopedAStatus StaNetwork::setBssid(
	const std::vector<uint8_t>& in_bssid)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setBssidInternal, in_bssid);
}

::ndk::ScopedAStatus StaNetwork::setDppKeys(const DppConnectionKeys& in_keys)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setDppKeysInternal, in_keys);
}

::ndk::ScopedAStatus StaNetwork::setScanSsid(bool in_enable)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setScanSsidInternal, in_enable);
}

::ndk::ScopedAStatus StaNetwork::setKeyMgmt(
	KeyMgmtMask in_keyMgmtMask)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setKeyMgmtInternal, in_keyMgmtMask);
}

::ndk::ScopedAStatus StaNetwork::setProto(
	ProtoMask in_protoMask)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setProtoInternal, in_protoMask);
}

::ndk::ScopedAStatus StaNetwork::setAuthAlg(
	AuthAlgMask in_authAlgMask)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setAuthAlgInternal, in_authAlgMask);
}

::ndk::ScopedAStatus StaNetwork::setGroupCipher(
	GroupCipherMask in_groupCipherMask)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setGroupCipherInternal, in_groupCipherMask);
}

::ndk::ScopedAStatus StaNetwork::setPairwiseCipher(
	PairwiseCipherMask in_pairwiseCipherMask)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setPairwiseCipherInternal,
		in_pairwiseCipherMask);
}

::ndk::ScopedAStatus StaNetwork::setPskPassphrase(
	const std::string& in_psk)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setPskPassphraseInternal, in_psk);
}

::ndk::ScopedAStatus StaNetwork::setPsk(
	const std::vector<uint8_t>& in_psk)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setPskInternal, in_psk);
}

::ndk::ScopedAStatus StaNetwork::setWepKey(
	int32_t in_keyIdx, const std::vector<uint8_t>& in_wepKey)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setWepKeyInternal, in_keyIdx, in_wepKey);
}

::ndk::ScopedAStatus StaNetwork::setWepTxKeyIdx(
	int32_t in_keyIdx)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setWepTxKeyIdxInternal, in_keyIdx);
}

::ndk::ScopedAStatus StaNetwork::setRequirePmf(bool in_enable)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setRequirePmfInternal, in_enable);
}

::ndk::ScopedAStatus StaNetwork::setEapMethod(
	EapMethod in_method)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setEapMethodInternal, in_method);
}

::ndk::ScopedAStatus StaNetwork::setEapPhase2Method(
	EapPhase2Method in_method)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setEapPhase2MethodInternal, in_method);
}

::ndk::ScopedAStatus StaNetwork::setEapIdentity(
	const std::vector<uint8_t>& in_identity)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setEapIdentityInternal, in_identity);
}

::ndk::ScopedAStatus StaNetwork::setEapEncryptedImsiIdentity(
	const std::vector<uint8_t>& in_identity)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setEapEncryptedImsiIdentityInternal,
		in_identity);
}

::ndk::ScopedAStatus StaNetwork::setStrictConservativePeerMode(
	bool in_enable)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setStrictConservativePeerModeInternal,
		in_enable);
}

::ndk::ScopedAStatus StaNetwork::setEapAnonymousIdentity(
	const std::vector<uint8_t>& in_identity)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setEapAnonymousIdentityInternal, in_identity);
}

::ndk::ScopedAStatus StaNetwork::setEapPassword(
	const std::vector<uint8_t>& in_password)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setEapPasswordInternal, in_password);
}

::ndk::ScopedAStatus StaNetwork::setEapCACert(
	const std::string& in_path)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setEapCACertInternal, in_path);
}

::ndk::ScopedAStatus StaNetwork::setEapCAPath(
	const std::string& in_path)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setEapCAPathInternal, in_path);
}

::ndk::ScopedAStatus StaNetwork::setEapClientCert(
	const std::string& in_path)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setEapClientCertInternal, in_path);
}

::ndk::ScopedAStatus StaNetwork::setEapPrivateKeyId(
	const std::string& in_id)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setEapPrivateKeyIdInternal, in_id);
}

::ndk::ScopedAStatus StaNetwork::setEapSubjectMatch(
	const std::string& in_match)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setEapSubjectMatchInternal, in_match);
}

::ndk::ScopedAStatus StaNetwork::setEapAltSubjectMatch(
	const std::string& in_match)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setEapAltSubjectMatchInternal, in_match);
}

::ndk::ScopedAStatus StaNetwork::setEapEngine(bool in_enable)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setEapEngineInternal, in_enable);
}

::ndk::ScopedAStatus StaNetwork::setEapEngineID(
	const std::string& in_id)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setEapEngineIDInternal, in_id);
}

::ndk::ScopedAStatus StaNetwork::setEapDomainSuffixMatch(
	const std::string& in_match)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setEapDomainSuffixMatchInternal, in_match);
}

::ndk::ScopedAStatus StaNetwork::setProactiveKeyCaching(
	bool in_enable)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setProactiveKeyCachingInternal, in_enable);
}

::ndk::ScopedAStatus StaNetwork::setIdStr(
	const std::string& in_idStr)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setIdStrInternal, in_idStr);
}

::ndk::ScopedAStatus StaNetwork::setUpdateIdentifier(
	int32_t in_id)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setUpdateIdentifierInternal, in_id);
}

::ndk::ScopedAStatus StaNetwork::setWapiCertSuite(
	const std::string& in_suite)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setWapiCertSuiteInternal, in_suite);
}

::ndk::ScopedAStatus StaNetwork::setEdmg(bool in_enable)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setEdmgInternal, in_enable);
}

::ndk::ScopedAStatus StaNetwork::getSsid(
	std::vector<uint8_t>* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getSsidInternal, _aidl_return);
}

ndk::ScopedAStatus StaNetwork::getBssid(
	std::vector<uint8_t>* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getBssidInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getScanSsid(
	bool* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getScanSsidInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getKeyMgmt(
	KeyMgmtMask* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getKeyMgmtInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getProto(
	ProtoMask* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getProtoInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getAuthAlg(
	AuthAlgMask* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getAuthAlgInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getGroupCipher(
	GroupCipherMask* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getGroupCipherInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getPairwiseCipher(
	PairwiseCipherMask* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getPairwiseCipherInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getPskPassphrase(
	std::string* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getPskPassphraseInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getPsk(
	std::vector<uint8_t>* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getPskInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getSaePassword(
	std::string* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getSaePasswordInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getSaePasswordId(
	std::string* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getSaePasswordIdInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getWepKey(
	int32_t in_keyIdx,
	std::vector<uint8_t>* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getWepKeyInternal, _aidl_return, in_keyIdx);
}

::ndk::ScopedAStatus StaNetwork::getWepTxKeyIdx(
	int32_t* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getWepTxKeyIdxInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getRequirePmf(
	bool* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getRequirePmfInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getEapMethod(
	EapMethod* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getEapMethodInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getEapPhase2Method(
	EapPhase2Method* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getEapPhase2MethodInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getEapIdentity(
	std::vector<uint8_t>* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getEapIdentityInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getEapAnonymousIdentity(
	std::vector<uint8_t>* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getEapAnonymousIdentityInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getEapPassword(
	std::vector<uint8_t>* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getEapPasswordInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getEapCACert(
	std::string* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getEapCACertInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getEapCAPath(
	std::string* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getEapCAPathInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getEapClientCert(
	std::string* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getEapClientCertInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getEapPrivateKeyId(
	std::string* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getEapPrivateKeyIdInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getEapSubjectMatch(
	std::string* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getEapSubjectMatchInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getEapAltSubjectMatch(
	std::string* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getEapAltSubjectMatchInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getEapEngine(
	bool* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getEapEngineInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getEapEngineId(
	std::string* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getEapEngineIdInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getEapDomainSuffixMatch(
	std::string* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getEapDomainSuffixMatchInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getIdStr(
	std::string* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getIdStrInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getWpsNfcConfigurationToken(
	std::vector<uint8_t>* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getWpsNfcConfigurationTokenInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getWapiCertSuite(
	std::string* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getWapiCertSuiteInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::getEdmg(
	bool* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getEdmgInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::enable(bool in_noConnect)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::enableInternal, in_noConnect);
}

::ndk::ScopedAStatus StaNetwork::disable()
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::disableInternal);
}

::ndk::ScopedAStatus StaNetwork::select()
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::selectInternal);
}

::ndk::ScopedAStatus StaNetwork::sendNetworkEapSimGsmAuthResponse(
	const std::vector<NetworkResponseEapSimGsmAuthParams>& in_params)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::sendNetworkEapSimGsmAuthResponseInternal,
		in_params);
}

::ndk::ScopedAStatus StaNetwork::sendNetworkEapSimGsmAuthFailure()
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::sendNetworkEapSimGsmAuthFailureInternal);
}

::ndk::ScopedAStatus StaNetwork::sendNetworkEapSimUmtsAuthResponse(
	const NetworkResponseEapSimUmtsAuthParams& in_params)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::sendNetworkEapSimUmtsAuthResponseInternal,
		in_params);
}

::ndk::ScopedAStatus StaNetwork::sendNetworkEapSimUmtsAutsResponse(
	const std::vector<uint8_t>& in_auts)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::sendNetworkEapSimUmtsAutsResponseInternal,
		in_auts);
}

::ndk::ScopedAStatus StaNetwork::sendNetworkEapSimUmtsAuthFailure()
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::sendNetworkEapSimUmtsAuthFailureInternal);
}

::ndk::ScopedAStatus StaNetwork::sendNetworkEapIdentityResponse(
	const std::vector<uint8_t>& in_identity,
	const std::vector<uint8_t>& in_encryptedIdentity)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::sendNetworkEapIdentityResponseInternal,
		in_identity, in_encryptedIdentity);
}

::ndk::ScopedAStatus StaNetwork::setGroupMgmtCipher(
	GroupMgmtCipherMask in_groupMgmtCipherMask)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setGroupMgmtCipherInternal,
		in_groupMgmtCipherMask);
}

::ndk::ScopedAStatus StaNetwork::getGroupMgmtCipher(
	GroupMgmtCipherMask* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getGroupMgmtCipherInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::enableTlsSuiteBEapPhase1Param(
	bool in_enable)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::enableTlsSuiteBEapPhase1ParamInternal, in_enable);
}

::ndk::ScopedAStatus StaNetwork::enableSuiteBEapOpenSslCiphers()
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::enableSuiteBEapOpenSslCiphersInternal);
}

::ndk::ScopedAStatus StaNetwork::setSaePassword(
	const std::string& in_saePassword)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setSaePasswordInternal, in_saePassword);
}

::ndk::ScopedAStatus StaNetwork::setSaePasswordId(
	const std::string& in_saePasswordId)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setSaePasswordIdInternal, in_saePasswordId);
}

::ndk::ScopedAStatus StaNetwork::setOcsp(
	OcspType in_ocspType)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setOcspInternal, in_ocspType);
}

::ndk::ScopedAStatus StaNetwork::getOcsp(
	OcspType* _aidl_return)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::getOcspInternal, _aidl_return);
}

::ndk::ScopedAStatus StaNetwork::setPmkCache(
	const std::vector<uint8_t>& in_serializedEntry)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setPmkCacheInternal, in_serializedEntry);
}

::ndk::ScopedAStatus StaNetwork::setEapErp(
	bool in_enable)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setEapErpInternal, in_enable);
}

::ndk::ScopedAStatus StaNetwork::setSaeH2eMode(
	SaeH2eMode in_mode)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setSaeH2eModeInternal, in_mode);
}

::ndk::ScopedAStatus StaNetwork::enableSaePkOnlyMode(
	bool in_enable)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::enableSaePkOnlyModeInternal, in_enable);
}

::ndk::ScopedAStatus StaNetwork::setRoamingConsortiumSelection(
	const std::vector<uint8_t>& in_selectedRcoi)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setRoamingConsortiumSelectionInternal, in_selectedRcoi);
}

::ndk::ScopedAStatus StaNetwork::setMinimumTlsVersionEapPhase1Param(
	TlsVersion in_tlsVersion)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setMinimumTlsVersionEapPhase1ParamInternal, in_tlsVersion);
}

::ndk::ScopedAStatus StaNetwork::disableEht()
{
    return validateAndCall(
            this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
                &StaNetwork::disableEhtInternal);
}

::ndk::ScopedAStatus StaNetwork::setVendorData(
	const std::vector<common::OuiKeyedData>& in_vendorData)
{
	return validateAndCall(
		this, SupplicantStatusCode::FAILURE_NETWORK_INVALID,
		&StaNetwork::setVendorDataInternal, in_vendorData);
}

std::pair<uint32_t, ndk::ScopedAStatus> StaNetwork::getIdInternal()
{
	return {network_id_, ndk::ScopedAStatus::ok()};
}

std::pair<std::string, ndk::ScopedAStatus> StaNetwork::getInterfaceNameInternal()
{
	return {ifname_, ndk::ScopedAStatus::ok()};
}

std::pair<IfaceType, ndk::ScopedAStatus> StaNetwork::getTypeInternal()
{
	return {IfaceType::STA, ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus StaNetwork::registerCallbackInternal(
	const std::shared_ptr<ISupplicantStaNetworkCallback> &callback)
{
	AidlManager *aidl_manager = AidlManager::getInstance();
	if (!aidl_manager || aidl_manager->addStaNetworkCallbackAidlObject(
				 ifname_, network_id_, callback)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setSsidInternal(const std::vector<uint8_t> &ssid)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (ssid.size() == 0 ||
		ssid.size() >
		static_cast<uint32_t>(ISupplicantStaNetwork::
					  SSID_MAX_LEN_IN_BYTES)) {
		return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);
	}
	if (setByteArrayFieldAndResetState(
		ssid.data(), ssid.size(), &(wpa_ssid->ssid),
		&(wpa_ssid->ssid_len), "ssid")) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	if (wpa_ssid->passphrase) {
		wpa_config_update_psk(wpa_ssid);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setBssidInternal(
	const std::vector<uint8_t> &bssid)
{
	if (bssid.size() != ETH_ALEN) {
		return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);
	}
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	int prev_bssid_set = wpa_ssid->bssid_set;
	u8 prev_bssid[ETH_ALEN];
	os_memcpy(prev_bssid, wpa_ssid->bssid, ETH_ALEN);
	// Zero'ed array is used to clear out the BSSID value.
	if (os_memcmp(bssid.data(), kZeroBssid, ETH_ALEN) == 0) {
		wpa_ssid->bssid_set = 0;
		wpa_printf(MSG_MSGDUMP, "BSSID any");
	} else {
		os_memcpy(wpa_ssid->bssid, bssid.data(), ETH_ALEN);
		wpa_ssid->bssid_set = 1;
		wpa_hexdump(MSG_MSGDUMP, "BSSID", wpa_ssid->bssid, ETH_ALEN);
	}
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	if ((wpa_ssid->bssid_set != prev_bssid_set ||
		 os_memcmp(wpa_ssid->bssid, prev_bssid, ETH_ALEN) != 0)) {
		wpas_notify_network_bssid_set_changed(wpa_s, wpa_ssid);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setDppKeysInternal(const DppConnectionKeys& keys)
{
#ifdef CONFIG_DPP
	if (keys.connector.empty() || keys.cSign.empty() || keys.netAccessKey.empty()) {
		return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);
	}

	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	std::string connector_str(keys.connector.begin(), keys.connector.end());

	if (setStringFieldAndResetState(
		connector_str.c_str(), &(wpa_ssid->dpp_connector), "dpp_connector")) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}

	if (setByteArrayFieldAndResetState(
		keys.cSign.data(), keys.cSign.size(), &(wpa_ssid->dpp_csign),
		&(wpa_ssid->dpp_csign_len), "dpp csign")) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}

	if (setByteArrayFieldAndResetState(
		keys.netAccessKey.data(), keys.netAccessKey.size(), &(wpa_ssid->dpp_netaccesskey),
		&(wpa_ssid->dpp_netaccesskey_len), "dpp netAccessKey")) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}

	return ndk::ScopedAStatus::ok();
#else
	return createStatus(SupplicantStatusCode::FAILURE_UNSUPPORTED);
#endif
}

ndk::ScopedAStatus StaNetwork::setScanSsidInternal(bool enable)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	wpa_ssid->scan_ssid = enable ? 1 : 0;
	resetInternalStateAfterParamsUpdate();
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setAuthAlgInternal(
	AuthAlgMask mask)
{
	uint32_t auth_alg_mask = static_cast<uint32_t>(mask);
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (auth_alg_mask & ~kAllowedAuthAlgMask) {
		return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);
	}
	wpa_ssid->auth_alg = auth_alg_mask;
	wpa_printf(MSG_MSGDUMP, "auth_alg: 0x%x", wpa_ssid->auth_alg);
	resetInternalStateAfterParamsUpdate();
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setEdmgInternal(bool enable)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	wpa_ssid->enable_edmg = enable ? 1 : 0;
	resetInternalStateAfterParamsUpdate();
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setPskPassphraseInternal(const std::string &rawPsk)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	std::string psk = rawPsk;
#ifdef CONFIG_WAPI_INTERFACE
	if (wpa_ssid->key_mgmt & WPA_KEY_MGMT_WAPI_PSK) {
		if (rawPsk.size() > 2 && rawPsk.front()== '"' && rawPsk.back() == '"') {
			psk = rawPsk.substr(1, rawPsk.size() - 2);
		} else {
			if ((rawPsk.size() & 1)) {
				return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);
			}
			size_t len = psk.size() / 2;
			uint8_t *buf = (uint8_t *) os_malloc(len);
			if (hexstr2bin(psk.c_str(), buf, len) < 0) {
					os_free(buf);
				return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);
			}
			std::vector<uint8_t> bytes(buf, buf + len);
			os_free(buf);
			return setWapiPskInternal(bytes);
		}
	}
#endif
	if (isPskPassphraseValid(psk)) {
		return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);
	}
	if (wpa_ssid->passphrase &&
		os_strlen(wpa_ssid->passphrase) == psk.size() &&
		os_memcmp(wpa_ssid->passphrase, psk.c_str(), psk.size()) == 0) {
		return ndk::ScopedAStatus::ok();
	}
	// Flag to indicate if raw psk is calculated or not using
	// |wpa_config_update_psk|. Deferred if ssid not already set.
	wpa_ssid->psk_set = 0;
	if (setStringKeyFieldAndResetState(
		psk.c_str(), &(wpa_ssid->passphrase), "psk passphrase")) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	if (wpa_ssid->ssid_len) {
		wpa_config_update_psk(wpa_ssid);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setPskInternal(const std::vector<uint8_t> &psk)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	WPA_ASSERT(psk.size() == sizeof(wpa_ssid->psk));
	str_clear_free(wpa_ssid->passphrase);
	wpa_ssid->passphrase = nullptr;
	os_memcpy(wpa_ssid->psk, psk.data(), sizeof(wpa_ssid->psk));
	wpa_ssid->psk_set = 1;
	resetInternalStateAfterParamsUpdate();
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setWepKeyInternal(
	uint32_t key_idx, const std::vector<uint8_t> &wep_key)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (key_idx >=
		static_cast<uint32_t>(
		ISupplicantStaNetwork::WEP_KEYS_MAX_NUM)) {
		return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);
	}
	if (wep_key.size() !=
		static_cast<uint32_t>(ISupplicantStaNetwork::
					  WEP40_KEY_LEN_IN_BYTES) &&
		wep_key.size() !=
		static_cast<uint32_t>(ISupplicantStaNetwork::
					  WEP104_KEY_LEN_IN_BYTES)) {
		return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);
	}
	os_memcpy(wpa_ssid->wep_key[key_idx], wep_key.data(), wep_key.size());
	wpa_ssid->wep_key_len[key_idx] = wep_key.size();
	std::string msg_dump_title("wep_key" + std::to_string(key_idx));
	wpa_hexdump_key(
		MSG_MSGDUMP, msg_dump_title.c_str(), wpa_ssid->wep_key[key_idx],
		wpa_ssid->wep_key_len[key_idx]);
	resetInternalStateAfterParamsUpdate();
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setWepTxKeyIdxInternal(uint32_t key_idx)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (key_idx >=
		static_cast<uint32_t>(
		ISupplicantStaNetwork::WEP_KEYS_MAX_NUM)) {
		return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);
	}
	wpa_ssid->wep_tx_keyidx = key_idx;
	resetInternalStateAfterParamsUpdate();
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setRequirePmfInternal(bool enable)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (enable) {
		wpa_ssid->ieee80211w = MGMT_FRAME_PROTECTION_REQUIRED;
	}
	resetInternalStateAfterParamsUpdate();
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setEapMethodInternal(
	EapMethod method)
{
	uint32_t eap_method_idx = static_cast<
		std::underlying_type<EapMethod>::type>(
		method);
	if (eap_method_idx >= kEapMethodMax) {
		return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);
	}

	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	int retrieved_vendor, retrieved_method;
	const char *method_str = kEapMethodStrings[eap_method_idx];
	// This string lookup is needed to check if the device supports the
	// corresponding EAP type.
	retrieved_method = eap_peer_get_type(method_str, &retrieved_vendor);
	if (retrieved_vendor == EAP_VENDOR_IETF &&
		retrieved_method == EAP_TYPE_NONE) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	if (wpa_ssid->eap.eap_methods) {
		os_free(wpa_ssid->eap.eap_methods);
	}
	// wpa_supplicant can support setting multiple eap methods for each
	// network. But, this is not really used by Android. So, just adding
	// support for setting one EAP method for each network. The additional
	// |eap_method_type| member in the array is used to indicate the end
	// of list.
	wpa_ssid->eap.eap_methods =
		(eap_method_type *)os_malloc(sizeof(eap_method_type) * 2);
	if (!wpa_ssid->eap.eap_methods) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	wpa_ssid->eap.eap_methods[0].vendor = retrieved_vendor;
	wpa_ssid->eap.eap_methods[0].method = retrieved_method;
	wpa_ssid->eap.eap_methods[1].vendor = EAP_VENDOR_IETF;
	wpa_ssid->eap.eap_methods[1].method = EAP_TYPE_NONE;

	wpa_ssid->leap = 0;
	wpa_ssid->non_leap = 0;
	if (retrieved_vendor == EAP_VENDOR_IETF &&
		retrieved_method == EAP_TYPE_LEAP) {
		wpa_ssid->leap++;
	} else {
		wpa_ssid->non_leap++;
	}
	wpa_hexdump(
		MSG_MSGDUMP, "eap methods", (u8 *)wpa_ssid->eap.eap_methods,
		sizeof(eap_method_type) * 2);
	resetInternalStateAfterParamsUpdate();
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setEapPhase2MethodInternal(
	EapPhase2Method method)
{
	uint32_t eap_phase2_method_idx = static_cast<
		std::underlying_type<EapPhase2Method>::type>(
		method);
	if (eap_phase2_method_idx >= kEapPhase2MethodMax) {
		return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);
	}

	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	// EAP method needs to be set for us to construct the eap
	// phase 2 method string.
	ndk::ScopedAStatus status;
	EapMethod eap_method;
	std::tie(eap_method, status) = getEapMethodInternal();
	if (!status.isOk()) {
		return createStatusWithMsg(SupplicantStatusCode::FAILURE_UNKNOWN,
			"EAP method not set");
	}
	std::string eap_phase2_str;
	if (method == EapPhase2Method::NONE) {
		eap_phase2_str = "";
	} else if (
		eap_method == EapMethod::TTLS &&
		method == EapPhase2Method::GTC) {
		eap_phase2_str = kEapPhase2AuthEapPrefix;
	} else {
		eap_phase2_str = kEapPhase2AuthPrefix;
	}
	eap_phase2_str += kEapPhase2MethodStrings[eap_phase2_method_idx];
	if (setStringFieldAndResetState(
		eap_phase2_str.c_str(), &(wpa_ssid->eap.phase2),
		"eap phase2")) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setEapIdentityInternal(
	const std::vector<uint8_t> &identity)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (setByteArrayFieldAndResetState(
		identity.data(), identity.size(), &(wpa_ssid->eap.identity),
		&(wpa_ssid->eap.identity_len), "eap identity")) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	// plain IMSI identity
	if (setByteArrayFieldAndResetState(
		identity.data(), identity.size(),
		&(wpa_ssid->eap.imsi_identity),
		&(wpa_ssid->eap.imsi_identity_len), "eap imsi identity")) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setEapEncryptedImsiIdentityInternal(
	const std::vector<uint8_t> &identity)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	// encrypted IMSI identity
	if (setByteArrayFieldAndResetState(
		identity.data(), identity.size(), &(wpa_ssid->eap.identity),
		&(wpa_ssid->eap.identity_len), "eap encrypted imsi identity")) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setStrictConservativePeerModeInternal(bool enable)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	wpa_ssid->eap.strict_conservative_peer_mode = enable ? 1 : 0;
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setEapAnonymousIdentityInternal(
	const std::vector<uint8_t> &identity)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	// If current supplicant pseudonym is the prefix of new pseudonym,
	// the credential is not changed, just update the decoration.
	// As a result, no need to reset the state.
	// The decorated identity will have a postfix like
	// @mncXXX.mccYYY.3gppnetwork.org, so the length will be always
	// greater than the current one.
	bool resetState = wpa_ssid->eap.anonymous_identity == NULL
		|| wpa_ssid->eap.anonymous_identity_len == 0
		|| identity.size() == 0
		|| wpa_ssid->eap.anonymous_identity_len >= identity.size()
		|| os_strncmp((char *) identity.data(),
			(char *) wpa_ssid->eap.anonymous_identity,
			wpa_ssid->eap.anonymous_identity_len) != 0;
	if (setByteArrayField(
		identity.data(), identity.size(),
		&(wpa_ssid->eap.anonymous_identity),
		&(wpa_ssid->eap.anonymous_identity_len),
		"eap anonymous_identity", resetState)) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setEapPasswordInternal(
	const std::vector<uint8_t> &password)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (setByteArrayKeyFieldAndResetState(
		password.data(), password.size(), &(wpa_ssid->eap.password),
		&(wpa_ssid->eap.password_len), "eap password")) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	wpa_ssid->eap.flags &= ~EAP_CONFIG_FLAGS_PASSWORD_NTHASH;
	wpa_ssid->eap.flags &= ~EAP_CONFIG_FLAGS_EXT_PASSWORD;
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setEapCACertInternal(const std::string &path)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (setStringFieldAndResetState(
		path.c_str(), &(wpa_ssid->eap.cert.ca_cert), "eap ca_cert")) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setEapCAPathInternal(const std::string &path)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (setStringFieldAndResetState(
		path.c_str(), &(wpa_ssid->eap.cert.ca_path), "eap ca_path")) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setEapClientCertInternal(const std::string &path)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (setStringFieldAndResetState(
		path.c_str(), &(wpa_ssid->eap.cert.client_cert),
		"eap client_cert")) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setEapPrivateKeyIdInternal(const std::string &id)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (setStringFieldAndResetState(
		id.c_str(), &(wpa_ssid->eap.cert.key_id), "eap key_id")) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setEapSubjectMatchInternal(
	const std::string &match)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (setStringFieldAndResetState(
		match.c_str(), &(wpa_ssid->eap.cert.subject_match),
		"eap subject_match")) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setEapAltSubjectMatchInternal(
	const std::string &match)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (setStringFieldAndResetState(
		match.c_str(), &(wpa_ssid->eap.cert.altsubject_match),
		"eap altsubject_match")) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setEapEngineInternal(bool enable)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	wpa_ssid->eap.cert.engine = enable ? 1 : 0;
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setEapEngineIDInternal(const std::string &id)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (setStringFieldAndResetState(
		id.c_str(), &(wpa_ssid->eap.cert.engine_id), "eap engine_id")) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setEapDomainSuffixMatchInternal(
	const std::string &match)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (setStringFieldAndResetState(
		match.c_str(), &(wpa_ssid->eap.cert.domain_suffix_match),
		"eap domain_suffix_match")) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setProactiveKeyCachingInternal(bool enable)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	wpa_ssid->proactive_key_caching = enable ? 1 : 0;
	resetInternalStateAfterParamsUpdate();
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setIdStrInternal(const std::string &id_str)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (setStringFieldAndResetState(
		id_str.c_str(), &(wpa_ssid->id_str), "id_str")) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setUpdateIdentifierInternal(uint32_t id)
{
#ifdef CONFIG_HS20
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	wpa_ssid->update_identifier = id;
	wpa_printf(
		MSG_MSGDUMP, "update_identifier: %d", wpa_ssid->update_identifier);
	resetInternalStateAfterParamsUpdate();
	return ndk::ScopedAStatus::ok();
#else
	return createStatusWithMsg(SupplicantStatusCode::FAILURE_UNKNOWN, "Not implemented");
#endif /* CONFIG_HS20 */
}

ndk::ScopedAStatus StaNetwork::setWapiCertSuiteInternal(const std::string &suite)
{
#ifdef CONFIG_WAPI_INTERFACE
	// Dummy implementation
	dummyWapiCertSuite = suite;
	return ndk::ScopedAStatus::ok();
#else
	return createStatusWithMsg(SupplicantStatusCode::FAILURE_UNKNOWN, "Not implemented");
#endif
}

ndk::ScopedAStatus StaNetwork::setWapiPskInternal(const std::vector<uint8_t> &psk)
{
#ifdef CONFIG_WAPI_INTERFACE
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	str_clear_free(wpa_ssid->passphrase);
	wpa_ssid->passphrase = nullptr;

	// Dummy implementation
	dummyWapiPsk = psk;

	wpa_ssid->psk_set = 1;
	resetInternalStateAfterParamsUpdate();
	return ndk::ScopedAStatus::ok();
#else
	return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
#endif
}

std::pair<std::vector<uint8_t>, ndk::ScopedAStatus> StaNetwork::getSsidInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	std::vector<uint8_t> ssid(
		wpa_ssid->ssid,
		wpa_ssid->ssid + wpa_ssid->ssid_len);
	return {std::move(ssid), ndk::ScopedAStatus::ok()};
}

std::pair<std::vector<uint8_t>, ndk::ScopedAStatus>
StaNetwork::getBssidInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	std::vector<uint8_t> bssid(kZeroBssid, kZeroBssid + ETH_ALEN);
	if (wpa_ssid->bssid_set) {
		bssid.assign(wpa_ssid->bssid, wpa_ssid->bssid + ETH_ALEN);
	}
	return {std::move(bssid), ndk::ScopedAStatus::ok()};
}

std::pair<bool, ndk::ScopedAStatus> StaNetwork::getScanSsidInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	return {(wpa_ssid->scan_ssid == 1), ndk::ScopedAStatus::ok()};
}

std::pair<AuthAlgMask, ndk::ScopedAStatus>
StaNetwork::getAuthAlgInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	uint32_t auth_alg_mask = wpa_ssid->auth_alg & kAllowedAuthAlgMask;
	return {static_cast<AuthAlgMask>(auth_alg_mask), ndk::ScopedAStatus::ok()};
}

std::pair<std::string, ndk::ScopedAStatus> StaNetwork::getPskPassphraseInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
#ifdef CONFIG_WAPI_INTERFACE
	if (wpa_ssid->key_mgmt & WPA_KEY_MGMT_WAPI_PSK) {
		if (wpa_ssid->psk_set) {
			std::pair<std::vector<uint8_t>, ndk::ScopedAStatus> ret = getWapiPskInternal();
			std::string psk;
			char buf[3] = {0};
			for (int i = 0; i < ret.second.size(); i++) {
				snprintf(buf, sizeof(buf), "%02x", ret.second[i]);
				psk.append(buf);
			}
			return {psk, ndk::ScopedAStatus::ok()};
		} else {
			if (!wpa_ssid->passphrase) {
				return {"", createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
			}
			std::string passphrase;
			passphrase.append("\"");
			passphrase.append(wpa_ssid->passphrase);
			passphrase.append("\"");
			return {passphrase, ndk::ScopedAStatus::ok()};
		}
	}
#endif
	if (!wpa_ssid->passphrase) {
		return {"", createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {wpa_ssid->passphrase, ndk::ScopedAStatus::ok()};
}

std::pair<std::vector<uint8_t>, ndk::ScopedAStatus>
StaNetwork::getPskInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	WPA_ASSERT(psk.size() == sizeof(wpa_ssid->psk));
	if (!wpa_ssid->psk_set) {
		return {std::vector<uint8_t>(),
			createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	std::vector<uint8_t> psk(wpa_ssid->psk, wpa_ssid->psk + 32);
	return {psk, ndk::ScopedAStatus::ok()};
}

std::pair<std::string, ndk::ScopedAStatus> StaNetwork::getSaePasswordInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (!wpa_ssid->sae_password) {
		return {"", createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {misc_utils::charBufToString(wpa_ssid->sae_password),
		ndk::ScopedAStatus::ok()};
}

std::pair<std::string, ndk::ScopedAStatus> StaNetwork::getSaePasswordIdInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (!wpa_ssid->sae_password_id) {
		return {"", createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {misc_utils::charBufToString(wpa_ssid->sae_password_id),
		ndk::ScopedAStatus::ok()};
}

std::pair<std::vector<uint8_t>, ndk::ScopedAStatus> StaNetwork::getWepKeyInternal(
	uint32_t key_idx)
{
	std::vector<uint8_t> wep_key;
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (key_idx >=
		static_cast<uint32_t>(
		ISupplicantStaNetwork::WEP_KEYS_MAX_NUM)) {
		return {wep_key,
			createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID)};
	}
	wep_key.assign(
		wpa_ssid->wep_key[key_idx],
		wpa_ssid->wep_key[key_idx] + wpa_ssid->wep_key_len[key_idx]);
	return {std::move(wep_key), ndk::ScopedAStatus::ok()};
}

std::pair<uint32_t, ndk::ScopedAStatus> StaNetwork::getWepTxKeyIdxInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	return {wpa_ssid->wep_tx_keyidx, ndk::ScopedAStatus::ok()};
}

std::pair<bool, ndk::ScopedAStatus> StaNetwork::getRequirePmfInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	return {(wpa_ssid->ieee80211w == MGMT_FRAME_PROTECTION_REQUIRED),
		ndk::ScopedAStatus::ok()};
}

std::pair<EapMethod, ndk::ScopedAStatus>
StaNetwork::getEapMethodInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (!wpa_ssid->eap.eap_methods) {
		return {static_cast<EapMethod>(0),
			createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	// wpa_supplicant can support setting multiple eap methods for each
	// network. But, this is not really used by Android. So, just reading
	// the first EAP method for each network.
	const std::string eap_method_str = eap_get_name(
		wpa_ssid->eap.eap_methods[0].vendor,
		static_cast<enum eap_type>(wpa_ssid->eap.eap_methods[0].method));
	size_t eap_method_idx =
		std::find(
		std::begin(kEapMethodStrings), std::end(kEapMethodStrings),
		eap_method_str) -
		std::begin(kEapMethodStrings);
	if (eap_method_idx >= kEapMethodMax) {
		return {static_cast<EapMethod>(0),
			createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {static_cast<EapMethod>(eap_method_idx), ndk::ScopedAStatus::ok()};
}

std::pair<EapPhase2Method, ndk::ScopedAStatus>
StaNetwork::getEapPhase2MethodInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (!wpa_ssid->eap.phase2) {
		return {static_cast<EapPhase2Method>(0),
			createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	const std::string eap_phase2_method_str_with_prefix =
		wpa_ssid->eap.phase2;
	std::string eap_phase2_method_str;
	// Strip out the phase 2 method prefix before doing a reverse lookup
	// of phase 2 string to the Eap Phase 2 type.
	if (eap_phase2_method_str_with_prefix.find(kEapPhase2AuthPrefix) == 0) {
		eap_phase2_method_str =
			eap_phase2_method_str_with_prefix.substr(
			strlen(kEapPhase2AuthPrefix),
			eap_phase2_method_str_with_prefix.size());
	} else if (
		eap_phase2_method_str_with_prefix.find(kEapPhase2AuthEapPrefix) ==
		0) {
		eap_phase2_method_str =
			eap_phase2_method_str_with_prefix.substr(
			strlen(kEapPhase2AuthEapPrefix),
			eap_phase2_method_str_with_prefix.size());
	}
	size_t eap_phase2_method_idx =
		std::find(
		std::begin(kEapPhase2MethodStrings),
		std::end(kEapPhase2MethodStrings), eap_phase2_method_str) -
		std::begin(kEapPhase2MethodStrings);
	if (eap_phase2_method_idx >= kEapPhase2MethodMax) {
		return {static_cast<EapPhase2Method>(0),
			createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {static_cast<EapPhase2Method>(eap_phase2_method_idx),
		ndk::ScopedAStatus::ok()};
}

std::pair<std::vector<uint8_t>, ndk::ScopedAStatus>
StaNetwork::getEapIdentityInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (!wpa_ssid->eap.identity) {
		return {std::vector<uint8_t>(),
			createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {std::vector<uint8_t>(
			wpa_ssid->eap.identity,
			wpa_ssid->eap.identity + wpa_ssid->eap.identity_len),
		ndk::ScopedAStatus::ok()};
}

std::pair<std::vector<uint8_t>, ndk::ScopedAStatus>
StaNetwork::getEapAnonymousIdentityInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (!wpa_ssid->eap.anonymous_identity) {
		return {std::vector<uint8_t>(),
			createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {std::vector<uint8_t>(
			wpa_ssid->eap.anonymous_identity,
			wpa_ssid->eap.anonymous_identity +
			wpa_ssid->eap.anonymous_identity_len),
		ndk::ScopedAStatus::ok()};
}

std::pair<std::vector<uint8_t>, ndk::ScopedAStatus>
StaNetwork::getEapPasswordInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (!wpa_ssid->eap.password) {
		return {std::vector<uint8_t>(), createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {std::vector<uint8_t>(
			wpa_ssid->eap.password,
			wpa_ssid->eap.password + wpa_ssid->eap.password_len),
		ndk::ScopedAStatus::ok()};
}

std::pair<std::string, ndk::ScopedAStatus> StaNetwork::getEapCACertInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (!wpa_ssid->eap.cert.ca_cert) {
		return {"", createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {misc_utils::charBufToString(wpa_ssid->eap.cert.ca_cert),
		ndk::ScopedAStatus::ok()};
}

std::pair<std::string, ndk::ScopedAStatus> StaNetwork::getEapCAPathInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (!wpa_ssid->eap.cert.ca_path) {
		return {"", createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {misc_utils::charBufToString(wpa_ssid->eap.cert.ca_path),
		ndk::ScopedAStatus::ok()};
}

std::pair<std::string, ndk::ScopedAStatus> StaNetwork::getEapClientCertInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (!wpa_ssid->eap.cert.client_cert) {
		return {"", createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {misc_utils::charBufToString(wpa_ssid->eap.cert.client_cert),
		ndk::ScopedAStatus::ok()};
}

std::pair<std::string, ndk::ScopedAStatus>
StaNetwork::getEapPrivateKeyIdInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (!wpa_ssid->eap.cert.key_id) {
		return {"", createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {misc_utils::charBufToString(reinterpret_cast<char *>(wpa_ssid->eap.cert.key_id)),
		ndk::ScopedAStatus::ok()};
}

std::pair<std::string, ndk::ScopedAStatus>
StaNetwork::getEapSubjectMatchInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (!wpa_ssid->eap.cert.subject_match) {
		return {"", createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {misc_utils::charBufToString(wpa_ssid->eap.cert.subject_match),
		ndk::ScopedAStatus::ok()};
}

std::pair<std::string, ndk::ScopedAStatus>
StaNetwork::getEapAltSubjectMatchInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (!wpa_ssid->eap.cert.altsubject_match) {
		return {"", createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {misc_utils::charBufToString(wpa_ssid->eap.cert.altsubject_match),
		ndk::ScopedAStatus::ok()};
}

std::pair<bool, ndk::ScopedAStatus> StaNetwork::getEapEngineInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	return {wpa_ssid->eap.cert.engine == 1, ndk::ScopedAStatus::ok()};
}

std::pair<std::string, ndk::ScopedAStatus> StaNetwork::getEapEngineIdInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (!wpa_ssid->eap.cert.engine_id) {
		return {"", createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {misc_utils::charBufToString(wpa_ssid->eap.cert.engine_id),
		ndk::ScopedAStatus::ok()};
}

std::pair<std::string, ndk::ScopedAStatus>
StaNetwork::getEapDomainSuffixMatchInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (!wpa_ssid->eap.cert.domain_suffix_match) {
		return {"", createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {misc_utils::charBufToString(wpa_ssid->eap.cert.domain_suffix_match),
		ndk::ScopedAStatus::ok()};
}

std::pair<std::string, ndk::ScopedAStatus> StaNetwork::getIdStrInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (!wpa_ssid->id_str) {
		return {"", createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {misc_utils::charBufToString(wpa_ssid->id_str),
		ndk::ScopedAStatus::ok()};
}

std::pair<bool, ndk::ScopedAStatus> StaNetwork::getEdmgInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	return {(wpa_ssid->enable_edmg == 1), ndk::ScopedAStatus::ok()};
}

std::pair<std::vector<uint8_t>, ndk::ScopedAStatus>
StaNetwork::getWpsNfcConfigurationTokenInternal()
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	auto token_buf = misc_utils::createWpaBufUniquePtr(
		wpas_wps_network_config_token(wpa_s, 0, wpa_ssid));
	if (!token_buf) {
		return {std::vector<uint8_t>(),
			createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
	}
	return {misc_utils::convertWpaBufToVector(token_buf.get()),
		ndk::ScopedAStatus::ok()};
}

std::pair<std::string, ndk::ScopedAStatus> StaNetwork::getWapiCertSuiteInternal()
{
#ifdef CONFIG_WAPI_INTERFACE
	// Dummy implementation
	return {dummyWapiCertSuite, ndk::ScopedAStatus::ok()};
#else
	return {"", createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
#endif
}

std::pair<std::vector<uint8_t>, ndk::ScopedAStatus> StaNetwork::getWapiPskInternal()
{
#ifdef CONFIG_WAPI_INTERFACE
	// Dummy implementation
	return {dummyWapiPsk, ndk::ScopedAStatus::ok()};
#else
	return {std::vector<uint8_t>(),
		createStatus(SupplicantStatusCode::FAILURE_UNKNOWN)};
#endif
}

ndk::ScopedAStatus StaNetwork::enableInternal(bool no_connect)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (wpa_ssid->disabled == 2) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	if (no_connect) {
		wpa_ssid->disabled = 0;
	} else {
		wpa_s->scan_min_time.sec = 0;
		wpa_s->scan_min_time.usec = 0;
		wpa_supplicant_enable_network(wpa_s, wpa_ssid);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::disableInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (wpa_ssid->disabled == 2) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	wpa_supplicant_disable_network(wpa_s, wpa_ssid);
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::selectInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (wpa_ssid->disabled == 2) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	wpa_s->scan_min_time.sec = 0;
	wpa_s->scan_min_time.usec = 0;
	wpa_supplicant_select_network(wpa_s, wpa_ssid);
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::sendNetworkEapSimGsmAuthResponseInternal(
	const std::vector<NetworkResponseEapSimGsmAuthParams>
	&vec_params)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	// Convert the incoming parameters to a string to pass to
	// wpa_supplicant.
	std::string ctrl_rsp_param = std::string(kNetworkEapSimGsmAuthResponse);
	for (const auto &params : vec_params) {
		uint32_t kc_hex_len = params.kc.size() * 2 + 1;
		std::vector<char> kc_hex(kc_hex_len);
		uint32_t sres_hex_len = params.sres.size() * 2 + 1;
		std::vector<char> sres_hex(sres_hex_len);
		wpa_snprintf_hex(
			kc_hex.data(), kc_hex.size(), params.kc.data(),
			params.kc.size());
		wpa_snprintf_hex(
			sres_hex.data(), sres_hex.size(), params.sres.data(),
			params.sres.size());
		ctrl_rsp_param += ":" + std::string(kc_hex.data()) + ":" +
				  std::string(sres_hex.data());
	}
	enum wpa_ctrl_req_type rtype = WPA_CTRL_REQ_SIM;
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	if (wpa_supplicant_ctrl_rsp_handle(
		wpa_s, wpa_ssid, rtype, ctrl_rsp_param.c_str(),
		ctrl_rsp_param.size())) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	eapol_sm_notify_ctrl_response(wpa_s->eapol);
	wpa_hexdump_ascii_key(
		MSG_DEBUG, "network sim gsm auth response param",
		(const u8 *)ctrl_rsp_param.c_str(), ctrl_rsp_param.size());
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::sendNetworkEapSimGsmAuthFailureInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	enum wpa_ctrl_req_type rtype = WPA_CTRL_REQ_SIM;
	if (wpa_supplicant_ctrl_rsp_handle(
		wpa_s, wpa_ssid, rtype, kNetworkEapSimGsmAuthFailure,
		strlen(kNetworkEapSimGsmAuthFailure))) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	eapol_sm_notify_ctrl_response(wpa_s->eapol);
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::sendNetworkEapSimUmtsAuthResponseInternal(
	const NetworkResponseEapSimUmtsAuthParams &params)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	// Convert the incoming parameters to a string to pass to
	// wpa_supplicant.
	uint32_t ik_hex_len = params.ik.size() * 2 + 1;
	std::vector<char> ik_hex(ik_hex_len);
	uint32_t ck_hex_len = params.ck.size() * 2 + 1;
	std::vector<char> ck_hex(ck_hex_len);
	uint32_t res_hex_len = params.res.size() * 2 + 1;
	std::vector<char> res_hex(res_hex_len);
	wpa_snprintf_hex(
		ik_hex.data(), ik_hex.size(), params.ik.data(), params.ik.size());
	wpa_snprintf_hex(
		ck_hex.data(), ck_hex.size(), params.ck.data(), params.ck.size());
	wpa_snprintf_hex(
		res_hex.data(), res_hex.size(), params.res.data(),
		params.res.size());
	std::string ctrl_rsp_param =
		std::string(kNetworkEapSimUmtsAuthResponse) + ":" +
		std::string(ik_hex.data()) + ":" + std::string(ck_hex.data()) +
		":" + std::string(res_hex.data());
	enum wpa_ctrl_req_type rtype = WPA_CTRL_REQ_SIM;
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	if (wpa_supplicant_ctrl_rsp_handle(
		wpa_s, wpa_ssid, rtype, ctrl_rsp_param.c_str(),
		ctrl_rsp_param.size())) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	eapol_sm_notify_ctrl_response(wpa_s->eapol);
	wpa_hexdump_ascii_key(
		MSG_DEBUG, "network sim umts auth response param",
		(const u8 *)ctrl_rsp_param.c_str(), ctrl_rsp_param.size());
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::sendNetworkEapSimUmtsAutsResponseInternal(
	const std::vector<uint8_t> &auts)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	uint32_t auts_hex_len = auts.size() * 2 + 1;
	std::vector<char> auts_hex(auts_hex_len);
	wpa_snprintf_hex(
		auts_hex.data(), auts_hex.size(), auts.data(), auts.size());
	std::string ctrl_rsp_param =
		std::string(kNetworkEapSimUmtsAutsResponse) + ":" +
		std::string(auts_hex.data());
	enum wpa_ctrl_req_type rtype = WPA_CTRL_REQ_SIM;
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	if (wpa_supplicant_ctrl_rsp_handle(
		wpa_s, wpa_ssid, rtype, ctrl_rsp_param.c_str(),
		ctrl_rsp_param.size())) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	eapol_sm_notify_ctrl_response(wpa_s->eapol);
	wpa_hexdump_ascii_key(
		MSG_DEBUG, "network sim umts auts response param",
		(const u8 *)ctrl_rsp_param.c_str(), ctrl_rsp_param.size());
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::sendNetworkEapSimUmtsAuthFailureInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	enum wpa_ctrl_req_type rtype = WPA_CTRL_REQ_SIM;
	if (wpa_supplicant_ctrl_rsp_handle(
		wpa_s, wpa_ssid, rtype, kNetworkEapSimUmtsAuthFailure,
		strlen(kNetworkEapSimUmtsAuthFailure))) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	eapol_sm_notify_ctrl_response(wpa_s->eapol);
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::sendNetworkEapIdentityResponseInternal(
	const std::vector<uint8_t> &identity,
	const std::vector<uint8_t> &encrypted_imsi_identity)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	std::string ctrl_rsp_param(identity.begin(), identity.end());
	// If encrypted identity is included, format is:
	// plain identity + ":" + encrypted_identity
	if (encrypted_imsi_identity.size() != 0) {
		ctrl_rsp_param += ":" + std::string(
			encrypted_imsi_identity.begin(), encrypted_imsi_identity.end());
	}
	enum wpa_ctrl_req_type rtype = WPA_CTRL_REQ_EAP_IDENTITY;
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	if (wpa_supplicant_ctrl_rsp_handle(
		wpa_s, wpa_ssid, rtype, ctrl_rsp_param.c_str(),
		ctrl_rsp_param.size())) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	eapol_sm_notify_ctrl_response(wpa_s->eapol);
	wpa_hexdump_ascii_key(
		MSG_DEBUG, "network identity response param",
		(const u8 *)ctrl_rsp_param.c_str(), ctrl_rsp_param.size());
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::enableTlsSuiteBEapPhase1ParamInternal(bool enable)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	int val = enable == true ? 1 : 0;
	if (enable) {
		setTlsFlagsFor192BitMode(true /*rsaMode */);
	} else {
		tlsFlags &= ~TLS_CONN_SUITEB;
	}
	std::string phase1_params("tls_suiteb=" + std::to_string(val));
	if (wpa_ssid->eap.phase1 != NULL) {
		phase1_params.append(wpa_ssid->eap.phase1);
	}

	if (setStringKeyFieldAndResetState(
		phase1_params.c_str(), &(wpa_ssid->eap.phase1), "phase1")) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::enableSuiteBEapOpenSslCiphersInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	const char openssl_suiteb_cipher[] = "SUITEB192";

	if (setStringKeyFieldAndResetState(
		openssl_suiteb_cipher, &(wpa_ssid->eap.openssl_ciphers),
		"openssl_ciphers")) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	setTlsFlagsFor192BitMode(false /*rsaMode */);
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setSaePasswordInternal(
	const std::string &sae_password)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (sae_password.length() < 1) {
		return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);
	}
	if (wpa_ssid->sae_password &&
		os_strlen(wpa_ssid->sae_password) == sae_password.length() &&
		os_memcmp(
		wpa_ssid->sae_password, sae_password.c_str(),
		sae_password.length()) == 0) {
		return ndk::ScopedAStatus::ok();
	}
	wpa_ssid->psk_set = 1;
	if (setStringKeyFieldAndResetState(
		sae_password.c_str(), &(wpa_ssid->sae_password),
		"sae password")) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setSaePasswordIdInternal(
	const std::string &sae_password_id)
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (sae_password_id.length() < 1) {
		return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);
	}
	if (wpa_ssid->sae_password_id &&
		os_strlen(wpa_ssid->sae_password_id) == sae_password_id.length() &&
		os_memcmp(
		wpa_ssid->sae_password_id, sae_password_id.c_str(),
		sae_password_id.length()) == 0) {
		return ndk::ScopedAStatus::ok();
	}
	wpa_ssid->psk_set = 1;
	if (setStringKeyFieldAndResetState(
		sae_password_id.c_str(), &(wpa_ssid->sae_password_id),
		"sae password id")) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setGroupMgmtCipherInternal(
		GroupMgmtCipherMask mask)
{
	uint32_t group_mgmt_cipher_mask = static_cast<uint32_t>(mask);
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (group_mgmt_cipher_mask & ~kAllowedGroupMgmtCipherMask) {
		return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);
	}
	wpa_ssid->group_mgmt_cipher = group_mgmt_cipher_mask;
	wpa_printf(MSG_MSGDUMP, "group_mgmt_cipher: 0x%x",
			wpa_ssid->group_mgmt_cipher);
	resetInternalStateAfterParamsUpdate();
	return ndk::ScopedAStatus::ok();
}

std::pair<GroupMgmtCipherMask, ndk::ScopedAStatus>
StaNetwork::getGroupMgmtCipherInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	uint32_t group_mgmt_cipher_mask =
			wpa_ssid->group_mgmt_cipher & kAllowedGroupMgmtCipherMask;
	return {static_cast<GroupMgmtCipherMask>(group_mgmt_cipher_mask),
		ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus StaNetwork::setOcspInternal(OcspType ocspType) {
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (ocspType < OcspType::NONE || ocspType > OcspType::REQUIRE_ALL_CERTS_STATUS) {
		return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);
	}
	wpa_ssid->eap.cert.ocsp = (int) ocspType;
	wpa_printf(
		MSG_MSGDUMP, "ocsp: %d", wpa_ssid->eap.cert.ocsp);
	resetInternalStateAfterParamsUpdate();
	return ndk::ScopedAStatus::ok();
}

std::pair<OcspType, ndk::ScopedAStatus> StaNetwork::getOcspInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	return {static_cast<OcspType>(wpa_ssid->eap.cert.ocsp),
		ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus StaNetwork::setPmkCacheInternal(const std::vector<uint8_t>& serializedEntry) {
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	struct rsn_pmksa_cache_entry *new_entry = NULL;

	new_entry = (struct rsn_pmksa_cache_entry *) os_zalloc(sizeof(*new_entry));
	if (!new_entry) {
		return createStatusWithMsg(SupplicantStatusCode::FAILURE_UNKNOWN,
			"Allocating memory failed");
	}

	std::stringstream ss(
		std::stringstream::in | std::stringstream::out | std::stringstream::binary);
	ss.write((char *) serializedEntry.data(), std::streamsize(serializedEntry.size()));
	if (misc_utils::deserializePmkCacheEntry(ss, new_entry) < 0) {
		os_free(new_entry);
		return createStatusWithMsg(SupplicantStatusCode::FAILURE_ARGS_INVALID,
		 "Invalid pmk length");
	}
	new_entry->network_ctx = wpa_ssid;

	// If there is an entry has a later expiration, ignore this one.
	struct rsn_pmksa_cache_entry *existing_entry = wpa_sm_pmksa_cache_get(
		wpa_s->wpa, new_entry->aa, NULL, NULL, new_entry->akmp);
	if (NULL != existing_entry &&
		existing_entry->expiration >= new_entry->expiration) {
		return ndk::ScopedAStatus::ok();
	}

	new_entry->external = true;
	wpa_sm_pmksa_cache_add_entry(wpa_s->wpa, new_entry);

	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setKeyMgmtInternal(
	KeyMgmtMask mask)
{
	uint32_t key_mgmt_mask = static_cast<uint32_t>(mask);
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (key_mgmt_mask & ~kAllowedKeyMgmtMask) {
		return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);
	}
#ifdef CONFIG_SAE
	struct wpa_driver_capa capa;
	int res = wpa_drv_get_capa(wpa_s, &capa);
	if ((res == 0) && (key_mgmt_mask & WPA_KEY_MGMT_SAE) &&
		(capa.key_mgmt_iftype[WPA_IF_STATION] & WPA_DRIVER_CAPA_KEY_MGMT_SAE_EXT_KEY)) {
		key_mgmt_mask |= WPA_KEY_MGMT_SAE_EXT_KEY;
	}
#endif
	setFastTransitionKeyMgmt(key_mgmt_mask);

	if (key_mgmt_mask & WPA_KEY_MGMT_OWE) {
		// Do not allow to connect to Open network when OWE is selected
		wpa_ssid->owe_only = 1;
		wpa_ssid->owe_ptk_workaround = 1;
	}
	wpa_ssid->key_mgmt = key_mgmt_mask;
	wpa_printf(MSG_MSGDUMP, "key_mgmt: 0x%x", wpa_ssid->key_mgmt);
	resetInternalStateAfterParamsUpdate();
	return ndk::ScopedAStatus::ok();
}

std::pair<KeyMgmtMask, ndk::ScopedAStatus>
StaNetwork::getKeyMgmtInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	uint32_t key_mgmt_mask = wpa_ssid->key_mgmt & kAllowedKeyMgmtMask;

	resetFastTransitionKeyMgmt(key_mgmt_mask);
	return {static_cast<KeyMgmtMask>(key_mgmt_mask),
		ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus StaNetwork::setProtoInternal(
	ProtoMask mask)
{
	uint32_t proto_mask = static_cast<uint32_t>(mask);
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (proto_mask & ~kAllowedProtoMask) {
		return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);
	}
	wpa_ssid->proto = proto_mask;
	wpa_printf(MSG_MSGDUMP, "proto: 0x%x", wpa_ssid->proto);
	resetInternalStateAfterParamsUpdate();
	return ndk::ScopedAStatus::ok();
}

std::pair<ProtoMask, ndk::ScopedAStatus>
StaNetwork::getProtoInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	uint32_t proto_mask = wpa_ssid->proto & kAllowedProtoMask;
	return {static_cast<ProtoMask>(proto_mask), ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus StaNetwork::setGroupCipherInternal(
	GroupCipherMask mask)
{
	uint32_t group_cipher_mask = static_cast<uint32_t>(mask);
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (group_cipher_mask & ~kAllowedGroupCipherMask) {
		return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);
	}
	wpa_ssid->group_cipher = group_cipher_mask;
	wpa_printf(MSG_MSGDUMP, "group_cipher: 0x%x", wpa_ssid->group_cipher);
	resetInternalStateAfterParamsUpdate();
	return ndk::ScopedAStatus::ok();
}

std::pair<GroupCipherMask, ndk::ScopedAStatus>
StaNetwork::getGroupCipherInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	uint32_t group_cipher_mask = wpa_ssid->group_cipher & kAllowedGroupCipherMask;
	return {static_cast<GroupCipherMask>(group_cipher_mask),
		ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus StaNetwork::setPairwiseCipherInternal(
	PairwiseCipherMask mask)
{
	uint32_t pairwise_cipher_mask = static_cast<uint32_t>(mask);
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (pairwise_cipher_mask & ~kAllowedPairwisewCipherMask) {
		return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);
	}
	wpa_ssid->pairwise_cipher = pairwise_cipher_mask;
	wpa_printf(
		MSG_MSGDUMP, "pairwise_cipher: 0x%x", wpa_ssid->pairwise_cipher);
	resetInternalStateAfterParamsUpdate();
	return ndk::ScopedAStatus::ok();
}

std::pair<PairwiseCipherMask, ndk::ScopedAStatus>
StaNetwork::getPairwiseCipherInternal()
{
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	uint32_t pairwise_cipher_mask = wpa_ssid->pairwise_cipher & kAllowedPairwisewCipherMask;
	return {static_cast<PairwiseCipherMask>(pairwise_cipher_mask),
		ndk::ScopedAStatus::ok()};
}

ndk::ScopedAStatus StaNetwork::setRoamingConsortiumSelectionInternal(
	const std::vector<uint8_t> &selectedRcoi)
{
#ifdef CONFIG_HS20
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (wpa_ssid == NULL) {
		return createStatus(SupplicantStatusCode::FAILURE_NETWORK_INVALID);
	}

	if (setByteArrayFieldAndResetState(
		selectedRcoi.data(), selectedRcoi.size(),
		&(wpa_ssid->roaming_consortium_selection),
		&(wpa_ssid->roaming_consortium_selection_len),
		"roaming_consortium_selection")) {
		return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
	}

	resetInternalStateAfterParamsUpdate();
	return ndk::ScopedAStatus::ok();
#else
	return createStatusWithMsg(SupplicantStatusCode::FAILURE_UNKNOWN, "Not implemented");
#endif /* CONFIG_HS20 */
}

/**
 * Retrieve the underlying |wpa_ssid| struct pointer for
 * this network.
 * If the underlying network is removed or the interface
 * this network belong to
 * is removed, all RPC method calls on this object will
 * return failure.
 */
struct wpa_ssid *StaNetwork::retrieveNetworkPtr()
{
	wpa_supplicant *wpa_s = retrieveIfacePtr();
	if (!wpa_s)
		return nullptr;
	return wpa_config_get_network(wpa_s->conf, network_id_);
}

/**
 * Retrieve the underlying |wpa_supplicant| struct
 * pointer for
 * this network.
 */
struct wpa_supplicant *StaNetwork::retrieveIfacePtr()
{
	return wpa_supplicant_get_iface(wpa_global_, ifname_.c_str());
}

/**
 * Check if the provided psk passhrase is valid or not.
 *
 * Returns 0 if valid, 1 otherwise.
 */
int StaNetwork::isPskPassphraseValid(const std::string &psk)
{
	if (psk.size() <
		static_cast<uint32_t>(ISupplicantStaNetwork::
					  PSK_PASSPHRASE_MIN_LEN_IN_BYTES) ||
		psk.size() >
		static_cast<uint32_t>(ISupplicantStaNetwork::
					  PSK_PASSPHRASE_MAX_LEN_IN_BYTES)) {
		return 1;
	}
	if (has_ctrl_char((u8 *)psk.c_str(), psk.size())) {
		return 1;
	}
	return 0;
}

/**
 * Reset internal wpa_supplicant state machine state
 * after params update (except
 * bssid).
 */
void StaNetwork::resetInternalStateAfterParamsUpdate()
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();

	wpa_sm_pmksa_cache_flush(wpa_s->wpa, wpa_ssid);

	if (wpa_s->current_ssid == wpa_ssid || wpa_s->current_ssid == NULL) {
		/*
		 * Invalidate the EAP session cache if
		 * anything in the
		 * current or previously used
		 * configuration changes.
		 */
		eapol_sm_invalidate_cached_session(wpa_s->eapol);
	}
}

/**
 * Helper function to set value in a string field in |wpa_ssid| structue
 * instance for this network.
 * This function frees any existing data in these fields.
 */
int StaNetwork::setStringFieldAndResetState(
	const char *value, uint8_t **to_update_field, const char *hexdump_prefix)
{
	return setStringFieldAndResetState(
		value, (char **)to_update_field, hexdump_prefix);
}

/**
 * Helper function to set value in a string field in |wpa_ssid| structue
 * instance for this network.
 * This function frees any existing data in these fields.
 */
int StaNetwork::setStringFieldAndResetState(
	const char *value, char **to_update_field, const char *hexdump_prefix)
{
	int value_len = strlen(value);
	if (*to_update_field) {
		os_free(*to_update_field);
	}
	*to_update_field = dup_binstr(value, value_len);
	if (!(*to_update_field)) {
		return 1;
	}
	wpa_hexdump_ascii(
		MSG_MSGDUMP, hexdump_prefix, *to_update_field, value_len);
	resetInternalStateAfterParamsUpdate();
	return 0;
}

/**
 * Helper function to set value in a string key field in |wpa_ssid| structue
 * instance for this network.
 * This function frees any existing data in these fields.
 */
int StaNetwork::setStringKeyFieldAndResetState(
	const char *value, char **to_update_field, const char *hexdump_prefix)
{
	int value_len = strlen(value);
	if (*to_update_field) {
		str_clear_free(*to_update_field);
	}
	*to_update_field = dup_binstr(value, value_len);
	if (!(*to_update_field)) {
		return 1;
	}
	wpa_hexdump_ascii_key(
		MSG_MSGDUMP, hexdump_prefix, *to_update_field, value_len);
	resetInternalStateAfterParamsUpdate();
	return 0;
}

/**
 * Helper function to set value in a string field with a corresponding length
 * field in |wpa_ssid| structure instance for this network.
 * This function frees any existing data in these fields.
 */
int StaNetwork::setByteArrayField(
	const uint8_t *value, const size_t value_len, uint8_t **to_update_field,
	size_t *to_update_field_len, const char *hexdump_prefix, bool resetState)
{
	if (*to_update_field) {
		os_free(*to_update_field);
	}
	*to_update_field = (uint8_t *)os_malloc(value_len);
	if (!(*to_update_field)) {
		return 1;
	}
	os_memcpy(*to_update_field, value, value_len);
	*to_update_field_len = value_len;

	wpa_hexdump_ascii(
		MSG_MSGDUMP, hexdump_prefix, *to_update_field,
		*to_update_field_len);

	if (resetState) {
		resetInternalStateAfterParamsUpdate();
	}
	return 0;
}

/**
 * Helper function to set value in a string field with a corresponding length
 * field in |wpa_ssid| structure instance for this network.
 * This function frees any existing data in these fields.
 */
int StaNetwork::setByteArrayFieldAndResetState(
	const uint8_t *value, const size_t value_len, uint8_t **to_update_field,
	size_t *to_update_field_len, const char *hexdump_prefix)
{
	return setByteArrayField(value, value_len, to_update_field,
		to_update_field_len, hexdump_prefix, true);
}

/**
 * Helper function to set value in a string key field with a corresponding
 * length field in |wpa_ssid| structue instance for this network.
 * This function frees any existing data in these fields.
 */
int StaNetwork::setByteArrayKeyFieldAndResetState(
	const uint8_t *value, const size_t value_len, uint8_t **to_update_field,
	size_t *to_update_field_len, const char *hexdump_prefix)
{
	if (*to_update_field) {
		bin_clear_free(*to_update_field, *to_update_field_len);
	}
	*to_update_field = (uint8_t *)os_malloc(value_len);
	if (!(*to_update_field)) {
		return 1;
	}
	os_memcpy(*to_update_field, value, value_len);
	*to_update_field_len = value_len;

	wpa_hexdump_ascii_key(
		MSG_MSGDUMP, hexdump_prefix, *to_update_field,
		*to_update_field_len);
	resetInternalStateAfterParamsUpdate();
	return 0;
}

/**
 * Helper function to set the fast transition bits in the key management
 * bitmask, to allow FT support when possible.
 */
void StaNetwork::setFastTransitionKeyMgmt(uint32_t &key_mgmt_mask)
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	int res;
	struct wpa_driver_capa capa;

	if (key_mgmt_mask & WPA_KEY_MGMT_PSK) {
		key_mgmt_mask |= WPA_KEY_MGMT_FT_PSK;
	}

	if (key_mgmt_mask & WPA_KEY_MGMT_IEEE8021X) {
		key_mgmt_mask |= WPA_KEY_MGMT_FT_IEEE8021X;
	}

	res = wpa_drv_get_capa(wpa_s, &capa);
	if (res == 0) {
#ifdef CONFIG_IEEE80211R
#ifdef CONFIG_SAE
		if ((key_mgmt_mask & WPA_KEY_MGMT_SAE) &&
			(capa.key_mgmt_iftype[WPA_IF_STATION] & WPA_DRIVER_CAPA_KEY_MGMT_FT_SAE)) {
			key_mgmt_mask |= WPA_KEY_MGMT_FT_SAE;
		}
		if ((key_mgmt_mask & WPA_KEY_MGMT_SAE_EXT_KEY) &&
			(capa.key_mgmt_iftype[WPA_IF_STATION] &
			    WPA_DRIVER_CAPA_KEY_MGMT_FT_SAE_EXT_KEY)) {
			key_mgmt_mask |= WPA_KEY_MGMT_FT_SAE_EXT_KEY;
		}
#endif
#ifdef CONFIG_FILS
		if ((key_mgmt_mask & WPA_KEY_MGMT_FILS_SHA256) &&
		    (capa.key_mgmt_iftype[WPA_IF_STATION] &
			WPA_DRIVER_CAPA_KEY_MGMT_FT_FILS_SHA256)) {
			key_mgmt_mask |= WPA_KEY_MGMT_FT_FILS_SHA256;
		}

		if ((key_mgmt_mask & WPA_KEY_MGMT_FILS_SHA384) &&
		    (capa.key_mgmt_iftype[WPA_IF_STATION] &
			WPA_DRIVER_CAPA_KEY_MGMT_FT_FILS_SHA384)) {
			key_mgmt_mask |= WPA_KEY_MGMT_FT_FILS_SHA384;
		}
#endif
#ifdef CONFIG_SUITEB192
		if ((key_mgmt_mask & WPA_KEY_MGMT_IEEE8021X_SUITE_B_192) &&
		    (capa.key_mgmt_iftype[WPA_IF_STATION] &
			WPA_DRIVER_CAPA_KEY_MGMT_FT_802_1X_SHA384)) {
			key_mgmt_mask |= WPA_KEY_MGMT_FT_IEEE8021X_SHA384;
		}
#endif
#endif
	}

}

/**
 * Helper function to reset the fast transition bits in the key management
 * bitmask.
 */
void StaNetwork::resetFastTransitionKeyMgmt(uint32_t &key_mgmt_mask)
{
	if (key_mgmt_mask & WPA_KEY_MGMT_PSK) {
		key_mgmt_mask &= ~WPA_KEY_MGMT_FT_PSK;
	}

	if (key_mgmt_mask & WPA_KEY_MGMT_IEEE8021X) {
		key_mgmt_mask &= ~WPA_KEY_MGMT_FT_IEEE8021X;
	}
#ifdef CONFIG_IEEE80211R
#ifdef CONFIG_SAE
	if (key_mgmt_mask & WPA_KEY_MGMT_SAE) {
		key_mgmt_mask &= ~WPA_KEY_MGMT_FT_SAE;
	}
#endif
#ifdef CONFIG_FILS
	if (key_mgmt_mask & WPA_KEY_MGMT_FILS_SHA256) {
		key_mgmt_mask &= ~WPA_KEY_MGMT_FT_FILS_SHA256;
	}

	if (key_mgmt_mask & WPA_KEY_MGMT_FILS_SHA384) {
		key_mgmt_mask &= ~WPA_KEY_MGMT_FT_FILS_SHA384;
	}
#endif
#ifdef CONFIG_SUITEB192
	if (key_mgmt_mask & WPA_KEY_MGMT_IEEE8021X_SUITE_B_192) {
		key_mgmt_mask &= ~WPA_KEY_MGMT_FT_IEEE8021X_SHA384;
	}
#endif
#endif
}

/**
 * Helper function to enable erp keys generation while connecting to FILS
 * enabled APs.
 */
ndk::ScopedAStatus StaNetwork::setEapErpInternal(bool enable)
{
#ifdef CONFIG_FILS
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	wpa_ssid->eap.erp = enable ? 1 : 0;
	return ndk::ScopedAStatus::ok();
#else /* CONFIG_FILS */
	return createStatus(SupplicantStatusCode::FAILURE_UNKNOWN);
#endif /* CONFIG_FILS */
}

ndk::ScopedAStatus StaNetwork::setSaeH2eModeInternal(
	SaeH2eMode mode)
{
	struct wpa_supplicant *wpa_s = retrieveIfacePtr();
	switch (mode) {
	case SaeH2eMode::DISABLED:
		wpa_s->conf->sae_pwe = SAE_PWE_HUNT_AND_PECK;
		break;
	case SaeH2eMode::H2E_MANDATORY:
		wpa_s->conf->sae_pwe = SAE_PWE_HASH_TO_ELEMENT;
		break;
	case SaeH2eMode::H2E_OPTIONAL:
		wpa_s->conf->sae_pwe = SAE_PWE_BOTH;
		break;
	}
	resetInternalStateAfterParamsUpdate();
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::enableSaePkOnlyModeInternal(bool enable)
{
#ifdef CONFIG_SAE_PK
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	wpa_ssid->sae_pk = enable ? SAE_PK_MODE_ONLY : SAE_PK_MODE_AUTOMATIC;
	resetInternalStateAfterParamsUpdate();
	return ndk::ScopedAStatus::ok();
#else
	return createStatus(SupplicantStatusCode::FAILURE_UNSUPPORTED);
#endif
}

ndk::ScopedAStatus StaNetwork::setMinimumTlsVersionEapPhase1ParamInternal(TlsVersion tlsVersion)
{
	if (tlsVersion < TlsVersion::TLS_V1_0 || tlsVersion > TlsVersion::TLS_V1_3) {
		return createStatus(SupplicantStatusCode::FAILURE_ARGS_INVALID);
	}
	if (tlsVersion == TlsVersion::TLS_V1_0) {
		// no restriction
		return ndk::ScopedAStatus::ok();
	}

	if (tlsVersion < TlsVersion::TLS_V1_3 && (tlsFlags & TLS_CONN_SUITEB)) {
		// TLS configuration already set up for WPA3-Enterprise 192-bit mode
		return ndk::ScopedAStatus::ok();
	}

	tlsFlags &= ~(TLS_CONN_DISABLE_TLSv1_3 | TLS_CONN_DISABLE_TLSv1_2 \
			| TLS_CONN_DISABLE_TLSv1_1 | TLS_CONN_DISABLE_TLSv1_0);

	// Fallback to disable lower version TLS cascadingly.
	switch (tlsVersion) {
#ifdef EAP_TLSV1_3
		case TlsVersion::TLS_V1_3:
			tlsFlags |= TLS_CONN_DISABLE_TLSv1_2;
			FALLTHROUGH_INTENDED;
#endif
		case TlsVersion::TLS_V1_2:
			tlsFlags |= TLS_CONN_DISABLE_TLSv1_1;
			FALLTHROUGH_INTENDED;
		case TlsVersion::TLS_V1_1:
			tlsFlags |= TLS_CONN_DISABLE_TLSv1_0;
			break;
		default:
			return createStatus(SupplicantStatusCode::FAILURE_UNSUPPORTED);
	}

	generateTlsParams();
	return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::disableEhtInternal()
{
  struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
  if (wpa_ssid == nullptr ) {
    return createStatus(SupplicantStatusCode::FAILURE_NETWORK_INVALID);
  }
  wpa_ssid->disable_eht = 1;
  resetInternalStateAfterParamsUpdate();
  return ndk::ScopedAStatus::ok();
}

ndk::ScopedAStatus StaNetwork::setVendorDataInternal(
		const std::vector<common::OuiKeyedData>& /* vendorData */) {
	// Not implemented in the default implementation.
	return ndk::ScopedAStatus::ok();
}

/**
 * WPA3-Enterprise 192-bit mode workaround to force the connection to EAP-TLSv1.2 due to
 * interoperability issues in TLSv1.3 which disables the SSL_SIGN_RSA_PKCS1_SHA384
 * signature algorithm, and has its own set of incompatible cipher suites which the
 * current WPA3 specification doesn't specify. The only specified cipher suites in the
 * WPA3 specifications are:
 * TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384, TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384, and
 * TLS_DHE_RSA_WITH_AES_256_GCM_SHA384.
 * See boringssl/include/openssl/tls1.h for TLSv1.3 cipher suites.
 */
void StaNetwork::setTlsFlagsFor192BitMode(bool rsaMode) {
	// Disable TLSv1.0 and TLSv1.1 by default for 192-bit mode
	int flags = TLS_CONN_DISABLE_TLSv1_1 \
			| TLS_CONN_DISABLE_TLSv1_0;
	if (rsaMode) {
		// Check if flags not set or already set to use EAP-TLSv1.3
		if (tlsFlags == 0 || !(tlsFlags & TLS_CONN_DISABLE_TLSv1_2)) {
			// Set up EAP-TLSv1.2 by default for maximum compatibility
			tlsFlags |= TLS_CONN_DISABLE_TLSv1_3;
			tlsFlags &= ~TLS_CONN_DISABLE_TLSv1_2;
		}
		tlsFlags |= TLS_CONN_SUITEB;
	}

	tlsFlags |= flags;
	generateTlsParams();
}

void StaNetwork::generateTlsParams() {
	struct wpa_ssid *wpa_ssid = retrieveNetworkPtr();
	if (wpa_ssid->eap.phase1 != NULL) {
		os_free(wpa_ssid->eap.phase1);
		wpa_ssid->eap.phase1 = NULL;
	}
	std::string tlsConfig;

	if (tlsFlags & TLS_CONN_DISABLE_TLSv1_3) {
		tlsConfig.append("tls_disable_tlsv1_3=1");
	} else {
		tlsConfig.append("tls_disable_tlsv1_3=0");
	}
	if (tlsFlags & TLS_CONN_DISABLE_TLSv1_2) {
		tlsConfig.append("tls_disable_tlsv1_2=1");
	} else {
		tlsConfig.append("tls_disable_tlsv1_2=0");
	}
	if (tlsFlags & TLS_CONN_DISABLE_TLSv1_1) {
		tlsConfig.append("tls_disable_tlsv1_1=1");
	} else {
		tlsConfig.append("tls_disable_tlsv1_1=0");
	}
	if (tlsFlags & TLS_CONN_DISABLE_TLSv1_0) {
		tlsConfig.append("tls_disable_tlsv1_0=1");
	} else {
		tlsConfig.append("tls_disable_tlsv1_0=0");
	}
	if (tlsFlags & TLS_CONN_SUITEB) {
		tlsConfig.append("tls_suiteb=1");
	}

	wpa_printf(MSG_DEBUG, "TLS configuration: %s", tlsConfig.c_str());
	setStringKeyFieldAndResetState(
			tlsConfig.c_str(), &(wpa_ssid->eap.phase1), "phase1");
}
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
