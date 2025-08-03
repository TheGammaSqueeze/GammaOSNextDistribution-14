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
#include <aidl/android/hardware/identity/RequestDataItem.h>
namespace aidl {
namespace android {
namespace hardware {
namespace identity {
class RequestNamespace {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string namespaceName;
  std::vector<::aidl::android::hardware::identity::RequestDataItem> items;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const RequestNamespace& rhs) const {
    return std::tie(namespaceName, items) != std::tie(rhs.namespaceName, rhs.items);
  }
  inline bool operator<(const RequestNamespace& rhs) const {
    return std::tie(namespaceName, items) < std::tie(rhs.namespaceName, rhs.items);
  }
  inline bool operator<=(const RequestNamespace& rhs) const {
    return std::tie(namespaceName, items) <= std::tie(rhs.namespaceName, rhs.items);
  }
  inline bool operator==(const RequestNamespace& rhs) const {
    return std::tie(namespaceName, items) == std::tie(rhs.namespaceName, rhs.items);
  }
  inline bool operator>(const RequestNamespace& rhs) const {
    return std::tie(namespaceName, items) > std::tie(rhs.namespaceName, rhs.items);
  }
  inline bool operator>=(const RequestNamespace& rhs) const {
    return std::tie(namespaceName, items) >= std::tie(rhs.namespaceName, rhs.items);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "RequestNamespace{";
    os << "namespaceName: " << ::android::internal::ToString(namespaceName);
    os << ", items: " << ::android::internal::ToString(items);
    os << "}";
    return os.str();
  }
};
}  // namespace identity
}  // namespace hardware
}  // namespace android
}  // namespace aidl
