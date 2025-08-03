#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
class RadioConst {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;


  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const RadioConst&) const {
    return std::tie() != std::tie();
  }
  inline bool operator<(const RadioConst&) const {
    return std::tie() < std::tie();
  }
  inline bool operator<=(const RadioConst&) const {
    return std::tie() <= std::tie();
  }
  inline bool operator==(const RadioConst&) const {
    return std::tie() == std::tie();
  }
  inline bool operator>(const RadioConst&) const {
    return std::tie() > std::tie();
  }
  inline bool operator>=(const RadioConst&) const {
    return std::tie() >= std::tie();
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { MAX_RILDS = 3 };
  enum : int32_t { MAX_UUID_LENGTH = 64 };
  enum : int32_t { CARD_MAX_APPS = 8 };
  enum : int32_t { P2_CONSTANT_NO_P2 = -1 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "RadioConst{";
    os << "}";
    return os.str();
  }
};
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
