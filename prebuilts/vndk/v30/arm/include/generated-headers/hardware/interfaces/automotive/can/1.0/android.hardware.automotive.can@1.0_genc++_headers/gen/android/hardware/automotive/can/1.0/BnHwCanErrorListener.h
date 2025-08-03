#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_CAN_V1_0_BNHWCANERRORLISTENER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_CAN_V1_0_BNHWCANERRORLISTENER_H

#include <android/hardware/automotive/can/1.0/IHwCanErrorListener.h>

namespace android {
namespace hardware {
namespace automotive {
namespace can {
namespace V1_0 {

struct BnHwCanErrorListener : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwCanErrorListener(const ::android::sp<ICanErrorListener> &_hidl_impl);
    explicit BnHwCanErrorListener(const ::android::sp<ICanErrorListener> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwCanErrorListener();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef ICanErrorListener Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<ICanErrorListener> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::automotive::can::V1_0::ICanErrorListener follow.
    static ::android::status_t _hidl_onError(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::automotive::can::V1_0::ICanErrorListener follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<ICanErrorListener> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace can
}  // namespace automotive
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_CAN_V1_0_BNHWCANERRORLISTENER_H
