#pragma once

#include <array>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_enums.h>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/frameworks/cameraservice/device/OutputConfiguration.h>
#include <aidl/android/hardware/common/NativeHandle.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl::android::hardware::common {
class NativeHandle;
}  // namespace aidl::android::hardware::common
namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace device {
class OutputConfiguration {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  enum class Rotation : int32_t {
    R0 = 0,
    R90 = 1,
    R180 = 2,
    R270 = 3,
  };

  enum class WindowGroupId : int32_t {
    NONE = -1,
  };

  std::vector<::aidl::android::hardware::common::NativeHandle> windowHandles;
  ::aidl::android::frameworks::cameraservice::device::OutputConfiguration::Rotation rotation = ::aidl::android::frameworks::cameraservice::device::OutputConfiguration::Rotation(0);
  int32_t windowGroupId = 0;
  std::string physicalCameraId;
  int32_t width = 0;
  int32_t height = 0;
  bool isDeferred = false;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const OutputConfiguration& rhs) const {
    return std::tie(windowHandles, rotation, windowGroupId, physicalCameraId, width, height, isDeferred) != std::tie(rhs.windowHandles, rhs.rotation, rhs.windowGroupId, rhs.physicalCameraId, rhs.width, rhs.height, rhs.isDeferred);
  }
  inline bool operator<(const OutputConfiguration& rhs) const {
    return std::tie(windowHandles, rotation, windowGroupId, physicalCameraId, width, height, isDeferred) < std::tie(rhs.windowHandles, rhs.rotation, rhs.windowGroupId, rhs.physicalCameraId, rhs.width, rhs.height, rhs.isDeferred);
  }
  inline bool operator<=(const OutputConfiguration& rhs) const {
    return std::tie(windowHandles, rotation, windowGroupId, physicalCameraId, width, height, isDeferred) <= std::tie(rhs.windowHandles, rhs.rotation, rhs.windowGroupId, rhs.physicalCameraId, rhs.width, rhs.height, rhs.isDeferred);
  }
  inline bool operator==(const OutputConfiguration& rhs) const {
    return std::tie(windowHandles, rotation, windowGroupId, physicalCameraId, width, height, isDeferred) == std::tie(rhs.windowHandles, rhs.rotation, rhs.windowGroupId, rhs.physicalCameraId, rhs.width, rhs.height, rhs.isDeferred);
  }
  inline bool operator>(const OutputConfiguration& rhs) const {
    return std::tie(windowHandles, rotation, windowGroupId, physicalCameraId, width, height, isDeferred) > std::tie(rhs.windowHandles, rhs.rotation, rhs.windowGroupId, rhs.physicalCameraId, rhs.width, rhs.height, rhs.isDeferred);
  }
  inline bool operator>=(const OutputConfiguration& rhs) const {
    return std::tie(windowHandles, rotation, windowGroupId, physicalCameraId, width, height, isDeferred) >= std::tie(rhs.windowHandles, rhs.rotation, rhs.windowGroupId, rhs.physicalCameraId, rhs.width, rhs.height, rhs.isDeferred);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "OutputConfiguration{";
    os << "windowHandles: " << ::android::internal::ToString(windowHandles);
    os << ", rotation: " << ::android::internal::ToString(rotation);
    os << ", windowGroupId: " << ::android::internal::ToString(windowGroupId);
    os << ", physicalCameraId: " << ::android::internal::ToString(physicalCameraId);
    os << ", width: " << ::android::internal::ToString(width);
    os << ", height: " << ::android::internal::ToString(height);
    os << ", isDeferred: " << ::android::internal::ToString(isDeferred);
    os << "}";
    return os.str();
  }
};
}  // namespace device
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace device {
[[nodiscard]] static inline std::string toString(OutputConfiguration::Rotation val) {
  switch(val) {
  case OutputConfiguration::Rotation::R0:
    return "R0";
  case OutputConfiguration::Rotation::R90:
    return "R90";
  case OutputConfiguration::Rotation::R180:
    return "R180";
  case OutputConfiguration::Rotation::R270:
    return "R270";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace device
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::frameworks::cameraservice::device::OutputConfiguration::Rotation, 4> enum_values<aidl::android::frameworks::cameraservice::device::OutputConfiguration::Rotation> = {
  aidl::android::frameworks::cameraservice::device::OutputConfiguration::Rotation::R0,
  aidl::android::frameworks::cameraservice::device::OutputConfiguration::Rotation::R90,
  aidl::android::frameworks::cameraservice::device::OutputConfiguration::Rotation::R180,
  aidl::android::frameworks::cameraservice::device::OutputConfiguration::Rotation::R270,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace device {
[[nodiscard]] static inline std::string toString(OutputConfiguration::WindowGroupId val) {
  switch(val) {
  case OutputConfiguration::WindowGroupId::NONE:
    return "NONE";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace device
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::frameworks::cameraservice::device::OutputConfiguration::WindowGroupId, 1> enum_values<aidl::android::frameworks::cameraservice::device::OutputConfiguration::WindowGroupId> = {
  aidl::android::frameworks::cameraservice::device::OutputConfiguration::WindowGroupId::NONE,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
