/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "../lua_engine.h"

#include <gtest/gtest.h>

#include <iostream>
#include <sstream>
#include <string>
#include <vector>

namespace lua_interpreter {
namespace tests {

// The fixture for testing class LuaEngine
class LuaEngineTest : public testing::Test {
 protected:
  lua_interpreter::LuaEngine lua_engine_;

  std::string ConvertVectorToString(std::vector<std::string> vector) {
    std::stringstream output;
    for (std::string s : vector) {
      output << s;
    }
    return output.str();
  }

  std::string ConvertArrayToString(char** array, int size) {
    std::stringstream output;
    for (int i = 0; i < size; i++) {
      output << array[i];
    }
    return output.str();
  }
};

TEST_F(LuaEngineTest, ExecuteScriptLogCallback) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function test(data, state) log('Logging here') end", "test", "{}", "{}");
  std::string actual = ConvertVectorToString(output);
  EXPECT_NE(actual.find("LUA: Logging here"), std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnSuccessMoreArguments) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function test(data, state) on_success({}, {}) end", "test", "{}", "{}");
  std::string actual = ConvertVectorToString(output);
  EXPECT_NE(actual.find("on_success can push only a single parameter from "
                        "Lua - a Lua table"),
            std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnSuccessNonTable) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function test(data, state) on_success('Success!') end", "test", "{}",
      "{}");
  std::string actual = ConvertVectorToString(output);
  EXPECT_NE(actual.find("on_success can push only a single parameter from "
                        "Lua - a Lua table"),
            std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnSuccessWithPopulatedTable) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function test(data, state) tbl = {}; tbl['sessionId'] = 1; "
      "on_success(tbl) end",
      "test", "{}", "{}");
  std::string saved_state = lua_engine_.GetSavedState();
  EXPECT_EQ("{\"sessionId\":1}\n", saved_state);
}

TEST_F(LuaEngineTest, ExecuteScriptOnSuccessWithEmptyTable) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function test(data, state) tbl = {}; "
      "on_success(tbl) end",
      "test", "{}", "{}");
  std::string saved_state = lua_engine_.GetSavedState();
  EXPECT_EQ("[]\n", saved_state);
}

TEST_F(LuaEngineTest, ExecuteScriptOnScriptFinishedMoreArguments) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function test(data, state) on_script_finished({}, {}) end", "test", "{}",
      "{}");
  std::string actual = ConvertVectorToString(output);
  EXPECT_NE(actual.find("on_script_finished can push only a single parameter "
                        "from Lua - a Lua "
                        "table"),
            std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnScriptFinishedNonTable) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function test(data, state) on_script_finished('Script "
      "finished') end",
      "test", "{}", "{}");
  std::string actual = ConvertVectorToString(output);
  EXPECT_NE(actual.find("on_script_finished can push only a single parameter "
                        "from Lua - a Lua "
                        "table"),
            std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnScriptFinishedWithTable) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function test(data, state) tbl = {}; tbl['sessionId'] = 1; "
      "on_script_finished(tbl) "
      "end",
      "test", "{}", "{}");
  std::string actual = ConvertVectorToString(output);
  EXPECT_NE(actual.find("{\"sessionId\":1}"), std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnErrorMoreArguments) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function test(data, state) on_error('ERROR ONE', 'ERROR "
      "TWO') end",
      "test", "{}", "{}");
  std::string actual = ConvertVectorToString(output);
  EXPECT_NE(
      actual.find("on_error can push only a single string parameter from Lua"),
      std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnErrorNonString) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function test(data, state) on_error({}) end", "test", "{}", "{}");
  std::string actual = ConvertVectorToString(output);
  EXPECT_NE(
      actual.find("on_error can push only a single string parameter from Lua"),
      std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnErrorWithSingleString) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function test(data, state) on_error('ERROR: 2') end", "test", "{}",
      "{}");
  std::string actual = ConvertVectorToString(output);
  EXPECT_NE(actual.find("ERROR: 2"), std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnMetricsReportMoreArguments) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function test(data, state) on_metrics_report({}, {}, {}) end", "test",
      "{}", "{}");
  std::string actual = ConvertVectorToString(output);
  EXPECT_NE(
      actual.find(
          "on_metrics_report should push 1 to 2 parameters of Lua table type. "
          "The first table is a metrics report and the second is an optional "
          "state to save"),
      std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnMetricsReportNonTable) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function test(data, state) on_metrics_report('Incoming "
      "metrics report') "
      "end",
      "test", "{}", "{}");
  std::string actual = ConvertVectorToString(output);
  EXPECT_NE(
      actual.find(
          "on_metrics_report should push 1 to 2 parameters of Lua table type. "
          "The first table is a metrics report and the second is an optional "
          "state to save"),
      std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnMetricsReportNonTableWithTable) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function test(data, state) on_metrics_report('Incoming "
      "metrics report', "
      "{}) end",
      "test", "{}", "{}");
  std::string actual = ConvertVectorToString(output);
  EXPECT_NE(
      actual.find(
          "on_metrics_report should push 1 to 2 parameters of Lua table type. "
          "The first table is a metrics report and the second is an optional "
          "state to save"),
      std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnMetricsReportTableWithNonTable) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function test(data, state) on_metrics_report({}, 'Saved "
      "state here') "
      "end",
      "test", "{}", "{}");
  std::string actual = ConvertVectorToString(output);
  EXPECT_NE(
      actual.find(
          "on_metrics_report should push 1 to 2 parameters of Lua table "
          "type. "
          "The first table is a metrics report and the second is an optional "
          "state to save"),
      std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnMetricsReportSingleTable) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function test(data, state) tbl = {}; tbl['sessionId'] = 1; "
      "on_metrics_report(tbl) "
      "end",
      "test", "{}", "{}");
  std::string actual = ConvertVectorToString(output);
  EXPECT_NE(actual.find("{\"sessionId\":1}"), std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptOnMetricsReportMultipleTable) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function test(data, state) tbl = {}; tbl['sessionId'] = 1; "
      "on_metrics_report(tbl, "
      "tbl) end",
      "test", "{}", "{}");
  std::string actual = ConvertVectorToString(output);
  EXPECT_EQ("{\"sessionId\":1}\n", actual);
  EXPECT_EQ("{\"sessionId\":1}\n", lua_engine_.GetSavedState());
}

