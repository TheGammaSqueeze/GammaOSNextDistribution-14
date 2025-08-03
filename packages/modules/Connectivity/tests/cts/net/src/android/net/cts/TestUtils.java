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

package android.net.cts;

import android.os.Build;

import com.android.modules.utils.build.SdkLevel;
import com.android.networkstack.apishim.ConstantsShim;

/**
 * Utils class to provide common shared test helper methods or constants that behave differently
 * depending on the SDK against which they are compiled.
 */
public class TestUtils {
    /**
     * Whether to test S+ APIs. This requires a) that the test be running on an S+ device, and
     * b) that the code be compiled against shims new enough to access these APIs.
     */
    public static boolean shouldTestSApis() {
        return SdkLevel.isAtLeastS() && ConstantsShim.VERSION > Build.VERSION_CODES.R;
    }

    /**
     * Whether to test T+ APIs. This requires a) that the test be running on an T+ device, and
     * b) that the code be compiled against shims new enough to access these APIs.
     */
    public static boolean shouldTestTApis() {
        return SdkLevel.isAtLeastT() && ConstantsShim.VERSION > Build.VERSION_CODES.S_V2;
    }

    /**
     * Whether to test U+ APIs. This requires a) that the test be running on an U+ device, and
     * b) that the code be compiled against shims new enough to access these APIs.
     */
    public static boolean shouldTestUApis() {
        return SdkLevel.isAtLeastU() && ConstantsShim.VERSION > Build.VERSION_CODES.TIRAMISU;
    }
}
