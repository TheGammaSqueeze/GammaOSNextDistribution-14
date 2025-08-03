#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_BPHWWIFICHIP_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_BPHWWIFICHIP_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/wifi/1.0/IHwWifiChip.h>

namespace android {
namespace hardware {
namespace wifi {
namespace V1_0 {

struct BpHwWifiChip : public ::android::hardware::BpInterface<IWifiChip>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwWifiChip(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IWifiChip Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::wifi::V1_0::IWifiChip follow.
    static ::android::hardware::Return<void>  _hidl_getId(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getId_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_registerEventCallback(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::wifi::V1_0::IWifiChipEventCallback>& callback, registerEventCallback_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getCapabilities(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getCapabilities_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getAvailableModes(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getAvailableModes_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_configureChip(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t modeId, configureChip_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getMode(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getMode_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_requestChipDebugInfo(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, requestChipDebugInfo_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_requestDriverDebugDump(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, requestDriverDebugDump_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_requestFirmwareDebugDump(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, requestFirmwareDebugDump_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_createApIface(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, createApIface_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getApIfaceNames(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getApIfaceNames_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getApIface(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& ifname, getApIface_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_removeApIface(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& ifname, removeApIface_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_createNanIface(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, createNanIface_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getNanIfaceNames(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getNanIfaceNames_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getNanIface(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& ifname, getNanIface_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_removeNanIface(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& ifname, removeNanIface_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_createP2pIface(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, createP2pIface_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getP2pIfaceNames(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getP2pIfaceNames_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getP2pIface(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& ifname, getP2pIface_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_removeP2pIface(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& ifname, removeP2pIface_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_createStaIface(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, createStaIface_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getStaIfaceNames(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getStaIfaceNames_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getStaIface(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& ifname, getStaIface_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_removeStaIface(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& ifname, removeStaIface_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_createRttController(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::wifi::V1_0::IWifiIface>& boundIface, createRttController_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getDebugRingBuffersStatus(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getDebugRingBuffersStatus_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_startLoggingToDebugRingBuffer(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& ringName, ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel verboseLevel, uint32_t maxIntervalInSec, uint32_t minDataSizeInBytes, startLoggingToDebugRingBuffer_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_forceDumpToDebugRingBuffer(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& ringName, forceDumpToDebugRingBuffer_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_stopLoggingToDebugRingBuffer(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, stopLoggingToDebugRingBuffer_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getDebugHostWakeReasonStats(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getDebugHostWakeReasonStats_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_enableDebugErrorAlerts(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, bool enable, enableDebugErrorAlerts_cb _hidl_cb);

    // Methods from ::android::hardware::wifi::V1_0::IWifiChip follow.
    ::android::hardware::Return<void> getId(getId_cb _hidl_cb) override;
    ::android::hardware::Return<void> registerEventCallback(const ::android::sp<::android::hardware::wifi::V1_0::IWifiChipEventCallback>& callback, registerEventCallback_cb _hidl_cb) override;
    ::android::hardware::Return<void> getCapabilities(getCapabilities_cb _hidl_cb) override;
    ::android::hardware::Return<void> getAvailableModes(getAvailableModes_cb _hidl_cb) override;
    ::android::hardware::Return<void> configureChip(uint32_t modeId, configureChip_cb _hidl_cb) override;
    ::android::hardware::Return<void> getMode(getMode_cb _hidl_cb) override;
    ::android::hardware::Return<void> requestChipDebugInfo(requestChipDebugInfo_cb _hidl_cb) override;
    ::android::hardware::Return<void> requestDriverDebugDump(requestDriverDebugDump_cb _hidl_cb) override;
    ::android::hardware::Return<void> requestFirmwareDebugDump(requestFirmwareDebugDump_cb _hidl_cb) override;
    ::android::hardware::Return<void> createApIface(createApIface_cb _hidl_cb) override;
    ::android::hardware::Return<void> getApIfaceNames(getApIfaceNames_cb _hidl_cb) override;
    ::android::hardware::Return<void> getApIface(const ::android::hardware::hidl_string& ifname, getApIface_cb _hidl_cb) override;
    ::android::hardware::Return<void> removeApIface(const ::android::hardware::hidl_string& ifname, removeApIface_cb _hidl_cb) override;
    ::android::hardware::Return<void> createNanIface(createNanIface_cb _hidl_cb) override;
    ::android::hardware::Return<void> getNanIfaceNames(getNanIfaceNames_cb _hidl_cb) override;
    ::android::hardware::Return<void> getNanIface(const ::android::hardware::hidl_string& ifname, getNanIface_cb _hidl_cb) override;
    ::android::hardware::Return<void> removeNanIface(const ::android::hardware::hidl_string& ifname, removeNanIface_cb _hidl_cb) override;
    ::android::hardware::Return<void> createP2pIface(createP2pIface_cb _hidl_cb) override;
    ::android::hardware::Return<void> getP2pIfaceNames(getP2pIfaceNames_cb _hidl_cb) override;
    ::android::hardware::Return<void> getP2pIface(const ::android::hardware::hidl_string& ifname, getP2pIface_cb _hidl_cb) override;
    ::android::hardware::Return<void> removeP2pIface(const ::android::hardware::hidl_string& ifname, removeP2pIface_cb _hidl_cb) override;
    ::android::hardware::Return<void> createStaIface(createStaIface_cb _hidl_cb) override;
    ::android::hardware::Return<void> getStaIfaceNames(getStaIfaceNames_cb _hidl_cb) override;
    ::android::hardware::Return<void> getStaIface(const ::android::hardware::hidl_string& ifname, getStaIface_cb _hidl_cb) override;
    ::android::hardware::Return<void> removeStaIface(const ::android::hardware::hidl_string& ifname, removeStaIface_cb _hidl_cb) override;
    ::android::hardware::Return<void> createRttController(const ::android::sp<::android::hardware::wifi::V1_0::IWifiIface>& boundIface, createRttController_cb _hidl_cb) override;
    ::android::hardware::Return<void> getDebugRingBuffersStatus(getDebugRingBuffersStatus_cb _hidl_cb) override;
    ::android::hardware::Return<void> startLoggingToDebugRingBuffer(const ::android::hardware::hidl_string& ringName, ::android::hardware::wifi::V1_0::WifiDebugRingBufferVerboseLevel verboseLevel, uint32_t maxIntervalInSec, uint32_t minDataSizeInBytes, startLoggingToDebugRingBuffer_cb _hidl_cb) override;
    ::android::hardware::Return<void> forceDumpToDebugRingBuffer(const ::android::hardware::hidl_string& ringName, forceDumpToDebugRingBuffer_cb _hidl_cb) override;
    ::android::hardware::Return<void> stopLoggingToDebugRingBuffer(stopLoggingToDebugRingBuffer_cb _hidl_cb) override;
    ::android::hardware::Return<void> getDebugHostWakeReasonStats(getDebugHostWakeReasonStats_cb _hidl_cb) override;
    ::android::hardware::Return<void> enableDebugErrorAlerts(bool enable, enableDebugErrorAlerts_cb _hidl_cb) override;

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
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_0_BPHWWIFICHIP_H
