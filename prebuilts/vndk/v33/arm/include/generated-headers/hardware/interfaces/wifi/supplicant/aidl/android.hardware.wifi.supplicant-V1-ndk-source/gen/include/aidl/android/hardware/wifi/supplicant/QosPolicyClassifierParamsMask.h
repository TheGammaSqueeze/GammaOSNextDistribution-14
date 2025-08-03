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
namespace wifi {
namespace supplicant {
enum class QosPolicyClassifierParamsMask : int32_t {
  SRC_IP = 1,
  DST_IP = 2,
  SRC_PORT = 4,
  DST_PORT_RANGE = 8,
  PROTOCOL_NEXT_HEADER = 16,
  FLOW_LABEL = 32,
  DOMAIN_NAME = 64,
};

}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace wifi {
namespace supplicant {
[[nodiscard]] static inline std::string toString(QosPolicyClassifierParamsMask val) {
  switch(val) {
  case QosPolicyClassifierParamsMask::SRC_IP:
    return "SRC_IP";
  case QosPolicyClassifierParamsMask::DST_IP:
    return "DST_IP";
  case QosPolicyClassifierParamsMask::SRC_PORT:
    return "SRC_PORT";
  case QosPolicyClassifierParamsMask::DST_PORT_RANGE:
    return "DST_PORT_RANGE";
  case QosPolicyClassifierParamsMask::PROTOCOL_NEXT_HEADER:
    return "PROTOCOL_NEXT_HEADER";
  case QosPolicyClassifierParamsMask::FLOW_LABEL:
    return "FLOW_LABEL";
  case QosPolicyClassifierParamsMask::DOMAIN_NAME:
    return "DOMAIN_NAME";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace supplicant
}  // namespace wifi
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::QosPolicyClassifierParamsMask, 7> enum_values<aidl::android::hardware::wifi::supplicant::QosPolicyClassifierParamsMask> = {
  aidl::android::hardware::wifi::supplicant::QosPolicyClassifierParamsMask::SRC_IP,
  aidl::android::hardware::wifi::supplicant::QosPolicyClassifierParamsMask::DST_IP,
  aidl::android::hardware::wifi::supplicant::QosPolicyClassifierParamsMask::SRC_PORT,
  aidl::android::hardware::wifi::supplicant::QosPolicyClassifierParamsMask::DST_PORT_RANGE,
  aidl::android::hardware::wifi::supplicant::QosPolicyClassifierParamsMask::PROTOCOL_NEXT_HEADER,
  aidl::android::hardware::wifi::supplicant::QosPolicyClassifierParamsMask::FLOW_LABEL,
  aidl::android::hardware::wifi::supplicant::QosPolicyClassifierParamsMask::DOMAIN_NAME,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
