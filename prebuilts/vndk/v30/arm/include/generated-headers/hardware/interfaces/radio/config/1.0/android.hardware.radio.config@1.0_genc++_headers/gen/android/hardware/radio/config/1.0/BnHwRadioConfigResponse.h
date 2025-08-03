#ifndef HIDL_GENERATED_ANDROID_HARDWARE_RADIO_CONFIG_V1_0_BNHWRADIOCONFIGRESPONSE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_RADIO_CONFIG_V1_0_BNHWRADIOCONFIGRESPONSE_H

#include <android/hardware/radio/config/1.0/IHwRadioConfigResponse.h>

namespace android {
namespace hardware {
namespace radio {
namespace config {
namespace V1_0 {

struct BnHwRadioConfigResponse : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwRadioConfigResponse(const ::android::sp<IRadioConfigResponse> &_hidl_impl);
    explicit BnHwRadioConfigResponse(const ::android::sp<IRadioConfigResponse> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwRadioConfigResponse();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IRadioConfigResponse Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IRadioConfigResponse> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::radio::config::V1_0::IRadioConfigResponse follow.
    static ::android::status_t _hidl_getSimSlotsStatusResponse(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_setSimSlotsMappingResponse(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::radio::config::V1_0::IRadioConfigResponse follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IRadioConfigResponse> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace config
}  // namespace radio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_RADIO_CONFIG_V1_0_BNHWRADIOCONFIGRESPONSE_H
