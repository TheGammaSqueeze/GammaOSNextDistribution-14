#ifndef HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V2_1_BPHWSENSORSCALLBACK_H
#define HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V2_1_BPHWSENSORSCALLBACK_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/sensors/2.1/IHwSensorsCallback.h>

namespace android {
namespace hardware {
namespace sensors {
namespace V2_1 {

struct BpHwSensorsCallback : public ::android::hardware::BpInterface<ISensorsCallback>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwSensorsCallback(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ISensorsCallback Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::sensors::V2_1::ISensorsCallback follow.
    static ::android::hardware::Return<void>  _hidl_onDynamicSensorsConnected_2_1(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::hidl_vec<::android::hardware::sensors::V2_1::SensorInfo>& sensorInfos);

    // Methods from ::android::hardware::sensors::V2_0::ISensorsCallback follow.
    ::android::hardware::Return<void> onDynamicSensorsConnected(const ::android::hardware::hidl_vec<::android::hardware::sensors::V1_0::SensorInfo>& sensorInfos) override;
    ::android::hardware::Return<void> onDynamicSensorsDisconnected(const ::android::hardware::hidl_vec<int32_t>& sensorHandles) override;

    // Methods from ::android::hardware::sensors::V2_1::ISensorsCallback follow.
    ::android::hardware::Return<void> onDynamicSensorsConnected_2_1(const ::android::hardware::hidl_vec<::android::hardware::sensors::V2_1::SensorInfo>& sensorInfos) override;

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

}  // namespace V2_1
}  // namespace sensors
}  // namespace hardware
}  // namespace android

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V2_1_BPHWSENSORSCALLBACK_H
