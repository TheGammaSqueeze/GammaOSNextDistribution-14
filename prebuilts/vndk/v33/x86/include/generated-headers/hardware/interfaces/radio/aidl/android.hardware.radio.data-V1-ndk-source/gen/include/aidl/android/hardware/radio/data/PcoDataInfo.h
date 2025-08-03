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
class PcoDataInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t cid = 0;
  std::string bearerProto;
  int32_t pcoId = 0;
  std::vector<uint8_t> contents;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const PcoDataInfo& rhs) const {
    return std::tie(cid, bearerProto, pcoId, contents) != std::tie(rhs.cid, rhs.bearerProto, rhs.pcoId, rhs.contents);
  }
  inline bool operator<(const PcoDataInfo& rhs) const {
    return std::tie(cid, bearerProto, pcoId, contents) < std::tie(rhs.cid, rhs.bearerProto, rhs.pcoId, rhs.contents);
  }
  inline bool operator<=(const PcoDataInfo& rhs) const {
    return std::tie(cid, bearerProto, pcoId, contents) <= std::tie(rhs.cid, rhs.bearerProto, rhs.pcoId, rhs.contents);
  }
  inline bool operator==(const PcoDataInfo& rhs) const {
    return std::tie(cid, bearerProto, pcoId, contents) == std::tie(rhs.cid, rhs.bearerProto, rhs.pcoId, rhs.contents);
  }
  inline bool operator>(const PcoDataInfo& rhs) const {
    return std::tie(cid, bearerProto, pcoId, contents) > std::tie(rhs.cid, rhs.bearerProto, rhs.pcoId, rhs.contents);
  }
  inline bool operator>=(const PcoDataInfo& rhs) const {
    return std::tie(cid, bearerProto, pcoId, contents) >= std::tie(rhs.cid, rhs.bearerProto, rhs.pcoId, rhs.contents);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "PcoDataInfo{";
    os << "cid: " << ::android::internal::ToString(cid);
    os << ", bearerProto: " << ::android::internal::ToString(bearerProto);
    os << ", pcoId: " << ::android::internal::ToString(pcoId);
    os << ", contents: " << ::android::internal::ToString(contents);
    os << "}";
    return os.str();
  }
};
}  // namespace data
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
