/*
 * Copyright (c) 2021, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "LuaEngine.h"

#include "BundleWrapper.h"
#include "JniUtils.h"
#include "jni.h"

#include <android-base/logging.h>

#include <sstream>
#include <string>
#include <utility>
#include <vector>

extern "C" {
#include "lauxlib.h"
#include "lua.h"
#include "lualib.h"
}

namespace com {
namespace android {
namespace car {
namespace scriptexecutor {

namespace {

enum LuaNumReturnedResults {
    ZERO_RETURNED_RESULTS = 0,
};

// Prefix for logging messages coming from lua script.
const char kLuaLogTag[] = "LUA: ";

}  // namespace

ScriptExecutorListener* LuaEngine::sListener = nullptr;

LuaEngine::LuaEngine() {
    // Instantiate Lua environment
    mLuaState = luaL_newstate();
    luaL_openlibs(mLuaState);
}

LuaEngine::~LuaEngine() {
    lua_close(mLuaState);
}

lua_State* LuaEngine::getLuaState() {
    return mLuaState;
}

void LuaEngine::resetListener(ScriptExecutorListener* listener) {
    if (sListener != nullptr) {
        delete sListener;
    }
    sListener = listener;
}

int LuaEngine::loadScript(const char* scriptBody) {
    // As the first step in Lua script execution we want to load
    // the body of the script into Lua stack and have it processed by Lua
    // to catch any errors.
    // More on luaL_dostring: https://www.lua.org/manual/5.3/manual.html#lual_dostring
    // If error, pushes the error object into the stack.
    const auto status = luaL_dostring(mLuaState, scriptBody);
    if (status) {
        // Removes error object from the stack.
        // Lua stack must be properly maintained due to its limited size,
        // ~20 elements and its critical function because all interaction with
        // Lua happens via the stack.
        // Starting read about Lua stack: https://www.lua.org/pil/24.2.html
        const char* error = lua_tostring(mLuaState, -1);
        lua_pop(mLuaState, 1);
        std::ostringstream out;
        out << "Error encountered while loading the script. A possible cause could be syntax "
               "errors in the script. Error: "
            << error;
        sListener->onError(ERROR_TYPE_LUA_RUNTIME_ERROR, out.str().c_str(), "");
        return status;
    }

    // Register limited set of reserved methods for Lua to call native side.
    lua_register(mLuaState, "log", LuaEngine::scriptLog);
    lua_register(mLuaState, "on_success", LuaEngine::onSuccess);
    lua_register(mLuaState, "on_script_finished", LuaEngine::onScriptFinished);
    lua_register(mLuaState, "on_error", LuaEngine::onError);
    lua_register(mLuaState, "on_metrics_report", LuaEngine::onMetricsReport);
    return status;
}

int LuaEngine::pushFunction(const char* functionName) {
    // Interaction between native code and Lua happens via Lua stack.
    // In such model, a caller first pushes the name of the function
    // that needs to be called, followed by the function's input
    // arguments, one input value pushed at a time.
    // More info: https://www.lua.org/pil/24.2.html
    lua_getglobal(mLuaState, functionName);
    const auto status = lua_isfunction(mLuaState, /*idx= */ -1);
    if (status == 0) {
        lua_pop(mLuaState, 1);
        std::ostringstream out;
        out << "Wrong function name. Provided functionName=" << functionName
            << " does not correspond to any function in the provided script";
        sListener->onError(ERROR_TYPE_LUA_RUNTIME_ERROR, out.str().c_str(), "");
    }
    return status;
}

