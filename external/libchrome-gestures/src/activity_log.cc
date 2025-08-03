// Copyright 2012 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "include/activity_log.h"

#include <errno.h>
#include <fcntl.h>
#include <set>
#include <string>
#include <sys/stat.h>
#include <sys/types.h>

#include <json/value.h>
#include <json/writer.h>

#include "include/file_util.h"
#include "include/logging.h"
#include "include/prop_registry.h"
#include "include/string_util.h"

#define QUINTTAP_COUNT 5  /* BTN_TOOL_QUINTTAP - Five fingers on trackpad */

using std::set;
using std::string;

namespace {

// Helper to std::visit with lambdas.
template <typename... V>
struct Visitor : V... {
  using V::operator()...;
};
// Explicit deduction guide (not needed as of C++20).
template <typename... V>
Visitor(V...) -> Visitor<V...>;

} // namespace

namespace gestures {

ActivityLog::ActivityLog(PropRegistry* prop_reg)
    : head_idx_(0), size_(0), max_fingers_(0), hwprops_(),
      prop_reg_(prop_reg) {}

void ActivityLog::SetHardwareProperties(const HardwareProperties& hwprops) {
  hwprops_ = hwprops;

  // For old devices(such as mario, alex, zgb..), the reporting touch count
  // or 'max_touch_cnt' will be less than number of slots or 'max_finger_cnt'
  // they support. As kernel evdev drivers do not have a bitset to report
  // touch count greater than five (bitset for five-fingers gesture is
  // BTN_TOOL_QUINTAP), we respect 'max_finger_cnt' than 'max_touch_cnt'
  // reported from kernel driver as the 'max_fingers_' instead.
  if (hwprops.max_touch_cnt < QUINTTAP_COUNT) {
    max_fingers_ = std::min<size_t>(hwprops.max_finger_cnt,
                                    hwprops.max_touch_cnt);
  } else {
    max_fingers_ = std::max<size_t>(hwprops.max_finger_cnt,
                                    hwprops.max_touch_cnt);
  }

  finger_states_.reset(new FingerState[kBufferSize * max_fingers_]);
}

void ActivityLog::LogHardwareState(const HardwareState& hwstate) {
  Entry* entry = PushBack();
  entry->details = hwstate;
  HardwareState& entry_hwstate = std::get<HardwareState>(entry->details);
  if (hwstate.finger_cnt > max_fingers_) {
    Err("Too many fingers! Max is %zu, but I got %d",
        max_fingers_, hwstate.finger_cnt);
    entry_hwstate.fingers = nullptr;
    entry_hwstate.finger_cnt = 0;
    return;
  }
  if (!finger_states_.get())
    return;
  entry_hwstate.fingers = &finger_states_[TailIdx() * max_fingers_];
  std::copy(&hwstate.fingers[0], &hwstate.fingers[hwstate.finger_cnt],
            entry_hwstate.fingers);
}

void ActivityLog::LogTimerCallback(stime_t now) {
  Entry* entry = PushBack();
  entry->details = TimerCallbackEntry{now};
}

void ActivityLog::LogCallbackRequest(stime_t when) {
  Entry* entry = PushBack();
  entry->details = CallbackRequestEntry{when};
}

void ActivityLog::LogGesture(const Gesture& gesture) {
  Entry* entry = PushBack();
  entry->details = gesture;
}

void ActivityLog::LogPropChange(const PropChangeEntry& prop_change) {
  Entry* entry = PushBack();
  entry->details = prop_change;
}

void ActivityLog::LogGestureConsume(
    const std::string& name, const Gesture& gesture) {
  GestureConsume gesture_consume { name, gesture };
  Entry* entry = PushBack();
  entry->details = gesture_consume;
}

void ActivityLog::LogGestureProduce(
    const std::string& name, const Gesture& gesture) {
  GestureProduce gesture_produce { name, gesture };
  Entry* entry = PushBack();
  entry->details = gesture_produce;
}

void ActivityLog::LogHardwareStatePre(const std::string& name,
                                      const HardwareState& hwstate) {
  HardwareStatePre hwstate_pre { name, hwstate };
  Entry* entry = PushBack();
  entry->details = hwstate_pre;
}

void ActivityLog::LogHardwareStatePost(const std::string& name,
                                       const HardwareState& hwstate) {
  HardwareStatePost hwstate_post { name, hwstate };
  Entry* entry = PushBack();
  entry->details = hwstate_post;
}

void ActivityLog::LogHandleTimerPre(const std::string& name,
                                    stime_t now, const stime_t* timeout) {
  HandleTimerPre handle;
  handle.name = name;
  handle.now = now;
  handle.timeout_is_present = (timeout != nullptr);
  handle.timeout = (timeout == nullptr) ? 0 : *timeout;
  Entry* entry = PushBack();
  entry->details = handle;
}

void ActivityLog::LogHandleTimerPost(const std::string& name,
                                     stime_t now, const stime_t* timeout) {
  HandleTimerPost handle;
  handle.name = name;
  handle.now = now;
  handle.timeout_is_present = (timeout != nullptr);
  handle.timeout = (timeout == nullptr) ? 0 : *timeout;
  Entry* entry = PushBack();
  entry->details = handle;
}

void ActivityLog::Dump(const char* filename) {
  string data = Encode();
  WriteFile(filename, data.c_str(), data.size());
}

ActivityLog::Entry* ActivityLog::PushBack() {
  if (size_ == kBufferSize) {
    Entry* ret = &buffer_[head_idx_];
    head_idx_ = (head_idx_ + 1) % kBufferSize;
    return ret;
  }
  ++size_;
  return &buffer_[TailIdx()];
}

Json::Value ActivityLog::EncodeHardwareProperties() const {
  Json::Value ret(Json::objectValue);
  ret[kKeyHardwarePropLeft] = Json::Value(hwprops_.left);
  ret[kKeyHardwarePropTop] = Json::Value(hwprops_.top);
  ret[kKeyHardwarePropRight] = Json::Value(hwprops_.right);
  ret[kKeyHardwarePropBottom] = Json::Value(hwprops_.bottom);
  ret[kKeyHardwarePropXResolution] = Json::Value(hwprops_.res_x);
  ret[kKeyHardwarePropYResolution] = Json::Value(hwprops_.res_y);
  ret[kKeyHardwarePropXDpi] = Json::Value(hwprops_.screen_x_dpi);
  ret[kKeyHardwarePropYDpi] = Json::Value(hwprops_.screen_y_dpi);
  ret[kKeyHardwarePropOrientationMinimum] =
      Json::Value(hwprops_.orientation_minimum);
  ret[kKeyHardwarePropOrientationMaximum] =
      Json::Value(hwprops_.orientation_maximum);
  ret[kKeyHardwarePropMaxFingerCount] = Json::Value(hwprops_.max_finger_cnt);
  ret[kKeyHardwarePropMaxTouchCount] = Json::Value(hwprops_.max_touch_cnt);

  ret[kKeyHardwarePropSupportsT5R2] = Json::Value(hwprops_.supports_t5r2 != 0);
  ret[kKeyHardwarePropSemiMt] = Json::Value(hwprops_.support_semi_mt != 0);
  ret[kKeyHardwarePropIsButtonPad] = Json::Value(hwprops_.is_button_pad != 0);
  ret[kKeyHardwarePropHasWheel] = Json::Value(hwprops_.has_wheel != 0);
  return ret;
}

Json::Value ActivityLog::EncodeHardwareStateCommon(
    const HardwareState& hwstate) {
  Json::Value ret(Json::objectValue);
  ret[kKeyHardwareStateButtonsDown] = Json::Value(hwstate.buttons_down);
  ret[kKeyHardwareStateTouchCnt] = Json::Value(hwstate.touch_cnt);
  ret[kKeyHardwareStateTimestamp] = Json::Value(hwstate.timestamp);
  Json::Value fingers(Json::arrayValue);
  for (size_t i = 0; i < hwstate.finger_cnt; ++i) {
    if (hwstate.fingers == nullptr) {
      Err("Have finger_cnt %d but fingers is null!", hwstate.finger_cnt);
      break;
    }
    const FingerState& fs = hwstate.fingers[i];
    Json::Value finger(Json::objectValue);
    finger[kKeyFingerStateTouchMajor] = Json::Value(fs.touch_major);
    finger[kKeyFingerStateTouchMinor] = Json::Value(fs.touch_minor);
    finger[kKeyFingerStateWidthMajor] = Json::Value(fs.width_major);
    finger[kKeyFingerStateWidthMinor] = Json::Value(fs.width_minor);
    finger[kKeyFingerStatePressure] = Json::Value(fs.pressure);
    finger[kKeyFingerStateOrientation] = Json::Value(fs.orientation);
    finger[kKeyFingerStatePositionX] = Json::Value(fs.position_x);
    finger[kKeyFingerStatePositionY] = Json::Value(fs.position_y);
    finger[kKeyFingerStateTrackingId] = Json::Value(fs.tracking_id);
    finger[kKeyFingerStateFlags] = Json::Value(static_cast<int>(fs.flags));
    fingers.append(finger);
  }
  ret[kKeyHardwareStateFingers] = fingers;
  ret[kKeyHardwareStateRelX] = Json::Value(hwstate.rel_x);
  ret[kKeyHardwareStateRelY] = Json::Value(hwstate.rel_y);
  ret[kKeyHardwareStateRelWheel] = Json::Value(hwstate.rel_wheel);
  ret[kKeyHardwareStateRelHWheel] = Json::Value(hwstate.rel_hwheel);
  return ret;
}

Json::Value ActivityLog::EncodeHardwareState(const HardwareState& hwstate) {
  auto ret = EncodeHardwareStateCommon(hwstate);
  ret[kKeyType] = Json::Value(kKeyHardwareState);
  return ret;
}

Json::Value ActivityLog::EncodeHardwareState(
    const HardwareStatePre& pre_hwstate) {
  auto ret = EncodeHardwareStateCommon(pre_hwstate.hwstate);
  ret[kKeyType] = Json::Value(kKeyHardwareStatePre);
  ret[kKeyMethodName] = Json::Value(pre_hwstate.name);
  return ret;
}

Json::Value ActivityLog::EncodeHardwareState(
    const HardwareStatePost& post_hwstate) {
  auto ret = EncodeHardwareStateCommon(post_hwstate.hwstate);
  ret[kKeyType] = Json::Value(kKeyHardwareStatePost);
  ret[kKeyMethodName] = Json::Value(post_hwstate.name);
  return ret;
}

Json::Value ActivityLog::EncodeHandleTimer(const HandleTimerPre& handle) {
  Json::Value ret(Json::objectValue);
  ret[kKeyType] = Json::Value(kKeyHandleTimerPre);
  ret[kKeyMethodName] = Json::Value(handle.name);
  ret[kKeyTimerNow] = Json::Value(handle.now);
  if (handle.timeout_is_present)
    ret[kKeyHandleTimerTimeout] = Json::Value(handle.timeout);
  return ret;
}

Json::Value ActivityLog::EncodeHandleTimer(const HandleTimerPost& handle) {
  Json::Value ret(Json::objectValue);
  ret[kKeyType] = Json::Value(kKeyHandleTimerPost);
  ret[kKeyMethodName] = Json::Value(handle.name);
  ret[kKeyTimerNow] = Json::Value(handle.now);
  if (handle.timeout_is_present)
    ret[kKeyHandleTimerTimeout] = Json::Value(handle.timeout);
  return ret;
}

Json::Value ActivityLog::EncodeTimerCallback(stime_t timestamp) {
  Json::Value ret(Json::objectValue);
  ret[kKeyType] = Json::Value(kKeyTimerCallback);
  ret[kKeyTimerNow] = Json::Value(timestamp);
  return ret;
}

Json::Value ActivityLog::EncodeCallbackRequest(stime_t timestamp) {
  Json::Value ret(Json::objectValue);
  ret[kKeyType] = Json::Value(kKeyCallbackRequest);
  ret[kKeyCallbackRequestWhen] = Json::Value(timestamp);
  return ret;
}

Json::Value ActivityLog::EncodeGestureCommon(const Gesture& gesture) {
  Json::Value ret(Json::objectValue);
  ret[kKeyGestureStartTime] = Json::Value(gesture.start_time);
  ret[kKeyGestureEndTime] = Json::Value(gesture.end_time);

  switch (gesture.type) {
    case kGestureTypeNull:
      ret[kKeyGestureType] = Json::Value("null");
      break;
    case kGestureTypeContactInitiated:
      ret[kKeyGestureType] = Json::Value(kValueGestureTypeContactInitiated);
      break;
    case kGestureTypeMove:
      ret[kKeyGestureType] = Json::Value(kValueGestureTypeMove);
      ret[kKeyGestureDX] = Json::Value(gesture.details.move.dx);
      ret[kKeyGestureDY] = Json::Value(gesture.details.move.dy);
      ret[kKeyGestureOrdinalDX] = Json::Value(gesture.details.move.ordinal_dx);
      ret[kKeyGestureOrdinalDY] = Json::Value(gesture.details.move.ordinal_dy);
      break;
    case kGestureTypeScroll:
      ret[kKeyGestureType] = Json::Value(kValueGestureTypeScroll);
      ret[kKeyGestureDX] = Json::Value(gesture.details.scroll.dx);
      ret[kKeyGestureDY] = Json::Value(gesture.details.scroll.dy);
      ret[kKeyGestureOrdinalDX] =
          Json::Value(gesture.details.scroll.ordinal_dx);
      ret[kKeyGestureOrdinalDY] =
          Json::Value(gesture.details.scroll.ordinal_dy);
      break;
    case kGestureTypeMouseWheel:
      ret[kKeyGestureType] = Json::Value(kValueGestureTypeMouseWheel);
      ret[kKeyGestureDX] = Json::Value(gesture.details.wheel.dx);
      ret[kKeyGestureDY] = Json::Value(gesture.details.wheel.dy);
      ret[kKeyGestureMouseWheelTicksDX] =
          Json::Value(gesture.details.wheel.tick_120ths_dx);
      ret[kKeyGestureMouseWheelTicksDY] =
          Json::Value(gesture.details.wheel.tick_120ths_dy);
      break;
    case kGestureTypePinch:
      ret[kKeyGestureType] = Json::Value(kValueGestureTypePinch);
      ret[kKeyGesturePinchDZ] = Json::Value(gesture.details.pinch.dz);
      ret[kKeyGesturePinchOrdinalDZ] =
          Json::Value(gesture.details.pinch.ordinal_dz);
      ret[kKeyGesturePinchZoomState] =
          Json::Value(gesture.details.pinch.zoom_state);
      break;
    case kGestureTypeButtonsChange:
      ret[kKeyGestureType] = Json::Value(kValueGestureTypeButtonsChange);
      ret[kKeyGestureButtonsChangeDown] =
          Json::Value(static_cast<int>(gesture.details.buttons.down));
      ret[kKeyGestureButtonsChangeUp] =
          Json::Value(static_cast<int>(gesture.details.buttons.up));
      break;
    case kGestureTypeFling:
      ret[kKeyGestureType] = Json::Value(kValueGestureTypeFling);
      ret[kKeyGestureFlingVX] = Json::Value(gesture.details.fling.vx);
      ret[kKeyGestureFlingVY] = Json::Value(gesture.details.fling.vy);
      ret[kKeyGestureFlingOrdinalVX] =
          Json::Value(gesture.details.fling.ordinal_vx);
      ret[kKeyGestureFlingOrdinalVY] =
          Json::Value(gesture.details.fling.ordinal_vy);
      ret[kKeyGestureFlingState] =
          Json::Value(static_cast<int>(gesture.details.fling.fling_state));
      break;
    case kGestureTypeSwipe:
      ret[kKeyGestureType] = Json::Value(kValueGestureTypeSwipe);
      ret[kKeyGestureDX] = Json::Value(gesture.details.swipe.dx);
      ret[kKeyGestureDY] = Json::Value(gesture.details.swipe.dy);
      ret[kKeyGestureOrdinalDX] =
          Json::Value(gesture.details.swipe.ordinal_dx);
      ret[kKeyGestureOrdinalDY] =
          Json::Value(gesture.details.swipe.ordinal_dy);
      break;
    case kGestureTypeSwipeLift:
      ret[kKeyGestureType] = Json::Value(kValueGestureTypeSwipeLift);
      break;
    case kGestureTypeFourFingerSwipe:
      ret[kKeyGestureType] = Json::Value(kValueGestureTypeFourFingerSwipe);
      ret[kKeyGestureDX] =
          Json::Value(gesture.details.four_finger_swipe.dx);
      ret[kKeyGestureDY] =
          Json::Value(gesture.details.four_finger_swipe.dy);
      ret[kKeyGestureOrdinalDX] =
          Json::Value(gesture.details.four_finger_swipe.ordinal_dx);
      ret[kKeyGestureOrdinalDY] =
          Json::Value(gesture.details.four_finger_swipe.ordinal_dy);
      break;
    case kGestureTypeFourFingerSwipeLift:
      ret[kKeyGestureType] = Json::Value(kValueGestureTypeFourFingerSwipeLift);
      break;
    case kGestureTypeMetrics:
      ret[kKeyGestureType] = Json::Value(kValueGestureTypeMetrics);
      ret[kKeyGestureMetricsType] =
          Json::Value(static_cast<int>(gesture.details.metrics.type));
      ret[kKeyGestureMetricsData1] =
          Json::Value(gesture.details.metrics.data[0]);
      ret[kKeyGestureMetricsData2] =
          Json::Value(gesture.details.metrics.data[1]);
      break;
    default:
      ret[kKeyGestureType] =
          Json::Value(StringPrintf("Unhandled %d", gesture.type));
  }
  return ret;
}

Json::Value ActivityLog::EncodeGesture(const Gesture& gesture) {
  auto ret = EncodeGestureCommon(gesture);
  ret[kKeyType] = Json::Value(kKeyGesture);
  return ret;
}

Json::Value ActivityLog::EncodeGesture(const GestureConsume& gesture_consume) {
  auto ret = EncodeGestureCommon(gesture_consume.gesture);
  ret[kKeyType] = Json::Value(kKeyGestureConsume);
  ret[kKeyMethodName] = Json::Value(gesture_consume.name);
  return ret;
}

Json::Value ActivityLog::EncodeGesture(const GestureProduce& gesture_produce) {
  auto ret = EncodeGestureCommon(gesture_produce.gesture);
  ret[kKeyType] = Json::Value(kKeyGestureProduce);
  ret[kKeyMethodName] = Json::Value(gesture_produce.name);
  return ret;
}

Json::Value ActivityLog::EncodePropChange(const PropChangeEntry& prop_change) {
  Json::Value ret(Json::objectValue);
  ret[kKeyType] = Json::Value(kKeyPropChange);
  ret[kKeyPropChangeName] = Json::Value(prop_change.name);
  Json::Value val;
  Json::Value type;
  std::visit(
    Visitor {
      [&val, &type](GesturesPropBool value) {
        val = Json::Value(value);
        type = Json::Value(kValuePropChangeTypeBool);
      },
      [&val, &type](double value) {
        val = Json::Value(value);
        type = Json::Value(kValuePropChangeTypeDouble);
      },
      [&val, &type](int value) {
        val = Json::Value(value);
        type = Json::Value(kValuePropChangeTypeInt);
      },
      [&val, &type](short value) {
        val = Json::Value(value);
        type = Json::Value(kValuePropChangeTypeShort);
      },
      [](auto arg) {
        Err("Invalid value type");
      }
    }, prop_change.value);
  if (!val.isNull())
    ret[kKeyPropChangeValue] = val;
  if (!type.isNull())
    ret[kKeyPropChangeType] = type;
  return ret;
}

Json::Value ActivityLog::EncodeGestureDebug(
    const AccelGestureDebug& debug_data) {
  Json::Value ret(Json::objectValue);
  ret[kKeyType] = Json::Value(kKeyAccelGestureDebug);
  ret[kKeyAccelDebugDroppedGesture] = Json::Value(debug_data.dropped_gesture);
  if (debug_data.no_accel_for_gesture_type)
    ret[kKeyAccelDebugNoAccelGestureType] = Json::Value(true);
  else if (debug_data.no_accel_for_small_dt)
    ret[kKeyAccelDebugNoAccelSmallDt] = Json::Value(true);
  else if (debug_data.no_accel_for_small_speed)
    ret[kKeyAccelDebugNoAccelSmallSpeed] = Json::Value(true);
  else if (debug_data.no_accel_for_bad_gain)
    ret[kKeyAccelDebugNoAccelBadGain] = Json::Value(true);
  ret[kKeyAccelDebugXYAreVelocity] = Json::Value(debug_data.x_y_are_velocity);
  ret[kKeyAccelDebugXScale] = Json::Value(debug_data.x_scale);
  ret[kKeyAccelDebugYScale] = Json::Value(debug_data.y_scale);
  ret[kKeyAccelDebugDt] = Json::Value(debug_data.dt);
  ret[kKeyAccelDebugAdjustedDt] = Json::Value(debug_data.adjusted_dt);
  ret[kKeyAccelDebugSpeed] = Json::Value(debug_data.speed);
  if (debug_data.speed != debug_data.smoothed_speed)
    ret[kKeyAccelDebugSmoothSpeed] = Json::Value(debug_data.smoothed_speed);
  ret[kKeyAccelDebugGainX] = Json::Value(debug_data.gain_x);
  ret[kKeyAccelDebugGainY] = Json::Value(debug_data.gain_y);
  return ret;
}

Json::Value ActivityLog::EncodeGestureDebug(
    const TimestampGestureDebug& debug_data) {
  Json::Value ret(Json::objectValue);
  ret[kKeyType] = Json::Value(kKeyTimestampGestureDebug);
  ret[kKeyTimestampDebugSkew] = Json::Value(debug_data.skew);
  return ret;
}

Json::Value ActivityLog::EncodeHardwareStateDebug(
    const TimestampHardwareStateDebug& debug_data) {
  Json::Value ret(Json::objectValue);
  ret[kKeyType] = Json::Value(kKeyTimestampHardwareStateDebug);
  ret[kKeyTimestampDebugIsUsingFake] = Json::Value(debug_data.is_using_fake);
  if (debug_data.is_using_fake) {
    ret[kKeyTimestampDebugWasFirstOrBackward] =
        Json::Value(debug_data.was_first_or_backward);
    ret[kKeyTimestampDebugPrevMscTimestampIn] =
        Json::Value(debug_data.prev_msc_timestamp_in);
    ret[kKeyTimestampDebugPrevMscTimestampOut] =
        Json::Value(debug_data.prev_msc_timestamp_out);
  } else {
    ret[kKeyTimestampDebugWasDivergenceReset] =
        Json::Value(debug_data.was_divergence_reset);
    ret[kKeyTimestampDebugFakeTimestampIn] =
        Json::Value(debug_data.fake_timestamp_in);
    ret[kKeyTimestampDebugFakeTimestampDelta] =
        Json::Value(debug_data.fake_timestamp_delta);
    ret[kKeyTimestampDebugFakeTimestampOut] =
        Json::Value(debug_data.fake_timestamp_out);
  }
  ret[kKeyTimestampDebugSkew] = Json::Value(debug_data.skew);
  ret[kKeyTimestampDebugMaxSkew] = Json::Value(debug_data.max_skew);
  return ret;
}

Json::Value ActivityLog::EncodePropRegistry() {
  Json::Value ret(Json::objectValue);
  if (!prop_reg_)
    return ret;

  const set<Property*>& props = prop_reg_->props();
  for (set<Property*>::const_iterator it = props.begin(), e = props.end();
       it != e; ++it) {
    ret[(*it)->name()] = (*it)->NewValue();
  }
  return ret;
}

Json::Value ActivityLog::EncodeCommonInfo() {
  Json::Value root(Json::objectValue);
  Json::Value entries(Json::arrayValue);
  for (size_t i = 0; i < size_; ++i) {
    const Entry& entry = buffer_[(i + head_idx_) % kBufferSize];
    std::visit(
      Visitor {
        [this, &entries](HardwareState hwstate) {
          entries.append(EncodeHardwareState(hwstate));
        },
        [this, &entries](HardwareStatePre hwstate) {
          entries.append(EncodeHardwareState(hwstate));
        },
        [this, &entries](HardwareStatePost hwstate) {
          entries.append(EncodeHardwareState(hwstate));
        },
        [this, &entries](TimerCallbackEntry now) {
          entries.append(EncodeTimerCallback(now.timestamp));
        },
        [this, &entries](CallbackRequestEntry when) {
          entries.append(EncodeCallbackRequest(when.timestamp));
        },
        [this, &entries](Gesture gesture) {
          entries.append(EncodeGesture(gesture));
        },
        [this, &entries](GestureConsume gesture) {
          entries.append(EncodeGesture(gesture));
        },
        [this, &entries](GestureProduce gesture) {
          entries.append(EncodeGesture(gesture));
        },
        [this, &entries](PropChangeEntry prop_change) {
          entries.append(EncodePropChange(prop_change));
        },
        [this, &entries](HandleTimerPre handle) {
          entries.append(EncodeHandleTimer(handle));
        },
        [this, &entries](HandleTimerPost handle) {
          entries.append(EncodeHandleTimer(handle));
        },
        [this, &entries](AccelGestureDebug debug_data) {
          entries.append(EncodeGestureDebug(debug_data));
        },
        [this, &entries](TimestampGestureDebug debug_data) {
          entries.append(EncodeGestureDebug(debug_data));
        },
        [this, &entries](TimestampHardwareStateDebug debug_data) {
          entries.append(EncodeHardwareStateDebug(debug_data));
        },
        [](auto arg) {
          Err("Unknown entry type");
        }
      }, entry.details);
  }
  root[kKeyRoot] = entries;
  root[kKeyHardwarePropRoot] = EncodeHardwareProperties();

  return root;
}

void ActivityLog::AddEncodeInfo(Json::Value* root) {
  (*root)["version"] = Json::Value(1);
  string gestures_version = VCSID;

  // Strip tailing whitespace.
  TrimWhitespaceASCII(gestures_version, TRIM_ALL, &gestures_version);
  (*root)["gesturesVersion"] = Json::Value(gestures_version);
  (*root)[kKeyProperties] = EncodePropRegistry();
}

string ActivityLog::Encode() {
  Json::Value root = EncodeCommonInfo();
  AddEncodeInfo(&root);
  return root.toStyledString();
}

const char ActivityLog::kKeyInterpreterName[] = "interpreterName";
const char ActivityLog::kKeyNext[] = "nextLayer";
const char ActivityLog::kKeyRoot[] = "entries";
const char ActivityLog::kKeyType[] = "type";
const char ActivityLog::kKeyMethodName[] = "methodName";
const char ActivityLog::kKeyHardwareState[] = "hardwareState";
const char ActivityLog::kKeyHardwareStatePre[] = "debugHardwareStatePre";
const char ActivityLog::kKeyHardwareStatePost[] = "debugHardwareStatePost";
const char ActivityLog::kKeyTimerCallback[] = "timerCallback";
const char ActivityLog::kKeyCallbackRequest[] = "callbackRequest";
const char ActivityLog::kKeyGesture[] = "gesture";
const char ActivityLog::kKeyGestureConsume[] = "debugGestureConsume";
const char ActivityLog::kKeyGestureProduce[] = "debugGestureProduce";
const char ActivityLog::kKeyHandleTimerPre[] = "debugHandleTimerPre";
const char ActivityLog::kKeyHandleTimerPost[] = "debugHandleTimerPost";
const char ActivityLog::kKeyTimerNow[] = "now";
const char ActivityLog::kKeyHandleTimerTimeout[] = "timeout";
const char ActivityLog::kKeyPropChange[] = "propertyChange";
const char ActivityLog::kKeyHardwareStateTimestamp[] = "timestamp";
const char ActivityLog::kKeyHardwareStateButtonsDown[] = "buttonsDown";
const char ActivityLog::kKeyHardwareStateTouchCnt[] = "touchCount";
const char ActivityLog::kKeyHardwareStateFingers[] = "fingers";
const char ActivityLog::kKeyHardwareStateRelX[] = "relX";
const char ActivityLog::kKeyHardwareStateRelY[] = "relY";
const char ActivityLog::kKeyHardwareStateRelWheel[] = "relWheel";
const char ActivityLog::kKeyHardwareStateRelHWheel[] = "relHWheel";
const char ActivityLog::kKeyFingerStateTouchMajor[] = "touchMajor";
const char ActivityLog::kKeyFingerStateTouchMinor[] = "touchMinor";
const char ActivityLog::kKeyFingerStateWidthMajor[] = "widthMajor";
const char ActivityLog::kKeyFingerStateWidthMinor[] = "widthMinor";
const char ActivityLog::kKeyFingerStatePressure[] = "pressure";
const char ActivityLog::kKeyFingerStateOrientation[] = "orientation";
const char ActivityLog::kKeyFingerStatePositionX[] = "positionX";
const char ActivityLog::kKeyFingerStatePositionY[] = "positionY";
const char ActivityLog::kKeyFingerStateTrackingId[] = "trackingId";
const char ActivityLog::kKeyFingerStateFlags[] = "flags";
const char ActivityLog::kKeyCallbackRequestWhen[] = "when";
const char ActivityLog::kKeyGestureType[] = "gestureType";
const char ActivityLog::kValueGestureTypeContactInitiated[] =
    "contactInitiated";
const char ActivityLog::kValueGestureTypeMove[] = "move";
const char ActivityLog::kValueGestureTypeScroll[] = "scroll";
const char ActivityLog::kValueGestureTypeMouseWheel[] = "mouseWheel";
const char ActivityLog::kValueGestureTypePinch[] = "pinch";
const char ActivityLog::kValueGestureTypeButtonsChange[] = "buttonsChange";
const char ActivityLog::kValueGestureTypeFling[] = "fling";
const char ActivityLog::kValueGestureTypeSwipe[] = "swipe";
const char ActivityLog::kValueGestureTypeSwipeLift[] = "swipeLift";
const char ActivityLog::kValueGestureTypeFourFingerSwipe[] = "fourFingerSwipe";
const char ActivityLog::kValueGestureTypeFourFingerSwipeLift[] =
    "fourFingerSwipeLift";
const char ActivityLog::kValueGestureTypeMetrics[] = "metrics";
const char ActivityLog::kKeyGestureStartTime[] = "startTime";
const char ActivityLog::kKeyGestureEndTime[] = "endTime";
const char ActivityLog::kKeyGestureDX[] = "dx";
const char ActivityLog::kKeyGestureDY[] = "dy";
const char ActivityLog::kKeyGestureOrdinalDX[] = "ordinalDx";
const char ActivityLog::kKeyGestureOrdinalDY[] = "ordinalDy";
const char ActivityLog::kKeyGestureMouseWheelTicksDX[] = "ticksDx";
const char ActivityLog::kKeyGestureMouseWheelTicksDY[] = "ticksDy";
const char ActivityLog::kKeyGesturePinchDZ[] = "dz";
const char ActivityLog::kKeyGesturePinchOrdinalDZ[] = "ordinalDz";
const char ActivityLog::kKeyGesturePinchZoomState[] = "zoomState";
const char ActivityLog::kKeyGestureButtonsChangeDown[] = "down";
const char ActivityLog::kKeyGestureButtonsChangeUp[] = "up";
const char ActivityLog::kKeyGestureFlingVX[] = "vx";
const char ActivityLog::kKeyGestureFlingVY[] = "vy";
const char ActivityLog::kKeyGestureFlingOrdinalVX[] = "ordinalVx";
const char ActivityLog::kKeyGestureFlingOrdinalVY[] = "ordinalVy";
const char ActivityLog::kKeyGestureFlingState[] = "flingState";
const char ActivityLog::kKeyGestureMetricsType[] = "metricsType";
const char ActivityLog::kKeyGestureMetricsData1[] = "data1";
const char ActivityLog::kKeyGestureMetricsData2[] = "data2";
const char ActivityLog::kKeyPropChangeType[] = "propChangeType";
const char ActivityLog::kKeyPropChangeName[] = "name";
const char ActivityLog::kKeyPropChangeValue[] = "value";
const char ActivityLog::kValuePropChangeTypeBool[] = "bool";
const char ActivityLog::kValuePropChangeTypeDouble[] = "double";
const char ActivityLog::kValuePropChangeTypeInt[] = "int";
const char ActivityLog::kValuePropChangeTypeShort[] = "short";
const char ActivityLog::kKeyHardwarePropRoot[] = "hardwareProperties";
const char ActivityLog::kKeyHardwarePropLeft[] = "left";
const char ActivityLog::kKeyHardwarePropTop[] = "top";
const char ActivityLog::kKeyHardwarePropRight[] = "right";
const char ActivityLog::kKeyHardwarePropBottom[] = "bottom";
const char ActivityLog::kKeyHardwarePropXResolution[] = "xResolution";
const char ActivityLog::kKeyHardwarePropYResolution[] = "yResolution";
const char ActivityLog::kKeyHardwarePropXDpi[] = "xDpi";
const char ActivityLog::kKeyHardwarePropYDpi[] = "yDpi";
const char ActivityLog::kKeyHardwarePropOrientationMinimum[] =
    "orientationMinimum";
const char ActivityLog::kKeyHardwarePropOrientationMaximum[] =
    "orientationMaximum";
const char ActivityLog::kKeyHardwarePropMaxFingerCount[] = "maxFingerCount";
const char ActivityLog::kKeyHardwarePropMaxTouchCount[] = "maxTouchCount";
const char ActivityLog::kKeyHardwarePropSupportsT5R2[] = "supportsT5R2";
const char ActivityLog::kKeyHardwarePropSemiMt[] = "semiMt";
const char ActivityLog::kKeyHardwarePropIsButtonPad[] = "isButtonPad";
const char ActivityLog::kKeyHardwarePropHasWheel[] = "hasWheel";

const char ActivityLog::kKeyProperties[] = "properties";

const char ActivityLog::kKeyAccelGestureDebug[] = "debugAccelGesture";
const char ActivityLog::kKeyAccelDebugNoAccelBadGain[] = "noAccelBadGain";
const char ActivityLog::kKeyAccelDebugNoAccelGestureType[] = "noAccelBadType";
const char ActivityLog::kKeyAccelDebugNoAccelSmallDt[] = "noAccelSmallDt";
const char ActivityLog::kKeyAccelDebugNoAccelSmallSpeed[] =
    "noAccelSmallSpeed";
const char ActivityLog::kKeyAccelDebugDroppedGesture[] = "gestureDropped";
const char ActivityLog::kKeyAccelDebugXYAreVelocity[] = "XYAreVelocity";
const char ActivityLog::kKeyAccelDebugXScale[] = "XScale";
const char ActivityLog::kKeyAccelDebugYScale[] = "YScale";
const char ActivityLog::kKeyAccelDebugDt[] = "dt";
const char ActivityLog::kKeyAccelDebugAdjustedDt[] = "adjDt";
const char ActivityLog::kKeyAccelDebugSpeed[] = "speed";
const char ActivityLog::kKeyAccelDebugSmoothSpeed[] = "smoothSpeed";
const char ActivityLog::kKeyAccelDebugGainX[] = "gainX";
const char ActivityLog::kKeyAccelDebugGainY[] = "gainY";

const char ActivityLog::kKeyTimestampGestureDebug[] = "debugTimestampGesture";
const char ActivityLog::kKeyTimestampHardwareStateDebug[] =
    "debugTimestampHardwareState";
const char ActivityLog::kKeyTimestampDebugIsUsingFake[] = "isUsingFake";
const char ActivityLog::kKeyTimestampDebugWasFirstOrBackward[] =
    "wasFirstOrBackward";
const char ActivityLog::kKeyTimestampDebugPrevMscTimestampIn[] =
    "prevMscTimestampIn";
const char ActivityLog::kKeyTimestampDebugPrevMscTimestampOut[] =
    "prevMscTimestampOut";
const char ActivityLog::kKeyTimestampDebugWasDivergenceReset[] =
    "wasDivergenceReset";
const char ActivityLog::kKeyTimestampDebugFakeTimestampIn[] =
    "fakeTimestampIn";
const char ActivityLog::kKeyTimestampDebugFakeTimestampDelta[] =
    "fakeTimestampDelta";
const char ActivityLog::kKeyTimestampDebugFakeTimestampOut[] =
    "fakeTimestampOut";
const char ActivityLog::kKeyTimestampDebugSkew[] = "skew";
const char ActivityLog::kKeyTimestampDebugMaxSkew[] = "maxSkew";

}  // namespace gestures
