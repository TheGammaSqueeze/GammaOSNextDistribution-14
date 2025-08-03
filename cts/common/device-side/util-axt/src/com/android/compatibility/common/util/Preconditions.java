/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.compatibility.common.util;

import org.junit.Assert;

import java.io.File;

/**
 * Static methods used to validate preconditions in the media CTS suite to simplify failure
 * diagnosis.
 */
public final class Preconditions {
    private static final String TAG = "Preconditions";

    /**
     * While accessing resource file, if it is not present, media codec api sometimes sends
     * obfuscated message indicating the same. Have the test run this check before accessing
     * resource.
     */
    public static void assertTestFileExists(String pathName) {
        File testFile = new File(pathName);
        Assert.assertTrue("Test Setup Error, missing file: " + pathName, testFile.exists());
    }

    private Preconditions() {}
}
