#ifndef HIDL_GENERATED_ANDROID_HIDL_TOKEN_V1_0_BNHWTOKENMANAGER_H
#define HIDL_GENERATED_ANDROID_HIDL_TOKEN_V1_0_BNHWTOKENMANAGER_H

#include <android/hidl/token/1.0/IHwTokenManager.h>

namespace android {
namespace hidl {
namespace token {
namespace V1_0 {

struct BnHwTokenManager : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwTokenManager(const ::android::sp<ITokenManager> &_hidl_impl);
    explicit BnHwTokenManager(const ::android::sp<ITokenManager> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwTokenManager();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef ITokenManager Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<ITokenManager> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hidl::token::V1_0::ITokenManager follow.
    static ::android::status_t _hidl_createToken(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_unregister(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_get(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hidl::token::V1_0::ITokenManager follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<ITokenManager> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace token
}  // namespace hidl
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HIDL_TOKEN_V1_0_BNHWTOKENMANAGER_H
