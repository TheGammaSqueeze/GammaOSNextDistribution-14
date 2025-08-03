#pragma once

#include "aidl/android/hardware/drm/IDrmPluginListener.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace drm {
class BpDrmPluginListener : public ::ndk::BpCInterface<IDrmPluginListener> {
public:
  explicit BpDrmPluginListener(const ::ndk::SpAIBinder& binder);
  virtual ~BpDrmPluginListener();

  ::ndk::ScopedAStatus onEvent(::aidl::android::hardware::drm::EventType in_eventType, const std::vector<uint8_t>& in_sessionId, const std::vector<uint8_t>& in_data) override;
  ::ndk::ScopedAStatus onExpirationUpdate(const std::vector<uint8_t>& in_sessionId, int64_t in_expiryTimeInMS) override;
  ::ndk::ScopedAStatus onKeysChange(const std::vector<uint8_t>& in_sessionId, const std::vector<::aidl::android::hardware::drm::KeyStatus>& in_keyStatusList, bool in_hasNewUsableKey) override;
  ::ndk::ScopedAStatus onSessionLostState(const std::vector<uint8_t>& in_sessionId) override;
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
