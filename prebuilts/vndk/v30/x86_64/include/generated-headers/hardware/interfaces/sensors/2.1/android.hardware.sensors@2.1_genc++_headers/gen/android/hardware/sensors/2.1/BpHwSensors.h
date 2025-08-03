#ifndef HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V2_1_BPHWSENSORS_H
#define HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V2_1_BPHWSENSORS_H

#include <hidl/HidlTransportSupport.h>

#include <android/hardware/sensors/2.1/IHwSensors.h>

namespace android {
namespace hardware {
namespace sensors {
namespace V2_1 {

struct BpHwSensors : public ::android::hardware::BpInterface<ISensors>, public ::android::hardware::details::HidlInstrumentor {
    explicit BpHwSensors(const ::android::sp<::android::hardware::IBinder> &_hidl_impl);

    /**
     * The pure class is what this class wraps.
     */
    typedef ISensors Pure;

    /**
     * Type tag for use in template logic that indicates this is a 'proxy' class.
     */
    typedef ::android::hardware::details::bphw_tag _hidl_tag;

    virtual bool isRemote() const override { return true; }

    void onLastStrongRef(const void* id) override;

    // Methods from ::android::hardware::sensors::V2_1::ISensors follow.
    static ::android::hardware::Return<void>  _hidl_getSensorsList_2_1(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, getSensorsList_2_1_cb _hidl_cb);
    static ::android::hardware::Return<::android::hardware::sensors::V1_0::Result>  _hidl_initialize_2_1(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::MQDescriptorSync<::android::hardware::sensors::V2_1::Event>& eventQueueDescriptor, const ::android::hardware::MQDescriptorSync<uint32_t>& wakeLockDescriptor, const ::android::sp<::android::hardware::sensors::V2_1::ISensorsCallback>& sensorsCallback);
    static ::android::hardware::Return<::android::hardware::sensors::V1_0::Result>  _hidl_injectSensorData_2_1(::android::hardware::IInterface* _hidl_this, ::android::hardware::details::HidlInstrumentor *_hidl_this_instrumentor, const ::android::hardware::sensors::V2_1::Event& event);

    // Methods from ::android::hardware::sensors::V2_0::ISensors follow.
    ::android::hardware::Return<void> getSensorsList(getSensorsList_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::sensors::V1_0::Result> setOperationMode(::android::hardware::sensors::V1_0::OperationMode mode) override;
    ::android::hardware::Return<::android::hardware::sensors::V1_0::Result> activate(int32_t sensorHandle, bool enabled) override;
    ::android::hardware::Return<::android::hardware::sensors::V1_0::Result> initialize(const ::android::hardware::MQDescriptorSync<::android::hardware::sensors::V1_0::Event>& eventQueueDescriptor, const ::android::hardware::MQDescriptorSync<uint32_t>& wakeLockDescriptor, const ::android::sp<::android::hardware::sensors::V2_0::ISensorsCallback>& sensorsCallback) override;
    ::android::hardware::Return<::android::hardware::sensors::V1_0::Result> batch(int32_t sensorHandle, int64_t samplingPeriodNs, int64_t maxReportLatencyNs) override;
    ::android::hardware::Return<::android::hardware::sensors::V1_0::Result> flush(int32_t sensorHandle) override;
    ::android::hardware::Return<::android::hardware::sensors::V1_0::Result> injectSensorData(const ::android::hardware::sensors::V1_0::Event& event) override;
    ::android::hardware::Return<void> registerDirectChannel(const ::android::hardware::sensors::V1_0::SharedMemInfo& mem, registerDirectChannel_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::sensors::V1_0::Result> unregisterDirectChannel(int32_t channelHandle) override;
    ::android::hardware::Return<void> configDirectReport(int32_t sensorHandle, int32_t channelHandle, ::android::hardware::sensors::V1_0::RateLevel rate, configDirectReport_cb _hidl_cb) override;

    // Methods from ::android::hardware::sensors::V2_1::ISensors follow.
    ::android::hardware::Return<void> getSensorsList_2_1(getSensorsList_2_1_cb _hidl_cb) override;
    ::android::hardware::Return<::android::hardware::sensors::V1_0::Result> initialize_2_1(const ::android::hardware::MQDescriptorSync<::android::hardware::sensors::V2_1::Event>& eventQueueDescriptor, const ::android::hardware::MQDescriptorSync<uint32_t>& wakeLockDescriptor, const ::android::sp<::android::hardware::sensors::V2_1::ISensorsCallback>& sensorsCallback) override;
    ::android::hardware::Return<::android::hardware::sensors::V1_0::Result> injectSensorData_2_1(const ::android::hardware::sensors::V2_1::Event& event) override;

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

#endif  // HIDL_GENERATED_ANDROID_HARDWARE_SENSORS_V2_1_BPHWSENSORS_H
