#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V1_0_BPHWTUNER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V1_0_BPHWTUNER_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/broadcastradio/1.0/IHwTuner.h>

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V1_0 {

struct BpHwTuner : public ::android::hardware::BpInterface<ITuner>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwTuner(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ITuner Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::broadcastradio::V1_0::ITuner follow.
    static ::android::hardware::Return<::android::hardware::broadcastradio::V1_0::Result>  _hidl_setConfiguration(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::broadcastradio::V1_0::BandConfig& config);
    static ::android::hardware::Return<void>  _hidl_getConfiguration(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getConfiguration_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::broadcastradio::V1_0::Result>  _hidl_scan(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::broadcastradio::V1_0::Direction direction, bool skipSubChannel);
    static ::android::hardware::Return<::android::hardware::broadcastradio::V1_0::Result>  _hidl_step(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::broadcastradio::V1_0::Direction direction, bool skipSubChannel);
    static ::android::hardware::Return<::android::hardware::broadcastradio::V1_0::Result>  _hidl_tune(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, uint32_t channel, uint32_t subChannel);
    static ::android::hardware::Return<::android::hardware::broadcastradio::V1_0::Result>  _hidl_cancel(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_getProgramInformation(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getProgramInformation_cb _hidl_cb);

    // Methods from ::android::hardware::broadcastradio::V1_0::ITuner follow.
    ::android::hardware::Return<::android::hardware::broadcastradio::V1_0::Result> setConfiguration(const ::android::hardware::broadcastradio::V1_0::BandConfig& config) override;
    ::android::hardware::Return<void> getConfiguration(getConfiguration_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::broadcastradio::V1_0::Result> scan(::android::hardware::broadcastradio::V1_0::Direction direction, bool skipSubChannel) override;
    ::android::hardware::Return<::android::hardware::broadcastradio::V1_0::Result> step(::android::hardware::broadcastradio::V1_0::Direction direction, bool skipSubChannel) override;
    ::android::hardware::Return<::android::hardware::broadcastradio::V1_0::Result> tune(uint32_t channel, uint32_t subChannel) override;
    ::android::hardware::Return<::android::hardware::broadcastradio::V1_0::Result> cancel() override;
    ::android::hardware::Return<void> getProgramInformation(getProgramInformation_cb _hidl_cb) override;

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
}  // namespace broadcastradio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V1_0_BPHWTUNER_H
