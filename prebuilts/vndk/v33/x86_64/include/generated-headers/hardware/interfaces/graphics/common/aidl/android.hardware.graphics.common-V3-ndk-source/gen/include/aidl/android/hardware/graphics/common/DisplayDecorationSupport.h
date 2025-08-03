#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/graphics/common/AlphaInterpretation.h>
#include <aidl/android/hardware/graphics/common/PixelFormat.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace common {
class DisplayDecorationSupport {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  ::aidl::android::hardware::graphics::common::PixelFormat format = ::aidl::android::hardware::graphics::common::PixelFormat(0);
  ::aidl::android::hardware::graphics::common::AlphaInterpretation alphaInterpretation = ::aidl::android::hardware::graphics::common::AlphaInterpretation(0);

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const DisplayDecorationSupport& rhs) const {
    return std::tie(format, alphaInterpretation) != std::tie(rhs.format, rhs.alphaInterpretation);
  }
  inline bool operator<(const DisplayDecorationSupport& rhs) const {
    return std::tie(format, alphaInterpretation) < std::tie(rhs.format, rhs.alphaInterpretation);
  }
  inline bool operator<=(const DisplayDecorationSupport& rhs) const {
    return std::tie(format, alphaInterpretation) <= std::tie(rhs.format, rhs.alphaInterpretation);
  }
  inline bool operator==(const DisplayDecorationSupport& rhs) const {
    return std::tie(format, alphaInterpretation) == std::tie(rhs.format, rhs.alphaInterpretation);
  }
  inline bool operator>(const DisplayDecorationSupport& rhs) const {
    return std::tie(format, alphaInterpretation) > std::tie(rhs.format, rhs.alphaInterpretation);
  }
  inline bool operator>=(const DisplayDecorationSupport& rhs) const {
    return std::tie(format, alphaInterpretation) >= std::tie(rhs.format, rhs.alphaInterpretation);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "DisplayDecorationSupport{";
    os << "format: " << ::android::internal::ToString(format);
    os << ", alphaInterpretation: " << ::android::internal::ToString(alphaInterpretation);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
