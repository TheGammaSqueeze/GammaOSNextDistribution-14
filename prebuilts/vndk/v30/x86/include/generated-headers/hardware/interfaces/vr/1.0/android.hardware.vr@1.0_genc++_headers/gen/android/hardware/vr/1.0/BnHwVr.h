#ifndef HIDL_GENERATED_ANDROID_HARDWARE_VR_V1_0_BNHWVR_H
#define HIDL_GENERATED_ANDROID_HARDWARE_VR_V1_0_BNHWVR_H

#include <android/hardware/vr/1.0/IHwVr.h>

namespace android {
namespace hardware {
namespace vr {
namespace V1_0 {

struct BnHwVr : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwVr(const ::android::sp<IVr> &_hidl_impl);
    explicit BnHwVr(const ::android::sp<IVr> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwVr();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IVr Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IVr> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::vr::V1_0::IVr follow.
    static ::android::status_t _hidl_init(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_setVrMode(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::vr::V1_0::IVr follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IVr> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace vr
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_VR_V1_0_BNHWVR_H
