#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CONFIGSTORE_V1_0_BPHWSURFACEFLINGERCONFIGS_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CONFIGSTORE_V1_0_BPHWSURFACEFLINGERCONFIGS_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/configstore/1.0/IHwSurfaceFlingerConfigs.h>

namespace android {
namespace hardware {
namespace configstore {
namespace V1_0 {

struct BpHwSurfaceFlingerConfigs : public ::android::hardware::BpInterface<ISurfaceFlingerConfigs>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwSurfaceFlingerConfigs(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ISurfaceFlingerConfigs Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::configstore::V1_0::ISurfaceFlingerConfigs follow.
    static ::android::hardware::Return<void>  _hidl_vsyncEventPhaseOffsetNs(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, vsyncEventPhaseOffsetNs_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_vsyncSfEventPhaseOffsetNs(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, vsyncSfEventPhaseOffsetNs_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_useContextPriority(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, useContextPriority_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_hasWideColorDisplay(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, hasWideColorDisplay_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_hasHDRDisplay(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, hasHDRDisplay_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_presentTimeOffsetFromVSyncNs(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, presentTimeOffsetFromVSyncNs_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_useHwcForRGBtoYUV(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, useHwcForRGBtoYUV_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_maxVirtualDisplaySize(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, maxVirtualDisplaySize_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_hasSyncFramework(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, hasSyncFramework_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_useVrFlinger(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, useVrFlinger_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_maxFrameBufferAcquiredBuffers(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, maxFrameBufferAcquiredBuffers_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_startGraphicsAllocatorService(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, startGraphicsAllocatorService_cb _hidl_cb);

    // Methods from ::android::hardware::configstore::V1_0::ISurfaceFlingerConfigs follow.
    ::android::hardware::Return<void> vsyncEventPhaseOffsetNs(vsyncEventPhaseOffsetNs_cb _hidl_cb) override;
    ::android::hardware::Return<void> vsyncSfEventPhaseOffsetNs(vsyncSfEventPhaseOffsetNs_cb _hidl_cb) override;
    ::android::hardware::Return<void> useContextPriority(useContextPriority_cb _hidl_cb) override;
    ::android::hardware::Return<void> hasWideColorDisplay(hasWideColorDisplay_cb _hidl_cb) override;
    ::android::hardware::Return<void> hasHDRDisplay(hasHDRDisplay_cb _hidl_cb) override;
    ::android::hardware::Return<void> presentTimeOffsetFromVSyncNs(presentTimeOffsetFromVSyncNs_cb _hidl_cb) override;
    ::android::hardware::Return<void> useHwcForRGBtoYUV(useHwcForRGBtoYUV_cb _hidl_cb) override;
    ::android::hardware::Return<void> maxVirtualDisplaySize(maxVirtualDisplaySize_cb _hidl_cb) override;
    ::android::hardware::Return<void> hasSyncFramework(hasSyncFramework_cb _hidl_cb) override;
    ::android::hardware::Return<void> useVrFlinger(useVrFlinger_cb _hidl_cb) override;
    ::android::hardware::Return<void> maxFrameBufferAcquiredBuffers(maxFrameBufferAcquiredBuffers_cb _hidl_cb) override;
    ::android::hardware::Return<void> startGraphicsAllocatorService(startGraphicsAllocatorService_cb _hidl_cb) override;

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
}  // namespace configstore
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CONFIGSTORE_V1_0_BPHWSURFACEFLINGERCONFIGS_H
