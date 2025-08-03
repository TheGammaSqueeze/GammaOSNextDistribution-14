#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/gnss/measurement_corrections/IMeasurementCorrectionsCallback.h>
#include <aidl/android/hardware/gnss/measurement_corrections/MeasurementCorrections.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
namespace measurement_corrections {
class IMeasurementCorrectionsInterface : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IMeasurementCorrectionsInterface();
  virtual ~IMeasurementCorrectionsInterface();

  static const int32_t version = 2;
  static inline const std::string hash = "fc957f1d3d261d065ff5e5415f2d21caa79c310f";
  static constexpr uint32_t TRANSACTION_setCorrections = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_setCallback = FIRST_CALL_TRANSACTION + 1;

  static std::shared_ptr<IMeasurementCorrectionsInterface> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IMeasurementCorrectionsInterface>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IMeasurementCorrectionsInterface>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IMeasurementCorrectionsInterface>& impl);
  static const std::shared_ptr<IMeasurementCorrectionsInterface>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus setCorrections(const ::aidl::android::hardware::gnss::measurement_corrections::MeasurementCorrections& in_corrections) = 0;
  virtual ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::measurement_corrections::IMeasurementCorrectionsCallback>& in_callback) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IMeasurementCorrectionsInterface> default_impl;
};
class IMeasurementCorrectionsInterfaceDefault : public IMeasurementCorrectionsInterface {
public:
  ::ndk::ScopedAStatus setCorrections(const ::aidl::android::hardware::gnss::measurement_corrections::MeasurementCorrections& in_corrections) override;
  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::measurement_corrections::IMeasurementCorrectionsCallback>& in_callback) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace measurement_corrections
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
