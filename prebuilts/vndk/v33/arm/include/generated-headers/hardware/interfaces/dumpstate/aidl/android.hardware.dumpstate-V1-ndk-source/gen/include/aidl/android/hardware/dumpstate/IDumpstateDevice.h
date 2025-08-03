#pragma once

#include <array>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_enums.h>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/dumpstate/IDumpstateDevice.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace dumpstate {
class IDumpstateDevice : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IDumpstateDevice();
  virtual ~IDumpstateDevice();

  enum class DumpstateMode : int32_t {
    FULL = 0,
    INTERACTIVE = 1,
    REMOTE = 2,
    WEAR = 3,
    CONNECTIVITY = 4,
    WIFI = 5,
    DEFAULT = 6,
    PROTO = 7,
  };

  enum : int32_t { ERROR_UNSUPPORTED_MODE = 1 };
  enum : int32_t { ERROR_DEVICE_LOGGING_NOT_ENABLED = 2 };
  static const int32_t version = 1;
  static inline const std::string hash = "d7c8d1b9054ec2fb0510edb68b95ca69fd038719";
  static constexpr uint32_t TRANSACTION_dumpstateBoard = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_getVerboseLoggingEnabled = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_setVerboseLoggingEnabled = FIRST_CALL_TRANSACTION + 2;

  static std::shared_ptr<IDumpstateDevice> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IDumpstateDevice>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IDumpstateDevice>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IDumpstateDevice>& impl);
  static const std::shared_ptr<IDumpstateDevice>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus dumpstateBoard(const std::vector<::ndk::ScopedFileDescriptor>& in_fd, ::aidl::android::hardware::dumpstate::IDumpstateDevice::DumpstateMode in_mode, int64_t in_timeoutMillis) = 0;
  virtual ::ndk::ScopedAStatus getVerboseLoggingEnabled(bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus setVerboseLoggingEnabled(bool in_enable) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IDumpstateDevice> default_impl;
};
class IDumpstateDeviceDefault : public IDumpstateDevice {
public:
  ::ndk::ScopedAStatus dumpstateBoard(const std::vector<::ndk::ScopedFileDescriptor>& in_fd, ::aidl::android::hardware::dumpstate::IDumpstateDevice::DumpstateMode in_mode, int64_t in_timeoutMillis) override;
  ::ndk::ScopedAStatus getVerboseLoggingEnabled(bool* _aidl_return) override;
  ::ndk::ScopedAStatus setVerboseLoggingEnabled(bool in_enable) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace dumpstate
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace aidl {
namespace android {
namespace hardware {
namespace dumpstate {
[[nodiscard]] static inline std::string toString(IDumpstateDevice::DumpstateMode val) {
  switch(val) {
  case IDumpstateDevice::DumpstateMode::FULL:
    return "FULL";
  case IDumpstateDevice::DumpstateMode::INTERACTIVE:
    return "INTERACTIVE";
  case IDumpstateDevice::DumpstateMode::REMOTE:
    return "REMOTE";
  case IDumpstateDevice::DumpstateMode::WEAR:
    return "WEAR";
  case IDumpstateDevice::DumpstateMode::CONNECTIVITY:
    return "CONNECTIVITY";
  case IDumpstateDevice::DumpstateMode::WIFI:
    return "WIFI";
  case IDumpstateDevice::DumpstateMode::DEFAULT:
    return "DEFAULT";
  case IDumpstateDevice::DumpstateMode::PROTO:
    return "PROTO";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace dumpstate
}  // namespace hardware
}  // namespace android
}  // namespace aidl
namespace ndk {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<aidl::android::hardware::dumpstate::IDumpstateDevice::DumpstateMode, 8> enum_values<aidl::android::hardware::dumpstate::IDumpstateDevice::DumpstateMode> = {
  aidl::android::hardware::dumpstate::IDumpstateDevice::DumpstateMode::FULL,
  aidl::android::hardware::dumpstate::IDumpstateDevice::DumpstateMode::INTERACTIVE,
  aidl::android::hardware::dumpstate::IDumpstateDevice::DumpstateMode::REMOTE,
  aidl::android::hardware::dumpstate::IDumpstateDevice::DumpstateMode::WEAR,
  aidl::android::hardware::dumpstate::IDumpstateDevice::DumpstateMode::CONNECTIVITY,
  aidl::android::hardware::dumpstate::IDumpstateDevice::DumpstateMode::WIFI,
  aidl::android::hardware::dumpstate::IDumpstateDevice::DumpstateMode::DEFAULT,
  aidl::android::hardware::dumpstate::IDumpstateDevice::DumpstateMode::PROTO,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace ndk
