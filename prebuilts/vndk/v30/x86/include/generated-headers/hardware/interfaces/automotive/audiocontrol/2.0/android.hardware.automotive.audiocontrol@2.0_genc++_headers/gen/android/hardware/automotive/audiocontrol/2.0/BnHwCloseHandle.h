#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_AUDIOCONTROL_V2_0_BNHWCLOSEHANDLE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_AUDIOCONTROL_V2_0_BNHWCLOSEHANDLE_H

#include <android/hardware/automotive/audiocontrol/2.0/IHwCloseHandle.h>

namespace android {
namespace hardware {
namespace automotive {
namespace audiocontrol {
namespace V2_0 {

struct BnHwCloseHandle : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwCloseHandle(const ::android::sp<ICloseHandle> &_hidl_impl);
    explicit BnHwCloseHandle(const ::android::sp<ICloseHandle> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwCloseHandle();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef ICloseHandle Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<ICloseHandle> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::automotive::audiocontrol::V2_0::ICloseHandle follow.
    static ::android::status_t _hidl_close(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::automotive::audiocontrol::V2_0::ICloseHandle follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<ICloseHandle> _hidl_mImpl;
};

}  // namespace V2_0
}  // namespace audiocontrol
}  // namespace automotive
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_AUDIOCONTROL_V2_0_BNHWCLOSEHANDLE_H
