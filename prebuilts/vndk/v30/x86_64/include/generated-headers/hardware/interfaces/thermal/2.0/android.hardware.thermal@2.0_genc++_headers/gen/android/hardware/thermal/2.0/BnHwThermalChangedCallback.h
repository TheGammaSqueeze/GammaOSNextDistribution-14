#ifndef HIDL_GENERATED_ANDROID_HARDWARE_THERMAL_V2_0_BNHWTHERMALCHANGEDCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_THERMAL_V2_0_BNHWTHERMALCHANGEDCALLBACK_H

#include <android/hardware/thermal/2.0/IHwThermalChangedCallback.h>

namespace android {
namespace hardware {
namespace thermal {
namespace V2_0 {

struct BnHwThermalChangedCallback : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwThermalChangedCallback(const ::android::sp<IThermalChangedCallback> &_hidl_impl);
    explicit BnHwThermalChangedCallback(const ::android::sp<IThermalChangedCallback> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwThermalChangedCallback();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IThermalChangedCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IThermalChangedCallback> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::thermal::V2_0::IThermalChangedCallback follow.
    static ::android::status_t _hidl_notifyThrottling(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::thermal::V2_0::IThermalChangedCallback follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IThermalChangedCallback> _hidl_mImpl;
};

}  // namespace V2_0
}  // namespace thermal
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_THERMAL_V2_0_BNHWTHERMALCHANGEDCALLBACK_H
