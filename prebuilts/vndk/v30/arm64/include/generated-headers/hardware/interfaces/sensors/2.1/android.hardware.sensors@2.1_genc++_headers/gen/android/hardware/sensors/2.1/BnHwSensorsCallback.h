#ifndef HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V2_1_BNHWSENSORSCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V2_1_BNHWSENSORSCALLBACK_H

#include <android/hardware/sensors/2.1/IHwSensorsCallback.h>

namespace android {
namespace hardware {
namespace sensors {
namespace V2_1 {

struct BnHwSensorsCallback : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwSensorsCallback(const ::android::sp<ISensorsCallback> &_hidl_impl);
    explicit BnHwSensorsCallback(const ::android::sp<ISensorsCallback> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwSensorsCallback();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef ISensorsCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<ISensorsCallback> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::sensors::V2_1::ISensorsCallback follow.
    static ::android::status_t _hidl_onDynamicSensorsConnected_2_1(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::sensors::V2_0::ISensorsCallback follow.

    // Methods from ::android::hardware::sensors::V2_1::ISensorsCallback follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<ISensorsCallback> _hidl_mImpl;
};

}  // namespace V2_1
}  // namespace sensors
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V2_1_BNHWSENSORSCALLBACK_H
