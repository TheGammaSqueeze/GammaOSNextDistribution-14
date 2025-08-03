#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_BPHWSUPPLICANTSTANETWORK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_BPHWSUPPLICANTSTANETWORK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/wifi/supplicant/1.0/IHwSupplicantStaNetwork.h>

namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace V1_0 {

struct BpHwSupplicantStaNetwork : public ::android::hardware::BpInterface<ISupplicantStaNetwork>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwSupplicantStaNetwork(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ISupplicantStaNetwork Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork follow.
    static ::android::hardware::Return<void>  _hidl_registerCallback(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetworkCallback>& callback, registerCallback_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setSsid(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& ssid, setSsid_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setBssid(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_array<uint8_t, 6>& bssid, setBssid_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setScanSsid(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, bool enable, setScanSsid_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setKeyMgmt(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork::KeyMgmtMask> keyMgmtMask, setKeyMgmt_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setProto(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork::ProtoMask> protoMask, setProto_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setAuthAlg(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork::AuthAlgMask> authAlgMask, setAuthAlg_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setGroupCipher(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork::GroupCipherMask> groupCipherMask, setGroupCipher_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setPairwiseCipher(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork::PairwiseCipherMask> pairwiseCipherMask, setPairwiseCipher_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setPskPassphrase(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& psk, setPskPassphrase_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setPsk(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_array<uint8_t, 32>& psk, setPsk_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setWepKey(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t keyIdx, const ::android::hardware::hidl_vec<uint8_t>& wepKey, setWepKey_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setWepTxKeyIdx(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t keyIdx, setWepTxKeyIdx_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setRequirePmf(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, bool enable, setRequirePmf_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setEapMethod(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork::EapMethod method, setEapMethod_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setEapPhase2Method(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork::EapPhase2Method method, setEapPhase2Method_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setEapIdentity(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& identity, setEapIdentity_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setEapAnonymousIdentity(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& identity, setEapAnonymousIdentity_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setEapPassword(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& password, setEapPassword_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setEapCACert(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& path, setEapCACert_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setEapCAPath(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& path, setEapCAPath_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setEapClientCert(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& path, setEapClientCert_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setEapPrivateKeyId(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& id, setEapPrivateKeyId_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setEapSubjectMatch(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& match, setEapSubjectMatch_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setEapAltSubjectMatch(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& match, setEapAltSubjectMatch_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setEapEngine(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, bool enable, setEapEngine_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setEapEngineID(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& id, setEapEngineID_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setEapDomainSuffixMatch(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& match, setEapDomainSuffixMatch_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setProactiveKeyCaching(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, bool enable, setProactiveKeyCaching_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setIdStr(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& idStr, setIdStr_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setUpdateIdentifier(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t id, setUpdateIdentifier_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getSsid(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getSsid_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getBssid(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getBssid_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getScanSsid(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getScanSsid_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getKeyMgmt(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getKeyMgmt_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getProto(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getProto_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getAuthAlg(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getAuthAlg_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getGroupCipher(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getGroupCipher_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getPairwiseCipher(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getPairwiseCipher_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getPskPassphrase(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getPskPassphrase_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getPsk(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getPsk_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getWepKey(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t keyIdx, getWepKey_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getWepTxKeyIdx(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getWepTxKeyIdx_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getRequirePmf(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getRequirePmf_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getEapMethod(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getEapMethod_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getEapPhase2Method(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getEapPhase2Method_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getEapIdentity(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getEapIdentity_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getEapAnonymousIdentity(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getEapAnonymousIdentity_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getEapPassword(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getEapPassword_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getEapCACert(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getEapCACert_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getEapCAPath(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getEapCAPath_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getEapClientCert(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getEapClientCert_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getEapPrivateKeyId(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getEapPrivateKeyId_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getEapSubjectMatch(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getEapSubjectMatch_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getEapAltSubjectMatch(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getEapAltSubjectMatch_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getEapEngine(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getEapEngine_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getEapEngineID(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getEapEngineID_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getEapDomainSuffixMatch(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getEapDomainSuffixMatch_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getIdStr(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getIdStr_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getWpsNfcConfigurationToken(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getWpsNfcConfigurationToken_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_enable(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, bool noConnect, enable_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_disable(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, disable_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_select(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, select_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_sendNetworkEapSimGsmAuthResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork::NetworkResponseEapSimGsmAuthParams>& params, sendNetworkEapSimGsmAuthResponse_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_sendNetworkEapSimGsmAuthFailure(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, sendNetworkEapSimGsmAuthFailure_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_sendNetworkEapSimUmtsAuthResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork::NetworkResponseEapSimUmtsAuthParams& params, sendNetworkEapSimUmtsAuthResponse_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_sendNetworkEapSimUmtsAutsResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_array<uint8_t, 14>& auts, sendNetworkEapSimUmtsAutsResponse_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_sendNetworkEapSimUmtsAuthFailure(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, sendNetworkEapSimUmtsAuthFailure_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_sendNetworkEapIdentityResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& identity, sendNetworkEapIdentityResponse_cb _hidl_cb);

    // Methods from ::android::hardware::wifi::supplicant::V1_0::ISupplicantNetwork follow.
    ::android::hardware::Return<void> getId(getId_cb _hidl_cb) override;
    ::android::hardware::Return<void> getInterfaceName(getInterfaceName_cb _hidl_cb) override;
    ::android::hardware::Return<void> getType(getType_cb _hidl_cb) override;

    // Methods from ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork follow.
    ::android::hardware::Return<void> registerCallback(const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetworkCallback>& callback, registerCallback_cb _hidl_cb) override;
    ::android::hardware::Return<void> setSsid(const ::android::hardware::hidl_vec<uint8_t>& ssid, setSsid_cb _hidl_cb) override;
    ::android::hardware::Return<void> setBssid(const ::android::hardware::hidl_array<uint8_t, 6>& bssid, setBssid_cb _hidl_cb) override;
    ::android::hardware::Return<void> setScanSsid(bool enable, setScanSsid_cb _hidl_cb) override;
    ::android::hardware::Return<void> setKeyMgmt(::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork::KeyMgmtMask> keyMgmtMask, setKeyMgmt_cb _hidl_cb) override;
    ::android::hardware::Return<void> setProto(::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork::ProtoMask> protoMask, setProto_cb _hidl_cb) override;
    ::android::hardware::Return<void> setAuthAlg(::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork::AuthAlgMask> authAlgMask, setAuthAlg_cb _hidl_cb) override;
    ::android::hardware::Return<void> setGroupCipher(::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork::GroupCipherMask> groupCipherMask, setGroupCipher_cb _hidl_cb) override;
    ::android::hardware::Return<void> setPairwiseCipher(::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork::PairwiseCipherMask> pairwiseCipherMask, setPairwiseCipher_cb _hidl_cb) override;
    ::android::hardware::Return<void> setPskPassphrase(const ::android::hardware::hidl_string& psk, setPskPassphrase_cb _hidl_cb) override;
    ::android::hardware::Return<void> setPsk(const ::android::hardware::hidl_array<uint8_t, 32>& psk, setPsk_cb _hidl_cb) override;
    ::android::hardware::Return<void> setWepKey(uint32_t keyIdx, const ::android::hardware::hidl_vec<uint8_t>& wepKey, setWepKey_cb _hidl_cb) override;
    ::android::hardware::Return<void> setWepTxKeyIdx(uint32_t keyIdx, setWepTxKeyIdx_cb _hidl_cb) override;
    ::android::hardware::Return<void> setRequirePmf(bool enable, setRequirePmf_cb _hidl_cb) override;
    ::android::hardware::Return<void> setEapMethod(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork::EapMethod method, setEapMethod_cb _hidl_cb) override;
    ::android::hardware::Return<void> setEapPhase2Method(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork::EapPhase2Method method, setEapPhase2Method_cb _hidl_cb) override;
    ::android::hardware::Return<void> setEapIdentity(const ::android::hardware::hidl_vec<uint8_t>& identity, setEapIdentity_cb _hidl_cb) override;
    ::android::hardware::Return<void> setEapAnonymousIdentity(const ::android::hardware::hidl_vec<uint8_t>& identity, setEapAnonymousIdentity_cb _hidl_cb) override;
    ::android::hardware::Return<void> setEapPassword(const ::android::hardware::hidl_vec<uint8_t>& password, setEapPassword_cb _hidl_cb) override;
    ::android::hardware::Return<void> setEapCACert(const ::android::hardware::hidl_string& path, setEapCACert_cb _hidl_cb) override;
    ::android::hardware::Return<void> setEapCAPath(const ::android::hardware::hidl_string& path, setEapCAPath_cb _hidl_cb) override;
    ::android::hardware::Return<void> setEapClientCert(const ::android::hardware::hidl_string& path, setEapClientCert_cb _hidl_cb) override;
    ::android::hardware::Return<void> setEapPrivateKeyId(const ::android::hardware::hidl_string& id, setEapPrivateKeyId_cb _hidl_cb) override;
    ::android::hardware::Return<void> setEapSubjectMatch(const ::android::hardware::hidl_string& match, setEapSubjectMatch_cb _hidl_cb) override;
    ::android::hardware::Return<void> setEapAltSubjectMatch(const ::android::hardware::hidl_string& match, setEapAltSubjectMatch_cb _hidl_cb) override;
    ::android::hardware::Return<void> setEapEngine(bool enable, setEapEngine_cb _hidl_cb) override;
    ::android::hardware::Return<void> setEapEngineID(const ::android::hardware::hidl_string& id, setEapEngineID_cb _hidl_cb) override;
    ::android::hardware::Return<void> setEapDomainSuffixMatch(const ::android::hardware::hidl_string& match, setEapDomainSuffixMatch_cb _hidl_cb) override;
    ::android::hardware::Return<void> setProactiveKeyCaching(bool enable, setProactiveKeyCaching_cb _hidl_cb) override;
    ::android::hardware::Return<void> setIdStr(const ::android::hardware::hidl_string& idStr, setIdStr_cb _hidl_cb) override;
    ::android::hardware::Return<void> setUpdateIdentifier(uint32_t id, setUpdateIdentifier_cb _hidl_cb) override;
    ::android::hardware::Return<void> getSsid(getSsid_cb _hidl_cb) override;
    ::android::hardware::Return<void> getBssid(getBssid_cb _hidl_cb) override;
    ::android::hardware::Return<void> getScanSsid(getScanSsid_cb _hidl_cb) override;
    ::android::hardware::Return<void> getKeyMgmt(getKeyMgmt_cb _hidl_cb) override;
    ::android::hardware::Return<void> getProto(getProto_cb _hidl_cb) override;
    ::android::hardware::Return<void> getAuthAlg(getAuthAlg_cb _hidl_cb) override;
    ::android::hardware::Return<void> getGroupCipher(getGroupCipher_cb _hidl_cb) override;
    ::android::hardware::Return<void> getPairwiseCipher(getPairwiseCipher_cb _hidl_cb) override;
    ::android::hardware::Return<void> getPskPassphrase(getPskPassphrase_cb _hidl_cb) override;
    ::android::hardware::Return<void> getPsk(getPsk_cb _hidl_cb) override;
    ::android::hardware::Return<void> getWepKey(uint32_t keyIdx, getWepKey_cb _hidl_cb) override;
    ::android::hardware::Return<void> getWepTxKeyIdx(getWepTxKeyIdx_cb _hidl_cb) override;
    ::android::hardware::Return<void> getRequirePmf(getRequirePmf_cb _hidl_cb) override;
    ::android::hardware::Return<void> getEapMethod(getEapMethod_cb _hidl_cb) override;
    ::android::hardware::Return<void> getEapPhase2Method(getEapPhase2Method_cb _hidl_cb) override;
    ::android::hardware::Return<void> getEapIdentity(getEapIdentity_cb _hidl_cb) override;
    ::android::hardware::Return<void> getEapAnonymousIdentity(getEapAnonymousIdentity_cb _hidl_cb) override;
    ::android::hardware::Return<void> getEapPassword(getEapPassword_cb _hidl_cb) override;
    ::android::hardware::Return<void> getEapCACert(getEapCACert_cb _hidl_cb) override;
    ::android::hardware::Return<void> getEapCAPath(getEapCAPath_cb _hidl_cb) override;
    ::android::hardware::Return<void> getEapClientCert(getEapClientCert_cb _hidl_cb) override;
    ::android::hardware::Return<void> getEapPrivateKeyId(getEapPrivateKeyId_cb _hidl_cb) override;
    ::android::hardware::Return<void> getEapSubjectMatch(getEapSubjectMatch_cb _hidl_cb) override;
    ::android::hardware::Return<void> getEapAltSubjectMatch(getEapAltSubjectMatch_cb _hidl_cb) override;
    ::android::hardware::Return<void> getEapEngine(getEapEngine_cb _hidl_cb) override;
    ::android::hardware::Return<void> getEapEngineID(getEapEngineID_cb _hidl_cb) override;
    ::android::hardware::Return<void> getEapDomainSuffixMatch(getEapDomainSuffixMatch_cb _hidl_cb) override;
    ::android::hardware::Return<void> getIdStr(getIdStr_cb _hidl_cb) override;
    ::android::hardware::Return<void> getWpsNfcConfigurationToken(getWpsNfcConfigurationToken_cb _hidl_cb) override;
    ::android::hardware::Return<void> enable(bool noConnect, enable_cb _hidl_cb) override;
    ::android::hardware::Return<void> disable(disable_cb _hidl_cb) override;
    ::android::hardware::Return<void> select(select_cb _hidl_cb) override;
    ::android::hardware::Return<void> sendNetworkEapSimGsmAuthResponse(const ::android::hardware::hidl_vec<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork::NetworkResponseEapSimGsmAuthParams>& params, sendNetworkEapSimGsmAuthResponse_cb _hidl_cb) override;
    ::android::hardware::Return<void> sendNetworkEapSimGsmAuthFailure(sendNetworkEapSimGsmAuthFailure_cb _hidl_cb) override;
    ::android::hardware::Return<void> sendNetworkEapSimUmtsAuthResponse(const ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaNetwork::NetworkResponseEapSimUmtsAuthParams& params, sendNetworkEapSimUmtsAuthResponse_cb _hidl_cb) override;
    ::android::hardware::Return<void> sendNetworkEapSimUmtsAutsResponse(const ::android::hardware::hidl_array<uint8_t, 14>& auts, sendNetworkEapSimUmtsAutsResponse_cb _hidl_cb) override;
    ::android::hardware::Return<void> sendNetworkEapSimUmtsAuthFailure(sendNetworkEapSimUmtsAuthFailure_cb _hidl_cb) override;
    ::android::hardware::Return<void> sendNetworkEapIdentityResponse(const ::android::hardware::hidl_vec<uint8_t>& identity, sendNetworkEapIdentityResponse_cb _hidl_cb) override;

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> interfaceChain(interfaceChain_cb _hidl_cb) override;
    ::android::hardware::Return<void> debug(const ::android::hardware::hidl_handle& fd, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& options) override;
    ::android::hardware::Return<void> interfaceDescriptor(interfaceDescriptor_cb _hidl_cb) override;
    ::android::hardware::Return<void> getHashChain(getHashChain_cb _hidl_cb) override;
    ::android::hardware::Return<void> setHALInstrumentation() override;
    ::android::hardware::Return<bool> linkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient, uint64_t cookie) override;
    ::android::hardware::Return<void> ping() override;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb) override;
    ::android::hardware::Return<void> notifySyspropsChanged() override;
    ::android::hardware::Return<bool> unlinkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient) override;

private:
    std::mutex _hidl_mMutex;
    std::vector<::android::sp<::android::hardware::hidl_binder_death_recipient>> _hidl_mDeathRecipients;
};

}  // namespace V1_0
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_BPHWSUPPLICANTSTANETWORK_H
