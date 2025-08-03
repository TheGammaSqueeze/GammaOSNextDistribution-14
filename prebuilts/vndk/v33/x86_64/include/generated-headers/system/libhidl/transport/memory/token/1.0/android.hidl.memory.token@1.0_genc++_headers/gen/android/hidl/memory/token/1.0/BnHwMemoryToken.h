#ifndef HIDL_GENERATED_ANDROID_HIDL_MEMORY_TOKEN_V1_0_BNHWMEMORYTOKEN_H
#define HIDL_GENERATED_ANDROID_HIDL_MEMORY_TOKEN_V1_0_BNHWMEMORYTOKEN_H

#include <android/hidl/memory/token/1.0/IHwMemoryToken.h>

namespace android {
namespace hidl {
namespace memory {
namespace token {
namespace V1_0 {

struct BnHwMemoryToken : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwMemoryToken(const ::android::sp<IMemoryToken> &_hidl_impl);
    explicit BnHwMemoryToken(const ::android::sp<IMemoryToken> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwMemoryToken();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IMemoryToken Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IMemoryToken> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hidl::memory::token::V1_0::IMemoryToken follow.
    static ::android::status_t _hidl_get(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hidl::memory::token::V1_0::IMemoryToken follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IMemoryToken> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace token
}  // namespace memory
}  // namespace hidl
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HIDL_MEMORY_TOKEN_V1_0_BNHWMEMORYTOKEN_H
