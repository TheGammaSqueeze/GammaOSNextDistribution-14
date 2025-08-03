#ifndef HIDL_GENERATED_ANDROID_HARDWARE_CAS_NATIVE_V1_0_BNHWDESCRAMBLER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_CAS_NATIVE_V1_0_BNHWDESCRAMBLER_H

#include <android/hardware/cas/native/1.0/IHwDescrambler.h>

namespace android {
namespace hardware {
namespace cas {
namespace native {
namespace V1_0 {

struct BnHwDescrambler : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwDescrambler(const ::android::sp<IDescrambler> &_hidl_impl);
    explicit BnHwDescrambler(const ::android::sp<IDescrambler> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwDescrambler();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IDescrambler Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IDescrambler> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::cas::native::V1_0::IDescrambler follow.
    static ::android::status_t _hidl_descramble(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::cas::V1_0::IDescramblerBase follow.

    // Methods from ::android::hardware::cas::native::V1_0::IDescrambler follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IDescrambler> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace native
}  // namespace cas
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_CAS_NATIVE_V1_0_BNHWDESCRAMBLER_H
