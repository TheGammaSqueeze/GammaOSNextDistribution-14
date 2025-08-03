#ifndef HIDL_GENERATED_ANDROID_HARDWARE_USB_V1_0_BPHWUSB_H
#define HIDL_GENERATED_ANDROID_HARDWARE_USB_V1_0_BPHWUSB_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/usb/1.0/IHwUsb.h>

namespace android {
namespace hardware {
namespace usb {
namespace V1_0 {

struct BpHwUsb : public ::android::hardware::BpInterface<IUsb>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwUsb(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IUsb Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::usb::V1_0::IUsb follow.
    static ::android::hardware::Return<void>  _hidl_switchRole(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& portName, const ::android::hardware::usb::V1_0::PortRole& role);
    static ::android::hardware::Return<void>  _hidl_setCallback(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::usb::V1_0::IUsbCallback>& callback);
    static ::android::hardware::Return<void>  _hidl_queryPortStatus(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);

    // Methods from ::android::hardware::usb::V1_0::IUsb follow.
    ::android::hardware::Return<void> switchRole(const ::android::hardware::hidl_string& portName, const ::android::hardware::usb::V1_0::PortRole& role) override;
    ::android::hardware::Return<void> setCallback(const ::android::sp<::android::hardware::usb::V1_0::IUsbCallback>& callback) override;
    ::android::hardware::Return<void> queryPortStatus() override;

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
}  // namespace usb
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_USB_V1_0_BPHWUSB_H
