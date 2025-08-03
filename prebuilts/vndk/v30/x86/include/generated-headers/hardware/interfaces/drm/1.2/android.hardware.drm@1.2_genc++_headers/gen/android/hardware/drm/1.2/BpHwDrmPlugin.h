#ifndef HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_2_BPHWDRMPLUGIN_H
#define HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_2_BPHWDRMPLUGIN_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/drm/1.2/IHwDrmPlugin.h>

namespace android {
namespace hardware {
namespace drm {
namespace V1_2 {

struct BpHwDrmPlugin : public ::android::hardware::BpInterface<IDrmPlugin>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwDrmPlugin(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IDrmPlugin Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::drm::V1_2::IDrmPlugin follow.
    static ::android::hardware::Return<void>  _hidl_getOfflineLicenseKeySetIds(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getOfflineLicenseKeySetIds_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::drm::V1_0::Status>  _hidl_removeOfflineLicense(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& keySetId);
    static ::android::hardware::Return<void>  _hidl_getOfflineLicenseState(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& keySetId, getOfflineLicenseState_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getKeyRequest_1_2(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& scope, const ::android::hardware::hidl_vec<uint8_t>& initData, const ::android::hardware::hidl_string& mimeType, ::android::hardware::drm::V1_0::KeyType keyType, const ::android::hardware::hidl_vec<::android::hardware::drm::V1_0::KeyValue>& optionalParameters, getKeyRequest_1_2_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getProvisionRequest_1_2(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& certificateType, const ::android::hardware::hidl_string& certificateAuthority, getProvisionRequest_1_2_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getHdcpLevels_1_2(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getHdcpLevels_1_2_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_sendSessionLostState(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& sessionId);
    static ::android::hardware::Return<void>  _hidl_sendKeysChange_1_2(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<::android::hardware::drm::V1_2::KeyStatus>& keyStatusList, bool hasNewUsableKey);

    // Methods from ::android::hardware::drm::V1_0::IDrmPlugin follow.
    ::android::hardware::Return<void> openSession(openSession_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::drm::V1_0::Status> closeSession(const ::android::hardware::hidl_vec<uint8_t>& sessionId) override;
    ::android::hardware::Return<void> getKeyRequest(const ::android::hardware::hidl_vec<uint8_t>& scope, const ::android::hardware::hidl_vec<uint8_t>& initData, const ::android::hardware::hidl_string& mimeType, ::android::hardware::drm::V1_0::KeyType keyType, const ::android::hardware::hidl_vec<::android::hardware::drm::V1_0::KeyValue>& optionalParameters, getKeyRequest_cb _hidl_cb) override;
    ::android::hardware::Return<void> provideKeyResponse(const ::android::hardware::hidl_vec<uint8_t>& scope, const ::android::hardware::hidl_vec<uint8_t>& response, provideKeyResponse_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::drm::V1_0::Status> removeKeys(const ::android::hardware::hidl_vec<uint8_t>& sessionId) override;
    ::android::hardware::Return<::android::hardware::drm::V1_0::Status> restoreKeys(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<uint8_t>& keySetId) override;
    ::android::hardware::Return<void> queryKeyStatus(const ::android::hardware::hidl_vec<uint8_t>& sessionId, queryKeyStatus_cb _hidl_cb) override;
    ::android::hardware::Return<void> getProvisionRequest(const ::android::hardware::hidl_string& certificateType, const ::android::hardware::hidl_string& certificateAuthority, getProvisionRequest_cb _hidl_cb) override;
    ::android::hardware::Return<void> provideProvisionResponse(const ::android::hardware::hidl_vec<uint8_t>& response, provideProvisionResponse_cb _hidl_cb) override;
    ::android::hardware::Return<void> getSecureStops(getSecureStops_cb _hidl_cb) override;
    ::android::hardware::Return<void> getSecureStop(const ::android::hardware::hidl_vec<uint8_t>& secureStopId, getSecureStop_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::drm::V1_0::Status> releaseAllSecureStops() override;
    ::android::hardware::Return<::android::hardware::drm::V1_0::Status> releaseSecureStop(const ::android::hardware::hidl_vec<uint8_t>& secureStopId) override;
    ::android::hardware::Return<void> getPropertyString(const ::android::hardware::hidl_string& propertyName, getPropertyString_cb _hidl_cb) override;
    ::android::hardware::Return<void> getPropertyByteArray(const ::android::hardware::hidl_string& propertyName, getPropertyByteArray_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::drm::V1_0::Status> setPropertyString(const ::android::hardware::hidl_string& propertyName, const ::android::hardware::hidl_string& value) override;
    ::android::hardware::Return<::android::hardware::drm::V1_0::Status> setPropertyByteArray(const ::android::hardware::hidl_string& propertyName, const ::android::hardware::hidl_vec<uint8_t>& value) override;
    ::android::hardware::Return<::android::hardware::drm::V1_0::Status> setCipherAlgorithm(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_string& algorithm) override;
    ::android::hardware::Return<::android::hardware::drm::V1_0::Status> setMacAlgorithm(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_string& algorithm) override;
    ::android::hardware::Return<void> encrypt(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<uint8_t>& keyId, const ::android::hardware::hidl_vec<uint8_t>& input, const ::android::hardware::hidl_vec<uint8_t>& iv, encrypt_cb _hidl_cb) override;
    ::android::hardware::Return<void> decrypt(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<uint8_t>& keyId, const ::android::hardware::hidl_vec<uint8_t>& input, const ::android::hardware::hidl_vec<uint8_t>& iv, decrypt_cb _hidl_cb) override;
    ::android::hardware::Return<void> sign(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<uint8_t>& keyId, const ::android::hardware::hidl_vec<uint8_t>& message, sign_cb _hidl_cb) override;
    ::android::hardware::Return<void> verify(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<uint8_t>& keyId, const ::android::hardware::hidl_vec<uint8_t>& message, const ::android::hardware::hidl_vec<uint8_t>& signature, verify_cb _hidl_cb) override;
    ::android::hardware::Return<void> signRSA(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_string& algorithm, const ::android::hardware::hidl_vec<uint8_t>& message, const ::android::hardware::hidl_vec<uint8_t>& wrappedkey, signRSA_cb _hidl_cb) override;
    ::android::hardware::Return<void> setListener(const ::android::sp<::android::hardware::drm::V1_0::IDrmPluginListener>& listener) override;
    ::android::hardware::Return<void> sendEvent(::android::hardware::drm::V1_0::EventType eventType, const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<uint8_t>& data) override;
    ::android::hardware::Return<void> sendExpirationUpdate(const ::android::hardware::hidl_vec<uint8_t>& sessionId, int64_t expiryTimeInMS) override;
    ::android::hardware::Return<void> sendKeysChange(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<::android::hardware::drm::V1_0::KeyStatus>& keyStatusList, bool hasNewUsableKey) override;

    // Methods from ::android::hardware::drm::V1_1::IDrmPlugin follow.
    ::android::hardware::Return<void> openSession_1_1(::android::hardware::drm::V1_1::SecurityLevel securityLevel, openSession_1_1_cb _hidl_cb) override;
    ::android::hardware::Return<void> getKeyRequest_1_1(const ::android::hardware::hidl_vec<uint8_t>& scope, const ::android::hardware::hidl_vec<uint8_t>& initData, const ::android::hardware::hidl_string& mimeType, ::android::hardware::drm::V1_0::KeyType keyType, const ::android::hardware::hidl_vec<::android::hardware::drm::V1_0::KeyValue>& optionalParameters, getKeyRequest_1_1_cb _hidl_cb) override;
    ::android::hardware::Return<void> getHdcpLevels(getHdcpLevels_cb _hidl_cb) override;
    ::android::hardware::Return<void> getNumberOfSessions(getNumberOfSessions_cb _hidl_cb) override;
    ::android::hardware::Return<void> getSecurityLevel(const ::android::hardware::hidl_vec<uint8_t>& sessionId, getSecurityLevel_cb _hidl_cb) override;
    ::android::hardware::Return<void> getMetrics(getMetrics_cb _hidl_cb) override;
    ::android::hardware::Return<void> getSecureStopIds(getSecureStopIds_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::drm::V1_0::Status> releaseSecureStops(const ::android::hardware::drm::V1_1::SecureStopRelease& ssRelease) override;
    ::android::hardware::Return<::android::hardware::drm::V1_0::Status> removeSecureStop(const ::android::hardware::hidl_vec<uint8_t>& secureStopId) override;
    ::android::hardware::Return<::android::hardware::drm::V1_0::Status> removeAllSecureStops() override;

    // Methods from ::android::hardware::drm::V1_2::IDrmPlugin follow.
    ::android::hardware::Return<void> getOfflineLicenseKeySetIds(getOfflineLicenseKeySetIds_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::drm::V1_0::Status> removeOfflineLicense(const ::android::hardware::hidl_vec<uint8_t>& keySetId) override;
    ::android::hardware::Return<void> getOfflineLicenseState(const ::android::hardware::hidl_vec<uint8_t>& keySetId, getOfflineLicenseState_cb _hidl_cb) override;
    ::android::hardware::Return<void> getKeyRequest_1_2(const ::android::hardware::hidl_vec<uint8_t>& scope, const ::android::hardware::hidl_vec<uint8_t>& initData, const ::android::hardware::hidl_string& mimeType, ::android::hardware::drm::V1_0::KeyType keyType, const ::android::hardware::hidl_vec<::android::hardware::drm::V1_0::KeyValue>& optionalParameters, getKeyRequest_1_2_cb _hidl_cb) override;
    ::android::hardware::Return<void> getProvisionRequest_1_2(const ::android::hardware::hidl_string& certificateType, const ::android::hardware::hidl_string& certificateAuthority, getProvisionRequest_1_2_cb _hidl_cb) override;
    ::android::hardware::Return<void> getHdcpLevels_1_2(getHdcpLevels_1_2_cb _hidl_cb) override;
    ::android::hardware::Return<void> sendSessionLostState(const ::android::hardware::hidl_vec<uint8_t>& sessionId) override;
    ::android::hardware::Return<void> sendKeysChange_1_2(const ::android::hardware::hidl_vec<uint8_t>& sessionId, const ::android::hardware::hidl_vec<::android::hardware::drm::V1_2::KeyStatus>& keyStatusList, bool hasNewUsableKey) override;

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

}  // namespace V1_2
}  // namespace drm
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_DRM_V1_2_BPHWDRMPLUGIN_H
