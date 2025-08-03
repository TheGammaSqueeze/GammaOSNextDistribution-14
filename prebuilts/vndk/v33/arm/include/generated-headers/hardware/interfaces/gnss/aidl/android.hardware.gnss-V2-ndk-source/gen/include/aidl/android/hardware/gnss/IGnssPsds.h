#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/gnss/IGnssPsdsCallback.h>
#include <aidl/android/hardware/gnss/PsdsType.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace gnss {
class IGnssPsds : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IGnssPsds();
  virtual ~IGnssPsds();

  static const int32_t version = 2;
  static inline const std::string hash = "fc957f1d3d261d065ff5e5415f2d21caa79c310f";
  static constexpr uint32_t TRANSACTION_injectPsdsData = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_setCallback = FIRST_CALL_TRANSACTION + 1;

  static std::shared_ptr<IGnssPsds> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IGnssPsds>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IGnssPsds>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IGnssPsds>& impl);
  static const std::shared_ptr<IGnssPsds>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus injectPsdsData(::aidl::android::hardware::gnss::PsdsType in_psdsType, const std::vector<uint8_t>& in_psdsData) = 0;
  virtual ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssPsdsCallback>& in_callback) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IGnssPsds> default_impl;
};
class IGnssPsdsDefault : public IGnssPsds {
public:
  ::ndk::ScopedAStatus injectPsdsData(::aidl::android::hardware::gnss::PsdsType in_psdsType, const std::vector<uint8_t>& in_psdsData) override;
  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::gnss::IGnssPsdsCallback>& in_callback) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace gnss
}  // namespace hardware
}  // namespace android
}  // namespace aidl
