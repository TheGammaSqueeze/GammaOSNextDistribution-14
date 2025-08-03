#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_MEASUREMENT_CORRECTIONS_V1_1_BNHWMEASUREMENTCORRECTIONS_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_MEASUREMENT_CORRECTIONS_V1_1_BNHWMEASUREMENTCORRECTIONS_H

#include <android/hardware/gnss/measurement_corrections/1.1/IHwMeasurementCorrections.h>

namespace android {
namespace hardware {
namespace gnss {
namespace measurement_corrections {
namespace V1_1 {

struct BnHwMeasurementCorrections : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwMeasurementCorrections(const ::android::sp<IMeasurementCorrections> &_hidl_impl);
    explicit BnHwMeasurementCorrections(const ::android::sp<IMeasurementCorrections> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwMeasurementCorrections();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IMeasurementCorrections Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IMeasurementCorrections> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::gnss::measurement_corrections::V1_1::IMeasurementCorrections follow.
    static ::android::status_t _hidl_setCorrections_1_1(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::gnss::measurement_corrections::V1_0::IMeasurementCorrections follow.

    // Methods from ::android::hardware::gnss::measurement_corrections::V1_1::IMeasurementCorrections follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IMeasurementCorrections> _hidl_mImpl;
};

}  // namespace V1_1
}  // namespace measurement_corrections
}  // namespace gnss
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_MEASUREMENT_CORRECTIONS_V1_1_BNHWMEASUREMENTCORRECTIONS_H
