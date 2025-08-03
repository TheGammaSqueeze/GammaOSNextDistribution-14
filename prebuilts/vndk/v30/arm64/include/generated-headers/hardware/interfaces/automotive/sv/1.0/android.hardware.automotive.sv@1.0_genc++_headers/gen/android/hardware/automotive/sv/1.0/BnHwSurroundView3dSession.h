#ifndef HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_SV_V1_0_BNHWSURROUNDVIEW3DSESSION_H
#define HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_SV_V1_0_BNHWSURROUNDVIEW3DSESSION_H

#include <android/hardware/automotive/sv/1.0/IHwSurroundView3dSession.h>

namespace android {
namespace hardware {
namespace automotive {
namespace sv {
namespace V1_0 {

struct BnHwSurroundView3dSession : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwSurroundView3dSession(const ::android::sp<ISurroundView3dSession> &_hidl_impl);
    explicit BnHwSurroundView3dSession(const ::android::sp<ISurroundView3dSession> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwSurroundView3dSession();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef ISurroundView3dSession Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<ISurroundView3dSession> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::automotive::sv::V1_0::ISurroundView3dSession follow.
    static ::android::status_t _hidl_setViews(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_set3dConfig(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_get3dConfig(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_updateOverlays(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_projectCameraPointsTo3dSurface(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::automotive::sv::V1_0::ISurroundViewSession follow.

    // Methods from ::android::hardware::automotive::sv::V1_0::ISurroundView3dSession follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<ISurroundView3dSession> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace sv
}  // namespace automotive
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_AUTOMOTIVE_SV_V1_0_BNHWSURROUNDVIEW3DSESSION_H
