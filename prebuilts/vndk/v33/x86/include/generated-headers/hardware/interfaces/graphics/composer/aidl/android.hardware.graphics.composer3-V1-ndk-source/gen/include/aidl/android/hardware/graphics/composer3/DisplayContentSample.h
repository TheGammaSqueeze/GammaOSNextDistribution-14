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
namespace graphics {
namespace composer3 {
class DisplayContentSample {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int64_t frameCount = 0L;
  std::vector<int64_t> sampleComponent0;
  std::vector<int64_t> sampleComponent1;
  std::vector<int64_t> sampleComponent2;
  std::vector<int64_t> sampleComponent3;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const DisplayContentSample& rhs) const {
    return std::tie(frameCount, sampleComponent0, sampleComponent1, sampleComponent2, sampleComponent3) != std::tie(rhs.frameCount, rhs.sampleComponent0, rhs.sampleComponent1, rhs.sampleComponent2, rhs.sampleComponent3);
  }
  inline bool operator<(const DisplayContentSample& rhs) const {
    return std::tie(frameCount, sampleComponent0, sampleComponent1, sampleComponent2, sampleComponent3) < std::tie(rhs.frameCount, rhs.sampleComponent0, rhs.sampleComponent1, rhs.sampleComponent2, rhs.sampleComponent3);
  }
  inline bool operator<=(const DisplayContentSample& rhs) const {
    return std::tie(frameCount, sampleComponent0, sampleComponent1, sampleComponent2, sampleComponent3) <= std::tie(rhs.frameCount, rhs.sampleComponent0, rhs.sampleComponent1, rhs.sampleComponent2, rhs.sampleComponent3);
  }
  inline bool operator==(const DisplayContentSample& rhs) const {
    return std::tie(frameCount, sampleComponent0, sampleComponent1, sampleComponent2, sampleComponent3) == std::tie(rhs.frameCount, rhs.sampleComponent0, rhs.sampleComponent1, rhs.sampleComponent2, rhs.sampleComponent3);
  }
  inline bool operator>(const DisplayContentSample& rhs) const {
    return std::tie(frameCount, sampleComponent0, sampleComponent1, sampleComponent2, sampleComponent3) > std::tie(rhs.frameCount, rhs.sampleComponent0, rhs.sampleComponent1, rhs.sampleComponent2, rhs.sampleComponent3);
  }
  inline bool operator>=(const DisplayContentSample& rhs) const {
    return std::tie(frameCount, sampleComponent0, sampleComponent1, sampleComponent2, sampleComponent3) >= std::tie(rhs.frameCount, rhs.sampleComponent0, rhs.sampleComponent1, rhs.sampleComponent2, rhs.sampleComponent3);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "DisplayContentSample{";
    os << "frameCount: " << ::android::internal::ToString(frameCount);
    os << ", sampleComponent0: " << ::android::internal::ToString(sampleComponent0);
    os << ", sampleComponent1: " << ::android::internal::ToString(sampleComponent1);
    os << ", sampleComponent2: " << ::android::internal::ToString(sampleComponent2);
    os << ", sampleComponent3: " << ::android::internal::ToString(sampleComponent3);
    os << "}";
    return os.str();
  }
};
}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
