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
#include <aidl/android/hardware/health/storage/Result.h>

namespace aidl {
namespace android {
namespace hardware {
namespace health {
namespace storage {
class IGarbageCollectCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IGarbageCollectCallback();
  virtual ~IGarbageCollectCallback();

  static const int32_t version = 1;
  static inline const std::string hash = "3828640730cbf161d79e54dafce9470fdafcca1e";
  static constexpr uint32_t TRANSACTION_onFinish = FIRST_CALL_TRANSACTION + 0;

  static std::shared_ptr<IGarbageCollectCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IGarbageCollectCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IGarbageCollectCallback>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IGarbageCollectCallback>& impl);
  static const std::shared_ptr<IGarbageCollectCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus onFinish(::aidl::android::hardware::health::storage::Result in_result) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IGarbageCollectCallback> default_impl;
};
class IGarbageCollectCallbackDefault : public IGarbageCollectCallback {
public:
  ::ndk::ScopedAStatus onFinish(::aidl::android::hardware::health::storage::Result in_result) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace storage
}  // namespace health
}  // namespace hardware
}  // namespace android
}  // namespace aidl
