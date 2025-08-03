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
class NativeHandle {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<::ndk::ScopedFileDescriptor> fds;
  std::vector<int32_t> ints;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const NativeHandle& rhs) const {
    return std::tie(fds, ints) != std::tie(rhs.fds, rhs.ints);
  }
  inline bool operator<(const NativeHandle& rhs) const {
    return std::tie(fds, ints) < std::tie(rhs.fds, rhs.ints);
  }
  inline bool operator<=(const NativeHandle& rhs) const {
    return std::tie(fds, ints) <= std::tie(rhs.fds, rhs.ints);
  }
  inline bool operator==(const NativeHandle& rhs) const {
    return std::tie(fds, ints) == std::tie(rhs.fds, rhs.ints);
  }
  inline bool operator>(const NativeHandle& rhs) const {
    return std::tie(fds, ints) > std::tie(rhs.fds, rhs.ints);
  }
  inline bool operator>=(const NativeHandle& rhs) const {
    return std::tie(fds, ints) >= std::tie(rhs.fds, rhs.ints);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "NativeHandle{";
    os << "fds: " << ::android::internal::ToString(fds);
    os << ", ints: " << ::android::internal::ToString(ints);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace hardware
}  // namespace android
}  // namespace aidl
