#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_BPHWGNSS_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_BPHWGNSS_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/gnss/1.0/IHwGnss.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V1_0 {

struct BpHwGnss : public ::android::hardware::BpInterface<IGnss>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwGnss(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IGnss Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::gnss::V1_0::IGnss follow.
    static ::android::hardware::Return<bool>  _hidl_setCallback(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::gnss::V1_0::IGnssCallback>& callback);
    static ::android::hardware::Return<bool>  _hidl_start(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<bool>  _hidl_stop(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_cleanup(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<bool>  _hidl_injectTime(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int64_t timeMs, int64_t timeReferenceMs, int32_t uncertaintyMs);
    static ::android::hardware::Return<bool>  _hidl_injectLocation(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, double latitudeDegrees, double longitudeDegrees, float accuracyMeters);
    static ::android::hardware::Return<void>  _hidl_deleteAidingData(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::gnss::V1_0::IGnss::GnssAidingData aidingDataFlags);
    static ::android::hardware::Return<bool>  _hidl_setPositionMode(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::gnss::V1_0::IGnss::GnssPositionMode mode, ::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence recurrence, uint32_t minIntervalMs, uint32_t preferredAccuracyMeters, uint32_t preferredTimeMs);
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IAGnssRil>>  _hidl_getExtensionAGnssRil(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssGeofencing>>  _hidl_getExtensionGnssGeofencing(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IAGnss>>  _hidl_getExtensionAGnss(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssNi>>  _hidl_getExtensionGnssNi(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssMeasurement>>  _hidl_getExtensionGnssMeasurement(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssNavigationMessage>>  _hidl_getExtensionGnssNavigationMessage(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssXtra>>  _hidl_getExtensionXtra(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssConfiguration>>  _hidl_getExtensionGnssConfiguration(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssDebug>>  _hidl_getExtensionGnssDebug(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssBatching>>  _hidl_getExtensionGnssBatching(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);

    // Methods from ::android::hardware::gnss::V1_0::IGnss follow.
    ::android::hardware::Return<bool> setCallback(const ::android::sp<::android::hardware::gnss::V1_0::IGnssCallback>& callback) override;
    ::android::hardware::Return<bool> start() override;
    ::android::hardware::Return<bool> stop() override;
    ::android::hardware::Return<void> cleanup() override;
    ::android::hardware::Return<bool> injectTime(int64_t timeMs, int64_t timeReferenceMs, int32_t uncertaintyMs) override;
    ::android::hardware::Return<bool> injectLocation(double latitudeDegrees, double longitudeDegrees, float accuracyMeters) override;
    ::android::hardware::Return<void> deleteAidingData(::android::hardware::gnss::V1_0::IGnss::GnssAidingData aidingDataFlags) override;
    ::android::hardware::Return<bool> setPositionMode(::android::hardware::gnss::V1_0::IGnss::GnssPositionMode mode, ::android::hardware::gnss::V1_0::IGnss::GnssPositionRecurrence recurrence, uint32_t minIntervalMs, uint32_t preferredAccuracyMeters, uint32_t preferredTimeMs) override;
    ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IAGnssRil>> getExtensionAGnssRil() override;
    ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssGeofencing>> getExtensionGnssGeofencing() override;
    ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IAGnss>> getExtensionAGnss() override;
    ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssNi>> getExtensionGnssNi() override;
    ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssMeasurement>> getExtensionGnssMeasurement() override;
    ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssNavigationMessage>> getExtensionGnssNavigationMessage() override;
    ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssXtra>> getExtensionXtra() override;
    ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssConfiguration>> getExtensionGnssConfiguration() override;
    ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssDebug>> getExtensionGnssDebug() override;
    ::android::hardware::Return<::android::sp<::android::hardware::gnss::V1_0::IGnssBatching>> getExtensionGnssBatching() override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V1_0_BPHWGNSS_H
