// Copyright 2012 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include "include/gestures.h"

#include <cstring>
#include <sys/time.h>

#include "include/accel_filter_interpreter.h"
#include "include/box_filter_interpreter.h"
#include "include/click_wiggle_filter_interpreter.h"
#include "include/finger_merge_filter_interpreter.h"
#include "include/finger_metrics.h"
#include "include/fling_stop_filter_interpreter.h"
#include "include/haptic_button_generator_filter_interpreter.h"
#include "include/iir_filter_interpreter.h"
#include "include/immediate_interpreter.h"
#include "include/integral_gesture_filter_interpreter.h"
#include "include/logging.h"
#include "include/logging_filter_interpreter.h"
#include "include/lookahead_filter_interpreter.h"
#include "include/metrics_filter_interpreter.h"
#include "include/mouse_interpreter.h"
#include "include/multitouch_mouse_interpreter.h"
#include "include/non_linearity_filter_interpreter.h"
#include "include/palm_classifying_filter_interpreter.h"
#include "include/prop_registry.h"
#include "include/scaling_filter_interpreter.h"
#include "include/sensor_jump_filter_interpreter.h"
#include "include/split_correcting_filter_interpreter.h"
#include "include/stationary_wiggle_filter_interpreter.h"
#include "include/string_util.h"
#include "include/stuck_button_inhibitor_filter_interpreter.h"
#include "include/t5r2_correcting_filter_interpreter.h"
#include "include/timestamp_filter_interpreter.h"
#include "include/trace_marker.h"
#include "include/tracer.h"
#include "include/trend_classifying_filter_interpreter.h"
#include "include/util.h"

using std::string;
using std::min;
using gestures::StringPrintf;
using gestures::StartsWithASCII;

// C API:

static const int kMinSupportedVersion = 1;
static const int kMaxSupportedVersion = 1;

stime_t StimeFromTimeval(const struct timeval* tv) {
  return static_cast<stime_t>(tv->tv_sec) +
      static_cast<stime_t>(tv->tv_usec) / 1000000.0;
}

stime_t StimeFromTimespec(const struct timespec* ts) {
  return static_cast<stime_t>(ts->tv_sec) +
      static_cast<stime_t>(ts->tv_nsec) / 1000000000.0;
}

std::string HardwareProperties::String() const {
  return StringPrintf("%f,  // left edge\n"
                      "%f,  // top edge\n"
                      "%f,  // right edge\n"
                      "%f,  // bottom edge\n"
                      "%f,  // x pixels/TP width\n"
                      "%f,  // y pixels/TP height\n"
                      "%f,  // x screen DPI\n"
                      "%f,  // y screen DPI\n"
                      "%f,  // orientation minimum\n"
                      "%f,  // orientation maximum\n"
                      "%u,  // max fingers\n"
                      "%u,  // max touch\n"
                      "%u,  // t5r2\n"
                      "%u,  // semi-mt\n"
                      "%u   // is button pad\n",
                      left, top, right, bottom,
                      res_x,
                      res_y,
                      screen_x_dpi,
                      screen_y_dpi,
                      orientation_minimum,
                      orientation_maximum,
                      max_finger_cnt,
                      max_touch_cnt,
                      supports_t5r2,
                      support_semi_mt,
                      is_button_pad);
}

namespace {
string NameForFingerStateFlag(unsigned flag) {
#define CASERET(name)                           \
  case name: return #name
  switch (flag) {
    CASERET(GESTURES_FINGER_WARP_X_NON_MOVE);
    CASERET(GESTURES_FINGER_WARP_Y_NON_MOVE);
    CASERET(GESTURES_FINGER_NO_TAP);
    CASERET(GESTURES_FINGER_POSSIBLE_PALM);
    CASERET(GESTURES_FINGER_PALM);
    CASERET(GESTURES_FINGER_WARP_X_MOVE);
    CASERET(GESTURES_FINGER_WARP_Y_MOVE);
    CASERET(GESTURES_FINGER_WARP_X_TAP_MOVE);
    CASERET(GESTURES_FINGER_WARP_Y_TAP_MOVE);
    CASERET(GESTURES_FINGER_MERGE);
    CASERET(GESTURES_FINGER_TREND_INC_X);
    CASERET(GESTURES_FINGER_TREND_DEC_X);
    CASERET(GESTURES_FINGER_TREND_INC_Y);
    CASERET(GESTURES_FINGER_TREND_DEC_Y);
    CASERET(GESTURES_FINGER_TREND_INC_PRESSURE);
    CASERET(GESTURES_FINGER_TREND_DEC_PRESSURE);
    CASERET(GESTURES_FINGER_TREND_INC_TOUCH_MAJOR);
    CASERET(GESTURES_FINGER_TREND_DEC_TOUCH_MAJOR);
    CASERET(GESTURES_FINGER_INSTANTANEOUS_MOVING);
    CASERET(GESTURES_FINGER_WARP_TELEPORTATION);
  }
#undef CASERET
  return "";
}
}  // namespace {}

