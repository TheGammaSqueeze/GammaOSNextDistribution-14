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
#include <aidl/android/hardware/gnss/IGnssMeasurementCallback.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class IGnssMeasurementInterface : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IGnssMeasurementInterface();
  virtual ~IGnssMeasurementInterface();

  static const int32_t version = 1;
  static inline const std::string hash = "10839720b90aaec329521e810d9e0501cfcef0d3";
  static constexpr uint32_t TRANSACTION_setCallback = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_close = FIRST_CALL_TRANSACTION + 1;

  static std::shared_ptr<IGnssMeasurementInterface> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IGnssMeasurementInterface>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IGnssMeasurementInterface>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IGnssMeasurementInterface>& impl);
  static const std::shared_ptr<IGnssMeasurementInterface>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssMeasurementCallback>& in_callback, bool in_enableFullTracking, bool in_enableCorrVecOutputs) = 0;
  virtual ::ndk::ScopedAStatus close() = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IGnssMeasurementInterface> default_impl;
};
class IGnssMeasurementInterfaceDefault : public IGnssMeasurementInterface {
public:
  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssMeasurementCallback>& in_callback, bool in_enableFullTracking, bool in_enableCorrVecOutputs) override;
  ::ndk::ScopedAStatus close() override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
