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
enum class P2pFrameTypeMask : int32_t {
  P2P_FRAME_PROBE_REQ_P2P = 1,
  P2P_FRAME_PROBE_RESP_P2P = 2,
  P2P_FRAME_PROBE_RESP_P2P_GO = 4,
  P2P_FRAME_BEACON_P2P_GO = 8,
  P2P_FRAME_P2P_PD_REQ = 16,
  P2P_FRAME_P2P_PD_RESP = 32,
  P2P_FRAME_P2P_GO_NEG_REQ = 64,
  P2P_FRAME_P2P_GO_NEG_RESP = 128,
  P2P_FRAME_P2P_GO_NEG_CONF = 256,
  P2P_FRAME_P2P_INV_REQ = 512,
  P2P_FRAME_P2P_INV_RESP = 1024,
  P2P_FRAME_P2P_ASSOC_REQ = 2048,
  P2P_FRAME_P2P_ASSOC_RESP = 4096,
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
[[nodiscard]] static inline std::string toString(P2pFrameTypeMask val) {
  switch(val) {
  case P2pFrameTypeMask::P2P_FRAME_PROBE_REQ_P2P:
    return "P2P_FRAME_PROBE_REQ_P2P";
  case P2pFrameTypeMask::P2P_FRAME_PROBE_RESP_P2P:
    return "P2P_FRAME_PROBE_RESP_P2P";
  case P2pFrameTypeMask::P2P_FRAME_PROBE_RESP_P2P_GO:
    return "P2P_FRAME_PROBE_RESP_P2P_GO";
  case P2pFrameTypeMask::P2P_FRAME_BEACON_P2P_GO:
    return "P2P_FRAME_BEACON_P2P_GO";
  case P2pFrameTypeMask::P2P_FRAME_P2P_PD_REQ:
    return "P2P_FRAME_P2P_PD_REQ";
  case P2pFrameTypeMask::P2P_FRAME_P2P_PD_RESP:
    return "P2P_FRAME_P2P_PD_RESP";
  case P2pFrameTypeMask::P2P_FRAME_P2P_GO_NEG_REQ:
    return "P2P_FRAME_P2P_GO_NEG_REQ";
  case P2pFrameTypeMask::P2P_FRAME_P2P_GO_NEG_RESP:
    return "P2P_FRAME_P2P_GO_NEG_RESP";
  case P2pFrameTypeMask::P2P_FRAME_P2P_GO_NEG_CONF:
    return "P2P_FRAME_P2P_GO_NEG_CONF";
  case P2pFrameTypeMask::P2P_FRAME_P2P_INV_REQ:
    return "P2P_FRAME_P2P_INV_REQ";
  case P2pFrameTypeMask::P2P_FRAME_P2P_INV_RESP:
    return "P2P_FRAME_P2P_INV_RESP";
  case P2pFrameTypeMask::P2P_FRAME_P2P_ASSOC_REQ:
    return "P2P_FRAME_P2P_ASSOC_REQ";
  case P2pFrameTypeMask::P2P_FRAME_P2P_ASSOC_RESP:
    return "P2P_FRAME_P2P_ASSOC_RESP";
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
constexpr inline std::array<aidl::android::hardware::wifi::supplicant::P2pFrameTypeMask, 13> enum_values<aidl::android::hardware::wifi::supplicant::P2pFrameTypeMask> = {
  aidl::android::hardware::wifi::supplicant::P2pFrameTypeMask::P2P_FRAME_PROBE_REQ_P2P,
  aidl::android::hardware::wifi::supplicant::P2pFrameTypeMask::P2P_FRAME_PROBE_RESP_P2P,
  aidl::android::hardware::wifi::supplicant::P2pFrameTypeMask::P2P_FRAME_PROBE_RESP_P2P_GO,
  aidl::android::hardware::wifi::supplicant::P2pFrameTypeMask::P2P_FRAME_BEACON_P2P_GO,
  aidl::android::hardware::wifi::supplicant::P2pFrameTypeMask::P2P_FRAME_P2P_PD_REQ,
  aidl::android::hardware::wifi::supplicant::P2pFrameTypeMask::P2P_FRAME_P2P_PD_RESP,
  aidl::android::hardware::wifi::supplicant::P2pFrameTypeMask::P2P_FRAME_P2P_GO_NEG_REQ,
  aidl::android::hardware::wifi::supplicant::P2pFrameTypeMask::P2P_FRAME_P2P_GO_NEG_RESP,
  aidl::android::hardware::wifi::supplicant::P2pFrameTypeMask::P2P_FRAME_P2P_GO_NEG_CONF,
  aidl::android::hardware::wifi::supplicant::P2pFrameTypeMask::P2P_FRAME_P2P_INV_REQ,
  aidl::android::hardware::wifi::supplicant::P2pFrameTypeMask::P2P_FRAME_P2P_INV_RESP,
  aidl::android::hardware::wifi::supplicant::P2pFrameTypeMask::P2P_FRAME_P2P_ASSOC_REQ,
  aidl::android::hardware::wifi::supplicant::P2pFrameTypeMask::P2P_FRAME_P2P_ASSOC_RESP,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