string FingerState::FlagsString(unsigned flags) {
  string ret;
  const char kPipeSeparator[] = " | ";
  for (unsigned i = 0; i < 8 * sizeof(flags); i++) {
    const unsigned flag = 1 << i;
    const string name = NameForFingerStateFlag(flag);
    if ((flags & flag) && !name.empty()) {
      ret += kPipeSeparator;
      ret += name;
      flags &= ~flag;
    }
  }
  if (flags) {
    // prepend remaining number
    ret = StringPrintf("%u%s", flags, ret.c_str());
  } else if (StartsWithASCII(ret, kPipeSeparator, false)) {
    // strip extra pipe
    ret = string(ret.c_str() + strlen(kPipeSeparator));
  } else {
    ret = "0";
  }
  return ret;
}

string FingerState::String() const {
  return StringPrintf("{ %f, %f, %f, %f, %f, %f, %f, %f, %d, %s }",
                      touch_major, touch_minor,
                      width_major, width_minor,
                      pressure,
                      orientation,
                      position_x,
                      position_y,
                      tracking_id,
                      FlagsString(flags).c_str());
}

FingerState* HardwareState::GetFingerState(short tracking_id) {
  return const_cast<FingerState*>(
      const_cast<const HardwareState*>(this)->GetFingerState(tracking_id));
}

const FingerState* HardwareState::GetFingerState(short tracking_id) const {
  for (short i = 0; i < finger_cnt; i++) {
    if (fingers[i].tracking_id == tracking_id)
      return &fingers[i];
  }
  return nullptr;
}

string HardwareState::String() const {
  string ret = StringPrintf("{ %f, %d, %d, %d, {",
                            timestamp,
                            buttons_down,
                            finger_cnt,
                            touch_cnt);
  for (size_t i = 0; i < finger_cnt; i++) {
    if (i != 0)
      ret += ",";
    ret += " ";
    ret += fingers[i].String();
  }
  if (finger_cnt > 0)
    ret += " ";
  ret += "} }";
  return ret;
}

bool HardwareState::SameFingersAs(const HardwareState& that) const {
  if (finger_cnt != that.finger_cnt || touch_cnt != that.touch_cnt)
    return false;
  // For now, require fingers to be in the same slots
  for (size_t i = 0; i < finger_cnt; i++)
    if (fingers[i].tracking_id != that.fingers[i].tracking_id)
      return false;
  return true;
}

void HardwareState::DeepCopy(const HardwareState& that,
                             unsigned short max_finger_cnt) {
  timestamp = that.timestamp;
  buttons_down = that.buttons_down;
  touch_cnt = that.touch_cnt;
  finger_cnt = min(that.finger_cnt, max_finger_cnt);
  if(that.fingers != nullptr) {
    memcpy(fingers, that.fingers, finger_cnt * sizeof(FingerState));
  } else if (finger_cnt > 0) {
    Err("HardwareState with no finger data but %d finger count", finger_cnt);
  }
  rel_x = that.rel_x;
  rel_y = that.rel_y;
  rel_wheel = that.rel_wheel;
  rel_wheel_hi_res = that.rel_wheel_hi_res;
  rel_hwheel = that.rel_hwheel;
  msc_timestamp = that.msc_timestamp;
}

