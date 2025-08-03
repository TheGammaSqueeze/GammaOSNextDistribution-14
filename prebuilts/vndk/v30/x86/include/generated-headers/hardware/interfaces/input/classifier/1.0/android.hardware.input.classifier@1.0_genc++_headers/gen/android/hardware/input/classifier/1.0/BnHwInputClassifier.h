#ifndef HIDL_GENERATED_ANDROID_HARDWARE_INPUT_CLASSIFIER_V1_0_BNHWINPUTCLASSIFIER_H
#define HIDL_GENERATED_ANDROID_HARDWARE_INPUT_CLASSIFIER_V1_0_BNHWINPUTCLASSIFIER_H

#include <android/hardware/input/classifier/1.0/IHwInputClassifier.h>

namespace android {
namespace hardware {
namespace input {
namespace classifier {
namespace V1_0 {

struct BnHwInputClassifier : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwInputClassifier(const ::android::sp<IInputClassifier> &_hidl_impl);
    explicit BnHwInputClassifier(const ::android::sp<IInputClassifier> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwInputClassifier();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IInputClassifier Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IInputClassifier> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::input::classifier::V1_0::IInputClassifier follow.
    static ::android::status_t _hidl_classify(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_reset(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_resetDevice(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::input::classifier::V1_0::IInputClassifier follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IInputClassifier> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace classifier
}  // namespace input
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_INPUT_CLASSIFIER_V1_0_BNHWINPUTCLASSIFIER_H
