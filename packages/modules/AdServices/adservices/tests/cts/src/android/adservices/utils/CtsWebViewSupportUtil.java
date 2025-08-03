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

package android.adservices.utils;

import android.content.Context;
import android.util.Log;

import androidx.javascriptengine.JavaScriptSandbox;

import com.android.adservices.common.SupportedByConditionRule;

import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * This class is only meant to be used in CTS tests. For unit tests use
 * #com.android.adservices.common.WebViewSupportUtil.
 */
public class CtsWebViewSupportUtil {

    private static final String TAG = "CtsWebViewSupportUtil";

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
     * @return instance of SupportedByConditionRule which checks if Javascript Sandbox supports
     *     evaluate without transaction limit.
     */
    public static SupportedByConditionRule
            createEvaluateWithoutTransactionLimitAvailableForFledgeRule(Context context) {
        Objects.requireNonNull(context);
        return new SupportedByConditionRule(
                "JS Sandbox does not support evaluate without transaction limit",
                () -> isEvaluateWithoutTransactionLimitSupportAvailable(context));
    }

    /**
     * @return a boolean to indicate if Javascript sandbox is available.
     */
    public static boolean isJSSandboxAvailable(Context context) {
        return checkFeatureInJavaScriptSandbox(
                context, JavaScriptSandbox.JS_FEATURE_ISOLATE_MAX_HEAP_SIZE);
    }

    /**
     * @return a boolean to indicate if Javascript sandbox supports WASM.
     */
    public static boolean isWasmSupportAvailable(Context context) {
        return checkFeatureInJavaScriptSandbox(
                context, JavaScriptSandbox.JS_FEATURE_WASM_COMPILATION);
    }

    /**
     * @return a boolean to indicate if Javascript sandbox supports evaluate without transaction
     *     limit.
     */
    public static boolean isEvaluateWithoutTransactionLimitSupportAvailable(Context context) {
        return checkFeatureInJavaScriptSandbox(
                context, JavaScriptSandbox.JS_FEATURE_EVALUATE_WITHOUT_TRANSACTION_LIMIT);
    }

    private static boolean checkFeatureInJavaScriptSandbox(Context context, String featureName) {
        if (!JavaScriptSandbox.isSupported()) {
            return false;
        }
        try {
            JavaScriptSandbox javaScriptSandbox =
                    JavaScriptSandbox.createConnectedInstanceAsync(context)
                            .get(2, TimeUnit.SECONDS);
            boolean result = javaScriptSandbox.isFeatureSupported(featureName);
            javaScriptSandbox.close();
            return result;
        } catch (InterruptedException | ExecutionException | TimeoutException exception) {
            Log.e(TAG, "Exception while trying to create JavaScriptSandbox", exception);
        }
        return false;
    }
}
