#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CONFIRMATIONUI_V1_0_BPHWCONFIRMATIONUI_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CONFIRMATIONUI_V1_0_BPHWCONFIRMATIONUI_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/confirmationui/1.0/IHwConfirmationUI.h>

namespace android {
namespace hardware {
namespace confirmationui {
namespace V1_0 {

struct BpHwConfirmationUI : public ::android::hardware::BpInterface<IConfirmationUI>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwConfirmationUI(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IConfirmationUI Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::confirmationui::V1_0::IConfirmationUI follow.
    static ::android::hardware::Return<::android::hardware::confirmationui::V1_0::ResponseCode>  _hidl_promptUserConfirmation(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::confirmationui::V1_0::IConfirmationResultCallback>& resultCB, const ::android::hardware::hidl_string& promptText, const ::android::hardware::hidl_vec<uint8_t>& extraData, const ::android::hardware::hidl_string& locale, const ::android::hardware::hidl_vec<::android::hardware::confirmationui::V1_0::UIOption>& uiOptions);
    static ::android::hardware::Return<::android::hardware::confirmationui::V1_0::ResponseCode>  _hidl_deliverSecureInputEvent(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::keymaster::V4_0::HardwareAuthToken& secureInputToken);
    static ::android::hardware::Return<void>  _hidl_abort(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);

    // Methods from ::android::hardware::confirmationui::V1_0::IConfirmationUI follow.
    ::android::hardware::Return<::android::hardware::confirmationui::V1_0::ResponseCode> promptUserConfirmation(const ::android::sp<::android::hardware::confirmationui::V1_0::IConfirmationResultCallback>& resultCB, const ::android::hardware::hidl_string& promptText, const ::android::hardware::hidl_vec<uint8_t>& extraData, const ::android::hardware::hidl_string& locale, const ::android::hardware::hidl_vec<::android::hardware::confirmationui::V1_0::UIOption>& uiOptions) override;
    ::android::hardware::Return<::android::hardware::confirmationui::V1_0::ResponseCode> deliverSecureInputEvent(const ::android::hardware::keymaster::V4_0::HardwareAuthToken& secureInputToken) override;
    ::android::hardware::Return<void> abort() override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CONFIRMATIONUI_V1_0_BPHWCONFIRMATIONUI_H
