#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/uwb/IUwbChip.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace uwb {
class IUwb : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IUwb();
  virtual ~IUwb();

  static const int32_t version = 1;
  static inline const std::string hash = "5bffd1b593a254bcbe9cb0b2fcf29f71452ae0af";
  static constexpr uint32_t TRANSACTION_getChips = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_getChip = FIRST_CALL_TRANSACTION + 1;

  static std::shared_ptr<IUwb> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IUwb>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IUwb>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IUwb>& impl);
  static const std::shared_ptr<IUwb>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus getChips(std::vector<std::string>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getChip(const std::string& in_name, std::shared_ptr<::aidl::android::hardware::uwb::IUwbChip>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IUwb> default_impl;
};
class IUwbDefault : public IUwb {
public:
  ::ndk::ScopedAStatus getChips(std::vector<std::string>* _aidl_return) override;
  ::ndk::ScopedAStatus getChip(const std::string& in_name, std::shared_ptr<::aidl::android::hardware::uwb::IUwbChip>* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace uwb
}  // namespace hardware
}  // namespace android
}  // namespace aidl
