#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/drm/DecryptArgs.h>
#include <aidl/android/hardware/drm/LogMessage.h>
#include <aidl/android/hardware/drm/SharedBuffer.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace drm {
class ICryptoPlugin : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  ICryptoPlugin();
  virtual ~ICryptoPlugin();

  static const int32_t version = 1;
  static inline const std::string hash = "7b4b0a0f36a7a6bb22d2016375e4a9d4a033592f";
  static constexpr uint32_t TRANSACTION_decrypt = FIRST_CALL_TRANSACTION + 0;
  static constexpr uint32_t TRANSACTION_getLogMessages = FIRST_CALL_TRANSACTION + 1;
  static constexpr uint32_t TRANSACTION_notifyResolution = FIRST_CALL_TRANSACTION + 2;
  static constexpr uint32_t TRANSACTION_requiresSecureDecoderComponent = FIRST_CALL_TRANSACTION + 3;
  static constexpr uint32_t TRANSACTION_setMediaDrmSession = FIRST_CALL_TRANSACTION + 4;
  static constexpr uint32_t TRANSACTION_setSharedBufferBase = FIRST_CALL_TRANSACTION + 5;

  static std::shared_ptr<ICryptoPlugin> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<ICryptoPlugin>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<ICryptoPlugin>* instance);
  static bool setDefaultImpl(const std::shared_ptr<ICryptoPlugin>& impl);
  static const std::shared_ptr<ICryptoPlugin>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus decrypt(const ::aidl::android::hardware::drm::DecryptArgs& in_args, int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getLogMessages(std::vector<::aidl::android::hardware::drm::LogMessage>* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus notifyResolution(int32_t in_width, int32_t in_height) = 0;
  virtual ::ndk::ScopedAStatus requiresSecureDecoderComponent(const std::string& in_mime, bool* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus setMediaDrmSession(const std::vector<uint8_t>& in_sessionId) = 0;
  virtual ::ndk::ScopedAStatus setSharedBufferBase(const ::aidl::android::hardware::drm::SharedBuffer& in_base) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<ICryptoPlugin> default_impl;
};
class ICryptoPluginDefault : public ICryptoPlugin {
public:
  ::ndk::ScopedAStatus decrypt(const ::aidl::android::hardware::drm::DecryptArgs& in_args, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getLogMessages(std::vector<::aidl::android::hardware::drm::LogMessage>* _aidl_return) override;
  ::ndk::ScopedAStatus notifyResolution(int32_t in_width, int32_t in_height) override;
  ::ndk::ScopedAStatus requiresSecureDecoderComponent(const std::string& in_mime, bool* _aidl_return) override;
  ::ndk::ScopedAStatus setMediaDrmSession(const std::vector<uint8_t>& in_sessionId) override;
  ::ndk::ScopedAStatus setSharedBufferBase(const ::aidl::android::hardware::drm::SharedBuffer& in_base) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace drm
}  // namespace hardware
}  // namespace android
}  // namespace aidl
