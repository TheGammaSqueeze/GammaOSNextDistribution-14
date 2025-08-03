#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace system {
namespace suspend {
class IWakeLock : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IWakeLock();
  virtual ~IWakeLock();

  static const int32_t version = 1;
  static inline const std::string hash = "082a58c2505b703702f34ab8abcb1d1b5e890be4";
  static constexpr uint32_t TRANSACTION_release = FIRST_CALL_TRANSACTION + 0;

  static std::shared_ptr<IWakeLock> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IWakeLock>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IWakeLock>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IWakeLock>& impl);
  static const std::shared_ptr<IWakeLock>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus release() = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IWakeLock> default_impl;
};
class IWakeLockDefault : public IWakeLock {
public:
  ::ndk::ScopedAStatus release() override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace suspend
}  // namespace system
}  // namespace android
}  // namespace aidl