int LuaEngine::run() {
    // Performs blocking call of the provided Lua function. Assumes all
    // input arguments are in the Lua stack as well in proper order.
    // On how to call Lua functions: https://www.lua.org/pil/25.2.html
    // Doc on lua_pcall: https://www.lua.org/manual/5.3/manual.html#lua_pcall
    int n_args = 2;
    int n_results = 0;

    // pushes the "debug" on top of the stack, so now "debug" is at index -1
    lua_getglobal(mLuaState, "debug");

    // pushes "traceback" as debug[traceback] because "debug" is the value at given index -1
    lua_getfield(mLuaState, -1, "traceback");

    // removing value "debug" from stack as we only need debug.traceback which is at index -1
    lua_remove(mLuaState, -2);

    // We need to insert err_handler (debug.traceback) before all arguments and function.
    // Current indices (starting from top of stack): debug.traceback (-1), arg2 (-2), arg1 (-3 ==
    // -n_args-1), function (-4 == -n_args-2) After insert (starting from top of stack): arg2 (-1),
    // arg1 (-2 == -n_args), function (-3 == -n_args-1), debug.traceback (-4 == -n_args-2) so, we
    // will insert error_handler at index : (-n_args - 2)
    int err_handler_index = -n_args - 2;
    lua_insert(mLuaState, err_handler_index);

    // After lua_pcall, the function and all arguments are removed from the stack i.e. (n_args+1)
    // If there is no error then lua_pcall pushes "n_results" elements to the stack.
    // But in case of error, lua_pcall pushes exactly one element (error message).
    // So, "error message" will be at top of the stack i.e. "-1".
    // Therefore, we need to pop error_handler explicitly.
    // error_handler will be at "-2" index from top of stack after lua_pcall,
    // but once we pop error_message from top of stack, error_handler's new index will be "-1".
    int status = lua_pcall(mLuaState, n_args, n_results, err_handler_index);
    if (status) {
        const char* error = lua_tostring(mLuaState, -1);
        std::string s = error;

        std::string delimiter = "stack traceback:";
        // because actual delimiter is "\nstack traceback:\n\t"
        // tried using \n and \t as part of the delimiter , but it did not work.
        // also tried \\n and \\t in the delimiter, but it did not work.
        // so to get error_msg string, avoided the \n in front by using dpos -1
        // and for stack_traceback, avoided \n and \t in the tail by using delimiter.length() + 2.
        int dpos = s.find(delimiter);
        std::string error_msg = s.substr(0, dpos - 1);
        std::string stack_traceback = s.substr(dpos + delimiter.length() + 2);

        lua_pop(mLuaState, 2);  // pop top 2 elements (error message & error handler) from the stack
        std::ostringstream out;
        out << "Error encountered while running the script. The returned error code=" << status
            << ". Refer to lua.h file of Lua C API library for error code definitions. Error: "
            << error_msg.c_str();
        sListener->onError(ERROR_TYPE_LUA_RUNTIME_ERROR, out.str().c_str(),
                           stack_traceback.c_str());
    }
    lua_pop(mLuaState, 1);  // pop top element (error handler) from the stack.
    return status;
}

int LuaEngine::scriptLog(lua_State* lua) {
    const auto n = lua_gettop(lua);
    // Loop through each argument, Lua table indices range from [1 .. N] instead of [0 .. N-1].
    // Negative indexes are stack positions and positive indexes are argument positions.
    for (int i = 1; i <= n; i++) {
        const char* message = lua_tostring(lua, i);
        LOG(INFO) << kLuaLogTag << message;
    }
    return ZERO_RETURNED_RESULTS;
}

int LuaEngine::onSuccess(lua_State* lua) {
    // Any script we run can call on_success only with a single argument of Lua table type.
    if (lua_gettop(lua) != 1 || !lua_istable(lua, /* index =*/-1)) {
        sListener->onError(ERROR_TYPE_LUA_SCRIPT_ERROR,
                           "on_success can push only a single parameter from Lua - a Lua table",
                           "");
        return ZERO_RETURNED_RESULTS;
    }

    // Helper object to create and populate Java PersistableBundle object.
    BundleWrapper bundleWrapper(sListener->getCurrentJNIEnv());
    const auto status = convertLuaTableToBundle(sListener->getCurrentJNIEnv(), lua, &bundleWrapper);
    if (!status.ok()) {
        sListener->onError(ERROR_TYPE_LUA_SCRIPT_ERROR, status.error().message().c_str(), "");
        // We explicitly must tell Lua how many results we return, which is 0 in this case.
        // More on the topic: https://www.lua.org/manual/5.3/manual.html#lua_CFunction
        return ZERO_RETURNED_RESULTS;
    }

    // Forward the populated Bundle object to Java callback.
    sListener->onSuccess(bundleWrapper.getBundle());

    // We explicitly must tell Lua how many results we return, which is 0 in this case.
    // More on the topic: https://www.lua.org/manual/5.3/manual.html#lua_CFunction
    return ZERO_RETURNED_RESULTS;
}

int LuaEngine::onScriptFinished(lua_State* lua) {
    // Any script we run can call on_success only with a single argument of Lua table type.
    if (lua_gettop(lua) != 1 || !lua_istable(lua, /* index =*/-1)) {
        sListener->onError(ERROR_TYPE_LUA_SCRIPT_ERROR,
                           "on_script_finished can push only a single parameter from Lua - a Lua "
                           "table",
                           "");
        return ZERO_RETURNED_RESULTS;
    }

    // Helper object to create and populate Java PersistableBundle object.
    BundleWrapper bundleWrapper(sListener->getCurrentJNIEnv());
    const auto status = convertLuaTableToBundle(sListener->getCurrentJNIEnv(), lua, &bundleWrapper);
    if (!status.ok()) {
        sListener->onError(ERROR_TYPE_LUA_SCRIPT_ERROR, status.error().message().c_str(), "");
        // We explicitly must tell Lua how many results we return, which is 0 in this case.
        // More on the topic: https://www.lua.org/manual/5.3/manual.html#lua_CFunction
        return ZERO_RETURNED_RESULTS;
    }

    // Forward the populated Bundle object to Java callback.
    sListener->onScriptFinished(bundleWrapper.getBundle());

    // We explicitly must tell Lua how many results we return, which is 0 in this case.
    // More on the topic: https://www.lua.org/manual/5.3/manual.html#lua_CFunction
    return ZERO_RETURNED_RESULTS;
}

