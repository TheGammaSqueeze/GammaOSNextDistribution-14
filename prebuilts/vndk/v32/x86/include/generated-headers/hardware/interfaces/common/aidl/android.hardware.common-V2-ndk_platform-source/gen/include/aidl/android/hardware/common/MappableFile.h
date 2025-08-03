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
class MappableFile {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int64_t length = 0L;
  int32_t prot = 0;
  ::ndk::ScopedFileDescriptor fd;
  int64_t offset = 0L;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const MappableFile& rhs) const {
    return std::tie(length, prot, fd, offset) != std::tie(rhs.length, rhs.prot, rhs.fd, rhs.offset);
  }
  inline bool operator<(const MappableFile& rhs) const {
    return std::tie(length, prot, fd, offset) < std::tie(rhs.length, rhs.prot, rhs.fd, rhs.offset);
  }
  inline bool operator<=(const MappableFile& rhs) const {
    return std::tie(length, prot, fd, offset) <= std::tie(rhs.length, rhs.prot, rhs.fd, rhs.offset);
  }
  inline bool operator==(const MappableFile& rhs) const {
    return std::tie(length, prot, fd, offset) == std::tie(rhs.length, rhs.prot, rhs.fd, rhs.offset);
  }
  inline bool operator>(const MappableFile& rhs) const {
    return std::tie(length, prot, fd, offset) > std::tie(rhs.length, rhs.prot, rhs.fd, rhs.offset);
  }
  inline bool operator>=(const MappableFile& rhs) const {
    return std::tie(length, prot, fd, offset) >= std::tie(rhs.length, rhs.prot, rhs.fd, rhs.offset);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "MappableFile{";
    os << "length: " << ::android::internal::ToString(length);
    os << ", prot: " << ::android::internal::ToString(prot);
    os << ", fd: " << ::android::internal::ToString(fd);
    os << ", offset: " << ::android::internal::ToString(offset);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace hardware
}  // namespace android
}  // namespace aidl
