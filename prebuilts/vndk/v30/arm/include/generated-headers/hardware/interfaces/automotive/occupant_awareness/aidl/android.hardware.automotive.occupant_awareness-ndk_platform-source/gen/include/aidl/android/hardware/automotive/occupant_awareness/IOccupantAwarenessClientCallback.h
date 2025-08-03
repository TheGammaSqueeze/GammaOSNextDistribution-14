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
#include <aidl/android/hardware/automotive/occupant_awareness/OccupantAwarenessStatus.h>
#include <aidl/android/hardware/automotive/occupant_awareness/OccupantDetections.h>

namespace aidl {
namespace android {
namespace hardware {
namespace automotive {
namespace occupant_awareness {
class IOccupantAwarenessClientCallback : public ::ndk::ICInterface {
public:
  static const char* descriptor;
  IOccupantAwarenessClientCallback();
  virtual ~IOccupantAwarenessClientCallback();


  static const int32_t version = 1;
  static inline const std::string hash = "3614b1c47ed7be85c1e77554e7f04966cf35b465";

  static std::shared_ptr<IOccupantAwarenessClientCallback> fromBinder(const ::ndk::SpAIBinder& binder);
  static binder_status_t writeToParcel(AParcel* parcel, const std::shared_ptr<IOccupantAwarenessClientCallback>& instance);
  static binder_status_t readFromParcel(const AParcel* parcel, std::shared_ptr<IOccupantAwarenessClientCallback>* instance);
  static bool setDefaultImpl(std::shared_ptr<IOccupantAwarenessClientCallback> impl);
  static const std::shared_ptr<IOccupantAwarenessClientCallback>& getDefaultImpl();
  virtual ::ndk::ScopedAStatus onSystemStatusChanged(int32_t in_detectionFlags, ::aidl::android::hardware::automotive::occupant_awareness::OccupantAwarenessStatus in_status) = 0;
  virtual ::ndk::ScopedAStatus onDetectionEvent(const ::aidl::android::hardware::automotive::occupant_awareness::OccupantDetections& in_detections) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) = 0;
  virtual ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) = 0;
private:
  static std::shared_ptr<IOccupantAwarenessClientCallback> default_impl;
};
class IOccupantAwarenessClientCallbackDefault : public IOccupantAwarenessClientCallback {
public:
  ::ndk::ScopedAStatus onSystemStatusChanged(int32_t in_detectionFlags, ::aidl::android::hardware::automotive::occupant_awareness::OccupantAwarenessStatus in_status) override;
  ::ndk::ScopedAStatus onDetectionEvent(const ::aidl::android::hardware::automotive::occupant_awareness::OccupantDetections& in_detections) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  ::ndk::SpAIBinder asBinder() override;
  bool isRemote() override;
};
}  // namespace occupant_awareness
}  // namespace automotive
}  // namespace hardware
}  // namespace android
}  // namespace aidl
