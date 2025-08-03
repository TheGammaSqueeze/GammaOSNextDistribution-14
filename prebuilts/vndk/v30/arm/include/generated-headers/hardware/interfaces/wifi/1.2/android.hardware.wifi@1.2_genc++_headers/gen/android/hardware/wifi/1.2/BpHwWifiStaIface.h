#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_2_BPHWWIFISTAIFACE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_2_BPHWWIFISTAIFACE_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/wifi/1.2/IHwWifiStaIface.h>

namespace android {
namespace hardware {
namespace wifi {
namespace V1_2 {

struct BpHwWifiStaIface : public ::android::hardware::BpInterface<IWifiStaIface>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwWifiStaIface(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IWifiStaIface Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::wifi::V1_2::IWifiStaIface follow.
    static ::android::hardware::Return<void>  _hidl_readApfPacketFilterData(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, readApfPacketFilterData_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_setMacAddress(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_array<uint8_t, 6>& mac, setMacAddress_cb _hidl_cb);

    // Methods from ::android::hardware::wifi::V1_0::IWifiIface follow.
    ::android::hardware::Return<void> getType(getType_cb _hidl_cb) override;
    ::android::hardware::Return<void> getName(getName_cb _hidl_cb) override;

    // Methods from ::android::hardware::wifi::V1_0::IWifiStaIface follow.
    ::android::hardware::Return<void> registerEventCallback(const ::android::sp<::android::hardware::wifi::V1_0::IWifiStaIfaceEventCallback>& callback, registerEventCallback_cb _hidl_cb) override;
    ::android::hardware::Return<void> getCapabilities(getCapabilities_cb _hidl_cb) override;
    ::android::hardware::Return<void> getApfPacketFilterCapabilities(getApfPacketFilterCapabilities_cb _hidl_cb) override;
    ::android::hardware::Return<void> installApfPacketFilter(uint32_t cmdId, const ::android::hardware::hidl_vec<uint8_t>& program, installApfPacketFilter_cb _hidl_cb) override;
    ::android::hardware::Return<void> getBackgroundScanCapabilities(getBackgroundScanCapabilities_cb _hidl_cb) override;
    ::android::hardware::Return<void> getValidFrequenciesForBand(::android::hardware::wifi::V1_0::WifiBand band, getValidFrequenciesForBand_cb _hidl_cb) override;
    ::android::hardware::Return<void> startBackgroundScan(uint32_t cmdId, const ::android::hardware::wifi::V1_0::StaBackgroundScanParameters& params, startBackgroundScan_cb _hidl_cb) override;
    ::android::hardware::Return<void> stopBackgroundScan(uint32_t cmdId, stopBackgroundScan_cb _hidl_cb) override;
    ::android::hardware::Return<void> enableLinkLayerStatsCollection(bool debug, enableLinkLayerStatsCollection_cb _hidl_cb) override;
    ::android::hardware::Return<void> disableLinkLayerStatsCollection(disableLinkLayerStatsCollection_cb _hidl_cb) override;
    ::android::hardware::Return<void> getLinkLayerStats(getLinkLayerStats_cb _hidl_cb) override;
    ::android::hardware::Return<void> startRssiMonitoring(uint32_t cmdId, int32_t maxRssi, int32_t minRssi, startRssiMonitoring_cb _hidl_cb) override;
    ::android::hardware::Return<void> stopRssiMonitoring(uint32_t cmdId, stopRssiMonitoring_cb _hidl_cb) override;
    ::android::hardware::Return<void> getRoamingCapabilities(getRoamingCapabilities_cb _hidl_cb) override;
    ::android::hardware::Return<void> configureRoaming(const ::android::hardware::wifi::V1_0::StaRoamingConfig& config, configureRoaming_cb _hidl_cb) override;
    ::android::hardware::Return<void> setRoamingState(::android::hardware::wifi::V1_0::StaRoamingState state, setRoamingState_cb _hidl_cb) override;
    ::android::hardware::Return<void> enableNdOffload(bool enable, enableNdOffload_cb _hidl_cb) override;
    ::android::hardware::Return<void> startSendingKeepAlivePackets(uint32_t cmdId, const ::android::hardware::hidl_vec<uint8_t>& ipPacketData, uint16_t etherType, const ::android::hardware::hidl_array<uint8_t, 6>& srcAddress, const ::android::hardware::hidl_array<uint8_t, 6>& dstAddress, uint32_t periodInMs, startSendingKeepAlivePackets_cb _hidl_cb) override;
    ::android::hardware::Return<void> stopSendingKeepAlivePackets(uint32_t cmdId, stopSendingKeepAlivePackets_cb _hidl_cb) override;
    ::android::hardware::Return<void> setScanningMacOui(const ::android::hardware::hidl_array<uint8_t, 3>& oui, setScanningMacOui_cb _hidl_cb) override;
    ::android::hardware::Return<void> startDebugPacketFateMonitoring(startDebugPacketFateMonitoring_cb _hidl_cb) override;
    ::android::hardware::Return<void> getDebugTxPacketFates(getDebugTxPacketFates_cb _hidl_cb) override;
    ::android::hardware::Return<void> getDebugRxPacketFates(getDebugRxPacketFates_cb _hidl_cb) override;

    // Methods from ::android::hardware::wifi::V1_2::IWifiStaIface follow.
    ::android::hardware::Return<void> readApfPacketFilterData(readApfPacketFilterData_cb _hidl_cb) override;
    ::android::hardware::Return<void> setMacAddress(const ::android::hardware::hidl_array<uint8_t, 6>& mac, setMacAddress_cb _hidl_cb) override;

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
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_2_BPHWWIFISTAIFACE_H