string Gesture::String() const {
  switch (type) {
    case kGestureTypeNull:
      return "(Gesture type: null)";
    case kGestureTypeContactInitiated:
      return StringPrintf("(Gesture type: contactInitiated "
                          "start: %f stop: %f)", start_time, end_time);
    case kGestureTypeMove:
      return StringPrintf("(Gesture type: move start: %f stop: %f "
                          "dx: %f dy: %f ordinal_dx: %f ordinal_dy: %f)",
                          start_time, end_time,
                          details.move.dx, details.move.dy,
                          details.move.ordinal_dx, details.move.ordinal_dy);
    case kGestureTypeScroll:
      return StringPrintf("(Gesture type: scroll start: %f stop: %f "
                          "dx: %f dy: %f ordinal_dx: %f ordinal_dy: %f)",
                          start_time, end_time,
                          details.scroll.dx, details.scroll.dy,
                          details.scroll.ordinal_dx, details.scroll.ordinal_dy);
    case kGestureTypeMouseWheel:
      return StringPrintf("(Gesture type: wheel start: %f stop %f "
                          "dx: %f dy: %f "
                          "tick_120ths_dx: %d tick_120ths_dy: %d)",
                          start_time, end_time,
                          details.wheel.dx, details.wheel.dy,
                          details.wheel.tick_120ths_dx,
                          details.wheel.tick_120ths_dy);
    case kGestureTypePinch:
      return StringPrintf("(Gesture type: pinch start: %f stop: %f "
                          "dz: %f ordinal_dz: %f, state: %d)", start_time,
                          end_time, details.pinch.dz, details.pinch.ordinal_dz,
                          details.pinch.zoom_state);
    case kGestureTypeButtonsChange:
      return StringPrintf("(Gesture type: buttons start: %f stop: "
                          "%f down: %d up: %d)", start_time, end_time,
                          details.buttons.down, details.buttons.up);
    case kGestureTypeFling:
      return StringPrintf("(Gesture type: fling start: %f stop: "
                          "%f vx: %f vy: %f ordinal_dx: %f ordinal_dy: %f "
                          "state: %s)", start_time, end_time,
                          details.fling.vx, details.fling.vy,
                          details.fling.ordinal_vx, details.fling.ordinal_vy,
                          details.fling.fling_state == GESTURES_FLING_START ?
                          "start" : "tapdown");
    case kGestureTypeSwipe:
      return StringPrintf("(Gesture type: swipe start: %f stop: %f "
                          "dx: %f dy: %f ordinal_dx: %f ordinal_dy: %f)",
                          start_time, end_time,
                          details.swipe.dx, details.swipe.dy,
                          details.swipe.ordinal_dx, details.swipe.ordinal_dy);
    case kGestureTypeSwipeLift:
      return StringPrintf("(Gesture type: swipeLift start: %f stop: %f)",
                          start_time, end_time);
    case kGestureTypeFourFingerSwipe:
      return StringPrintf("(Gesture type: fourFingerSwipe start: %f stop: %f "
                          "dx: %f dy: %f ordinal_dx: %f ordinal_dy: %f)",
                          start_time, end_time,
                          details.four_finger_swipe.dx,
                          details.four_finger_swipe.dy,
                          details.four_finger_swipe.ordinal_dx,
                          details.four_finger_swipe.ordinal_dy);
    case kGestureTypeFourFingerSwipeLift:
      return StringPrintf("(Gesture type: fourFingerSwipeLift start: %f "
                          "stop: %f)", start_time, end_time);
    case kGestureTypeMetrics:
      return StringPrintf("(Gesture type: metrics start: %f stop: %f "
                          "type: %d d1: %f d2: %f)", start_time, end_time,
                          details.metrics.type,
                          details.metrics.data[0], details.metrics.data[1]);
  }
  return "(Gesture type: unknown)";
}

