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
namespace network {
class OperatorInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::string alphaLong;
  std::string alphaShort;
  std::string operatorNumeric;
  int32_t status = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const OperatorInfo& rhs) const {
    return std::tie(alphaLong, alphaShort, operatorNumeric, status) != std::tie(rhs.alphaLong, rhs.alphaShort, rhs.operatorNumeric, rhs.status);
  }
  inline bool operator<(const OperatorInfo& rhs) const {
    return std::tie(alphaLong, alphaShort, operatorNumeric, status) < std::tie(rhs.alphaLong, rhs.alphaShort, rhs.operatorNumeric, rhs.status);
  }
  inline bool operator<=(const OperatorInfo& rhs) const {
    return std::tie(alphaLong, alphaShort, operatorNumeric, status) <= std::tie(rhs.alphaLong, rhs.alphaShort, rhs.operatorNumeric, rhs.status);
  }
  inline bool operator==(const OperatorInfo& rhs) const {
    return std::tie(alphaLong, alphaShort, operatorNumeric, status) == std::tie(rhs.alphaLong, rhs.alphaShort, rhs.operatorNumeric, rhs.status);
  }
  inline bool operator>(const OperatorInfo& rhs) const {
    return std::tie(alphaLong, alphaShort, operatorNumeric, status) > std::tie(rhs.alphaLong, rhs.alphaShort, rhs.operatorNumeric, rhs.status);
  }
  inline bool operator>=(const OperatorInfo& rhs) const {
    return std::tie(alphaLong, alphaShort, operatorNumeric, status) >= std::tie(rhs.alphaLong, rhs.alphaShort, rhs.operatorNumeric, rhs.status);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { STATUS_UNKNOWN = 0 };
  enum : int32_t { STATUS_AVAILABLE = 1 };
  enum : int32_t { STATUS_CURRENT = 2 };
  enum : int32_t { STATUS_FORBIDDEN = 3 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "OperatorInfo{";
    os << "alphaLong: " << ::android::internal::ToString(alphaLong);
    os << ", alphaShort: " << ::android::internal::ToString(alphaShort);
    os << ", operatorNumeric: " << ::android::internal::ToString(operatorNumeric);
    os << ", status: " << ::android::internal::ToString(status);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
