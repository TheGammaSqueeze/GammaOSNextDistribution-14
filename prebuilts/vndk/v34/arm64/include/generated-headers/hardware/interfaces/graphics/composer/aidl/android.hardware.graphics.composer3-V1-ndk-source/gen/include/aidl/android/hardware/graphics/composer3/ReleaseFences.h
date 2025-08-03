#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/graphics/composer3/ReleaseFences.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
class ReleaseFences {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  class Layer {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    int64_t layer = 0L;
    ::ndk::ScopedFileDescriptor fence;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const Layer& rhs) const {
      return std::tie(layer, fence) != std::tie(rhs.layer, rhs.fence);
    }
    inline bool operator<(const Layer& rhs) const {
      return std::tie(layer, fence) < std::tie(rhs.layer, rhs.fence);
    }
    inline bool operator<=(const Layer& rhs) const {
      return std::tie(layer, fence) <= std::tie(rhs.layer, rhs.fence);
    }
    inline bool operator==(const Layer& rhs) const {
      return std::tie(layer, fence) == std::tie(rhs.layer, rhs.fence);
    }
    inline bool operator>(const Layer& rhs) const {
      return std::tie(layer, fence) > std::tie(rhs.layer, rhs.fence);
    }
    inline bool operator>=(const Layer& rhs) const {
      return std::tie(layer, fence) >= std::tie(rhs.layer, rhs.fence);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    inline std::string toString() const {
      std::ostringstream os;
      os << "Layer{";
      os << "layer: " << ::android::internal::ToString(layer);
      os << ", fence: " << ::android::internal::ToString(fence);
      os << "}";
      return os.str();
    }
  };
  int64_t display = 0L;
  std::vector<::aidl::android::hardware::graphics::composer3::ReleaseFences::Layer> layers;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ReleaseFences& rhs) const {
    return std::tie(display, layers) != std::tie(rhs.display, rhs.layers);
  }
  inline bool operator<(const ReleaseFences& rhs) const {
    return std::tie(display, layers) < std::tie(rhs.display, rhs.layers);
  }
  inline bool operator<=(const ReleaseFences& rhs) const {
    return std::tie(display, layers) <= std::tie(rhs.display, rhs.layers);
  }
  inline bool operator==(const ReleaseFences& rhs) const {
    return std::tie(display, layers) == std::tie(rhs.display, rhs.layers);
  }
  inline bool operator>(const ReleaseFences& rhs) const {
    return std::tie(display, layers) > std::tie(rhs.display, rhs.layers);
  }
  inline bool operator>=(const ReleaseFences& rhs) const {
    return std::tie(display, layers) >= std::tie(rhs.display, rhs.layers);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "ReleaseFences{";
    os << "display: " << ::android::internal::ToString(display);
    os << ", layers: " << ::android::internal::ToString(layers);
    os << "}";
    return os.str();
  }
};
}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
