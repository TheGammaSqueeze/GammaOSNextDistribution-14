#pragma once

#include <array>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_enums.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace sim {
enum class PersoSubstate : int32_t {
  UNKNOWN = 0,
  IN_PROGRESS = 1,
  READY = 2,
  SIM_NETWORK = 3,
  SIM_NETWORK_SUBSET = 4,
  SIM_CORPORATE = 5,
  SIM_SERVICE_PROVIDER = 6,
  SIM_SIM = 7,
  SIM_NETWORK_PUK = 8,
  SIM_NETWORK_SUBSET_PUK = 9,
  SIM_CORPORATE_PUK = 10,
  SIM_SERVICE_PROVIDER_PUK = 11,
  SIM_SIM_PUK = 12,
  RUIM_NETWORK1 = 13,
  RUIM_NETWORK2 = 14,
  RUIM_HRPD = 15,
  RUIM_CORPORATE = 16,
  RUIM_SERVICE_PROVIDER = 17,
  RUIM_RUIM = 18,
  RUIM_NETWORK1_PUK = 19,
  RUIM_NETWORK2_PUK = 20,
  RUIM_HRPD_PUK = 21,
  RUIM_CORPORATE_PUK = 22,
  RUIM_SERVICE_PROVIDER_PUK = 23,
  RUIM_RUIM_PUK = 24,
  SIM_SPN = 25,
  SIM_SPN_PUK = 26,
  SIM_SP_EHPLMN = 27,
  SIM_SP_EHPLMN_PUK = 28,
  SIM_ICCID = 29,
  SIM_ICCID_PUK = 30,
  SIM_IMPI = 31,
  SIM_IMPI_PUK = 32,
  SIM_NS_SP = 33,
  SIM_NS_SP_PUK = 34,
};

}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace sim {
[[nodiscard]] static inline std::string toString(PersoSubstate val) {
  switch(val) {
  case PersoSubstate::UNKNOWN:
    return "UNKNOWN";
  case PersoSubstate::IN_PROGRESS:
    return "IN_PROGRESS";
  case PersoSubstate::READY:
    return "READY";
  case PersoSubstate::SIM_NETWORK:
    return "SIM_NETWORK";
  case PersoSubstate::SIM_NETWORK_SUBSET:
    return "SIM_NETWORK_SUBSET";
  case PersoSubstate::SIM_CORPORATE:
    return "SIM_CORPORATE";
  case PersoSubstate::SIM_SERVICE_PROVIDER:
    return "SIM_SERVICE_PROVIDER";
  case PersoSubstate::SIM_SIM:
    return "SIM_SIM";
  case PersoSubstate::SIM_NETWORK_PUK:
    return "SIM_NETWORK_PUK";
  case PersoSubstate::SIM_NETWORK_SUBSET_PUK:
    return "SIM_NETWORK_SUBSET_PUK";
  case PersoSubstate::SIM_CORPORATE_PUK:
    return "SIM_CORPORATE_PUK";
  case PersoSubstate::SIM_SERVICE_PROVIDER_PUK:
    return "SIM_SERVICE_PROVIDER_PUK";
  case PersoSubstate::SIM_SIM_PUK:
    return "SIM_SIM_PUK";
  case PersoSubstate::RUIM_NETWORK1:
    return "RUIM_NETWORK1";
  case PersoSubstate::RUIM_NETWORK2:
    return "RUIM_NETWORK2";
  case PersoSubstate::RUIM_HRPD:
    return "RUIM_HRPD";
  case PersoSubstate::RUIM_CORPORATE:
    return "RUIM_CORPORATE";
  case PersoSubstate::RUIM_SERVICE_PROVIDER:
    return "RUIM_SERVICE_PROVIDER";
  case PersoSubstate::RUIM_RUIM:
    return "RUIM_RUIM";
  case PersoSubstate::RUIM_NETWORK1_PUK:
    return "RUIM_NETWORK1_PUK";
  case PersoSubstate::RUIM_NETWORK2_PUK:
    return "RUIM_NETWORK2_PUK";
  case PersoSubstate::RUIM_HRPD_PUK:
    return "RUIM_HRPD_PUK";
  case PersoSubstate::RUIM_CORPORATE_PUK:
    return "RUIM_CORPORATE_PUK";
  case PersoSubstate::RUIM_SERVICE_PROVIDER_PUK:
    return "RUIM_SERVICE_PROVIDER_PUK";
  case PersoSubstate::RUIM_RUIM_PUK:
    return "RUIM_RUIM_PUK";
  case PersoSubstate::SIM_SPN:
    return "SIM_SPN";
  case PersoSubstate::SIM_SPN_PUK:
    return "SIM_SPN_PUK";
  case PersoSubstate::SIM_SP_EHPLMN:
    return "SIM_SP_EHPLMN";
  case PersoSubstate::SIM_SP_EHPLMN_PUK:
    return "SIM_SP_EHPLMN_PUK";
  case PersoSubstate::SIM_ICCID:
    return "SIM_ICCID";
  case PersoSubstate::SIM_ICCID_PUK:
    return "SIM_ICCID_PUK";
  case PersoSubstate::SIM_IMPI:
    return "SIM_IMPI";
  case PersoSubstate::SIM_IMPI_PUK:
    return "SIM_IMPI_PUK";
  case PersoSubstate::SIM_NS_SP:
    return "SIM_NS_SP";
  case PersoSubstate::SIM_NS_SP_PUK:
    return "SIM_NS_SP_PUK";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::sim::PersoSubstate, 35> enum_values<aidl::android::hardware::radio::sim::PersoSubstate> = {
  aidl::android::hardware::radio::sim::PersoSubstate::UNKNOWN,
  aidl::android::hardware::radio::sim::PersoSubstate::IN_PROGRESS,
  aidl::android::hardware::radio::sim::PersoSubstate::READY,
  aidl::android::hardware::radio::sim::PersoSubstate::SIM_NETWORK,
  aidl::android::hardware::radio::sim::PersoSubstate::SIM_NETWORK_SUBSET,
  aidl::android::hardware::radio::sim::PersoSubstate::SIM_CORPORATE,
  aidl::android::hardware::radio::sim::PersoSubstate::SIM_SERVICE_PROVIDER,
  aidl::android::hardware::radio::sim::PersoSubstate::SIM_SIM,
  aidl::android::hardware::radio::sim::PersoSubstate::SIM_NETWORK_PUK,
  aidl::android::hardware::radio::sim::PersoSubstate::SIM_NETWORK_SUBSET_PUK,
  aidl::android::hardware::radio::sim::PersoSubstate::SIM_CORPORATE_PUK,
  aidl::android::hardware::radio::sim::PersoSubstate::SIM_SERVICE_PROVIDER_PUK,
  aidl::android::hardware::radio::sim::PersoSubstate::SIM_SIM_PUK,
  aidl::android::hardware::radio::sim::PersoSubstate::RUIM_NETWORK1,
  aidl::android::hardware::radio::sim::PersoSubstate::RUIM_NETWORK2,
  aidl::android::hardware::radio::sim::PersoSubstate::RUIM_HRPD,
  aidl::android::hardware::radio::sim::PersoSubstate::RUIM_CORPORATE,
  aidl::android::hardware::radio::sim::PersoSubstate::RUIM_SERVICE_PROVIDER,
  aidl::android::hardware::radio::sim::PersoSubstate::RUIM_RUIM,
  aidl::android::hardware::radio::sim::PersoSubstate::RUIM_NETWORK1_PUK,
  aidl::android::hardware::radio::sim::PersoSubstate::RUIM_NETWORK2_PUK,
  aidl::android::hardware::radio::sim::PersoSubstate::RUIM_HRPD_PUK,
  aidl::android::hardware::radio::sim::PersoSubstate::RUIM_CORPORATE_PUK,
  aidl::android::hardware::radio::sim::PersoSubstate::RUIM_SERVICE_PROVIDER_PUK,
  aidl::android::hardware::radio::sim::PersoSubstate::RUIM_RUIM_PUK,
  aidl::android::hardware::radio::sim::PersoSubstate::SIM_SPN,
  aidl::android::hardware::radio::sim::PersoSubstate::SIM_SPN_PUK,
  aidl::android::hardware::radio::sim::PersoSubstate::SIM_SP_EHPLMN,
  aidl::android::hardware::radio::sim::PersoSubstate::SIM_SP_EHPLMN_PUK,
  aidl::android::hardware::radio::sim::PersoSubstate::SIM_ICCID,
  aidl::android::hardware::radio::sim::PersoSubstate::SIM_ICCID_PUK,
  aidl::android::hardware::radio::sim::PersoSubstate::SIM_IMPI,
  aidl::android::hardware::radio::sim::PersoSubstate::SIM_IMPI_PUK,
  aidl::android::hardware::radio::sim::PersoSubstate::SIM_NS_SP,
  aidl::android::hardware::radio::sim::PersoSubstate::SIM_NS_SP_PUK,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
