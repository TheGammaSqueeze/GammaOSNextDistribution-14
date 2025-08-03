// Copyright 2012 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#ifndef GESTURES_ACTIVITY_LOG_H_
#define GESTURES_ACTIVITY_LOG_H_

#include "include/gestures.h"

#include <string>
#include <variant>

#include <gtest/gtest.h>  // For FRIEND_TEST
#include <json/value.h>

// This should be set by build system:
#ifndef VCSID
#define VCSID "Unknown"
#endif  // VCSID

// This is a class that circularly buffers all incoming and outgoing activity
// so that end users can report issues and engineers can reproduce them.

namespace gestures {

class PropRegistry;

class ActivityLog {
  FRIEND_TEST(ActivityLogTest, SimpleTest);
  FRIEND_TEST(ActivityLogTest, WrapAroundTest);
  FRIEND_TEST(ActivityLogTest, VersionTest);
  FRIEND_TEST(ActivityLogTest, EncodePropChangeBoolTest);
  FRIEND_TEST(ActivityLogTest, EncodePropChangeDoubleTest);
  FRIEND_TEST(ActivityLogTest, EncodePropChangeIntTest);
  FRIEND_TEST(ActivityLogTest, EncodePropChangeShortTest);
  FRIEND_TEST(ActivityLogTest, GestureConsumeTest);
  FRIEND_TEST(ActivityLogTest, GestureProduceTest);
  FRIEND_TEST(ActivityLogTest, HardwareStatePreTest);
  FRIEND_TEST(ActivityLogTest, HardwareStatePostTest);
  FRIEND_TEST(LoggingFilterInterpreterTest, SimpleTest);
  FRIEND_TEST(PropRegistryTest, PropChangeTest);
 public:
  struct TimerCallbackEntry {
    stime_t timestamp;
  };
  struct CallbackRequestEntry {
    stime_t timestamp;
  };
  struct PropChangeEntry {
    std::string name;
    // No string variant because string values can't change
    std::variant<GesturesPropBool,
                 double,
                 int,
                 short> value;
  };

  struct HardwareStatePre {
    std::string name;
    HardwareState hwstate;
  };
  struct HardwareStatePost {
    std::string name;
    HardwareState hwstate;
  };
  struct GestureConsume {
    std::string name;
    Gesture gesture;
  };
  struct GestureProduce {
    std::string name;
    Gesture gesture;
  };
  struct HandleTimerPre {
    std::string name;
    bool timeout_is_present;
    stime_t now;
    stime_t timeout;
  };
  struct HandleTimerPost {
    std::string name;
    bool timeout_is_present;
    stime_t now;
    stime_t timeout;
  };
  struct AccelGestureDebug {
    bool no_accel_for_gesture_type;
    bool no_accel_for_small_dt;
    bool no_accel_for_small_speed;
    bool no_accel_for_bad_gain;
    bool dropped_gesture;
    bool x_y_are_velocity;
    float x_scale, y_scale;
    float dt;
    float adjusted_dt;
    float speed;
    float smoothed_speed;
    float gain_x, gain_y;
  };
  struct TimestampGestureDebug {
    stime_t skew;
  };
  struct TimestampHardwareStateDebug {
    bool is_using_fake;
    union {
      struct {
        bool was_first_or_backward;
        stime_t prev_msc_timestamp_in;
        stime_t prev_msc_timestamp_out;
      };
      struct {
        bool was_divergence_reset;
        stime_t fake_timestamp_in;
        stime_t fake_timestamp_delta;
        stime_t fake_timestamp_out;
      };
    };
    stime_t skew;
    stime_t max_skew;
  };

  struct Entry {
    std::variant<HardwareState,
                 TimerCallbackEntry,
                 CallbackRequestEntry,
                 Gesture,
                 PropChangeEntry,
                 HardwareStatePre,
                 HardwareStatePost,
                 GestureConsume,
                 GestureProduce,
                 HandleTimerPre,
                 HandleTimerPost,
                 AccelGestureDebug,
                 TimestampGestureDebug,
                 TimestampHardwareStateDebug> details;
  };

