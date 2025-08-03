#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BLUETOOTH_V1_1_BPHWBLUETOOTHHCICALLBACKS_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BLUETOOTH_V1_1_BPHWBLUETOOTHHCICALLBACKS_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/bluetooth/1.1/IHwBluetoothHciCallbacks.h>

namespace android {
namespace hardware {
namespace bluetooth {
namespace V1_1 {

struct BpHwBluetoothHciCallbacks : public ::android::hardware::BpInterface<IBluetoothHciCallbacks>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwBluetoothHciCallbacks(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IBluetoothHciCallbacks Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::bluetooth::V1_1::IBluetoothHciCallbacks follow.
    static ::android::hardware::Return<void>  _hidl_isoDataReceived(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<uint8_t>& data);

    // Methods from ::android::hardware::bluetooth::V1_0::IBluetoothHciCallbacks follow.
    ::android::hardware::Return<void> initializationComplete(::android::hardware::bluetooth::V1_0::Status status) override;
    ::android::hardware::Return<void> hciEventReceived(const ::android::hardware::hidl_vec<uint8_t>& event) override;
    ::android::hardware::Return<void> aclDataReceived(const ::android::hardware::hidl_vec<uint8_t>& data) override;
    ::android::hardware::Return<void> scoDataReceived(const ::android::hardware::hidl_vec<uint8_t>& data) override;

    // Methods from ::android::hardware::bluetooth::V1_1::IBluetoothHciCallbacks follow.
    ::android::hardware::Return<void> isoDataReceived(const ::android::hardware::hidl_vec<uint8_t>& data) override;

    // Methods from ::android::hidl::base::V1_0::IBase follow.
    ::android::hardware::Return<void> interfaceChain(interfaceChain_cb _hidl_cb) override;
    ::android::hardware::Return<void> debug(const ::android::hardware::hidl_handle& fd, const ::android::hardware::hidl_vec<::android::hardware::hidl_string>& options) override;
    ::android::hardware::Return<void> interfaceDescriptor(interfaceDescriptor_cb _hidl_cb) override;
    ::android::hardware::Return<void> getHashChain(getHashChain_cb _hidl_cb) override;
    ::android::hardware::Return<void> setHALInstrumentation() override;
    ::android::hardware::Return<bool> linkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient, uint64_t cookie) override;
    ::android::hardware::Return<void> ping() override;
    ::android::hardware::Return<void> getDebugInfo(getDebugInfo_cb _hidl_cb) override;
    ::android::hardware::Return<void> notifySyspropsChanged() override;
    ::android::hardware::Return<bool> unlinkToDeath(const ::android::sp<::android::hardware::hidl_death_recipient>& recipient) override;

private:
    std::mutex _hidl_mMutex;
    std::vector<::android::sp<::android::hardware::hidl_binder_death_recipient>> _hidl_mDeathRecipients;
};

}  // namespace V1_1
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BLUETOOTH_V1_1_BPHWBLUETOOTHHCICALLBACKS_H