int LuaEngine::onError(lua_State* lua) {
    // Any script we run can call on_error only with a single argument of Lua string type.
    if (lua_gettop(lua) != 1 || !lua_isstring(lua, /* index = */ -1)) {
        sListener->onError(ERROR_TYPE_LUA_SCRIPT_ERROR,
                           "on_error can push only a single string parameter from Lua", "");
        return ZERO_RETURNED_RESULTS;
    }
    sListener->onError(ERROR_TYPE_LUA_SCRIPT_ERROR, lua_tostring(lua, /* index = */ -1),
                       /* stackTrace =*/"");
    return ZERO_RETURNED_RESULTS;
}

int LuaEngine::onMetricsReport(lua_State* lua) {
    // Any script we run can call on_metrics_report with at most 2 arguments of Lua table type.
    if (lua_gettop(lua) > 2 || !lua_istable(lua, /* index =*/-1)) {
        sListener->onError(ERROR_TYPE_LUA_SCRIPT_ERROR,
                           "on_metrics_report should push 1 to 2 parameters of Lua table type. "
                           "The first table is a metrics report and the second is an optional "
                           "state to save",
                           "");
        return ZERO_RETURNED_RESULTS;
    }

    // stack with 2 items:                      stack with 1 item:
    //     index -1: state_to_persist               index -1: report
    //     index -2: report
    // If the stack has 2 items, top of the stack is the state.
    // If the stack only has one item, top of the stack is the report.

    // Process the top of the stack. Create helper object and populate Java PersistableBundle
    // object.
    BundleWrapper topBundleWrapper(sListener->getCurrentJNIEnv());
    // If the helper function succeeds, it should not change the stack
    const auto status =
            convertLuaTableToBundle(sListener->getCurrentJNIEnv(), lua, &topBundleWrapper);
    if (!status.ok()) {
        sListener->onError(ERROR_TYPE_LUA_SCRIPT_ERROR, status.error().message().c_str(), "");
        // We explicitly must tell Lua how many results we return, which is 0 in this case.
        // More on the topic: https://www.lua.org/manual/5.3/manual.html#lua_CFunction
        return ZERO_RETURNED_RESULTS;
    }

    // If the script provided 1 argument, return now
    if (lua_gettop(lua) == 1) {
        sListener->onMetricsReport(topBundleWrapper.getBundle(), nullptr);
        return ZERO_RETURNED_RESULTS;
    }

    // Otherwise the script provided a report and a state
    // pop the state_to_persist because it has already been processed in topBundleWrapper
    lua_pop(lua, 1);

    // check that the second argument is also a table
    if (!lua_istable(lua, /* index =*/-1)) {
        sListener->onError(ERROR_TYPE_LUA_SCRIPT_ERROR,
                           "on_metrics_report should push 1 to 2 parameters of Lua table type. "
                           "The first table is a metrics report and the second is an optional "
                           "state to save",
                           "");
        return ZERO_RETURNED_RESULTS;
    }

    // process the report
    BundleWrapper bottomBundleWrapper(sListener->getCurrentJNIEnv());
    const auto statusBottom =
            convertLuaTableToBundle(sListener->getCurrentJNIEnv(), lua, &bottomBundleWrapper);
    if (!statusBottom.ok()) {
        sListener->onError(ERROR_TYPE_LUA_SCRIPT_ERROR, statusBottom.error().message().c_str(), "");
        // We explicitly must tell Lua how many results we return, which is 0 in this case.
        // More on the topic: https://www.lua.org/manual/5.3/manual.html#lua_CFunction
        return ZERO_RETURNED_RESULTS;
    }

    // Top of the stack = state, bottom of the stack = report
    sListener->onMetricsReport(bottomBundleWrapper.getBundle(), topBundleWrapper.getBundle());

    // We explicitly must tell Lua how many results we return, which is 0 in this case.
    // More on the topic: https://www.lua.org/manual/5.3/manual.html#lua_CFunction
    return ZERO_RETURNED_RESULTS;
}

}  // namespace scriptexecutor
}  // namespace car
}  // namespace android
}  // namespace com
