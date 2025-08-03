#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace media {
namespace soundtrigger {
class ModelParameterRange {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t minInclusive = 0;
  int32_t maxInclusive = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ModelParameterRange& rhs) const {
    return std::tie(minInclusive, maxInclusive) != std::tie(rhs.minInclusive, rhs.maxInclusive);
  }
  inline bool operator<(const ModelParameterRange& rhs) const {
    return std::tie(minInclusive, maxInclusive) < std::tie(rhs.minInclusive, rhs.maxInclusive);
  }
  inline bool operator<=(const ModelParameterRange& rhs) const {
    return std::tie(minInclusive, maxInclusive) <= std::tie(rhs.minInclusive, rhs.maxInclusive);
  }
  inline bool operator==(const ModelParameterRange& rhs) const {
    return std::tie(minInclusive, maxInclusive) == std::tie(rhs.minInclusive, rhs.maxInclusive);
  }
  inline bool operator>(const ModelParameterRange& rhs) const {
    return std::tie(minInclusive, maxInclusive) > std::tie(rhs.minInclusive, rhs.maxInclusive);
  }
  inline bool operator>=(const ModelParameterRange& rhs) const {
    return std::tie(minInclusive, maxInclusive) >= std::tie(rhs.minInclusive, rhs.maxInclusive);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "ModelParameterRange{";
    os << "minInclusive: " << ::android::internal::ToString(minInclusive);
    os << ", maxInclusive: " << ::android::internal::ToString(maxInclusive);
    os << "}";
    return os.str();
  }
};
}  // namespace soundtrigger
}  // namespace media
}  // namespace android
}  // namespace aidl
