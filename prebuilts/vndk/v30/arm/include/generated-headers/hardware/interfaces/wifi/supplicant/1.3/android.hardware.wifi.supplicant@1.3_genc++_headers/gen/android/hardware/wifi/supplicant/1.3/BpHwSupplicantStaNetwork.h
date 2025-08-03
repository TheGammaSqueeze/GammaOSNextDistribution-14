#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_3_BPHWSUPPLICANTSTANETWORK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_3_BPHWSUPPLICANTSTANETWORK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/wifi/supplicant/1.3/IHwSupplicantStaNetwork.h>

namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace V1_3 {

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

    // Methods from ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaNetwork follow.
    static ::android::hardware::Return<void>  _hidl_setOcsp(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::wifi::supplicant::V1_3::OcspType ocspType, setOcsp_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getOcsp(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getOcsp_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setKeyMgmt_1_3(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaNetwork::KeyMgmtMask> keyMgmtMask, setKeyMgmt_1_3_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getKeyMgmt_1_3(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getKeyMgmt_1_3_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setProto_1_3(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaNetwork::ProtoMask> protoMask, setProto_1_3_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getProto_1_3(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getProto_1_3_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setGroupCipher_1_3(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaNetwork::GroupCipherMask> groupCipherMask, setGroupCipher_1_3_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getPairwiseCipher_1_3(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getPairwiseCipher_1_3_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setPairwiseCipher_1_3(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaNetwork::PairwiseCipherMask> pairwiseCipherMask, setPairwiseCipher_1_3_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getGroupCipher_1_3(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getGroupCipher_1_3_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setWapiCertSuite(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& suite, setWapiCertSuite_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getWapiCertSuite(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getWapiCertSuite_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setPmkCache(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& serializedEntry, setPmkCache_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setAuthAlg_1_3(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaNetwork::AuthAlgMask> authAlgMask, setAuthAlg_1_3_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getAuthAlg_1_3(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getAuthAlg_1_3_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setEapErp(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, bool enable, setEapErp_cb _hidl_cb);

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

    // Methods from ::android::hardware::wifi::supplicant::V1_1::ISupplicantStaNetwork follow.
    ::android::hardware::Return<void> setEapEncryptedImsiIdentity(const ::android::hardware::hidl_vec<uint8_t>& identity, setEapEncryptedImsiIdentity_cb _hidl_cb) override;
    ::android::hardware::Return<void> sendNetworkEapIdentityResponse_1_1(const ::android::hardware::hidl_vec<uint8_t>& identity, const ::android::hardware::hidl_vec<uint8_t>& encryptedIdentity, sendNetworkEapIdentityResponse_1_1_cb _hidl_cb) override;

    // Methods from ::android::hardware::wifi::supplicant::V1_2::ISupplicantStaNetwork follow.
    ::android::hardware::Return<void> setKeyMgmt_1_2(::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_2::ISupplicantStaNetwork::KeyMgmtMask> keyMgmtMask, setKeyMgmt_1_2_cb _hidl_cb) override;
    ::android::hardware::Return<void> getKeyMgmt_1_2(getKeyMgmt_1_2_cb _hidl_cb) override;
    ::android::hardware::Return<void> setPairwiseCipher_1_2(::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_2::ISupplicantStaNetwork::PairwiseCipherMask> pairwiseCipherMask, setPairwiseCipher_1_2_cb _hidl_cb) override;
    ::android::hardware::Return<void> getPairwiseCipher_1_2(getPairwiseCipher_1_2_cb _hidl_cb) override;
    ::android::hardware::Return<void> setGroupCipher_1_2(::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_2::ISupplicantStaNetwork::GroupCipherMask> groupCipherMask, setGroupCipher_1_2_cb _hidl_cb) override;
    ::android::hardware::Return<void> getGroupCipher_1_2(getGroupCipher_1_2_cb _hidl_cb) override;
    ::android::hardware::Return<void> setGroupMgmtCipher(::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_2::ISupplicantStaNetwork::GroupMgmtCipherMask> groupMgmtCipherMask, setGroupMgmtCipher_cb _hidl_cb) override;
    ::android::hardware::Return<void> getGroupMgmtCipher(getGroupMgmtCipher_cb _hidl_cb) override;
    ::android::hardware::Return<void> enableTlsSuiteBEapPhase1Param(bool enable, enableTlsSuiteBEapPhase1Param_cb _hidl_cb) override;
    ::android::hardware::Return<void> enableSuiteBEapOpenSslCiphers(enableSuiteBEapOpenSslCiphers_cb _hidl_cb) override;
    ::android::hardware::Return<void> getSaePassword(getSaePassword_cb _hidl_cb) override;
    ::android::hardware::Return<void> getSaePasswordId(getSaePasswordId_cb _hidl_cb) override;
    ::android::hardware::Return<void> setSaePassword(const ::android::hardware::hidl_string& saePassword, setSaePassword_cb _hidl_cb) override;
    ::android::hardware::Return<void> setSaePasswordId(const ::android::hardware::hidl_string& saePasswordId, setSaePasswordId_cb _hidl_cb) override;

    // Methods from ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaNetwork follow.
    ::android::hardware::Return<void> setOcsp(::android::hardware::wifi::supplicant::V1_3::OcspType ocspType, setOcsp_cb _hidl_cb) override;
    ::android::hardware::Return<void> getOcsp(getOcsp_cb _hidl_cb) override;
    ::android::hardware::Return<void> setKeyMgmt_1_3(::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaNetwork::KeyMgmtMask> keyMgmtMask, setKeyMgmt_1_3_cb _hidl_cb) override;
    ::android::hardware::Return<void> getKeyMgmt_1_3(getKeyMgmt_1_3_cb _hidl_cb) override;
    ::android::hardware::Return<void> setProto_1_3(::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaNetwork::ProtoMask> protoMask, setProto_1_3_cb _hidl_cb) override;
    ::android::hardware::Return<void> getProto_1_3(getProto_1_3_cb _hidl_cb) override;
    ::android::hardware::Return<void> setGroupCipher_1_3(::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaNetwork::GroupCipherMask> groupCipherMask, setGroupCipher_1_3_cb _hidl_cb) override;
    ::android::hardware::Return<void> getPairwiseCipher_1_3(getPairwiseCipher_1_3_cb _hidl_cb) override;
    ::android::hardware::Return<void> setPairwiseCipher_1_3(::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaNetwork::PairwiseCipherMask> pairwiseCipherMask, setPairwiseCipher_1_3_cb _hidl_cb) override;
    ::android::hardware::Return<void> getGroupCipher_1_3(getGroupCipher_1_3_cb _hidl_cb) override;
    ::android::hardware::Return<void> setWapiCertSuite(const ::android::hardware::hidl_string& suite, setWapiCertSuite_cb _hidl_cb) override;
    ::android::hardware::Return<void> getWapiCertSuite(getWapiCertSuite_cb _hidl_cb) override;
    ::android::hardware::Return<void> setPmkCache(const ::android::hardware::hidl_vec<uint8_t>& serializedEntry, setPmkCache_cb _hidl_cb) override;
    ::android::hardware::Return<void> setAuthAlg_1_3(::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaNetwork::AuthAlgMask> authAlgMask, setAuthAlg_1_3_cb _hidl_cb) override;
    ::android::hardware::Return<void> getAuthAlg_1_3(getAuthAlg_1_3_cb _hidl_cb) override;
    ::android::hardware::Return<void> setEapErp(bool enable, setEapErp_cb _hidl_cb) override;

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

}  // namespace V1_3
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_3_BPHWSUPPLICANTSTANETWORK_H
