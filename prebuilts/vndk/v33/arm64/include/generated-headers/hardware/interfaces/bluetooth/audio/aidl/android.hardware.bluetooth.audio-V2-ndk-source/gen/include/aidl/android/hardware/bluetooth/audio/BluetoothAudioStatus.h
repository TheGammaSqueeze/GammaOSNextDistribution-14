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
namespace bluetooth {
namespace audio {
enum class BluetoothAudioStatus : int32_t {
  UNKNOWN = 0,
  SUCCESS = 1,
  UNSUPPORTED_CODEC_CONFIGURATION = 2,
  FAILURE = 3,
  RECONFIGURATION = 4,
};

}  // namespace audio
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace bluetooth {
namespace audio {
[[nodiscard]] static inline std::string toString(BluetoothAudioStatus val) {
  switch(val) {
  case BluetoothAudioStatus::UNKNOWN:
    return "UNKNOWN";
  case BluetoothAudioStatus::SUCCESS:
    return "SUCCESS";
  case BluetoothAudioStatus::UNSUPPORTED_CODEC_CONFIGURATION:
    return "UNSUPPORTED_CODEC_CONFIGURATION";
  case BluetoothAudioStatus::FAILURE:
    return "FAILURE";
  case BluetoothAudioStatus::RECONFIGURATION:
    return "RECONFIGURATION";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace audio
}  // namespace bluetooth
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::bluetooth::audio::BluetoothAudioStatus, 5> enum_values<aidl::android::hardware::bluetooth::audio::BluetoothAudioStatus> = {
  aidl::android::hardware::bluetooth::audio::BluetoothAudioStatus::UNKNOWN,
  aidl::android::hardware::bluetooth::audio::BluetoothAudioStatus::SUCCESS,
  aidl::android::hardware::bluetooth::audio::BluetoothAudioStatus::UNSUPPORTED_CODEC_CONFIGURATION,
  aidl::android::hardware::bluetooth::audio::BluetoothAudioStatus::FAILURE,
  aidl::android::hardware::bluetooth::audio::BluetoothAudioStatus::RECONFIGURATION,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
