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
namespace memtrack {
class MemtrackRecord {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t flags = 0;
  int64_t sizeInBytes = 0L;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const MemtrackRecord& rhs) const {
    return std::tie(flags, sizeInBytes) != std::tie(rhs.flags, rhs.sizeInBytes);
  }
  inline bool operator<(const MemtrackRecord& rhs) const {
    return std::tie(flags, sizeInBytes) < std::tie(rhs.flags, rhs.sizeInBytes);
  }
  inline bool operator<=(const MemtrackRecord& rhs) const {
    return std::tie(flags, sizeInBytes) <= std::tie(rhs.flags, rhs.sizeInBytes);
  }
  inline bool operator==(const MemtrackRecord& rhs) const {
    return std::tie(flags, sizeInBytes) == std::tie(rhs.flags, rhs.sizeInBytes);
  }
  inline bool operator>(const MemtrackRecord& rhs) const {
    return std::tie(flags, sizeInBytes) > std::tie(rhs.flags, rhs.sizeInBytes);
  }
  inline bool operator>=(const MemtrackRecord& rhs) const {
    return std::tie(flags, sizeInBytes) >= std::tie(rhs.flags, rhs.sizeInBytes);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { FLAG_SMAPS_ACCOUNTED = 2 };
  enum : int32_t { FLAG_SMAPS_UNACCOUNTED = 4 };
  enum : int32_t { FLAG_SHARED = 8 };
  enum : int32_t { FLAG_SHARED_PSS = 16 };
  enum : int32_t { FLAG_PRIVATE = 32 };
  enum : int32_t { FLAG_SYSTEM = 64 };
  enum : int32_t { FLAG_DEDICATED = 128 };
  enum : int32_t { FLAG_NONSECURE = 256 };
  enum : int32_t { FLAG_SECURE = 512 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "MemtrackRecord{";
    os << "flags: " << ::android::internal::ToString(flags);
    os << ", sizeInBytes: " << ::android::internal::ToString(sizeInBytes);
    os << "}";
    return os.str();
  }
};
}  // namespace memtrack
}  // namespace hardware
}  // namespace android
}  // namespace aidl
