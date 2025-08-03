#pragma once

#include "aidl/android/hardware/automotive/occupant_awareness/IOccupantAwareness.h"

#include <android/binder_ibinder.h>

namespace aidl {
namespace android {
namespace hardware {
namespace automotive {
namespace occupant_awareness {
class BpOccupantAwareness : public ::ndk::BpCInterface<IOccupantAwareness> {
public:
  BpOccupantAwareness(const ::ndk::SpAIBinder& binder);
  virtual ~BpOccupantAwareness();

  ::ndk::ScopedAStatus startDetection(::aidl::android::hardware::automotive::occupant_awareness::OccupantAwarenessStatus* _aidl_return) override;
  ::ndk::ScopedAStatus stopDetection(::aidl::android::hardware::automotive::occupant_awareness::OccupantAwarenessStatus* _aidl_return) override;
  ::ndk::ScopedAStatus getCapabilityForRole(::aidl::android::hardware::automotive::occupant_awareness::Role in_occupantRole, int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getState(::aidl::android::hardware::automotive::occupant_awareness::Role in_occupantRole, int32_t in_detectionCapability, ::aidl::android::hardware::automotive::occupant_awareness::OccupantAwarenessStatus* _aidl_return) override;
  ::ndk::ScopedAStatus setCallback(const std::shared_ptr<::aidl::android::hardware::automotive::occupant_awareness::IOccupantAwarenessClientCallback>& in_callback) override;
  ::ndk::ScopedAStatus getLatestDetection(::aidl::android::hardware::automotive::occupant_awareness::OccupantDetections* out_detections) override;
  ::ndk::ScopedAStatus getInterfaceVersion(int32_t* _aidl_return) override;
  ::ndk::ScopedAStatus getInterfaceHash(std::string* _aidl_return) override;
  int32_t _aidl_cached_version = -1;
  std::string _aidl_cached_hash = "-1";
  std::mutex _aidl_cached_hash_mutex;
};
}  // namespace occupant_awareness
}  // namespace automotive
}  // namespace hardware
}  // namespace android
}  // namespace aidl
