#pragma once

#include <array>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_enums.h>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/graphics/composer3/PresentOrValidate.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
class PresentOrValidate {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  enum class Result : int8_t {
    Validated = 0,
    Presented = 1,
  };

  int64_t display = 0L;
  ::aidl::android::hardware::graphics::composer3::PresentOrValidate::Result result = ::aidl::android::hardware::graphics::composer3::PresentOrValidate::Result(0);

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const PresentOrValidate& rhs) const {
    return std::tie(display, result) != std::tie(rhs.display, rhs.result);
  }
  inline bool operator<(const PresentOrValidate& rhs) const {
    return std::tie(display, result) < std::tie(rhs.display, rhs.result);
  }
  inline bool operator<=(const PresentOrValidate& rhs) const {
    return std::tie(display, result) <= std::tie(rhs.display, rhs.result);
  }
  inline bool operator==(const PresentOrValidate& rhs) const {
    return std::tie(display, result) == std::tie(rhs.display, rhs.result);
  }
  inline bool operator>(const PresentOrValidate& rhs) const {
    return std::tie(display, result) > std::tie(rhs.display, rhs.result);
  }
  inline bool operator>=(const PresentOrValidate& rhs) const {
    return std::tie(display, result) >= std::tie(rhs.display, rhs.result);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "PresentOrValidate{";
    os << "display: " << ::android::internal::ToString(display);
    os << ", result: " << ::android::internal::ToString(result);
    os << "}";
    return os.str();
  }
};
}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
[[nodiscard]] static inline std::string toString(PresentOrValidate::Result val) {
  switch(val) {
  case PresentOrValidate::Result::Validated:
    return "Validated";
  case PresentOrValidate::Result::Presented:
    return "Presented";
  default:
    return std::to_string(static_cast<int8_t>(val));
  }
}
}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::graphics::composer3::PresentOrValidate::Result, 2> enum_values<aidl::android::hardware::graphics::composer3::PresentOrValidate::Result> = {
  aidl::android::hardware::graphics::composer3::PresentOrValidate::Result::Validated,
  aidl::android::hardware::graphics::composer3::PresentOrValidate::Result::Presented,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
