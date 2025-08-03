#ifndef HIDL_GENERATED_ANDROID_HARDWARE_MEMTRACK_V1_0_BPHWMEMTRACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_MEMTRACK_V1_0_BPHWMEMTRACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/memtrack/1.0/IHwMemtrack.h>

namespace android {
namespace hardware {
namespace memtrack {
namespace V1_0 {

struct BpHwMemtrack : public ::android::hardware::BpInterface<IMemtrack>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwMemtrack(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IMemtrack Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::memtrack::V1_0::IMemtrack follow.
    static ::android::hardware::Return<void>  _hidl_getMemory(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t pid, ::android::hardware::memtrack::V1_0::MemtrackType type, getMemory_cb _hidl_cb);

    // Methods from ::android::hardware::memtrack::V1_0::IMemtrack follow.
    ::android::hardware::Return<void> getMemory(int32_t pid, ::android::hardware::memtrack::V1_0::MemtrackType type, getMemory_cb _hidl_cb) override;

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
}  // namespace memtrack
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_MEMTRACK_V1_0_BPHWMEMTRACK_H
