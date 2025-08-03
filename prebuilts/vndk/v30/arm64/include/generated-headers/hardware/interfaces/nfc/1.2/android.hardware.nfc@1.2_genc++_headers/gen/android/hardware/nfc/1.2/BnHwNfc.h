#ifndef HIDL_GENERATED_ANDROID_HARDWARE_NFC_V1_2_BNHWNFC_H
#define HIDL_GENERATED_ANDROID_HARDWARE_NFC_V1_2_BNHWNFC_H

#include <android/hardware/nfc/1.2/IHwNfc.h>

namespace android {
namespace hardware {
namespace nfc {
namespace V1_2 {

struct BnHwNfc : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwNfc(const ::android::sp<INfc> &_hidl_impl);
    explicit BnHwNfc(const ::android::sp<INfc> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwNfc();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef INfc Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<INfc> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::nfc::V1_2::INfc follow.
    static ::android::status_t _hidl_getConfig_1_2(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::nfc::V1_0::INfc follow.

    // Methods from ::android::hardware::nfc::V1_1::INfc follow.

    // Methods from ::android::hardware::nfc::V1_2::INfc follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<INfc> _hidl_mImpl;
};

}  // namespace V1_2
}  // namespace nfc
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_NFC_V1_2_BNHWNFC_H
