#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/sensors/SensorType.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace sensors {
class SensorInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t sensorHandle = 0;
  std::string name;
  std::string vendor;
  int32_t version = 0;
  ::aidl::android::hardware::sensors::SensorType type = ::aidl::android::hardware::sensors::SensorType(0);
  std::string typeAsString;
  float maxRange = 0.000000f;
  float resolution = 0.000000f;
  float power = 0.000000f;
  int32_t minDelayUs = 0;
  int32_t fifoReservedEventCount = 0;
  int32_t fifoMaxEventCount = 0;
  std::string requiredPermission;
  int32_t maxDelayUs = 0;
  int32_t flags = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SensorInfo& rhs) const {
    return std::tie(sensorHandle, name, vendor, version, type, typeAsString, maxRange, resolution, power, minDelayUs, fifoReservedEventCount, fifoMaxEventCount, requiredPermission, maxDelayUs, flags) != std::tie(rhs.sensorHandle, rhs.name, rhs.vendor, rhs.version, rhs.type, rhs.typeAsString, rhs.maxRange, rhs.resolution, rhs.power, rhs.minDelayUs, rhs.fifoReservedEventCount, rhs.fifoMaxEventCount, rhs.requiredPermission, rhs.maxDelayUs, rhs.flags);
  }
  inline bool operator<(const SensorInfo& rhs) const {
    return std::tie(sensorHandle, name, vendor, version, type, typeAsString, maxRange, resolution, power, minDelayUs, fifoReservedEventCount, fifoMaxEventCount, requiredPermission, maxDelayUs, flags) < std::tie(rhs.sensorHandle, rhs.name, rhs.vendor, rhs.version, rhs.type, rhs.typeAsString, rhs.maxRange, rhs.resolution, rhs.power, rhs.minDelayUs, rhs.fifoReservedEventCount, rhs.fifoMaxEventCount, rhs.requiredPermission, rhs.maxDelayUs, rhs.flags);
  }
  inline bool operator<=(const SensorInfo& rhs) const {
    return std::tie(sensorHandle, name, vendor, version, type, typeAsString, maxRange, resolution, power, minDelayUs, fifoReservedEventCount, fifoMaxEventCount, requiredPermission, maxDelayUs, flags) <= std::tie(rhs.sensorHandle, rhs.name, rhs.vendor, rhs.version, rhs.type, rhs.typeAsString, rhs.maxRange, rhs.resolution, rhs.power, rhs.minDelayUs, rhs.fifoReservedEventCount, rhs.fifoMaxEventCount, rhs.requiredPermission, rhs.maxDelayUs, rhs.flags);
  }
  inline bool operator==(const SensorInfo& rhs) const {
    return std::tie(sensorHandle, name, vendor, version, type, typeAsString, maxRange, resolution, power, minDelayUs, fifoReservedEventCount, fifoMaxEventCount, requiredPermission, maxDelayUs, flags) == std::tie(rhs.sensorHandle, rhs.name, rhs.vendor, rhs.version, rhs.type, rhs.typeAsString, rhs.maxRange, rhs.resolution, rhs.power, rhs.minDelayUs, rhs.fifoReservedEventCount, rhs.fifoMaxEventCount, rhs.requiredPermission, rhs.maxDelayUs, rhs.flags);
  }
  inline bool operator>(const SensorInfo& rhs) const {
    return std::tie(sensorHandle, name, vendor, version, type, typeAsString, maxRange, resolution, power, minDelayUs, fifoReservedEventCount, fifoMaxEventCount, requiredPermission, maxDelayUs, flags) > std::tie(rhs.sensorHandle, rhs.name, rhs.vendor, rhs.version, rhs.type, rhs.typeAsString, rhs.maxRange, rhs.resolution, rhs.power, rhs.minDelayUs, rhs.fifoReservedEventCount, rhs.fifoMaxEventCount, rhs.requiredPermission, rhs.maxDelayUs, rhs.flags);
  }
  inline bool operator>=(const SensorInfo& rhs) const {
    return std::tie(sensorHandle, name, vendor, version, type, typeAsString, maxRange, resolution, power, minDelayUs, fifoReservedEventCount, fifoMaxEventCount, requiredPermission, maxDelayUs, flags) >= std::tie(rhs.sensorHandle, rhs.name, rhs.vendor, rhs.version, rhs.type, rhs.typeAsString, rhs.maxRange, rhs.resolution, rhs.power, rhs.minDelayUs, rhs.fifoReservedEventCount, rhs.fifoMaxEventCount, rhs.requiredPermission, rhs.maxDelayUs, rhs.flags);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { SENSOR_FLAG_BITS_WAKE_UP = 1 };
  enum : int32_t { SENSOR_FLAG_BITS_CONTINUOUS_MODE = 0 };
  enum : int32_t { SENSOR_FLAG_BITS_ON_CHANGE_MODE = 2 };
  enum : int32_t { SENSOR_FLAG_BITS_ONE_SHOT_MODE = 4 };
  enum : int32_t { SENSOR_FLAG_BITS_SPECIAL_REPORTING_MODE = 6 };
  enum : int32_t { SENSOR_FLAG_BITS_DATA_INJECTION = 16 };
  enum : int32_t { SENSOR_FLAG_BITS_DYNAMIC_SENSOR = 32 };
  enum : int32_t { SENSOR_FLAG_BITS_ADDITIONAL_INFO = 64 };
  enum : int32_t { SENSOR_FLAG_BITS_DIRECT_CHANNEL_ASHMEM = 1024 };
  enum : int32_t { SENSOR_FLAG_BITS_DIRECT_CHANNEL_GRALLOC = 2048 };
  enum : int32_t { SENSOR_FLAG_BITS_MASK_REPORTING_MODE = 14 };
  enum : int32_t { SENSOR_FLAG_BITS_MASK_DIRECT_REPORT = 896 };
  enum : int32_t { SENSOR_FLAG_BITS_MASK_DIRECT_CHANNEL = 3072 };
  enum : int32_t { SENSOR_FLAG_SHIFT_REPORTING_MODE = 1 };
  enum : int32_t { SENSOR_FLAG_SHIFT_DATA_INJECTION = 4 };
  enum : int32_t { SENSOR_FLAG_SHIFT_DYNAMIC_SENSOR = 5 };
  enum : int32_t { SENSOR_FLAG_SHIFT_ADDITIONAL_INFO = 6 };
  enum : int32_t { SENSOR_FLAG_SHIFT_DIRECT_REPORT = 7 };
  enum : int32_t { SENSOR_FLAG_SHIFT_DIRECT_CHANNEL = 10 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "SensorInfo{";
    os << "sensorHandle: " << ::android::internal::ToString(sensorHandle);
    os << ", name: " << ::android::internal::ToString(name);
    os << ", vendor: " << ::android::internal::ToString(vendor);
    os << ", version: " << ::android::internal::ToString(version);
    os << ", type: " << ::android::internal::ToString(type);
    os << ", typeAsString: " << ::android::internal::ToString(typeAsString);
    os << ", maxRange: " << ::android::internal::ToString(maxRange);
    os << ", resolution: " << ::android::internal::ToString(resolution);
    os << ", power: " << ::android::internal::ToString(power);
    os << ", minDelayUs: " << ::android::internal::ToString(minDelayUs);
    os << ", fifoReservedEventCount: " << ::android::internal::ToString(fifoReservedEventCount);
    os << ", fifoMaxEventCount: " << ::android::internal::ToString(fifoMaxEventCount);
    os << ", requiredPermission: " << ::android::internal::ToString(requiredPermission);
    os << ", maxDelayUs: " << ::android::internal::ToString(maxDelayUs);
    os << ", flags: " << ::android::internal::ToString(flags);
    os << "}";
    return os.str();
  }
};
}  // namespace sensors
}  // namespace hardware
}  // namespace android
}  // namespace aidl
