#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/graphics/composer3/DisplayRequest.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
class DisplayRequest {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  class LayerRequest {
  public:
    typedef std::false_type fixed_size;
    static const char* descriptor;

    int64_t layer = 0L;
    int32_t mask = 0;

    binder_status_t readFromParcel(const AParcel* parcel);
    binder_status_t writeToParcel(AParcel* parcel) const;

    inline bool operator!=(const LayerRequest& rhs) const {
      return std::tie(layer, mask) != std::tie(rhs.layer, rhs.mask);
    }
    inline bool operator<(const LayerRequest& rhs) const {
      return std::tie(layer, mask) < std::tie(rhs.layer, rhs.mask);
    }
    inline bool operator<=(const LayerRequest& rhs) const {
      return std::tie(layer, mask) <= std::tie(rhs.layer, rhs.mask);
    }
    inline bool operator==(const LayerRequest& rhs) const {
      return std::tie(layer, mask) == std::tie(rhs.layer, rhs.mask);
    }
    inline bool operator>(const LayerRequest& rhs) const {
      return std::tie(layer, mask) > std::tie(rhs.layer, rhs.mask);
    }
    inline bool operator>=(const LayerRequest& rhs) const {
      return std::tie(layer, mask) >= std::tie(rhs.layer, rhs.mask);
    }

    static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
    enum : int32_t { CLEAR_CLIENT_TARGET = 1 };
    inline std::string toString() const {
      std::ostringstream os;
      os << "LayerRequest{";
      os << "layer: " << ::android::internal::ToString(layer);
      os << ", mask: " << ::android::internal::ToString(mask);
      os << "}";
      return os.str();
    }
  };
  int64_t display = 0L;
  int32_t mask = 0;
  std::vector<::aidl::android::hardware::graphics::composer3::DisplayRequest::LayerRequest> layerRequests;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const DisplayRequest& rhs) const {
    return std::tie(display, mask, layerRequests) != std::tie(rhs.display, rhs.mask, rhs.layerRequests);
  }
  inline bool operator<(const DisplayRequest& rhs) const {
    return std::tie(display, mask, layerRequests) < std::tie(rhs.display, rhs.mask, rhs.layerRequests);
  }
  inline bool operator<=(const DisplayRequest& rhs) const {
    return std::tie(display, mask, layerRequests) <= std::tie(rhs.display, rhs.mask, rhs.layerRequests);
  }
  inline bool operator==(const DisplayRequest& rhs) const {
    return std::tie(display, mask, layerRequests) == std::tie(rhs.display, rhs.mask, rhs.layerRequests);
  }
  inline bool operator>(const DisplayRequest& rhs) const {
    return std::tie(display, mask, layerRequests) > std::tie(rhs.display, rhs.mask, rhs.layerRequests);
  }
  inline bool operator>=(const DisplayRequest& rhs) const {
    return std::tie(display, mask, layerRequests) >= std::tie(rhs.display, rhs.mask, rhs.layerRequests);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { FLIP_CLIENT_TARGET = 1 };
  enum : int32_t { WRITE_CLIENT_TARGET_TO_OUTPUT = 2 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "DisplayRequest{";
    os << "display: " << ::android::internal::ToString(display);
    os << ", mask: " << ::android::internal::ToString(mask);
    os << ", layerRequests: " << ::android::internal::ToString(layerRequests);
    os << "}";
    return os.str();
  }
};
}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
