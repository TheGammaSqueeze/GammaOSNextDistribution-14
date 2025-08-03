#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/graphics/common/Dataspace.h>
#include <aidl/android/hardware/graphics/common/Rect.h>
#include <aidl/android/hardware/graphics/composer3/Buffer.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
class ClientTarget {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::graphics::composer3::Buffer buffer;
  ::aidl::android::hardware::graphics::common::Dataspace dataspace = ::aidl::android::hardware::graphics::common::Dataspace(0);
  std::vector<::aidl::android::hardware::graphics::common::Rect> damage;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ClientTarget& rhs) const {
    return std::tie(buffer, dataspace, damage) != std::tie(rhs.buffer, rhs.dataspace, rhs.damage);
  }
  inline bool operator<(const ClientTarget& rhs) const {
    return std::tie(buffer, dataspace, damage) < std::tie(rhs.buffer, rhs.dataspace, rhs.damage);
  }
  inline bool operator<=(const ClientTarget& rhs) const {
    return std::tie(buffer, dataspace, damage) <= std::tie(rhs.buffer, rhs.dataspace, rhs.damage);
  }
  inline bool operator==(const ClientTarget& rhs) const {
    return std::tie(buffer, dataspace, damage) == std::tie(rhs.buffer, rhs.dataspace, rhs.damage);
  }
  inline bool operator>(const ClientTarget& rhs) const {
    return std::tie(buffer, dataspace, damage) > std::tie(rhs.buffer, rhs.dataspace, rhs.damage);
  }
  inline bool operator>=(const ClientTarget& rhs) const {
    return std::tie(buffer, dataspace, damage) >= std::tie(rhs.buffer, rhs.dataspace, rhs.damage);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "ClientTarget{";
    os << "buffer: " << ::android::internal::ToString(buffer);
    os << ", dataspace: " << ::android::internal::ToString(dataspace);
    os << ", damage: " << ::android::internal::ToString(damage);
    os << "}";
    return os.str();
  }
};
}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
