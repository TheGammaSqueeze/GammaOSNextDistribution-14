#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
#include <array>
#include <android/binder_enums.h>
namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
enum class GnssMultipathIndicator : int32_t {
  UNKNOWN = 0,
  PRESENT = 1,
  NOT_PRESENT = 2,
};

[[nodiscard]] static inline std::string toString(GnssMultipathIndicator val) {
  switch(val) {
  case GnssMultipathIndicator::UNKNOWN:
    return "UNKNOWN";
  case GnssMultipathIndicator::PRESENT:
    return "PRESENT";
  case GnssMultipathIndicator::NOT_PRESENT:
    return "NOT_PRESENT";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::gnss::GnssMultipathIndicator, 3> enum_values<aidl::android::hardware::gnss::GnssMultipathIndicator> = {
  aidl::android::hardware::gnss::GnssMultipathIndicator::UNKNOWN,
  aidl::android::hardware::gnss::GnssMultipathIndicator::PRESENT,
  aidl::android::hardware::gnss::GnssMultipathIndicator::NOT_PRESENT,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
