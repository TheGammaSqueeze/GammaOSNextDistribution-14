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
#include <aidl/android/hardware/gnss/IGnssCallback.h>
#include <aidl/android/hardware/gnss/IGnssConfiguration.h>
#include <aidl/android/hardware/gnss/IGnssMeasurementInterface.h>
#include <aidl/android/hardware/gnss/IGnssPowerIndication.h>
#include <aidl/android/hardware/gnss/IGnssPsds.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class IGnss : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IGnss();
  virtual ~IGnss();

  enum : int32_t { ERROR_INVALID_ARGUMENT = 1 };
  enum : int32_t { ERROR_ALREADY_INIT = 2 };
  enum : int32_t { ERROR_GENERIC = 3 };
  static const int32_t version = 1;
  static inline const std::string hash = "10839720b90aaec329521e810d9e0501cfcef0d3";
  static constexpr uint32_t TRANSACTION_setCallback = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_close = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_getExtensionPsds = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_getExtensionGnssConfiguration = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_getExtensionGnssMeasurement = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_getExtensionGnssPowerIndication = FIRST_CALL_TRANSACTION + 5;

  static std::shared_ptr<IGnss> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IGnss>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IGnss>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IGnss>& impl);
  static const std::shared_ptr<IGnss>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssCallback>& in_callback) = 0;
  virtual ::ndk::ScopedAStatus close() = 0;
  virtual ::ndk::ScopedAStatus getExtensionPsds(std::shared_ptr<::aidl::android::hardware::gnss::IGnssPsds>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getExtensionGnssConfiguration(std::shared_ptr<::aidl::android::hardware::gnss::IGnssConfiguration>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getExtensionGnssMeasurement(std::shared_ptr<::aidl::android::hardware::gnss::IGnssMeasurementInterface>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getExtensionGnssPowerIndication(std::shared_ptr<::aidl::android::hardware::gnss::IGnssPowerIndication>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IGnss> default_impl;
};
class IGnssDefault : public IGnss {
public:
  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssCallback>& in_callback) override;
  ::ndk::ScopedAStatus close() override;
  ::ndk::ScopedAStatus getExtensionPsds(std::shared_ptr<::aidl::android::hardware::gnss::IGnssPsds>* _aidl_return) override;
  ::ndk::ScopedAStatus getExtensionGnssConfiguration(std::shared_ptr<::aidl::android::hardware::gnss::IGnssConfiguration>* _aidl_return) override;
  ::ndk::ScopedAStatus getExtensionGnssMeasurement(std::shared_ptr<::aidl::android::hardware::gnss::IGnssMeasurementInterface>* _aidl_return) override;
  ::ndk::ScopedAStatus getExtensionGnssPowerIndication(std::shared_ptr<::aidl::android::hardware::gnss::IGnssPowerIndication>* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
