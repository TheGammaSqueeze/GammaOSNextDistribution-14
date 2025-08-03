#ifndef AIDL_GENERATED_ANDROID_VIEW_LAYER_METADATA_KEY_H_
#define AIDL_GENERATED_ANDROID_VIEW_LAYER_METADATA_KEY_H_

#include <array>
#include <binder/Enums.h>
#include <cstdint>
#include <string>

namespace android {

namespace view {

enum class LayerMetadataKey : int32_t {
  METADATA_OWNER_UID = 1,
  METADATA_WINDOW_TYPE = 2,
  METADATA_TASK_ID = 3,
  METADATA_MOUSE_CURSOR = 4,
};

static inline std::string toString(LayerMetadataKey val) {
  switch(val) {
  case LayerMetadataKey::METADATA_OWNER_UID:
    return "METADATA_OWNER_UID";
  case LayerMetadataKey::METADATA_WINDOW_TYPE:
    return "METADATA_WINDOW_TYPE";
  case LayerMetadataKey::METADATA_TASK_ID:
    return "METADATA_TASK_ID";
  case LayerMetadataKey::METADATA_MOUSE_CURSOR:
    return "METADATA_MOUSE_CURSOR";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}

}  // namespace view

}  // namespace android
namespace android {

namespace internal {

#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<::android::view::LayerMetadataKey, 4> enum_values<::android::view::LayerMetadataKey> = {
  ::android::view::LayerMetadataKey::METADATA_OWNER_UID,
  ::android::view::LayerMetadataKey::METADATA_WINDOW_TYPE,
  ::android::view::LayerMetadataKey::METADATA_TASK_ID,
  ::android::view::LayerMetadataKey::METADATA_MOUSE_CURSOR,
};
#pragma clang diagnostic pop

}  // namespace internal

}  // namespace android

#endif  // AIDL_GENERATED_ANDROID_VIEW_LAYER_METADATA_KEY_H_
