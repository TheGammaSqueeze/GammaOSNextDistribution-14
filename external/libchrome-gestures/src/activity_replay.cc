// Copyright 2011 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "include/activity_replay.h"

#include <limits.h>
#include <set>
#include <string>

#include <gtest/gtest.h>
#include <json/reader.h>
#include <json/writer.h>

#include "include/logging.h"
#include "include/prop_registry.h"
#include "include/unittest_util.h"
#include "include/util.h"

using std::endl;
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

ActivityReplay::ActivityReplay(PropRegistry* prop_reg)
    : log_(nullptr), prop_reg_(prop_reg) {}

bool ActivityReplay::Parse(const string& data) {
  std::set<string> emptyset;
  return Parse(data, emptyset);
}

bool ActivityReplay::Parse(const string& data,
                           const std::set<string>& honor_props) {
  log_.Clear();
  names_.clear();

  string error_msg;
  Json::Value root;
  {
    Json::CharReaderBuilder builder;
    std::unique_ptr<Json::CharReader> const reader(builder.newCharReader());
    const char * const data_str = data.c_str();

    if (!reader->parse(data_str, data_str + data.size(),
                       &root, &error_msg)) {  // root modified in parse()
      Err("Parse failed: %s", error_msg.c_str());
      return false;
    }
  }
  if (root.type() != Json::objectValue) {
    Err("Root type is %d, but expected %d (dictionary)",
        root.type(), Json::objectValue);
    return false;
  }
  // Get and apply user-configurable properties
  Json::Value props_dict =
      root.get(ActivityLog::kKeyProperties, Json::Value());
  if (root.isMember(ActivityLog::kKeyProperties) &&
      !ParseProperties(props_dict, honor_props)) {
    Err("Unable to parse properties.");
    return false;
  }
  // Get and apply hardware properties
  if (!root.isMember(ActivityLog::kKeyHardwarePropRoot)) {
    Err("Unable to get hwprops dict.");
    return false;
  }
  Json::Value hwprops_dict =
      root.get(ActivityLog::kKeyHardwarePropRoot, Json::Value());
  if (!ParseHardwareProperties(hwprops_dict, &hwprops_))
    return false;
  log_.SetHardwareProperties(hwprops_);
  Json::Value entries = root.get(ActivityLog::kKeyRoot, Json::Value());
  char next_layer_path[PATH_MAX];
  snprintf(next_layer_path, sizeof(next_layer_path), "%s.%s",
           ActivityLog::kKeyNext, ActivityLog::kKeyRoot);
  if (!root.isMember(ActivityLog::kKeyRoot)) {
    Err("Unable to get list of entries from root.");
    return false;
  }
  if (root.isMember(next_layer_path)) {
    Json::Value next_layer_entries = root[next_layer_path];
    if (entries.size() < next_layer_entries.size())
      entries = next_layer_entries;
  }

  for (size_t i = 0; i < entries.size(); ++i) {
    Json::Value entry = entries.get(i, Json::Value());
    if (!entries.isValidIndex(i)) {
      Err("Invalid entry at index %zu", i);
      return false;
    }
    if (!ParseEntry(entry))
      return false;
  }
  return true;
}

bool ActivityReplay::ParseProperties(const Json::Value& dict,
                                     const std::set<string>& honor_props) {
  if (!prop_reg_)
    return true;
  ::set<Property*> props = prop_reg_->props();
  for (::set<Property*>::const_iterator it = props.begin(), e = props.end();
       it != e; ++it) {
    const char* key = (*it)->name();

    // TODO(clchiou): This is just a emporary workaround for property changes.
    // I will work out a solution for this kind of changes.
    if (!strcmp(key, "Compute Surface Area from Pressure") ||
        !strcmp(key, "Touchpad Device Output Bias on X-Axis") ||
        !strcmp(key, "Touchpad Device Output Bias on Y-Axis")) {
      continue;
    }

    if (!honor_props.empty() && !SetContainsValue(honor_props, string(key)))
      continue;
    if (!dict.isMember(key)) {
      Err("Log doesn't have value for property %s", key);
      continue;
    }
    const Json::Value& value = dict[key];
    if (!(*it)->SetValue(value)) {
      Err("Unable to restore value for property %s", key);
      return false;
    }
  }
  return true;
}

