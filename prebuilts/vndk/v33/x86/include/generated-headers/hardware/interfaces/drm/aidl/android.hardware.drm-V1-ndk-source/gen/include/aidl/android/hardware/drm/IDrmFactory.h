#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/drm/CryptoSchemes.h>
#include <aidl/android/hardware/drm/ICryptoPlugin.h>
#include <aidl/android/hardware/drm/IDrmPlugin.h>
#include <aidl/android/hardware/drm/Uuid.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace drm {
class IDrmFactory : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IDrmFactory();
  virtual ~IDrmFactory();

  static const int32_t version = 1;
  static inline const std::string hash = "7b4b0a0f36a7a6bb22d2016375e4a9d4a033592f";
  static constexpr uint32_t TRANSACTION_createDrmPlugin = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_createCryptoPlugin = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_getSupportedCryptoSchemes = FIRST_CALL_TRANSACTION + 2;

  static std::shared_ptr<IDrmFactory> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IDrmFactory>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IDrmFactory>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IDrmFactory>& impl);
  static const std::shared_ptr<IDrmFactory>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus createDrmPlugin(const ::aidl::android::hardware::drm::Uuid& in_uuid, const std::string& in_appPackageName, std::shared_ptr<::aidl::android::hardware::drm::IDrmPlugin>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus createCryptoPlugin(const ::aidl::android::hardware::drm::Uuid& in_uuid, const std::vector<uint8_t>& in_initData, std::shared_ptr<::aidl::android::hardware::drm::ICryptoPlugin>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getSupportedCryptoSchemes(::aidl::android::hardware::drm::CryptoSchemes* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IDrmFactory> default_impl;
};
class IDrmFactoryDefault : public IDrmFactory {
public:
  ::ndk::ScopedAStatus createDrmPlugin(const ::aidl::android::hardware::drm::Uuid& in_uuid, const std::string& in_appPackageName, std::shared_ptr<::aidl::android::hardware::drm::IDrmPlugin>* _aidl_return) override;
  ::ndk::ScopedAStatus createCryptoPlugin(const ::aidl::android::hardware::drm::Uuid& in_uuid, const std::vector<uint8_t>& in_initData, std::shared_ptr<::aidl::android::hardware::drm::ICryptoPlugin>* _aidl_return) override;
  ::ndk::ScopedAStatus getSupportedCryptoSchemes(::aidl::android::hardware::drm::CryptoSchemes* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