bool Gesture::operator==(const Gesture& that) const {
  if (type != that.type)
    return false;
  bool times_equal = gestures::DoubleEq(start_time, that.start_time) &&
                     gestures::DoubleEq(end_time, that.end_time);
  switch (type) {
    case kGestureTypeNull:  // fall through
    case kGestureTypeContactInitiated:
      return true;
    case kGestureTypeMove:
      return times_equal &&
          gestures::FloatEq(details.move.dx, that.details.move.dx) &&
          gestures::FloatEq(details.move.dy, that.details.move.dy);
    case kGestureTypeScroll:
      return times_equal &&
          gestures::FloatEq(details.scroll.dx, that.details.scroll.dx) &&
          gestures::FloatEq(details.scroll.dy, that.details.scroll.dy);
    case kGestureTypeMouseWheel:
      return times_equal &&
          gestures::FloatEq(details.wheel.dx, that.details.wheel.dx) &&
          gestures::FloatEq(details.wheel.dy, that.details.wheel.dy) &&
          details.wheel.tick_120ths_dx == that.details.wheel.tick_120ths_dx &&
          details.wheel.tick_120ths_dy == that.details.wheel.tick_120ths_dy;
    case kGestureTypePinch:
      return times_equal &&
          gestures::FloatEq(details.pinch.dz, that.details.pinch.dz);
    case kGestureTypeButtonsChange:
      return times_equal &&
          details.buttons.down == that.details.buttons.down &&
          details.buttons.up == that.details.buttons.up;
    case kGestureTypeFling:
      return times_equal &&
          gestures::FloatEq(details.fling.vx, that.details.fling.vx) &&
          gestures::FloatEq(details.fling.vy, that.details.fling.vy);
    case kGestureTypeSwipe:
      return times_equal &&
          gestures::FloatEq(details.swipe.dx, that.details.swipe.dx);
    case kGestureTypeSwipeLift:
      return times_equal;
    case kGestureTypeFourFingerSwipe:
      return times_equal &&
          gestures::FloatEq(details.four_finger_swipe.dx,
              that.details.four_finger_swipe.dx);
    case kGestureTypeFourFingerSwipeLift:
      return times_equal;
    case kGestureTypeMetrics:
      return times_equal &&
          details.metrics.type == that.details.metrics.type &&
          gestures::FloatEq(details.metrics.data[0],
              that.details.metrics.data[0]) &&
          gestures::FloatEq(details.metrics.data[1],
              that.details.metrics.data[1]);
  }
  return true;
}

GestureInterpreter* NewGestureInterpreterImpl(int version) {
  if (version < kMinSupportedVersion) {
    Err("Client too old. It's using version %d"
        ", but library has min supported version %d",
        version,
        kMinSupportedVersion);
    return nullptr;
  }
  if (version > kMaxSupportedVersion) {
    Err("Client too new. It's using version %d"
        ", but library has max supported version %d",
        version,
        kMaxSupportedVersion);
    return nullptr;
  }
  return new gestures::GestureInterpreter(version);
}

void DeleteGestureInterpreter(GestureInterpreter* obj) {
  delete obj;
}

void GestureInterpreterPushHardwareState(GestureInterpreter* obj,
                                         struct HardwareState* hwstate) {
  obj->PushHardwareState(hwstate);
}

void GestureInterpreterSetHardwareProperties(
    GestureInterpreter* obj,
    const struct HardwareProperties* hwprops) {
  obj->SetHardwareProperties(*hwprops);
}

void GestureInterpreterSetCallback(GestureInterpreter* obj,
                                   GestureReadyFunction fn,
                                   void* user_data) {
  obj->SetCallback(fn, user_data);
}

void GestureInterpreterSetTimerProvider(GestureInterpreter* obj,
                                        GesturesTimerProvider* tp,
                                        void* data) {
  obj->SetTimerProvider(tp, data);
}

void GestureInterpreterSetPropProvider(GestureInterpreter* obj,
                                       GesturesPropProvider* pp,
                                       void* data) {
  obj->SetPropProvider(pp, data);
}

void GestureInterpreterInitialize(GestureInterpreter* obj,
                                  enum GestureInterpreterDeviceClass cls) {
  obj->Initialize(cls);
}

// C++ API:
namespace gestures {
class GestureInterpreterConsumer : public GestureConsumer {
 public:
  GestureInterpreterConsumer(GestureReadyFunction callback,
                             void* callback_data)
      : callback_(callback),
        callback_data_(callback_data) {}

  void SetCallback(GestureReadyFunction callback, void* callback_data) {
    callback_ = callback;
    callback_data_ = callback_data;
  }

  void ConsumeGesture(const Gesture& gesture) {
    AssertWithReturn(gesture.type != kGestureTypeNull);
    if (callback_)
      callback_(callback_data_, &gesture);
  }

 private:
  GestureReadyFunction callback_;
  void* callback_data_;
};
}

