#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/radio/network/RadioAccessSpecifier.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace radio {
namespace network {
class NetworkScanRequest {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t type = 0;
  int32_t interval = 0;
  std::vector<::aidl::android::hardware::radio::network::RadioAccessSpecifier> specifiers;
  int32_t maxSearchTime = 0;
  bool incrementalResults = false;
  int32_t incrementalResultsPeriodicity = 0;
  std::vector<std::string> mccMncs;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const NetworkScanRequest& rhs) const {
    return std::tie(type, interval, specifiers, maxSearchTime, incrementalResults, incrementalResultsPeriodicity, mccMncs) != std::tie(rhs.type, rhs.interval, rhs.specifiers, rhs.maxSearchTime, rhs.incrementalResults, rhs.incrementalResultsPeriodicity, rhs.mccMncs);
  }
  inline bool operator<(const NetworkScanRequest& rhs) const {
    return std::tie(type, interval, specifiers, maxSearchTime, incrementalResults, incrementalResultsPeriodicity, mccMncs) < std::tie(rhs.type, rhs.interval, rhs.specifiers, rhs.maxSearchTime, rhs.incrementalResults, rhs.incrementalResultsPeriodicity, rhs.mccMncs);
  }
  inline bool operator<=(const NetworkScanRequest& rhs) const {
    return std::tie(type, interval, specifiers, maxSearchTime, incrementalResults, incrementalResultsPeriodicity, mccMncs) <= std::tie(rhs.type, rhs.interval, rhs.specifiers, rhs.maxSearchTime, rhs.incrementalResults, rhs.incrementalResultsPeriodicity, rhs.mccMncs);
  }
  inline bool operator==(const NetworkScanRequest& rhs) const {
    return std::tie(type, interval, specifiers, maxSearchTime, incrementalResults, incrementalResultsPeriodicity, mccMncs) == std::tie(rhs.type, rhs.interval, rhs.specifiers, rhs.maxSearchTime, rhs.incrementalResults, rhs.incrementalResultsPeriodicity, rhs.mccMncs);
  }
  inline bool operator>(const NetworkScanRequest& rhs) const {
    return std::tie(type, interval, specifiers, maxSearchTime, incrementalResults, incrementalResultsPeriodicity, mccMncs) > std::tie(rhs.type, rhs.interval, rhs.specifiers, rhs.maxSearchTime, rhs.incrementalResults, rhs.incrementalResultsPeriodicity, rhs.mccMncs);
  }
  inline bool operator>=(const NetworkScanRequest& rhs) const {
    return std::tie(type, interval, specifiers, maxSearchTime, incrementalResults, incrementalResultsPeriodicity, mccMncs) >= std::tie(rhs.type, rhs.interval, rhs.specifiers, rhs.maxSearchTime, rhs.incrementalResults, rhs.incrementalResultsPeriodicity, rhs.mccMncs);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  enum : int32_t { RADIO_ACCESS_SPECIFIER_MAX_SIZE = 8 };
  enum : int32_t { INCREMENTAL_RESULTS_PREIODICITY_RANGE_MIN = 1 };
  enum : int32_t { INCREMENTAL_RESULTS_PREIODICITY_RANGE_MAX = 10 };
  enum : int32_t { MAX_SEARCH_TIME_RANGE_MIN = 60 };
  enum : int32_t { MAX_SEARCH_TIME_RANGE_MAX = 3600 };
  enum : int32_t { SCAN_INTERVAL_RANGE_MIN = 5 };
  enum : int32_t { SCAN_INTERVAL_RANGE_MAX = 300 };
  enum : int32_t { SCAN_TYPE_ONE_SHOT = 0 };
  enum : int32_t { SCAN_TYPE_PERIODIC = 1 };
  inline std::string toString() const {
    std::ostringstream os;
    os << "NetworkScanRequest{";
    os << "type: " << ::android::internal::ToString(type);
    os << ", interval: " << ::android::internal::ToString(interval);
    os << ", specifiers: " << ::android::internal::ToString(specifiers);
    os << ", maxSearchTime: " << ::android::internal::ToString(maxSearchTime);
    os << ", incrementalResults: " << ::android::internal::ToString(incrementalResults);
    os << ", incrementalResultsPeriodicity: " << ::android::internal::ToString(incrementalResultsPeriodicity);
    os << ", mccMncs: " << ::android::internal::ToString(mccMncs);
    os << "}";
    return os.str();
  }
};
}  // namespace network
}  // namespace radio
}  // namespace hardware
}  // namespace android
}  // namespace aidl
