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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.OutcomeReceiver;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.sdksandbox.proto.Verifier.AllowedApi;
import com.android.server.sdksandbox.proto.Verifier.AllowedApisList;
import com.android.server.sdksandbox.verifier.SerialDexLoader.DexLoadResult;
import com.android.server.sdksandbox.verifier.SerialDexLoader.VerificationHandler;

import com.google.protobuf.InvalidProtocolBufferException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies the SDK being installed against the APIs allowlist. Verification runs on DEX files of
 * the SDK package.
 *
 * @hide
 */
public class SdkDexVerifier {

    private final Object mPlatformApiAllowlistsLock = new Object();
    private final Object mVerificationLock = new Object();

    private static final String TAG = "SdkSandboxVerifier";

    private static final String WILDCARD = "*";
    private static final String EMPTY_STRING = "";

    private static final AllowedApi[] DEFAULT_RULES = {
        AllowedApi.newBuilder().setClassName("Landroid/*").setAllow(false).build(),
        AllowedApi.newBuilder().setClassName("Ljava/*").setAllow(false).build(),
        AllowedApi.newBuilder().setClassName("Lcom/google/android/*").setAllow(false).build(),
        AllowedApi.newBuilder().setClassName("Lcom/android/*").setAllow(false).build(),
        AllowedApi.newBuilder().setClassName("Landroidx/*").setAllow(false).build(),
    };

    private static SdkDexVerifier sSdkDexVerifier;
    private ApiAllowlistProvider mApiAllowlistProvider;
    private SerialDexLoader mDexLoader;

    // Maps targetSdkVersion to its allowlist
    @GuardedBy("mPlatformApiAllowlistsLock")
    private Map<Long, AllowedApisList> mPlatformApiAllowlists;

    @GuardedBy("mPlatformApiAllowlistsLock")
    private Map<Long, StringTrie> mPlatformApiAllowTries = new HashMap<>();

    @GuardedBy("mVerificationLock")
    private Map<String, Long> mVerificationTimes = new HashMap<>();

    /** Returns a singleton instance of {@link SdkDexVerifier} */
    @NonNull
    public static SdkDexVerifier getInstance() {
        synchronized (SdkDexVerifier.class) {
            if (sSdkDexVerifier == null) {
                sSdkDexVerifier = new SdkDexVerifier(new Injector());
            }
        }
        return sSdkDexVerifier;
    }

    @VisibleForTesting
    SdkDexVerifier(Injector injector) {
        mApiAllowlistProvider = injector.getApiAllowlistProvider();
        mDexLoader = injector.getDexLoader();
    }

    /**
     * Starts verification of the requested sdk
     *
     * @param sdkPath path to the sdk package to be verified
     * @param targetSdkVersion Android SDK target version of the package being verified, declared in
     *     the package manifest.
     * @param callback to client for communication of parsing/verification results.
     */
    public void startDexVerification(
            String sdkPath,
            String packagename,
            long targetSdkVersion,
            OutcomeReceiver<Void, Exception> callback) {
        long startTime = SystemClock.elapsedRealtime();
        synchronized (mVerificationLock) {
            mVerificationTimes.put(packagename, startTime);
        }

        try {
            initAllowlist(targetSdkVersion);
        } catch (Exception e) {
            callback.onError(e);
            return;
        }

        File sdkPathFile = new File(sdkPath);

        if (!sdkPathFile.exists()) {
            callback.onError(new FileNotFoundException("Apk to verify not found: " + sdkPath));
            return;
        }

        mDexLoader.queueApkToLoad(
                sdkPath,
                packagename,
                new VerificationHandler() {
                    @Override
                    public boolean verify(DexLoadResult result) {
                        // TODO(b/231441674): verify against allowtrie.
                        return true;
                    }

                    @Override
                    public void verificationFinishedForPackage(boolean passed) {
                        if (passed) {
                            Log.d(TAG, packagename + " verified.");
                        } else {
                            Log.d(TAG, packagename + " rejected");
                        }
                        synchronized (mVerificationLock) {
                            long verificationTime =
                                    SystemClock.elapsedRealtime()
                                            - mVerificationTimes.remove(packagename);
                            Log.d(TAG, "Verification time (ms): " + verificationTime);
                        }

                        // TODO(b/231441674): cache and log verification result
                    }
                });
    }

