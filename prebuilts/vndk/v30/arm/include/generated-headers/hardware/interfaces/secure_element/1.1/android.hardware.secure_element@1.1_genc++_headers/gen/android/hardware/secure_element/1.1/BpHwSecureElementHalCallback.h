#ifndef HIDL_GENERATED_ANDROID_HARDWARE_SECURE_ELEMENT_V1_1_BPHWSECUREELEMENTHALCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_SECURE_ELEMENT_V1_1_BPHWSECUREELEMENTHALCALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/secure_element/1.1/IHwSecureElementHalCallback.h>

namespace android {
namespace hardware {
namespace secure_element {
namespace V1_1 {

struct BpHwSecureElementHalCallback : public ::android::hardware::BpInterface<ISecureElementHalCallback>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwSecureElementHalCallback(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ISecureElementHalCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::secure_element::V1_1::ISecureElementHalCallback follow.
    static ::android::hardware::Return<void>  _hidl_onStateChange_1_1(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, bool connected, const ::android::hardware::hidl_string& debugReason);

    // Methods from ::android::hardware::secure_element::V1_0::ISecureElementHalCallback follow.
    ::android::hardware::Return<void> onStateChange(bool connected) override;

    // Methods from ::android::hardware::secure_element::V1_1::ISecureElementHalCallback follow.
    ::android::hardware::Return<void> onStateChange_1_1(bool connected, const ::android::hardware::hidl_string& debugReason) override;

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
}  // namespace secure_element
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_SECURE_ELEMENT_V1_1_BPHWSECUREELEMENTHALCALLBACK_H
