#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/modem/HardwareConfigModem.h>
#include <aidl/android/hardware/radio/modem/HardwareConfigSim.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace modem {
class HardwareConfig {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t type = 0;
  std::string uuid;
  int32_t state = 0;
  std::vector<::aidl::android::hardware::radio::modem::HardwareConfigModem> modem;
  std::vector<::aidl::android::hardware::radio::modem::HardwareConfigSim> sim;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const HardwareConfig& rhs) const {
    return std::tie(type, uuid, state, modem, sim) != std::tie(rhs.type, rhs.uuid, rhs.state, rhs.modem, rhs.sim);
  }
  inline bool operator<(const HardwareConfig& rhs) const {
    return std::tie(type, uuid, state, modem, sim) < std::tie(rhs.type, rhs.uuid, rhs.state, rhs.modem, rhs.sim);
  }
  inline bool operator<=(const HardwareConfig& rhs) const {
    return std::tie(type, uuid, state, modem, sim) <= std::tie(rhs.type, rhs.uuid, rhs.state, rhs.modem, rhs.sim);
  }
  inline bool operator==(const HardwareConfig& rhs) const {
    return std::tie(type, uuid, state, modem, sim) == std::tie(rhs.type, rhs.uuid, rhs.state, rhs.modem, rhs.sim);
  }
  inline bool operator>(const HardwareConfig& rhs) const {
    return std::tie(type, uuid, state, modem, sim) > std::tie(rhs.type, rhs.uuid, rhs.state, rhs.modem, rhs.sim);
  }
  inline bool operator>=(const HardwareConfig& rhs) const {
    return std::tie(type, uuid, state, modem, sim) >= std::tie(rhs.type, rhs.uuid, rhs.state, rhs.modem, rhs.sim);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { STATE_ENABLED = 0 };
  enum : int32_t { STATE_STANDBY = 1 };
  enum : int32_t { STATE_DISABLED = 2 };
  enum : int32_t { TYPE_MODEM = 0 };
  enum : int32_t { TYPE_SIM = 1 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "HardwareConfig{";
    os << "type: " << ::android::internal::ToString(type);
    os << ", uuid: " << ::android::internal::ToString(uuid);
    os << ", state: " << ::android::internal::ToString(state);
    os << ", modem: " << ::android::internal::ToString(modem);
    os << ", sim: " << ::android::internal::ToString(sim);
    os << "}";
    return os.str();
  }
};
}  // namespace modem
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
