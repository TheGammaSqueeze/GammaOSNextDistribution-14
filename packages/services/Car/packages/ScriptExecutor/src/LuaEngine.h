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

#ifndef PACKAGES_SCRIPTEXECUTOR_SRC_LUAENGINE_H_
#define PACKAGES_SCRIPTEXECUTOR_SRC_LUAENGINE_H_

#include "ScriptExecutorListener.h"

#include <memory>

extern "C" {
#include "lua.h"
}

namespace com {
namespace android {
namespace car {
namespace scriptexecutor {

// Encapsulates Lua script execution environment.
class LuaEngine {
public:
    LuaEngine();

    virtual ~LuaEngine();

    // Returns pointer to Lua state object.
    lua_State* getLuaState();

    // Loads Lua script provided as scriptBody string.
    // Returns 0 if successful. Otherwise returns non-zero Lua error code.
    int loadScript(const char* scriptBody);

    // Pushes a Lua function under provided name into the stack.
    // Returns 1 if successful. Otherwise, an error is sent back to the client via the callback
    // and 0 is returned.
    int pushFunction(const char* functionName);

    // Invokes function with the inputs provided in the stack.
    // Assumes that the script body has been already loaded and successfully
    // compiled and run, and all input arguments, and the function have been
    // pushed to the stack.
    // Returns 0 if successful. Otherwise returns non-zero Lua error code
    // and sends the error via a callback back to the client.
    int run();

    // Updates stored listener and destroys the previous one.
    static void resetListener(ScriptExecutorListener* listener);

private:
    // Invoked by a running Lua script to produce a log to logcat. This is useful for debugging.
    // This does not invoke ScriptExecutorListener. Scripts are expected to call one of the
    // terminating functions to end the script execution.
    // This method returns 0 to indicate that no results were pushed to Lua stack according
    // to Lua C function calling convention.
    // More info: https://www.lua.org/manual/5.3/manual.html#lua_CFunction
    // Usage in lua script:
    //   log("selected gear: ", g)
    static int scriptLog(lua_State* lua);

    // Invoked by a running Lua script to store intermediate results.
    // The script will provide the results as a Lua table.
    // We currently support only non-nested fields in the table and the fields can be the following
    // Lua types: boolean, number, integer, and string.
    // The result pushed by Lua is converted to PersistableBundle and forwarded to
    // ScriptExecutor service via callback interface.
    // This method returns 0 to indicate that no results were pushed to Lua stack according
    // to Lua C function calling convention.
    // More info: https://www.lua.org/manual/5.3/manual.html#lua_CFunction
    static int onSuccess(lua_State* lua);

    // Invoked by a running Lua script to effectively mark the completion of the script's lifecycle,
    // and send the final results to CarTelemetryService and then to the user.
    // The script will provide the final results as a Lua table.
    // We currently support only non-nested fields in the table and the fields can be the following
    // Lua types: boolean, number, integer, and string.
    // The result pushed by Lua is converted to Android PersistableBundle and forwarded to
    // ScriptExecutor service via callback interface.
    // This method returns 0 to indicate that no results were pushed to Lua stack according
    // to Lua C function calling convention.
    // More info: https://www.lua.org/manual/5.3/manual.html#lua_CFunction
    static int onScriptFinished(lua_State* lua);

    // Invoked by a running Lua script to indicate than an error occurred. This is the mechanism to
    // for a script author to receive error logs. The caller script encapsulates all the information
    // about the error that the author wants to provide in a single string parameter.
    // This method returns 0 to indicate that no results were pushed to Lua stack according
    // to Lua C function calling convention.
    // More info: https://www.lua.org/manual/5.3/manual.html#lua_CFunction
    static int onError(lua_State* lua);

    // Invoked by a running Lua script to produce a metrics report without completing
    // the script's lifecycle,
    // The metrics report will be sent to CarTelemetryService and then to the user.
    // The script will provide the report as a Lua table.
    // We currently support only non-nested fields in the table and the fields can be the following
    // Lua types: boolean, number, integer, and string.
    // The result pushed by Lua is converted to Android PersistableBundle and forwarded to
    // ScriptExecutor service via callback interface.
    // This method returns 0 to indicate that no results were pushed to Lua stack according
    // to Lua C function calling convention.
    // More info: https://www.lua.org/manual/5.3/manual.html#lua_CFunction
    // Usage in lua script:
    //   on_metrics_report(report_as_a_table)
    //   on_metrics_report(report_as_a_table, saved_state_as_a_table)
    static int onMetricsReport(lua_State* lua);

    // Points to the current listener object.
    // Lua cannot call non-static class methods. We need to access listener object instance in
    // Lua callbacks. Therefore, callbacks callable by Lua are static class methods and the pointer
    // to a listener object needs to be static, since static methods cannot access non-static
    // members.
    // Only one listener is supported at any given time.
    // Since listeners are heap-allocated, the destructor does not need to run at shutdown
    // of the service because the memory allocated to the current listener object will be
    // reclaimed by the OS.
    static ScriptExecutorListener* sListener;

    lua_State* mLuaState;  // owned
};

}  // namespace scriptexecutor
}  // namespace car
}  // namespace android
}  // namespace com

#endif  // PACKAGES_SCRIPTEXECUTOR_SRC_LUAENGINE_H_
