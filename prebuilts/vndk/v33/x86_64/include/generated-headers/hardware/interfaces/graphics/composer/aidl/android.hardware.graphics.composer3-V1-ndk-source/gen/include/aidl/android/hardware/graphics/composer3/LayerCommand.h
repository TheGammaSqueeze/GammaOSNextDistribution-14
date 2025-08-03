#pragma once

#include <cstdint>
#include <memory>
#include <optional>
#include <string>
#include <vector>
#include <android/binder_interface_utils.h>
#include <android/binder_parcelable_utils.h>
#include <android/binder_to_string.h>
#include <aidl/android/hardware/common/NativeHandle.h>
#include <aidl/android/hardware/graphics/common/FRect.h>
#include <aidl/android/hardware/graphics/common/Point.h>
#include <aidl/android/hardware/graphics/common/Rect.h>
#include <aidl/android/hardware/graphics/composer3/Buffer.h>
#include <aidl/android/hardware/graphics/composer3/Color.h>
#include <aidl/android/hardware/graphics/composer3/LayerBrightness.h>
#include <aidl/android/hardware/graphics/composer3/ParcelableBlendMode.h>
#include <aidl/android/hardware/graphics/composer3/ParcelableComposition.h>
#include <aidl/android/hardware/graphics/composer3/ParcelableDataspace.h>
#include <aidl/android/hardware/graphics/composer3/ParcelableTransform.h>
#include <aidl/android/hardware/graphics/composer3/PerFrameMetadata.h>
#include <aidl/android/hardware/graphics/composer3/PerFrameMetadataBlob.h>
#include <aidl/android/hardware/graphics/composer3/PlaneAlpha.h>
#include <aidl/android/hardware/graphics/composer3/ZOrder.h>
#ifdef BINDER_STABILITY_SUPPORT
#include <android/binder_stability.h>
#endif  // BINDER_STABILITY_SUPPORT

namespace aidl {
namespace android {
namespace hardware {
namespace graphics {
namespace composer3 {
class LayerCommand {
public:
  typedef std::false_type fixed_size;
  static const char* descriptor;

  int64_t layer = 0L;
  std::optional<::aidl::android::hardware::graphics::common::Point> cursorPosition;
  std::optional<::aidl::android::hardware::graphics::composer3::Buffer> buffer;
  std::optional<std::vector<std::optional<::aidl::android::hardware::graphics::common::Rect>>> damage;
  std::optional<::aidl::android::hardware::graphics::composer3::ParcelableBlendMode> blendMode;
  std::optional<::aidl::android::hardware::graphics::composer3::Color> color;
  std::optional<::aidl::android::hardware::graphics::composer3::ParcelableComposition> composition;
  std::optional<::aidl::android::hardware::graphics::composer3::ParcelableDataspace> dataspace;
  std::optional<::aidl::android::hardware::graphics::common::Rect> displayFrame;
  std::optional<::aidl::android::hardware::graphics::composer3::PlaneAlpha> planeAlpha;
  std::optional<::aidl::android::hardware::common::NativeHandle> sidebandStream;
  std::optional<::aidl::android::hardware::graphics::common::FRect> sourceCrop;
  std::optional<::aidl::android::hardware::graphics::composer3::ParcelableTransform> transform;
  std::optional<std::vector<std::optional<::aidl::android::hardware::graphics::common::Rect>>> visibleRegion;
  std::optional<::aidl::android::hardware::graphics::composer3::ZOrder> z;
  std::optional<std::vector<float>> colorTransform;
  std::optional<::aidl::android::hardware::graphics::composer3::LayerBrightness> brightness;
  std::optional<std::vector<std::optional<::aidl::android::hardware::graphics::composer3::PerFrameMetadata>>> perFrameMetadata;
  std::optional<std::vector<std::optional<::aidl::android::hardware::graphics::composer3::PerFrameMetadataBlob>>> perFrameMetadataBlob;
  std::optional<std::vector<std::optional<::aidl::android::hardware::graphics::common::Rect>>> blockingRegion;

  binder_status_t readFromParcel(const AParcel* parcel);
  binder_status_t writeToParcel(AParcel* parcel) const;

