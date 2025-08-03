#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/frameworks/cameraservice/common/CameraMetadataType.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace common {
class VendorTag {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int64_t tagId = 0L;
  std::string tagName;
  ::aidl::android::frameworks::cameraservice::common::CameraMetadataType tagType = ::aidl::android::frameworks::cameraservice::common::CameraMetadataType(0);

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const VendorTag& rhs) const {
    return std::tie(tagId, tagName, tagType) != std::tie(rhs.tagId, rhs.tagName, rhs.tagType);
  }
  inline bool operator<(const VendorTag& rhs) const {
    return std::tie(tagId, tagName, tagType) < std::tie(rhs.tagId, rhs.tagName, rhs.tagType);
  }
  inline bool operator<=(const VendorTag& rhs) const {
    return std::tie(tagId, tagName, tagType) <= std::tie(rhs.tagId, rhs.tagName, rhs.tagType);
  }
  inline bool operator==(const VendorTag& rhs) const {
    return std::tie(tagId, tagName, tagType) == std::tie(rhs.tagId, rhs.tagName, rhs.tagType);
  }
  inline bool operator>(const VendorTag& rhs) const {
    return std::tie(tagId, tagName, tagType) > std::tie(rhs.tagId, rhs.tagName, rhs.tagType);
  }
  inline bool operator>=(const VendorTag& rhs) const {
    return std::tie(tagId, tagName, tagType) >= std::tie(rhs.tagId, rhs.tagName, rhs.tagType);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "VendorTag{";
    os << "tagId: " << ::android::internal::ToString(tagId);
    os << ", tagName: " << ::android::internal::ToString(tagName);
    os << ", tagType: " << ::android::internal::ToString(tagType);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
