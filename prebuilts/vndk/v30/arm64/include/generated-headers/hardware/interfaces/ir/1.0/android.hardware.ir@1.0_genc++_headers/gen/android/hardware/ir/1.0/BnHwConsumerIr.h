#ifndef HIDL_GENERATED_ANDROID_HARDWARE_IR_V1_0_BNHWCONSUMERIR_H
#define HIDL_GENERATED_ANDROID_HARDWARE_IR_V1_0_BNHWCONSUMERIR_H

#include <android/hardware/ir/1.0/IHwConsumerIr.h>

namespace android {
namespace hardware {
namespace ir {
namespace V1_0 {

struct BnHwConsumerIr : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwConsumerIr(const ::android::sp<IConsumerIr> &_hidl_impl);
    explicit BnHwConsumerIr(const ::android::sp<IConsumerIr> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwConsumerIr();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IConsumerIr Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IConsumerIr> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::ir::V1_0::IConsumerIr follow.
    static ::android::status_t _hidl_transmit(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_getCarrierFreqs(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::ir::V1_0::IConsumerIr follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IConsumerIr> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace ir
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_IR_V1_0_BNHWCONSUMERIR_H
