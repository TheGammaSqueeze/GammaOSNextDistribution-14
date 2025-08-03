#ifndef HIDL_GENERATED_ANDROID_HARDWARE_SECURE_ELEMENT_V1_1_BPHWSECUREELEMENT_H
#define HIDL_GENERATED_ANDROID_HARDWARE_SECURE_ELEMENT_V1_1_BPHWSECUREELEMENT_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/secure_element/1.1/IHwSecureElement.h>

namespace android {
namespace hardware {
namespace secure_element {
namespace V1_1 {

struct BpHwSecureElement : public ::android::hardware::BpInterface<ISecureElement>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwSecureElement(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ISecureElement Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::secure_element::V1_1::ISecureElement follow.
    static ::android::hardware::Return<void>  _hidl_init_1_1(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::secure_element::V1_1::ISecureElementHalCallback>& clientCallback);

    // Methods from ::android::hardware::secure_element::V1_0::ISecureElement follow.
    ::android::hardware::Return<void> init(const ::android::sp<::android::hardware::secure_element::V1_0::ISecureElementHalCallback>& clientCallback) override;
    ::android::hardware::Return<void> getAtr(getAtr_cb _hidl_cb) override;
    ::android::hardware::Return<bool> isCardPresent() override;
    ::android::hardware::Return<void> transmit(const ::android::hardware::hidl_vec<uint8_t>& data, transmit_cb _hidl_cb) override;
    ::android::hardware::Return<void> openLogicalChannel(const ::android::hardware::hidl_vec<uint8_t>& aid, uint8_t p2, openLogicalChannel_cb _hidl_cb) override;
    ::android::hardware::Return<void> openBasicChannel(const ::android::hardware::hidl_vec<uint8_t>& aid, uint8_t p2, openBasicChannel_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::secure_element::V1_0::SecureElementStatus> closeChannel(uint8_t channelNumber) override;

    // Methods from ::android::hardware::secure_element::V1_1::ISecureElement follow.
    ::android::hardware::Return<void> init_1_1(const ::android::sp<::android::hardware::secure_element::V1_1::ISecureElementHalCallback>& clientCallback) override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_SECURE_ELEMENT_V1_1_BPHWSECUREELEMENT_H
