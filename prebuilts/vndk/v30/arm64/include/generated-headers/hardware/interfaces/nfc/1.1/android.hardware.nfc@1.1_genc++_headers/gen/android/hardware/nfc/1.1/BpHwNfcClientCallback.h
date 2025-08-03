#ifndef HIDL_GENERATED_ANDROID_HARDWARE_NFC_V1_1_BPHWNFCCLIENTCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_NFC_V1_1_BPHWNFCCLIENTCALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/nfc/1.1/IHwNfcClientCallback.h>

namespace android {
namespace hardware {
namespace nfc {
namespace V1_1 {

struct BpHwNfcClientCallback : public ::android::hardware::BpInterface<INfcClientCallback>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwNfcClientCallback(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef INfcClientCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::nfc::V1_1::INfcClientCallback follow.
    static ::android::hardware::Return<void>  _hidl_sendEvent_1_1(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::nfc::V1_1::NfcEvent event, ::android::hardware::nfc::V1_0::NfcStatus status);

    // Methods from ::android::hardware::nfc::V1_0::INfcClientCallback follow.
    ::android::hardware::Return<void> sendEvent(::android::hardware::nfc::V1_0::NfcEvent event, ::android::hardware::nfc::V1_0::NfcStatus status) override;
    ::android::hardware::Return<void> sendData(const ::android::hardware::hidl_vec<uint8_t>& data) override;

    // Methods from ::android::hardware::nfc::V1_1::INfcClientCallback follow.
    ::android::hardware::Return<void> sendEvent_1_1(::android::hardware::nfc::V1_1::NfcEvent event, ::android::hardware::nfc::V1_0::NfcStatus status) override;

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
}  // namespace nfc
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_NFC_V1_1_BPHWNFCCLIENTCALLBACK_H
