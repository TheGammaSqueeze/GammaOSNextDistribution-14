/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.common;

import android.content.Context;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.js.JSScriptEngine;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class WebViewSupportUtil {

    private static final String TAG = "WebViewSupportUtil";

    /**
     * @return instance of SupportedByConditionRule which checks if Javascript Sandbox is available
     */
    public static SupportedByConditionRule createJSSandboxAvailableRule(Context context) {
        Objects.requireNonNull(context);
        return new SupportedByConditionRule(
                "JS Sandbox is not available", () -> isJSSandboxAvailable(context));
    }

    /**
     * @return instance of SupportedByConditionRule which checks if Javascript Sandbox supports WASM
     *     for Fledge/Protected Audience
     */
    public static SupportedByConditionRule createWasmSupportAvailableRule(Context context) {
        Objects.requireNonNull(context);
        return new SupportedByConditionRule(
                "JS Sandbox does not support WASM", () -> isWasmSupportAvailable(context));
    }

    /**
     * @return a boolean to indicate if Javascript sandbox is available.
     */
    public static boolean isJSSandboxAvailable(Context context)
            throws ExecutionException, InterruptedException, TimeoutException {
        return JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable()
                && JSScriptEngine.getInstance(context, LoggerFactory.getLogger())
                        .isConfigurableHeapSizeSupported()
                        .get(2, TimeUnit.SECONDS);
    }

    /**
     * @return a boolean to indicate if Javascript sandbox supports WASM.
     */
    public static boolean isWasmSupportAvailable(Context context)
            throws ExecutionException, InterruptedException, TimeoutException {
        return JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable()
                && JSScriptEngine.getInstance(context, LoggerFactory.getLogger())
                        .isWasmSupported()
                        .get(2, TimeUnit.SECONDS);
    }
}
