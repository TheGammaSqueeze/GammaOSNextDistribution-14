#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_0_BPHWGNSSCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_0_BPHWGNSSCALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/gnss/2.0/IHwGnssCallback.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V2_0 {

struct BpHwGnssCallback : public ::android::hardware::BpInterface<IGnssCallback>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwGnssCallback(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IGnssCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::gnss::V2_0::IGnssCallback follow.
    static ::android::hardware::Return<void>  _hidl_gnssSetCapabilitiesCb_2_0(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::hidl_bitfield<::android::hardware::gnss::V2_0::IGnssCallback::Capabilities> capabilities);
    static ::android::hardware::Return<void>  _hidl_gnssLocationCb_2_0(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::gnss::V2_0::GnssLocation& location);
    static ::android::hardware::Return<void>  _hidl_gnssRequestLocationCb_2_0(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, bool independentFromGnss, bool isUserEmergency);
    static ::android::hardware::Return<void>  _hidl_gnssSvStatusCb_2_0(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::gnss::V2_0::IGnssCallback::GnssSvInfo>& svInfoList);

    // Methods from ::android::hardware::gnss::V1_0::IGnssCallback follow.
    ::android::hardware::Return<void> gnssLocationCb(const ::android::hardware::gnss::V1_0::GnssLocation& location) override;
    ::android::hardware::Return<void> gnssStatusCb(::android::hardware::gnss::V1_0::IGnssCallback::GnssStatusValue status) override;
    ::android::hardware::Return<void> gnssSvStatusCb(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSvStatus& svInfo) override;
    ::android::hardware::Return<void> gnssNmeaCb(int64_t timestamp, const ::android::hardware::hidl_string& nmea) override;
    ::android::hardware::Return<void> gnssSetCapabilitesCb(::android::hardware::hidl_bitfield<::android::hardware::gnss::V1_0::IGnssCallback::Capabilities> capabilities) override;
    ::android::hardware::Return<void> gnssAcquireWakelockCb() override;
    ::android::hardware::Return<void> gnssReleaseWakelockCb() override;
    ::android::hardware::Return<void> gnssRequestTimeCb() override;
    ::android::hardware::Return<void> gnssSetSystemInfoCb(const ::android::hardware::gnss::V1_0::IGnssCallback::GnssSystemInfo& info) override;

    // Methods from ::android::hardware::gnss::V1_1::IGnssCallback follow.
    ::android::hardware::Return<void> gnssNameCb(const ::android::hardware::hidl_string& name) override;
    ::android::hardware::Return<void> gnssRequestLocationCb(bool independentFromGnss) override;

    // Methods from ::android::hardware::gnss::V2_0::IGnssCallback follow.
    ::android::hardware::Return<void> gnssSetCapabilitiesCb_2_0(::android::hardware::hidl_bitfield<::android::hardware::gnss::V2_0::IGnssCallback::Capabilities> capabilities) override;
    ::android::hardware::Return<void> gnssLocationCb_2_0(const ::android::hardware::gnss::V2_0::GnssLocation& location) override;
    ::android::hardware::Return<void> gnssRequestLocationCb_2_0(bool independentFromGnss, bool isUserEmergency) override;
    ::android::hardware::Return<void> gnssSvStatusCb_2_0(const ::android::hardware::hidl_vec<::android::hardware::gnss::V2_0::IGnssCallback::GnssSvInfo>& svInfoList) override;

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

}  // namespace V2_0
}  // namespace gnss
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_0_BPHWGNSSCALLBACK_H
