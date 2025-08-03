#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V1_1_BPHWBROADCASTRADIO_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V1_1_BPHWBROADCASTRADIO_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/broadcastradio/1.1/IHwBroadcastRadio.h>

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V1_1 {

struct BpHwBroadcastRadio : public ::android::hardware::BpInterface<IBroadcastRadio>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwBroadcastRadio(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IBroadcastRadio Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::broadcastradio::V1_1::IBroadcastRadio follow.
    static ::android::hardware::Return<void>  _hidl_getProperties_1_1(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getProperties_1_1_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getImage(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, int32_t id, getImage_cb _hidl_cb);

    // Methods from ::android::hardware::broadcastradio::V1_0::IBroadcastRadio follow.
    ::android::hardware::Return<void> getProperties(getProperties_cb _hidl_cb) override;
    ::android::hardware::Return<void> openTuner(const ::android::hardware::broadcastradio::V1_0::BandConfig& config, bool audio, const ::android::sp<::android::hardware::broadcastradio::V1_0::ITunerCallback>& callback, openTuner_cb _hidl_cb) override;

    // Methods from ::android::hardware::broadcastradio::V1_1::IBroadcastRadio follow.
    ::android::hardware::Return<void> getProperties_1_1(getProperties_1_1_cb _hidl_cb) override;
    ::android::hardware::Return<void> getImage(int32_t id, getImage_cb _hidl_cb) override;

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

}  // namespace V1_1
}  // namespace broadcastradio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V1_1_BPHWBROADCASTRADIO_H
