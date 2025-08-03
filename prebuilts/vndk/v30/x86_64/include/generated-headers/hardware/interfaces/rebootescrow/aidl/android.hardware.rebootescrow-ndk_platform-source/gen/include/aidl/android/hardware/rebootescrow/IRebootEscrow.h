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

namespace aidl {
namespace android {
namespace hardware {
namespace rebootescrow {
class IRebootEscrow : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IRebootEscrow();
  virtual ~IRebootEscrow();


  static const int32_t version = 1;
  static inline const std::string hash = "ba450432e0dab8ee7bbc30013819ea8aef12054b";

  static std::shared_ptr<IRebootEscrow> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IRebootEscrow>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IRebootEscrow>* instance);
  static bool setDefaultImpl(std::shared_ptr<IRebootEscrow> impl);
  static const std::shared_ptr<IRebootEscrow>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus storeKey(const std::vector<int8_t>& in_kek) = 0;
  virtual ::ndk::ScopedAStatus retrieveKey(std::vector<int8_t>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IRebootEscrow> default_impl;
};
class IRebootEscrowDefault : public IRebootEscrow {
public:
  ::ndk::ScopedAStatus storeKey(const std::vector<int8_t>& in_kek) override;
  ::ndk::ScopedAStatus retrieveKey(std::vector<int8_t>* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace rebootescrow
}  // namespace hardware
}  // namespace android
}  // namespace aidl
