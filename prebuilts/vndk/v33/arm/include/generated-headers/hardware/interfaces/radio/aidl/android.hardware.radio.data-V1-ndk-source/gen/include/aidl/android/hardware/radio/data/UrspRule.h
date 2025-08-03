#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/data/RouteSelectionDescriptor.h>
#include <aidl/android/hardware/radio/data/TrafficDescriptor.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace data {
class UrspRule {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t precedence = 0;
  std::vector<::aidl::android::hardware::radio::data::TrafficDescriptor> trafficDescriptors;
  std::vector<::aidl::android::hardware::radio::data::RouteSelectionDescriptor> routeSelectionDescriptor;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const UrspRule& rhs) const {
    return std::tie(precedence, trafficDescriptors, routeSelectionDescriptor) != std::tie(rhs.precedence, rhs.trafficDescriptors, rhs.routeSelectionDescriptor);
  }
  inline bool operator<(const UrspRule& rhs) const {
    return std::tie(precedence, trafficDescriptors, routeSelectionDescriptor) < std::tie(rhs.precedence, rhs.trafficDescriptors, rhs.routeSelectionDescriptor);
  }
  inline bool operator<=(const UrspRule& rhs) const {
    return std::tie(precedence, trafficDescriptors, routeSelectionDescriptor) <= std::tie(rhs.precedence, rhs.trafficDescriptors, rhs.routeSelectionDescriptor);
  }
  inline bool operator==(const UrspRule& rhs) const {
    return std::tie(precedence, trafficDescriptors, routeSelectionDescriptor) == std::tie(rhs.precedence, rhs.trafficDescriptors, rhs.routeSelectionDescriptor);
  }
  inline bool operator>(const UrspRule& rhs) const {
    return std::tie(precedence, trafficDescriptors, routeSelectionDescriptor) > std::tie(rhs.precedence, rhs.trafficDescriptors, rhs.routeSelectionDescriptor);
  }
  inline bool operator>=(const UrspRule& rhs) const {
    return std::tie(precedence, trafficDescriptors, routeSelectionDescriptor) >= std::tie(rhs.precedence, rhs.trafficDescriptors, rhs.routeSelectionDescriptor);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "UrspRule{";
    os << "precedence: " << ::android::internal::ToString(precedence);
    os << ", trafficDescriptors: " << ::android::internal::ToString(trafficDescriptors);
    os << ", routeSelectionDescriptor: " << ::android::internal::ToString(routeSelectionDescriptor);
    os << "}";
    return os.str();
  }
};
}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
