#ifndef HIDL_GENERATED_ANDROID_HARDWARE_POWER_V1_0_BPHWPOWER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_POWER_V1_0_BPHWPOWER_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/power/1.0/IHwPower.h>

namespace android {
namespace hardware {
namespace power {
namespace V1_0 {

struct BpHwPower : public ::android::hardware::BpInterface<IPower>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwPower(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IPower Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::power::V1_0::IPower follow.
    static ::android::hardware::Return<void>  _hidl_setInteractive(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, bool interactive);
    static ::android::hardware::Return<void>  _hidl_powerHint(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::power::V1_0::PowerHint hint, int32_t data);
    static ::android::hardware::Return<void>  _hidl_setFeature(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::power::V1_0::Feature feature, bool activate);
    static ::android::hardware::Return<void>  _hidl_getPlatformLowPowerStats(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getPlatformLowPowerStats_cb _hidl_cb);

    // Methods from ::android::hardware::power::V1_0::IPower follow.
    ::android::hardware::Return<void> setInteractive(bool interactive) override;
    ::android::hardware::Return<void> powerHint(::android::hardware::power::V1_0::PowerHint hint, int32_t data) override;
    ::android::hardware::Return<void> setFeature(::android::hardware::power::V1_0::Feature feature, bool activate) override;
    ::android::hardware::Return<void> getPlatformLowPowerStats(getPlatformLowPowerStats_cb _hidl_cb) override;

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
}  // namespace power
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_POWER_V1_0_BPHWPOWER_H