#define PARSE_HP(obj, key, IsTypeFn, KeyFn, var, VarType, required)      \
  do {                                                                  \
    if (!obj.isMember(key) || !obj[key].IsTypeFn()) {                   \
      Err("Parse failed for key %s", key);                              \
      if (required)                                                     \
        return false;                                                   \
    }                                                                   \
    var = obj[key].KeyFn();                                             \
  } while (0)

bool ActivityReplay::ParseHardwareProperties(const Json::Value& obj,
                                             HardwareProperties* out_props) {
  HardwareProperties props;
  PARSE_HP(obj, ActivityLog::kKeyHardwarePropLeft, isDouble, asDouble,
           props.left, float, true);
  PARSE_HP(obj, ActivityLog::kKeyHardwarePropTop, isDouble, asDouble,
           props.top, float, true);
  PARSE_HP(obj, ActivityLog::kKeyHardwarePropRight, isDouble, asDouble,
           props.right, float, true);
  PARSE_HP(obj, ActivityLog::kKeyHardwarePropBottom, isDouble, asDouble,
           props.bottom, float, true);
  PARSE_HP(obj, ActivityLog::kKeyHardwarePropXResolution, isDouble, asDouble,
           props.res_x, float, true);
  PARSE_HP(obj, ActivityLog::kKeyHardwarePropYResolution, isDouble, asDouble,
           props.res_y, float, true);
  PARSE_HP(obj, ActivityLog::kKeyHardwarePropXDpi, isDouble, asDouble,
           props.screen_x_dpi, float, true);
  PARSE_HP(obj, ActivityLog::kKeyHardwarePropYDpi, isDouble, asDouble,
           props.screen_y_dpi, float, true);
  PARSE_HP(obj, ActivityLog::kKeyHardwarePropOrientationMinimum,
           isDouble, asDouble, props.orientation_minimum, float, false);
  PARSE_HP(obj, ActivityLog::kKeyHardwarePropOrientationMaximum,
           isDouble, asDouble, props.orientation_maximum, float, false);
  PARSE_HP(obj, ActivityLog::kKeyHardwarePropMaxFingerCount, isInt, asUInt,
           props.max_finger_cnt, unsigned short, true);
  PARSE_HP(obj, ActivityLog::kKeyHardwarePropMaxTouchCount, isInt, asUInt,
           props.max_touch_cnt, unsigned short, true);
  PARSE_HP(obj, ActivityLog::kKeyHardwarePropSupportsT5R2, isBool, asBool,
           props.supports_t5r2, bool, true);
  PARSE_HP(obj, ActivityLog::kKeyHardwarePropSemiMt,isBool, asBool,
           props.support_semi_mt, bool, true);
  PARSE_HP(obj, ActivityLog::kKeyHardwarePropIsButtonPad,isBool, asBool,
           props.is_button_pad, bool, true);
  PARSE_HP(obj, ActivityLog::kKeyHardwarePropHasWheel,isBool, asBool,
           props.has_wheel, bool, true);
  *out_props = props;
  return true;
}

#undef PARSE_HP

bool ActivityReplay::ParseEntry(const Json::Value& entry) {
  if (!entry.isMember(ActivityLog::kKeyType) ||
      entry[ActivityLog::kKeyType].type() != Json::stringValue) {
    Err("Can't get entry type.");
    return false;
  }
  string type = entry[ActivityLog::kKeyType].asString();
  if (type == ActivityLog::kKeyHardwareState)
    return ParseHardwareState(entry);
  if (type == ActivityLog::kKeyTimerCallback)
    return ParseTimerCallback(entry);
  if (type == ActivityLog::kKeyCallbackRequest)
    return ParseCallbackRequest(entry);
  if (type == ActivityLog::kKeyGesture)
    return ParseGesture(entry);
  if (type == ActivityLog::kKeyPropChange)
    return ParsePropChange(entry);
  Err("Unknown entry type");
  return false;
}

