#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/camera/common/VendorTag.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace common {
class VendorTagSection {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string sectionName;
  std::vector<::aidl::android::hardware::camera::common::VendorTag> tags;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const VendorTagSection& rhs) const {
    return std::tie(sectionName, tags) != std::tie(rhs.sectionName, rhs.tags);
  }
  inline bool operator<(const VendorTagSection& rhs) const {
    return std::tie(sectionName, tags) < std::tie(rhs.sectionName, rhs.tags);
  }
  inline bool operator<=(const VendorTagSection& rhs) const {
    return std::tie(sectionName, tags) <= std::tie(rhs.sectionName, rhs.tags);
  }
  inline bool operator==(const VendorTagSection& rhs) const {
    return std::tie(sectionName, tags) == std::tie(rhs.sectionName, rhs.tags);
  }
  inline bool operator>(const VendorTagSection& rhs) const {
    return std::tie(sectionName, tags) > std::tie(rhs.sectionName, rhs.tags);
  }
  inline bool operator>=(const VendorTagSection& rhs) const {
    return std::tie(sectionName, tags) >= std::tie(rhs.sectionName, rhs.tags);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "VendorTagSection{";
    os << "sectionName: " << ::android::internal::ToString(sectionName);
    os << ", tags: " << ::android::internal::ToString(tags);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
