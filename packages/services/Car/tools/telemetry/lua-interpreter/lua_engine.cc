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

#include "lua_engine.h"

#include <cstring>
#include <sstream>
#include <string>
#include <vector>

#include "lua.hpp"

namespace lua_interpreter {

std::vector<std::string> LuaEngine::output_;

// Represents the returns of the various CFunctions in the lua_engine.
// We explicitly must tell Lua how many results we return. More on the topic:
// https://www.lua.org/manual/5.4/manual.html#lua_CFunction
enum LuaNumReturnedResults {
  ZERO_RETURNED_RESULTS = 0,
};

// Prefix for logging messages coming from lua script.
const char kLuaLogTag[] = "LUA: ";

// Key for retrieving saved state from the registry.
const char* const kSavedStateKey = "saved_state";

LuaEngine::LuaEngine() {
  lua_state_ = luaL_newstate();
  luaL_openlibs(lua_state_);

  // Register limited set of reserved methods for Lua to call native side.
  lua_register(lua_state_, "log", LuaEngine::ScriptLog);
  lua_register(lua_state_, "on_success", LuaEngine::OnSuccess);
  lua_register(lua_state_, "on_script_finished", LuaEngine::OnScriptFinished);
  lua_register(lua_state_, "on_error", LuaEngine::OnError);
  lua_register(lua_state_, "on_metrics_report", LuaEngine::OnMetricsReport);
}

LuaEngine::~LuaEngine() { lua_close(lua_state_); }

// We need to insert error_handler (debug.traceback) before all arguments and function.
// Current indices (starting from top of stack):
// For example:
// debug.traceback (-1), arg2 (-2), arg1 (-3 == -n_args-1), function (-4 == -n_args-2)
// After insert (starting from top of stack): arg2 (-1), arg1 (-2 == -n_args),
// function (-3 == -n_args-1), debug.traceback (-4 == -n_args-2)
// so, we will insert error_handler at index : (-n_args - 2)
int PushLuaErrorHandler(lua_State* mLuaState, int n_args) {
  lua_getglobal(mLuaState, "debug");
  lua_getfield(mLuaState, -1, "traceback");
  lua_remove(mLuaState, -2);
  int error_handler_index = -n_args - 2;
  lua_insert(mLuaState, error_handler_index);
  return error_handler_index;
}

// Converts the published_data and saved_state JSON strings to Lua tables
// and pushes them onto the stack. If successful, the published_data table is at
// -2, and the saved_state table at -1. If unsuccessful, nothing is added to the
// stack.
// Returns true if both strings are successfully converted, appending
// any errors to output if not.
bool ConvertJsonToLuaTable(lua_State* lua_state, std::string published_data,
                           std::string saved_state,
                           std::vector<std::string>* output) {
  // After executing the file, the stack indices and its contents are:
  // -1: the json.lua table
  // the rest of the stack contents
  luaL_dofile(lua_state, "json.lua");

  // After obtaining "decode" function from the json.lua table, the stack
  // indices and its contents are:
  // -1: the decode function
  // -2: the json.lua table
  // the rest of the stack contents
  lua_getfield(lua_state, /*index=*/-1, "decode");

  // After pushing the published data argument, the stack indices and its
  // contents are:
  // -1: published_data string
  // -2: the decode function
  // -3: the json.lua table
  // the rest of the stack contents
  lua_pushstring(lua_state, published_data.c_str());

  // After this pcall, the stack indices and its contents are:
  // -1: converted published_data table
  // -2: the json.lua table
  // the rest of the stack contents
  lua_pcall(lua_state, /*nargs=*/1, /*nresults=*/1, /*msgh=*/0);

  // If the top element on the stack isn't a table, it's an error string from
  // json.lua specifiyng any issues from decoding (e.g. syntax)
  if (!lua_istable(lua_state, lua_gettop(lua_state))) {
    std::string error =
        std::string(lua_tostring(lua_state, lua_gettop(lua_state)));
    lua_pop(lua_state, 2);
    output->push_back("Error from parsing published data: " +
                      error.substr(error.find(' ')) + "\n");
    return false;
  }

  // After this insert, the stack indices and its contents are:
  // -1: the json.lua table
  // -2: converted published_data table
  // the rest of the stack contents
  lua_insert(lua_state, /*index=*/-2);

  // After obtaining "decode" function from the json.lua table, the stack
  // indices and its contents are:
  // -1: the decode function
  // -2: the json.lua table
  // -3: converted published_data table
  // the rest of the stack contents
  lua_getfield(lua_state, /*index=*/-1, "decode");

  // After pushing the saved state argument, the stack indices and its
  // contents are:
  // -1: saved_state string
  // -2: the decode function
  // -3: the json.lua table
  // -4: converted published_data table
  // the rest of the stack contents
  lua_pushstring(lua_state, saved_state.c_str());

  // After this pcall, the stack indices and its contents are:
  // -1: converted saved_state table
  // -2: the json.lua table
  // -3: converted published_data table
  // the rest of the stack contents
  lua_pcall(lua_state, /*nargs=*/1, /*nresults=*/1, /*msgh=*/0);

  // If the top element on the stack isn't a table, it's an error string from
  // json.lua specifiyng any issues from decoding (e.g. syntax)
  if (!lua_istable(lua_state, lua_gettop(lua_state))) {
    std::string error =
        std::string(lua_tostring(lua_state, lua_gettop(lua_state)));
    lua_pop(lua_state, 3);
    output->push_back("Error from parsing saved state: " +
                      error.substr(error.find(' ')) + "\n");
    return false;
  }

  // After this removal, the stack indices and its contents are:
  // -1: converted saved_state table
  // -2: converted published_data table
  // the rest of the stack contents
  lua_remove(lua_state, /*index=*/-2);

  return true;
}

void LuaEngine::SaveSavedStateToRegistry(lua_State* lua_state,
                                         std::string saved_state) {
  // After this push, the stack indices and its contents are:
  // -1: the saved state key string
  // the rest of the stack contents
  // The state is saved under the key kSavedStateKey
  lua_pushstring(lua_state, kSavedStateKey);

  // After this push, the stack indices and its contents are:
  // -1: the saved state JSON string
  // -2: the saved state key string
  // the rest of the stack contents
  lua_pushstring(lua_state, saved_state.c_str());

  // After setting the key to the value in the registry, the stack is at it's
  // original state.
  lua_settable(lua_state, LUA_REGISTRYINDEX);
}

void LuaEngine::ClearSavedStateInRegistry(lua_State* lua_state) {
  // After this push, the stack indices and its contents are:
  // -1: the saved state key string
  // the rest of the stack contents
  lua_pushstring(lua_state, kSavedStateKey);

  // After this push, the stack indices and its contents are:
  // -1: nil
  // -2: the saved state key string
  // the rest of the stack contents
  lua_pushnil(lua_state);

  // After setting the key to the value in the registry, the stack is at it's
  // original state.
  lua_settable(lua_state, LUA_REGISTRYINDEX);
}

std::vector<std::string> LuaEngine::ExecuteScript(std::string script_body,
                                                  std::string function_name,
                                                  std::string published_data,
                                                  std::string saved_state) {
  output_.clear();
  ClearSavedStateInRegistry(lua_state_);

  const int load_status = luaL_dostring(lua_state_, script_body.data());
  if (load_status != LUA_OK) {
    const char* error = lua_tostring(lua_state_, lua_gettop(lua_state_));
    lua_pop(lua_state_, lua_gettop(lua_state_));
    output_.push_back(std::string("Error encountered while loading the "
                                  "script. A possible cause could be "
                                  "syntax errors in the script. Error: ") +
                      std::string(error));
    return output_;
  }

  lua_getglobal(lua_state_, function_name.data());

  const bool function_status =
      lua_isfunction(lua_state_, lua_gettop(lua_state_));
  if (!function_status) {
    lua_pop(lua_state_, lua_gettop(lua_state_));
    output_.push_back(std::string(
        "Wrong function name. Provided function_name = " + function_name +
        " does not correspond to any function in the provided script"));
    return output_;
  }

  if (ConvertJsonToLuaTable(lua_state_, published_data, saved_state,
                            &output_)) {
    // Push an error_handler to the stack (to get the stack_trace in case of any error).
    // After this error_handler push, the stack indices and its contents are:
    // -1: converted saved_state table
    // -2: converted published_data table
    // -3: the function corresponding to the function_name in the script
    // -4: the error_handler
    // the rest of the stack contents
    int error_handler_index = PushLuaErrorHandler(lua_state_, 2);

    // After lua_pcall, the function and all arguments are removed from the stack i.e. (n_args+1)
    // If there is no error then lua_pcall pushes "n_results" elements to the stack.
    // But in case of error, lua_pcall pushes exactly one element (error message).
    // So, "error message" will be at top of the stack i.e. "-1".
    // Therefore, we need to pop error_handler explicitly.
    // error_handler will be at "-2" index from top of stack after lua_pcall,
    // but once we pop error_message from top of stack, error_handler's new index will be "-1".
    const int run_status =
            lua_pcall(lua_state_, /*nargs=*/2, /*nresults=*/0, /*msgh=*/error_handler_index);
    if (run_status != LUA_OK) {
      const char* error = lua_tostring(lua_state_, lua_gettop(lua_state_));
      lua_pop(lua_state_, lua_gettop(lua_state_));  // pop all the elements in stack.
      output_.push_back(
          std::string("Error encountered while running the script. "
                      "The returned error code = ") +
          std::to_string(run_status) +
          std::string(". Refer to lua.h file of Lua C API library "
                      "for error code definitions. Error: ") +
          std::string(error));
    }
  }
  lua_pop(lua_state_, lua_gettop(lua_state_));  // pop all the elements in stack.

  return output_;
}

char** LuaEngine::StringVectorToCharArray(std::vector<std::string> vector) {
  if (vector.size() == 0) {
    return nullptr;
  }

  char** array = new char*[vector.size()];
  for (unsigned int i = 0; i < vector.size(); i++) {
    // Size + 1 is for the null-terminating character.
    array[i] = new char[vector[i].size() + 1];
    strncpy(array[i], vector[i].c_str(), vector[i].size() + 1);
  }

  return array;
}

std::string LuaEngine::GetSavedState() {
  // After this push, the stack indices and its contents are:
  // -1: the saved state key string
  // the rest of the stack contents
  lua_pushstring(lua_state_, kSavedStateKey);

  // After obtaining the Lua value of the given key from the registry, the stack
  // indices and its contents are:
  // -1: the saved state JSON string (or nil if key is not assigned)
  // the rest of the stack contents
  lua_gettable(lua_state_, LUA_REGISTRYINDEX);

  if (lua_isnil(lua_state_, lua_gettop(lua_state_))) {
    // After popping the nil value from the stack, the stack is at it's
    // original state.
    lua_pop(lua_state_, 1);
    return std::string();
  }

  const auto saved_state = lua_tostring(lua_state_, lua_gettop(lua_state_));
  // After popping the saved state JSON string from the stack, the stack is at
  // it's original state.
  lua_pop(lua_state_, 1);

  return saved_state;
}

// Converts the Lua table at the top of the stack to a JSON string.
// This function removes the table from the stack.
std::string ConvertTableToJson(lua_State* lua_state) {
  // We re-insert the various items on the stack to keep the table
  // on top (since lua function arguments must be at the top of the stack above
  // the function itself).

  // After executing the file, the stack indices and its contents are:
  // -1: the json.lua table
  // -2: the pre-converted table
  // the rest of the stack contents
  luaL_dofile(lua_state, "json.lua");

  // After this insert, the stack indices and its contents are:
  // -1: the pre-converted table
  // -2: the json.lua table
  // the rest of the stack contents
  lua_insert(lua_state, /*index=*/-2);

  // After obtaining the "encode" function from the json.lua table, the stack
  // indices and its contents are:
  // -1: the encode function
  // -2: the pre-converted table
  // -3: the json.lua table
  // the rest of the stack contents
  lua_getfield(lua_state, /*index=*/-2, "encode");

  // After this insert, the stack indices and its contents are:
  // -1: the pre-converted table
  // -2: the encode function
  // -3: the json.lua table
  // the rest of the stack contents
  lua_insert(lua_state, /*index=*/-2);

  // After this pcall, the stack indices and its contents are:
  // -1: the converted JSON string / json.lua error if encoding fails
  // -2: the json.lua table
  // the rest of the stack contents
  lua_pcall(lua_state, /*nargs=*/1, /*nresults=*/1, /*msgh=*/0);
  std::string json = lua_tostring(lua_state, lua_gettop(lua_state));

  // After this pop, the stack contents are reverted back to it's original state
  // without the pre-converted table that was previously at the top.
  lua_pop(lua_state, /*num=*/2);

  return json + "\n";
}

int LuaEngine::ScriptLog(lua_State* lua_state) {
  std::stringstream log;

  for (int i = 1; i <= lua_gettop(lua_state); i++) {
    // NIL lua type cannot be coerced to string so must be explicitly checked to
    // prevent errors.
    if (!lua_isstring(lua_state, i)) {
      output_.push_back(
          std::string(kLuaLogTag) +
          "One of the log arguments cannot be coerced to a string; make "
          "sure that this value exists\n");
      return ZERO_RETURNED_RESULTS;
    }
    log << lua_tostring(lua_state, i);
  }

  output_.push_back(kLuaLogTag + log.str() + "\n");
  return ZERO_RETURNED_RESULTS;
}

int LuaEngine::OnSuccess(lua_State* lua_state) {
  // Any script we run can call on_success only with a single argument of Lua
  // table type.
  if (lua_gettop(lua_state) != 1 ||
      !lua_istable(lua_state, lua_gettop(lua_state))) {
    output_.push_back(
        "on_success can push only a single parameter from Lua - a Lua table\n");
    return ZERO_RETURNED_RESULTS;
  }

  SaveSavedStateToRegistry(lua_state, ConvertTableToJson(lua_state));

  return ZERO_RETURNED_RESULTS;
}

int LuaEngine::OnScriptFinished(lua_State* lua_state) {
  // Any script we run can call on_script_finished only with a single argument
  // of Lua table type.
  if (lua_gettop(lua_state) != 1 ||
      !lua_istable(lua_state, lua_gettop(lua_state))) {
    output_.push_back(
        "on_script_finished can push only a single parameter from Lua - a Lua "
        "table\n");
    return ZERO_RETURNED_RESULTS;
  }

  output_.push_back(ConvertTableToJson(lua_state));

  return ZERO_RETURNED_RESULTS;
}

int LuaEngine::OnError(lua_State* lua_state) {
  // Any script we run can call on_error only with a single argument of Lua
  // string type.
  if (lua_gettop(lua_state) != 1 ||
      !lua_isstring(lua_state, lua_gettop(lua_state))) {
    output_.push_back(
        "on_error can push only a single string parameter from Lua\n");
    return ZERO_RETURNED_RESULTS;
  }

  std::string error = lua_tostring(lua_state, lua_gettop(lua_state));
  output_.push_back(error + "\n");

  return ZERO_RETURNED_RESULTS;
}

int LuaEngine::OnMetricsReport(lua_State* lua_state) {
  // Any script we run can call on_metrics_report with at most 2 arguments of
  // Lua table type.
  if (lua_gettop(lua_state) > 2 ||
      !lua_istable(lua_state, lua_gettop(lua_state))) {
    output_.push_back(
        "on_metrics_report should push 1 to 2 parameters of Lua table type. "
        "The first table is a metrics report and the second is an optional "
        "state to save\n");
    return ZERO_RETURNED_RESULTS;
  }

  const auto first_table = ConvertTableToJson(lua_state);

  // If the script provided 1 argument, return now.
  // gettop would be zero since the single argument is popped off the stack
  // from ConvertTableToJson.
  if (lua_gettop(lua_state) == 0) {
    output_.push_back(first_table);
    return ZERO_RETURNED_RESULTS;
  }

  if (!lua_istable(lua_state, lua_gettop(lua_state))) {
    output_.push_back(
        "on_metrics_report should push 1 to 2 parameters of Lua table type. "
        "The first table is a metrics report and the second is an optional "
        "state to save\n");
    return ZERO_RETURNED_RESULTS;
  }

  const auto report = ConvertTableToJson(lua_state);
  output_.push_back(report);

  // If there's two tables, at index -1 would be the saved state table (since
  // it's the second argument for on_metrics_report), so first_table is the
  // saved state.
  SaveSavedStateToRegistry(lua_state, first_table);

  return ZERO_RETURNED_RESULTS;
}

extern "C" {
void FreeLuaOutput(LuaOutput* lua_output) {
  for (int i = 0; i < lua_output->size; i++) {
    delete[] lua_output->output[i];
  }
  delete[] lua_output->output;
  delete[] lua_output->saved_state;
  delete lua_output;
}

LuaEngine* NewLuaEngine() { return new LuaEngine(); }

LuaOutput* ExecuteScript(LuaEngine* l, char* script, char* function_name,
                         char* published_data, char* saved_state) {
  LuaOutput* lua_engine_output = new LuaOutput();
  std::vector<std::string> script_execution_output =
      l->ExecuteScript(script, function_name, published_data, saved_state);
  lua_engine_output->size = script_execution_output.size();
  lua_engine_output->output =
      LuaEngine::StringVectorToCharArray(script_execution_output);

  std::string new_saved_state = l->GetSavedState();
  // Size + 1 is for the null-terminating character which is included in
  // c_str().
  lua_engine_output->saved_state = new char[new_saved_state.size() + 1];
  strncpy(lua_engine_output->saved_state, new_saved_state.c_str(),
          new_saved_state.size() + 1);
  return lua_engine_output;
}
}  // extern "C"
}  // namespace lua_interpreter
