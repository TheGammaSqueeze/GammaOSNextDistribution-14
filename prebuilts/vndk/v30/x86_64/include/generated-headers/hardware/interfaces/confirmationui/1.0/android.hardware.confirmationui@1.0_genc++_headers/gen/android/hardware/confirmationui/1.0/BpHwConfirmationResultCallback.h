#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CONFIRMATIONUI_V1_0_BPHWCONFIRMATIONRESULTCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CONFIRMATIONUI_V1_0_BPHWCONFIRMATIONRESULTCALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/confirmationui/1.0/IHwConfirmationResultCallback.h>

namespace android {
namespace hardware {
namespace confirmationui {
namespace V1_0 {

struct BpHwConfirmationResultCallback : public ::android::hardware::BpInterface<IConfirmationResultCallback>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwConfirmationResultCallback(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IConfirmationResultCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::confirmationui::V1_0::IConfirmationResultCallback follow.
    static ::android::hardware::Return<void>  _hidl_result(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::confirmationui::V1_0::ResponseCode error, const ::android::hardware::hidl_vec<uint8_t>& formattedMessage, const ::android::hardware::hidl_vec<uint8_t>& confirmationToken);

    // Methods from ::android::hardware::confirmationui::V1_0::IConfirmationResultCallback follow.
    ::android::hardware::Return<void> result(::android::hardware::confirmationui::V1_0::ResponseCode error, const ::android::hardware::hidl_vec<uint8_t>& formattedMessage, const ::android::hardware::hidl_vec<uint8_t>& confirmationToken) override;

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
}  // namespace confirmationui
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CONFIRMATIONUI_V1_0_BPHWCONFIRMATIONRESULTCALLBACK_H
