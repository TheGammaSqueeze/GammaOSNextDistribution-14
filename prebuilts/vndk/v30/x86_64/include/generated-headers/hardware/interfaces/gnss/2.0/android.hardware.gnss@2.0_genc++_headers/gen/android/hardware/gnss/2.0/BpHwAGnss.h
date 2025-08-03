#ifndef HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_0_BPHWAGNSS_H
#define HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_0_BPHWAGNSS_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/gnss/2.0/IHwAGnss.h>

namespace android {
namespace hardware {
namespace gnss {
namespace V2_0 {

struct BpHwAGnss : public ::android::hardware::BpInterface<IAGnss>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwAGnss(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IAGnss Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::gnss::V2_0::IAGnss follow.
    static ::android::hardware::Return<void>  _hidl_setCallback(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::gnss::V2_0::IAGnssCallback>& callback);
    static ::android::hardware::Return<bool>  _hidl_dataConnClosed(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<bool>  _hidl_dataConnFailed(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<bool>  _hidl_setServer(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::gnss::V2_0::IAGnssCallback::AGnssType type, const ::android::hardware::hidl_string& hostname, int32_t port);
    static ::android::hardware::Return<bool>  _hidl_dataConnOpen(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t networkHandle, const ::android::hardware::hidl_string& apn, ::android::hardware::gnss::V2_0::IAGnss::ApnIpType apnIpType);

    // Methods from ::android::hardware::gnss::V2_0::IAGnss follow.
    ::android::hardware::Return<void> setCallback(const ::android::sp<::android::hardware::gnss::V2_0::IAGnssCallback>& callback) override;
    ::android::hardware::Return<bool> dataConnClosed() override;
    ::android::hardware::Return<bool> dataConnFailed() override;
    ::android::hardware::Return<bool> setServer(::android::hardware::gnss::V2_0::IAGnssCallback::AGnssType type, const ::android::hardware::hidl_string& hostname, int32_t port) override;
    ::android::hardware::Return<bool> dataConnOpen(uint64_t networkHandle, const ::android::hardware::hidl_string& apn, ::android::hardware::gnss::V2_0::IAGnss::ApnIpType apnIpType) override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_GNSS_V2_0_BPHWAGNSS_H
