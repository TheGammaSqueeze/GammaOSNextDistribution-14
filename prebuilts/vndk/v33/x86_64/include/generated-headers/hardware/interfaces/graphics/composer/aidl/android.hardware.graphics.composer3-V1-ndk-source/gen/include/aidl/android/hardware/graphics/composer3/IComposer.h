#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/graphics/composer3/Capability.h>
#include <aidl/android/hardware/graphics/composer3/IComposerClient.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
class IComposer : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IComposer();
  virtual ~IComposer();

  enum : int32_t { EX_NO_RESOURCES = 6 };
  static const int32_t version = 1;
  static inline const std::string hash = "6e0e98f955d0bf3e7502a30fac0465e56cc98e75";
  static constexpr uint32_t TRANSACTION_createClient = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_getCapabilities = FIRST_CALL_TRANSACTION + 1;

  static std::shared_ptr<IComposer> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IComposer>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IComposer>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IComposer>& impl);
  static const std::shared_ptr<IComposer>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus createClient(std::shared_ptr<::aidl::android::hardware::graphics::composer3::IComposerClient>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getCapabilities(std::vector<::aidl::android::hardware::graphics::composer3::Capability>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IComposer> default_impl;
};
class IComposerDefault : public IComposer {
public:
  ::ndk::ScopedAStatus createClient(std::shared_ptr<::aidl::android::hardware::graphics::composer3::IComposerClient>* _aidl_return) override;
  ::ndk::ScopedAStatus getCapabilities(std::vector<::aidl::android::hardware::graphics::composer3::Capability>* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
