#ifndef HIDL_GENERATED_ANDROID_FRAMEWORKS_STATS_V1_0_BPHWSTATS_H
#define HIDL_GENERATED_ANDROID_FRAMEWORKS_STATS_V1_0_BPHWSTATS_H

#include <hidl/HidlTransportSupport.h>

#include <android/frameworks/stats/1.0/IHwStats.h>

namespace android {
namespace frameworks {
namespace stats {
namespace V1_0 {

struct BpHwStats : public ::android::hardware::BpInterface<IStats>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwStats(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IStats Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::frameworks::stats::V1_0::IStats follow.
    static ::android::hardware::Return<void>  _hidl_reportSpeakerImpedance(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::frameworks::stats::V1_0::SpeakerImpedance& speakerImpedance);
    static ::android::hardware::Return<void>  _hidl_reportHardwareFailed(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::frameworks::stats::V1_0::HardwareFailed& hardwareFailed);
    static ::android::hardware::Return<void>  _hidl_reportPhysicalDropDetected(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::frameworks::stats::V1_0::PhysicalDropDetected& physicalDropDetected);
    static ::android::hardware::Return<void>  _hidl_reportChargeCycles(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::frameworks::stats::V1_0::ChargeCycles& chargeCycles);
    static ::android::hardware::Return<void>  _hidl_reportBatteryHealthSnapshot(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs& args);
    static ::android::hardware::Return<void>  _hidl_reportSlowIo(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::frameworks::stats::V1_0::SlowIo& slowIo);
    static ::android::hardware::Return<void>  _hidl_reportBatteryCausedShutdown(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::frameworks::stats::V1_0::BatteryCausedShutdown& batteryCausedShutdown);
    static ::android::hardware::Return<void>  _hidl_reportUsbPortOverheatEvent(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::frameworks::stats::V1_0::UsbPortOverheatEvent& usbPortOverheatEvent);
    static ::android::hardware::Return<void>  _hidl_reportSpeechDspStat(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::frameworks::stats::V1_0::SpeechDspStat& speechDspStat);
    static ::android::hardware::Return<void>  _hidl_reportVendorAtom(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::frameworks::stats::V1_0::VendorAtom& vendorAtom);

    // Methods from ::android::frameworks::stats::V1_0::IStats follow.
    ::android::hardware::Return<void> reportSpeakerImpedance(const ::android::frameworks::stats::V1_0::SpeakerImpedance& speakerImpedance) override;
    ::android::hardware::Return<void> reportHardwareFailed(const ::android::frameworks::stats::V1_0::HardwareFailed& hardwareFailed) override;
    ::android::hardware::Return<void> reportPhysicalDropDetected(const ::android::frameworks::stats::V1_0::PhysicalDropDetected& physicalDropDetected) override;
    ::android::hardware::Return<void> reportChargeCycles(const ::android::frameworks::stats::V1_0::ChargeCycles& chargeCycles) override;
    ::android::hardware::Return<void> reportBatteryHealthSnapshot(const ::android::frameworks::stats::V1_0::BatteryHealthSnapshotArgs& args) override;
    ::android::hardware::Return<void> reportSlowIo(const ::android::frameworks::stats::V1_0::SlowIo& slowIo) override;
    ::android::hardware::Return<void> reportBatteryCausedShutdown(const ::android::frameworks::stats::V1_0::BatteryCausedShutdown& batteryCausedShutdown) override;
    ::android::hardware::Return<void> reportUsbPortOverheatEvent(const ::android::frameworks::stats::V1_0::UsbPortOverheatEvent& usbPortOverheatEvent) override;
    ::android::hardware::Return<void> reportSpeechDspStat(const ::android::frameworks::stats::V1_0::SpeechDspStat& speechDspStat) override;
    ::android::hardware::Return<void> reportVendorAtom(const ::android::frameworks::stats::V1_0::VendorAtom& vendorAtom) override;

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
}  // namespace stats
}  // namespace frameworks
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_FRAMEWORKS_STATS_V1_0_BPHWSTATS_H