GestureInterpreter::GestureInterpreter(int version)
    : callback_(nullptr),
      callback_data_(nullptr),
      timer_provider_(nullptr),
      timer_provider_data_(nullptr),
      interpret_timer_(nullptr),
      loggingFilter_(nullptr) {
  prop_reg_.reset(new PropRegistry);
  tracer_.reset(new Tracer(prop_reg_.get(), TraceMarker::StaticTraceWrite));
  TraceMarker::CreateTraceMarker();
}

GestureInterpreter::~GestureInterpreter() {
  SetTimerProvider(nullptr, nullptr);
  SetPropProvider(nullptr, nullptr);
  TraceMarker::DeleteTraceMarker();
}

namespace {
stime_t InternalTimerCallback(stime_t now, void* callback_data) {
  Log("TimerCallback called");
  GestureInterpreter* gi = reinterpret_cast<GestureInterpreter*>(callback_data);
  stime_t next = NO_DEADLINE;
  gi->TimerCallback(now, &next);
  return next;
}
}

void GestureInterpreter::PushHardwareState(HardwareState* hwstate) {
  if (!interpreter_.get()) {
    Err("Filters are not composed yet!");
    return;
  }
  stime_t timeout = NO_DEADLINE;
  interpreter_->SyncInterpret(*hwstate, &timeout);
  if (timer_provider_ && interpret_timer_) {
    if (timeout == NO_DEADLINE) {
      timer_provider_->cancel_fn(timer_provider_data_, interpret_timer_);
    } else {
      timer_provider_->set_fn(timer_provider_data_,
                              interpret_timer_,
                              timeout,
                              InternalTimerCallback,
                              this);
      Log("Setting timer for %f s out.", timeout);
    }
  } else {
    ErrOnce("No timer provider has been set, so some features won't work.");
  }
}

void GestureInterpreter::SetHardwareProperties(
    const HardwareProperties& hwprops) {
  if (!interpreter_.get()) {
    Err("Filters are not composed yet!");
    return;
  }
  hwprops_ = hwprops;
  if (consumer_)
    interpreter_->Initialize(&hwprops_, nullptr, mprops_.get(),
                             consumer_.get());
}

void GestureInterpreter::TimerCallback(stime_t now, stime_t* timeout) {
  if (!interpreter_.get()) {
    Err("Filters are not composed yet!");
    return;
  }
  interpreter_->HandleTimer(now, timeout);
}

void GestureInterpreter::SetTimerProvider(GesturesTimerProvider* tp,
                                          void* data) {
  if (timer_provider_ == tp && timer_provider_data_ == data)
    return;
  if (timer_provider_ && interpret_timer_) {
    timer_provider_->free_fn(timer_provider_data_, interpret_timer_);
    interpret_timer_ = nullptr;
  }
  if (interpret_timer_)
    Log("How was interpret_timer_ not null?!");
  timer_provider_ = tp;
  timer_provider_data_ = data;
  if (timer_provider_)
    interpret_timer_ = timer_provider_->create_fn(timer_provider_data_);
}

void GestureInterpreter::SetPropProvider(GesturesPropProvider* pp,
                                         void* data) {
  prop_reg_->SetPropProvider(pp, data);
}

void GestureInterpreter::SetCallback(GestureReadyFunction callback,
                                     void* client_data) {
  callback_ = callback;
  callback_data_ = client_data;

  if (consumer_)
    consumer_->SetCallback(callback, client_data);
}

void GestureInterpreter::set_callback(GestureReadyFunction callback,
                                      void* client_data) {
  SetCallback(callback, client_data);
}