bool ActivityReplay::ParseHardwareState(const Json::Value& entry) {
  HardwareState hs = HardwareState();
  if (!entry.isMember(ActivityLog::kKeyHardwareStateButtonsDown)) {
    Err("Unable to parse hardware state buttons down");
    return false;
  }
  hs.buttons_down = entry[ActivityLog::kKeyHardwareStateButtonsDown].asUInt();
  if (!entry.isMember(ActivityLog::kKeyHardwareStateTouchCnt)) {
    Err("Unable to parse hardware state touch count");
    return false;
  }
  hs.touch_cnt = entry[ActivityLog::kKeyHardwareStateTouchCnt].asUInt();
  if (!entry.isMember(ActivityLog::kKeyHardwareStateTimestamp)) {
    Err("Unable to parse hardware state timestamp");
    return false;
  }
  hs.timestamp = entry[ActivityLog::kKeyHardwareStateTimestamp].asDouble();
  if (!entry.isMember(ActivityLog::kKeyHardwareStateFingers)) {
    Err("Unable to parse hardware state fingers");
    return false;
  }
  Json::Value fingers = entry[ActivityLog::kKeyHardwareStateFingers];
  // Sanity check
  if (fingers.size() > 30) {
    Err("Too many fingers in hardware state");
    return false;
  }
  FingerState fs[fingers.size()];
  for (size_t i = 0; i < fingers.size(); ++i) {
    if (!fingers.isValidIndex(i)) {
      Err("Invalid entry at index %zu", i);
      return false;
    }
    const Json::Value& finger_state = fingers[static_cast<int>(i)];
    if (!ParseFingerState(finger_state, &fs[i]))
      return false;
  }
  hs.fingers = fs;
  hs.finger_cnt = fingers.size();
  // There may not have rel_ entries for old logs
  if (entry.isMember(ActivityLog::kKeyHardwareStateRelX)) {
    hs.rel_x = entry[ActivityLog::kKeyHardwareStateRelX].asDouble();
    if (!entry.isMember(ActivityLog::kKeyHardwareStateRelY)) {
      Err("Unable to parse hardware state rel_y");
      return false;
    }
    hs.rel_x = entry[ActivityLog::kKeyHardwareStateRelY].asDouble();
    if (!entry.isMember(ActivityLog::kKeyHardwareStateRelWheel)) {
      Err("Unable to parse hardware state rel_wheel");
      return false;
    }
    hs.rel_wheel = entry[ActivityLog::kKeyHardwareStateRelWheel].asDouble();
    if (!entry.isMember(ActivityLog::kKeyHardwareStateRelHWheel)) {
      Err("Unable to parse hardware state rel_hwheel");
      return false;
    }
    hs.rel_hwheel = entry[ActivityLog::kKeyHardwareStateRelHWheel].asDouble();
  }
  log_.LogHardwareState(hs);
  return true;
}

