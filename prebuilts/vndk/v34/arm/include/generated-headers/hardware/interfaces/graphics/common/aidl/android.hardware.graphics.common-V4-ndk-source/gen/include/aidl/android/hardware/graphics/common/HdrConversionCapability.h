#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/graphics/common/Hdr.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace common {
class HdrConversionCapability {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::graphics::common::Hdr sourceType = ::aidl::android::hardware::graphics::common::Hdr(0);
  ::aidl::android::hardware::graphics::common::Hdr outputType = ::aidl::android::hardware::graphics::common::Hdr(0);
  bool addsLatency = false;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const HdrConversionCapability& rhs) const {
    return std::tie(sourceType, outputType, addsLatency) != std::tie(rhs.sourceType, rhs.outputType, rhs.addsLatency);
  }
  inline bool operator<(const HdrConversionCapability& rhs) const {
    return std::tie(sourceType, outputType, addsLatency) < std::tie(rhs.sourceType, rhs.outputType, rhs.addsLatency);
  }
  inline bool operator<=(const HdrConversionCapability& rhs) const {
    return std::tie(sourceType, outputType, addsLatency) <= std::tie(rhs.sourceType, rhs.outputType, rhs.addsLatency);
  }
  inline bool operator==(const HdrConversionCapability& rhs) const {
    return std::tie(sourceType, outputType, addsLatency) == std::tie(rhs.sourceType, rhs.outputType, rhs.addsLatency);
  }
  inline bool operator>(const HdrConversionCapability& rhs) const {
    return std::tie(sourceType, outputType, addsLatency) > std::tie(rhs.sourceType, rhs.outputType, rhs.addsLatency);
  }
  inline bool operator>=(const HdrConversionCapability& rhs) const {
    return std::tie(sourceType, outputType, addsLatency) >= std::tie(rhs.sourceType, rhs.outputType, rhs.addsLatency);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "HdrConversionCapability{";
    os << "sourceType: " << ::android::internal::ToString(sourceType);
    os << ", outputType: " << ::android::internal::ToString(outputType);
    os << ", addsLatency: " << ::android::internal::ToString(addsLatency);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
