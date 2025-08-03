#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/graphics/composer3/Buffer.h>
#include <aidl/android/hardware/graphics/composer3/ClientTarget.h>
#include <aidl/android/hardware/graphics/composer3/ClockMonotonicTimestamp.h>
#include <aidl/android/hardware/graphics/composer3/DisplayBrightness.h>
#include <aidl/android/hardware/graphics/composer3/LayerCommand.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
class DisplayCommand {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int64_t display = 0L;
  std::vector<::aidl::android::hardware::graphics::composer3::LayerCommand> layers;
  std::optional<std::vector<float>> colorTransformMatrix;
  std::optional<::aidl::android::hardware::graphics::composer3::DisplayBrightness> brightness;
  std::optional<::aidl::android::hardware::graphics::composer3::ClientTarget> clientTarget;
  std::optional<::aidl::android::hardware::graphics::composer3::Buffer> virtualDisplayOutputBuffer;
  std::optional<::aidl::android::hardware::graphics::composer3::ClockMonotonicTimestamp> expectedPresentTime;
  bool validateDisplay = false;
  bool acceptDisplayChanges = false;
  bool presentDisplay = false;
  bool presentOrValidateDisplay = false;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const DisplayCommand& rhs) const {
    return std::tie(display, layers, colorTransformMatrix, brightness, clientTarget, virtualDisplayOutputBuffer, expectedPresentTime, validateDisplay, acceptDisplayChanges, presentDisplay, presentOrValidateDisplay) != std::tie(rhs.display, rhs.layers, rhs.colorTransformMatrix, rhs.brightness, rhs.clientTarget, rhs.virtualDisplayOutputBuffer, rhs.expectedPresentTime, rhs.validateDisplay, rhs.acceptDisplayChanges, rhs.presentDisplay, rhs.presentOrValidateDisplay);
  }
  inline bool operator<(const DisplayCommand& rhs) const {
    return std::tie(display, layers, colorTransformMatrix, brightness, clientTarget, virtualDisplayOutputBuffer, expectedPresentTime, validateDisplay, acceptDisplayChanges, presentDisplay, presentOrValidateDisplay) < std::tie(rhs.display, rhs.layers, rhs.colorTransformMatrix, rhs.brightness, rhs.clientTarget, rhs.virtualDisplayOutputBuffer, rhs.expectedPresentTime, rhs.validateDisplay, rhs.acceptDisplayChanges, rhs.presentDisplay, rhs.presentOrValidateDisplay);
  }
  inline bool operator<=(const DisplayCommand& rhs) const {
    return std::tie(display, layers, colorTransformMatrix, brightness, clientTarget, virtualDisplayOutputBuffer, expectedPresentTime, validateDisplay, acceptDisplayChanges, presentDisplay, presentOrValidateDisplay) <= std::tie(rhs.display, rhs.layers, rhs.colorTransformMatrix, rhs.brightness, rhs.clientTarget, rhs.virtualDisplayOutputBuffer, rhs.expectedPresentTime, rhs.validateDisplay, rhs.acceptDisplayChanges, rhs.presentDisplay, rhs.presentOrValidateDisplay);
  }
  inline bool operator==(const DisplayCommand& rhs) const {
    return std::tie(display, layers, colorTransformMatrix, brightness, clientTarget, virtualDisplayOutputBuffer, expectedPresentTime, validateDisplay, acceptDisplayChanges, presentDisplay, presentOrValidateDisplay) == std::tie(rhs.display, rhs.layers, rhs.colorTransformMatrix, rhs.brightness, rhs.clientTarget, rhs.virtualDisplayOutputBuffer, rhs.expectedPresentTime, rhs.validateDisplay, rhs.acceptDisplayChanges, rhs.presentDisplay, rhs.presentOrValidateDisplay);
  }
  inline bool operator>(const DisplayCommand& rhs) const {
    return std::tie(display, layers, colorTransformMatrix, brightness, clientTarget, virtualDisplayOutputBuffer, expectedPresentTime, validateDisplay, acceptDisplayChanges, presentDisplay, presentOrValidateDisplay) > std::tie(rhs.display, rhs.layers, rhs.colorTransformMatrix, rhs.brightness, rhs.clientTarget, rhs.virtualDisplayOutputBuffer, rhs.expectedPresentTime, rhs.validateDisplay, rhs.acceptDisplayChanges, rhs.presentDisplay, rhs.presentOrValidateDisplay);
  }
  inline bool operator>=(const DisplayCommand& rhs) const {
    return std::tie(display, layers, colorTransformMatrix, brightness, clientTarget, virtualDisplayOutputBuffer, expectedPresentTime, validateDisplay, acceptDisplayChanges, presentDisplay, presentOrValidateDisplay) >= std::tie(rhs.display, rhs.layers, rhs.colorTransformMatrix, rhs.brightness, rhs.clientTarget, rhs.virtualDisplayOutputBuffer, rhs.expectedPresentTime, rhs.validateDisplay, rhs.acceptDisplayChanges, rhs.presentDisplay, rhs.presentOrValidateDisplay);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "DisplayCommand{";
    os << "display: " << ::android::internal::ToString(display);
    os << ", layers: " << ::android::internal::ToString(layers);
    os << ", colorTransformMatrix: " << ::android::internal::ToString(colorTransformMatrix);
    os << ", brightness: " << ::android::internal::ToString(brightness);
    os << ", clientTarget: " << ::android::internal::ToString(clientTarget);
    os << ", virtualDisplayOutputBuffer: " << ::android::internal::ToString(virtualDisplayOutputBuffer);
    os << ", expectedPresentTime: " << ::android::internal::ToString(expectedPresentTime);
    os << ", validateDisplay: " << ::android::internal::ToString(validateDisplay);
    os << ", acceptDisplayChanges: " << ::android::internal::ToString(acceptDisplayChanges);
    os << ", presentDisplay: " << ::android::internal::ToString(presentDisplay);
    os << ", presentOrValidateDisplay: " << ::android::internal::ToString(presentOrValidateDisplay);
    os << "}";
    return os.str();
  }
};
}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