  enum class EventDebug {
    // Base Event Types
    Gesture = 0,
    HardwareState,
    HandleTimer,
    // FilterInterpreter Debug Detail Event Types
    Accel,
    Box,
    ClickWiggle,
    FingerMerge,
    FlingStop,
    HapticButtonGenerator,
    Iir,
    IntegratGesture,
    Logging,
    Lookahead,
    Metrics,
    NonLinearity,
    PalmClassifying,
    Scaling,
    SensorJump,
    SplitCorrecting,
    StationaryWiggle,
    StuckButtonInhibitor,
    T5R2Correcting,
    Timestamp,
    TrendClassifying,
    // Interpreter Debug Detail Event Types
    ImmediateInterpreter,
    MouseInterpreter,
    MultitouchMouseInterpreter,
  };

  explicit ActivityLog(PropRegistry* prop_reg);
  void SetHardwareProperties(const HardwareProperties& hwprops);

  // Log*() functions record an argument into the buffer
  void LogHardwareState(const HardwareState& hwstate);
  void LogTimerCallback(stime_t now);
  void LogCallbackRequest(stime_t when);
  void LogGesture(const Gesture& gesture);
  void LogPropChange(const PropChangeEntry& prop_change);

  // Debug extensions for Log*()
  void LogGestureConsume(const std::string& name, const Gesture& gesture);
  void LogGestureProduce(const std::string& name, const Gesture& gesture);
  void LogHardwareStatePre(const std::string& name,
                           const HardwareState& hwstate);
  void LogHardwareStatePost(const std::string& name,
                            const HardwareState& hwstate);
  void LogHandleTimerPre(const std::string& name,
                         stime_t now, const stime_t* timeout);
  void LogHandleTimerPost(const std::string& name,
                          stime_t now, const stime_t* timeout);

  template<typename T>
  void LogDebugData(const T& debug_data) {
    Entry* entry = PushBack();
    entry->details = debug_data;
  }

  // Dump allocates, and thus must not be called on a signal handler.
  void Dump(const char* filename);
  void Clear() { head_idx_ = size_ = 0; }

  // Returns a JSON string representing all the state in the buffer
  std::string Encode();
  void AddEncodeInfo(Json::Value* root);
  Json::Value EncodeCommonInfo();
  size_t size() const { return size_; }
  size_t MaxSize() const { return kBufferSize; }
  Entry* GetEntry(size_t idx) {
    return &buffer_[(head_idx_ + idx) % kBufferSize];
  }