bool ActivityReplay::ParseFingerState(const Json::Value& entry,
                                      FingerState* out_fs) {
  if (!entry.isMember(ActivityLog::kKeyFingerStateTouchMajor)) {
    Err("can't parse finger's touch major");
    return false;
  }
  out_fs->touch_major =
      entry[ActivityLog::kKeyFingerStateTouchMajor].asDouble();
  if (!entry.isMember(ActivityLog::kKeyFingerStateTouchMinor)) {
    Err("can't parse finger's touch minor");
    return false;
  }
  out_fs->touch_minor =
      entry[ActivityLog::kKeyFingerStateTouchMinor].asDouble();
  if (!entry.isMember(ActivityLog::kKeyFingerStateWidthMajor)) {
    Err("can't parse finger's width major");
    return false;
  }
  out_fs->width_major =
      entry[ActivityLog::kKeyFingerStateWidthMajor].asDouble();
  if (!entry.isMember(ActivityLog::kKeyFingerStateWidthMinor)) {
    Err("can't parse finger's width minor");
    return false;
  }
  out_fs->width_minor =
      entry[ActivityLog::kKeyFingerStateWidthMinor].asDouble();
  if (!entry.isMember(ActivityLog::kKeyFingerStatePressure)) {
    Err("can't parse finger's pressure");
    return false;
  }
  out_fs->pressure = entry[ActivityLog::kKeyFingerStatePressure].asDouble();
  if (!entry.isMember(ActivityLog::kKeyFingerStateOrientation)) {
    Err("can't parse finger's orientation");
    return false;
  }
  out_fs->orientation =
      entry[ActivityLog::kKeyFingerStateOrientation].asDouble();
  if (!entry.isMember(ActivityLog::kKeyFingerStatePositionX)) {
    Err("can't parse finger's position x");
    return false;
  }
  out_fs->position_x = entry[ActivityLog::kKeyFingerStatePositionX].asDouble();
  if (!entry.isMember(ActivityLog::kKeyFingerStatePositionY)) {
    Err("can't parse finger's position y");
    return false;
  }
  out_fs->position_y = entry[ActivityLog::kKeyFingerStatePositionY].asDouble();
  if (!entry.isMember(ActivityLog::kKeyFingerStateTrackingId)) {
    Err("can't parse finger's tracking id");
    return false;
  }
  out_fs->tracking_id = entry[ActivityLog::kKeyFingerStateTrackingId].asInt();
  if (!entry.isMember(ActivityLog::kKeyFingerStateFlags))
    Err("can't parse finger's flags; continuing.");
  out_fs->flags = entry[ActivityLog::kKeyFingerStateFlags].asUInt();
  return true;
}

bool ActivityReplay::ParseTimerCallback(const Json::Value& entry) {
  if (!entry.isMember(ActivityLog::kKeyTimerNow)) {
    Err("can't parse timercallback");
    return false;
  }
  log_.LogTimerCallback(entry[ActivityLog::kKeyTimerNow].asDouble());
  return true;
}

bool ActivityReplay::ParseCallbackRequest(const Json::Value& entry) {
  if (!entry.isMember(ActivityLog::kKeyCallbackRequestWhen)) {
    Err("can't parse callback request");
    return false;
  }
  log_.LogCallbackRequest(
      entry[ActivityLog::kKeyCallbackRequestWhen].asDouble());
  return true;
}

bool ActivityReplay::ParseGesture(const Json::Value& entry) {
  if (!entry.isMember(ActivityLog::kKeyGestureType)) {
    Err("can't parse gesture type");
    return false;
  }
  string gesture_type = entry[ActivityLog::kKeyGestureType].asString();
  Gesture gs;

  if (!entry.isMember(ActivityLog::kKeyGestureStartTime)) {
    Err("Failed to parse gesture start time");
    return false;
  }
  gs.start_time = entry[ActivityLog::kKeyGestureStartTime].asDouble();
  if (!entry.isMember(ActivityLog::kKeyGestureEndTime)) {
    Err("Failed to parse gesture end time");
    return false;
  }
  gs.end_time = entry[ActivityLog::kKeyGestureEndTime].asDouble();

  if (gesture_type == ActivityLog::kValueGestureTypeContactInitiated) {
    gs.type = kGestureTypeContactInitiated;
  } else if (gesture_type == ActivityLog::kValueGestureTypeMove) {
    if (!ParseGestureMove(entry, &gs))
      return false;
  } else if (gesture_type == ActivityLog::kValueGestureTypeScroll) {
    if (!ParseGestureScroll(entry, &gs))
      return false;
  } else if (gesture_type == ActivityLog::kValueGestureTypeSwipe) {
    if (!ParseGestureSwipe(entry, &gs))
      return false;
  } else if (gesture_type == ActivityLog::kValueGestureTypeSwipeLift) {
    if (!ParseGestureSwipeLift(entry, &gs))
      return false;
  } else if (gesture_type == ActivityLog::kValueGestureTypePinch) {
    if (!ParseGesturePinch(entry, &gs))
      return false;
  } else if (gesture_type == ActivityLog::kValueGestureTypeButtonsChange) {
    if (!ParseGestureButtonsChange(entry, &gs))
      return false;
  } else if (gesture_type == ActivityLog::kValueGestureTypeFling) {
    if (!ParseGestureFling(entry, &gs))
      return false;
  } else if (gesture_type == ActivityLog::kValueGestureTypeMetrics) {
    if (!ParseGestureMetrics(entry, &gs))
      return false;
  } else {
    gs.type = kGestureTypeNull;
  }
  log_.LogGesture(gs);
  return true;
}

