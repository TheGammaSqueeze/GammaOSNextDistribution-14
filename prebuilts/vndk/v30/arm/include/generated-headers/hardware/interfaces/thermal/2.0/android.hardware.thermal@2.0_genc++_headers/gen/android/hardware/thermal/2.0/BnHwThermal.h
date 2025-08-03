#ifndef HIDL_GENERATED_ANDROID_HARDWARE_THERMAL_V2_0_BNHWTHERMAL_H
#define HIDL_GENERATED_ANDROID_HARDWARE_THERMAL_V2_0_BNHWTHERMAL_H

#include <android/hardware/thermal/2.0/IHwThermal.h>

namespace android {
namespace hardware {
namespace thermal {
namespace V2_0 {

struct BnHwThermal : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwThermal(const ::android::sp<IThermal> &_hidl_impl);
    explicit BnHwThermal(const ::android::sp<IThermal> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwThermal();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IThermal Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IThermal> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::thermal::V2_0::IThermal follow.
    static ::android::status_t _hidl_getCurrentTemperatures(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_getTemperatureThresholds(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_registerThermalChangedCallback(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_unregisterThermalChangedCallback(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_getCurrentCoolingDevices(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::thermal::V1_0::IThermal follow.

    // Methods from ::android::hardware::thermal::V2_0::IThermal follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IThermal> _hidl_mImpl;
};

}  // namespace V2_0
}  // namespace thermal
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_THERMAL_V2_0_BNHWTHERMAL_H
