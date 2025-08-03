#ifndef HIDL_GENERATED_ANDROID_HARDWARE_USB_V1_1_BPHWUSBCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_USB_V1_1_BPHWUSBCALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/usb/1.1/IHwUsbCallback.h>

namespace android {
namespace hardware {
namespace usb {
namespace V1_1 {

struct BpHwUsbCallback : public ::android::hardware::BpInterface<IUsbCallback>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwUsbCallback(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IUsbCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::usb::V1_1::IUsbCallback follow.
    static ::android::hardware::Return<void>  _hidl_notifyPortStatusChange_1_1(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::usb::V1_1::PortStatus_1_1>& currentPortStatus, ::android::hardware::usb::V1_0::Status retval);

    // Methods from ::android::hardware::usb::V1_0::IUsbCallback follow.
    ::android::hardware::Return<void> notifyPortStatusChange(const ::android::hardware::hidl_vec<::android::hardware::usb::V1_0::PortStatus>& currentPortStatus, ::android::hardware::usb::V1_0::Status retval) override;
    ::android::hardware::Return<void> notifyRoleSwitchStatus(const ::android::hardware::hidl_string& portName, const ::android::hardware::usb::V1_0::PortRole& newRole, ::android::hardware::usb::V1_0::Status retval) override;

    // Methods from ::android::hardware::usb::V1_1::IUsbCallback follow.
    ::android::hardware::Return<void> notifyPortStatusChange_1_1(const ::android::hardware::hidl_vec<::android::hardware::usb::V1_1::PortStatus_1_1>& currentPortStatus, ::android::hardware::usb::V1_0::Status retval) override;

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
}  // namespace usb
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_USB_V1_1_BPHWUSBCALLBACK_H
