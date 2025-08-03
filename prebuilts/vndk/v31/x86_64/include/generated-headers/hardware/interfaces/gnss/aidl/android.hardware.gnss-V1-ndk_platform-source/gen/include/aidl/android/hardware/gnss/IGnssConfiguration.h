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
#include <aidl/android/hardware/gnss/BlocklistedSource.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class IGnssConfiguration : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IGnssConfiguration();
  virtual ~IGnssConfiguration();

  enum : int32_t { SUPL_MODE_MSB = 1 };
  enum : int32_t { SUPL_MODE_MSA = 2 };
  enum : int32_t { LPP_PROFILE_USER_PLANE = 1 };
  enum : int32_t { LPP_PROFILE_CONTROL_PLANE = 2 };
  enum : int32_t { GLONASS_POS_PROTOCOL_RRC_CPLANE = 1 };
  enum : int32_t { GLONASS_POS_PROTOCOL_RRLP_UPLANE = 2 };
  enum : int32_t { GLONASS_POS_PROTOCOL_LPP_UPLANE = 4 };
  static const int32_t version = 1;
  static inline const std::string hash = "10839720b90aaec329521e810d9e0501cfcef0d3";
  static constexpr uint32_t TRANSACTION_setSuplVersion = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_setSuplMode = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_setLppProfile = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_setGlonassPositioningProtocol = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_setEmergencySuplPdn = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_setEsExtensionSec = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_setBlocklist = FIRST_CALL_TRANSACTION + 6;

  static std::shared_ptr<IGnssConfiguration> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IGnssConfiguration>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IGnssConfiguration>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IGnssConfiguration>& impl);
  static const std::shared_ptr<IGnssConfiguration>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus setSuplVersion(int32_t in_version) = 0;
  virtual ::ndk::ScopedAStatus setSuplMode(int32_t in_mode) = 0;
  virtual ::ndk::ScopedAStatus setLppProfile(int32_t in_lppProfile) = 0;
  virtual ::ndk::ScopedAStatus setGlonassPositioningProtocol(int32_t in_protocol) = 0;
  virtual ::ndk::ScopedAStatus setEmergencySuplPdn(bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus setEsExtensionSec(int32_t in_emergencyExtensionSeconds) = 0;
  virtual ::ndk::ScopedAStatus setBlocklist(const std::vector<::aidl::android::hardware::gnss::BlocklistedSource>& in_blocklist) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IGnssConfiguration> default_impl;
};
class IGnssConfigurationDefault : public IGnssConfiguration {
public:
  ::ndk::ScopedAStatus setSuplVersion(int32_t in_version) override;
  ::ndk::ScopedAStatus setSuplMode(int32_t in_mode) override;
  ::ndk::ScopedAStatus setLppProfile(int32_t in_lppProfile) override;
  ::ndk::ScopedAStatus setGlonassPositioningProtocol(int32_t in_protocol) override;
  ::ndk::ScopedAStatus setEmergencySuplPdn(bool in_enable) override;
  ::ndk::ScopedAStatus setEsExtensionSec(int32_t in_emergencyExtensionSeconds) override;
  ::ndk::ScopedAStatus setBlocklist(const std::vector<::aidl::android::hardware::gnss::BlocklistedSource>& in_blocklist) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
