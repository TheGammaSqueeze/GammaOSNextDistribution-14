#pragma once

#include <array>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_enums.h>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/gnss/IAGnssCallback.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class IAGnssCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IAGnssCallback();
  virtual ~IAGnssCallback();

  enum class AGnssType : int32_t {
    SUPL = 1,
    C2K = 2,
    SUPL_EIMS = 3,
    SUPL_IMS = 4,
  };

  enum class AGnssStatusValue : int32_t {
    REQUEST_AGNSS_DATA_CONN = 1,
    RELEASE_AGNSS_DATA_CONN = 2,
    AGNSS_DATA_CONNECTED = 3,
    AGNSS_DATA_CONN_DONE = 4,
    AGNSS_DATA_CONN_FAILED = 5,
  };

  static const int32_t version = 2;
  static inline const std::string hash = "fc957f1d3d261d065ff5e5415f2d21caa79c310f";
  static constexpr uint32_t TRANSACTION_agnssStatusCb = FIRST_CALL_TRANSACTION + 0;

  static std::shared_ptr<IAGnssCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IAGnssCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IAGnssCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IAGnssCallback>& impl);
  static const std::shared_ptr<IAGnssCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus agnssStatusCb(::aidl::android::hardware::gnss::IAGnssCallback::AGnssType in_type, ::aidl::android::hardware::gnss::IAGnssCallback::AGnssStatusValue in_status) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IAGnssCallback> default_impl;
};
class IAGnssCallbackDefault : public IAGnssCallback {
public:
  ::ndk::ScopedAStatus agnssStatusCb(::aidl::android::hardware::gnss::IAGnssCallback::AGnssType in_type, ::aidl::android::hardware::gnss::IAGnssCallback::AGnssStatusValue in_status) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
[[nodiscard]] static inline std::string toString(IAGnssCallback::AGnssType val) {
  switch(val) {
  case IAGnssCallback::AGnssType::SUPL:
    return "SUPL";
  case IAGnssCallback::AGnssType::C2K:
    return "C2K";
  case IAGnssCallback::AGnssType::SUPL_EIMS:
    return "SUPL_EIMS";
  case IAGnssCallback::AGnssType::SUPL_IMS:
    return "SUPL_IMS";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::gnss::IAGnssCallback::AGnssType, 4> enum_values<aidl::android::hardware::gnss::IAGnssCallback::AGnssType> = {
  aidl::android::hardware::gnss::IAGnssCallback::AGnssType::SUPL,
  aidl::android::hardware::gnss::IAGnssCallback::AGnssType::C2K,
  aidl::android::hardware::gnss::IAGnssCallback::AGnssType::SUPL_EIMS,
  aidl::android::hardware::gnss::IAGnssCallback::AGnssType::SUPL_IMS,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
[[nodiscard]] static inline std::string toString(IAGnssCallback::AGnssStatusValue val) {
  switch(val) {
  case IAGnssCallback::AGnssStatusValue::REQUEST_AGNSS_DATA_CONN:
    return "REQUEST_AGNSS_DATA_CONN";
  case IAGnssCallback::AGnssStatusValue::RELEASE_AGNSS_DATA_CONN:
    return "RELEASE_AGNSS_DATA_CONN";
  case IAGnssCallback::AGnssStatusValue::AGNSS_DATA_CONNECTED:
    return "AGNSS_DATA_CONNECTED";
  case IAGnssCallback::AGnssStatusValue::AGNSS_DATA_CONN_DONE:
    return "AGNSS_DATA_CONN_DONE";
  case IAGnssCallback::AGnssStatusValue::AGNSS_DATA_CONN_FAILED:
    return "AGNSS_DATA_CONN_FAILED";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::gnss::IAGnssCallback::AGnssStatusValue, 5> enum_values<aidl::android::hardware::gnss::IAGnssCallback::AGnssStatusValue> = {
  aidl::android::hardware::gnss::IAGnssCallback::AGnssStatusValue::REQUEST_AGNSS_DATA_CONN,
  aidl::android::hardware::gnss::IAGnssCallback::AGnssStatusValue::RELEASE_AGNSS_DATA_CONN,
  aidl::android::hardware::gnss::IAGnssCallback::AGnssStatusValue::AGNSS_DATA_CONNECTED,
  aidl::android::hardware::gnss::IAGnssCallback::AGnssStatusValue::AGNSS_DATA_CONN_DONE,
  aidl::android::hardware::gnss::IAGnssCallback::AGnssStatusValue::AGNSS_DATA_CONN_FAILED,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
