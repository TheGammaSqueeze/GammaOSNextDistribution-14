/*
 * Copyright (C) 2022 The Android Open Source Project
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

package tests.support;

import dalvik.annotation.compat.VersionCodes;

import java.util.Locale;

public final class PlatformVersions {

    private PlatformVersions() {}

    public static boolean isAtLeastU() {
        int sdkInt = AndroidProperties.getInt("ro.build.version.sdk", -1);

        return sdkInt >= VersionCodes.TIRAMISU && isAtLeastPreReleaseCodename("UpsideDownCake");
    }

    private static boolean isAtLeastPreReleaseCodename(String codename) {
        String buildCodename = AndroidProperties.getString("ro.build.version.codename", "");

        // Special case "REL", which means the build is not a pre-release build.
        if ("REL".equals(buildCodename)) {
            return false;
        }

        // Otherwise lexically compare them.  Return true if the build codename is equal to or
        // greater than the requested codename.
        String buildCodenameUpper = buildCodename.toUpperCase(Locale.ROOT);
        String codenameUpper = codename.toUpperCase(Locale.ROOT);

        return buildCodenameUpper.compareTo(codenameUpper) >= 0;
    }
}
