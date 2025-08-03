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
namespace drm {
enum class HdcpLevel : int32_t {
  HDCP_UNKNOWN = 0,
  HDCP_NONE = 1,
  HDCP_V1 = 2,
  HDCP_V2 = 3,
  HDCP_V2_1 = 4,
  HDCP_V2_2 = 5,
  HDCP_NO_OUTPUT = 6,
  HDCP_V2_3 = 7,
};

}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace drm {
[[nodiscard]] static inline std::string toString(HdcpLevel val) {
  switch(val) {
  case HdcpLevel::HDCP_UNKNOWN:
    return "HDCP_UNKNOWN";
  case HdcpLevel::HDCP_NONE:
    return "HDCP_NONE";
  case HdcpLevel::HDCP_V1:
    return "HDCP_V1";
  case HdcpLevel::HDCP_V2:
    return "HDCP_V2";
  case HdcpLevel::HDCP_V2_1:
    return "HDCP_V2_1";
  case HdcpLevel::HDCP_V2_2:
    return "HDCP_V2_2";
  case HdcpLevel::HDCP_NO_OUTPUT:
    return "HDCP_NO_OUTPUT";
  case HdcpLevel::HDCP_V2_3:
    return "HDCP_V2_3";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::drm::HdcpLevel, 8> enum_values<aidl::android::hardware::drm::HdcpLevel> = {
  aidl::android::hardware::drm::HdcpLevel::HDCP_UNKNOWN,
  aidl::android::hardware::drm::HdcpLevel::HDCP_NONE,
  aidl::android::hardware::drm::HdcpLevel::HDCP_V1,
  aidl::android::hardware::drm::HdcpLevel::HDCP_V2,
  aidl::android::hardware::drm::HdcpLevel::HDCP_V2_1,
  aidl::android::hardware::drm::HdcpLevel::HDCP_V2_2,
  aidl::android::hardware::drm::HdcpLevel::HDCP_NO_OUTPUT,
  aidl::android::hardware::drm::HdcpLevel::HDCP_V2_3,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
