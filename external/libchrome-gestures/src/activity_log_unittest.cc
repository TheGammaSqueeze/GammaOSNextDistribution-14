// Copyright 2011 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <string>

#include <gtest/gtest.h>

#include "include/activity_log.h"
#include "include/macros.h"
#include "include/prop_registry.h"
#include "include/unittest_util.h"

using std::string;

namespace gestures {

class ActivityLogTest : public ::testing::Test {};

TEST(ActivityLogTest, SimpleTest) {
  PropRegistry prop_reg;
  BoolProperty true_prop(&prop_reg, "true prop", true);
  BoolProperty false_prop(&prop_reg, "false prop", false);
  DoubleProperty double_prop(&prop_reg, "double prop", 77.25);
  IntProperty int_prop(&prop_reg, "int prop", -816);
  StringProperty string_prop(&prop_reg, "string prop", "foobarstr");

  ActivityLog log(&prop_reg);
  EXPECT_TRUE(strstr(log.Encode().c_str(), "true"));
  EXPECT_TRUE(strstr(log.Encode().c_str(), "false"));
  EXPECT_TRUE(strstr(log.Encode().c_str(), "77.25"));
  EXPECT_TRUE(strstr(log.Encode().c_str(), "-816"));
  EXPECT_TRUE(strstr(log.Encode().c_str(), "foobarstr"));

  HardwareProperties hwprops = {
    .left = 6011,
    .top = 6012,
    .right = 6013,
    .bottom = 6014,
    .res_x = 6015,
    .res_y = 6016,
    .screen_x_dpi = 6017,
    .screen_y_dpi = 6018,
    .orientation_minimum = 6019,
    .orientation_maximum = 6020,
    .max_finger_cnt = 6021,
    .max_touch_cnt = 6022,
    .supports_t5r2 = 1,
    .support_semi_mt = 0,
    .is_button_pad = 1,
    .has_wheel = 0,
    .wheel_is_hi_res = 0,
    .is_haptic_pad = 0,
  };

  log.SetHardwareProperties(hwprops);

  const char* expected_strings[] = {
    "6011", "6012", "6013", "6014", "6015", "6016",
    "6017", "6018", "6019", "6020", "6021", "6022"
  };
  string hwprops_log = log.Encode();
  for (size_t i = 0; i < arraysize(expected_strings); i++)
    EXPECT_TRUE(strstr(hwprops_log.c_str(), expected_strings[i]));

  EXPECT_EQ(0, log.size());
  EXPECT_GT(log.MaxSize(), 10);

  FingerState fs = { 0.0, 0.0, 0.0, 0.0, 9.0, 0.0, 3.0, 4.0, 22, 0 };
  HardwareState hs = make_hwstate(1.0, 0, 1, 1, &fs);
  log.LogHardwareState(hs);
  EXPECT_EQ(1, log.size());
  EXPECT_TRUE(strstr(log.Encode().c_str(), "22"));
  ActivityLog::Entry* entry = log.GetEntry(0);
  EXPECT_TRUE(std::holds_alternative<HardwareState>(entry->details));

  log.LogTimerCallback(234.5);
  EXPECT_EQ(2, log.size());
  EXPECT_TRUE(strstr(log.Encode().c_str(), "234.5"));
  entry = log.GetEntry(1);
  EXPECT_TRUE(std::holds_alternative<ActivityLog::TimerCallbackEntry>
                (entry->details));

  log.LogCallbackRequest(90210);
  EXPECT_EQ(3, log.size());
  EXPECT_TRUE(strstr(log.Encode().c_str(), "90210"));
  entry = log.GetEntry(2);
  EXPECT_TRUE(std::holds_alternative<ActivityLog::CallbackRequestEntry>
                (entry->details));

  Gesture null;
  Gesture move(kGestureMove, 1.0, 2.0, 773, 4.0);
  Gesture scroll(kGestureScroll, 1.0, 2.0, 312, 4.0);
  Gesture buttons(kGestureButtonsChange, 1.0, 847, 3, 4, false);
  Gesture contact_initiated;
  contact_initiated.type = kGestureTypeContactInitiated;
  Gesture mousewheel(kGestureMouseWheel, 1.0, 2.0, 30.0, 40.0, 3, 4);
  Gesture pinch(kGesturePinch, 1.0, 2.0, 3.0, 4.0);
  Gesture fling(kGestureFling, 1.0, 2.0, 42.0, 24.0, 1);
  Gesture swipe(kGestureSwipe, 1.0, 2.0, 128.0, 4.0);
  Gesture swipelift(kGestureSwipeLift, 1.0, 2.0);
  Gesture swipe4f(kGestureFourFingerSwipe, 1.0, 2.0, 256.0, 4.0);
  Gesture swipe4flift(kGestureFourFingerSwipeLift, 1.0, 2.0);
  Gesture metrics(kGestureMetrics, 1.0, 2.0,
                  kGestureMetricsTypeMouseMovement, 3.0, 4.0);

  Gesture* gs[] = {
    &null, &move, &scroll, &buttons, &contact_initiated,
    &mousewheel, &pinch, &fling, &swipe, &swipelift,
    &swipe4f, &swipe4flift, &metrics
  };
  const char* test_strs[] = {
    "null", "773", "312", "847", "nitiated",
    "30", "3", "42", "128", "null",
    "256", "null", "1"
  };

  ASSERT_EQ(arraysize(gs), arraysize(test_strs));
  for (size_t i = 0; i < arraysize(gs); ++i) {
    log.LogGesture(*gs[i]);
    EXPECT_TRUE(strstr(log.Encode().c_str(), test_strs[i]))
      << "i=" << i;
    entry = log.GetEntry(log.size() - 1);
    EXPECT_TRUE(std::holds_alternative<Gesture>(entry->details))
      << "i=" << i;
  }

  log.Clear();
  EXPECT_EQ(0, log.size());
}

TEST(ActivityLogTest, WrapAroundTest) {
  ActivityLog log(nullptr);
  // overfill the buffer
  const size_t fill_size = (ActivityLog::kBufferSize * 3) / 2;
  for (size_t i = 0; i < fill_size; i++)
    log.LogCallbackRequest(static_cast<stime_t>(i));
  const string::size_type prefix_length = 100;
  string first_prefix = log.Encode().substr(0, prefix_length);
  log.LogCallbackRequest(static_cast<stime_t>(fill_size));
  string second_prefix = log.Encode().substr(0, prefix_length);
  EXPECT_NE(first_prefix, second_prefix);
}

TEST(ActivityLogTest, VersionTest) {
  ActivityLog log(nullptr);
  string thelog = log.Encode();
  EXPECT_TRUE(thelog.find(VCSID) != string::npos);
}

TEST(ActivityLogTest, EncodePropChangeBoolTest) {
  ActivityLog log(nullptr);
  Json::Value ret;

  ActivityLog::PropChangeEntry bool_prop;
  bool_prop.name = "boolean";
  bool_prop.value = static_cast<GesturesPropBool>(true);
  ret = log.EncodePropChange(bool_prop);
  EXPECT_EQ(ret[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyPropChange));
  EXPECT_EQ(ret[ActivityLog::kKeyPropChangeName],
            Json::Value(bool_prop.name));
  EXPECT_EQ(static_cast<GesturesPropBool>(
            ret[ActivityLog::kKeyPropChangeValue].asBool()),
            std::get<GesturesPropBool>(bool_prop.value));
  EXPECT_EQ(ret[ActivityLog::kKeyPropChangeType],
            ActivityLog::kValuePropChangeTypeBool);
}

TEST(ActivityLogTest, EncodePropChangeDoubleTest) {
  ActivityLog log(nullptr);
  Json::Value ret;

  ActivityLog::PropChangeEntry double_prop;
  double_prop.name = "double";
  double_prop.value = 42.0;
  ret = log.EncodePropChange(double_prop);
  EXPECT_EQ(ret[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyPropChange));
  EXPECT_EQ(ret[ActivityLog::kKeyPropChangeName],
            Json::Value(double_prop.name));
  EXPECT_EQ(ret[ActivityLog::kKeyPropChangeValue].asDouble(),
            std::get<double>(double_prop.value));
  EXPECT_EQ(ret[ActivityLog::kKeyPropChangeType],
            ActivityLog::kValuePropChangeTypeDouble);
}

TEST(ActivityLogTest, EncodePropChangeIntTest) {
  ActivityLog log(nullptr);
  Json::Value ret;

  ActivityLog::PropChangeEntry int_prop;
  int_prop.name = "int";
  int_prop.value = 42;
  ret = log.EncodePropChange(int_prop);
  EXPECT_EQ(ret[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyPropChange));
  EXPECT_EQ(ret[ActivityLog::kKeyPropChangeName],
            Json::Value(int_prop.name));
  EXPECT_EQ(ret[ActivityLog::kKeyPropChangeValue].asInt(),
            std::get<int>(int_prop.value));
  EXPECT_EQ(ret[ActivityLog::kKeyPropChangeType],
            ActivityLog::kValuePropChangeTypeInt);
}

TEST(ActivityLogTest, EncodePropChangeShortTest) {
  ActivityLog log(nullptr);
  Json::Value ret;

  ActivityLog::PropChangeEntry short_prop;
  short_prop.name = "short";
  short_prop.value = static_cast<short>(42);
  ret = log.EncodePropChange(short_prop);
  EXPECT_EQ(ret[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyPropChange));
  EXPECT_EQ(ret[ActivityLog::kKeyPropChangeName],
            Json::Value(short_prop.name));
  EXPECT_EQ(static_cast<short>(
            ret[ActivityLog::kKeyPropChangeValue].asInt()),
            std::get<short>(short_prop.value));
  EXPECT_EQ(ret[ActivityLog::kKeyPropChangeType],
            ActivityLog::kValuePropChangeTypeShort);
}

TEST(ActivityLogTest, HardwareStatePreTest) {
  PropRegistry prop_reg;
  ActivityLog log(&prop_reg);

  HardwareProperties hwprops = {
    .left = 6011,
    .top = 6012,
    .right = 6013,
    .bottom = 6014,
    .res_x = 6015,
    .res_y = 6016,
    .screen_x_dpi = 6017,
    .screen_y_dpi = 6018,
    .orientation_minimum = 6019,
    .orientation_maximum = 6020,
    .max_finger_cnt = 6021,
    .max_touch_cnt = 6022,
    .supports_t5r2 = 1,
    .support_semi_mt = 0,
    .is_button_pad = 1,
    .has_wheel = 0,
    .wheel_is_hi_res = 0,
    .is_haptic_pad = 0,
  };
  log.SetHardwareProperties(hwprops);

  FingerState fs = { 0.0, 0.0, 0.0, 0.0, 9.0, 0.0, 3.0, 4.0, 22, 0 };
  HardwareState hs = make_hwstate(1.0, 0, 1, 1, &fs);

  ActivityLog::Entry* entry;
  Json::Value result;

  EXPECT_EQ(0, log.size());

  // Build and log a HardwareStatePre structure
  log.LogHardwareStatePre("ActivityLogTest_HwStateTest", hs);
  ASSERT_EQ(1, log.size());
  entry = log.GetEntry(0);
  ASSERT_TRUE(std::holds_alternative<ActivityLog::HardwareStatePre>
                (entry->details));

  // Encode the HardwareStatePre into Json
  result = log.EncodeCommonInfo();
  result = result[ActivityLog::kKeyRoot][0];

  // Verify the Json information
  EXPECT_EQ(result[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyHardwareStatePre));
  EXPECT_EQ(result[ActivityLog::kKeyMethodName],
            Json::Value("ActivityLogTest_HwStateTest"));
  EXPECT_EQ(result[ActivityLog::kKeyHardwareStateButtonsDown],
            Json::Value(hs.buttons_down));
  EXPECT_EQ(result[ActivityLog::kKeyHardwareStateTouchCnt],
            Json::Value(hs.touch_cnt));
  EXPECT_EQ(result[ActivityLog::kKeyHardwareStateTimestamp],
            Json::Value(hs.timestamp));
  EXPECT_EQ(result[ActivityLog::kKeyHardwareStateRelX], Json::Value(hs.rel_x));
  EXPECT_EQ(result[ActivityLog::kKeyHardwareStateRelY], Json::Value(hs.rel_y));
  EXPECT_EQ(result[ActivityLog::kKeyHardwareStateRelWheel],
            Json::Value(hs.rel_wheel));
  EXPECT_EQ(result[ActivityLog::kKeyHardwareStateRelHWheel],
            Json::Value(hs.rel_hwheel));
  log.Clear();
}

TEST(ActivityLogTest, HardwareStatePostTest) {
  PropRegistry prop_reg;
  ActivityLog log(&prop_reg);

  HardwareProperties hwprops = {
    .left = 6011,
    .top = 6012,
    .right = 6013,
    .bottom = 6014,
    .res_x = 6015,
    .res_y = 6016,
    .screen_x_dpi = 6017,
    .screen_y_dpi = 6018,
    .orientation_minimum = 6019,
    .orientation_maximum = 6020,
    .max_finger_cnt = 6021,
    .max_touch_cnt = 6022,
    .supports_t5r2 = 1,
    .support_semi_mt = 0,
    .is_button_pad = 1,
    .has_wheel = 0,
    .wheel_is_hi_res = 0,
    .is_haptic_pad = 0,
  };
  log.SetHardwareProperties(hwprops);

  FingerState fs = { 0.0, 0.0, 0.0, 0.0, 9.0, 0.0, 3.0, 4.0, 22, 0 };
  HardwareState hs = make_hwstate(1.0, 0, 1, 1, &fs);

  ActivityLog::Entry* entry;
  Json::Value result;

  EXPECT_EQ(0, log.size());

  // Build and log a HardwareStatePost structure
  log.LogHardwareStatePost("ActivityLogTest_HwStateTest", hs);
  ASSERT_EQ(1, log.size());
  entry = log.GetEntry(0);
  ASSERT_TRUE(std::holds_alternative<ActivityLog::HardwareStatePost>
                (entry->details));

  // Encode the HardwareStatePost into Json
  result = log.EncodeCommonInfo();
  result = result[ActivityLog::kKeyRoot][0];

  // Verify the Json information
  EXPECT_EQ(result[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyHardwareStatePost));
  EXPECT_EQ(result[ActivityLog::kKeyMethodName],
            Json::Value("ActivityLogTest_HwStateTest"));
  EXPECT_EQ(result[ActivityLog::kKeyHardwareStateButtonsDown],
            Json::Value(hs.buttons_down));
  EXPECT_EQ(result[ActivityLog::kKeyHardwareStateTouchCnt],
            Json::Value(hs.touch_cnt));
  EXPECT_EQ(result[ActivityLog::kKeyHardwareStateTimestamp],
            Json::Value(hs.timestamp));
  EXPECT_EQ(result[ActivityLog::kKeyHardwareStateRelX], Json::Value(hs.rel_x));
  EXPECT_EQ(result[ActivityLog::kKeyHardwareStateRelY], Json::Value(hs.rel_y));
  EXPECT_EQ(result[ActivityLog::kKeyHardwareStateRelWheel],
            Json::Value(hs.rel_wheel));
  EXPECT_EQ(result[ActivityLog::kKeyHardwareStateRelHWheel],
            Json::Value(hs.rel_hwheel));
  log.Clear();
}


TEST(ActivityLogTest, GestureConsumeTest) {
  PropRegistry prop_reg;
  ActivityLog log(&prop_reg);
  ActivityLog::Entry* entry;
  Json::Value result;

  EXPECT_EQ(0, log.size());

  // Build and log a GestureConsume structure
  Gesture move(kGestureMove, 1.0, 2.0, 773, 4.0);
  log.LogGestureConsume("ActivityLogTest_GestureTest", move);
  ASSERT_EQ(1, log.size());
  entry = log.GetEntry(0);
  ASSERT_TRUE(std::holds_alternative<ActivityLog::GestureConsume>
                (entry->details));

  // Encode the GestureConsume into Json
  result = log.EncodeCommonInfo();
  result = result[ActivityLog::kKeyRoot][0];

  // Verify the Json information
  EXPECT_EQ(result[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyGestureConsume));
  EXPECT_EQ(result[ActivityLog::kKeyMethodName],
            Json::Value("ActivityLogTest_GestureTest"));
  EXPECT_EQ(result[ActivityLog::kKeyGestureType],
            Json::Value(ActivityLog::kValueGestureTypeMove));
  EXPECT_EQ(result[ActivityLog::kKeyGestureDX],
            Json::Value(move.details.move.dx));
  EXPECT_EQ(result[ActivityLog::kKeyGestureDY],
            Json::Value(move.details.move.dy));
  EXPECT_EQ(result[ActivityLog::kKeyGestureOrdinalDX],
            Json::Value(move.details.move.ordinal_dx));
  EXPECT_EQ(result[ActivityLog::kKeyGestureOrdinalDY],
            Json::Value(move.details.move.ordinal_dy));
  log.Clear();
}

TEST(ActivityLogTest, GestureProduceTest) {
  PropRegistry prop_reg;
  ActivityLog log(&prop_reg);
  ActivityLog::Entry* entry;
  Json::Value result;

  EXPECT_EQ(0, log.size());

  // Build and log a GestureProduce structure
  Gesture scroll(kGestureScroll, 1.0, 2.0, 312, 4.0);
  log.LogGestureProduce("ActivityLogTest_GestureTest", scroll);
  ASSERT_EQ(1, log.size());
  entry = log.GetEntry(0);
  ASSERT_TRUE(std::holds_alternative<ActivityLog::GestureProduce>
                (entry->details));

  // Encode the GestureProduce into Json
  result = log.EncodeCommonInfo();
  result = result[ActivityLog::kKeyRoot][0];

  // Verify the Json information
  EXPECT_EQ(result[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyGestureProduce));
  EXPECT_EQ(result[ActivityLog::kKeyMethodName],
            Json::Value("ActivityLogTest_GestureTest"));
  EXPECT_EQ(result[ActivityLog::kKeyGestureType],
            Json::Value(ActivityLog::kValueGestureTypeScroll));
  EXPECT_EQ(result[ActivityLog::kKeyGestureDX],
            Json::Value(scroll.details.scroll.dx));
  EXPECT_EQ(result[ActivityLog::kKeyGestureDY],
            Json::Value(scroll.details.scroll.dy));
  EXPECT_EQ(result[ActivityLog::kKeyGestureOrdinalDX],
            Json::Value(scroll.details.scroll.ordinal_dx));
  EXPECT_EQ(result[ActivityLog::kKeyGestureOrdinalDY],
            Json::Value(scroll.details.scroll.ordinal_dy));
  log.Clear();
}

TEST(ActivityLogTest, HandleTimerPreTest) {
  PropRegistry prop_reg;
  ActivityLog log(&prop_reg);
  ActivityLog::Entry* entry;
  Json::Value result;
  stime_t timeout = 1;

  EXPECT_EQ(0, log.size());

  // Build and log a HandleTimerPre structure
  log.LogHandleTimerPre("ActivityLogTest_HandleTimerTest", 0, &timeout);
  EXPECT_EQ(1, log.size());
  entry = log.GetEntry(0);
  EXPECT_TRUE(std::holds_alternative<ActivityLog::HandleTimerPre>
                (entry->details));

  // Encode the HandleTimerPre into Json
  result = log.EncodeCommonInfo();
  result = result[ActivityLog::kKeyRoot][0];

  // Verify the Json information
  EXPECT_EQ(result[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyHandleTimerPre));
  EXPECT_EQ(result[ActivityLog::kKeyMethodName],
            Json::Value("ActivityLogTest_HandleTimerTest"));
  EXPECT_EQ(result[ActivityLog::kKeyTimerNow],
            Json::Value(static_cast<stime_t>(0)));
  EXPECT_EQ(result[ActivityLog::kKeyHandleTimerTimeout], Json::Value(timeout));
  log.Clear();
}

TEST(ActivityLogTest, HandleTimerPostTest) {
  PropRegistry prop_reg;
  ActivityLog log(&prop_reg);
  ActivityLog::Entry* entry;
  Json::Value result;
  stime_t timeout = 1;

  EXPECT_EQ(0, log.size());

  // Build and log a HandleTimerPost structure
  log.LogHandleTimerPost("ActivityLogTest_HandleTimerTest", 0, &timeout);
  EXPECT_EQ(1, log.size());
  entry = log.GetEntry(0);
  EXPECT_TRUE(std::holds_alternative<ActivityLog::HandleTimerPost>
                (entry->details));

  // Encode the HandleTimerPost into Json
  result = log.EncodeCommonInfo();
  result = result[ActivityLog::kKeyRoot][0];

  // Verify the Json information
  EXPECT_EQ(result[ActivityLog::kKeyType],
            Json::Value(ActivityLog::kKeyHandleTimerPost));
  EXPECT_EQ(result[ActivityLog::kKeyMethodName],
            Json::Value("ActivityLogTest_HandleTimerTest"));
  EXPECT_EQ(result[ActivityLog::kKeyTimerNow],
            Json::Value(static_cast<stime_t>(0)));
  EXPECT_EQ(result[ActivityLog::kKeyHandleTimerTimeout], Json::Value(timeout));
  log.Clear();
}

}  // namespace gestures
