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
namespace sim {
class IccIo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t command = 0;
  int32_t fileId = 0;
  std::string path;
  int32_t p1 = 0;
  int32_t p2 = 0;
  int32_t p3 = 0;
  std::string data;
  std::string pin2;
  std::string aid;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const IccIo& rhs) const {
    return std::tie(command, fileId, path, p1, p2, p3, data, pin2, aid) != std::tie(rhs.command, rhs.fileId, rhs.path, rhs.p1, rhs.p2, rhs.p3, rhs.data, rhs.pin2, rhs.aid);
  }
  inline bool operator<(const IccIo& rhs) const {
    return std::tie(command, fileId, path, p1, p2, p3, data, pin2, aid) < std::tie(rhs.command, rhs.fileId, rhs.path, rhs.p1, rhs.p2, rhs.p3, rhs.data, rhs.pin2, rhs.aid);
  }
  inline bool operator<=(const IccIo& rhs) const {
    return std::tie(command, fileId, path, p1, p2, p3, data, pin2, aid) <= std::tie(rhs.command, rhs.fileId, rhs.path, rhs.p1, rhs.p2, rhs.p3, rhs.data, rhs.pin2, rhs.aid);
  }
  inline bool operator==(const IccIo& rhs) const {
    return std::tie(command, fileId, path, p1, p2, p3, data, pin2, aid) == std::tie(rhs.command, rhs.fileId, rhs.path, rhs.p1, rhs.p2, rhs.p3, rhs.data, rhs.pin2, rhs.aid);
  }
  inline bool operator>(const IccIo& rhs) const {
    return std::tie(command, fileId, path, p1, p2, p3, data, pin2, aid) > std::tie(rhs.command, rhs.fileId, rhs.path, rhs.p1, rhs.p2, rhs.p3, rhs.data, rhs.pin2, rhs.aid);
  }
  inline bool operator>=(const IccIo& rhs) const {
    return std::tie(command, fileId, path, p1, p2, p3, data, pin2, aid) >= std::tie(rhs.command, rhs.fileId, rhs.path, rhs.p1, rhs.p2, rhs.p3, rhs.data, rhs.pin2, rhs.aid);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "IccIo{";
    os << "command: " << ::android::internal::ToString(command);
    os << ", fileId: " << ::android::internal::ToString(fileId);
    os << ", path: " << ::android::internal::ToString(path);
    os << ", p1: " << ::android::internal::ToString(p1);
    os << ", p2: " << ::android::internal::ToString(p2);
    os << ", p3: " << ::android::internal::ToString(p3);
    os << ", data: " << ::android::internal::ToString(data);
    os << ", pin2: " << ::android::internal::ToString(pin2);
    os << ", aid: " << ::android::internal::ToString(aid);
    os << "}";
    return os.str();
  }
};
}  // namespace sim
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
