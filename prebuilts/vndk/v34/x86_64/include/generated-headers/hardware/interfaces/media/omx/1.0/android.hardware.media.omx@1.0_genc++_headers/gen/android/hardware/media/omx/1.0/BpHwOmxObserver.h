#ifndef HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_OMX_V1_0_BPHWOMXOBSERVER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_OMX_V1_0_BPHWOMXOBSERVER_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/media/omx/1.0/IHwOmxObserver.h>

namespace android {
namespace hardware {
namespace media {
namespace omx {
namespace V1_0 {

struct BpHwOmxObserver : public ::android::hardware::BpInterface<IOmxObserver>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwOmxObserver(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IOmxObserver Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::media::omx::V1_0::IOmxObserver follow.
    static ::android::hardware::Return<void>  _hidl_onMessages(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::media::omx::V1_0::Message>& messages);

    // Methods from ::android::hardware::media::omx::V1_0::IOmxObserver follow.
    ::android::hardware::Return<void> onMessages(const ::android::hardware::hidl_vec<::android::hardware::media::omx::V1_0::Message>& messages) override;

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
}  // namespace omx
}  // namespace media
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_OMX_V1_0_BPHWOMXOBSERVER_H
