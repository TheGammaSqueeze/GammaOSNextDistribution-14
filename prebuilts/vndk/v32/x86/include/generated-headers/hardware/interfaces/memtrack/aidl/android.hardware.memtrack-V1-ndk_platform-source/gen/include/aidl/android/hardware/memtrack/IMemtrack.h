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
#include <aidl/android/hardware/memtrack/DeviceInfo.h>
#include <aidl/android/hardware/memtrack/MemtrackRecord.h>
#include <aidl/android/hardware/memtrack/MemtrackType.h>

namespace aidl {
namespace android {
namespace hardware {
namespace memtrack {
class IMemtrack : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IMemtrack();
  virtual ~IMemtrack();

  static const int32_t version = 1;
  static inline const std::string hash = "25438132a462bf5b6f4334d966c95b70824e66c0";
  static constexpr uint32_t TRANSACTION_getMemory = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_getGpuDeviceInfo = FIRST_CALL_TRANSACTION + 1;

  static std::shared_ptr<IMemtrack> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IMemtrack>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IMemtrack>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IMemtrack>& impl);
  static const std::shared_ptr<IMemtrack>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus getMemory(int32_t in_pid, ::aidl::android::hardware::memtrack::MemtrackType in_type, std::vector<::aidl::android::hardware::memtrack::MemtrackRecord>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getGpuDeviceInfo(std::vector<::aidl::android::hardware::memtrack::DeviceInfo>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IMemtrack> default_impl;
};
class IMemtrackDefault : public IMemtrack {
public:
  ::ndk::ScopedAStatus getMemory(int32_t in_pid, ::aidl::android::hardware::memtrack::MemtrackType in_type, std::vector<::aidl::android::hardware::memtrack::MemtrackRecord>* _aidl_return) override;
  ::ndk::ScopedAStatus getGpuDeviceInfo(std::vector<::aidl::android::hardware::memtrack::DeviceInfo>* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace memtrack
}  // namespace hardware
}  // namespace android
}  // namespace aidl
