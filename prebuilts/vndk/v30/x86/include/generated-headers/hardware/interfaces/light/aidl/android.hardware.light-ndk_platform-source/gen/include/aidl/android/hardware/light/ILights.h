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
#include <aidl/android/hardware/light/HwLight.h>
#include <aidl/android/hardware/light/HwLightState.h>

namespace aidl {
namespace android {
namespace hardware {
namespace light {
class ILights : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ILights();
  virtual ~ILights();


  static const int32_t version = 1;
  static inline const std::string hash = "33fec8401b6e66bddaeff251e1a2a0f4fa0d3bee";

  static std::shared_ptr<ILights> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ILights>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ILights>* instance);
  static bool setDefaultImpl(std::shared_ptr<ILights> impl);
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
