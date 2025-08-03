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

package libcore.test.reasons;

/**
 * Reasons for {@link libcore.test.annotation.NonMts}.
 */
public class NonMtsReasons {

    /**
     * If the test depends on a new API from ICU, and version-specific API behavior or locale data,
     * you can use this reason.
     */
    public static final String ICU_VERSION_DEPENDENCY = "The API behavior depends on the "
           + "platform version. The test only passes above a certain API level.";

    /**
     * If the test only passes above a certain API level.
     */
    public static final String API_LEVEL_GATING = "The test only passes above "
            + "a certain API level.";

    /**
     * If the test asserts a new behavior not tested in CTS, OEMs have the right to customize the
     * behavior, but impossible to revert the behavior without system image OTA. Thus, we disable
     * the test in MTS until it's tested in the dessert CTS.
     */
    public static final String OEM_CUSTOMIZATION = "The test doesn't pass with certain " +
            "customizations from AOSP.";

    private NonMtsReasons() {}
}
