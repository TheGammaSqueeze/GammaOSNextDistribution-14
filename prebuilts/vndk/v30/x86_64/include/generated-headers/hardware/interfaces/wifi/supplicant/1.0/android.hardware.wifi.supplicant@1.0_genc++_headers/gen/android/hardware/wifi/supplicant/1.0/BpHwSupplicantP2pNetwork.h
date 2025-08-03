#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_BPHWSUPPLICANTP2PNETWORK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_BPHWSUPPLICANTP2PNETWORK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/wifi/supplicant/1.0/IHwSupplicantP2pNetwork.h>

namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace V1_0 {

struct BpHwSupplicantP2pNetwork : public ::android::hardware::BpInterface<ISupplicantP2pNetwork>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwSupplicantP2pNetwork(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ISupplicantP2pNetwork Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pNetwork follow.
    static ::android::hardware::Return<void>  _hidl_registerCallback(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pNetworkCallback>& callback, registerCallback_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getSsid(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getSsid_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getBssid(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getBssid_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_isCurrent(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, isCurrent_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_isPersistent(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, isPersistent_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_isGo(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, isGo_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setClientList(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::hidl_array<uint8_t, 6>>& clients, setClientList_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getClientList(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getClientList_cb _hidl_cb);

    // Methods from ::android::hardware::wifi::supplicant::V1_0::ISupplicantNetwork follow.
    ::android::hardware::Return<void> getId(getId_cb _hidl_cb) override;
    ::android::hardware::Return<void> getInterfaceName(getInterfaceName_cb _hidl_cb) override;
    ::android::hardware::Return<void> getType(getType_cb _hidl_cb) override;

    // Methods from ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pNetwork follow.
    ::android::hardware::Return<void> registerCallback(const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pNetworkCallback>& callback, registerCallback_cb _hidl_cb) override;
    ::android::hardware::Return<void> getSsid(getSsid_cb _hidl_cb) override;
    ::android::hardware::Return<void> getBssid(getBssid_cb _hidl_cb) override;
    ::android::hardware::Return<void> isCurrent(isCurrent_cb _hidl_cb) override;
    ::android::hardware::Return<void> isPersistent(isPersistent_cb _hidl_cb) override;
    ::android::hardware::Return<void> isGo(isGo_cb _hidl_cb) override;
    ::android::hardware::Return<void> setClientList(const ::android::hardware::hidl_vec<::android::hardware::hidl_array<uint8_t, 6>>& clients, setClientList_cb _hidl_cb) override;
    ::android::hardware::Return<void> getClientList(getClientList_cb _hidl_cb) override;

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
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_BPHWSUPPLICANTP2PNETWORK_H
