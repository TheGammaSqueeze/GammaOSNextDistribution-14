#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/drm/EventType.h>
#include <aidl/android/hardware/drm/KeyStatus.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace drm {
class IDrmPluginListener : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IDrmPluginListener();
  virtual ~IDrmPluginListener();

  static const int32_t version = 1;
  static inline const std::string hash = "7b4b0a0f36a7a6bb22d2016375e4a9d4a033592f";
  static constexpr uint32_t TRANSACTION_onEvent = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_onExpirationUpdate = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_onKeysChange = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_onSessionLostState = FIRST_CALL_TRANSACTION + 3;

  static std::shared_ptr<IDrmPluginListener> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IDrmPluginListener>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IDrmPluginListener>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IDrmPluginListener>& impl);
  static const std::shared_ptr<IDrmPluginListener>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus onEvent(::aidl::android::hardware::drm::EventType in_eventType, const std::vector<uint8_t>& in_sessionId, const std::vector<uint8_t>& in_data) = 0;
  virtual ::ndk::ScopedAStatus onExpirationUpdate(const std::vector<uint8_t>& in_sessionId, int64_t in_expiryTimeInMS) = 0;
  virtual ::ndk::ScopedAStatus onKeysChange(const std::vector<uint8_t>& in_sessionId, const std::vector<::aidl::android::hardware::drm::KeyStatus>& in_keyStatusList, bool in_hasNewUsableKey) = 0;
  virtual ::ndk::ScopedAStatus onSessionLostState(const std::vector<uint8_t>& in_sessionId) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IDrmPluginListener> default_impl;
};
class IDrmPluginListenerDefault : public IDrmPluginListener {
public:
  ::ndk::ScopedAStatus onEvent(::aidl::android::hardware::drm::EventType in_eventType, const std::vector<uint8_t>& in_sessionId, const std::vector<uint8_t>& in_data) override;
  ::ndk::ScopedAStatus onExpirationUpdate(const std::vector<uint8_t>& in_sessionId, int64_t in_expiryTimeInMS) override;
  ::ndk::ScopedAStatus onKeysChange(const std::vector<uint8_t>& in_sessionId, const std::vector<::aidl::android::hardware::drm::KeyStatus>& in_keyStatusList, bool in_hasNewUsableKey) override;
  ::ndk::ScopedAStatus onSessionLostState(const std::vector<uint8_t>& in_sessionId) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
