#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V1_0_BNHWBROADCASTRADIOFACTORY_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V1_0_BNHWBROADCASTRADIOFACTORY_H

#include <android/hardware/broadcastradio/1.0/IHwBroadcastRadioFactory.h>

namespace android {
namespace hardware {
namespace broadcastradio {
namespace V1_0 {

struct BnHwBroadcastRadioFactory : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwBroadcastRadioFactory(const ::android::sp<IBroadcastRadioFactory> &_hidl_impl);
    explicit BnHwBroadcastRadioFactory(const ::android::sp<IBroadcastRadioFactory> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwBroadcastRadioFactory();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IBroadcastRadioFactory Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IBroadcastRadioFactory> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::broadcastradio::V1_0::IBroadcastRadioFactory follow.
    static ::android::status_t _hidl_connectModule(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::broadcastradio::V1_0::IBroadcastRadioFactory follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IBroadcastRadioFactory> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace broadcastradio
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BROADCASTRADIO_V1_0_BNHWBROADCASTRADIOFACTORY_H
