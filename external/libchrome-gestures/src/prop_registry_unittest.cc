// Copyright 2011 The ChromiumOS Authors
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

#include <string>

#include <gtest/gtest.h>

#include "include/activity_log.h"
#include "include/prop_registry.h"

using std::string;

// Mock struct GesturesProp implementation (outside of namespace gestures)
struct GesturesProp { };

namespace gestures {

class PropRegistryTest : public ::testing::Test {};

class PropRegistryTestDelegate : public PropertyDelegate {
 public:
  PropRegistryTestDelegate() : call_cnt_(0) {}
  virtual void BoolWasWritten(BoolProperty* prop) { call_cnt_++; };
  virtual void BoolArrayWasWritten(BoolArrayProperty* prop) { call_cnt_++; };
  virtual void DoubleWasWritten(DoubleProperty* prop) { call_cnt_++; };
  virtual void DoubleArrayWasWritten(DoubleArrayProperty* prop) {
    call_cnt_++;
  };
  virtual void IntWasWritten(IntProperty* prop) { call_cnt_++; };
  virtual void IntArrayWasWritten(IntArrayProperty* prop) { call_cnt_++; };
  virtual void StringWasWritten(StringProperty* prop) { call_cnt_++; };

  int call_cnt_;
};

namespace {
string ValueForProperty(const Property& prop) {
  Json::Value temp(Json::objectValue);
  temp["tempkey"] = prop.NewValue();
  return temp.toStyledString();
}

}  // namespace {}

TEST(PropRegistryTest, SimpleTest) {
  PropRegistry reg;
  PropRegistryTestDelegate delegate;

  int expected_call_cnt = 0;
  BoolProperty bp1(&reg, "hi", false);
  bp1.SetDelegate(&delegate);
  EXPECT_TRUE(strstr(ValueForProperty(bp1).c_str(), "false"));
  bp1.HandleGesturesPropWritten();
  EXPECT_EQ(++expected_call_cnt, delegate.call_cnt_);

  BoolProperty bp2(&reg, "hi", true);
  EXPECT_TRUE(strstr(ValueForProperty(bp2).c_str(), "true"));
  bp2.HandleGesturesPropWritten();
  EXPECT_EQ(expected_call_cnt, delegate.call_cnt_);

  DoubleProperty dp1(&reg, "hi", 2721.0);
  dp1.SetDelegate(&delegate);
  EXPECT_TRUE(strstr(ValueForProperty(dp1).c_str(), "2721"));
  dp1.HandleGesturesPropWritten();
  EXPECT_EQ(++expected_call_cnt, delegate.call_cnt_);

  DoubleProperty dp2(&reg, "hi", 3.1);
  EXPECT_TRUE(strstr(ValueForProperty(dp2).c_str(), "3.1"));
  dp2.HandleGesturesPropWritten();
  EXPECT_EQ(expected_call_cnt, delegate.call_cnt_);

  IntProperty ip1(&reg, "hi", 567);
  ip1.SetDelegate(&delegate);
  EXPECT_TRUE(strstr(ValueForProperty(ip1).c_str(), "567"));
  ip1.HandleGesturesPropWritten();
  EXPECT_EQ(++expected_call_cnt, delegate.call_cnt_);

  IntProperty ip2(&reg, "hi", 568);
  EXPECT_TRUE(strstr(ValueForProperty(ip2).c_str(), "568"));
  ip2.HandleGesturesPropWritten();
  EXPECT_EQ(expected_call_cnt, delegate.call_cnt_);

  StringProperty stp1(&reg, "hi", "foo");
  stp1.SetDelegate(&delegate);
  EXPECT_TRUE(strstr(ValueForProperty(stp1).c_str(), "foo"));
  stp1.HandleGesturesPropWritten();
  EXPECT_EQ(++expected_call_cnt, delegate.call_cnt_);

  StringProperty stp2(&reg, "hi", "bar");
  EXPECT_TRUE(strstr(ValueForProperty(stp2).c_str(), "bar"));
  stp2.HandleGesturesPropWritten();
  EXPECT_EQ(expected_call_cnt, delegate.call_cnt_);


  Json::Value my_bool_val = bp1.NewValue();
  Json::Value my_int_val = ip1.NewValue();
  Json::Value my_double_val = dp1.NewValue();
  Json::Value my_str_val = stp1.NewValue();
  EXPECT_TRUE(bp1.SetValue(my_bool_val));
  EXPECT_FALSE(bp1.SetValue(my_int_val));
  EXPECT_FALSE(bp1.SetValue(my_double_val));
  EXPECT_FALSE(bp1.SetValue(my_str_val));

  EXPECT_FALSE(ip1.SetValue(my_bool_val));
  EXPECT_TRUE(ip1.SetValue(my_int_val));
  EXPECT_FALSE(ip1.SetValue(my_double_val));
  EXPECT_FALSE(ip1.SetValue(my_str_val));

  EXPECT_FALSE(dp1.SetValue(my_bool_val));
  EXPECT_TRUE(dp1.SetValue(my_int_val));
  EXPECT_TRUE(dp1.SetValue(my_double_val));
  EXPECT_FALSE(dp1.SetValue(my_str_val));

  EXPECT_FALSE(stp1.SetValue(my_bool_val));
  EXPECT_FALSE(stp1.SetValue(my_int_val));
  EXPECT_FALSE(stp1.SetValue(my_double_val));
  EXPECT_TRUE(stp1.SetValue(my_str_val));

  // This code does not do anything but the code coverage
  // hits for not covered areas due to not running the
  // unused default virtual functions.
  PropertyDelegate pd;

  pd.BoolWasWritten(&bp1);
  pd.DoubleWasWritten(&dp1);
  pd.IntWasWritten(&ip1);
  pd.StringWasWritten(&stp1);
}

TEST(PropRegistryTest, PropChangeTest) {
  PropRegistry reg;
  ActivityLog log(&reg);
  reg.set_activity_log(&log);

  DoubleProperty dp(&reg, "hi", 1234.0);
  EXPECT_EQ(0, log.size());
  dp.HandleGesturesPropWritten();
  EXPECT_EQ(1, log.size());

  BoolProperty bp(&reg, "there", true);
  EXPECT_EQ(1, log.size());
  bp.HandleGesturesPropWritten();
  EXPECT_EQ(2, log.size());
}

// Mock GesturesPropProvider
GesturesProp* MockGesturesPropCreateBool(void* data, const char* name,
                                         GesturesPropBool* loc,
                                         size_t count,
                                         const GesturesPropBool* init) {
  GesturesProp *dummy = new GesturesProp();
  *loc = true;
  return dummy;
}

GesturesProp* MockGesturesPropCreateInt(void* data, const char* name,
                                        int* loc, size_t count,
                                        const int* init) {
  GesturesProp *dummy = new GesturesProp();
  *loc = 1;
  return dummy;
}

GesturesProp* MockGesturesPropCreateReal(void* data, const char* name,
                                         double* loc, size_t count,
                                         const double* init) {
  GesturesProp *dummy = new GesturesProp();
  *loc = 1.0;
  return dummy;
}

GesturesProp* MockGesturesPropCreateString(void* data, const char* name,
                                           const char** loc,
                                           const char* const init) {
  GesturesProp *dummy = new GesturesProp();
  *loc = "1";
  return dummy;
}

void MockGesturesPropRegisterHandlers(void* data, GesturesProp* prop,
                                      void* handler_data,
                                      GesturesPropGetHandler getter,
                                      GesturesPropSetHandler setter) {}

void MockGesturesPropFree(void* data, GesturesProp* prop) {
  delete prop;
}

// This tests that if we create a prop, then set the prop provider, and the
// prop provider changes the value at that time, that we notify the prop
// delegate that the value was changed.
TEST(PropRegistryTest, SetAtCreateShouldNotifyTest) {
  GesturesPropProvider mock_gestures_props_provider = {
    MockGesturesPropCreateInt,
    nullptr,
    MockGesturesPropCreateBool,
    MockGesturesPropCreateString,
    MockGesturesPropCreateReal,
    MockGesturesPropRegisterHandlers,
    MockGesturesPropFree
  };

  PropRegistry reg;
  PropRegistryTestDelegate delegate;
  BoolProperty my_bool(&reg, "MyBool", 0);
  my_bool.SetDelegate(&delegate);
  DoubleProperty my_double(&reg, "MyDouble", 0.0);
  my_double.SetDelegate(&delegate);
  IntProperty my_int(&reg, "MyInt", 0);
  my_int.SetDelegate(&delegate);
  IntProperty my_int_no_change(&reg, "MyIntNoChange", 1);
  my_int_no_change.SetDelegate(&delegate);
  StringProperty my_string(&reg, "MyString", "mine");
  my_string.SetDelegate(&delegate);

  EXPECT_EQ(0, delegate.call_cnt_);
  reg.SetPropProvider(&mock_gestures_props_provider, nullptr);
  EXPECT_EQ(4, delegate.call_cnt_);
}

TEST(PropRegistryTest, DoublePromoteIntTest) {
  PropRegistry reg;
  PropRegistryTestDelegate delegate;

  DoubleProperty my_double(&reg, "MyDouble", 1234.5);
  my_double.SetDelegate(&delegate);
  EXPECT_TRUE(strstr(ValueForProperty(my_double).c_str(), "1234.5"));
  IntProperty my_int(&reg, "MyInt", 321);
  my_int.SetDelegate(&delegate);
  Json::Value my_int_val = my_int.NewValue();
  EXPECT_TRUE(my_double.SetValue(my_int_val));
  EXPECT_TRUE(strstr(ValueForProperty(my_double).c_str(), "321"));
}

TEST(PropRegistryTest, BoolArrayTest) {
  PropRegistry reg;
  PropRegistry reg_with_delegate;
  PropRegistryTestDelegate delegate;

  GesturesPropBool vals[] = { false, true };
  BoolArrayProperty my_bool_array_w_delegate(
    &reg, "MyBoolArray", vals, 2);
  my_bool_array_w_delegate.SetDelegate(&delegate);
  EXPECT_EQ(0, delegate.call_cnt_);
  my_bool_array_w_delegate.HandleGesturesPropWritten();
  EXPECT_EQ(1, delegate.call_cnt_);
  delegate.BoolArrayWasWritten(&my_bool_array_w_delegate);
  EXPECT_EQ(2, delegate.call_cnt_);

  IntProperty ip1(&reg, "hi", 567);
  ip1.SetDelegate(&delegate);
  StringProperty stp1(&reg, "hi", "foo");
  stp1.SetDelegate(&delegate);
  Json::Value my_bool_array_val = my_bool_array_w_delegate.NewValue();
  Json::Value my_int_val = ip1.NewValue();
  Json::Value my_str_val = stp1.NewValue();
  EXPECT_FALSE(my_bool_array_w_delegate.SetValue(my_int_val));
  EXPECT_FALSE(my_bool_array_w_delegate.SetValue(my_str_val));
  EXPECT_TRUE(my_bool_array_w_delegate.SetValue(my_bool_array_val));

  // This code does not do anything but the code coverage
  // hits for not covered areas due to not running the
  // unused default virtual functions.
  PropertyDelegate pd;

  BoolArrayProperty my_bool_array(&reg, "MyBoolArray", vals, 2);
  pd.BoolArrayWasWritten(&my_bool_array);
}

TEST(PropRegistryTest, DoubleArrayTest) {
  PropRegistry reg;
  PropRegistry reg_with_delegate;
  PropRegistryTestDelegate delegate;

  double vals[] = { 0.0, 1.0 };
  DoubleArrayProperty my_double_array_w_delegate(
    &reg, "MyDoubleArray", vals, 2);
  my_double_array_w_delegate.SetDelegate(&delegate);
  EXPECT_EQ(0, delegate.call_cnt_);
  my_double_array_w_delegate.HandleGesturesPropWritten();
  EXPECT_EQ(1, delegate.call_cnt_);
  delegate.DoubleArrayWasWritten(&my_double_array_w_delegate);
  EXPECT_EQ(2, delegate.call_cnt_);

  IntProperty ip1(&reg, "hi", 567);
  ip1.SetDelegate(&delegate);
  StringProperty stp1(&reg, "hi", "foo");
  stp1.SetDelegate(&delegate);
  Json::Value my_double_array_val = my_double_array_w_delegate.NewValue();
  Json::Value my_int_val = ip1.NewValue();
  Json::Value my_str_val = stp1.NewValue();
  EXPECT_FALSE(my_double_array_w_delegate.SetValue(my_int_val));
  EXPECT_FALSE(my_double_array_w_delegate.SetValue(my_str_val));
  EXPECT_TRUE(my_double_array_w_delegate.SetValue(my_double_array_val));

  // This code does not do anything but the code coverage
  // hits for not covered areas due to not running the
  // unused default virtual functions.
  PropertyDelegate pd;

  DoubleArrayProperty my_double_array(&reg, "MyDoubleArray", vals, 2);
  pd.DoubleArrayWasWritten(&my_double_array);
}

TEST(PropRegistryTest, IntArrayTest) {
  PropRegistry reg;
  PropRegistry reg_with_delegate;
  PropRegistryTestDelegate delegate;

  int vals[] = { 0, 1 };
  IntArrayProperty my_int_array_w_delegate(
    &reg, "MyIntArray", vals, 2);
  my_int_array_w_delegate.SetDelegate(&delegate);
  EXPECT_EQ(0, delegate.call_cnt_);
  my_int_array_w_delegate.HandleGesturesPropWritten();
  EXPECT_EQ(1, delegate.call_cnt_);
  delegate.IntArrayWasWritten(&my_int_array_w_delegate);
  EXPECT_EQ(2, delegate.call_cnt_);

  IntProperty ip1(&reg, "hi", 567);
  ip1.SetDelegate(&delegate);
  StringProperty stp1(&reg, "hi", "foo");
  stp1.SetDelegate(&delegate);
  Json::Value my_int_array_val = my_int_array_w_delegate.NewValue();
  Json::Value my_int_val = ip1.NewValue();
  Json::Value my_str_val = stp1.NewValue();
  EXPECT_FALSE(my_int_array_w_delegate.SetValue(my_int_val));
  EXPECT_FALSE(my_int_array_w_delegate.SetValue(my_str_val));
  EXPECT_TRUE(my_int_array_w_delegate.SetValue(my_int_array_val));

  // This code does not do anything but the code coverage
  // hits for not covered areas due to not running the
  // unused default virtual functions.
  PropertyDelegate pd;

  IntArrayProperty my_int_array(&reg, "MyIntArray", vals, 2);
  pd.IntArrayWasWritten(&my_int_array);
}

}  // namespace gestures
