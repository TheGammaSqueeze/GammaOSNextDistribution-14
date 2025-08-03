#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_BNHWGNSSGEOFENCECALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_BNHWGNSSGEOFENCECALLBACK_H

#include <android/hardware/gnss/1.0/IHwGnssGeofenceCallback.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V1_0 {

struct BnHwGnssGeofenceCallback : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwGnssGeofenceCallback(const ::android::sp<IGnssGeofenceCallback> &_hidl_impl);
    explicit BnHwGnssGeofenceCallback(const ::android::sp<IGnssGeofenceCallback> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwGnssGeofenceCallback();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IGnssGeofenceCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IGnssGeofenceCallback> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::gnss::V1_0::IGnssGeofenceCallback follow.
    static ::android::status_t _hidl_gnssGeofenceTransitionCb(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_gnssGeofenceStatusCb(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_gnssGeofenceAddCb(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_gnssGeofenceRemoveCb(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_gnssGeofencePauseCb(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_gnssGeofenceResumeCb(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::gnss::V1_0::IGnssGeofenceCallback follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IGnssGeofenceCallback> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace gnss
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_BNHWGNSSGEOFENCECALLBACK_H
