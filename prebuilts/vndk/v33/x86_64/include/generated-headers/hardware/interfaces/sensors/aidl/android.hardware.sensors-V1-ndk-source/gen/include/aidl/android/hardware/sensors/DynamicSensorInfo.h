#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/sensors/DynamicSensorInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace sensors {
class DynamicSensorInfo {
public:
  typedef std::true_type fixed_size;
  static const char* descriptor;

  class Uuid {
  public:
    typedef std::true_type fixed_size;
    static const char* descriptor;

    std::array<uint8_t, 16> values __attribute__((aligned (1))) = {{}};

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const Uuid& rhs) const {
      return std::tie(values) != std::tie(rhs.values);
    }
    inline bool operator<(const Uuid& rhs) const {
      return std::tie(values) < std::tie(rhs.values);
    }
    inline bool operator<=(const Uuid& rhs) const {
      return std::tie(values) <= std::tie(rhs.values);
    }
    inline bool operator==(const Uuid& rhs) const {
      return std::tie(values) == std::tie(rhs.values);
    }
    inline bool operator>(const Uuid& rhs) const {
      return std::tie(values) > std::tie(rhs.values);
    }
    inline bool operator>=(const Uuid& rhs) const {
      return std::tie(values) >= std::tie(rhs.values);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "Uuid{";
      os << "values: " << ::android::internal::ToString(values);
      os << "}";
      return os.str();
    }
  };
  bool connected __attribute__((aligned (1))) = false;
  int32_t sensorHandle __attribute__((aligned (4))) = 0;
  ::aidl::android::hardware::sensors::DynamicSensorInfo::Uuid uuid;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const DynamicSensorInfo& rhs) const {
    return std::tie(connected, sensorHandle, uuid) != std::tie(rhs.connected, rhs.sensorHandle, rhs.uuid);
  }
  inline bool operator<(const DynamicSensorInfo& rhs) const {
    return std::tie(connected, sensorHandle, uuid) < std::tie(rhs.connected, rhs.sensorHandle, rhs.uuid);
  }
  inline bool operator<=(const DynamicSensorInfo& rhs) const {
    return std::tie(connected, sensorHandle, uuid) <= std::tie(rhs.connected, rhs.sensorHandle, rhs.uuid);
  }
  inline bool operator==(const DynamicSensorInfo& rhs) const {
    return std::tie(connected, sensorHandle, uuid) == std::tie(rhs.connected, rhs.sensorHandle, rhs.uuid);
  }
  inline bool operator>(const DynamicSensorInfo& rhs) const {
    return std::tie(connected, sensorHandle, uuid) > std::tie(rhs.connected, rhs.sensorHandle, rhs.uuid);
  }
  inline bool operator>=(const DynamicSensorInfo& rhs) const {
    return std::tie(connected, sensorHandle, uuid) >= std::tie(rhs.connected, rhs.sensorHandle, rhs.uuid);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "DynamicSensorInfo{";
    os << "connected: " << ::android::internal::ToString(connected);
    os << ", sensorHandle: " << ::android::internal::ToString(sensorHandle);
    os << ", uuid: " << ::android::internal::ToString(uuid);
    os << "}";
    return os.str();
  }
};
}  // namespace sensors
}  // namespace hardware
}  // namespace android
}  // namespace aidl
