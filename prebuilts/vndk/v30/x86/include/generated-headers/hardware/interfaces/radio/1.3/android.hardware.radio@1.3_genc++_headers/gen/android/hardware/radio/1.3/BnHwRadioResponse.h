#ifndef HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_3_BNHWRADIORESPONSE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_3_BNHWRADIORESPONSE_H

#include <android/hardware/radio/1.3/IHwRadioResponse.h>

namespace android {
namespace hardware {
namespace radio {
namespace V1_3 {

struct BnHwRadioResponse : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwRadioResponse(const ::android::sp<IRadioResponse> &_hidl_impl);
    explicit BnHwRadioResponse(const ::android::sp<IRadioResponse> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwRadioResponse();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IRadioResponse Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IRadioResponse> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::radio::V1_3::IRadioResponse follow.
    static ::android::status_t _hidl_setSystemSelectionChannelsResponse(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_enableModemResponse(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_getModemStackStatusResponse(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::radio::V1_0::IRadioResponse follow.

    // Methods from ::android::hardware::radio::V1_1::IRadioResponse follow.

    // Methods from ::android::hardware::radio::V1_2::IRadioResponse follow.

    // Methods from ::android::hardware::radio::V1_3::IRadioResponse follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IRadioResponse> _hidl_mImpl;
};

}  // namespace V1_3
}  // namespace radio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_RADIO_V1_3_BNHWRADIORESPONSE_H
