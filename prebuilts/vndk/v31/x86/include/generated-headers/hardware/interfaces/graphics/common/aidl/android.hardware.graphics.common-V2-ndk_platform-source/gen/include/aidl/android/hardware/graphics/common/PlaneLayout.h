#pragma once
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT
#include <aidl/android/hardware/graphics/common/PlaneLayoutComponent.h>
namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace common {
class PlaneLayout {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::vector<::aidl::android::hardware::graphics::common::PlaneLayoutComponent> components;
  int64_t offsetInBytes = 0L;
  int64_t sampleIncrementInBits = 0L;
  int64_t strideInBytes = 0L;
  int64_t widthInSamples = 0L;
  int64_t heightInSamples = 0L;
  int64_t totalSizeInBytes = 0L;
  int64_t horizontalSubsampling = 0L;
  int64_t verticalSubsampling = 0L;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const PlaneLayout& rhs) const {
    return std::tie(components, offsetInBytes, sampleIncrementInBits, strideInBytes, widthInSamples, heightInSamples, totalSizeInBytes, horizontalSubsampling, verticalSubsampling) != std::tie(rhs.components, rhs.offsetInBytes, rhs.sampleIncrementInBits, rhs.strideInBytes, rhs.widthInSamples, rhs.heightInSamples, rhs.totalSizeInBytes, rhs.horizontalSubsampling, rhs.verticalSubsampling);
  }
  inline bool operator<(const PlaneLayout& rhs) const {
    return std::tie(components, offsetInBytes, sampleIncrementInBits, strideInBytes, widthInSamples, heightInSamples, totalSizeInBytes, horizontalSubsampling, verticalSubsampling) < std::tie(rhs.components, rhs.offsetInBytes, rhs.sampleIncrementInBits, rhs.strideInBytes, rhs.widthInSamples, rhs.heightInSamples, rhs.totalSizeInBytes, rhs.horizontalSubsampling, rhs.verticalSubsampling);
  }
  inline bool operator<=(const PlaneLayout& rhs) const {
    return std::tie(components, offsetInBytes, sampleIncrementInBits, strideInBytes, widthInSamples, heightInSamples, totalSizeInBytes, horizontalSubsampling, verticalSubsampling) <= std::tie(rhs.components, rhs.offsetInBytes, rhs.sampleIncrementInBits, rhs.strideInBytes, rhs.widthInSamples, rhs.heightInSamples, rhs.totalSizeInBytes, rhs.horizontalSubsampling, rhs.verticalSubsampling);
  }
  inline bool operator==(const PlaneLayout& rhs) const {
    return std::tie(components, offsetInBytes, sampleIncrementInBits, strideInBytes, widthInSamples, heightInSamples, totalSizeInBytes, horizontalSubsampling, verticalSubsampling) == std::tie(rhs.components, rhs.offsetInBytes, rhs.sampleIncrementInBits, rhs.strideInBytes, rhs.widthInSamples, rhs.heightInSamples, rhs.totalSizeInBytes, rhs.horizontalSubsampling, rhs.verticalSubsampling);
  }
  inline bool operator>(const PlaneLayout& rhs) const {
    return std::tie(components, offsetInBytes, sampleIncrementInBits, strideInBytes, widthInSamples, heightInSamples, totalSizeInBytes, horizontalSubsampling, verticalSubsampling) > std::tie(rhs.components, rhs.offsetInBytes, rhs.sampleIncrementInBits, rhs.strideInBytes, rhs.widthInSamples, rhs.heightInSamples, rhs.totalSizeInBytes, rhs.horizontalSubsampling, rhs.verticalSubsampling);
  }
  inline bool operator>=(const PlaneLayout& rhs) const {
    return std::tie(components, offsetInBytes, sampleIncrementInBits, strideInBytes, widthInSamples, heightInSamples, totalSizeInBytes, horizontalSubsampling, verticalSubsampling) >= std::tie(rhs.components, rhs.offsetInBytes, rhs.sampleIncrementInBits, rhs.strideInBytes, rhs.widthInSamples, rhs.heightInSamples, rhs.totalSizeInBytes, rhs.horizontalSubsampling, rhs.verticalSubsampling);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "PlaneLayout{";
    os << "components: " << ::android::internal::ToString(components);
    os << ", offsetInBytes: " << ::android::internal::ToString(offsetInBytes);
    os << ", sampleIncrementInBits: " << ::android::internal::ToString(sampleIncrementInBits);
    os << ", strideInBytes: " << ::android::internal::ToString(strideInBytes);
    os << ", widthInSamples: " << ::android::internal::ToString(widthInSamples);
    os << ", heightInSamples: " << ::android::internal::ToString(heightInSamples);
    os << ", totalSizeInBytes: " << ::android::internal::ToString(totalSizeInBytes);
    os << ", horizontalSubsampling: " << ::android::internal::ToString(horizontalSubsampling);
    os << ", verticalSubsampling: " << ::android::internal::ToString(verticalSubsampling);
    os << "}";
    return os.str();
  }
};
}  // namespace common
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
