#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FINGERPRINT_V2_2_BPHWBIOMETRICSFINGERPRINT_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FINGERPRINT_V2_2_BPHWBIOMETRICSFINGERPRINT_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/biometrics/fingerprint/2.2/IHwBiometricsFingerprint.h>

namespace android {
namespace hardware {
namespace biometrics {
namespace fingerprint {
namespace V2_2 {

struct BpHwBiometricsFingerprint : public ::android::hardware::BpInterface<IBiometricsFingerprint>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwBiometricsFingerprint(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IBiometricsFingerprint Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;


    // Methods from ::android::hardware::biometrics::fingerprint::V2_1::IBiometricsFingerprint follow.
    ::android::hardware::Return<uint64_t> setNotify(const ::android::sp<::android::hardware::biometrics::fingerprint::V2_1::IBiometricsFingerprintClientCallback>& clientCallback) override;
    ::android::hardware::Return<uint64_t> preEnroll() override;
    ::android::hardware::Return<::android::hardware::biometrics::fingerprint::V2_1::RequestStatus> enroll(const ::android::hardware::hidl_array<uint8_t, 69>& hat, uint32_t gid, uint32_t timeoutSec) override;
    ::android::hardware::Return<::android::hardware::biometrics::fingerprint::V2_1::RequestStatus> postEnroll() override;
    ::android::hardware::Return<uint64_t> getAuthenticatorId() override;
    ::android::hardware::Return<::android::hardware::biometrics::fingerprint::V2_1::RequestStatus> cancel() override;
    ::android::hardware::Return<::android::hardware::biometrics::fingerprint::V2_1::RequestStatus> enumerate() override;
    ::android::hardware::Return<::android::hardware::biometrics::fingerprint::V2_1::RequestStatus> remove(uint32_t gid, uint32_t fid) override;
    ::android::hardware::Return<::android::hardware::biometrics::fingerprint::V2_1::RequestStatus> setActiveGroup(uint32_t gid, const ::android::hardware::hidl_string& storePath) override;
    ::android::hardware::Return<::android::hardware::biometrics::fingerprint::V2_1::RequestStatus> authenticate(uint64_t operationId, uint32_t gid) override;

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

}  // namespace V2_2
}  // namespace fingerprint
}  // namespace biometrics
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FINGERPRINT_V2_2_BPHWBIOMETRICSFINGERPRINT_H