bool ActivityReplay::ParseGestureMove(const Json::Value& entry,
                                      Gesture* out_gs) {
  out_gs->type = kGestureTypeMove;
  if (!entry.isMember(ActivityLog::kKeyGestureDX)) {
    Err("can't parse move dx");
    return false;
  }
  out_gs->details.move.dx = entry[ActivityLog::kKeyGestureDX].asDouble();
  if (!entry.isMember(ActivityLog::kKeyGestureDY)) {
    Err("can't parse move dy");
    return false;
  }
  out_gs->details.move.dy = entry[ActivityLog::kKeyGestureDY].asDouble();
  if (!entry.isMember(ActivityLog::kKeyGestureOrdinalDX)) {
    Err("can't parse move ordinal_dx");
    return false;
  }
  out_gs->details.move.ordinal_dx =
      entry[ActivityLog::kKeyGestureOrdinalDX].asDouble();
  if (!entry.isMember(ActivityLog::kKeyGestureOrdinalDY)) {
    Err("can't parse move ordinal_dy");
    return false;
  }
  out_gs->details.move.ordinal_dy =
      entry[ActivityLog::kKeyGestureOrdinalDY].asDouble();
  return true;
}

bool ActivityReplay::ParseGestureScroll(const Json::Value& entry,
                                        Gesture* out_gs) {
  out_gs->type = kGestureTypeScroll;
  if (!entry.isMember(ActivityLog::kKeyGestureDX)) {
    Err("can't parse scroll dx");
    return false;
  }
  out_gs->details.scroll.dx =
      entry[ActivityLog::kKeyGestureDX].asDouble();
  if (!entry.isMember(ActivityLog::kKeyGestureDY)) {
    Err("can't parse scroll dy");
    return false;
  }
  out_gs->details.scroll.dy =
      entry[ActivityLog::kKeyGestureDY].asDouble();
  if (!entry.isMember(ActivityLog::kKeyGestureOrdinalDX)) {
    Err("can't parse scroll ordinal_dx");
    return false;
  }
  out_gs->details.scroll.ordinal_dx =
      entry[ActivityLog::kKeyGestureOrdinalDX].asDouble();
  if (!entry.isMember(ActivityLog::kKeyGestureOrdinalDY)) {
    Err("can't parse scroll ordinal_dy");
    return false;
  }
  out_gs->details.scroll.ordinal_dy =
      entry[ActivityLog::kKeyGestureOrdinalDY].asDouble();
  return true;
}