TEST_F(LuaEngineTest, ExecuteScriptWithPreviousState) {
  lua_engine_.ExecuteScript(
      "function test(data, state) tbl = {}; tbl['result'] = state.data + 1; "
      "on_success(tbl) end",
      "test", "{}", "{\"data\": 1}");
  std::string saved_state = lua_engine_.GetSavedState();
  EXPECT_EQ("{\"result\":2}\n", saved_state);
}

TEST_F(LuaEngineTest, ExecuteScriptWrongFunctionName) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function test(data, state) end", "tesT", "{}", "{}");
  std::string actual = ConvertVectorToString(output);
  EXPECT_NE(actual.find("Wrong function name."), std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptSyntaxError) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function test(data, state) end f", "test", "{}", "{}");
  std::string actual = ConvertVectorToString(output);
  EXPECT_NE(actual.find("Error encountered while loading the script."),
            std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptSyntaxErrorInsideFunction) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function test(data, state) x == 1 end", "test", "{}", "{}");
  std::string actual = ConvertVectorToString(output);
  EXPECT_NE(actual.find("Error encountered while loading the script."),
            std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptRuntimeError) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function test(data, state) unknown_function(data, state) end", "test",
      "{}", "{}");
  std::string actual = ConvertVectorToString(output);
  EXPECT_NE(actual.find("Error encountered while running the script."),
            std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptRuntimeErrorWithStackTrace) {
  const char* script = "function func_2(data, state)\n"
                       "    func_3(data, state)\n"
                       "end\n"
                       "function func_1(data, state)\n"
                       "    func_2(data, state)\n"
                       "end";
  std::vector<std::string> output = lua_engine_.ExecuteScript(script, "func_1", "{}", "{}");
  std::string actual = ConvertVectorToString(output);
  EXPECT_NE(actual.find("Error encountered while running the script."), std::string::npos);
  EXPECT_NE(actual.find("func_3"), std::string::npos);
  EXPECT_NE(actual.find("func_2"), std::string::npos);
  EXPECT_NE(actual.find("func_1"), std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptInvalidPublishedData) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function test(data, state) end", "test", "invalid", "{}");
  std::string actual = ConvertVectorToString(output);
  EXPECT_NE(actual.find("Error from parsing published data"),
            std::string::npos);
}

TEST_F(LuaEngineTest, ExecuteScriptInvalidSavedState) {
  std::vector<std::string> output = lua_engine_.ExecuteScript(
      "function test(data, state) end", "test", "{}", "invalid");
  std::string actual = ConvertVectorToString(output);
  EXPECT_NE(actual.find("Error from parsing saved state"), std::string::npos);
}

TEST_F(LuaEngineTest, StringVectorToArrayEmpty) {
  std::vector<std::string> vector = {};
  char** array = LuaEngine::StringVectorToCharArray(vector);
  EXPECT_EQ(nullptr, array);
}

TEST_F(LuaEngineTest, StringVectorToArrayNonEmpty) {
  std::vector<std::string> vector = {"1", "2", "3", "4"};
  char** array = LuaEngine::StringVectorToCharArray(vector);
  EXPECT_EQ("1234", ConvertArrayToString(array, 4));
}
}  // namespace tests
}  // namespace lua_interpreter

int main(int argc, char** argv) {
  ::testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}
