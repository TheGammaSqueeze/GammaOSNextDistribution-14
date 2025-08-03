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
#include <aidl/android/hardware/identity/RequestDataItem.h>
namespace aidl {
namespace android {
namespace hardware {
namespace identity {
class RequestNamespace {
public:
  static const char* descriptor;

  std::string namespaceName;
  std::vector<::aidl::android::hardware::identity::RequestDataItem> items;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;
};
}  // namespace identity
}  // namespace hardware
}  // namespace android
}  // namespace aidl
