#pragma once

#include <array>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_enums.h>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/gnss/IAGnss.h>
#include <aidl/android/hardware/gnss/IAGnssCallback.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class IAGnss : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IAGnss();
  virtual ~IAGnss();

  enum class ApnIpType : int32_t {
    INVALID = 0,
    IPV4 = 1,
    IPV6 = 2,
    IPV4V6 = 3,
  };

  static const int32_t version = 2;
  static inline const std::string hash = "fc957f1d3d261d065ff5e5415f2d21caa79c310f";
  static constexpr uint32_t TRANSACTION_setCallback = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_dataConnClosed = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_dataConnFailed = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_setServer = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_dataConnOpen = FIRST_CALL_TRANSACTION + 4;

  static std::shared_ptr<IAGnss> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IAGnss>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IAGnss>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IAGnss>& impl);
  static const std::shared_ptr<IAGnss>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IAGnssCallback>& in_callback) = 0;
  virtual ::ndk::ScopedAStatus dataConnClosed() = 0;
  virtual ::ndk::ScopedAStatus dataConnFailed() = 0;
  virtual ::ndk::ScopedAStatus setServer(::aidl::android::hardware::gnss::IAGnssCallback::AGnssType in_type, const std::string& in_hostname, int32_t in_port) = 0;
  virtual ::ndk::ScopedAStatus dataConnOpen(int64_t in_networkHandle, const std::string& in_apn, ::aidl::android::hardware::gnss::IAGnss::ApnIpType in_apnIpType) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IAGnss> default_impl;
};
class IAGnssDefault : public IAGnss {
public:
  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IAGnssCallback>& in_callback) override;
  ::ndk::ScopedAStatus dataConnClosed() override;
  ::ndk::ScopedAStatus dataConnFailed() override;
  ::ndk::ScopedAStatus setServer(::aidl::android::hardware::gnss::IAGnssCallback::AGnssType in_type, const std::string& in_hostname, int32_t in_port) override;
  ::ndk::ScopedAStatus dataConnOpen(int64_t in_networkHandle, const std::string& in_apn, ::aidl::android::hardware::gnss::IAGnss::ApnIpType in_apnIpType) override;
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
[[nodiscard]] static inline std::string toString(IAGnss::ApnIpType val) {
  switch(val) {
  case IAGnss::ApnIpType::INVALID:
    return "INVALID";
  case IAGnss::ApnIpType::IPV4:
    return "IPV4";
  case IAGnss::ApnIpType::IPV6:
    return "IPV6";
  case IAGnss::ApnIpType::IPV4V6:
    return "IPV4V6";
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
constexpr inline std::array<aidl::android::hardware::gnss::IAGnss::ApnIpType, 4> enum_values<aidl::android::hardware::gnss::IAGnss::ApnIpType> = {
  aidl::android::hardware::gnss::IAGnss::ApnIpType::INVALID,
  aidl::android::hardware::gnss::IAGnss::ApnIpType::IPV4,
  aidl::android::hardware::gnss::IAGnss::ApnIpType::IPV6,
  aidl::android::hardware::gnss::IAGnss::ApnIpType::IPV4V6,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
