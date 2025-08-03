#ifndef HIDL_GENERATED_ANDROID_HARDWARE_TETHEROFFLOAD_CONTROL_V1_0_BNHWTETHERINGOFFLOADCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_TETHEROFFLOAD_CONTROL_V1_0_BNHWTETHERINGOFFLOADCALLBACK_H

#include <android/hardware/tetheroffload/control/1.0/IHwTetheringOffloadCallback.h>

namespace android {
namespace hardware {
namespace tetheroffload {
namespace control {
namespace V1_0 {

struct BnHwTetheringOffloadCallback : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwTetheringOffloadCallback(const ::android::sp<ITetheringOffloadCallback> &_hidl_impl);
    explicit BnHwTetheringOffloadCallback(const ::android::sp<ITetheringOffloadCallback> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwTetheringOffloadCallback();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef ITetheringOffloadCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<ITetheringOffloadCallback> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::tetheroffload::control::V1_0::ITetheringOffloadCallback follow.
    static ::android::status_t _hidl_onEvent(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_updateTimeout(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::tetheroffload::control::V1_0::ITetheringOffloadCallback follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<ITetheringOffloadCallback> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace control
}  // namespace tetheroffload
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_TETHEROFFLOAD_CONTROL_V1_0_BNHWTETHERINGOFFLOADCALLBACK_H
