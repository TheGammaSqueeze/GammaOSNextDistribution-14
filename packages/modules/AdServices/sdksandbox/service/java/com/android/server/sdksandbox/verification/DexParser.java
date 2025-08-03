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

import java.util.List;

/**
 * Interface for DEX parsing, needed for SDK verification
 *
 * @hide
 */
public interface DexParser {

    /**
     * Returns the list of all dex files contained in the file
     *
     * @param apkPath path to an apk containing one or more dex files
     */
    List<String> getDexList(String apkPath);

    /**
     * Parses the dex file and reads the symbols in, populates data in the DexLoadResult.
     *
     * @param dexFile path to dex file to parse
     * @param dexLoadResult sparse array to insert the methods loaded from the dex file
     */
    void loadDexSymbols(String dexFile, DexLoadResult dexLoadResult);
}
