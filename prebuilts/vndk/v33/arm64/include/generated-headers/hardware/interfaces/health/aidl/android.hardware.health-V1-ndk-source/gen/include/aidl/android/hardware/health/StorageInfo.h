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
namespace hardware {
namespace health {
class StorageInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t eol = 0;
  int32_t lifetimeA = 0;
  int32_t lifetimeB = 0;
  std::string version;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const StorageInfo& rhs) const {
    return std::tie(eol, lifetimeA, lifetimeB, version) != std::tie(rhs.eol, rhs.lifetimeA, rhs.lifetimeB, rhs.version);
  }
  inline bool operator<(const StorageInfo& rhs) const {
    return std::tie(eol, lifetimeA, lifetimeB, version) < std::tie(rhs.eol, rhs.lifetimeA, rhs.lifetimeB, rhs.version);
  }
  inline bool operator<=(const StorageInfo& rhs) const {
    return std::tie(eol, lifetimeA, lifetimeB, version) <= std::tie(rhs.eol, rhs.lifetimeA, rhs.lifetimeB, rhs.version);
  }
  inline bool operator==(const StorageInfo& rhs) const {
    return std::tie(eol, lifetimeA, lifetimeB, version) == std::tie(rhs.eol, rhs.lifetimeA, rhs.lifetimeB, rhs.version);
  }
  inline bool operator>(const StorageInfo& rhs) const {
    return std::tie(eol, lifetimeA, lifetimeB, version) > std::tie(rhs.eol, rhs.lifetimeA, rhs.lifetimeB, rhs.version);
  }
  inline bool operator>=(const StorageInfo& rhs) const {
    return std::tie(eol, lifetimeA, lifetimeB, version) >= std::tie(rhs.eol, rhs.lifetimeA, rhs.lifetimeB, rhs.version);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "StorageInfo{";
    os << "eol: " << ::android::internal::ToString(eol);
    os << ", lifetimeA: " << ::android::internal::ToString(lifetimeA);
    os << ", lifetimeB: " << ::android::internal::ToString(lifetimeB);
    os << ", version: " << ::android::internal::ToString(version);
    os << "}";
    return os.str();
  }
};
}  // namespace health
}  // namespace hardware
}  // namespace android
}  // namespace aidl
