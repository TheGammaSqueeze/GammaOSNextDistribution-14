#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/graphics/common/BufferUsage.h>
#include <aidl/android/hardware/graphics/common/Dataspace.h>
#include <aidl/android/hardware/graphics/common/PixelFormat.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace camera {
namespace device {
class HalStream {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int32_t id = 0;
  ::aidl::android::hardware::graphics::common::PixelFormat overrideFormat = ::aidl::android::hardware::graphics::common::PixelFormat(0);
  ::aidl::android::hardware::graphics::common::BufferUsage producerUsage = ::aidl::android::hardware::graphics::common::BufferUsage(0);
  ::aidl::android::hardware::graphics::common::BufferUsage consumerUsage = ::aidl::android::hardware::graphics::common::BufferUsage(0);
  int32_t maxBuffers = 0;
  ::aidl::android::hardware::graphics::common::Dataspace overrideDataSpace = ::aidl::android::hardware::graphics::common::Dataspace(0);
  std::string physicalCameraId;
  bool supportOffline = false;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const HalStream& rhs) const {
    return std::tie(id, overrideFormat, producerUsage, consumerUsage, maxBuffers, overrideDataSpace, physicalCameraId, supportOffline) != std::tie(rhs.id, rhs.overrideFormat, rhs.producerUsage, rhs.consumerUsage, rhs.maxBuffers, rhs.overrideDataSpace, rhs.physicalCameraId, rhs.supportOffline);
  }
  inline bool operator<(const HalStream& rhs) const {
    return std::tie(id, overrideFormat, producerUsage, consumerUsage, maxBuffers, overrideDataSpace, physicalCameraId, supportOffline) < std::tie(rhs.id, rhs.overrideFormat, rhs.producerUsage, rhs.consumerUsage, rhs.maxBuffers, rhs.overrideDataSpace, rhs.physicalCameraId, rhs.supportOffline);
  }
  inline bool operator<=(const HalStream& rhs) const {
    return std::tie(id, overrideFormat, producerUsage, consumerUsage, maxBuffers, overrideDataSpace, physicalCameraId, supportOffline) <= std::tie(rhs.id, rhs.overrideFormat, rhs.producerUsage, rhs.consumerUsage, rhs.maxBuffers, rhs.overrideDataSpace, rhs.physicalCameraId, rhs.supportOffline);
  }
  inline bool operator==(const HalStream& rhs) const {
    return std::tie(id, overrideFormat, producerUsage, consumerUsage, maxBuffers, overrideDataSpace, physicalCameraId, supportOffline) == std::tie(rhs.id, rhs.overrideFormat, rhs.producerUsage, rhs.consumerUsage, rhs.maxBuffers, rhs.overrideDataSpace, rhs.physicalCameraId, rhs.supportOffline);
  }
  inline bool operator>(const HalStream& rhs) const {
    return std::tie(id, overrideFormat, producerUsage, consumerUsage, maxBuffers, overrideDataSpace, physicalCameraId, supportOffline) > std::tie(rhs.id, rhs.overrideFormat, rhs.producerUsage, rhs.consumerUsage, rhs.maxBuffers, rhs.overrideDataSpace, rhs.physicalCameraId, rhs.supportOffline);
  }
  inline bool operator>=(const HalStream& rhs) const {
    return std::tie(id, overrideFormat, producerUsage, consumerUsage, maxBuffers, overrideDataSpace, physicalCameraId, supportOffline) >= std::tie(rhs.id, rhs.overrideFormat, rhs.producerUsage, rhs.consumerUsage, rhs.maxBuffers, rhs.overrideDataSpace, rhs.physicalCameraId, rhs.supportOffline);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "HalStream{";
    os << "id: " << ::android::internal::ToString(id);
    os << ", overrideFormat: " << ::android::internal::ToString(overrideFormat);
    os << ", producerUsage: " << ::android::internal::ToString(producerUsage);
    os << ", consumerUsage: " << ::android::internal::ToString(consumerUsage);
    os << ", maxBuffers: " << ::android::internal::ToString(maxBuffers);
    os << ", overrideDataSpace: " << ::android::internal::ToString(overrideDataSpace);
    os << ", physicalCameraId: " << ::android::internal::ToString(physicalCameraId);
    os << ", supportOffline: " << ::android::internal::ToString(supportOffline);
    os << "}";
    return os.str();
  }
};
}  // namespace device
}  // namespace camera
}  // namespace hardware
}  // namespace android
}  // namespace aidl
