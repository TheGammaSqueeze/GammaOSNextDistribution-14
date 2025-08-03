#ifndef HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_BNHWFRONTENDCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_BNHWFRONTENDCALLBACK_H

#include <android/hardware/tv/tuner/1.0/IHwFrontendCallback.h>

namespace android {
namespace hardware {
namespace tv {
namespace tuner {
namespace V1_0 {

struct BnHwFrontendCallback : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwFrontendCallback(const ::android::sp<IFrontendCallback> &_hidl_impl);
    explicit BnHwFrontendCallback(const ::android::sp<IFrontendCallback> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwFrontendCallback();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IFrontendCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IFrontendCallback> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::tv::tuner::V1_0::IFrontendCallback follow.
    static ::android::status_t _hidl_onEvent(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_onScanMessage(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::tv::tuner::V1_0::IFrontendCallback follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IFrontendCallback> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace tuner
}  // namespace tv
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_TV_TUNER_V1_0_BNHWFRONTENDCALLBACK_H
