#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAS_V1_0_BNHWDESCRAMBLERBASE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAS_V1_0_BNHWDESCRAMBLERBASE_H

#include <android/hardware/cas/1.0/IHwDescramblerBase.h>

namespace android {
namespace hardware {
namespace cas {
namespace V1_0 {

struct BnHwDescramblerBase : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwDescramblerBase(const ::android::sp<IDescramblerBase> &_hidl_impl);
    explicit BnHwDescramblerBase(const ::android::sp<IDescramblerBase> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwDescramblerBase();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IDescramblerBase Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IDescramblerBase> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::cas::V1_0::IDescramblerBase follow.
    static ::android::status_t _hidl_setMediaCasSession(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_requiresSecureDecoderComponent(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_release(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::cas::V1_0::IDescramblerBase follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IDescramblerBase> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace cas
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAS_V1_0_BNHWDESCRAMBLERBASE_H
