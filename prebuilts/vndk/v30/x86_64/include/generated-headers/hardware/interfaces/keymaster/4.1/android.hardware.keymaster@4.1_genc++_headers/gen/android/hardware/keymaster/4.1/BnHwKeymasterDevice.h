#ifndef HIDL_GENERATED_ANDROID_HARDWARE_KEYMASTER_V4_1_BNHWKEYMASTERDEVICE_H
#define HIDL_GENERATED_ANDROID_HARDWARE_KEYMASTER_V4_1_BNHWKEYMASTERDEVICE_H

#include <android/hardware/keymaster/4.1/IHwKeymasterDevice.h>

namespace android {
namespace hardware {
namespace keymaster {
namespace V4_1 {

struct BnHwKeymasterDevice : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwKeymasterDevice(const ::android::sp<IKeymasterDevice> &_hidl_impl);
    explicit BnHwKeymasterDevice(const ::android::sp<IKeymasterDevice> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwKeymasterDevice();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IKeymasterDevice Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IKeymasterDevice> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::keymaster::V4_1::IKeymasterDevice follow.
    static ::android::status_t _hidl_deviceLocked(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_earlyBootEnded(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::keymaster::V4_0::IKeymasterDevice follow.

    // Methods from ::android::hardware::keymaster::V4_1::IKeymasterDevice follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IKeymasterDevice> _hidl_mImpl;
};

}  // namespace V4_1
}  // namespace keymaster
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_KEYMASTER_V4_1_BNHWKEYMASTERDEVICE_H
