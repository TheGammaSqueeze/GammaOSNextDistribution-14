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
#include <aidl/android/hardware/gnss/PsdsType.h>

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class IGnssPsdsCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IGnssPsdsCallback();
  virtual ~IGnssPsdsCallback();

  static const int32_t version = 1;
  static inline const std::string hash = "10839720b90aaec329521e810d9e0501cfcef0d3";
  static constexpr uint32_t TRANSACTION_downloadRequestCb = FIRST_CALL_TRANSACTION + 0;

  static std::shared_ptr<IGnssPsdsCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IGnssPsdsCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IGnssPsdsCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IGnssPsdsCallback>& impl);
  static const std::shared_ptr<IGnssPsdsCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus downloadRequestCb(::aidl::android::hardware::gnss::PsdsType in_psdsType) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IGnssPsdsCallback> default_impl;
};
class IGnssPsdsCallbackDefault : public IGnssPsdsCallback {
public:
  ::ndk::ScopedAStatus downloadRequestCb(::aidl::android::hardware::gnss::PsdsType in_psdsType) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
