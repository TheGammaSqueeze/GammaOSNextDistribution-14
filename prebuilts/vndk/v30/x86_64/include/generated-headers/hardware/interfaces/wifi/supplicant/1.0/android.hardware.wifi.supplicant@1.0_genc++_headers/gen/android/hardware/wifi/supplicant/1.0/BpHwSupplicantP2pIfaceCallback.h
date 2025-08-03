#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_BPHWSUPPLICANTP2PIFACECALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_BPHWSUPPLICANTP2PIFACECALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/wifi/supplicant/1.0/IHwSupplicantP2pIfaceCallback.h>

namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace V1_0 {

struct BpHwSupplicantP2pIfaceCallback : public ::android::hardware::BpInterface<ISupplicantP2pIfaceCallback>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwSupplicantP2pIfaceCallback(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ISupplicantP2pIfaceCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback follow.
    static ::android::hardware::Return<void>  _hidl_onNetworkAdded(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t id);
    static ::android::hardware::Return<void>  _hidl_onNetworkRemoved(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t id);
    static ::android::hardware::Return<void>  _hidl_onDeviceFound(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_array<uint8_t, 6>& srcAddress, const ::android::hardware::hidl_array<uint8_t, 6>& p2pDeviceAddress, const ::android::hardware::hidl_array<uint8_t, 8>& primaryDeviceType, const ::android::hardware::hidl_string& deviceName, ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods> configMethods, uint8_t deviceCapabilities, ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask> groupCapabilities, const ::android::hardware::hidl_array<uint8_t, 6>& wfdDeviceInfo);
    static ::android::hardware::Return<void>  _hidl_onDeviceLost(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_array<uint8_t, 6>& p2pDeviceAddress);
    static ::android::hardware::Return<void>  _hidl_onFindStopped(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_onGoNegotiationRequest(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_array<uint8_t, 6>& srcAddress, ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId passwordId);
    static ::android::hardware::Return<void>  _hidl_onGoNegotiationCompleted(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode status);
    static ::android::hardware::Return<void>  _hidl_onGroupFormationSuccess(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_onGroupFormationFailure(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& failureReason);
    static ::android::hardware::Return<void>  _hidl_onGroupStarted(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& groupIfname, bool isGo, const ::android::hardware::hidl_vec<uint8_t>& ssid, uint32_t frequency, const ::android::hardware::hidl_array<uint8_t, 32>& psk, const ::android::hardware::hidl_string& passphrase, const ::android::hardware::hidl_array<uint8_t, 6>& goDeviceAddress, bool isPersistent);
    static ::android::hardware::Return<void>  _hidl_onGroupRemoved(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& groupIfname, bool isGo);
    static ::android::hardware::Return<void>  _hidl_onInvitationReceived(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_array<uint8_t, 6>& srcAddress, const ::android::hardware::hidl_array<uint8_t, 6>& goDeviceAddress, const ::android::hardware::hidl_array<uint8_t, 6>& bssid, uint32_t persistentNetworkId, uint32_t operatingFrequency);
    static ::android::hardware::Return<void>  _hidl_onInvitationResult(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_array<uint8_t, 6>& bssid, ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode status);
    static ::android::hardware::Return<void>  _hidl_onProvisionDiscoveryCompleted(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_array<uint8_t, 6>& p2pDeviceAddress, bool isRequest, ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode status, ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods> configMethods, const ::android::hardware::hidl_string& generatedPin);
    static ::android::hardware::Return<void>  _hidl_onServiceDiscoveryResponse(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_array<uint8_t, 6>& srcAddress, uint16_t updateIndicator, const ::android::hardware::hidl_vec<uint8_t>& tlvs);
    static ::android::hardware::Return<void>  _hidl_onStaAuthorized(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_array<uint8_t, 6>& srcAddress, const ::android::hardware::hidl_array<uint8_t, 6>& p2pDeviceAddress);
    static ::android::hardware::Return<void>  _hidl_onStaDeauthorized(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_array<uint8_t, 6>& srcAddress, const ::android::hardware::hidl_array<uint8_t, 6>& p2pDeviceAddress);

    // Methods from ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback follow.
    ::android::hardware::Return<void> onNetworkAdded(uint32_t id) override;
    ::android::hardware::Return<void> onNetworkRemoved(uint32_t id) override;
    ::android::hardware::Return<void> onDeviceFound(const ::android::hardware::hidl_array<uint8_t, 6>& srcAddress, const ::android::hardware::hidl_array<uint8_t, 6>& p2pDeviceAddress, const ::android::hardware::hidl_array<uint8_t, 8>& primaryDeviceType, const ::android::hardware::hidl_string& deviceName, ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods> configMethods, uint8_t deviceCapabilities, ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::P2pGroupCapabilityMask> groupCapabilities, const ::android::hardware::hidl_array<uint8_t, 6>& wfdDeviceInfo) override;
    ::android::hardware::Return<void> onDeviceLost(const ::android::hardware::hidl_array<uint8_t, 6>& p2pDeviceAddress) override;
    ::android::hardware::Return<void> onFindStopped() override;
    ::android::hardware::Return<void> onGoNegotiationRequest(const ::android::hardware::hidl_array<uint8_t, 6>& srcAddress, ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::WpsDevPasswordId passwordId) override;
    ::android::hardware::Return<void> onGoNegotiationCompleted(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode status) override;
    ::android::hardware::Return<void> onGroupFormationSuccess() override;
    ::android::hardware::Return<void> onGroupFormationFailure(const ::android::hardware::hidl_string& failureReason) override;
    ::android::hardware::Return<void> onGroupStarted(const ::android::hardware::hidl_string& groupIfname, bool isGo, const ::android::hardware::hidl_vec<uint8_t>& ssid, uint32_t frequency, const ::android::hardware::hidl_array<uint8_t, 32>& psk, const ::android::hardware::hidl_string& passphrase, const ::android::hardware::hidl_array<uint8_t, 6>& goDeviceAddress, bool isPersistent) override;
    ::android::hardware::Return<void> onGroupRemoved(const ::android::hardware::hidl_string& groupIfname, bool isGo) override;
    ::android::hardware::Return<void> onInvitationReceived(const ::android::hardware::hidl_array<uint8_t, 6>& srcAddress, const ::android::hardware::hidl_array<uint8_t, 6>& goDeviceAddress, const ::android::hardware::hidl_array<uint8_t, 6>& bssid, uint32_t persistentNetworkId, uint32_t operatingFrequency) override;
    ::android::hardware::Return<void> onInvitationResult(const ::android::hardware::hidl_array<uint8_t, 6>& bssid, ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pStatusCode status) override;
    ::android::hardware::Return<void> onProvisionDiscoveryCompleted(const ::android::hardware::hidl_array<uint8_t, 6>& p2pDeviceAddress, bool isRequest, ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback::P2pProvDiscStatusCode status, ::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods> configMethods, const ::android::hardware::hidl_string& generatedPin) override;
    ::android::hardware::Return<void> onServiceDiscoveryResponse(const ::android::hardware::hidl_array<uint8_t, 6>& srcAddress, uint16_t updateIndicator, const ::android::hardware::hidl_vec<uint8_t>& tlvs) override;
    ::android::hardware::Return<void> onStaAuthorized(const ::android::hardware::hidl_array<uint8_t, 6>& srcAddress, const ::android::hardware::hidl_array<uint8_t, 6>& p2pDeviceAddress) override;
    ::android::hardware::Return<void> onStaDeauthorized(const ::android::hardware::hidl_array<uint8_t, 6>& srcAddress, const ::android::hardware::hidl_array<uint8_t, 6>& p2pDeviceAddress) override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_0_BPHWSUPPLICANTP2PIFACECALLBACK_H
