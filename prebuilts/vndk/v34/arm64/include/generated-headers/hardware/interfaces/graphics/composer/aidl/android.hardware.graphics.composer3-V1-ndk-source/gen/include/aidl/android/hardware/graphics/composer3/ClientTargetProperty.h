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
#include <aidl/android/hardware/graphics/common/PixelFormat.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
class ClientTargetProperty {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::graphics::common::PixelFormat pixelFormat = ::aidl::android::hardware::graphics::common::PixelFormat(0);
  ::aidl::android::hardware::graphics::common::Dataspace dataspace = ::aidl::android::hardware::graphics::common::Dataspace(0);

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const ClientTargetProperty& rhs) const {
    return std::tie(pixelFormat, dataspace) != std::tie(rhs.pixelFormat, rhs.dataspace);
  }
  inline bool operator<(const ClientTargetProperty& rhs) const {
    return std::tie(pixelFormat, dataspace) < std::tie(rhs.pixelFormat, rhs.dataspace);
  }
  inline bool operator<=(const ClientTargetProperty& rhs) const {
    return std::tie(pixelFormat, dataspace) <= std::tie(rhs.pixelFormat, rhs.dataspace);
  }
  inline bool operator==(const ClientTargetProperty& rhs) const {
    return std::tie(pixelFormat, dataspace) == std::tie(rhs.pixelFormat, rhs.dataspace);
  }
  inline bool operator>(const ClientTargetProperty& rhs) const {
    return std::tie(pixelFormat, dataspace) > std::tie(rhs.pixelFormat, rhs.dataspace);
  }
  inline bool operator>=(const ClientTargetProperty& rhs) const {
    return std::tie(pixelFormat, dataspace) >= std::tie(rhs.pixelFormat, rhs.dataspace);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "ClientTargetProperty{";
    os << "pixelFormat: " << ::android::internal::ToString(pixelFormat);
    os << ", dataspace: " << ::android::internal::ToString(dataspace);
    os << "}";
    return os.str();
  }
};
}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
