#pragma once

#include <array>
#include <binder/Enums.h>
#include <cstdint>
#include <string>

namespace android {
namespace gui {
enum class FrameEvent : int32_t {
  POSTED = 0,
  REQUESTED_PRESENT = 1,
  LATCH = 2,
  ACQUIRE = 3,
  FIRST_REFRESH_START = 4,
  LAST_REFRESH_START = 5,
  GPU_COMPOSITION_DONE = 6,
  DISPLAY_PRESENT = 7,
  DEQUEUE_READY = 8,
  RELEASE = 9,
  EVENT_COUNT = 10,
};
}  // namespace gui
}  // namespace android
namespace android {
namespace gui {
[[nodiscard]] static inline std::string toString(FrameEvent val) {
  switch(val) {
  case FrameEvent::POSTED:
    return "POSTED";
  case FrameEvent::REQUESTED_PRESENT:
    return "REQUESTED_PRESENT";
  case FrameEvent::LATCH:
    return "LATCH";
  case FrameEvent::ACQUIRE:
    return "ACQUIRE";
  case FrameEvent::FIRST_REFRESH_START:
    return "FIRST_REFRESH_START";
  case FrameEvent::LAST_REFRESH_START:
    return "LAST_REFRESH_START";
  case FrameEvent::GPU_COMPOSITION_DONE:
    return "GPU_COMPOSITION_DONE";
  case FrameEvent::DISPLAY_PRESENT:
    return "DISPLAY_PRESENT";
  case FrameEvent::DEQUEUE_READY:
    return "DEQUEUE_READY";
  case FrameEvent::RELEASE:
    return "RELEASE";
  case FrameEvent::EVENT_COUNT:
    return "EVENT_COUNT";
  default:
    return std::to_string(static_cast<int32_t>(val));
  }
}
}  // namespace gui
}  // namespace android
namespace android {
namespace internal {
#pragma clang diagnostic push
#pragma clang diagnostic ignored "-Wc++17-extensions"
template <>
constexpr inline std::array<::android::gui::FrameEvent, 11> enum_values<::android::gui::FrameEvent> = {
  ::android::gui::FrameEvent::POSTED,
  ::android::gui::FrameEvent::REQUESTED_PRESENT,
  ::android::gui::FrameEvent::LATCH,
  ::android::gui::FrameEvent::ACQUIRE,
  ::android::gui::FrameEvent::FIRST_REFRESH_START,
  ::android::gui::FrameEvent::LAST_REFRESH_START,
  ::android::gui::FrameEvent::GPU_COMPOSITION_DONE,
  ::android::gui::FrameEvent::DISPLAY_PRESENT,
  ::android::gui::FrameEvent::DEQUEUE_READY,
  ::android::gui::FrameEvent::RELEASE,
  ::android::gui::FrameEvent::EVENT_COUNT,
};
#pragma clang diagnostic pop
}  // namespace internal
}  // namespace android
