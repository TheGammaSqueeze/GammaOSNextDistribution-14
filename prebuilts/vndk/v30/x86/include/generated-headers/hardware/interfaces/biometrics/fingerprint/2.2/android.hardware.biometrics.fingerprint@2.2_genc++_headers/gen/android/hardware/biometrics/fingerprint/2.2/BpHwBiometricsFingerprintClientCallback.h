#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FINGERPRINT_V2_2_BPHWBIOMETRICSFINGERPRINTCLIENTCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FINGERPRINT_V2_2_BPHWBIOMETRICSFINGERPRINTCLIENTCALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/biometrics/fingerprint/2.2/IHwBiometricsFingerprintClientCallback.h>

namespace android {
namespace hardware {
namespace biometrics {
namespace fingerprint {
namespace V2_2 {

struct BpHwBiometricsFingerprintClientCallback : public ::android::hardware::BpInterface<IBiometricsFingerprintClientCallback>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwBiometricsFingerprintClientCallback(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IBiometricsFingerprintClientCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::biometrics::fingerprint::V2_2::IBiometricsFingerprintClientCallback follow.
    static ::android::hardware::Return<void>  _hidl_onAcquired_2_2(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t deviceId, ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo acquiredInfo, int32_t vendorCode);

    // Methods from ::android::hardware::biometrics::fingerprint::V2_1::IBiometricsFingerprintClientCallback follow.
    ::android::hardware::Return<void> onEnrollResult(uint64_t deviceId, uint32_t fingerId, uint32_t groupId, uint32_t remaining) override;
    ::android::hardware::Return<void> onAcquired(uint64_t deviceId, ::android::hardware::biometrics::fingerprint::V2_1::FingerprintAcquiredInfo acquiredInfo, int32_t vendorCode) override;
    ::android::hardware::Return<void> onAuthenticated(uint64_t deviceId, uint32_t fingerId, uint32_t groupId, const ::android::hardware::hidl_vec<uint8_t>& token) override;
    ::android::hardware::Return<void> onError(uint64_t deviceId, ::android::hardware::biometrics::fingerprint::V2_1::FingerprintError error, int32_t vendorCode) override;
    ::android::hardware::Return<void> onRemoved(uint64_t deviceId, uint32_t fingerId, uint32_t groupId, uint32_t remaining) override;
    ::android::hardware::Return<void> onEnumerate(uint64_t deviceId, uint32_t fingerId, uint32_t groupId, uint32_t remaining) override;

    // Methods from ::android::hardware::biometrics::fingerprint::V2_2::IBiometricsFingerprintClientCallback follow.
    ::android::hardware::Return<void> onAcquired_2_2(uint64_t deviceId, ::android::hardware::biometrics::fingerprint::V2_2::FingerprintAcquiredInfo acquiredInfo, int32_t vendorCode) override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FINGERPRINT_V2_2_BPHWBIOMETRICSFINGERPRINTCLIENTCALLBACK_H
