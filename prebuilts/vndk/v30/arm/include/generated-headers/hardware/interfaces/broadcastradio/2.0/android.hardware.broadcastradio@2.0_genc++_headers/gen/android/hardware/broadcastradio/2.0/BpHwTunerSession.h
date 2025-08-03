#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V2_0_BPHWTUNERSESSION_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V2_0_BPHWTUNERSESSION_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/broadcastradio/2.0/IHwTunerSession.h>

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V2_0 {

struct BpHwTunerSession : public ::android::hardware::BpInterface<ITunerSession>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwTunerSession(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ITunerSession Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::broadcastradio::V2_0::ITunerSession follow.
    static ::android::hardware::Return<::android::hardware::broadcastradio::V2_0::Result>  _hidl_tune(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::broadcastradio::V2_0::ProgramSelector& program);
    static ::android::hardware::Return<::android::hardware::broadcastradio::V2_0::Result>  _hidl_scan(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, bool directionUp, bool skipSubChannel);
    static ::android::hardware::Return<::android::hardware::broadcastradio::V2_0::Result>  _hidl_step(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, bool directionUp);
    static ::android::hardware::Return<void>  _hidl_cancel(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<::android::hardware::broadcastradio::V2_0::Result>  _hidl_startProgramListUpdates(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::broadcastradio::V2_0::ProgramFilter& filter);
    static ::android::hardware::Return<void>  _hidl_stopProgramListUpdates(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_isConfigFlagSet(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::broadcastradio::V2_0::ConfigFlag flag, isConfigFlagSet_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::broadcastradio::V2_0::Result>  _hidl_setConfigFlag(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::broadcastradio::V2_0::ConfigFlag flag, bool value);
    static ::android::hardware::Return<void>  _hidl_setParameters(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::broadcastradio::V2_0::VendorKeyValue>& parameters, setParameters_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getParameters(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& keys, getParameters_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_close(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);

    // Methods from ::android::hardware::broadcastradio::V2_0::ITunerSession follow.
    ::android::hardware::Return<::android::hardware::broadcastradio::V2_0::Result> tune(const ::android::hardware::broadcastradio::V2_0::ProgramSelector& program) override;
    ::android::hardware::Return<::android::hardware::broadcastradio::V2_0::Result> scan(bool directionUp, bool skipSubChannel) override;
    ::android::hardware::Return<::android::hardware::broadcastradio::V2_0::Result> step(bool directionUp) override;
    ::android::hardware::Return<void> cancel() override;
    ::android::hardware::Return<::android::hardware::broadcastradio::V2_0::Result> startProgramListUpdates(const ::android::hardware::broadcastradio::V2_0::ProgramFilter& filter) override;
    ::android::hardware::Return<void> stopProgramListUpdates() override;
    ::android::hardware::Return<void> isConfigFlagSet(::android::hardware::broadcastradio::V2_0::ConfigFlag flag, isConfigFlagSet_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::broadcastradio::V2_0::Result> setConfigFlag(::android::hardware::broadcastradio::V2_0::ConfigFlag flag, bool value) override;
    ::android::hardware::Return<void> setParameters(const ::android::hardware::hidl_vec<::android::hardware::broadcastradio::V2_0::VendorKeyValue>& parameters, setParameters_cb _hidl_cb) override;
    ::android::hardware::Return<void> getParameters(const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& keys, getParameters_cb _hidl_cb) override;
    ::android::hardware::Return<void> close() override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V2_0_BPHWTUNERSESSION_H
