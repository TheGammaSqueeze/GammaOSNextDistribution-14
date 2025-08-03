#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/graphics/composer3/ClientTargetProperty.h>
#include <aidl/android/hardware/graphics/composer3/DimmingStage.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
class ClientTargetPropertyWithBrightness {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int64_t display = 0L;
  ::aidl::android::hardware::graphics::composer3::ClientTargetProperty clientTargetProperty;
  float brightness = 0.000000f;
  ::aidl::android::hardware::graphics::composer3::DimmingStage dimmingStage = ::aidl::android::hardware::graphics::composer3::DimmingStage(0);

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ClientTargetPropertyWithBrightness& rhs) const {
    return std::tie(display, clientTargetProperty, brightness, dimmingStage) != std::tie(rhs.display, rhs.clientTargetProperty, rhs.brightness, rhs.dimmingStage);
  }
  inline bool operator<(const ClientTargetPropertyWithBrightness& rhs) const {
    return std::tie(display, clientTargetProperty, brightness, dimmingStage) < std::tie(rhs.display, rhs.clientTargetProperty, rhs.brightness, rhs.dimmingStage);
  }
  inline bool operator<=(const ClientTargetPropertyWithBrightness& rhs) const {
    return std::tie(display, clientTargetProperty, brightness, dimmingStage) <= std::tie(rhs.display, rhs.clientTargetProperty, rhs.brightness, rhs.dimmingStage);
  }
  inline bool operator==(const ClientTargetPropertyWithBrightness& rhs) const {
    return std::tie(display, clientTargetProperty, brightness, dimmingStage) == std::tie(rhs.display, rhs.clientTargetProperty, rhs.brightness, rhs.dimmingStage);
  }
  inline bool operator>(const ClientTargetPropertyWithBrightness& rhs) const {
    return std::tie(display, clientTargetProperty, brightness, dimmingStage) > std::tie(rhs.display, rhs.clientTargetProperty, rhs.brightness, rhs.dimmingStage);
  }
  inline bool operator>=(const ClientTargetPropertyWithBrightness& rhs) const {
    return std::tie(display, clientTargetProperty, brightness, dimmingStage) >= std::tie(rhs.display, rhs.clientTargetProperty, rhs.brightness, rhs.dimmingStage);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "ClientTargetPropertyWithBrightness{";
    os << "display: " << ::android::internal::ToString(display);
    os << ", clientTargetProperty: " << ::android::internal::ToString(clientTargetProperty);
    os << ", brightness: " << ::android::internal::ToString(brightness);
    os << ", dimmingStage: " << ::android::internal::ToString(dimmingStage);
    os << "}";
    return os.str();
  }
};
}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
