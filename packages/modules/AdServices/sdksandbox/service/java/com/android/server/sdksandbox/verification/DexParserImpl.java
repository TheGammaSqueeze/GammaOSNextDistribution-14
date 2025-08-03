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

import com.android.server.sdksandbox.verifier.SerialDexLoader.DexLoadResult;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * DEX parser for SDK verification
 *
 * @hide
 */
public class DexParserImpl implements DexParser {

    @Override
    public List<String> getDexList(String apkPath) {
        File apkPathFile = new File(apkPath);

        ArrayList<String> apkSplits = new ArrayList<>();

        // If multi-apk directory, find a base apk and zero or more split apks
        if (apkPathFile.isDirectory()) {
            for (File apkFile : apkPathFile.listFiles()) {
                if (apkFile.isFile()) {
                    apkSplits.add(apkFile.getAbsolutePath());
                }
            }
        } else {
            apkSplits.add(apkPath);
        }

        // TODO(b/231441674): get dex entries for each apk

        return apkSplits;
    }

    @Override
    public void loadDexSymbols(String dexFile, DexLoadResult dexLoadResult) {
        // TODO(b/279165123): parse dex file
    }
}
