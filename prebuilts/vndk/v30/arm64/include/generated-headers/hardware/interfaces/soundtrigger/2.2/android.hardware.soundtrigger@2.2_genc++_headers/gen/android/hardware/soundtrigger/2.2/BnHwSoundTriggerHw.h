#ifndef HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_2_BNHWSOUNDTRIGGERHW_H
#define HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_2_BNHWSOUNDTRIGGERHW_H

#include <android/hardware/soundtrigger/2.2/IHwSoundTriggerHw.h>

namespace android {
namespace hardware {
namespace soundtrigger {
namespace V2_2 {

struct BnHwSoundTriggerHw : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwSoundTriggerHw(const ::android::sp<ISoundTriggerHw> &_hidl_impl);
    explicit BnHwSoundTriggerHw(const ::android::sp<ISoundTriggerHw> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwSoundTriggerHw();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef ISoundTriggerHw Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<ISoundTriggerHw> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::soundtrigger::V2_2::ISoundTriggerHw follow.
    static ::android::status_t _hidl_getModelState(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::soundtrigger::V2_0::ISoundTriggerHw follow.

    // Methods from ::android::hardware::soundtrigger::V2_1::ISoundTriggerHw follow.

    // Methods from ::android::hardware::soundtrigger::V2_2::ISoundTriggerHw follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<ISoundTriggerHw> _hidl_mImpl;
};

}  // namespace V2_2
}  // namespace soundtrigger
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_SOUNDTRIGGER_V2_2_BNHWSOUNDTRIGGERHW_H
