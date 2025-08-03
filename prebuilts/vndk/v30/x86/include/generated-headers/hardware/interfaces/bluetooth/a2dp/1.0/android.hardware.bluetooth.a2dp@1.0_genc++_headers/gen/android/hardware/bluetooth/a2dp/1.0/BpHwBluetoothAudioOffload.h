#ifndef HIDL_GENERATED_ANDROID_HARDWARE_BLUETOOTH_A2DP_V1_0_BPHWBLUETOOTHAUDIOOFFLOAD_H
#define HIDL_GENERATED_ANDROID_HARDWARE_BLUETOOTH_A2DP_V1_0_BPHWBLUETOOTHAUDIOOFFLOAD_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/bluetooth/a2dp/1.0/IHwBluetoothAudioOffload.h>

namespace android {
namespace hardware {
namespace bluetooth {
namespace a2dp {
namespace V1_0 {

struct BpHwBluetoothAudioOffload : public ::android::hardware::BpInterface<IBluetoothAudioOffload>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwBluetoothAudioOffload(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef IBluetoothAudioOffload Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::bluetooth::a2dp::V1_0::IBluetoothAudioOffload follow.
    static ::android::hardware::Return<::android::hardware::bluetooth::a2dp::V1_0::Status>  _hidl_startSession(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::sp<::android::hardware::bluetooth::a2dp::V1_0::IBluetoothAudioHost>& hostIf, const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration& codecConfig);
    static ::android::hardware::Return<void>  _hidl_streamStarted(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::bluetooth::a2dp::V1_0::Status status);
    static ::android::hardware::Return<void>  _hidl_streamSuspended(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, ::android::hardware::bluetooth::a2dp::V1_0::Status status);
    static ::android::hardware::Return<void>  _hidl_endSession(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor);

    // Methods from ::android::hardware::bluetooth::a2dp::V1_0::IBluetoothAudioOffload follow.
    ::android::hardware::Return<::android::hardware::bluetooth::a2dp::V1_0::Status> startSession(const ::android::sp<::android::hardware::bluetooth::a2dp::V1_0::IBluetoothAudioHost>& hostIf, const ::android::hardware::bluetooth::a2dp::V1_0::CodecConfiguration& codecConfig) override;
    ::android::hardware::Return<void> streamStarted(::android::hardware::bluetooth::a2dp::V1_0::Status status) override;
    ::android::hardware::Return<void> streamSuspended(::android::hardware::bluetooth::a2dp::V1_0::Status status) override;
    ::android::hardware::Return<void> endSession() override;

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

}  // namespace V1_0
}  // namespace a2dp
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_BLUETOOTH_A2DP_V1_0_BPHWBLUETOOTHAUDIOOFFLOAD_H
