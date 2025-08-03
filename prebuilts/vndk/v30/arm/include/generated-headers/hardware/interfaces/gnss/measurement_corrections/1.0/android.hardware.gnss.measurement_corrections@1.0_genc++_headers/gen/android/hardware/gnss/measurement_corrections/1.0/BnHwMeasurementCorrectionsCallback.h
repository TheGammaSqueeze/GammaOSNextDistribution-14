#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_MEASUREMENT_CORRECTIONS_V1_0_BNHWMEASUREMENTCORRECTIONSCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_MEASUREMENT_CORRECTIONS_V1_0_BNHWMEASUREMENTCORRECTIONSCALLBACK_H

#include <android/hardware/gnss/measurement_corrections/1.0/IHwMeasurementCorrectionsCallback.h>

namespace android {
namespace hardware {
namespace gnss {
namespace measurement_corrections {
namespace V1_0 {

struct BnHwMeasurementCorrectionsCallback : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwMeasurementCorrectionsCallback(const ::android::sp<IMeasurementCorrectionsCallback> &_hidl_impl);
    explicit BnHwMeasurementCorrectionsCallback(const ::android::sp<IMeasurementCorrectionsCallback> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwMeasurementCorrectionsCallback();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IMeasurementCorrectionsCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IMeasurementCorrectionsCallback> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::gnss::measurement_corrections::V1_0::IMeasurementCorrectionsCallback follow.
    static ::android::status_t _hidl_setCapabilitiesCb(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::gnss::measurement_corrections::V1_0::IMeasurementCorrectionsCallback follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IMeasurementCorrectionsCallback> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace measurement_corrections
}  // namespace gnss
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_MEASUREMENT_CORRECTIONS_V1_0_BNHWMEASUREMENTCORRECTIONSCALLBACK_H
