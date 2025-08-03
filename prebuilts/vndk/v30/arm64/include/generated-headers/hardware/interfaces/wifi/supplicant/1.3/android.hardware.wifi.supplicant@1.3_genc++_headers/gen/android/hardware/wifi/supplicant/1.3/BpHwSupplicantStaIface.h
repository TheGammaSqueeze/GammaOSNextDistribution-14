#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_3_BPHWSUPPLICANTSTAIFACE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_3_BPHWSUPPLICANTSTAIFACE_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/wifi/supplicant/1.3/IHwSupplicantStaIface.h>

namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
namespace V1_3 {

struct BpHwSupplicantStaIface : public ::android::hardware::BpInterface<ISupplicantStaIface>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwSupplicantStaIface(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ISupplicantStaIface Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIface follow.
    static ::android::hardware::Return<void>  _hidl_registerCallback_1_3(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback>& callback, registerCallback_1_3_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getConnectionCapabilities(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getConnectionCapabilities_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getWpaDriverCapabilities(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getWpaDriverCapabilities_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setMboCellularDataStatus(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, bool available, setMboCellularDataStatus_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getKeyMgmtCapabilities_1_3(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getKeyMgmtCapabilities_1_3_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_filsHlpFlushRequest(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, filsHlpFlushRequest_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_filsHlpAddRequest(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_array<uint8_t, 6>& dst_mac, const ::android::hardware::hidl_vec<uint8_t>& pkt, filsHlpAddRequest_cb _hidl_cb);

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

    // Methods from ::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIface follow.
    ::android::hardware::Return<void> registerCallback(const ::android::sp<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIfaceCallback>& callback, registerCallback_cb _hidl_cb) override;
    ::android::hardware::Return<void> reassociate(reassociate_cb _hidl_cb) override;
    ::android::hardware::Return<void> reconnect(reconnect_cb _hidl_cb) override;
    ::android::hardware::Return<void> disconnect(disconnect_cb _hidl_cb) override;
    ::android::hardware::Return<void> setPowerSave(bool enable, setPowerSave_cb _hidl_cb) override;
    ::android::hardware::Return<void> initiateTdlsDiscover(const ::android::hardware::hidl_array<uint8_t, 6>& macAddress, initiateTdlsDiscover_cb _hidl_cb) override;
    ::android::hardware::Return<void> initiateTdlsSetup(const ::android::hardware::hidl_array<uint8_t, 6>& macAddress, initiateTdlsSetup_cb _hidl_cb) override;
    ::android::hardware::Return<void> initiateTdlsTeardown(const ::android::hardware::hidl_array<uint8_t, 6>& macAddress, initiateTdlsTeardown_cb _hidl_cb) override;
    ::android::hardware::Return<void> initiateAnqpQuery(const ::android::hardware::hidl_array<uint8_t, 6>& macAddress, const ::android::hardware::hidl_vec<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIface::AnqpInfoId>& infoElements, const ::android::hardware::hidl_vec<::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIface::Hs20AnqpSubtypes>& subTypes, initiateAnqpQuery_cb _hidl_cb) override;
    ::android::hardware::Return<void> initiateHs20IconQuery(const ::android::hardware::hidl_array<uint8_t, 6>& macAddress, const ::android::hardware::hidl_string& fileName, initiateHs20IconQuery_cb _hidl_cb) override;
    ::android::hardware::Return<void> getMacAddress(getMacAddress_cb _hidl_cb) override;
    ::android::hardware::Return<void> startRxFilter(startRxFilter_cb _hidl_cb) override;
    ::android::hardware::Return<void> stopRxFilter(stopRxFilter_cb _hidl_cb) override;
    ::android::hardware::Return<void> addRxFilter(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIface::RxFilterType type, addRxFilter_cb _hidl_cb) override;
    ::android::hardware::Return<void> removeRxFilter(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIface::RxFilterType type, removeRxFilter_cb _hidl_cb) override;
    ::android::hardware::Return<void> setBtCoexistenceMode(::android::hardware::wifi::supplicant::V1_0::ISupplicantStaIface::BtCoexistenceMode mode, setBtCoexistenceMode_cb _hidl_cb) override;
    ::android::hardware::Return<void> setBtCoexistenceScanModeEnabled(bool enable, setBtCoexistenceScanModeEnabled_cb _hidl_cb) override;
    ::android::hardware::Return<void> setSuspendModeEnabled(bool enable, setSuspendModeEnabled_cb _hidl_cb) override;
    ::android::hardware::Return<void> setCountryCode(const ::android::hardware::hidl_array<int8_t, 2>& code, setCountryCode_cb _hidl_cb) override;
    ::android::hardware::Return<void> startWpsRegistrar(const ::android::hardware::hidl_array<uint8_t, 6>& bssid, const ::android::hardware::hidl_string& pin, startWpsRegistrar_cb _hidl_cb) override;
    ::android::hardware::Return<void> startWpsPbc(const ::android::hardware::hidl_array<uint8_t, 6>& bssid, startWpsPbc_cb _hidl_cb) override;
    ::android::hardware::Return<void> startWpsPinKeypad(const ::android::hardware::hidl_string& pin, startWpsPinKeypad_cb _hidl_cb) override;
    ::android::hardware::Return<void> startWpsPinDisplay(const ::android::hardware::hidl_array<uint8_t, 6>& bssid, startWpsPinDisplay_cb _hidl_cb) override;
    ::android::hardware::Return<void> cancelWps(cancelWps_cb _hidl_cb) override;
    ::android::hardware::Return<void> setExternalSim(bool useExternalSim, setExternalSim_cb _hidl_cb) override;
    ::android::hardware::Return<void> addExtRadioWork(const ::android::hardware::hidl_string& name, uint32_t freqInMhz, uint32_t timeoutInSec, addExtRadioWork_cb _hidl_cb) override;
    ::android::hardware::Return<void> removeExtRadioWork(uint32_t id, removeExtRadioWork_cb _hidl_cb) override;
    ::android::hardware::Return<void> enableAutoReconnect(bool enable, enableAutoReconnect_cb _hidl_cb) override;

    // Methods from ::android::hardware::wifi::supplicant::V1_1::ISupplicantStaIface follow.
    ::android::hardware::Return<void> registerCallback_1_1(const ::android::sp<::android::hardware::wifi::supplicant::V1_1::ISupplicantStaIfaceCallback>& callback, registerCallback_1_1_cb _hidl_cb) override;

    // Methods from ::android::hardware::wifi::supplicant::V1_2::ISupplicantStaIface follow.
    ::android::hardware::Return<void> registerCallback_1_2(const ::android::sp<::android::hardware::wifi::supplicant::V1_2::ISupplicantStaIfaceCallback>& callback, registerCallback_1_2_cb _hidl_cb) override;
    ::android::hardware::Return<void> getKeyMgmtCapabilities(getKeyMgmtCapabilities_cb _hidl_cb) override;
    ::android::hardware::Return<void> addDppPeerUri(const ::android::hardware::hidl_string& uri, addDppPeerUri_cb _hidl_cb) override;
    ::android::hardware::Return<void> removeDppUri(uint32_t id, removeDppUri_cb _hidl_cb) override;
    ::android::hardware::Return<void> startDppConfiguratorInitiator(uint32_t peerBootstrapId, uint32_t ownBootstrapId, const ::android::hardware::hidl_string& ssid, const ::android::hardware::hidl_string& password, const ::android::hardware::hidl_string& psk, ::android::hardware::wifi::supplicant::V1_2::DppNetRole netRole, ::android::hardware::wifi::supplicant::V1_2::DppAkm securityAkm, startDppConfiguratorInitiator_cb _hidl_cb) override;
    ::android::hardware::Return<void> startDppEnrolleeInitiator(uint32_t peerBootstrapId, uint32_t ownBootstrapId, startDppEnrolleeInitiator_cb _hidl_cb) override;
    ::android::hardware::Return<void> stopDppInitiator(stopDppInitiator_cb _hidl_cb) override;

    // Methods from ::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIface follow.
    ::android::hardware::Return<void> registerCallback_1_3(const ::android::sp<::android::hardware::wifi::supplicant::V1_3::ISupplicantStaIfaceCallback>& callback, registerCallback_1_3_cb _hidl_cb) override;
    ::android::hardware::Return<void> getConnectionCapabilities(getConnectionCapabilities_cb _hidl_cb) override;
    ::android::hardware::Return<void> getWpaDriverCapabilities(getWpaDriverCapabilities_cb _hidl_cb) override;
    ::android::hardware::Return<void> setMboCellularDataStatus(bool available, setMboCellularDataStatus_cb _hidl_cb) override;
    ::android::hardware::Return<void> getKeyMgmtCapabilities_1_3(getKeyMgmtCapabilities_1_3_cb _hidl_cb) override;
    ::android::hardware::Return<void> filsHlpFlushRequest(filsHlpFlushRequest_cb _hidl_cb) override;
    ::android::hardware::Return<void> filsHlpAddRequest(const ::android::hardware::hidl_array<uint8_t, 6>& dst_mac, const ::android::hardware::hidl_vec<uint8_t>& pkt, filsHlpAddRequest_cb _hidl_cb) override;

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

}  // namespace V1_3
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_SUPPLICANT_V1_3_BPHWSUPPLICANTSTAIFACE_H
