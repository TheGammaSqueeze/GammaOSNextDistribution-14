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
namespace data {
class SliceInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int8_t sliceServiceType = 0;
  int32_t sliceDifferentiator = 0;
  int8_t mappedHplmnSst = 0;
  int32_t mappedHplmnSd = 0;
  int8_t status = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const SliceInfo& rhs) const {
    return std::tie(sliceServiceType, sliceDifferentiator, mappedHplmnSst, mappedHplmnSd, status) != std::tie(rhs.sliceServiceType, rhs.sliceDifferentiator, rhs.mappedHplmnSst, rhs.mappedHplmnSd, rhs.status);
  }
  inline bool operator<(const SliceInfo& rhs) const {
    return std::tie(sliceServiceType, sliceDifferentiator, mappedHplmnSst, mappedHplmnSd, status) < std::tie(rhs.sliceServiceType, rhs.sliceDifferentiator, rhs.mappedHplmnSst, rhs.mappedHplmnSd, rhs.status);
  }
  inline bool operator<=(const SliceInfo& rhs) const {
    return std::tie(sliceServiceType, sliceDifferentiator, mappedHplmnSst, mappedHplmnSd, status) <= std::tie(rhs.sliceServiceType, rhs.sliceDifferentiator, rhs.mappedHplmnSst, rhs.mappedHplmnSd, rhs.status);
  }
  inline bool operator==(const SliceInfo& rhs) const {
    return std::tie(sliceServiceType, sliceDifferentiator, mappedHplmnSst, mappedHplmnSd, status) == std::tie(rhs.sliceServiceType, rhs.sliceDifferentiator, rhs.mappedHplmnSst, rhs.mappedHplmnSd, rhs.status);
  }
  inline bool operator>(const SliceInfo& rhs) const {
    return std::tie(sliceServiceType, sliceDifferentiator, mappedHplmnSst, mappedHplmnSd, status) > std::tie(rhs.sliceServiceType, rhs.sliceDifferentiator, rhs.mappedHplmnSst, rhs.mappedHplmnSd, rhs.status);
  }
  inline bool operator>=(const SliceInfo& rhs) const {
    return std::tie(sliceServiceType, sliceDifferentiator, mappedHplmnSst, mappedHplmnSd, status) >= std::tie(rhs.sliceServiceType, rhs.sliceDifferentiator, rhs.mappedHplmnSst, rhs.mappedHplmnSd, rhs.status);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int8_t { SERVICE_TYPE_NONE = 0 };
  enum : int8_t { SERVICE_TYPE_EMBB = 1 };
  enum : int8_t { SERVICE_TYPE_URLLC = 2 };
  enum : int8_t { SERVICE_TYPE_MIOT = 3 };
  enum : int8_t { STATUS_UNKNOWN = 0 };
  enum : int8_t { STATUS_CONFIGURED = 1 };
  enum : int8_t { STATUS_ALLOWED = 2 };
  enum : int8_t { STATUS_REJECTED_NOT_AVAILABLE_IN_PLMN = 3 };
  enum : int8_t { STATUS_REJECTED_NOT_AVAILABLE_IN_REG_AREA = 4 };
  enum : int8_t { STATUS_DEFAULT_CONFIGURED = 5 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "SliceInfo{";
    os << "sliceServiceType: " << ::android::internal::ToString(sliceServiceType);
    os << ", sliceDifferentiator: " << ::android::internal::ToString(sliceDifferentiator);
    os << ", mappedHplmnSst: " << ::android::internal::ToString(mappedHplmnSst);
    os << ", mappedHplmnSd: " << ::android::internal::ToString(mappedHplmnSd);
    os << ", status: " << ::android::internal::ToString(status);
    os << "}";
    return os.str();
  }
};
}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
