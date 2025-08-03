#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/ir/ConsumerIrFreqRange.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace ir {
class IConsumerIr : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IConsumerIr();
  virtual ~IConsumerIr();

  static const int32_t version = 1;
  static inline const std::string hash = "3e04aed366e96850c6164287eaf78a8e4ab071b0";
  static constexpr uint32_t TRANSACTION_getCarrierFreqs = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_transmit = FIRST_CALL_TRANSACTION + 1;

  static std::shared_ptr<IConsumerIr> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IConsumerIr>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IConsumerIr>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IConsumerIr>& impl);
  static const std::shared_ptr<IConsumerIr>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus getCarrierFreqs(std::vector<::aidl::android::hardware::ir::ConsumerIrFreqRange>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus transmit(int32_t in_carrierFreqHz, const std::vector<int32_t>& in_pattern) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IConsumerIr> default_impl;
};
class IConsumerIrDefault : public IConsumerIr {
public:
  ::ndk::ScopedAStatus getCarrierFreqs(std::vector<::aidl::android::hardware::ir::ConsumerIrFreqRange>* _aidl_return) override;
  ::ndk::ScopedAStatus transmit(int32_t in_carrierFreqHz, const std::vector<int32_t>& in_pattern) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace ir
}  // namespace hardware
}  // namespace android
}  // namespace aidl
