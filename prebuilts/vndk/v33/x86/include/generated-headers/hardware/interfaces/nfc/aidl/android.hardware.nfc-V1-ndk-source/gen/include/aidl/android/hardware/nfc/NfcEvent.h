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
enum class NfcEvent : int32_t {
  OPEN_CPLT = 0,
  CLOSE_CPLT = 1,
  POST_INIT_CPLT = 2,
  PRE_DISCOVER_CPLT = 3,
  HCI_NETWORK_RESET = 4,
  ERROR = 5,
};

}  // namespace nfc
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace nfc {
[[nodiscard]] static inline std::string toString(NfcEvent val) {
  switch(val) {
  case NfcEvent::OPEN_CPLT:
    return "OPEN_CPLT";
  case NfcEvent::CLOSE_CPLT:
    return "CLOSE_CPLT";
  case NfcEvent::POST_INIT_CPLT:
    return "POST_INIT_CPLT";
  case NfcEvent::PRE_DISCOVER_CPLT:
    return "PRE_DISCOVER_CPLT";
  case NfcEvent::HCI_NETWORK_RESET:
    return "HCI_NETWORK_RESET";
  case NfcEvent::ERROR:
    return "ERROR";
  default:
    return std::to_string(static_cast<int32_t>(val));
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
constexpr inline std::array<aidl::android::hardware::nfc::NfcEvent, 6> enum_values<aidl::android::hardware::nfc::NfcEvent> = {
  aidl::android::hardware::nfc::NfcEvent::OPEN_CPLT,
  aidl::android::hardware::nfc::NfcEvent::CLOSE_CPLT,
  aidl::android::hardware::nfc::NfcEvent::POST_INIT_CPLT,
  aidl::android::hardware::nfc::NfcEvent::PRE_DISCOVER_CPLT,
  aidl::android::hardware::nfc::NfcEvent::HCI_NETWORK_RESET,
  aidl::android::hardware::nfc::NfcEvent::ERROR,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
