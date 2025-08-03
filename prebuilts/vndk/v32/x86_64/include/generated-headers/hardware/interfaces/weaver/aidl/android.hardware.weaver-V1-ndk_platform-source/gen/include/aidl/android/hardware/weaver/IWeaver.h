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
#include <aidl/android/hardware/weaver/WeaverConfig.h>
#include <aidl/android/hardware/weaver/WeaverReadResponse.h>

namespace aidl {
namespace android {
namespace hardware {
namespace weaver {
class IWeaver : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IWeaver();
  virtual ~IWeaver();

  enum : int32_t { STATUS_FAILED = 1 };
  enum : int32_t { STATUS_INCORRECT_KEY = 2 };
  enum : int32_t { STATUS_THROTTLE = 3 };
  static const int32_t version = 1;
  static inline const std::string hash = "c2ea8ac04f236492c02b992dc46ae904db0acc7e";
  static constexpr uint32_t TRANSACTION_getConfig = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_read = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_write = FIRST_CALL_TRANSACTION + 2;

  static std::shared_ptr<IWeaver> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IWeaver>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IWeaver>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IWeaver>& impl);
  static const std::shared_ptr<IWeaver>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus getConfig(::aidl::android::hardware::weaver::WeaverConfig* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus read(int32_t in_slotId, const std::vector<uint8_t>& in_key, ::aidl::android::hardware::weaver::WeaverReadResponse* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus write(int32_t in_slotId, const std::vector<uint8_t>& in_key, const std::vector<uint8_t>& in_value) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IWeaver> default_impl;
};
class IWeaverDefault : public IWeaver {
public:
  ::ndk::ScopedAStatus getConfig(::aidl::android::hardware::weaver::WeaverConfig* _aidl_return) override;
  ::ndk::ScopedAStatus read(int32_t in_slotId, const std::vector<uint8_t>& in_key, ::aidl::android::hardware::weaver::WeaverReadResponse* _aidl_return) override;
  ::ndk::ScopedAStatus write(int32_t in_slotId, const std::vector<uint8_t>& in_key, const std::vector<uint8_t>& in_value) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace weaver
}  // namespace hardware
}  // namespace android
}  // namespace aidl
