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

import android.os.Handler;

import java.util.List;

/**
 * Handles the loading of dex files for multiple apks to be verified, ensures that a single dex file
 * is loaded at any time.
 */
public class SerialDexLoader {
    private static final String TAG = "SdkSandboxVerifier";

    private DexParser mParser;
    private Handler mHandler;
    private DexLoadResult mDexLoadResult;

    public SerialDexLoader(DexParser parser, Handler handler) {
        mParser = parser;
        mHandler = handler;
        mDexLoadResult = new DexLoadResult();
    }

    /**
     * Queues all dex files found for an apk for serially loading and analyzing.
     *
     * @param apkPath path to an apk containing one or more dex files
     * @param packagename packagename associated with the apk
     * @param verificationHandler object to handle the verification of the loaded dex
     */
    public void queueApkToLoad(
            String apkPath, String packagename, VerificationHandler verificationHandler) {
        List<String> dexFiles = mParser.getDexList(apkPath);

        mHandler.post(
                () -> {
                    for (String dexFile : dexFiles) {
                        mParser.loadDexSymbols(dexFile, mDexLoadResult);

                        if (!verificationHandler.verify(mDexLoadResult)) {
                            verificationHandler.verificationFinishedForPackage(false);
                            return;
                        }
                    }
                    verificationHandler.verificationFinishedForPackage(true);
                });
    }

    /** Interface for handling processing of the loaded dex contents */
    public interface VerificationHandler {

        /**
         * Takes in the DexLoadResult and verifies its contents.
         *
         * @param result object contains the symbols parsed from the loaded dex file
         */
        boolean verify(DexLoadResult result);

        /**
         * Called when all the loaded dex files have passed verification, or when one has failed.
         *
         * @param passed is false if the last loaded dex failed verification, or true if all dexes
         *     passed.
         */
        void verificationFinishedForPackage(boolean passed);
    }

    /** Result class that contains symbols loaded from a DEX file */
    public static class DexLoadResult {}
}
