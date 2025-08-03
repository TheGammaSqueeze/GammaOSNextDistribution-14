#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GATEKEEPER_V1_0_BPHWGATEKEEPER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GATEKEEPER_V1_0_BPHWGATEKEEPER_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/gatekeeper/1.0/IHwGatekeeper.h>

namespace android {
namespace hardware {
namespace gatekeeper {
namespace V1_0 {

struct BpHwGatekeeper : public ::android::hardware::BpInterface<IGatekeeper>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwGatekeeper(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IGatekeeper Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::gatekeeper::V1_0::IGatekeeper follow.
    static ::android::hardware::Return<void>  _hidl_enroll(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t uid, const ::android::hardware::hidl_vec<uint8_t>& currentPasswordHandle, const ::android::hardware::hidl_vec<uint8_t>& currentPassword, const ::android::hardware::hidl_vec<uint8_t>& desiredPassword, enroll_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_verify(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t uid, uint64_t challenge, const ::android::hardware::hidl_vec<uint8_t>& enrolledPasswordHandle, const ::android::hardware::hidl_vec<uint8_t>& providedPassword, verify_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_deleteUser(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t uid, deleteUser_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_deleteAllUsers(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, deleteAllUsers_cb _hidl_cb);

    // Methods from ::android::hardware::gatekeeper::V1_0::IGatekeeper follow.
    ::android::hardware::Return<void> enroll(uint32_t uid, const ::android::hardware::hidl_vec<uint8_t>& currentPasswordHandle, const ::android::hardware::hidl_vec<uint8_t>& currentPassword, const ::android::hardware::hidl_vec<uint8_t>& desiredPassword, enroll_cb _hidl_cb) override;
    ::android::hardware::Return<void> verify(uint32_t uid, uint64_t challenge, const ::android::hardware::hidl_vec<uint8_t>& enrolledPasswordHandle, const ::android::hardware::hidl_vec<uint8_t>& providedPassword, verify_cb _hidl_cb) override;
    ::android::hardware::Return<void> deleteUser(uint32_t uid, deleteUser_cb _hidl_cb) override;
    ::android::hardware::Return<void> deleteAllUsers(deleteAllUsers_cb _hidl_cb) override;

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
}  // namespace gatekeeper
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GATEKEEPER_V1_0_BPHWGATEKEEPER_H
