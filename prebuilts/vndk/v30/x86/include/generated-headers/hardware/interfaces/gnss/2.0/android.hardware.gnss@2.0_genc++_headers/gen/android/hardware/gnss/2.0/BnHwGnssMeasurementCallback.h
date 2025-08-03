#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_0_BNHWGNSSMEASUREMENTCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_0_BNHWGNSSMEASUREMENTCALLBACK_H

#include <android/hardware/gnss/2.0/IHwGnssMeasurementCallback.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V2_0 {

struct BnHwGnssMeasurementCallback : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwGnssMeasurementCallback(const ::android::sp<IGnssMeasurementCallback> &_hidl_impl);
    explicit BnHwGnssMeasurementCallback(const ::android::sp<IGnssMeasurementCallback> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwGnssMeasurementCallback();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IGnssMeasurementCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IGnssMeasurementCallback> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::gnss::V2_0::IGnssMeasurementCallback follow.
    static ::android::status_t _hidl_gnssMeasurementCb_2_0(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::gnss::V1_0::IGnssMeasurementCallback follow.

    // Methods from ::android::hardware::gnss::V1_1::IGnssMeasurementCallback follow.

    // Methods from ::android::hardware::gnss::V2_0::IGnssMeasurementCallback follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IGnssMeasurementCallback> _hidl_mImpl;
};

}  // namespace V2_0
}  // namespace gnss
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_0_BNHWGNSSMEASUREMENTCALLBACK_H
