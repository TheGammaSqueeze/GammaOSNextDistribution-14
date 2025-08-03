#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_1_BNHWGNSSCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_1_BNHWGNSSCALLBACK_H

#include <android/hardware/gnss/2.1/IHwGnssCallback.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V2_1 {

struct BnHwGnssCallback : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwGnssCallback(const ::android::sp<IGnssCallback> &_hidl_impl);
    explicit BnHwGnssCallback(const ::android::sp<IGnssCallback> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwGnssCallback();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IGnssCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IGnssCallback> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::gnss::V2_1::IGnssCallback follow.
    static ::android::status_t _hidl_gnssSetCapabilitiesCb_2_1(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_gnssSvStatusCb_2_1(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::gnss::V1_0::IGnssCallback follow.

    // Methods from ::android::hardware::gnss::V1_1::IGnssCallback follow.

    // Methods from ::android::hardware::gnss::V2_0::IGnssCallback follow.

    // Methods from ::android::hardware::gnss::V2_1::IGnssCallback follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IGnssCallback> _hidl_mImpl;
};

}  // namespace V2_1
}  // namespace gnss
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_1_BNHWGNSSCALLBACK_H