  inline bool operator!=(const LayerCommand& rhs) const {
    return std::tie(layer, cursorPosition, buffer, damage, blendMode, color, composition, dataspace, displayFrame, planeAlpha, sidebandStream, sourceCrop, transform, visibleRegion, z, colorTransform, brightness, perFrameMetadata, perFrameMetadataBlob, blockingRegion) != std::tie(rhs.layer, rhs.cursorPosition, rhs.buffer, rhs.damage, rhs.blendMode, rhs.color, rhs.composition, rhs.dataspace, rhs.displayFrame, rhs.planeAlpha, rhs.sidebandStream, rhs.sourceCrop, rhs.transform, rhs.visibleRegion, rhs.z, rhs.colorTransform, rhs.brightness, rhs.perFrameMetadata, rhs.perFrameMetadataBlob, rhs.blockingRegion);
  }
  inline bool operator<(const LayerCommand& rhs) const {
    return std::tie(layer, cursorPosition, buffer, damage, blendMode, color, composition, dataspace, displayFrame, planeAlpha, sidebandStream, sourceCrop, transform, visibleRegion, z, colorTransform, brightness, perFrameMetadata, perFrameMetadataBlob, blockingRegion) < std::tie(rhs.layer, rhs.cursorPosition, rhs.buffer, rhs.damage, rhs.blendMode, rhs.color, rhs.composition, rhs.dataspace, rhs.displayFrame, rhs.planeAlpha, rhs.sidebandStream, rhs.sourceCrop, rhs.transform, rhs.visibleRegion, rhs.z, rhs.colorTransform, rhs.brightness, rhs.perFrameMetadata, rhs.perFrameMetadataBlob, rhs.blockingRegion);
  }
  inline bool operator<=(const LayerCommand& rhs) const {
    return std::tie(layer, cursorPosition, buffer, damage, blendMode, color, composition, dataspace, displayFrame, planeAlpha, sidebandStream, sourceCrop, transform, visibleRegion, z, colorTransform, brightness, perFrameMetadata, perFrameMetadataBlob, blockingRegion) <= std::tie(rhs.layer, rhs.cursorPosition, rhs.buffer, rhs.damage, rhs.blendMode, rhs.color, rhs.composition, rhs.dataspace, rhs.displayFrame, rhs.planeAlpha, rhs.sidebandStream, rhs.sourceCrop, rhs.transform, rhs.visibleRegion, rhs.z, rhs.colorTransform, rhs.brightness, rhs.perFrameMetadata, rhs.perFrameMetadataBlob, rhs.blockingRegion);
  }
  inline bool operator==(const LayerCommand& rhs) const {
    return std::tie(layer, cursorPosition, buffer, damage, blendMode, color, composition, dataspace, displayFrame, planeAlpha, sidebandStream, sourceCrop, transform, visibleRegion, z, colorTransform, brightness, perFrameMetadata, perFrameMetadataBlob, blockingRegion) == std::tie(rhs.layer, rhs.cursorPosition, rhs.buffer, rhs.damage, rhs.blendMode, rhs.color, rhs.composition, rhs.dataspace, rhs.displayFrame, rhs.planeAlpha, rhs.sidebandStream, rhs.sourceCrop, rhs.transform, rhs.visibleRegion, rhs.z, rhs.colorTransform, rhs.brightness, rhs.perFrameMetadata, rhs.perFrameMetadataBlob, rhs.blockingRegion);
  }
  inline bool operator>(const LayerCommand& rhs) const {
    return std::tie(layer, cursorPosition, buffer, damage, blendMode, color, composition, dataspace, displayFrame, planeAlpha, sidebandStream, sourceCrop, transform, visibleRegion, z, colorTransform, brightness, perFrameMetadata, perFrameMetadataBlob, blockingRegion) > std::tie(rhs.layer, rhs.cursorPosition, rhs.buffer, rhs.damage, rhs.blendMode, rhs.color, rhs.composition, rhs.dataspace, rhs.displayFrame, rhs.planeAlpha, rhs.sidebandStream, rhs.sourceCrop, rhs.transform, rhs.visibleRegion, rhs.z, rhs.colorTransform, rhs.brightness, rhs.perFrameMetadata, rhs.perFrameMetadataBlob, rhs.blockingRegion);
  }
  inline bool operator>=(const LayerCommand& rhs) const {
    return std::tie(layer, cursorPosition, buffer, damage, blendMode, color, composition, dataspace, displayFrame, planeAlpha, sidebandStream, sourceCrop, transform, visibleRegion, z, colorTransform, brightness, perFrameMetadata, perFrameMetadataBlob, blockingRegion) >= std::tie(rhs.layer, rhs.cursorPosition, rhs.buffer, rhs.damage, rhs.blendMode, rhs.color, rhs.composition, rhs.dataspace, rhs.displayFrame, rhs.planeAlpha, rhs.sidebandStream, rhs.sourceCrop, rhs.transform, rhs.visibleRegion, rhs.z, rhs.colorTransform, rhs.brightness, rhs.perFrameMetadata, rhs.perFrameMetadataBlob, rhs.blockingRegion);
  }

  static const ::ndk::parcelable_stability_t _aidl_stability = ::ndk::STABILITY_VINTF;
  inline std::string toString() const {
    std::ostringstream os;
    os << "LayerCommand{";
    os << "layer: " << ::android::internal::ToString(layer);
    os << ", cursorPosition: " << ::android::internal::ToString(cursorPosition);
    os << ", buffer: " << ::android::internal::ToString(buffer);
    os << ", damage: " << ::android::internal::ToString(damage);
    os << ", blendMode: " << ::android::internal::ToString(blendMode);
    os << ", color: " << ::android::internal::ToString(color);
    os << ", composition: " << ::android::internal::ToString(composition);
    os << ", dataspace: " << ::android::internal::ToString(dataspace);
    os << ", displayFrame: " << ::android::internal::ToString(displayFrame);
    os << ", planeAlpha: " << ::android::internal::ToString(planeAlpha);
    os << ", sidebandStream: " << ::android::internal::ToString(sidebandStream);
    os << ", sourceCrop: " << ::android::internal::ToString(sourceCrop);
    os << ", transform: " << ::android::internal::ToString(transform);
    os << ", visibleRegion: " << ::android::internal::ToString(visibleRegion);
    os << ", z: " << ::android::internal::ToString(z);
    os << ", colorTransform: " << ::android::internal::ToString(colorTransform);
    os << ", brightness: " << ::android::internal::ToString(brightness);
    os << ", perFrameMetadata: " << ::android::internal::ToString(perFrameMetadata);
    os << ", perFrameMetadataBlob: " << ::android::internal::ToString(perFrameMetadataBlob);
    os << ", blockingRegion: " << ::android::internal::ToString(blockingRegion);
    os << "}";
    return os.str();
  }
};
}  // namespace composer3
}  // namespace graphics
}  // namespace hardware
}  // namespace android
}  // namespace aidl
