#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_0_BPHWAGNSSRIL_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_0_BPHWAGNSSRIL_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/gnss/2.0/IHwAGnssRil.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V2_0 {

struct BpHwAGnssRil : public ::android::hardware::BpInterface<IAGnssRil>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwAGnssRil(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IAGnssRil Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::gnss::V2_0::IAGnssRil follow.
    static ::android::hardware::Return<bool>  _hidl_updateNetworkState_2_0(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkAttributes& attributes);

    // Methods from ::android::hardware::gnss::V1_0::IAGnssRil follow.
    ::android::hardware::Return<void> setCallback(const ::android::sp<::android::hardware::gnss::V1_0::IAGnssRilCallback>& callback) override;
    ::android::hardware::Return<void> setRefLocation(const ::android::hardware::gnss::V1_0::IAGnssRil::AGnssRefLocation& agnssReflocation) override;
    ::android::hardware::Return<bool> setSetId(::android::hardware::gnss::V1_0::IAGnssRil::SetIDType type, const ::android::hardware::hidl_string& setid) override;
    ::android::hardware::Return<bool> updateNetworkState(bool connected, ::android::hardware::gnss::V1_0::IAGnssRil::NetworkType type, bool roaming) override;
    ::android::hardware::Return<bool> updateNetworkAvailability(bool available, const ::android::hardware::hidl_string& apn) override;

    // Methods from ::android::hardware::gnss::V2_0::IAGnssRil follow.
    ::android::hardware::Return<bool> updateNetworkState_2_0(const ::android::hardware::gnss::V2_0::IAGnssRil::NetworkAttributes& attributes) override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_0_BPHWAGNSSRIL_H