void GestureInterpreter::InitializeTouchpad(void) {
  if (prop_reg_.get()) {
    stack_version_ = std::make_unique<IntProperty>(prop_reg_.get(),
                                                   "Touchpad Stack Version", 2);
    if (stack_version_->val_ == 2) {
      InitializeTouchpad2();
      return;
    }
  }

  Interpreter* temp = new ImmediateInterpreter(prop_reg_.get(), tracer_.get());
  temp = new FlingStopFilterInterpreter(prop_reg_.get(), temp, tracer_.get(),
                                        GESTURES_DEVCLASS_TOUCHPAD);
  temp = new ClickWiggleFilterInterpreter(prop_reg_.get(), temp, tracer_.get());
  temp = new PalmClassifyingFilterInterpreter(prop_reg_.get(), temp,
                                              tracer_.get());
  temp = new IirFilterInterpreter(prop_reg_.get(), temp, tracer_.get());
  temp = new LookaheadFilterInterpreter(prop_reg_.get(), temp, tracer_.get());
  temp = new BoxFilterInterpreter(prop_reg_.get(), temp, tracer_.get());
  temp = new StationaryWiggleFilterInterpreter(prop_reg_.get(), temp,
                                               tracer_.get());
  temp = new SensorJumpFilterInterpreter(prop_reg_.get(), temp, tracer_.get());
  temp = new AccelFilterInterpreter(prop_reg_.get(), temp, tracer_.get());
  temp = new SplitCorrectingFilterInterpreter(prop_reg_.get(), temp,
                                              tracer_.get());
  temp = new TrendClassifyingFilterInterpreter(prop_reg_.get(), temp,
                                               tracer_.get());
  temp = new MetricsFilterInterpreter(prop_reg_.get(), temp, tracer_.get(),
                                      GESTURES_DEVCLASS_TOUCHPAD);
  temp = new ScalingFilterInterpreter(prop_reg_.get(), temp, tracer_.get(),
                                      GESTURES_DEVCLASS_TOUCHPAD);
  temp = new FingerMergeFilterInterpreter(prop_reg_.get(), temp, tracer_.get());
  temp = new StuckButtonInhibitorFilterInterpreter(temp, tracer_.get());
  temp = new HapticButtonGeneratorFilterInterpreter(prop_reg_.get(), temp,
                                                    tracer_.get());
  temp = new T5R2CorrectingFilterInterpreter(prop_reg_.get(), temp,
                                             tracer_.get());
  temp = new NonLinearityFilterInterpreter(prop_reg_.get(), temp,
                                           tracer_.get());
  temp = new TimestampFilterInterpreter(prop_reg_.get(), temp, tracer_.get());
  temp = loggingFilter_ = new LoggingFilterInterpreter(prop_reg_.get(), temp,
                                                       tracer_.get());
  interpreter_.reset(temp);
  temp = nullptr;
}

void GestureInterpreter::InitializeTouchpad2(void) {
  Interpreter* temp = new ImmediateInterpreter(prop_reg_.get(), tracer_.get());
  temp = new FlingStopFilterInterpreter(prop_reg_.get(), temp, tracer_.get(),
                                        GESTURES_DEVCLASS_TOUCHPAD);
  temp = new ClickWiggleFilterInterpreter(prop_reg_.get(), temp, tracer_.get());
  temp = new PalmClassifyingFilterInterpreter(prop_reg_.get(), temp,
                                              tracer_.get());
  temp = new LookaheadFilterInterpreter(prop_reg_.get(), temp, tracer_.get());
  temp = new BoxFilterInterpreter(prop_reg_.get(), temp, tracer_.get());
  temp = new StationaryWiggleFilterInterpreter(prop_reg_.get(), temp,
                                               tracer_.get());
  temp = new AccelFilterInterpreter(prop_reg_.get(), temp, tracer_.get());
  temp = new TrendClassifyingFilterInterpreter(prop_reg_.get(), temp,
                                               tracer_.get());
  temp = new MetricsFilterInterpreter(prop_reg_.get(), temp, tracer_.get(),
                                      GESTURES_DEVCLASS_TOUCHPAD);
  temp = new ScalingFilterInterpreter(prop_reg_.get(), temp, tracer_.get(),
                                      GESTURES_DEVCLASS_TOUCHPAD);
  temp = new FingerMergeFilterInterpreter(prop_reg_.get(), temp, tracer_.get());
  temp = new StuckButtonInhibitorFilterInterpreter(temp, tracer_.get());
  temp = new HapticButtonGeneratorFilterInterpreter(prop_reg_.get(), temp,
                                                    tracer_.get());
  temp = new TimestampFilterInterpreter(prop_reg_.get(), temp, tracer_.get());
  temp = loggingFilter_ = new LoggingFilterInterpreter(prop_reg_.get(), temp,
                                                       tracer_.get());
  interpreter_.reset(temp);
  temp = nullptr;
}

