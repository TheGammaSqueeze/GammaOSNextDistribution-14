#pragma once
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
namespace aidl {
namespace android {
namespace hardware {
namespace common {
namespace fmq {
class GrantorDescriptor {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t fdIndex = 0;
  int32_t offset = 0;
  int64_t extent = 0L;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const GrantorDescriptor& rhs) const {
    return std::tie(fdIndex, offset, extent) != std::tie(rhs.fdIndex, rhs.offset, rhs.extent);
  }
  inline bool operator<(const GrantorDescriptor& rhs) const {
    return std::tie(fdIndex, offset, extent) < std::tie(rhs.fdIndex, rhs.offset, rhs.extent);
  }
  inline bool operator<=(const GrantorDescriptor& rhs) const {
    return std::tie(fdIndex, offset, extent) <= std::tie(rhs.fdIndex, rhs.offset, rhs.extent);
  }
  inline bool operator==(const GrantorDescriptor& rhs) const {
    return std::tie(fdIndex, offset, extent) == std::tie(rhs.fdIndex, rhs.offset, rhs.extent);
  }
  inline bool operator>(const GrantorDescriptor& rhs) const {
    return std::tie(fdIndex, offset, extent) > std::tie(rhs.fdIndex, rhs.offset, rhs.extent);
  }
  inline bool operator>=(const GrantorDescriptor& rhs) const {
    return std::tie(fdIndex, offset, extent) >= std::tie(rhs.fdIndex, rhs.offset, rhs.extent);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "GrantorDescriptor{";
    os << "fdIndex: " << ::android::internal::ToString(fdIndex);
    os << ", offset: " << ::android::internal::ToString(offset);
    os << ", extent: " << ::android::internal::ToString(extent);
    os << "}";
    return os.str();
  }
};
}  // namespace fmq
}  // namespace common
}  // namespace hardware
}  // namespace android
}  // namespace aidl