bool ActivityReplay::ParseGestureSwipe(const Json::Value& entry,
                                       Gesture* out_gs) {
  out_gs->type = kGestureTypeSwipe;
  if (!entry.isMember(ActivityLog::kKeyGestureDX)) {
    Err("can't parse swipe dx");
    return false;
  }
  out_gs->details.swipe.dx = entry[ActivityLog::kKeyGestureDX].asDouble();
  if (!entry.isMember(ActivityLog::kKeyGestureDY)) {
    Err("can't parse swipe dy");
    return false;
  }
  out_gs->details.swipe.dy = entry[ActivityLog::kKeyGestureDY].asDouble();
  if (!entry.isMember(ActivityLog::kKeyGestureOrdinalDX)) {
    Err("can't parse swipe ordinal_dx");
    return false;
  }
  out_gs->details.swipe.ordinal_dx =
      entry[ActivityLog::kKeyGestureOrdinalDX].asDouble();
  if (!entry.isMember(ActivityLog::kKeyGestureOrdinalDY)) {
    Err("can't parse swipe ordinal_dy");
    return false;
  }
  out_gs->details.swipe.ordinal_dy =
      entry[ActivityLog::kKeyGestureOrdinalDY].asDouble();
  return true;
}

bool ActivityReplay::ParseGestureSwipeLift(const Json::Value& entry,
                                           Gesture* out_gs) {
  out_gs->type = kGestureTypeSwipeLift;
  return true;
}

bool ActivityReplay::ParseGesturePinch(const Json::Value& entry,
                                       Gesture* out_gs) {
  out_gs->type = kGestureTypePinch;
  if (!entry.isMember(ActivityLog::kKeyGesturePinchDZ)) {
    Err("can't parse pinch dz");
    return false;
  }
  out_gs->details.pinch.dz = entry[ActivityLog::kKeyGesturePinchDZ].asDouble();
  if (!entry.isMember(ActivityLog::kKeyGesturePinchOrdinalDZ)) {
    Err("can't parse pinch ordinal_dz");
    return false;
  }
  out_gs->details.pinch.ordinal_dz =
      entry[ActivityLog::kKeyGesturePinchOrdinalDZ].asDouble();
  if (!entry.isMember(ActivityLog::kKeyGesturePinchZoomState)) {
    Err("can't parse pinch zoom_state");
    return false;
  }
  out_gs->details.pinch.zoom_state =
      entry[ActivityLog::kKeyGesturePinchZoomState].asInt();
  return true;
}

bool ActivityReplay::ParseGestureButtonsChange(const Json::Value& entry,
                                               Gesture* out_gs) {
  out_gs->type = kGestureTypeButtonsChange;
  if (!entry.isMember(ActivityLog::kKeyGestureButtonsChangeDown)) {
    Err("can't parse buttons down");
    return false;
  }
  out_gs->details.buttons.down =
      entry[ActivityLog::kKeyGestureButtonsChangeDown].asUInt();
  if (!entry.isMember(ActivityLog::kKeyGestureButtonsChangeUp)) {
    Err("can't parse buttons up");
    return false;
  }
  out_gs->details.buttons.up =
      entry[ActivityLog::kKeyGestureButtonsChangeUp].asUInt();
  return true;
}

bool ActivityReplay::ParseGestureFling(const Json::Value& entry,
                                       Gesture* out_gs) {
  out_gs->type = kGestureTypeFling;
  if (!entry.isMember(ActivityLog::kKeyGestureFlingVX)) {
    Err("can't parse fling vx");
    return false;
  }
  out_gs->details.fling.vx = entry[ActivityLog::kKeyGestureFlingVX].asDouble();
  if (!entry.isMember(ActivityLog::kKeyGestureFlingVY)) {
    Err("can't parse fling vy");
    return false;
  }
  out_gs->details.fling.vy = entry[ActivityLog::kKeyGestureFlingVY].asDouble();
  if (!entry.isMember(ActivityLog::kKeyGestureFlingOrdinalVX)) {
    Err("can't parse fling ordinal_vx");
    return false;
  }
  out_gs->details.fling.ordinal_vx =
      entry[ActivityLog::kKeyGestureFlingOrdinalVX].asDouble();
  if (!entry.isMember(ActivityLog::kKeyGestureFlingOrdinalVY)) {
    Err("can't parse fling ordinal_vy");
    return false;
  }
  out_gs->details.fling.ordinal_vy =
      entry[ActivityLog::kKeyGestureFlingOrdinalVY].asDouble();
  if (!entry.isMember(ActivityLog::kKeyGestureFlingState)) {
    Err("can't parse scroll is_scroll_begin");
    return false;
  }
  out_gs->details.fling.fling_state =
      entry[ActivityLog::kKeyGestureFlingState].asInt();
  return true;
}

