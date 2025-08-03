#pragma once
#include <android/binder_interface_utils.h>

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
namespace identity {
class RequestDataItem {
public:
  static const char* descriptor;

  std::string name;
  int64_t size = {};
  std::vector<int32_t> accessControlProfileIds;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;
};
}  // namespace identity
}  // namespace hardware
}  // namespace android
}  // namespace aidl
