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

#ifndef TOOLS_TELEMETRY_LUA_INTERPRETER_LUA_ENGINE_H_
#define TOOLS_TELEMETRY_LUA_INTERPRETER_LUA_ENGINE_H_

#include <string>
#include <vector>

#include "lua.hpp"

namespace lua_interpreter {

// Encapsulates Lua script execution environment.
// Example:
//      LuaEngine lua_engine;
//      std::vector<std::string> script_output =
//          lua_engine.ExecuteScript("print('2')")
class LuaEngine {
 public:
  // Creates a new instance of the LuaEngine.
  LuaEngine();
  ~LuaEngine();

  // Loads Lua script provided as script_body string and invokes the Lua
  // function corresponding to function_name, passing in the corresponding
  // published_data and saved_state arguments as Lua tables.
  //
  // Returns the output from executing the given script. If loading or
  // invocation are unsuccessful, the errors are returned in the output.
  std::vector<std::string> ExecuteScript(std::string script_body,
                                         std::string function_name,
                                         std::string published_data,
                                         std::string saved_state);

  // Returns the saved state stored in the Lua registry of the LuaEngine
  // instance, if any, in the form of a JSON string.
  std::string GetSavedState();

  // Returns an allocated char** pointing to null-terminated equivalents
  // of the strings within the vector passed in.
  // Returns nullptr if the vector contains no elements.
  //
  // There is no std::vector<std::string> in C, so this type must be
  // converted to a type usable by C, hence this utility function.
  static char** StringVectorToCharArray(std::vector<std::string> vector);

 private:
  // Invoked by a running Lua script to produce a log to the output. This is
  // useful for debugging.
  //
  // This method returns 0 to indicate that no results were pushed to Lua
  // stack according to Lua C function calling convention. More info:
  // https://www.lua.org/manual/5.3/manual.html#lua_CFunction Usage in lua
  // script:
  //   log("selected gear: ", g)
  static int ScriptLog(lua_State* lua);

  // Invoked by a running Lua script to store intermediate results.
  // The script will provide the results as a Lua table. The result pushed by
  // Lua is then forwarded to the Lua registry.
  //
  // The IDE supports nested fields in the table, but the actual
  // ScriptExecutor currently supports boolean, number, integer, string, and
  // their arrays. Refer to
  // packages/services/Car/packages/ScriptExecutor/src/LuaEngine.h for the
  // most up to date documentation on the supported types.
  //
  // This method returns 0 to indicate that no results were pushed to Lua
  // stack according to Lua C function calling convention. More info:
  // https://www.lua.org/manual/5.4/manual.html#lua_CFunction
  static int OnSuccess(lua_State* lua);

  // Invoked by a running Lua script to effectively mark the completion of the
  // script's lifecycle. The script will provide the final results as a Lua
  // table. The result pushed by Lua is then forwarded to the
  // output.
  //
  // The IDE supports nested fields in the table, but the actual
  // ScriptExecutor currently supports boolean, number, integer, string, and
  // their arrays. Refer to
  // packages/services/Car/packages/ScriptExecutor/src/LuaEngine.h for the
  // most up to date documentation on the supported types.
  //
  // This method returns 0 to indicate that no results were pushed to Lua
  // stack according to Lua C function calling convention. More info:
  // https://www.lua.org/manual/5.4/manual.html#lua_CFunction
  static int OnScriptFinished(lua_State* lua);

  // Invoked by a running Lua script to indicate that an error occurred. This
  // is the mechanism for a script author to receive error logs. The caller
  // script encapsulates all the information about the error that the author
  // wants to provide in a single string parameter. The error is
  // then forwarded to the output.
  //
  // This method returns 0 to indicate that no results were pushed to Lua
  // stack according to Lua C function calling convention. More info:
  // https://www.lua.org/manual/5.4/manual.html#lua_CFunction
  static int OnError(lua_State* lua);

  // Invoked by a running Lua script to produce a metrics report without
  // completing the script's lifecycle, The script will provide the
  // report as a Lua table. The report pushed by Lua is then forwarded to the
  // output.
  //
  // on_metrics_report can also be used by a running Lua script to store
  // intermediate results if the second argument is specified. The script
  // will provide the results as a Lua table. The result pushed by Lua is then
  // forwarded to the Lua registry.
  //
  // This method returns 0 to indicate that no results were pushed to
  // Lua stack according to Lua C function calling convention. More info:
  // https://www.lua.org/manual/5.4/manual.html#lua_CFunction Usage in lua
  // script:
  //   on_metrics_report(report_as_a_table)
  //   on_metrics_report(report_as_a_table, saved_state_as_a_table)
  static int OnMetricsReport(lua_State* lua);

  // Saves the saved_state to the Lua registry of the lua_State.
  static void SaveSavedStateToRegistry(lua_State* lua, std::string saved_state);

  // Clears the current saved state in the Lua registry of the lua_State.
  static void ClearSavedStateInRegistry(lua_State* lua);

  // Maintains the state of Lua.
  lua_State* lua_state_;

  // Holds the metric reports and logs from the last script execution.
  static std::vector<std::string> output_;
};

// Adds compatibility with Python.
// Since Python is written in C, the external functions must be C callable (so
// C++ types must be converted to C compatible types). These are the functions
// that can be directly called on by the Python ctypes library.
extern "C" {

// Holds information about the output of the execution.
struct LuaOutput {
  // Holds the metric reports and logs from the last script execution.
  char** output;

  // Details how many strings are within output.
  //
  // The output array doesn't have size information attached so
  // the size of the array must be encoded in the struct for iteration (or risk
  // Segmentation Faults from accessing random data).
  int size;

  // Holds the saved state of the script execution, if any,
  // in the form of a JSON string.
  char* saved_state;
};

// Frees up the memory used by the lua_output.
void FreeLuaOutput(LuaOutput* lua_output);

// Creates a new instance of the LuaEngine.
LuaEngine* NewLuaEngine();

// Loads Lua script provided as script_body string and invokes the Lua
// function corresponding to function_name, passing in the corresponding
// published_data and saved_state arguments as Lua tables.
//
// Allocates and returns the output from executing the given script in the
// form of the LuaOutput struct. If loading or invocation are unsuccessful, the
// errors are returned in the output.
LuaOutput* ExecuteScript(LuaEngine* l, char* script, char* function_name,
                         char* published_data, char* saved_state);
}  // extern "C"
}  // namespace lua_interpreter

#endif  // TOOLS_TELEMETRY_LUA_INTERPRETER_LUA_ENGINE_H_
