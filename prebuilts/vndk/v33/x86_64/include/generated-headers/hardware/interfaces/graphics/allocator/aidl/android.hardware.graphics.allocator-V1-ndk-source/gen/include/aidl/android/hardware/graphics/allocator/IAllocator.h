#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <aidl/android/hardware/graphics/allocator/AllocationResult.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace allocator {
class IAllocator : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IAllocator();
  virtual ~IAllocator();

  static const int32_t version = 1;
  static inline const std::string hash = "0dab5f7fc46756d126ac127cd6033fc242eff0aa";
  static constexpr uint32_t TRANSACTION_allocate = FIRST_CALL_TRANSACTION + 0;

  static std::shared_ptr<IAllocator> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IAllocator>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IAllocator>* instance);
  static bool setDefaultImpl(const std::shared_ptr<IAllocator>& impl);
  static const std::shared_ptr<IAllocator>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus allocate(const std::vector<uint8_t>& in_descriptor, int32_t in_count, ::aidl::android::hardware::graphics::allocator::AllocationResult* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IAllocator> default_impl;
};
class IAllocatorDefault : public IAllocator {
public:
  ::ndk::ScopedAStatus allocate(const std::vector<uint8_t>& in_descriptor, int32_t in_count, ::aidl::android::hardware::graphics::allocator::AllocationResult* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace allocator
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
