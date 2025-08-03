#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BLUETOOTH_A2DP_V1_0_BNHWBLUETOOTHAUDIOOFFLOAD_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BLUETOOTH_A2DP_V1_0_BNHWBLUETOOTHAUDIOOFFLOAD_H

#include <android/hardware/bluetooth/a2dp/1.0/IHwBluetoothAudioOffload.h>

namespace android {
namespace hardware {
namespace bluetooth {
namespace a2dp {
namespace V1_0 {

struct BnHwBluetoothAudioOffload : public ::android::hidl::base::V1_0::BnHwBase {
    explicit BnHwBluetoothAudioOffload(const ::android::sp<IBluetoothAudioOffload> &_hidl_impl);
    explicit BnHwBluetoothAudioOffload(const ::android::sp<IBluetoothAudioOffload> &_hidl_impl, const std::string& HidlInstrumentor_package, const std::string& HidlInstrumentor_interface);

    virtual ~BnHwBluetoothAudioOffload();

    ::android::status_t onTransact(
            uint32_t _hidl_code,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            uint32_t _hidl_flags = 0,
            TransactCallback _hidl_cb = nullptr) override;


    /**
     * The pure class is what this class wraps.
     */
    typedef IBluetoothAudioOffload Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'native' class.
     */
    typedef ::android::hardware::details::bnhw_tag _hidl_tag;

    ::android::sp<IBluetoothAudioOffload> getImpl() { return _hidl_mImpl; }
    // Methods from ::android::hardware::bluetooth::a2dp::V1_0::IBluetoothAudioOffload follow.
    static ::android::status_t _hidl_startSession(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_streamStarted(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_streamSuspended(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);


    static ::android::status_t _hidl_endSession(
            ::android::hidl::base::V1_0::BnHwBase* _hidl_this,
            const ::android::hardware::Parcel &_hidl_data,
            ::android::hardware::Parcel *_hidl_reply,
            TransactCallback _hidl_cb);



private:
    // Methods from ::android::hardware::bluetooth::a2dp::V1_0::IBluetoothAudioOffload follow.

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> ping();
    using getDebugInfo_cb = ::android::hidl::base::V1_0::IBase::getDebugInfo_cb;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb);

    ::android::sp<IBluetoothAudioOffload> _hidl_mImpl;
};

}  // namespace V1_0
}  // namespace a2dp
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BLUETOOTH_A2DP_V1_0_BNHWBLUETOOTHAUDIOOFFLOAD_H
