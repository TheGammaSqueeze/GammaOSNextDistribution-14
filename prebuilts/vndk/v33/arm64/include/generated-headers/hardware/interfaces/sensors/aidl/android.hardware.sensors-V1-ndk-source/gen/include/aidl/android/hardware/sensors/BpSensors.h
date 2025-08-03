#pragma once

#include "aidl/android/hardware/sensors/ISensors.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace sensors {
class BpSensors : public ::ndk::BpCInterface<ISensors> {
public:
  explicit BpSensors(const ::ndk::SpAIBinder& binder);
  virtual ~BpSensors();

  ::ndk::ScopedAStatus activate(int32_t in_sensorHandle, bool in_enabled) override;
  ::ndk::ScopedAStatus batch(int32_t in_sensorHandle, int64_t in_samplingPeriodNs, int64_t in_maxReportLatencyNs) override;
  ::ndk::ScopedAStatus configDirectReport(int32_t in_sensorHandle, int32_t in_channelHandle, ::aidl::android::hardware::sensors::ISensors::RateLevel in_rate, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus flush(int32_t in_sensorHandle) override;
  ::ndk::ScopedAStatus getSensorsList(std::vector<::aidl::android::hardware::sensors::SensorInfo>* _aidl_return) override;
  ::ndk::ScopedAStatus initialize(const ::aidl::android::hardware::common::fmq::MQDescriptor<::aidl::android::hardware::sensors::Event, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>& in_eventQueueDescriptor, const ::aidl::android::hardware::common::fmq::MQDescriptor<int32_t, ::aidl::android::hardware::common::fmq::SynchronizedReadWrite>& in_wakeLockDescriptor, const std::shared_ptr<::aidl::android::hardware::sensors::ISensorsCallback>& in_sensorsCallback) override;
  ::ndk::ScopedAStatus injectSensorData(const ::aidl::android::hardware::sensors::Event& in_event) override;
  ::ndk::ScopedAStatus registerDirectChannel(const ::aidl::android::hardware::sensors::ISensors::SharedMemInfo& in_mem, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus setOperationMode(::aidl::android::hardware::sensors::ISensors::OperationMode in_mode) override;
  ::ndk::ScopedAStatus unregisterDirectChannel(int32_t in_channelHandle) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace sensors
}  // namespace hardware
}  // namespace android
}  // namespace aidl
