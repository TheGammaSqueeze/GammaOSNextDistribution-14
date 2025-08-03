#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/voice/UusInfo.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace voice {
class Dial {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string address;
  int32_t clir = 0;
  std::vector<::aidl::android::hardware::radio::voice::UusInfo> uusInfo;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const Dial& rhs) const {
    return std::tie(address, clir, uusInfo) != std::tie(rhs.address, rhs.clir, rhs.uusInfo);
  }
  inline bool operator<(const Dial& rhs) const {
    return std::tie(address, clir, uusInfo) < std::tie(rhs.address, rhs.clir, rhs.uusInfo);
  }
  inline bool operator<=(const Dial& rhs) const {
    return std::tie(address, clir, uusInfo) <= std::tie(rhs.address, rhs.clir, rhs.uusInfo);
  }
  inline bool operator==(const Dial& rhs) const {
    return std::tie(address, clir, uusInfo) == std::tie(rhs.address, rhs.clir, rhs.uusInfo);
  }
  inline bool operator>(const Dial& rhs) const {
    return std::tie(address, clir, uusInfo) > std::tie(rhs.address, rhs.clir, rhs.uusInfo);
  }
  inline bool operator>=(const Dial& rhs) const {
    return std::tie(address, clir, uusInfo) >= std::tie(rhs.address, rhs.clir, rhs.uusInfo);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { CLIR_DEFAULT = 0 };
  enum : int32_t { CLIR_INVOCATION = 1 };
  enum : int32_t { CLIR_SUPPRESSION = 2 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "Dial{";
    os << "address: " << ::android::internal::ToString(address);
    os << ", clir: " << ::android::internal::ToString(clir);
    os << ", uusInfo: " << ::android::internal::ToString(uusInfo);
    os << "}";
    return os.str();
  }
};
}  // namespace voice
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