  static const char kKeyInterpreterName[];
  static const char kKeyNext[];
  static const char kKeyRoot[];
  static const char kKeyType[];
  static const char kKeyMethodName[];
  static const char kKeyHardwareState[];
  static const char kKeyHardwareStatePre[];
  static const char kKeyHardwareStatePost[];
  static const char kKeyTimerCallback[];
  static const char kKeyCallbackRequest[];
  static const char kKeyGesture[];
  static const char kKeyGestureConsume[];
  static const char kKeyGestureProduce[];
  static const char kKeyPropChange[];
  static const char kKeyHandleTimerPre[];
  static const char kKeyHandleTimerPost[];
  // HardwareState keys:
  static const char kKeyHardwareStateTimestamp[];
  static const char kKeyHardwareStateButtonsDown[];
  static const char kKeyHardwareStateTouchCnt[];
  static const char kKeyHardwareStateFingers[];
  static const char kKeyHardwareStateRelX[];
  static const char kKeyHardwareStateRelY[];
  static const char kKeyHardwareStateRelWheel[];
  static const char kKeyHardwareStateRelHWheel[];
  // FingerState keys (part of HardwareState):
  static const char kKeyFingerStateTouchMajor[];
  static const char kKeyFingerStateTouchMinor[];
  static const char kKeyFingerStateWidthMajor[];
  static const char kKeyFingerStateWidthMinor[];
  static const char kKeyFingerStatePressure[];
  static const char kKeyFingerStateOrientation[];
  static const char kKeyFingerStatePositionX[];
  static const char kKeyFingerStatePositionY[];
  static const char kKeyFingerStateTrackingId[];
  static const char kKeyFingerStateFlags[];
  // Timer/Callback keys:
  static const char kKeyTimerNow[];
  static const char kKeyHandleTimerTimeout[];
  static const char kKeyCallbackRequestWhen[];
  // Gesture keys:
  static const char kKeyGestureType[];
  static const char kValueGestureTypeContactInitiated[];
  static const char kValueGestureTypeMove[];
  static const char kValueGestureTypeScroll[];
  static const char kValueGestureTypeMouseWheel[];
  static const char kValueGestureTypePinch[];
  static const char kValueGestureTypeButtonsChange[];
  static const char kValueGestureTypeFling[];
  static const char kValueGestureTypeSwipe[];
  static const char kValueGestureTypeSwipeLift[];
  static const char kValueGestureTypeFourFingerSwipe[];
  static const char kValueGestureTypeFourFingerSwipeLift[];
  static const char kValueGestureTypeMetrics[];
  static const char kKeyGestureStartTime[];
  static const char kKeyGestureEndTime[];
  static const char kKeyGestureDX[];
  static const char kKeyGestureDY[];
  static const char kKeyGestureOrdinalDX[];
  static const char kKeyGestureOrdinalDY[];
  static const char kKeyGestureMouseWheelTicksDX[];
  static const char kKeyGestureMouseWheelTicksDY[];
  static const char kKeyGesturePinchDZ[];
  static const char kKeyGesturePinchOrdinalDZ[];
  static const char kKeyGesturePinchZoomState[];
  static const char kKeyGestureButtonsChangeDown[];
  static const char kKeyGestureButtonsChangeUp[];
  static const char kKeyGestureFlingVX[];
  static const char kKeyGestureFlingVY[];
  static const char kKeyGestureFlingOrdinalVX[];
  static const char kKeyGestureFlingOrdinalVY[];
  static const char kKeyGestureFlingState[];
  static const char kKeyGestureMetricsType[];
  static const char kKeyGestureMetricsData1[];
  static const char kKeyGestureMetricsData2[];
  // PropChange keys:
  static const char kKeyPropChangeType[];
  static const char kKeyPropChangeName[];
  static const char kKeyPropChangeValue[];
  static const char kValuePropChangeTypeBool[];
  static const char kValuePropChangeTypeDouble[];
  static const char kValuePropChangeTypeInt[];
  static const char kValuePropChangeTypeShort[];

  // Hardware Properties keys:
  static const char kKeyHardwarePropRoot[];
  static const char kKeyHardwarePropLeft[];
  static const char kKeyHardwarePropTop[];
  static const char kKeyHardwarePropRight[];
  static const char kKeyHardwarePropBottom[];
  static const char kKeyHardwarePropXResolution[];
  static const char kKeyHardwarePropYResolution[];
  static const char kKeyHardwarePropXDpi[];
  static const char kKeyHardwarePropYDpi[];
  static const char kKeyHardwarePropOrientationMinimum[];
  static const char kKeyHardwarePropOrientationMaximum[];
  static const char kKeyHardwarePropMaxFingerCount[];
  static const char kKeyHardwarePropMaxTouchCount[];
  static const char kKeyHardwarePropSupportsT5R2[];
  static const char kKeyHardwarePropSemiMt[];
  static const char kKeyHardwarePropIsButtonPad[];
  static const char kKeyHardwarePropHasWheel[];

  static const char kKeyProperties[];

