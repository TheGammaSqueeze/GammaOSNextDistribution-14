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
#include <aidl/android/hardware/graphics/common/ExtendableType.h>
#include <aidl/android/hardware/graphics/common/PixelFormat.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl::android::hardware::graphics::common {
class ExtendableType;
}  // namespace aidl::android::hardware::graphics::common
namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace allocator {
class BufferDescriptorInfo {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  std::array<uint8_t, 128> name = {{}};
  int32_t width = 0;
  int32_t height = 0;
  int32_t layerCount = 0;
  ::aidl::android::hardware::graphics::common::PixelFormat format = ::aidl::android::hardware::graphics::common::PixelFormat::UNSPECIFIED;
  ::aidl::android::hardware::graphics::common::BufferUsage usage = ::aidl::android::hardware::graphics::common::BufferUsage::CPU_READ_NEVER;
  int64_t reservedSize = 0L;
  std::vector<::aidl::android::hardware::graphics::common::ExtendableType> additionalOptions;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const BufferDescriptorInfo& rhs) const {
    return std::tie(name, width, height, layerCount, format, usage, reservedSize, additionalOptions) != std::tie(rhs.name, rhs.width, rhs.height, rhs.layerCount, rhs.format, rhs.usage, rhs.reservedSize, rhs.additionalOptions);
  }
  inline bool operator<(const BufferDescriptorInfo& rhs) const {
    return std::tie(name, width, height, layerCount, format, usage, reservedSize, additionalOptions) < std::tie(rhs.name, rhs.width, rhs.height, rhs.layerCount, rhs.format, rhs.usage, rhs.reservedSize, rhs.additionalOptions);
  }
  inline bool operator<=(const BufferDescriptorInfo& rhs) const {
    return std::tie(name, width, height, layerCount, format, usage, reservedSize, additionalOptions) <= std::tie(rhs.name, rhs.width, rhs.height, rhs.layerCount, rhs.format, rhs.usage, rhs.reservedSize, rhs.additionalOptions);
  }
  inline bool operator==(const BufferDescriptorInfo& rhs) const {
    return std::tie(name, width, height, layerCount, format, usage, reservedSize, additionalOptions) == std::tie(rhs.name, rhs.width, rhs.height, rhs.layerCount, rhs.format, rhs.usage, rhs.reservedSize, rhs.additionalOptions);
  }
  inline bool operator>(const BufferDescriptorInfo& rhs) const {
    return std::tie(name, width, height, layerCount, format, usage, reservedSize, additionalOptions) > std::tie(rhs.name, rhs.width, rhs.height, rhs.layerCount, rhs.format, rhs.usage, rhs.reservedSize, rhs.additionalOptions);
  }
  inline bool operator>=(const BufferDescriptorInfo& rhs) const {
    return std::tie(name, width, height, layerCount, format, usage, reservedSize, additionalOptions) >= std::tie(rhs.name, rhs.width, rhs.height, rhs.layerCount, rhs.format, rhs.usage, rhs.reservedSize, rhs.additionalOptions);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "BufferDescriptorInfo{";
    os << "name: " << ::android::internal::ToString(name);
    os << ", width: " << ::android::internal::ToString(width);
    os << ", height: " << ::android::internal::ToString(height);
    os << ", layerCount: " << ::android::internal::ToString(layerCount);
    os << ", format: " << ::android::internal::ToString(format);
    os << ", usage: " << ::android::internal::ToString(usage);
    os << ", reservedSize: " << ::android::internal::ToString(reservedSize);
    os << ", additionalOptions: " << ::android::internal::ToString(additionalOptions);
    os << "}";
    return os.str();
  }
};
}  // namespace allocator
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
