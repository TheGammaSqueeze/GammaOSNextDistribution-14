#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_BPHWEVSDISPLAY_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_BPHWEVSDISPLAY_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/automotive/evs/1.1/IHwEvsDisplay.h>

namespace android {
namespace hardware {
namespace automotive {
namespace evs {
namespace V1_1 {

struct BpHwEvsDisplay : public ::android::hardware::BpInterface<IEvsDisplay>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwEvsDisplay(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IEvsDisplay Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::automotive::evs::V1_1::IEvsDisplay follow.
    static ::android::hardware::Return<void>  _hidl_getDisplayInfo_1_1(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getDisplayInfo_1_1_cb _hidl_cb);

    // Methods from ::android::hardware::automotive::evs::V1_0::IEvsDisplay follow.
    ::android::hardware::Return<void> getDisplayInfo(getDisplayInfo_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> setDisplayState(::android::hardware::automotive::evs::V1_0::DisplayState state) override;
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::DisplayState> getDisplayState() override;
    ::android::hardware::Return<void> getTargetBuffer(getTargetBuffer_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::EvsResult> returnTargetBufferForDisplay(const ::android::hardware::automotive::evs::V1_0::BufferDesc& buffer) override;

    // Methods from ::android::hardware::automotive::evs::V1_1::IEvsDisplay follow.
    ::android::hardware::Return<void> getDisplayInfo_1_1(getDisplayInfo_1_1_cb _hidl_cb) override;

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
}  // namespace evs
}  // namespace automotive
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_1_BPHWEVSDISPLAY_H
