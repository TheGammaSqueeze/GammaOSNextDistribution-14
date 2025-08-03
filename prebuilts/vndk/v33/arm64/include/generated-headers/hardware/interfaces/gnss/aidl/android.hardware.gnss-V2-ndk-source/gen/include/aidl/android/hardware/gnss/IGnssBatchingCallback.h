#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/gnss/GnssLocation.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class IGnssBatchingCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IGnssBatchingCallback();
  virtual ~IGnssBatchingCallback();

  static const int32_t version = 2;
  static inline const std::string hash = "fc957f1d3d261d065ff5e5415f2d21caa79c310f";
  static constexpr uint32_t TRANSACTION_gnssLocationBatchCb = FIRST_CALL_TRANSACTION + 0;

  static std::shared_ptr<IGnssBatchingCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IGnssBatchingCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IGnssBatchingCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IGnssBatchingCallback>& impl);
  static const std::shared_ptr<IGnssBatchingCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus gnssLocationBatchCb(const std::vector<::aidl::android::hardware::gnss::GnssLocation>& in_locations) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IGnssBatchingCallback> default_impl;
};
class IGnssBatchingCallbackDefault : public IGnssBatchingCallback {
public:
  ::ndk::ScopedAStatus gnssLocationBatchCb(const std::vector<::aidl::android::hardware::gnss::GnssLocation>& in_locations) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
