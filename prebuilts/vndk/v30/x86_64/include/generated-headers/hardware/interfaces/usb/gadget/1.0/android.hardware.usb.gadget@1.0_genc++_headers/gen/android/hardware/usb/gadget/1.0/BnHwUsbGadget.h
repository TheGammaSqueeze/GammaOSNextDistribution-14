#ifndef HIDL_GENERATED_ANDROID_HARDWARE_USB_GADGET_V1_0_BNHWUSBGADGET_H
#define HIDL_GENERATED_ANDROID_HARDWARE_USB_GADGET_V1_0_BNHWUSBGADGET_H

#include <android/hardware/usb/gadget/1.0/IHwUsbGadget.h>

namespace android {
namespace hardware {
namespace usb {
namespace gadget {
namespace V1_0 {

struct BnHwUsbGadget : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwUsbGadget(const ::android::sp<IUsbGadget> &_hidl_impl);
    explicit BnHwUsbGadget(const ::android::sp<IUsbGadget> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwUsbGadget();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IUsbGadget Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IUsbGadget> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::usb::gadget::V1_0::IUsbGadget follow.
    static ::android::status_t _hidl_setCurrentUsbFunctions(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_getCurrentUsbFunctions(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::usb::gadget::V1_0::IUsbGadget follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IUsbGadget> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace gadget
}  // namespace usb
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_USB_GADGET_V1_0_BNHWUSBGADGET_H
