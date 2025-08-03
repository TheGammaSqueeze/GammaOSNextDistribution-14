#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/gnss/GnssData.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class IGnssMeasurementCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IGnssMeasurementCallback();
  virtual ~IGnssMeasurementCallback();

  static const int32_t version = 2;
  static inline const std::string hash = "fc957f1d3d261d065ff5e5415f2d21caa79c310f";
  static constexpr uint32_t TRANSACTION_gnssMeasurementCb = FIRST_CALL_TRANSACTION + 0;

  static std::shared_ptr<IGnssMeasurementCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IGnssMeasurementCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IGnssMeasurementCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IGnssMeasurementCallback>& impl);
  static const std::shared_ptr<IGnssMeasurementCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus gnssMeasurementCb(const ::aidl::android::hardware::gnss::GnssData& in_data) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IGnssMeasurementCallback> default_impl;
};
class IGnssMeasurementCallbackDefault : public IGnssMeasurementCallback {
public:
  ::ndk::ScopedAStatus gnssMeasurementCb(const ::aidl::android::hardware::gnss::GnssData& in_data) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
