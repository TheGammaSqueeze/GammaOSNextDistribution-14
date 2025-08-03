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
namespace nfc {
enum class PresenceCheckAlgorithm : int8_t {
  DEFAULT = 0,
  I_BLOCK = 1,
  ISO_DEP_NAK = 2,
};

}  // namespace nfc
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace nfc {
[[nodiscard]] static inline std::string toString(PresenceCheckAlgorithm val) {
  switch(val) {
  case PresenceCheckAlgorithm::DEFAULT:
    return "DEFAULT";
  case PresenceCheckAlgorithm::I_BLOCK:
    return "I_BLOCK";
  case PresenceCheckAlgorithm::ISO_DEP_NAK:
    return "ISO_DEP_NAK";
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace nfc
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::nfc::PresenceCheckAlgorithm, 3> enum_values<aidl::android::hardware::nfc::PresenceCheckAlgorithm> = {
  aidl::android::hardware::nfc::PresenceCheckAlgorithm::DEFAULT,
  aidl::android::hardware::nfc::PresenceCheckAlgorithm::I_BLOCK,
  aidl::android::hardware::nfc::PresenceCheckAlgorithm::ISO_DEP_NAK,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