void GestureInterpreter::InitializeMouse(GestureInterpreterDeviceClass cls) {
  Interpreter* temp = new MouseInterpreter(prop_reg_.get(), tracer_.get());
  // TODO(clchiou;chromium-os:36321): Use mouse acceleration algorithm for mice
  temp = new AccelFilterInterpreter(prop_reg_.get(), temp, tracer_.get());
  temp = new ScalingFilterInterpreter(prop_reg_.get(), temp, tracer_.get(),
                                      cls);
  temp = new MetricsFilterInterpreter(prop_reg_.get(), temp, tracer_.get(),
                                      cls);
  temp = new IntegralGestureFilterInterpreter(temp, tracer_.get());
  temp = loggingFilter_ = new LoggingFilterInterpreter(prop_reg_.get(), temp,
                                                       tracer_.get());
  interpreter_.reset(temp);
  temp = nullptr;
}

void GestureInterpreter::InitializeMultitouchMouse(void) {
  Interpreter* temp = new MultitouchMouseInterpreter(prop_reg_.get(),
                                                     tracer_.get());
  temp = new FlingStopFilterInterpreter(prop_reg_.get(), temp, tracer_.get(),
                                        GESTURES_DEVCLASS_MULTITOUCH_MOUSE);
  temp = new ClickWiggleFilterInterpreter(prop_reg_.get(), temp, tracer_.get());
  temp = new LookaheadFilterInterpreter(prop_reg_.get(), temp, tracer_.get());
  temp = new BoxFilterInterpreter(prop_reg_.get(), temp, tracer_.get());
  // TODO(clchiou;chromium-os:36321): Use mouse acceleration algorithm for mice
  temp = new AccelFilterInterpreter(prop_reg_.get(), temp, tracer_.get());
  temp = new ScalingFilterInterpreter(prop_reg_.get(), temp, tracer_.get(),
                                      GESTURES_DEVCLASS_MULTITOUCH_MOUSE);
  temp = new MetricsFilterInterpreter(prop_reg_.get(), temp, tracer_.get(),
                                      GESTURES_DEVCLASS_MULTITOUCH_MOUSE);
  temp = new IntegralGestureFilterInterpreter(temp, tracer_.get());
  temp = new StuckButtonInhibitorFilterInterpreter(temp, tracer_.get());
  temp = new NonLinearityFilterInterpreter(prop_reg_.get(), temp,
                                           tracer_.get());
  temp = loggingFilter_ = new LoggingFilterInterpreter(prop_reg_.get(), temp,
                                                       tracer_.get());
  interpreter_.reset(temp);
  temp = nullptr;
}

void GestureInterpreter::Initialize(GestureInterpreterDeviceClass cls) {
  if (cls == GESTURES_DEVCLASS_TOUCHPAD ||
      cls == GESTURES_DEVCLASS_TOUCHSCREEN)
    InitializeTouchpad();
  else if (cls == GESTURES_DEVCLASS_MOUSE ||
           cls == GESTURES_DEVCLASS_POINTING_STICK)
    InitializeMouse(cls);
  else if (cls == GESTURES_DEVCLASS_MULTITOUCH_MOUSE)
    InitializeMultitouchMouse();
  else
    Err("Couldn't recognize device class: %d", cls);

  mprops_.reset(new MetricsProperties(prop_reg_.get()));
  consumer_.reset(new GestureInterpreterConsumer(callback_,
                                                   callback_data_));
}

std::string GestureInterpreter::EncodeActivityLog() {
  return loggingFilter_->EncodeActivityLog();
}

const GestureMove kGestureMove = { 0, 0, 0, 0 };
const GestureScroll kGestureScroll = { 0, 0, 0, 0, 0 };
const GestureMouseWheel kGestureMouseWheel = { 0, 0, 0, 0 };
const GestureButtonsChange kGestureButtonsChange = { 0, 0, 0 };
const GestureFling kGestureFling = { 0, 0, 0, 0, 0 };
const GestureSwipe kGestureSwipe = { 0, 0, 0, 0 };
const GestureFourFingerSwipe kGestureFourFingerSwipe = { 0, 0, 0, 0 };
const GesturePinch kGesturePinch = { 0, 0, 0 };
const GestureSwipeLift kGestureSwipeLift = { };
const GestureFourFingerSwipeLift kGestureFourFingerSwipeLift = { };
const GestureMetrics kGestureMetrics = { kGestureMetricsTypeUnknown, {0, 0} };
