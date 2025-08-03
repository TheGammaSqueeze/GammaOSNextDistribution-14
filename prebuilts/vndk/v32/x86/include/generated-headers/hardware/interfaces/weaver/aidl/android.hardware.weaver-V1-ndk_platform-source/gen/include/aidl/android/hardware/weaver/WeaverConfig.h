#pragma once
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
namespace aidl {
namespace android {
namespace hardware {
namespace weaver {
class WeaverConfig {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t slots = 0;
  int32_t keySize = 0;
  int32_t valueSize = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const WeaverConfig& rhs) const {
    return std::tie(slots, keySize, valueSize) != std::tie(rhs.slots, rhs.keySize, rhs.valueSize);
  }
  inline bool operator<(const WeaverConfig& rhs) const {
    return std::tie(slots, keySize, valueSize) < std::tie(rhs.slots, rhs.keySize, rhs.valueSize);
  }
  inline bool operator<=(const WeaverConfig& rhs) const {
    return std::tie(slots, keySize, valueSize) <= std::tie(rhs.slots, rhs.keySize, rhs.valueSize);
  }
  inline bool operator==(const WeaverConfig& rhs) const {
    return std::tie(slots, keySize, valueSize) == std::tie(rhs.slots, rhs.keySize, rhs.valueSize);
  }
  inline bool operator>(const WeaverConfig& rhs) const {
    return std::tie(slots, keySize, valueSize) > std::tie(rhs.slots, rhs.keySize, rhs.valueSize);
  }
  inline bool operator>=(const WeaverConfig& rhs) const {
    return std::tie(slots, keySize, valueSize) >= std::tie(rhs.slots, rhs.keySize, rhs.valueSize);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "WeaverConfig{";
    os << "slots: " << ::android::internal::ToString(slots);
    os << ", keySize: " << ::android::internal::ToString(keySize);
    os << ", valueSize: " << ::android::internal::ToString(valueSize);
    os << "}";
    return os.str();
  }
};
}  // namespace weaver
}  // namespace hardware
}  // namespace android
}  // namespace aidl
