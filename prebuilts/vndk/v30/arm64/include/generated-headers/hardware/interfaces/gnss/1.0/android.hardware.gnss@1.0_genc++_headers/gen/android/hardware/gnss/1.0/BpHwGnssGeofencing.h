#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_BPHWGNSSGEOFENCING_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_BPHWGNSSGEOFENCING_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/gnss/1.0/IHwGnssGeofencing.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V1_0 {

struct BpHwGnssGeofencing : public ::android::hardware::BpInterface<IGnssGeofencing>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwGnssGeofencing(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IGnssGeofencing Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::gnss::V1_0::IGnssGeofencing follow.
    static ::android::hardware::Return<void>  _hidl_setCallback(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::gnss::V1_0::IGnssGeofenceCallback>& callback);
    static ::android::hardware::Return<void>  _hidl_addGeofence(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t geofenceId, double latitudeDegrees, double longitudeDegrees, double radiusMeters, ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition lastTransition, ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition> monitorTransitions, uint32_t notificationResponsivenessMs, uint32_t unknownTimerMs);
    static ::android::hardware::Return<void>  _hidl_pauseGeofence(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t geofenceId);
    static ::android::hardware::Return<void>  _hidl_resumeGeofence(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t geofenceId, ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition> monitorTransitions);
    static ::android::hardware::Return<void>  _hidl_removeGeofence(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t geofenceId);

    // Methods from ::android::hardware::gnss::V1_0::IGnssGeofencing follow.
    ::android::hardware::Return<void> setCallback(const ::android::sp<::android::hardware::gnss::V1_0::IGnssGeofenceCallback>& callback) override;
    ::android::hardware::Return<void> addGeofence(int32_t geofenceId, double latitudeDegrees, double longitudeDegrees, double radiusMeters, ::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition lastTransition, ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition> monitorTransitions, uint32_t notificationResponsivenessMs, uint32_t unknownTimerMs) override;
    ::android::hardware::Return<void> pauseGeofence(int32_t geofenceId) override;
    ::android::hardware::Return<void> resumeGeofence(int32_t geofenceId, ::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IGnssGeofenceCallback::GeofenceTransition> monitorTransitions) override;
    ::android::hardware::Return<void> removeGeofence(int32_t geofenceId) override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_BPHWGNSSGEOFENCING_H
