#ifndef HIDL_GENERATED_ANDROID_HARDWARE_POWER_V1_3_BPHWPOWER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_POWER_V1_3_BPHWPOWER_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/power/1.3/IHwPower.h>

namespace android {
namespace hardware {
namespace power {
namespace V1_3 {

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

    // Methods from ::android::hardware::power::V1_3::IPower follow.
    static ::android::hardware::Return<void>  _hidl_powerHintAsync_1_3(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::power::V1_3::PowerHint hint, int32_t data);

    // Methods from ::android::hardware::power::V1_0::IPower follow.
    ::android::hardware::Return<void> setInteractive(bool interactive) override;
    ::android::hardware::Return<void> powerHint(::android::hardware::power::V1_0::PowerHint hint, int32_t data) override;
    ::android::hardware::Return<void> setFeature(::android::hardware::power::V1_0::Feature feature, bool activate) override;
    ::android::hardware::Return<void> getPlatformLowPowerStats(getPlatformLowPowerStats_cb _hidl_cb) override;

    // Methods from ::android::hardware::power::V1_1::IPower follow.
    ::android::hardware::Return<void> getSubsystemLowPowerStats(getSubsystemLowPowerStats_cb _hidl_cb) override;
    ::android::hardware::Return<void> powerHintAsync(::android::hardware::power::V1_0::PowerHint hint, int32_t data) override;

    // Methods from ::android::hardware::power::V1_2::IPower follow.
    ::android::hardware::Return<void> powerHintAsync_1_2(::android::hardware::power::V1_2::PowerHint hint, int32_t data) override;

    // Methods from ::android::hardware::power::V1_3::IPower follow.
    ::android::hardware::Return<void> powerHintAsync_1_3(::android::hardware::power::V1_3::PowerHint hint, int32_t data) override;

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

}  // namespace V1_3
}  // namespace power
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_POWER_V1_3_BPHWPOWER_H
