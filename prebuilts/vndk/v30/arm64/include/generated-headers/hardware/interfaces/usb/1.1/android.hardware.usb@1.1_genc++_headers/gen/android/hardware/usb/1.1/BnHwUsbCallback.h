#ifndef HIDL_GENERATED_ANDROID_HARDWARE_USB_V1_1_BNHWUSBCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_USB_V1_1_BNHWUSBCALLBACK_H

#include <android/hardware/usb/1.1/IHwUsbCallback.h>

namespace android {
namespace hardware {
namespace usb {
namespace V1_1 {

struct BnHwUsbCallback : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwUsbCallback(const ::android::sp<IUsbCallback> &_hidl_impl);
    explicit BnHwUsbCallback(const ::android::sp<IUsbCallback> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwUsbCallback();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IUsbCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IUsbCallback> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::usb::V1_1::IUsbCallback follow.
    static ::android::status_t _hidl_notifyPortStatusChange_1_1(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::usb::V1_0::IUsbCallback follow.

    // Methods from ::android::hardware::usb::V1_1::IUsbCallback follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IUsbCallback> _hidl_mImpl;
};

}  // namespace V1_1
}  // namespace usb
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_USB_V1_1_BNHWUSBCALLBACK_H
