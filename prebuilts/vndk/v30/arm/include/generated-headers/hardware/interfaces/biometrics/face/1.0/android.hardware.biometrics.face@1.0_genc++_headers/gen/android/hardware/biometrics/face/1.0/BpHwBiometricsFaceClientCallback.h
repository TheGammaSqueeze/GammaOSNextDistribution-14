#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FACE_V1_0_BPHWBIOMETRICSFACECLIENTCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FACE_V1_0_BPHWBIOMETRICSFACECLIENTCALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/biometrics/face/1.0/IHwBiometricsFaceClientCallback.h>

namespace android {
namespace hardware {
namespace biometrics {
namespace face {
namespace V1_0 {

struct BpHwBiometricsFaceClientCallback : public ::android::hardware::BpInterface<IBiometricsFaceClientCallback>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwBiometricsFaceClientCallback(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IBiometricsFaceClientCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::biometrics::face::V1_0::IBiometricsFaceClientCallback follow.
    static ::android::hardware::Return<void>  _hidl_onEnrollResult(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t deviceId, uint32_t faceId, int32_t userId, uint32_t remaining);
    static ::android::hardware::Return<void>  _hidl_onAuthenticated(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t deviceId, uint32_t faceId, int32_t userId, const ::android::hardware::hidl_vec<uint8_t>& token);
    static ::android::hardware::Return<void>  _hidl_onAcquired(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t deviceId, int32_t userId, ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo acquiredInfo, int32_t vendorCode);
    static ::android::hardware::Return<void>  _hidl_onError(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t deviceId, int32_t userId, ::android::hardware::biometrics::face::V1_0::FaceError error, int32_t vendorCode);
    static ::android::hardware::Return<void>  _hidl_onRemoved(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t deviceId, const ::android::hardware::hidl_vec<uint32_t>& removed, int32_t userId);
    static ::android::hardware::Return<void>  _hidl_onEnumerate(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t deviceId, const ::android::hardware::hidl_vec<uint32_t>& faceIds, int32_t userId);
    static ::android::hardware::Return<void>  _hidl_onLockoutChanged(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t duration);

    // Methods from ::android::hardware::biometrics::face::V1_0::IBiometricsFaceClientCallback follow.
    ::android::hardware::Return<void> onEnrollResult(uint64_t deviceId, uint32_t faceId, int32_t userId, uint32_t remaining) override;
    ::android::hardware::Return<void> onAuthenticated(uint64_t deviceId, uint32_t faceId, int32_t userId, const ::android::hardware::hidl_vec<uint8_t>& token) override;
    ::android::hardware::Return<void> onAcquired(uint64_t deviceId, int32_t userId, ::android::hardware::biometrics::face::V1_0::FaceAcquiredInfo acquiredInfo, int32_t vendorCode) override;
    ::android::hardware::Return<void> onError(uint64_t deviceId, int32_t userId, ::android::hardware::biometrics::face::V1_0::FaceError error, int32_t vendorCode) override;
    ::android::hardware::Return<void> onRemoved(uint64_t deviceId, const ::android::hardware::hidl_vec<uint32_t>& removed, int32_t userId) override;
    ::android::hardware::Return<void> onEnumerate(uint64_t deviceId, const ::android::hardware::hidl_vec<uint32_t>& faceIds, int32_t userId) override;
    ::android::hardware::Return<void> onLockoutChanged(uint64_t duration) override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BIOMETRICS_FACE_V1_0_BPHWBIOMETRICSFACECLIENTCALLBACK_H
