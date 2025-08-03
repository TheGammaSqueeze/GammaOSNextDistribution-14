#ifndef HIDL_GENERATED_ANDROID_HARDWARE_TV_INPUT_V1_0_BNHWTVINPUT_H
#define HIDL_GENERATED_ANDROID_HARDWARE_TV_INPUT_V1_0_BNHWTVINPUT_H

#include <android/hardware/tv/input/1.0/IHwTvInput.h>

namespace android {
namespace hardware {
namespace tv {
namespace input {
namespace V1_0 {

struct BnHwTvInput : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwTvInput(const ::android::sp<ITvInput> &_hidl_impl);
    explicit BnHwTvInput(const ::android::sp<ITvInput> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwTvInput();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef ITvInput Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<ITvInput> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::tv::input::V1_0::ITvInput follow.
    static ::android::status_t _hidl_setCallback(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_getStreamConfigurations(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_openStream(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_closeStream(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::tv::input::V1_0::ITvInput follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<ITvInput> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace input
}  // namespace tv
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_TV_INPUT_V1_0_BNHWTVINPUT_H
