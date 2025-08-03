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
#include <aidl/android/hardware/health/storage/IGarbageCollectCallback.h>

namespace aidl {
namespace android {
namespace hardware {
namespace health {
namespace storage {
class IStorage : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IStorage();
  virtual ~IStorage();

  static const int32_t version = 1;
  static inline const std::string hash = "3828640730cbf161d79e54dafce9470fdafcca1e";
  static constexpr uint32_t TRANSACTION_garbageCollect = FIRST_CALL_TRANSACTION + 0;

  static std::shared_ptr<IStorage> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IStorage>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IStorage>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IStorage>& impl);
  static const std::shared_ptr<IStorage>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus garbageCollect(int64_t in_timeoutSeconds, const std::shared_ptr<::aidl::android::hardware::health::storage::IGarbageCollectCallback>& in_callback) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IStorage> default_impl;
};
class IStorageDefault : public IStorage {
public:
  ::ndk::ScopedAStatus garbageCollect(int64_t in_timeoutSeconds, const std::shared_ptr<::aidl::android::hardware::health::storage::IGarbageCollectCallback>& in_callback) override;
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
