#ifndef HIDL_GENERATED_ANDROID_SYSTEM_WIFI_KEYSTORE_V1_0_BNHWKEYSTORE_H
#define HIDL_GENERATED_ANDROID_SYSTEM_WIFI_KEYSTORE_V1_0_BNHWKEYSTORE_H

#include <android/system/wifi/keystore/1.0/IHwKeystore.h>

namespace android {
namespace system {
namespace wifi {
namespace keystore {
namespace V1_0 {

struct BnHwKeystore : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwKeystore(const ::android::sp<IKeystore> &_hidl_impl);
    explicit BnHwKeystore(const ::android::sp<IKeystore> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwKeystore();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IKeystore Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IKeystore> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::system::wifi::keystore::V1_0::IKeystore follow.
    static ::android::status_t _hidl_getBlob(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_getPublicKey(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_sign(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::system::wifi::keystore::V1_0::IKeystore follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IKeystore> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace keystore
}  // namespace wifi
}  // namespace system
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_SYSTEM_WIFI_KEYSTORE_V1_0_BNHWKEYSTORE_H
