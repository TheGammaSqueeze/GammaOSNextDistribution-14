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
namespace data {
enum class PdpProtocolType : int32_t {
  UNKNOWN = -1,
  IP = 0,
  IPV6 = 1,
  IPV4V6 = 2,
  PPP = 3,
  NON_IP = 4,
  UNSTRUCTURED = 5,
};

}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace data {
[[nodiscard]] static inline std::string toString(PdpProtocolType val) {
  switch(val) {
  case PdpProtocolType::UNKNOWN:
    return "UNKNOWN";
  case PdpProtocolType::IP:
    return "IP";
  case PdpProtocolType::IPV6:
    return "IPV6";
  case PdpProtocolType::IPV4V6:
    return "IPV4V6";
  case PdpProtocolType::PPP:
    return "PPP";
  case PdpProtocolType::NON_IP:
    return "NON_IP";
  case PdpProtocolType::UNSTRUCTURED:
    return "UNSTRUCTURED";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::data::PdpProtocolType, 7> enum_values<aidl::android::hardware::radio::data::PdpProtocolType> = {
  aidl::android::hardware::radio::data::PdpProtocolType::UNKNOWN,
  aidl::android::hardware::radio::data::PdpProtocolType::IP,
  aidl::android::hardware::radio::data::PdpProtocolType::IPV6,
  aidl::android::hardware::radio::data::PdpProtocolType::IPV4V6,
  aidl::android::hardware::radio::data::PdpProtocolType::PPP,
  aidl::android::hardware::radio::data::PdpProtocolType::NON_IP,
  aidl::android::hardware::radio::data::PdpProtocolType::UNSTRUCTURED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