    /*
     * Converts an AllowedApi object into an array of keys that will be added to the verification
     * trie. The AllowedApi rules should follow TypeDescriptors semantics from the DEX format.
     *
     * The list of tokens is generated splitting the class name at '/' and adding the
     * subsequent fields to be matched for equality or wildcard. The parameters list specifies
     * all of the parameter types, its order is preserved from the method signature in
     * the source file and there is no distinction between input and return parameters.
     * Therefore, the order of the parameters in the rules matters when computing a rule match.
     * Omitted fields will add a wildcard to match all possibilities.
     *
     * A fully qualified rule will match an exact method, an example:
     * {
     *      allow : false
     *      class_name : "Landroid/view/inputmethod/InputMethodManager"
     *      method_name : "getCurrentInputMethodSubtype"
     *      parameters: ["V"]
     *      return_type: "Landroid/view/inputmethod/InputMethodSubtype"
     * }
     * This rule will produce the list of tokens:
     * ["Landroid", "view", "inputmethod", "InputMethodManager", "getCurrentInputMethodSubtype",
     *      "V", "Landroid/view/inputmethod/InputMethodSubtype"]
     *
     * A generalized rule, that matches all methods in the InputMethodManager class that
     * return an InputMethodSubtype object, will look like this:
     * {
     *      allow : false
     *      class_name : "Landroid/view/inputmethod/InputMethodManager"
     *      return_type: "Landroid/view/inputmethod/InputMethodSubtype"
     * }
     * This rule produces the list of tokens:
     * ["Landroid", "view", "inputmethod", "InputMethodManager",
     *      null, "Landroid/view/inputmethod/InputMethodSubtype"]
     *
     * Wildcards can be included in the class name to generalize to packages, for example:
     * "Landroid/view/inputmethod/*" matches all classes within the android.view.inputmethod.
     */
    @VisibleForTesting
    @Nullable
    String[] getApiTokens(AllowedApi apiRule) {
        ArrayList<String> tokens = new ArrayList<>();

        if (apiRule.getClassName().equals(EMPTY_STRING)) {
            // match unspecified class name
            tokens.add(WILDCARD);
        } else {
            List<String> classTokens = Arrays.asList(apiRule.getClassName().toString().split("/"));
            tokens.addAll(classTokens);
        }

        if (!apiRule.getMethodName().equals(EMPTY_STRING)) {
            tokens.add(apiRule.getMethodName());
        } else if (!WILDCARD.equals(tokens.get(tokens.size() - 1))) {
            // match unspecified method name
            tokens.add(WILDCARD);
        }

        if (apiRule.getParametersCount() != 0) {
            tokens.addAll(apiRule.getParametersList());
        } else if (!WILDCARD.equals(tokens.get(tokens.size() - 1))) {
            // match unspecified params
            tokens.add(WILDCARD);
        }

        if (!apiRule.getReturnType().equals(EMPTY_STRING)) {
            tokens.add(apiRule.getReturnType());
        } else if (!WILDCARD.equals(tokens.get(tokens.size() - 1))) {
            // match unspecified return type
            tokens.add(WILDCARD);
        }

        // To catch a malformed rule like "Landroid//com"
        if (tokens.contains(EMPTY_STRING)) {
            return null;
        }

        tokens.replaceAll(token -> token.equals(WILDCARD) ? null : token);

        return tokens.toArray(new String[tokens.size()]);
    }

    /**
     * Initializes the allowlist for a given target sandbox sdk version
     *
     * @param targetSdkVersion declared in the manifest of the installed package, different from
     *     effectiveTargetSdkVersion.
     */
    private void initAllowlist(long targetSdkVersion)
            throws FileNotFoundException, InvalidProtocolBufferException, IOException {
        synchronized (mPlatformApiAllowlistsLock) {
            if (mPlatformApiAllowlists == null) {
                mPlatformApiAllowlists = mApiAllowlistProvider.loadPlatformApiAllowlist();
            }

            if (!mPlatformApiAllowTries.containsKey(targetSdkVersion)) {
                buildAllowTrie(targetSdkVersion, mPlatformApiAllowlists.get(targetSdkVersion));
            }
        }
    }

    @GuardedBy("mPlatformApiAllowlistsLock")
    private void buildAllowTrie(long targetSdkVersion, AllowedApisList allowedApisList) {
        if (allowedApisList == null) {
            Log.w(TAG, "No allowlist found for targetSdk " + targetSdkVersion);
            return;
        }

        StringTrie<AllowedApi> allowTrie = getBaseRuleTrie();

        for (AllowedApi apiRule : allowedApisList.getAllowedApisList()) {
            String[] apiTokens = getApiTokens(apiRule);
            if (apiTokens != null) {
                AllowedApi oldRule = allowTrie.put(apiRule, apiTokens);
                if (oldRule != null && oldRule.getAllow() != apiRule.getAllow()) {
                    Log.w(
                            TAG,
                            "Rule was replaced for class "
                                    + oldRule.getClassName()
                                    + ". New rule value is: "
                                    + apiRule.getAllow());
                }
            } else {
                Log.w(TAG, "API Rule was malformed for rule with class " + apiRule.getClassName());
                return;
            }
        }

        mPlatformApiAllowTries.put(targetSdkVersion, allowTrie);
    }

    private StringTrie<AllowedApi> getBaseRuleTrie() {
        StringTrie<AllowedApi> allowTrie = new StringTrie();
        for (int i = 0; i < DEFAULT_RULES.length; i++) {
            allowTrie.put(DEFAULT_RULES[i], getApiTokens(DEFAULT_RULES[i]));
        }
        return allowTrie;
    }

    static class Injector {
        private ApiAllowlistProvider mAllowlistProvider;
        private SerialDexLoader mDexLoader;

        Injector() {
            mAllowlistProvider = new ApiAllowlistProvider();
            HandlerThread handlerThread =
                    new HandlerThread("DexParsingThread", Process.THREAD_PRIORITY_BACKGROUND);
            handlerThread.start();
            DexParser dexParser = new DexParserImpl();
            mDexLoader = new SerialDexLoader(dexParser, new Handler(handlerThread.getLooper()));
        }

        Injector(ApiAllowlistProvider apiAllowlistProvider, SerialDexLoader serialDexLoader) {
            mAllowlistProvider = apiAllowlistProvider;
            mDexLoader = serialDexLoader;
        }

        ApiAllowlistProvider getApiAllowlistProvider() {
            return mAllowlistProvider;
        }

        SerialDexLoader getDexLoader() {
            return mDexLoader;
        }
    }
}
