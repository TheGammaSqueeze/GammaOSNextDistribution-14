#ifndef HIDL_GENERATED_ANDROID_HARDWARE_OEMLOCK_V1_0_BPHWOEMLOCK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_OEMLOCK_V1_0_BPHWOEMLOCK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/oemlock/1.0/IHwOemLock.h>

namespace android {
namespace hardware {
namespace oemlock {
namespace V1_0 {

struct BpHwOemLock : public ::android::hardware::BpInterface<IOemLock>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwOemLock(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IOemLock Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::oemlock::V1_0::IOemLock follow.
    static ::android::hardware::Return<void>  _hidl_getName(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getName_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::oemlock::V1_0::OemLockSecureStatus>  _hidl_setOemUnlockAllowedByCarrier(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, bool allowed, const ::android::hardware::hidl_vec<uint8_t>& signature);
    static ::android::hardware::Return<void>  _hidl_isOemUnlockAllowedByCarrier(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, isOemUnlockAllowedByCarrier_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::oemlock::V1_0::OemLockStatus>  _hidl_setOemUnlockAllowedByDevice(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, bool allowed);
    static ::android::hardware::Return<void>  _hidl_isOemUnlockAllowedByDevice(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, isOemUnlockAllowedByDevice_cb _hidl_cb);

    // Methods from ::android::hardware::oemlock::V1_0::IOemLock follow.
    ::android::hardware::Return<void> getName(getName_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::oemlock::V1_0::OemLockSecureStatus> setOemUnlockAllowedByCarrier(bool allowed, const ::android::hardware::hidl_vec<uint8_t>& signature) override;
    ::android::hardware::Return<void> isOemUnlockAllowedByCarrier(isOemUnlockAllowedByCarrier_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::oemlock::V1_0::OemLockStatus> setOemUnlockAllowedByDevice(bool allowed) override;
    ::android::hardware::Return<void> isOemUnlockAllowedByDevice(isOemUnlockAllowedByDevice_cb _hidl_cb) override;

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
}  // namespace oemlock
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_OEMLOCK_V1_0_BPHWOEMLOCK_H
