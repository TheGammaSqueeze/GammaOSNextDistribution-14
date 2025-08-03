#ifndef HIDL_GENERATED_ANDROID_SYSTEM_NET_NETD_V1_0_BPHWNETD_H
#define HIDL_GENERATED_ANDROID_SYSTEM_NET_NETD_V1_0_BPHWNETD_H

#include <hidl/HidlTransportSupport.h>

#include <android/system/net/netd/1.0/IHwNetd.h>

namespace android {
namespace system {
namespace net {
namespace netd {
namespace V1_0 {

struct BpHwNetd : public ::android::hardware::BpInterface<INetd>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwNetd(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef INetd Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::system::net::netd::V1_0::INetd follow.
    static ::android::hardware::Return<void>  _hidl_createOemNetwork(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, createOemNetwork_cb _hidl_cb);
    static ::android::hardware::Return<::android::system::net::netd::V1_0::INetd::StatusCode>  _hidl_destroyOemNetwork(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint64_t networkHandle);

    // Methods from ::android::system::net::netd::V1_0::INetd follow.
    ::android::hardware::Return<void> createOemNetwork(createOemNetwork_cb _hidl_cb) override;
    ::android::hardware::Return<::android::system::net::netd::V1_0::INetd::StatusCode> destroyOemNetwork(uint64_t networkHandle) override;

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
}  // namespace netd
}  // namespace net
}  // namespace system
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_SYSTEM_NET_NETD_V1_0_BPHWNETD_H
