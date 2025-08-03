#ifndef HIDL_GENERATED_ANDROID_HARDWARE_NFC_V1_1_BNHWNFCCLIENTCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_NFC_V1_1_BNHWNFCCLIENTCALLBACK_H

#include <android/hardware/nfc/1.1/IHwNfcClientCallback.h>

namespace android {
namespace hardware {
namespace nfc {
namespace V1_1 {

struct BnHwNfcClientCallback : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwNfcClientCallback(const ::android::sp<INfcClientCallback> &_hidl_impl);
    explicit BnHwNfcClientCallback(const ::android::sp<INfcClientCallback> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwNfcClientCallback();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef INfcClientCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<INfcClientCallback> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::nfc::V1_1::INfcClientCallback follow.
    static ::android::status_t _hidl_sendEvent_1_1(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::nfc::V1_0::INfcClientCallback follow.

    // Methods from ::android::hardware::nfc::V1_1::INfcClientCallback follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<INfcClientCallback> _hidl_mImpl;
};

}  // namespace V1_1
}  // namespace nfc
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_NFC_V1_1_BNHWNFCCLIENTCALLBACK_H