  // AccelFilterInterpreter Debug Data keys:
  static const char kKeyAccelGestureDebug[];
  static const char kKeyAccelDebugNoAccelBadGain[];
  static const char kKeyAccelDebugNoAccelGestureType[];
  static const char kKeyAccelDebugNoAccelSmallDt[];
  static const char kKeyAccelDebugNoAccelSmallSpeed[];
  static const char kKeyAccelDebugDroppedGesture[];
  static const char kKeyAccelDebugXYAreVelocity[];
  static const char kKeyAccelDebugXScale[];
  static const char kKeyAccelDebugYScale[];
  static const char kKeyAccelDebugDt[];
  static const char kKeyAccelDebugAdjustedDt[];
  static const char kKeyAccelDebugSpeed[];
  static const char kKeyAccelDebugSmoothSpeed[];
  static const char kKeyAccelDebugGainX[];
  static const char kKeyAccelDebugGainY[];

  // Timestamp Debug Data keys:
  static const char kKeyTimestampGestureDebug[];
  static const char kKeyTimestampHardwareStateDebug[];
  static const char kKeyTimestampDebugIsUsingFake[];
  static const char kKeyTimestampDebugWasFirstOrBackward[];
  static const char kKeyTimestampDebugPrevMscTimestampIn[];
  static const char kKeyTimestampDebugPrevMscTimestampOut[];
  static const char kKeyTimestampDebugWasDivergenceReset[];
  static const char kKeyTimestampDebugFakeTimestampIn[];
  static const char kKeyTimestampDebugFakeTimestampDelta[];
  static const char kKeyTimestampDebugFakeTimestampOut[];
  static const char kKeyTimestampDebugSkew[];
  static const char kKeyTimestampDebugMaxSkew[];

 private:
  // Extends the tail of the buffer by one element and returns that new element.
  // This may cause an older element to be overwritten if the buffer is full.
  Entry* PushBack();

  size_t TailIdx() const { return (head_idx_ + size_ - 1) % kBufferSize; }

  // JSON-encoders for various types
  Json::Value EncodeHardwareProperties() const;

  Json::Value EncodeHardwareStateCommon(const HardwareState& hwstate);
  Json::Value EncodeHardwareState(const HardwareState& hwstate);
  Json::Value EncodeHardwareState(const HardwareStatePre& hwstate);
  Json::Value EncodeHardwareState(const HardwareStatePost& hwstate);
  Json::Value EncodeHardwareStateDebug(
      const TimestampHardwareStateDebug& debug_data);

  Json::Value EncodeTimerCallback(stime_t timestamp);
  Json::Value EncodeHandleTimer(const HandleTimerPre& handle);
  Json::Value EncodeHandleTimer(const HandleTimerPost& handle);
  Json::Value EncodeCallbackRequest(stime_t timestamp);

  Json::Value EncodeGestureCommon(const Gesture& gesture);
  Json::Value EncodeGesture(const Gesture& gesture);
  Json::Value EncodeGesture(const GestureConsume& gesture);
  Json::Value EncodeGesture(const GestureProduce& gesture);
  Json::Value EncodeGestureDebug(const AccelGestureDebug& debug_data);
  Json::Value EncodeGestureDebug(const TimestampGestureDebug& debug_data);

  Json::Value EncodePropChange(const PropChangeEntry& prop_change);

  // Encode user-configurable properties
  Json::Value EncodePropRegistry();

#ifdef GESTURES_LARGE_LOGGING_BUFFER
  static const size_t kBufferSize = 65536;
#else
  static const size_t kBufferSize = 8192;
#endif

  Entry buffer_[kBufferSize];
  size_t head_idx_;
  size_t size_;

  // We allocate this to be number of entries * max fingers/entry, and
  // if buffer_[i] is a kHardwareState type, then the fingers for it are
  // at finger_states_[i * (max fingers/entry)].
  std::unique_ptr<FingerState[]> finger_states_;
  size_t max_fingers_;

  HardwareProperties hwprops_;
  PropRegistry* prop_reg_;
};

}  // namespace gestures

#endif  // GESTURES_ACTIVITY_LOG_H_
