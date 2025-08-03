#ifndef HIDL_GENERATED_ANDROID_HARDWARE_NFC_V1_2_BPHWNFC_H
#define HIDL_GENERATED_ANDROID_HARDWARE_NFC_V1_2_BPHWNFC_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/nfc/1.2/IHwNfc.h>

namespace android {
namespace hardware {
namespace nfc {
namespace V1_2 {

struct BpHwNfc : public ::android::hardware::BpInterface<INfc>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwNfc(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef INfc Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::nfc::V1_2::INfc follow.
    static ::android::hardware::Return<void>  _hidl_getConfig_1_2(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getConfig_1_2_cb _hidl_cb);

    // Methods from ::android::hardware::nfc::V1_0::INfc follow.
    ::android::hardware::Return<::android::hardware::nfc::V1_0::NfcStatus> open(const ::android::sp<::android::hardware::nfc::V1_0::INfcClientCallback>& clientCallback) override;
    ::android::hardware::Return<uint32_t> write(const ::android::hardware::hidl_vec<uint8_t>& data) override;
    ::android::hardware::Return<::android::hardware::nfc::V1_0::NfcStatus> coreInitialized(const ::android::hardware::hidl_vec<uint8_t>& data) override;
    ::android::hardware::Return<::android::hardware::nfc::V1_0::NfcStatus> prediscover() override;
    ::android::hardware::Return<::android::hardware::nfc::V1_0::NfcStatus> close() override;
    ::android::hardware::Return<::android::hardware::nfc::V1_0::NfcStatus> controlGranted() override;
    ::android::hardware::Return<::android::hardware::nfc::V1_0::NfcStatus> powerCycle() override;

    // Methods from ::android::hardware::nfc::V1_1::INfc follow.
    ::android::hardware::Return<void> factoryReset() override;
    ::android::hardware::Return<::android::hardware::nfc::V1_0::NfcStatus> closeForPowerOffCase() override;
    ::android::hardware::Return<::android::hardware::nfc::V1_0::NfcStatus> open_1_1(const ::android::sp<::android::hardware::nfc::V1_1::INfcClientCallback>& clientCallback) override;
    ::android::hardware::Return<void> getConfig(getConfig_cb _hidl_cb) override;

    // Methods from ::android::hardware::nfc::V1_2::INfc follow.
    ::android::hardware::Return<void> getConfig_1_2(getConfig_1_2_cb _hidl_cb) override;

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

}  // namespace V1_2
}  // namespace nfc
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_NFC_V1_2_BPHWNFC_H
