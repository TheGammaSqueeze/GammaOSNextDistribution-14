#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_BPHWGNSSGEOFENCECALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_BPHWGNSSGEOFENCECALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/gnss/1.0/IHwGnssGeofenceCallback.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V1_0 {

struct BpHwGnssGeofenceCallback : public ::android::hardware::BpInterface<IGnssGeofenceCallback>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwGnssGeofenceCallback(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IGnssGeofenceCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::gnss::V1_0::IGnssGeofenceCallback follow.
    static ::android::hardware::Return<void>  _hidl_gnssGeofenceTransitionCb(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t geofenceId, const ::android::hardware::gnss::V1_0::GnssLocation& location, ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition transition, int64_t timestamp);
    static ::android::hardware::Return<void>  _hidl_gnssGeofenceStatusCb(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability status, const ::android::hardware::gnss::V1_0::GnssLocation& lastLocation);
    static ::android::hardware::Return<void>  _hidl_gnssGeofenceAddCb(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t geofenceId, ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus status);
    static ::android::hardware::Return<void>  _hidl_gnssGeofenceRemoveCb(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t geofenceId, ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus status);
    static ::android::hardware::Return<void>  _hidl_gnssGeofencePauseCb(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t geofenceId, ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus status);
    static ::android::hardware::Return<void>  _hidl_gnssGeofenceResumeCb(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t geofenceId, ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus status);

    // Methods from ::android::hardware::gnss::V1_0::IGnssGeofenceCallback follow.
    ::android::hardware::Return<void> gnssGeofenceTransitionCb(int32_t geofenceId, const ::android::hardware::gnss::V1_0::GnssLocation& location, ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition transition, int64_t timestamp) override;
    ::android::hardware::Return<void> gnssGeofenceStatusCb(::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceAvailability status, const ::android::hardware::gnss::V1_0::GnssLocation& lastLocation) override;
    ::android::hardware::Return<void> gnssGeofenceAddCb(int32_t geofenceId, ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus status) override;
    ::android::hardware::Return<void> gnssGeofenceRemoveCb(int32_t geofenceId, ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus status) override;
    ::android::hardware::Return<void> gnssGeofencePauseCb(int32_t geofenceId, ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus status) override;
    ::android::hardware::Return<void> gnssGeofenceResumeCb(int32_t geofenceId, ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceStatus status) override;

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> interfaceChain(interfaceChain_cb _hidl_cb) override;
    ::android::hardware::Return<void> debug(const ::android::hardware::hidl_handle& fd, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& options) override;
    ::android::hardware::Return<void> interfaceDescriptor(interfaceDescriptor_cb _hidl_cb) override;
    ::android::hardware::Return<void> getHashChain(getHashChain_cb _hidl_cb) override;
    ::android::hardware::Return<void> setHALInstrumentation() override;
    ::android::hardware::Return<bool> linkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient, uint64_t cookie) override;
    ::android::hardware::Return<void> ping() override;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb) override;
    ::android::hardware::Return<void> notifySyspropsChanged() override;
    ::android::hardware::Return<bool> unlinkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient) override;

private:
    std::mutex _hidl_mMutex;
    std::vector<::android::sp<::android::hardware::hidl_binder_death_recipient>> _hidl_mDeathRecipients;
};

}  // namespace V1_0
}  // namespace gnss
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_BPHWGNSSGEOFENCECALLBACK_H
