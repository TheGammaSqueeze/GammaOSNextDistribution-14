#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CONFIRMATIONUI_V1_0_BNHWCONFIRMATIONUI_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CONFIRMATIONUI_V1_0_BNHWCONFIRMATIONUI_H

#include <android/hardware/confirmationui/1.0/IHwConfirmationUI.h>

namespace android {
namespace hardware {
namespace confirmationui {
namespace V1_0 {

struct BnHwConfirmationUI : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwConfirmationUI(const ::android::sp<IConfirmationUI> &_hidl_impl);
    explicit BnHwConfirmationUI(const ::android::sp<IConfirmationUI> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwConfirmationUI();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IConfirmationUI Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IConfirmationUI> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::confirmationui::V1_0::IConfirmationUI follow.
    static ::android::status_t _hidl_promptUserConfirmation(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_deliverSecureInputEvent(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_abort(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::confirmationui::V1_0::IConfirmationUI follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IConfirmationUI> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace confirmationui
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CONFIRMATIONUI_V1_0_BNHWCONFIRMATIONUI_H
