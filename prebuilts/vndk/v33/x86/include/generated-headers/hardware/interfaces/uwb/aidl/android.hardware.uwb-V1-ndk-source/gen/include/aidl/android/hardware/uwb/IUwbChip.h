#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/uwb/IUwbClientCallback.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace uwb {
class IUwbChip : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IUwbChip();
  virtual ~IUwbChip();

  static const int32_t version = 1;
  static inline const std::string hash = "5bffd1b593a254bcbe9cb0b2fcf29f71452ae0af";
  static constexpr uint32_t TRANSACTION_getName = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_open = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_close = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_coreInit = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_sessionInit = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_getSupportedAndroidUciVersion = FIRST_CALL_TRANSACTION + 5;
  static constexpr uint32_t TRANSACTION_sendUciMessage = FIRST_CALL_TRANSACTION + 6;

  static std::shared_ptr<IUwbChip> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IUwbChip>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IUwbChip>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IUwbChip>& impl);
  static const std::shared_ptr<IUwbChip>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus getName(std::string* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus open(const std::shared_ptr<::aidl::android::hardware::uwb::IUwbClientCallback>& in_clientCallback) = 0;
  virtual ::ndk::ScopedAStatus close() = 0;
  virtual ::ndk::ScopedAStatus coreInit() = 0;
  virtual ::ndk::ScopedAStatus sessionInit(int32_t in_sessionId) = 0;
  virtual ::ndk::ScopedAStatus getSupportedAndroidUciVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus sendUciMessage(const std::vector<uint8_t>& in_data, int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IUwbChip> default_impl;
};
class IUwbChipDefault : public IUwbChip {
public:
  ::ndk::ScopedAStatus getName(std::string* _aidl_return) override;
  ::ndk::ScopedAStatus open(const std::shared_ptr<::aidl::android::hardware::uwb::IUwbClientCallback>& in_clientCallback) override;
  ::ndk::ScopedAStatus close() override;
  ::ndk::ScopedAStatus coreInit() override;
  ::ndk::ScopedAStatus sessionInit(int32_t in_sessionId) override;
  ::ndk::ScopedAStatus getSupportedAndroidUciVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus sendUciMessage(const std::vector<uint8_t>& in_data, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace uwb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