bool ActivityReplay::ParseGestureMetrics(const Json::Value& entry,
                                       Gesture* out_gs) {
  out_gs->type = kGestureTypeMetrics;
  if (!entry.isMember(ActivityLog::kKeyGestureMetricsData1)) {
    Err("can't parse metrics data 1");
    return false;
  }
  out_gs->details.metrics.data[0] =
      entry[ActivityLog::kKeyGestureMetricsData1].asDouble();
  if (!entry.isMember(ActivityLog::kKeyGestureMetricsData2)) {
    Err("can't parse metrics data 2");
    return false;
  }
  out_gs->details.metrics.data[1] =
      entry[ActivityLog::kKeyGestureMetricsData2].asDouble();
  if (!entry.isMember(ActivityLog::kKeyGestureMetricsType)) {
    Err("can't parse metrics type");
    return false;
  }
  int type = entry[ActivityLog::kKeyGestureMetricsType].asInt();
  if (type == 0) {
    out_gs->details.metrics.type = kGestureMetricsTypeNoisyGround;
    return true;
  }
  out_gs->details.metrics.type = kGestureMetricsTypeUnknown;
  return true;
}

bool ActivityReplay::ParsePropChange(const Json::Value& entry) {
  ActivityLog::PropChangeEntry prop_change;
  if (!entry.isMember(ActivityLog::kKeyPropChangeType)) {
    Err("Can't get prop change type");
    return false;
  }
  string type = entry[ActivityLog::kKeyPropChangeType].asString();

  if (type == ActivityLog::kValuePropChangeTypeBool) {
    if (!entry.isMember(ActivityLog::kKeyPropChangeValue)) {
      Err("Can't parse prop change value");
      return false;
    }
    prop_change.value =
        static_cast<GesturesPropBool>(
          entry[ActivityLog::kKeyPropChangeValue].asBool());
  } else if (type == ActivityLog::kValuePropChangeTypeDouble) {
    if (!entry.isMember(ActivityLog::kKeyPropChangeValue)) {
      Err("Can't parse prop change value");
      return false;
    }
    prop_change.value =
        entry[ActivityLog::kKeyPropChangeValue].asDouble();
  } else if (type == ActivityLog::kValuePropChangeTypeInt) {
    if (!entry.isMember(ActivityLog::kKeyPropChangeValue)) {
      Err("Can't parse prop change value");
      return false;
    }
    prop_change.value =
        entry[ActivityLog::kKeyPropChangeValue].asInt();
  } else if (type == ActivityLog::kValuePropChangeTypeShort) {
    if (!entry.isMember(ActivityLog::kKeyPropChangeValue)) {
      Err("Can't parse prop change value");
      return false;
    }
    prop_change.value =
        static_cast<short>(
          entry[ActivityLog::kKeyPropChangeValue].asInt());
  } else {
    Err("Unable to parse prop change type %s", type.c_str());
    return false;
  }
  if (!entry.isMember(ActivityLog::kKeyPropChangeName)) {
    Err("Unable to parse prop change name.");
    return false;
  }
  const string* stored_name =
      new string(entry[ActivityLog::kKeyPropChangeName].asString());  // alloc
  // transfer ownership:
  names_.push_back(std::shared_ptr<const string>(stored_name));
  prop_change.name = stored_name->c_str();
  log_.LogPropChange(prop_change);
  return true;
}

