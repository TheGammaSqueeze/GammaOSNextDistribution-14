#ifndef HIDL_GENERATED_ANDROID_SYSTEM_SUSPEND_V1_0_BPHWSYSTEMSUSPEND_H
#define HIDL_GENERATED_ANDROID_SYSTEM_SUSPEND_V1_0_BPHWSYSTEMSUSPEND_H

#include <hidl/HidlTransportSupport.h>

#include <android/system/suspend/1.0/IHwSystemSuspend.h>

namespace android {
namespace system {
namespace suspend {
namespace V1_0 {

struct BpHwSystemSuspend : public ::android::hardware::BpInterface<ISystemSuspend>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwSystemSuspend(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ISystemSuspend Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::system::suspend::V1_0::ISystemSuspend follow.
    static ::android::hardware::Return<::android::sp<::android::system::suspend::V1_0::IWakeLock>>  _hidl_acquireWakeLock(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::system::suspend::V1_0::WakeLockType type, const ::android::hardware::hidl_string& debugName);

    // Methods from ::android::system::suspend::V1_0::ISystemSuspend follow.
    ::android::hardware::Return<::android::sp<::android::system::suspend::V1_0::IWakeLock>> acquireWakeLock(::android::system::suspend::V1_0::WakeLockType type, const ::android::hardware::hidl_string& debugName) override;

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
}  // namespace suspend
}  // namespace system
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_SYSTEM_SUSPEND_V1_0_BPHWSYSTEMSUSPEND_H
