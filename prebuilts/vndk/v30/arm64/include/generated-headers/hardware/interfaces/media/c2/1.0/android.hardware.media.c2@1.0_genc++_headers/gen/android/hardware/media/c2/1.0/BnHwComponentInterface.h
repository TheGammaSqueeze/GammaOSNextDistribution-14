#ifndef HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_BNHWCOMPONENTINTERFACE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_BNHWCOMPONENTINTERFACE_H

#include <android/hardware/media/c2/1.0/IHwComponentInterface.h>

namespace android {
namespace hardware {
namespace media {
namespace c2 {
namespace V1_0 {

struct BnHwComponentInterface : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwComponentInterface(const ::android::sp<IComponentInterface> &_hidl_impl);
    explicit BnHwComponentInterface(const ::android::sp<IComponentInterface> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwComponentInterface();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IComponentInterface Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IComponentInterface> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::media::c2::V1_0::IComponentInterface follow.
    static ::android::status_t _hidl_getConfigurable(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::media::c2::V1_0::IComponentInterface follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IComponentInterface> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace c2
}  // namespace media
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_MEDIA_C2_V1_0_BNHWCOMPONENTINTERFACE_H
