#ifndef HIDL_GENERATED_ANDROID_HARDWARE_USB_GADGET_V1_0_BPHWUSBGADGET_H
#define HIDL_GENERATED_ANDROID_HARDWARE_USB_GADGET_V1_0_BPHWUSBGADGET_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/usb/gadget/1.0/IHwUsbGadget.h>

namespace android {
namespace hardware {
namespace usb {
namespace gadget {
namespace V1_0 {

struct BpHwUsbGadget : public ::android::hardware::BpInterface<IUsbGadget>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwUsbGadget(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IUsbGadget Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::usb::gadget::V1_0::IUsbGadget follow.
    static ::android::hardware::Return<void>  _hidl_setCurrentUsbFunctions(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::hidl_bitfield<::android::hardware::usb::gadget::V1_0::GadgetFunction> functions, const ::android::sp<::android::hardware::usb::gadget::V1_0::IUsbGadgetCallback>& callback, uint64_t timeout);
    static ::android::hardware::Return<void>  _hidl_getCurrentUsbFunctions(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::usb::gadget::V1_0::IUsbGadgetCallback>& callback);

    // Methods from ::android::hardware::usb::gadget::V1_0::IUsbGadget follow.
    ::android::hardware::Return<void> setCurrentUsbFunctions(::android::hardware::hidl_bitfield<::android::hardware::usb::gadget::V1_0::GadgetFunction> functions, const ::android::sp<::android::hardware::usb::gadget::V1_0::IUsbGadgetCallback>& callback, uint64_t timeout) override;
    ::android::hardware::Return<void> getCurrentUsbFunctions(const ::android::sp<::android::hardware::usb::gadget::V1_0::IUsbGadgetCallback>& callback) override;

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
}  // namespace gadget
}  // namespace usb
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_USB_GADGET_V1_0_BPHWUSBGADGET_H
