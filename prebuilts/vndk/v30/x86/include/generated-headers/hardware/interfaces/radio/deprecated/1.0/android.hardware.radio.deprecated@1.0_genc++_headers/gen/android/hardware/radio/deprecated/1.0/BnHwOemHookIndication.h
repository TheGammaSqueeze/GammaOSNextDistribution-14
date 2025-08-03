#ifndef HIDL_GENERATED_ANDROID_HARDWARE_RADIO_DEPRECATED_V1_0_BNHWOEMHOOKINDICATION_H
#define HIDL_GENERATED_ANDROID_HARDWARE_RADIO_DEPRECATED_V1_0_BNHWOEMHOOKINDICATION_H

#include <android/hardware/radio/deprecated/1.0/IHwOemHookIndication.h>

namespace android {
namespace hardware {
namespace radio {
namespace deprecated {
namespace V1_0 {

struct BnHwOemHookIndication : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwOemHookIndication(const ::android::sp<IOemHookIndication> &_hidl_impl);
    explicit BnHwOemHookIndication(const ::android::sp<IOemHookIndication> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwOemHookIndication();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IOemHookIndication Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IOemHookIndication> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::radio::deprecated::V1_0::IOemHookIndication follow.
    static ::android::status_t _hidl_oemHookRaw(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::radio::deprecated::V1_0::IOemHookIndication follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IOemHookIndication> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace deprecated
}  // namespace radio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_RADIO_DEPRECATED_V1_0_BNHWOEMHOOKINDICATION_H
