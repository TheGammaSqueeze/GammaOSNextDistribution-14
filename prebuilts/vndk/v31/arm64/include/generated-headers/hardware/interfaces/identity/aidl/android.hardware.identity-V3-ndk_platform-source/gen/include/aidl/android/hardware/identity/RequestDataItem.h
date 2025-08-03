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
namespace identity {
class RequestDataItem {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string name;
  int64_t size = 0L;
  std::vector<int32_t> accessControlProfileIds;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const RequestDataItem& rhs) const {
    return std::tie(name, size, accessControlProfileIds) != std::tie(rhs.name, rhs.size, rhs.accessControlProfileIds);
  }
  inline bool operator<(const RequestDataItem& rhs) const {
    return std::tie(name, size, accessControlProfileIds) < std::tie(rhs.name, rhs.size, rhs.accessControlProfileIds);
  }
  inline bool operator<=(const RequestDataItem& rhs) const {
    return std::tie(name, size, accessControlProfileIds) <= std::tie(rhs.name, rhs.size, rhs.accessControlProfileIds);
  }
  inline bool operator==(const RequestDataItem& rhs) const {
    return std::tie(name, size, accessControlProfileIds) == std::tie(rhs.name, rhs.size, rhs.accessControlProfileIds);
  }
  inline bool operator>(const RequestDataItem& rhs) const {
    return std::tie(name, size, accessControlProfileIds) > std::tie(rhs.name, rhs.size, rhs.accessControlProfileIds);
  }
  inline bool operator>=(const RequestDataItem& rhs) const {
    return std::tie(name, size, accessControlProfileIds) >= std::tie(rhs.name, rhs.size, rhs.accessControlProfileIds);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "RequestDataItem{";
    os << "name: " << ::android::internal::ToString(name);
    os << ", size: " << ::android::internal::ToString(size);
    os << ", accessControlProfileIds: " << ::android::internal::ToString(accessControlProfileIds);
    os << "}";
    return os.str();
  }
};
}  // namespace identity
}  // namespace hardware
}  // namespace android
}  // namespace aidl
