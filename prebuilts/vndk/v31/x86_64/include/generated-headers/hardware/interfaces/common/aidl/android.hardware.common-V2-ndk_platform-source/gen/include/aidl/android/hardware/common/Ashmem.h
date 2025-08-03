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
class Ashmem {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::ndk::ScopedFileDescriptor fd;
  int64_t size = 0L;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const Ashmem& rhs) const {
    return std::tie(fd, size) != std::tie(rhs.fd, rhs.size);
  }
  inline bool operator<(const Ashmem& rhs) const {
    return std::tie(fd, size) < std::tie(rhs.fd, rhs.size);
  }
  inline bool operator<=(const Ashmem& rhs) const {
    return std::tie(fd, size) <= std::tie(rhs.fd, rhs.size);
  }
  inline bool operator==(const Ashmem& rhs) const {
    return std::tie(fd, size) == std::tie(rhs.fd, rhs.size);
  }
  inline bool operator>(const Ashmem& rhs) const {
    return std::tie(fd, size) > std::tie(rhs.fd, rhs.size);
  }
  inline bool operator>=(const Ashmem& rhs) const {
    return std::tie(fd, size) >= std::tie(rhs.fd, rhs.size);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "Ashmem{";
    os << "fd: " << ::android::internal::ToString(fd);
    os << ", size: " << ::android::internal::ToString(size);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace hardware
}  // namespace android
}  // namespace aidl
