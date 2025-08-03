#pragma once

#include "aidl/android/hardware/drm/ICryptoPlugin.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace drm {
class BpCryptoPlugin : public ::ndk::BpCInterface<ICryptoPlugin> {
public:
  explicit BpCryptoPlugin(const ::ndk::SpAIBinder& binder);
  virtual ~BpCryptoPlugin();

  ::ndk::ScopedAStatus decrypt(const ::aidl::android::hardware::drm::DecryptArgs& in_args, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getLogMessages(std::vector<::aidl::android::hardware::drm::LogMessage>* _aidl_return) override;
  ::ndk::ScopedAStatus notifyResolution(int32_t in_width, int32_t in_height) override;
  ::ndk::ScopedAStatus requiresSecureDecoderComponent(const std::string& in_mime, bool* _aidl_return) override;
  ::ndk::ScopedAStatus setMediaDrmSession(const std::vector<uint8_t>& in_sessionId) override;
  ::ndk::ScopedAStatus setSharedBufferBase(const ::aidl::android::hardware::drm::SharedBuffer& in_base) override;
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
