#pragma once

#include "aidl/android/hardware/sensors/ISensorsCallback.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace sensors {
class BpSensorsCallback : public ::ndk::BpCInterface<ISensorsCallback> {
public:
  explicit BpSensorsCallback(const ::ndk::SpAIBinder& binder);
  virtual ~BpSensorsCallback();

  ::ndk::ScopedAStatus onDynamicSensorsConnected(const std::vector<::aidl::android::hardware::sensors::SensorInfo>& in_sensorInfos) override;
  ::ndk::ScopedAStatus onDynamicSensorsDisconnected(const std::vector<int32_t>& in_sensorHandles) override;
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
