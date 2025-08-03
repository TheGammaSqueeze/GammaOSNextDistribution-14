#ifndef HIDL_GENERATED_ANDROID_HARDWARE_THERMAL_V1_1_BNHWTHERMALCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_THERMAL_V1_1_BNHWTHERMALCALLBACK_H

#include <android/hardware/thermal/1.1/IHwThermalCallback.h>

namespace android {
namespace hardware {
namespace thermal {
namespace V1_1 {

struct BnHwThermalCallback : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwThermalCallback(const ::android::sp<IThermalCallback> &_hidl_impl);
    explicit BnHwThermalCallback(const ::android::sp<IThermalCallback> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwThermalCallback();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IThermalCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IThermalCallback> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::thermal::V1_1::IThermalCallback follow.
    static ::android::status_t _hidl_notifyThrottling(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::thermal::V1_1::IThermalCallback follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IThermalCallback> _hidl_mImpl;
};

}  // namespace V1_1
}  // namespace thermal
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_THERMAL_V1_1_BNHWTHERMALCALLBACK_H
