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

import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import com.android.car.telemetry.scriptexecutorinterface.BundleList;
import com.android.car.telemetry.scriptexecutorinterface.IScriptExecutorListener;

/**
 * An internal API provided by isolated Script Executor process
 * for executing Lua scripts in a sandboxed environment
 */
oneway interface IScriptExecutor {
  /**
   * Executes a specified function in a provided Lua script with given input arguments.
   *
   * @param scriptBody complete body of Lua script that also contains the function to be invoked
   * @param functionName the name of the function to execute
   * @param publishedData input data provided by the source which the function handles
   * @param savedState key-value pairs preserved from the previous invocation of the function
   * @param listener callback for the sandboxed environment to report back script execution results,
   * errors, and logs
   */
  void invokeScript(String scriptBody,
                    String functionName,
                    in PersistableBundle publishedData,
                    in @nullable PersistableBundle savedState,
                    in IScriptExecutorListener listener);

  /**
   * Executes a specified function in a provided Lua script with given input arguments.
   * This is a specialized version of invokeScript API above for a case when publishedData input
   * could be potentially large and overflow Binder's buffer.
   *
   * @param scriptBody complete body of Lua script that also contains the function to be invoked
   * @param functionName the name of the function to execute
   * @param publishedDataFileDescriptor file descriptor which is be used to open a pipe to read
   * large amount of input data. The input data is then handled by the provided Lua function.
   * @param savedState key-value pairs preserved from the previous invocation of the function
   * @param listener callback for the sandboxed environment to report back script execution results,
   * errors, and logs
   */
  void invokeScriptForLargeInput(String scriptBody,
                    String functionName,
                    in ParcelFileDescriptor publishedDataFileDescriptor,
                    in @nullable PersistableBundle savedState,
                    in IScriptExecutorListener listener);

  /**
   * Executes a specified function in a provided Lua script with given input arguments.
   * This is a specialized version of invokeScript API above for sending a list of bundles. The
   * data is delivered use LargeParcelable so it can handle large data sizes automatically.
   *
   * @param scriptBody complete body of Lua script that also contains the function to be invoked
   * @param functionName the name of the function to execute
   * @param bundleList the list of bundles as input data which the scripts will handle
   * @param savedState key-value pairs preserved from the previous invocation of the function
   * @param listener callback for the sandboxed environment to report back script execution results,
   * errors, and logs
   */
  void invokeScriptForBundleList(String scriptBody,
                    String functionName,
                    in BundleList bundleList,
                    in @nullable PersistableBundle savedState,
                    in IScriptExecutorListener listener);
}
