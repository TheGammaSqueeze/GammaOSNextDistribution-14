#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/graphics/composer3/PerFrameMetadataKey.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
class PerFrameMetadataBlob {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::graphics::composer3::PerFrameMetadataKey key = ::aidl::android::hardware::graphics::composer3::PerFrameMetadataKey(0);
  std::vector<uint8_t> blob;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const PerFrameMetadataBlob& rhs) const {
    return std::tie(key, blob) != std::tie(rhs.key, rhs.blob);
  }
  inline bool operator<(const PerFrameMetadataBlob& rhs) const {
    return std::tie(key, blob) < std::tie(rhs.key, rhs.blob);
  }
  inline bool operator<=(const PerFrameMetadataBlob& rhs) const {
    return std::tie(key, blob) <= std::tie(rhs.key, rhs.blob);
  }
  inline bool operator==(const PerFrameMetadataBlob& rhs) const {
    return std::tie(key, blob) == std::tie(rhs.key, rhs.blob);
  }
  inline bool operator>(const PerFrameMetadataBlob& rhs) const {
    return std::tie(key, blob) > std::tie(rhs.key, rhs.blob);
  }
  inline bool operator>=(const PerFrameMetadataBlob& rhs) const {
    return std::tie(key, blob) >= std::tie(rhs.key, rhs.blob);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "PerFrameMetadataBlob{";
    os << "key: " << ::android::internal::ToString(key);
    os << ", blob: " << ::android::internal::ToString(blob);
    os << "}";
    return os.str();
  }
};
}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
