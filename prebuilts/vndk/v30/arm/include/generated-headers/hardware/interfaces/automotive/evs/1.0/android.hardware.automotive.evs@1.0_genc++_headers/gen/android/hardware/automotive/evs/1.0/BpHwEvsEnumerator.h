#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_0_BPHWEVSENUMERATOR_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_0_BPHWEVSENUMERATOR_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/automotive/evs/1.0/IHwEvsEnumerator.h>

namespace android {
namespace hardware {
namespace automotive {
namespace evs {
namespace V1_0 {

struct BpHwEvsEnumerator : public ::android::hardware::BpInterface<IEvsEnumerator>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwEvsEnumerator(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IEvsEnumerator Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::automotive::evs::V1_0::IEvsEnumerator follow.
    static ::android::hardware::Return<void>  _hidl_getCameraList(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getCameraList_cb _hidl_cb);
    static ::android::hardware::Return<::android::sp<::android::hardware::automotive::evs::V1_0::IEvsCamera>>  _hidl_openCamera(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& cameraId);
    static ::android::hardware::Return<void>  _hidl_closeCamera(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::automotive::evs::V1_0::IEvsCamera>& carCamera);
    static ::android::hardware::Return<::android::sp<::android::hardware::automotive::evs::V1_0::IEvsDisplay>>  _hidl_openDisplay(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);
    static ::android::hardware::Return<void>  _hidl_closeDisplay(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::automotive::evs::V1_0::IEvsDisplay>& display);
    static ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::DisplayState>  _hidl_getDisplayState(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);

    // Methods from ::android::hardware::automotive::evs::V1_0::IEvsEnumerator follow.
    ::android::hardware::Return<void> getCameraList(getCameraList_cb _hidl_cb) override;
    ::android::hardware::Return<::android::sp<::android::hardware::automotive::evs::V1_0::IEvsCamera>> openCamera(const ::android::hardware::hidl_string& cameraId) override;
    ::android::hardware::Return<void> closeCamera(const ::android::sp<::android::hardware::automotive::evs::V1_0::IEvsCamera>& carCamera) override;
    ::android::hardware::Return<::android::sp<::android::hardware::automotive::evs::V1_0::IEvsDisplay>> openDisplay() override;
    ::android::hardware::Return<void> closeDisplay(const ::android::sp<::android::hardware::automotive::evs::V1_0::IEvsDisplay>& display) override;
    ::android::hardware::Return<::android::hardware::automotive::evs::V1_0::DisplayState> getDisplayState() override;

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
}  // namespace evs
}  // namespace automotive
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_EVS_V1_0_BPHWEVSENUMERATOR_H
