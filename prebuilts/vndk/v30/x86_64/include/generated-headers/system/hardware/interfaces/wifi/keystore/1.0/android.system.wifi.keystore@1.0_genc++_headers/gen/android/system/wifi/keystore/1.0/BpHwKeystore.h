#ifndef HIDL_GENERATED_ANDROID_SYSTEM_WIFI_KEYSTORE_V1_0_BPHWKEYSTORE_H
#define HIDL_GENERATED_ANDROID_SYSTEM_WIFI_KEYSTORE_V1_0_BPHWKEYSTORE_H

#include <hidl/HidlTransportSupport.h>

#include <android/system/wifi/keystore/1.0/IHwKeystore.h>

namespace android {
namespace system {
namespace wifi {
namespace keystore {
namespace V1_0 {

struct BpHwKeystore : public ::android::hardware::BpInterface<IKeystore>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwKeystore(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IKeystore Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::system::wifi::keystore::V1_0::IKeystore follow.
    static ::android::hardware::Return<void>  _hidl_getBlob(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& key, getBlob_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_getPublicKey(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& keyId, getPublicKey_cb _hidl_cb);
    static ::android::hardware::Return<void>  _hidl_sign(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_string& keyId, const ::android::hardware::hidl_vec<uint8_t>& dataToSign, sign_cb _hidl_cb);

    // Methods from ::android::system::wifi::keystore::V1_0::IKeystore follow.
    ::android::hardware::Return<void> getBlob(const ::android::hardware::hidl_string& key, getBlob_cb _hidl_cb) override;
    ::android::hardware::Return<void> getPublicKey(const ::android::hardware::hidl_string& keyId, getPublicKey_cb _hidl_cb) override;
    ::android::hardware::Return<void> sign(const ::android::hardware::hidl_string& keyId, const ::android::hardware::hidl_vec<uint8_t>& dataToSign, sign_cb _hidl_cb) override;

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
}  // namespace keystore
}  // namespace wifi
}  // namespace system
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_SYSTEM_WIFI_KEYSTORE_V1_0_BPHWKEYSTORE_H
