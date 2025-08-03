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

package com.android.car.pm;

import static android.car.content.pm.CarPackageManager.MANIFEST_METADATA_TARGET_CAR_VERSION;

import static com.android.car.pm.CarPackageManagerService.DBG;

import android.annotation.Nullable;
import android.car.CarVersion;
import android.car.builtin.util.Slogf;
import android.content.pm.ApplicationInfo;

import com.android.internal.annotations.VisibleForTesting;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class used to parse the target Car API version in an appication manifest.
 *
 */
final class CarVersionParser {

    private static final String TAG = CarVersionParser.class.getSimpleName();

    private static final Pattern API_VERSION_REGEX = Pattern
            .compile("^(?<major>\\d+)(:(?<minor>\\d+))?$");

    /**
     * Gets the target Car API version of the app.
     */
    public static CarVersion getTargetCarVersion(ApplicationInfo info) {
        String pkgName = info.packageName;
        int major, minor;
        if (info.metaData == null) {
            major = info.targetSdkVersion;
            minor = 0;
            if (DBG) {
                Slogf.d(TAG, "parse(%s): no metadata, returning (%d, %d)", pkgName, major, minor);
            }
            return CarVersion.forMajorAndMinorVersions(major, minor);
        }
        return parse(pkgName, info.metaData.getString(MANIFEST_METADATA_TARGET_CAR_VERSION),
                info.targetSdkVersion);
    }

    @VisibleForTesting
    static CarVersion parse(String pkgName, @Nullable String value, int targetSdkVersion) {
        if (value == null) {
            return CarVersion.forMajorAndMinorVersions(targetSdkVersion, 0);
        }
        Matcher matcher = API_VERSION_REGEX.matcher(value);
        if (!matcher.matches()) {
            if (DBG) {
                Slogf.d(TAG, "parse(%s): no match on %s, returning targetSdkVersion(%d) instead",
                        pkgName, value, targetSdkVersion);
            }
            return CarVersion.forMajorAndMinorVersions(targetSdkVersion, 0);
        }
        try {
            int major = Integer.parseInt(matcher.group("major"));
            String minorMatch = matcher.group("minor");
            int minor = minorMatch != null ? Integer.parseInt(minorMatch) : 0;
            return CarVersion.forMajorAndMinorVersions(major, minor);
        } catch (Exception e) {
            // Shouldn't happen, as it matched regex
            Slogf.w(TAG, e, "parse(%s): exception parsing valued value (%s) for pkg %s using %s"
                    + "; return targetSdkVersion(%d) instead", pkgName, API_VERSION_REGEX, value,
                    targetSdkVersion);
            return CarVersion.forMajorAndMinorVersions(targetSdkVersion, 0);
        }

    }

    private CarVersionParser() {
        throw new UnsupportedOperationException("contains only static methods");
    }
}
