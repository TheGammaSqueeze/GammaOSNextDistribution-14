#ifndef HIDL_GENERATED_ANDROID_HARDWARE_VIBRATOR_V1_2_BNHWVIBRATOR_H
#define HIDL_GENERATED_ANDROID_HARDWARE_VIBRATOR_V1_2_BNHWVIBRATOR_H

#include <android/hardware/vibrator/1.2/IHwVibrator.h>

namespace android {
namespace hardware {
namespace vibrator {
namespace V1_2 {

struct BnHwVibrator : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwVibrator(const ::android::sp<IVibrator> &_hidl_impl);
    explicit BnHwVibrator(const ::android::sp<IVibrator> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwVibrator();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IVibrator Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IVibrator> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::vibrator::V1_2::IVibrator follow.
    static ::android::status_t _hidl_perform_1_2(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::vibrator::V1_0::IVibrator follow.

    // Methods from ::android::hardware::vibrator::V1_1::IVibrator follow.

    // Methods from ::android::hardware::vibrator::V1_2::IVibrator follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IVibrator> _hidl_mImpl;
};

}  // namespace V1_2
}  // namespace vibrator
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_VIBRATOR_V1_2_BNHWVIBRATOR_H
