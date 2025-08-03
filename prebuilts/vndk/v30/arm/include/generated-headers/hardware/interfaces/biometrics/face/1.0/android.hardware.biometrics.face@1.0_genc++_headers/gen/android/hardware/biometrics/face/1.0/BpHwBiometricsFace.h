#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FACE_V1_0_BPHWBIOMETRICSFACE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FACE_V1_0_BPHWBIOMETRICSFACE_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/biometrics/face/1.0/IHwBiometricsFace.h>

namespace android {
namespace hardware {
namespace biometrics {
namespace face {
namespace V1_0 {

struct BpHwBiometricsFace : public ::android::hardware::BpInterface<IBiometricsFace>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwBiometricsFace(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IBiometricsFace Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::biometrics::face::V1_0::IBiometricsFace follow.
    static ::android::hardware::Return<void>  _hidl_setCallback(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::biometrics::face::V1_0::IBiometricsFaceClientCallback>& clientCallback, setCallback_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status>  _hidl_setActiveUser(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t userId, const ::android::hardware::hidl_string& storePath);
    static ::android::hardware::Return<void>  _hidl_generateChallenge(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t challengeTimeoutSec, generateChallenge_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status>  _hidl_enroll(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& hat, uint32_t timeoutSec, const ::android::hardware::hidl_vec<::android::hardware::biometrics::face::V1_0::Feature>& disabledFeatures);
    static ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status>  _hidl_revokeChallenge(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status>  _hidl_setFeature(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::biometrics::face::V1_0::Feature feature, bool enabled, const ::android::hardware::hidl_vec<uint8_t>& hat, uint32_t faceId);
    static ::android::hardware::Return<void>  _hidl_getFeature(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::biometrics::face::V1_0::Feature feature, uint32_t faceId, getFeature_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getAuthenticatorId(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getAuthenticatorId_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status>  _hidl_cancel(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status>  _hidl_enumerate(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status>  _hidl_remove(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t faceId);
    static ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status>  _hidl_authenticate(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t operationId);
    static ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status>  _hidl_userActivity(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status>  _hidl_resetLockout(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& hat);

    // Methods from ::android::hardware::biometrics::face::V1_0::IBiometricsFace follow.
    ::android::hardware::Return<void> setCallback(const ::android::sp<::android::hardware::biometrics::face::V1_0::IBiometricsFaceClientCallback>& clientCallback, setCallback_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status> setActiveUser(int32_t userId, const ::android::hardware::hidl_string& storePath) override;
    ::android::hardware::Return<void> generateChallenge(uint32_t challengeTimeoutSec, generateChallenge_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status> enroll(const ::android::hardware::hidl_vec<uint8_t>& hat, uint32_t timeoutSec, const ::android::hardware::hidl_vec<::android::hardware::biometrics::face::V1_0::Feature>& disabledFeatures) override;
    ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status> revokeChallenge() override;
    ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status> setFeature(::android::hardware::biometrics::face::V1_0::Feature feature, bool enabled, const ::android::hardware::hidl_vec<uint8_t>& hat, uint32_t faceId) override;
    ::android::hardware::Return<void> getFeature(::android::hardware::biometrics::face::V1_0::Feature feature, uint32_t faceId, getFeature_cb _hidl_cb) override;
    ::android::hardware::Return<void> getAuthenticatorId(getAuthenticatorId_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status> cancel() override;
    ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status> enumerate() override;
    ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status> remove(uint32_t faceId) override;
    ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status> authenticate(uint64_t operationId) override;
    ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status> userActivity() override;
    ::android::hardware::Return<::android::hardware::biometrics::face::V1_0::Status> resetLockout(const ::android::hardware::hidl_vec<uint8_t>& hat) override;

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
}  // namespace face
}  // namespace biometrics
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FACE_V1_0_BPHWBIOMETRICSFACE_H
