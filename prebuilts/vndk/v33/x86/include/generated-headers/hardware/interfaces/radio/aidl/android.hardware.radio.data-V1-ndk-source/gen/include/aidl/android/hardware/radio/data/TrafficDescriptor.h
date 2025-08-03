#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/data/OsAppId.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace data {
class TrafficDescriptor {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::optional<std::string> dnn;
  std::optional<::aidl::android::hardware::radio::data::OsAppId> osAppId;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const TrafficDescriptor& rhs) const {
    return std::tie(dnn, osAppId) != std::tie(rhs.dnn, rhs.osAppId);
  }
  inline bool operator<(const TrafficDescriptor& rhs) const {
    return std::tie(dnn, osAppId) < std::tie(rhs.dnn, rhs.osAppId);
  }
  inline bool operator<=(const TrafficDescriptor& rhs) const {
    return std::tie(dnn, osAppId) <= std::tie(rhs.dnn, rhs.osAppId);
  }
  inline bool operator==(const TrafficDescriptor& rhs) const {
    return std::tie(dnn, osAppId) == std::tie(rhs.dnn, rhs.osAppId);
  }
  inline bool operator>(const TrafficDescriptor& rhs) const {
    return std::tie(dnn, osAppId) > std::tie(rhs.dnn, rhs.osAppId);
  }
  inline bool operator>=(const TrafficDescriptor& rhs) const {
    return std::tie(dnn, osAppId) >= std::tie(rhs.dnn, rhs.osAppId);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "TrafficDescriptor{";
    os << "dnn: " << ::android::internal::ToString(dnn);
    os << ", osAppId: " << ::android::internal::ToString(osAppId);
    os << "}";
    return os.str();
  }
};
}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
