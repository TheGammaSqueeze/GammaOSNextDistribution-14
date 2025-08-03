#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V2_0_BPHWTUNERCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V2_0_BPHWTUNERCALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/broadcastradio/2.0/IHwTunerCallback.h>

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V2_0 {

struct BpHwTunerCallback : public ::android::hardware::BpInterface<ITunerCallback>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwTunerCallback(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ITunerCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::broadcastradio::V2_0::ITunerCallback follow.
    static ::android::hardware::Return<void>  _hidl_onTuneFailed(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::broadcastradio::V2_0::Result result, const ::android::hardware::broadcastradio::V2_0::ProgramSelector& selector);
    static ::android::hardware::Return<void>  _hidl_onCurrentProgramInfoChanged(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::broadcastradio::V2_0::ProgramInfo& info);
    static ::android::hardware::Return<void>  _hidl_onProgramListUpdated(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::broadcastradio::V2_0::ProgramListChunk& chunk);
    static ::android::hardware::Return<void>  _hidl_onAntennaStateChange(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, bool connected);
    static ::android::hardware::Return<void>  _hidl_onParametersUpdated(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::broadcastradio::V2_0::VendorKeyValue>& parameters);

    // Methods from ::android::hardware::broadcastradio::V2_0::ITunerCallback follow.
    ::android::hardware::Return<void> onTuneFailed(::android::hardware::broadcastradio::V2_0::Result result, const ::android::hardware::broadcastradio::V2_0::ProgramSelector& selector) override;
    ::android::hardware::Return<void> onCurrentProgramInfoChanged(const ::android::hardware::broadcastradio::V2_0::ProgramInfo& info) override;
    ::android::hardware::Return<void> onProgramListUpdated(const ::android::hardware::broadcastradio::V2_0::ProgramListChunk& chunk) override;
    ::android::hardware::Return<void> onAntennaStateChange(bool connected) override;
    ::android::hardware::Return<void> onParametersUpdated(const ::android::hardware::hidl_vec<::android::hardware::broadcastradio::V2_0::VendorKeyValue>& parameters) override;

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
}  // namespace broadcastradio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V2_0_BPHWTUNERCALLBACK_H
