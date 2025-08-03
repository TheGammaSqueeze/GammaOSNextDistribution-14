#ifndef HIDL_GENERATED_ANDROID_HARDWARE_POWER_V1_3_BNHWPOWER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_POWER_V1_3_BNHWPOWER_H

#include <android/hardware/power/1.3/IHwPower.h>

namespace android {
namespace hardware {
namespace power {
namespace V1_3 {

struct BnHwPower : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwPower(const ::android::sp<IPower> &_hidl_impl);
    explicit BnHwPower(const ::android::sp<IPower> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwPower();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IPower Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IPower> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::power::V1_3::IPower follow.
    static ::android::status_t _hidl_powerHintAsync_1_3(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::power::V1_0::IPower follow.

    // Methods from ::android::hardware::power::V1_1::IPower follow.

    // Methods from ::android::hardware::power::V1_2::IPower follow.

    // Methods from ::android::hardware::power::V1_3::IPower follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IPower> _hidl_mImpl;
};

}  // namespace V1_3
}  // namespace power
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_POWER_V1_3_BNHWPOWER_H
