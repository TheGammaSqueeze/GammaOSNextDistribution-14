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

package com.android.adservices.service.profiling;

/** Logging constants. */
public class JSScriptEngineLogConstants {
    public static final String SANDBOX_INIT_TIME = "SANDBOX_INIT_TIME";
    public static final String ISOLATE_CREATE_TIME = "ISOLATE_CREATE_TIME";
    // JS Execution latency as measured by the calling Java process. Includes the overhead
    // of communicating with WebView.
    public static final String JAVA_EXECUTION_TIME = "JAVA_EXECUTION_TIME";
    // JS Execution latency as measured insided WebView by adding latency measures to the JS
    // scripts.
    public static final String WEBVIEW_EXECUTION_TIME = "WEBVIEW_EXECUTION_TIME";
}
