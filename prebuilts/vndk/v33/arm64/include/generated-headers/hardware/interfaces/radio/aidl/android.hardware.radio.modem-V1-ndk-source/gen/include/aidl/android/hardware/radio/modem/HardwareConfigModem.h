#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/RadioTechnology.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace modem {
class HardwareConfigModem {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t rilModel = 0;
  ::aidl::android::hardware::radio::RadioTechnology rat = ::aidl::android::hardware::radio::RadioTechnology(0);
  int32_t maxVoiceCalls = 0;
  int32_t maxDataCalls = 0;
  int32_t maxStandby = 0;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const HardwareConfigModem& rhs) const {
    return std::tie(rilModel, rat, maxVoiceCalls, maxDataCalls, maxStandby) != std::tie(rhs.rilModel, rhs.rat, rhs.maxVoiceCalls, rhs.maxDataCalls, rhs.maxStandby);
  }
  inline bool operator<(const HardwareConfigModem& rhs) const {
    return std::tie(rilModel, rat, maxVoiceCalls, maxDataCalls, maxStandby) < std::tie(rhs.rilModel, rhs.rat, rhs.maxVoiceCalls, rhs.maxDataCalls, rhs.maxStandby);
  }
  inline bool operator<=(const HardwareConfigModem& rhs) const {
    return std::tie(rilModel, rat, maxVoiceCalls, maxDataCalls, maxStandby) <= std::tie(rhs.rilModel, rhs.rat, rhs.maxVoiceCalls, rhs.maxDataCalls, rhs.maxStandby);
  }
  inline bool operator==(const HardwareConfigModem& rhs) const {
    return std::tie(rilModel, rat, maxVoiceCalls, maxDataCalls, maxStandby) == std::tie(rhs.rilModel, rhs.rat, rhs.maxVoiceCalls, rhs.maxDataCalls, rhs.maxStandby);
  }
  inline bool operator>(const HardwareConfigModem& rhs) const {
    return std::tie(rilModel, rat, maxVoiceCalls, maxDataCalls, maxStandby) > std::tie(rhs.rilModel, rhs.rat, rhs.maxVoiceCalls, rhs.maxDataCalls, rhs.maxStandby);
  }
  inline bool operator>=(const HardwareConfigModem& rhs) const {
    return std::tie(rilModel, rat, maxVoiceCalls, maxDataCalls, maxStandby) >= std::tie(rhs.rilModel, rhs.rat, rhs.maxVoiceCalls, rhs.maxDataCalls, rhs.maxStandby);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "HardwareConfigModem{";
    os << "rilModel: " << ::android::internal::ToString(rilModel);
    os << ", rat: " << ::android::internal::ToString(rat);
    os << ", maxVoiceCalls: " << ::android::internal::ToString(maxVoiceCalls);
    os << ", maxDataCalls: " << ::android::internal::ToString(maxDataCalls);
    os << ", maxStandby: " << ::android::internal::ToString(maxStandby);
    os << "}";
    return os.str();
  }
};
}  // namespace modem
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
