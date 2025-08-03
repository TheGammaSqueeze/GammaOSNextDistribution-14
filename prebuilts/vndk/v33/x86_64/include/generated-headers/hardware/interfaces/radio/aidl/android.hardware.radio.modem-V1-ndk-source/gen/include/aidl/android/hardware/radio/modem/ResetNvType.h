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
namespace modem {
enum class ResetNvType : int32_t {
  RELOAD = 0,
  ERASE = 1,
  FACTORY_RESET = 2,
};

}  // namespace modem
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace modem {
[[nodiscard]] static inline std::string toString(ResetNvType val) {
  switch(val) {
  case ResetNvType::RELOAD:
    return "RELOAD";
  case ResetNvType::ERASE:
    return "ERASE";
  case ResetNvType::FACTORY_RESET:
    return "FACTORY_RESET";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace modem
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::radio::modem::ResetNvType, 3> enum_values<aidl::android::hardware::radio::modem::ResetNvType> = {
  aidl::android::hardware::radio::modem::ResetNvType::RELOAD,
  aidl::android::hardware::radio::modem::ResetNvType::ERASE,
  aidl::android::hardware::radio::modem::ResetNvType::FACTORY_RESET,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
