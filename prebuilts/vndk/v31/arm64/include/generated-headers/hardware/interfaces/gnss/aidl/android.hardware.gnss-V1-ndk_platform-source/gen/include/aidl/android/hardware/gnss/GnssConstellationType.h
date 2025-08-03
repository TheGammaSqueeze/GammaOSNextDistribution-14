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
enum class GnssConstellationType : int32_t {
  UNKNOWN = 0,
  GPS = 1,
  SBAS = 2,
  GLONASS = 3,
  QZSS = 4,
  BEIDOU = 5,
  GALILEO = 6,
  IRNSS = 7,
};

[[nodiscard]] static inline std::string toString(GnssConstellationType val) {
  switch(val) {
  case GnssConstellationType::UNKNOWN:
    return "UNKNOWN";
  case GnssConstellationType::GPS:
    return "GPS";
  case GnssConstellationType::SBAS:
    return "SBAS";
  case GnssConstellationType::GLONASS:
    return "GLONASS";
  case GnssConstellationType::QZSS:
    return "QZSS";
  case GnssConstellationType::BEIDOU:
    return "BEIDOU";
  case GnssConstellationType::GALILEO:
    return "GALILEO";
  case GnssConstellationType::IRNSS:
    return "IRNSS";
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
constexpr inline std::array<aidl::android::hardware::gnss::GnssConstellationType, 8> enum_values<aidl::android::hardware::gnss::GnssConstellationType> = {
  aidl::android::hardware::gnss::GnssConstellationType::UNKNOWN,
  aidl::android::hardware::gnss::GnssConstellationType::GPS,
  aidl::android::hardware::gnss::GnssConstellationType::SBAS,
  aidl::android::hardware::gnss::GnssConstellationType::GLONASS,
  aidl::android::hardware::gnss::GnssConstellationType::QZSS,
  aidl::android::hardware::gnss::GnssConstellationType::BEIDOU,
  aidl::android::hardware::gnss::GnssConstellationType::GALILEO,
  aidl::android::hardware::gnss::GnssConstellationType::IRNSS,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
