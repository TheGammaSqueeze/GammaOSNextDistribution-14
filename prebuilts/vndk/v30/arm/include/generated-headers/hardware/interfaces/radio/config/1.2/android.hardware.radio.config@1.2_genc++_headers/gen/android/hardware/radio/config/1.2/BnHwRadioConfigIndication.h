#ifndef HIDL_GENERATED_ANDROID_HARDWARE_RADIO_CONFIG_V1_2_BNHWRADIOCONFIGINDICATION_H
#define HIDL_GENERATED_ANDROID_HARDWARE_RADIO_CONFIG_V1_2_BNHWRADIOCONFIGINDICATION_H

#include <android/hardware/radio/config/1.2/IHwRadioConfigIndication.h>

namespace android {
namespace hardware {
namespace radio {
namespace config {
namespace V1_2 {

struct BnHwRadioConfigIndication : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwRadioConfigIndication(const ::android::sp<IRadioConfigIndication> &_hidl_impl);
    explicit BnHwRadioConfigIndication(const ::android::sp<IRadioConfigIndication> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwRadioConfigIndication();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IRadioConfigIndication Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IRadioConfigIndication> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::radio::config::V1_2::IRadioConfigIndication follow.
    static ::android::status_t _hidl_simSlotsStatusChanged_1_2(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::radio::config::V1_0::IRadioConfigIndication follow.

    // Methods from ::android::hardware::radio::config::V1_2::IRadioConfigIndication follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IRadioConfigIndication> _hidl_mImpl;
};

}  // namespace V1_2
}  // namespace config
}  // namespace radio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_RADIO_CONFIG_V1_2_BNHWRADIOCONFIGINDICATION_H
