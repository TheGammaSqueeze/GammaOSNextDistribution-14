#ifndef HIDL_GENERATED_ANDROID_HARDWARE_KEYMASTER_V4_1_BPHWKEYMASTERDEVICE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_KEYMASTER_V4_1_BPHWKEYMASTERDEVICE_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/keymaster/4.1/IHwKeymasterDevice.h>

namespace android {
namespace hardware {
namespace keymaster {
namespace V4_1 {

struct BpHwKeymasterDevice : public ::android::hardware::BpInterface<IKeymasterDevice>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwKeymasterDevice(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IKeymasterDevice Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::keymaster::V4_1::IKeymasterDevice follow.
    static ::android::hardware::Return<::android::hardware::keymaster::V4_1::ErrorCode>  _hidl_deviceLocked(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, bool passwordOnly, const ::android::hardware::keymaster::V4_0::VerificationToken& verificationToken);
    static ::android::hardware::Return<::android::hardware::keymaster::V4_1::ErrorCode>  _hidl_earlyBootEnded(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);

    // Methods from ::android::hardware::keymaster::V4_0::IKeymasterDevice follow.
    ::android::hardware::Return<void> getHardwareInfo(getHardwareInfo_cb _hidl_cb) override;
    ::android::hardware::Return<void> getHmacSharingParameters(getHmacSharingParameters_cb _hidl_cb) override;
    ::android::hardware::Return<void> computeSharedHmac(const ::android::hardware::hidl_vec<::android::hardware::keymaster::V4_0::HmacSharingParameters>& params, computeSharedHmac_cb _hidl_cb) override;
    ::android::hardware::Return<void> verifyAuthorization(uint64_t operationHandle, const ::android::hardware::hidl_vec<::android::hardware::keymaster::V4_0::KeyParameter>& parametersToVerify, const ::android::hardware::keymaster::V4_0::HardwareAuthToken& authToken, verifyAuthorization_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::keymaster::V4_0::ErrorCode> addRngEntropy(const ::android::hardware::hidl_vec<uint8_t>& data) override;
    ::android::hardware::Return<void> generateKey(const ::android::hardware::hidl_vec<::android::hardware::keymaster::V4_0::KeyParameter>& keyParams, generateKey_cb _hidl_cb) override;
    ::android::hardware::Return<void> importKey(const ::android::hardware::hidl_vec<::android::hardware::keymaster::V4_0::KeyParameter>& keyParams, ::android::hardware::keymaster::V4_0::KeyFormat keyFormat, const ::android::hardware::hidl_vec<uint8_t>& keyData, importKey_cb _hidl_cb) override;
    ::android::hardware::Return<void> importWrappedKey(const ::android::hardware::hidl_vec<uint8_t>& wrappedKeyData, const ::android::hardware::hidl_vec<uint8_t>& wrappingKeyBlob, const ::android::hardware::hidl_vec<uint8_t>& maskingKey, const ::android::hardware::hidl_vec<::android::hardware::keymaster::V4_0::KeyParameter>& unwrappingParams, uint64_t passwordSid, uint64_t biometricSid, importWrappedKey_cb _hidl_cb) override;
    ::android::hardware::Return<void> getKeyCharacteristics(const ::android::hardware::hidl_vec<uint8_t>& keyBlob, const ::android::hardware::hidl_vec<uint8_t>& clientId, const ::android::hardware::hidl_vec<uint8_t>& appData, getKeyCharacteristics_cb _hidl_cb) override;
    ::android::hardware::Return<void> exportKey(::android::hardware::keymaster::V4_0::KeyFormat keyFormat, const ::android::hardware::hidl_vec<uint8_t>& keyBlob, const ::android::hardware::hidl_vec<uint8_t>& clientId, const ::android::hardware::hidl_vec<uint8_t>& appData, exportKey_cb _hidl_cb) override;
    ::android::hardware::Return<void> attestKey(const ::android::hardware::hidl_vec<uint8_t>& keyToAttest, const ::android::hardware::hidl_vec<::android::hardware::keymaster::V4_0::KeyParameter>& attestParams, attestKey_cb _hidl_cb) override;
    ::android::hardware::Return<void> upgradeKey(const ::android::hardware::hidl_vec<uint8_t>& keyBlobToUpgrade, const ::android::hardware::hidl_vec<::android::hardware::keymaster::V4_0::KeyParameter>& upgradeParams, upgradeKey_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::keymaster::V4_0::ErrorCode> deleteKey(const ::android::hardware::hidl_vec<uint8_t>& keyBlob) override;
    ::android::hardware::Return<::android::hardware::keymaster::V4_0::ErrorCode> deleteAllKeys() override;
    ::android::hardware::Return<::android::hardware::keymaster::V4_0::ErrorCode> destroyAttestationIds() override;
    ::android::hardware::Return<void> begin(::android::hardware::keymaster::V4_0::KeyPurpose purpose, const ::android::hardware::hidl_vec<uint8_t>& keyBlob, const ::android::hardware::hidl_vec<::android::hardware::keymaster::V4_0::KeyParameter>& inParams, const ::android::hardware::keymaster::V4_0::HardwareAuthToken& authToken, begin_cb _hidl_cb) override;
    ::android::hardware::Return<void> update(uint64_t operationHandle, const ::android::hardware::hidl_vec<::android::hardware::keymaster::V4_0::KeyParameter>& inParams, const ::android::hardware::hidl_vec<uint8_t>& input, const ::android::hardware::keymaster::V4_0::HardwareAuthToken& authToken, const ::android::hardware::keymaster::V4_0::VerificationToken& verificationToken, update_cb _hidl_cb) override;
    ::android::hardware::Return<void> finish(uint64_t operationHandle, const ::android::hardware::hidl_vec<::android::hardware::keymaster::V4_0::KeyParameter>& inParams, const ::android::hardware::hidl_vec<uint8_t>& input, const ::android::hardware::hidl_vec<uint8_t>& signature, const ::android::hardware::keymaster::V4_0::HardwareAuthToken& authToken, const ::android::hardware::keymaster::V4_0::VerificationToken& verificationToken, finish_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::keymaster::V4_0::ErrorCode> abort(uint64_t operationHandle) override;

    // Methods from ::android::hardware::keymaster::V4_1::IKeymasterDevice follow.
    ::android::hardware::Return<::android::hardware::keymaster::V4_1::ErrorCode> deviceLocked(bool passwordOnly, const ::android::hardware::keymaster::V4_0::VerificationToken& verificationToken) override;
    ::android::hardware::Return<::android::hardware::keymaster::V4_1::ErrorCode> earlyBootEnded() override;

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

}  // namespace V4_1
}  // namespace keymaster
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_KEYMASTER_V4_1_BPHWKEYMASTERDEVICE_H
