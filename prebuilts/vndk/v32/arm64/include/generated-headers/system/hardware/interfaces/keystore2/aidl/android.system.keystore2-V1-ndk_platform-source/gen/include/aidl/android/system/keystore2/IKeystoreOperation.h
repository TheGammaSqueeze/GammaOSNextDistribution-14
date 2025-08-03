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
#include <android/binder_parcel_platform.h>
#include <android/binder_ibinder_platform.h>

namespace aidl {
namespace android {
namespace system {
namespace keystore2 {
class IKeystoreOperation : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IKeystoreOperation();
  virtual ~IKeystoreOperation();

  static const int32_t version = 1;
  static inline const std::string hash = "19e8b65277839bad0ab335c781e3c652324920ce";
  static constexpr uint32_t TRANSACTION_updateAad = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_update = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_finish = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_abort = FIRST_CALL_TRANSACTION + 3;

  static std::shared_ptr<IKeystoreOperation> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IKeystoreOperation>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IKeystoreOperation>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IKeystoreOperation>& impl);
  static const std::shared_ptr<IKeystoreOperation>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus updateAad(const std::vector<uint8_t>& in_aadInput) = 0;
  virtual ::ndk::ScopedAStatus update(const std::vector<uint8_t>& in_input, std::optional<std::vector<uint8_t>>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus finish(const std::optional<std::vector<uint8_t>>& in_input, const std::optional<std::vector<uint8_t>>& in_signature, std::optional<std::vector<uint8_t>>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus abort() = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IKeystoreOperation> default_impl;
};
class IKeystoreOperationDefault : public IKeystoreOperation {
public:
  ::ndk::ScopedAStatus updateAad(const std::vector<uint8_t>& in_aadInput) override;
  ::ndk::ScopedAStatus update(const std::vector<uint8_t>& in_input, std::optional<std::vector<uint8_t>>* _aidl_return) override;
  ::ndk::ScopedAStatus finish(const std::optional<std::vector<uint8_t>>& in_input, const std::optional<std::vector<uint8_t>>& in_signature, std::optional<std::vector<uint8_t>>* _aidl_return) override;
  ::ndk::ScopedAStatus abort() override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace keystore2
}  // namespace system
}  // namespace android
}  // namespace aidl
