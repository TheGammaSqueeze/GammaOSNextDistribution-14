#pragma once

#include "aidl/android/hardware/drm/IDrmFactory.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace drm {
class BpDrmFactory : public ::ndk::BpCInterface<IDrmFactory> {
public:
  explicit BpDrmFactory(const ::ndk::SpAIBinder& binder);
  virtual ~BpDrmFactory();

  ::ndk::ScopedAStatus createDrmPlugin(const ::aidl::android::hardware::drm::Uuid& in_uuid, const std::string& in_appPackageName, std::shared_ptr<::aidl::android::hardware::drm::IDrmPlugin>* _aidl_return) override;
  ::ndk::ScopedAStatus createCryptoPlugin(const ::aidl::android::hardware::drm::Uuid& in_uuid, const std::vector<uint8_t>& in_initData, std::shared_ptr<::aidl::android::hardware::drm::ICryptoPlugin>* _aidl_return) override;
  ::ndk::ScopedAStatus getSupportedCryptoSchemes(::aidl::android::hardware::drm::CryptoSchemes* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
