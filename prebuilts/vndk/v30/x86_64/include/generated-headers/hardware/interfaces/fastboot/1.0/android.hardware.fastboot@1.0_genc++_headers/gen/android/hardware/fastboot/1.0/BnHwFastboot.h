#ifndef HIDL_GENERATED_ANDROID_HARDWARE_FASTBOOT_V1_0_BNHWFASTBOOT_H
#define HIDL_GENERATED_ANDROID_HARDWARE_FASTBOOT_V1_0_BNHWFASTBOOT_H

#include <android/hardware/fastboot/1.0/IHwFastboot.h>

namespace android {
namespace hardware {
namespace fastboot {
namespace V1_0 {

struct BnHwFastboot : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwFastboot(const ::android::sp<IFastboot> &_hidl_impl);
    explicit BnHwFastboot(const ::android::sp<IFastboot> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwFastboot();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IFastboot Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IFastboot> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::fastboot::V1_0::IFastboot follow.
    static ::android::status_t _hidl_getPartitionType(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_doOemCommand(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_getVariant(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_getOffModeChargeState(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_getBatteryVoltageFlashingThreshold(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::fastboot::V1_0::IFastboot follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IFastboot> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace fastboot
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_FASTBOOT_V1_0_BNHWFASTBOOT_H