// Replay the log and verify the output in a strict way.
void ActivityReplay::Replay(Interpreter* interpreter,
                            MetricsProperties* mprops) {
  interpreter->Initialize(&hwprops_, nullptr, mprops, this);

  stime_t last_timeout_req = -1.0;
  // Use last_gs to save a copy of last gesture.
  Gesture last_gs;
  for (size_t i = 0; i < log_.size(); ++i) {
    ActivityLog::Entry* entry = log_.GetEntry(i);
    std::visit(
      Visitor {
        [&interpreter, &last_timeout_req](HardwareState hs) {
          last_timeout_req = -1.0;
          for (size_t i = 0; i < hs.finger_cnt; i++)
            Log("Input Finger ID: %d", hs.fingers[i].tracking_id);
          interpreter->SyncInterpret(hs, &last_timeout_req);
        },
        [&interpreter, &last_timeout_req]
            (ActivityLog::TimerCallbackEntry now) {
          last_timeout_req = -1.0;
          interpreter->HandleTimer(now.timestamp, &last_timeout_req);
        },
        [&i, &last_timeout_req](ActivityLog::CallbackRequestEntry when) {
          if (!DoubleEq(last_timeout_req, when.timestamp)) {
            Err("Expected timeout request of %f, "
                "but log has %f (entry idx %zu)",
                last_timeout_req, when.timestamp, i);
          }
        },
        [this](Gesture gesture) {
          bool matched = false;
          while (!consumed_gestures_.empty() && !matched) {
            if (consumed_gestures_.front() == gesture) {
              Log("Gesture matched:\n  Actual gesture: %s.\n"
                  "Expected gesture: %s",
                  consumed_gestures_.front().String().c_str(),
                  gesture.String().c_str());
              matched = true;
            } else {
              Log("Unmatched actual gesture: %s\n",
                  consumed_gestures_.front().String().c_str());
              ADD_FAILURE();
            }
            consumed_gestures_.pop_front();
          }
          if (!matched) {
            Log("Missing logged gesture: %s", gesture.String().c_str());
            ADD_FAILURE();
          }
        },
        [this](ActivityLog::PropChangeEntry prop_change) {
          ReplayPropChange(prop_change);
        },
        [](auto arg) {
          Err("Unknown ActivityLog type");
        }
      }, entry->details);
  }
  while (!consumed_gestures_.empty()) {
    Log("Unmatched actual gesture: %s\n",
        consumed_gestures_.front().String().c_str());
    ADD_FAILURE();
    consumed_gestures_.pop_front();
  }
}

void ActivityReplay::ConsumeGesture(const Gesture& gesture) {
  consumed_gestures_.push_back(gesture);
}

bool ActivityReplay::ReplayPropChange(
    const ActivityLog::PropChangeEntry& entry) {
  if (!prop_reg_) {
    Err("Missing prop registry.");
    return false;
  }
  ::set<Property*> props = prop_reg_->props();
  Property* prop = nullptr;
  for (::set<Property*>::iterator it = props.begin(), e = props.end(); it != e;
       ++it) {
    prop = *it;
    if (strcmp(prop->name(), entry.name.c_str()) == 0)
      break;
    prop = nullptr;
  }
  if (!prop) {
    Err("Unable to find prop %s to set.", entry.name.c_str());
    return false;
  }
  bool valid_property = true;
  Json::Value value;
  std::visit(
    Visitor {
      [&value](GesturesPropBool entry_value) {
        value = Json::Value(static_cast<bool>(entry_value));
      },
      [&value](double entry_value) {
        value = Json::Value(entry_value);
      },
      [&value](int entry_value) {
        value = Json::Value(entry_value);
      },
      [&value](short entry_value) {
        value = Json::Value(entry_value);
      },
      [&valid_property](auto arg) {
        valid_property = false;
        Err("Invalid property type");
      }
    }, entry.value);
  if (valid_property) {
    prop->SetValue(value);
    prop->HandleGesturesPropWritten();
  }
  return valid_property;
}

}  // namespace gestures
