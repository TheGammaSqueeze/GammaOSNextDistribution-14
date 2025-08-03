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
enum class LogPriority : int32_t {
  UNKNOWN = 0,
  DEFAULT = 1,
  VERBOSE = 2,
  DEBUG = 3,
  INFO = 4,
  WARN = 5,
  ERROR = 6,
  FATAL = 7,
};

}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace drm {
[[nodiscard]] static inline std::string toString(LogPriority val) {
  switch(val) {
  case LogPriority::UNKNOWN:
    return "UNKNOWN";
  case LogPriority::DEFAULT:
    return "DEFAULT";
  case LogPriority::VERBOSE:
    return "VERBOSE";
  case LogPriority::DEBUG:
    return "DEBUG";
  case LogPriority::INFO:
    return "INFO";
  case LogPriority::WARN:
    return "WARN";
  case LogPriority::ERROR:
    return "ERROR";
  case LogPriority::FATAL:
    return "FATAL";
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
constexpr inline std::array<aidl::android::hardware::drm::LogPriority, 8> enum_values<aidl::android::hardware::drm::LogPriority> = {
  aidl::android::hardware::drm::LogPriority::UNKNOWN,
  aidl::android::hardware::drm::LogPriority::DEFAULT,
  aidl::android::hardware::drm::LogPriority::VERBOSE,
  aidl::android::hardware::drm::LogPriority::DEBUG,
  aidl::android::hardware::drm::LogPriority::INFO,
  aidl::android::hardware::drm::LogPriority::WARN,
  aidl::android::hardware::drm::LogPriority::ERROR,
  aidl::android::hardware::drm::LogPriority::FATAL,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
