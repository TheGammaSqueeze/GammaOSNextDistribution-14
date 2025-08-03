#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V2_0_BNHWTUNERCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V2_0_BNHWTUNERCALLBACK_H

#include <android/hardware/broadcastradio/2.0/IHwTunerCallback.h>

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V2_0 {

struct BnHwTunerCallback : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwTunerCallback(const ::android::sp<ITunerCallback> &_hidl_impl);
    explicit BnHwTunerCallback(const ::android::sp<ITunerCallback> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwTunerCallback();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef ITunerCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<ITunerCallback> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::broadcastradio::V2_0::ITunerCallback follow.
    static ::android::status_t _hidl_onTuneFailed(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_onCurrentProgramInfoChanged(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_onProgramListUpdated(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_onAntennaStateChange(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_onParametersUpdated(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::broadcastradio::V2_0::ITunerCallback follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<ITunerCallback> _hidl_mImpl;
};

}  // namespace V2_0
}  // namespace broadcastradio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V2_0_BNHWTUNERCALLBACK_H
