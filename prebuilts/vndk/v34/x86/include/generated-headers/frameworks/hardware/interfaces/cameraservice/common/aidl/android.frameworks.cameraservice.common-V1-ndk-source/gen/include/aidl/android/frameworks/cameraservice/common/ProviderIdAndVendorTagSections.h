#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/frameworks/cameraservice/common/VendorTagSection.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl::android::frameworks::cameraservice::common {
class VendorTagSection;
}  // namespace aidl::android::frameworks::cameraservice::common
namespace aidl {
namespace android {
namespace frameworks {
namespace cameraservice {
namespace common {
class ProviderIdAndVendorTagSections {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int64_t providerId = 0L;
  std::vector<::aidl::android::frameworks::cameraservice::common::VendorTagSection> vendorTagSections;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ProviderIdAndVendorTagSections& rhs) const {
    return std::tie(providerId, vendorTagSections) != std::tie(rhs.providerId, rhs.vendorTagSections);
  }
  inline bool operator<(const ProviderIdAndVendorTagSections& rhs) const {
    return std::tie(providerId, vendorTagSections) < std::tie(rhs.providerId, rhs.vendorTagSections);
  }
  inline bool operator<=(const ProviderIdAndVendorTagSections& rhs) const {
    return std::tie(providerId, vendorTagSections) <= std::tie(rhs.providerId, rhs.vendorTagSections);
  }
  inline bool operator==(const ProviderIdAndVendorTagSections& rhs) const {
    return std::tie(providerId, vendorTagSections) == std::tie(rhs.providerId, rhs.vendorTagSections);
  }
  inline bool operator>(const ProviderIdAndVendorTagSections& rhs) const {
    return std::tie(providerId, vendorTagSections) > std::tie(rhs.providerId, rhs.vendorTagSections);
  }
  inline bool operator>=(const ProviderIdAndVendorTagSections& rhs) const {
    return std::tie(providerId, vendorTagSections) >= std::tie(rhs.providerId, rhs.vendorTagSections);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "ProviderIdAndVendorTagSections{";
    os << "providerId: " << ::android::internal::ToString(providerId);
    os << ", vendorTagSections: " << ::android::internal::ToString(vendorTagSections);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace cameraservice
}  // namespace frameworks
}  // namespace android
}  // namespace aidl
