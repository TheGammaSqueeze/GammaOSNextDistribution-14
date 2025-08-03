#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_BNHWGNSSDEBUG_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_BNHWGNSSDEBUG_H

#include <android/hardware/gnss/1.0/IHwGnssDebug.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V1_0 {

struct BnHwGnssDebug : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwGnssDebug(const ::android::sp<IGnssDebug> &_hidl_impl);
    explicit BnHwGnssDebug(const ::android::sp<IGnssDebug> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwGnssDebug();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IGnssDebug Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IGnssDebug> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::gnss::V1_0::IGnssDebug follow.
    static ::android::status_t _hidl_getDebugData(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::gnss::V1_0::IGnssDebug follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IGnssDebug> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace gnss
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_BNHWGNSSDEBUG_H
