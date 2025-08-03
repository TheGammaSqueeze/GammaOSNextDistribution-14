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
#include <aidl/android/hardware/power/WorkDuration.h>

namespace aidl {
namespace android {
namespace hardware {
namespace power {
class IPowerHintSession : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IPowerHintSession();
  virtual ~IPowerHintSession();

  static const int32_t version = 2;
  static inline const std::string hash = "ef4f5ed58e39693f25c1f8fdcfe7b958c6b800bc";
  static constexpr uint32_t TRANSACTION_updateTargetWorkDuration = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_reportActualWorkDuration = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_pause = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_resume = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_close = FIRST_CALL_TRANSACTION + 4;

  static std::shared_ptr<IPowerHintSession> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IPowerHintSession>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IPowerHintSession>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IPowerHintSession>& impl);
  static const std::shared_ptr<IPowerHintSession>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus updateTargetWorkDuration(int64_t in_targetDurationNanos) = 0;
  virtual ::ndk::ScopedAStatus reportActualWorkDuration(const std::vector<::aidl::android::hardware::power::WorkDuration>& in_durations) = 0;
  virtual ::ndk::ScopedAStatus pause() = 0;
  virtual ::ndk::ScopedAStatus resume() = 0;
  virtual ::ndk::ScopedAStatus close() = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IPowerHintSession> default_impl;
};
class IPowerHintSessionDefault : public IPowerHintSession {
public:
  ::ndk::ScopedAStatus updateTargetWorkDuration(int64_t in_targetDurationNanos) override;
  ::ndk::ScopedAStatus reportActualWorkDuration(const std::vector<::aidl::android::hardware::power::WorkDuration>& in_durations) override;
  ::ndk::ScopedAStatus pause() override;
  ::ndk::ScopedAStatus resume() override;
  ::ndk::ScopedAStatus close() override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace power
}  // namespace hardware
}  // namespace android
}  // namespace aidl
