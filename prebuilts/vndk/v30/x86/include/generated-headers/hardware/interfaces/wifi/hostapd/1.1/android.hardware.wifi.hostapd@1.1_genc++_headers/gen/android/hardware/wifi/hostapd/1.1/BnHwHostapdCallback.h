#ifndef HIDL_GENERATED_ANDROID_HARDWARE_WIFI_HOSTAPD_V1_1_BNHWHOSTAPDCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_WIFI_HOSTAPD_V1_1_BNHWHOSTAPDCALLBACK_H

#include <android/hardware/wifi/hostapd/1.1/IHwHostapdCallback.h>

namespace android {
namespace hardware {
namespace wifi {
namespace hostapd {
namespace V1_1 {

struct BnHwHostapdCallback : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwHostapdCallback(const ::android::sp<IHostapdCallback> &_hidl_impl);
    explicit BnHwHostapdCallback(const ::android::sp<IHostapdCallback> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwHostapdCallback();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IHostapdCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IHostapdCallback> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::wifi::hostapd::V1_1::IHostapdCallback follow.
    static ::android::status_t _hidl_onFailure(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::wifi::hostapd::V1_1::IHostapdCallback follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IHostapdCallback> _hidl_mImpl;
};

}  // namespace V1_1
}  // namespace hostapd
}  // namespace wifi
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_WIFI_HOSTAPD_V1_1_BNHWHOSTAPDCALLBACK_H
