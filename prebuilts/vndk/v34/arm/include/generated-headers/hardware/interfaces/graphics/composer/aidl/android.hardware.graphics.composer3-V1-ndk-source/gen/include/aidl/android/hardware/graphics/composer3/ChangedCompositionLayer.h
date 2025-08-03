#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/graphics/composer3/Composition.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
class ChangedCompositionLayer {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int64_t layer = 0L;
  ::aidl::android::hardware::graphics::composer3::Composition composition = ::aidl::android::hardware::graphics::composer3::Composition(0);

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ChangedCompositionLayer& rhs) const {
    return std::tie(layer, composition) != std::tie(rhs.layer, rhs.composition);
  }
  inline bool operator<(const ChangedCompositionLayer& rhs) const {
    return std::tie(layer, composition) < std::tie(rhs.layer, rhs.composition);
  }
  inline bool operator<=(const ChangedCompositionLayer& rhs) const {
    return std::tie(layer, composition) <= std::tie(rhs.layer, rhs.composition);
  }
  inline bool operator==(const ChangedCompositionLayer& rhs) const {
    return std::tie(layer, composition) == std::tie(rhs.layer, rhs.composition);
  }
  inline bool operator>(const ChangedCompositionLayer& rhs) const {
    return std::tie(layer, composition) > std::tie(rhs.layer, rhs.composition);
  }
  inline bool operator>=(const ChangedCompositionLayer& rhs) const {
    return std::tie(layer, composition) >= std::tie(rhs.layer, rhs.composition);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "ChangedCompositionLayer{";
    os << "layer: " << ::android::internal::ToString(layer);
    os << ", composition: " << ::android::internal::ToString(composition);
    os << "}";
    return os.str();
  }
};
}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
