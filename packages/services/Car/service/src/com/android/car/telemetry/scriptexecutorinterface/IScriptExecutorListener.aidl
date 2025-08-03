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

package com.android.car.telemetry.scriptexecutorinterface;

import android.os.PersistableBundle;

/**
 * Listener for {@code IScriptExecutor#invokeScript}.
 *
 * An invocation of a script by Script Executor will result in a call of only one
 * of the three methods below. If a script fully completes its objective, onScriptFinished
 * is called. If a script's invocation completes normally, onSuccess is called.
 * onError is called if any error happens before or during script execution and we
 * should abandon this run of the script.
 */
interface IScriptExecutorListener {
  /**
   * Called by ScriptExecutor when the script declares itself as "finished".
   *
   * @param result final results of the script that will be uploaded.
   */
  void onScriptFinished(in PersistableBundle result);

  /**
   * Called by ScriptExecutor when a function completes successfully and also provides
   * optional state that the script wants CarTelemetryService to persist.
   *
   * @param stateToPersist key-value pairs to persist
   */
  void onSuccess(in @nullable PersistableBundle stateToPersist);

  /**
   * Called by ScriptExecutor to report errors that prevented the script
   * from running or completing execution successfully.
   *
   * @param errorType type of the error message as defined in this aidl file.
   * @param messsage the human-readable message containing information helpful for analysis or debugging.
   * @param stackTrace the stack trace of the error if available.
   */
  void onError(int errorType, String message, @nullable String stackTrace);

  /**
   * Called by ScriptExecutor when a function completes successfully and produces a
   * metrics report. The script is not "finished" yet.
   * It can also provide optional state that the script wants CarTelemetryService to persist.
   *
   * @param report metrics report that will be uploaded.
   * @param stateToPersist key-value pairs to persist
   */
  void onMetricsReport(
          in PersistableBundle report,
          in @nullable PersistableBundle stateToPersist);

  /**
   * Any changes to the following ERROR_TYPE_* constants must be also reflected in the following files:
   * p/s/C/packages/ScriptExecutor/src/ScriptExecutorListener.h
   * p/s/C/car-lib/src/android/car/telemetry/telemetry.proto
   */

  /**
   * Default error type.
   */
  const int ERROR_TYPE_UNSPECIFIED = 0;

  /**
   * Used when an error occurs in the ScriptExecutor code.
   */
  const int ERROR_TYPE_SCRIPT_EXECUTOR_ERROR = 1;

  /**
   * Used when an error occurs while executing the Lua script (such as
   * errors returned by lua_pcall)
   */
  const int ERROR_TYPE_LUA_RUNTIME_ERROR = 2;

  /**
   * Used to log errors by a script itself, for instance, when a script received
   * inputs outside of expected range.
   */
  const int ERROR_TYPE_LUA_SCRIPT_ERROR = 3;

  /**
   * Used to log errors due to publisher failure.
   */
  const int ERROR_TYPE_PUBLISHER_FAILED = 4;
}

