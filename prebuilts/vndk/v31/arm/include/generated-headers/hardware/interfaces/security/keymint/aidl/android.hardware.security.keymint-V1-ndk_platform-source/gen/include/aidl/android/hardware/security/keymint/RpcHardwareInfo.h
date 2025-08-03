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
namespace security {
namespace keymint {
class RpcHardwareInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t versionNumber = 0;
  std::string rpcAuthorName;
  int32_t supportedEekCurve = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const RpcHardwareInfo& rhs) const {
    return std::tie(versionNumber, rpcAuthorName, supportedEekCurve) != std::tie(rhs.versionNumber, rhs.rpcAuthorName, rhs.supportedEekCurve);
  }
  inline bool operator<(const RpcHardwareInfo& rhs) const {
    return std::tie(versionNumber, rpcAuthorName, supportedEekCurve) < std::tie(rhs.versionNumber, rhs.rpcAuthorName, rhs.supportedEekCurve);
  }
  inline bool operator<=(const RpcHardwareInfo& rhs) const {
    return std::tie(versionNumber, rpcAuthorName, supportedEekCurve) <= std::tie(rhs.versionNumber, rhs.rpcAuthorName, rhs.supportedEekCurve);
  }
  inline bool operator==(const RpcHardwareInfo& rhs) const {
    return std::tie(versionNumber, rpcAuthorName, supportedEekCurve) == std::tie(rhs.versionNumber, rhs.rpcAuthorName, rhs.supportedEekCurve);
  }
  inline bool operator>(const RpcHardwareInfo& rhs) const {
    return std::tie(versionNumber, rpcAuthorName, supportedEekCurve) > std::tie(rhs.versionNumber, rhs.rpcAuthorName, rhs.supportedEekCurve);
  }
  inline bool operator>=(const RpcHardwareInfo& rhs) const {
    return std::tie(versionNumber, rpcAuthorName, supportedEekCurve) >= std::tie(rhs.versionNumber, rhs.rpcAuthorName, rhs.supportedEekCurve);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { CURVE_NONE = 0 };
  enum : int32_t { CURVE_P256 = 1 };
  enum : int32_t { CURVE_25519 = 2 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "RpcHardwareInfo{";
    os << "versionNumber: " << ::android::internal::ToString(versionNumber);
    os << ", rpcAuthorName: " << ::android::internal::ToString(rpcAuthorName);
    os << ", supportedEekCurve: " << ::android::internal::ToString(supportedEekCurve);
    os << "}";
    return os.str();
  }
};
}  // namespace keymint
}  // namespace security
}  // namespace hardware
}  // namespace android
}  // namespace aidl
