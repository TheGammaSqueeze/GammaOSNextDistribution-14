#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/light/HwLight.h>
#include <aidl/android/hardware/light/HwLightState.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace light {
class ILights : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ILights();
  virtual ~ILights();

  static const int32_t version = 2;
  static inline const std::string hash = "c8b1e8ebb88c57dcb2c350a8d9b722e77dd864c8";
  static constexpr uint32_t TRANSACTION_setLightState = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_getLights = FIRST_CALL_TRANSACTION + 1;

  static std::shared_ptr<ILights> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ILights>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ILights>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ILights>& impl);
  static const std::shared_ptr<ILights>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus setLightState(int32_t in_id, const ::aidl::android::hardware::light::HwLightState& in_state) = 0;
  virtual ::ndk::ScopedAStatus getLights(std::vector<::aidl::android::hardware::light::HwLight>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ILights> default_impl;
};
class ILightsDefault : public ILights {
public:
  ::ndk::ScopedAStatus setLightState(int32_t in_id, const ::aidl::android::hardware::light::HwLightState& in_state) override;
  ::ndk::ScopedAStatus getLights(std::vector<::aidl::android::hardware::light::HwLight>* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace light
}  // namespace hardware
}  // namespace android
}  // namespace aidl
