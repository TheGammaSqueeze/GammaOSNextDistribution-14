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

package com.android.server.sdksandbox.verifier;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.os.Handler;
import android.os.Looper;
import android.os.OutcomeReceiver;

import com.android.server.sdksandbox.proto.Verifier.AllowedApi;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SdkDexVerifierUnitTest {

    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private final SdkDexVerifier mVerifier = SdkDexVerifier.getInstance();

    @Test
    public void getApiTokens_fullyQualifiedRule() {
        final AllowedApi apiRuleFull =
                AllowedApi.newBuilder()
                        .setClassName("Landroid/view/inputmethod/InputMethodManager")
                        .setMethodName("getCurrentInputMethodSubtype")
                        .addParameters("V")
                        .setReturnType("Landroid/view/inputmethod/InputMethodSubtype")
                        .build();
        String[] expectedKeys = {
            "Landroid",
            "view",
            "inputmethod",
            "InputMethodManager",
            "getCurrentInputMethodSubtype",
            "V",
            "Landroid/view/inputmethod/InputMethodSubtype"
        };

        String[] keys = mVerifier.getApiTokens(apiRuleFull);

        assertThat(keys).isEqualTo(expectedKeys);
    }

    @Test
    public void getApiTokens_classAndMethodRule() {
        final AllowedApi apiRuleClassMethod =
                AllowedApi.newBuilder()
                        .setClassName("Landroid/view/inputmethod/InputMethodManager")
                        .setMethodName("getCurrentInputMethodSubtype")
                        .build();
        String[] expectedKeys = {
            "Landroid",
            "view",
            "inputmethod",
            "InputMethodManager",
            "getCurrentInputMethodSubtype",
            null
        };

        String[] keys = mVerifier.getApiTokens(apiRuleClassMethod);

        assertThat(keys).isEqualTo(expectedKeys);
    }

    @Test
    public void getApiTokens_multiParam() {
        final AllowedApi apiRuleMultiParam =
                AllowedApi.newBuilder()
                        .setClassName("Landroid/view/inputmethod/InputMethodManager")
                        .setMethodName("getInputMethodListAsUser")
                        .addParameters("I") // int, according to DEX TypeDescriptor Semantics
                        .addParameters("I")
                        .setReturnType("Ljava/util/List")
                        .build();
        String[] expectedKeys = {
            "Landroid",
            "view",
            "inputmethod",
            "InputMethodManager",
            "getInputMethodListAsUser",
            "I",
            "I",
            "Ljava/util/List"
        };

        String[] keys = mVerifier.getApiTokens(apiRuleMultiParam);

        assertThat(keys).isEqualTo(expectedKeys);
    }

    @Test
    public void getApiTokens_classReturn() {
        final AllowedApi apiRuleClassReturn =
                AllowedApi.newBuilder()
                        .setClassName("Landroid/view/inputmethod/InputMethodManager")
                        .setReturnType("Ljava/util/List")
                        .build();
        String[] expectedKeys = {
            "Landroid", "view", "inputmethod", "InputMethodManager", null, "Ljava/util/List"
        };

        String[] keys = mVerifier.getApiTokens(apiRuleClassReturn);

        assertThat(keys).isEqualTo(expectedKeys);
    }

    @Test
    public void getApiTokens_classAndParams() {
        final AllowedApi apiRuleClassParam =
                AllowedApi.newBuilder()
                        .setClassName("Landroid/view/inputmethod/InputMethodManager")
                        .addParameters("V")
                        .build();
        String[] expectedKeys = {
            "Landroid", "view", "inputmethod", "InputMethodManager", null, "V", null
        };

        String[] keys = mVerifier.getApiTokens(apiRuleClassParam);

        assertThat(keys).isEqualTo(expectedKeys);
    }

    @Test
    public void startDexVerification_loadApisFails() throws Exception {
        ApiAllowlistProvider failAllowlistProvider =
                new ApiAllowlistProvider("allowlist_doesn't_exist");
        TestOutcomeReceiver callback = new TestOutcomeReceiver();
        SdkDexVerifier verifier =
                new SdkDexVerifier(
                        new SdkDexVerifier.Injector(
                                failAllowlistProvider,
                                new SerialDexLoader(new DexParserImpl(), MAIN_HANDLER)));

        verifier.startDexVerification(
                "apk_that_doesn't_get_verified", "com.test.unverified_test_app", 33, callback);

        assertThat(callback.getLastError()).isNotNull();
        assertThat(callback.getLastError().getMessage())
                .isEqualTo("allowlist_doesn't_exist not found.");
    }

    @Test
    public void startDexVerification_apkNotFound() throws Exception {
        TestOutcomeReceiver callback = new TestOutcomeReceiver();

        mVerifier.startDexVerification("bogusPath", "com.test.nonexistent_test_app", 33, callback);

        assertThat(callback.getLastError()).isNotNull();
        assertThat(callback.getLastError().getMessage())
                .isEqualTo("Apk to verify not found: bogusPath");
    }

    private static class TestOutcomeReceiver implements OutcomeReceiver<Void, Exception> {
        private CountDownLatch mLatch = new CountDownLatch(1);
        private Exception mLastError;

        public Exception getLastError() throws Exception {
            assertWithMessage("Latch timed out").that(mLatch.await(5, TimeUnit.SECONDS)).isTrue();
            return mLastError;
        }

        @Override
        public void onResult(Void result) {}

        @Override
        public void onError(Exception e) {
            mLastError = e;
            mLatch.countDown();
        }
    }
}
