#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_2_BPHWSUPPLICANTP2PIFACE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_2_BPHWSUPPLICANTP2PIFACE_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/wifi/supplicant/1.2/IHwSupplicantP2pIface.h>

namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace V1_2 {

struct BpHwSupplicantP2pIface : public ::android::hardware::BpInterface<ISupplicantP2pIface>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwSupplicantP2pIface(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ISupplicantP2pIface Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::wifi::supplicant::V1_2::ISupplicantP2pIface follow.
    static ::android::hardware::Return<void>  _hidl_addGroup_1_2(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& ssid, const ::android::hardware::hidl_string& pskPassphrase, bool persistent, uint32_t freq, const ::android::hardware::hidl_array<uint8_t, 6>& peerAddress, bool joinExistingGroup, addGroup_1_2_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setMacRandomization(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, bool enable, setMacRandomization_cb _hidl_cb);

    // Methods from ::android::hardware::wifi::supplicant::V1_0::ISupplicantIface follow.
    ::android::hardware::Return<void> getName(getName_cb _hidl_cb) override;
    ::android::hardware::Return<void> getType(getType_cb _hidl_cb) override;
    ::android::hardware::Return<void> addNetwork(addNetwork_cb _hidl_cb) override;
    ::android::hardware::Return<void> removeNetwork(uint32_t id, removeNetwork_cb _hidl_cb) override;
    ::android::hardware::Return<void> getNetwork(uint32_t id, getNetwork_cb _hidl_cb) override;
    ::android::hardware::Return<void> listNetworks(listNetworks_cb _hidl_cb) override;
    ::android::hardware::Return<void> setWpsDeviceName(const ::android::hardware::hidl_string& name, setWpsDeviceName_cb _hidl_cb) override;
    ::android::hardware::Return<void> setWpsDeviceType(const ::android::hardware::hidl_array<uint8_t, 8>& type, setWpsDeviceType_cb _hidl_cb) override;
    ::android::hardware::Return<void> setWpsManufacturer(const ::android::hardware::hidl_string& manufacturer, setWpsManufacturer_cb _hidl_cb) override;
    ::android::hardware::Return<void> setWpsModelName(const ::android::hardware::hidl_string& modelName, setWpsModelName_cb _hidl_cb) override;
    ::android::hardware::Return<void> setWpsModelNumber(const ::android::hardware::hidl_string& modelNumber, setWpsModelNumber_cb _hidl_cb) override;
    ::android::hardware::Return<void> setWpsSerialNumber(const ::android::hardware::hidl_string& serialNumber, setWpsSerialNumber_cb _hidl_cb) override;
    ::android::hardware::Return<void> setWpsConfigMethods(::android::hardware::hidl_bitfield<::android::hardware::wifi::supplicant::V1_0::WpsConfigMethods> configMethods, setWpsConfigMethods_cb _hidl_cb) override;

    // Methods from ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface follow.
    ::android::hardware::Return<void> registerCallback(const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIfaceCallback>& callback, registerCallback_cb _hidl_cb) override;
    ::android::hardware::Return<void> getDeviceAddress(getDeviceAddress_cb _hidl_cb) override;
    ::android::hardware::Return<void> setSsidPostfix(const ::android::hardware::hidl_vec<uint8_t>& postfix, setSsidPostfix_cb _hidl_cb) override;
    ::android::hardware::Return<void> setGroupIdle(const ::android::hardware::hidl_string& groupIfName, uint32_t timeoutInSec, setGroupIdle_cb _hidl_cb) override;
    ::android::hardware::Return<void> setPowerSave(const ::android::hardware::hidl_string& groupIfName, bool enable, setPowerSave_cb _hidl_cb) override;
    ::android::hardware::Return<void> find(uint32_t timeoutInSec, find_cb _hidl_cb) override;
    ::android::hardware::Return<void> stopFind(stopFind_cb _hidl_cb) override;
    ::android::hardware::Return<void> flush(flush_cb _hidl_cb) override;
    ::android::hardware::Return<void> connect(const ::android::hardware::hidl_array<uint8_t, 6>& peerAddress, ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod provisionMethod, const ::android::hardware::hidl_string& preSelectedPin, bool joinExistingGroup, bool persistent, uint32_t goIntent, connect_cb _hidl_cb) override;
    ::android::hardware::Return<void> cancelConnect(cancelConnect_cb _hidl_cb) override;
    ::android::hardware::Return<void> provisionDiscovery(const ::android::hardware::hidl_array<uint8_t, 6>& peerAddress, ::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::WpsProvisionMethod provisionMethod, provisionDiscovery_cb _hidl_cb) override;
    ::android::hardware::Return<void> addGroup(bool persistent, uint32_t persistentNetworkId, addGroup_cb _hidl_cb) override;
    ::android::hardware::Return<void> removeGroup(const ::android::hardware::hidl_string& groupIfName, removeGroup_cb _hidl_cb) override;
    ::android::hardware::Return<void> reject(const ::android::hardware::hidl_array<uint8_t, 6>& peerAddress, reject_cb _hidl_cb) override;
    ::android::hardware::Return<void> invite(const ::android::hardware::hidl_string& groupIfName, const ::android::hardware::hidl_array<uint8_t, 6>& goDeviceAddress, const ::android::hardware::hidl_array<uint8_t, 6>& peerAddress, invite_cb _hidl_cb) override;
    ::android::hardware::Return<void> reinvoke(uint32_t persistentNetworkId, const ::android::hardware::hidl_array<uint8_t, 6>& peerAddress, reinvoke_cb _hidl_cb) override;
    ::android::hardware::Return<void> configureExtListen(uint32_t periodInMillis, uint32_t intervalInMillis, configureExtListen_cb _hidl_cb) override;
    ::android::hardware::Return<void> setListenChannel(uint32_t channel, uint32_t operatingClass, setListenChannel_cb _hidl_cb) override;
    ::android::hardware::Return<void> setDisallowedFrequencies(const ::android::hardware::hidl_vec<::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::FreqRange>& ranges, setDisallowedFrequencies_cb _hidl_cb) override;
    ::android::hardware::Return<void> getSsid(const ::android::hardware::hidl_array<uint8_t, 6>& peerAddress, getSsid_cb _hidl_cb) override;
    ::android::hardware::Return<void> getGroupCapability(const ::android::hardware::hidl_array<uint8_t, 6>& peerAddress, getGroupCapability_cb _hidl_cb) override;
    ::android::hardware::Return<void> addBonjourService(const ::android::hardware::hidl_vec<uint8_t>& query, const ::android::hardware::hidl_vec<uint8_t>& response, addBonjourService_cb _hidl_cb) override;
    ::android::hardware::Return<void> removeBonjourService(const ::android::hardware::hidl_vec<uint8_t>& query, removeBonjourService_cb _hidl_cb) override;
    ::android::hardware::Return<void> addUpnpService(uint32_t version, const ::android::hardware::hidl_string& serviceName, addUpnpService_cb _hidl_cb) override;
    ::android::hardware::Return<void> removeUpnpService(uint32_t version, const ::android::hardware::hidl_string& serviceName, removeUpnpService_cb _hidl_cb) override;
    ::android::hardware::Return<void> flushServices(flushServices_cb _hidl_cb) override;
    ::android::hardware::Return<void> requestServiceDiscovery(const ::android::hardware::hidl_array<uint8_t, 6>& peerAddress, const ::android::hardware::hidl_vec<uint8_t>& query, requestServiceDiscovery_cb _hidl_cb) override;
    ::android::hardware::Return<void> cancelServiceDiscovery(uint64_t identifier, cancelServiceDiscovery_cb _hidl_cb) override;
    ::android::hardware::Return<void> setMiracastMode(::android::hardware::wifi::supplicant::V1_0::ISupplicantP2pIface::MiracastMode mode, setMiracastMode_cb _hidl_cb) override;
    ::android::hardware::Return<void> startWpsPbc(const ::android::hardware::hidl_string& groupIfName, const ::android::hardware::hidl_array<uint8_t, 6>& bssid, startWpsPbc_cb _hidl_cb) override;
    ::android::hardware::Return<void> startWpsPinKeypad(const ::android::hardware::hidl_string& groupIfName, const ::android::hardware::hidl_string& pin, startWpsPinKeypad_cb _hidl_cb) override;
    ::android::hardware::Return<void> startWpsPinDisplay(const ::android::hardware::hidl_string& groupIfName, const ::android::hardware::hidl_array<uint8_t, 6>& bssid, startWpsPinDisplay_cb _hidl_cb) override;
    ::android::hardware::Return<void> cancelWps(const ::android::hardware::hidl_string& groupIfName, cancelWps_cb _hidl_cb) override;
    ::android::hardware::Return<void> enableWfd(bool enable, enableWfd_cb _hidl_cb) override;
    ::android::hardware::Return<void> setWfdDeviceInfo(const ::android::hardware::hidl_array<uint8_t, 6>& info, setWfdDeviceInfo_cb _hidl_cb) override;
    ::android::hardware::Return<void> createNfcHandoverRequestMessage(createNfcHandoverRequestMessage_cb _hidl_cb) override;
    ::android::hardware::Return<void> createNfcHandoverSelectMessage(createNfcHandoverSelectMessage_cb _hidl_cb) override;
    ::android::hardware::Return<void> reportNfcHandoverResponse(const ::android::hardware::hidl_vec<uint8_t>& request, reportNfcHandoverResponse_cb _hidl_cb) override;
    ::android::hardware::Return<void> reportNfcHandoverInitiation(const ::android::hardware::hidl_vec<uint8_t>& select, reportNfcHandoverInitiation_cb _hidl_cb) override;
    ::android::hardware::Return<void> saveConfig(saveConfig_cb _hidl_cb) override;

    // Methods from ::android::hardware::wifi::supplicant::V1_2::ISupplicantP2pIface follow.
    ::android::hardware::Return<void> addGroup_1_2(const ::android::hardware::hidl_vec<uint8_t>& ssid, const ::android::hardware::hidl_string& pskPassphrase, bool persistent, uint32_t freq, const ::android::hardware::hidl_array<uint8_t, 6>& peerAddress, bool joinExistingGroup, addGroup_1_2_cb _hidl_cb) override;
    ::android::hardware::Return<void> setMacRandomization(bool enable, setMacRandomization_cb _hidl_cb) override;

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

}  // namespace V1_2
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_2_BPHWSUPPLICANTP2PIFACE_H
