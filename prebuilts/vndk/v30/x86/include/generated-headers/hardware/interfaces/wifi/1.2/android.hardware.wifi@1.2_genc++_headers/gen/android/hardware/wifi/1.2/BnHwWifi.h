#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_2_BNHWWIFI_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_2_BNHWWIFI_H

#include <android/hardware/wifi/1.2/IHwWifi.h>

namespace android {
namespace hardware {
namespace wifi {
namespace V1_2 {

struct BnHwWifi : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwWifi(const ::android::sp<IWifi> &_hidl_impl);
    explicit BnHwWifi(const ::android::sp<IWifi> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwWifi();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IWifi Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IWifi> getImpl() { return _hidl_mImpl; }

private:
    // Methods from ::android::hardware::wifi::V1_0::IWifi follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IWifi> _hidl_mImpl;
};

}  // namespace V1_2
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_V1_2_BNHWWIFI_H
