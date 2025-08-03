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
package android.car;

import android.annotation.NonNull;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;

/**
 * Represents the API version of the standard Android SDK.
 */
public final class PlatformVersion extends ApiVersion<PlatformVersion> implements Parcelable {

    private static final String CODENAME_REL = "REL";

    /**
     * Contains pre-defined versions matching Car releases.
     */
    public static class VERSION_CODES {

        /**
         * Helper object for main version of Android 13.
         */
        @NonNull
        public static final PlatformVersion TIRAMISU_0 =
                new PlatformVersion("TIRAMISU_0", Build.VERSION_CODES.TIRAMISU, 0);

        /**
         * Helper object for first minor upgrade of Android 13.
         */
        @NonNull
        public static final PlatformVersion TIRAMISU_1 =
                new PlatformVersion("TIRAMISU_1", Build.VERSION_CODES.TIRAMISU, 1);

        /**
         * Helper object for second minor upgrade of Android 13.
         */
        @NonNull
        public static final PlatformVersion TIRAMISU_2 =
                new PlatformVersion("TIRAMISU_2", Build.VERSION_CODES.TIRAMISU, 2);

        /**
         * Helper object for third minor upgrade of Android 13.
         */
        @NonNull
        public static final PlatformVersion TIRAMISU_3 =
                new PlatformVersion("TIRAMISU_3", Build.VERSION_CODES.TIRAMISU, 3);

        /**
         * Helper object for main version of Android 14.
         */
        @NonNull
        public static final PlatformVersion UPSIDE_DOWN_CAKE_0 =
                new PlatformVersion("UPSIDE_DOWN_CAKE_0", Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 0);

        /**
         * Helper object for first minor upgrade of Android 14.
         */
        @NonNull
        public static final PlatformVersion UPSIDE_DOWN_CAKE_1 =
                new PlatformVersion("UPSIDE_DOWN_CAKE_1", Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 1);

        // DO NOT ADD minor UPSIDE_DOWN_CAKE version until lint tool is working. (b/275125924)

        /**
         * Helper object for main version of Android 15.
         */
        @NonNull
        public static final PlatformVersion VANILLA_ICE_CREAM_0 =
                new PlatformVersion("VANILLA_ICE_CREAM_0", Build.VERSION_CODES.VANILLA_ICE_CREAM,
                        0);

        private VERSION_CODES() {
            throw new UnsupportedOperationException("Only provide constants");
        }
    }

    /**
     * Creates a named instance with the given major and minor versions.
     */
    // TODO(b/243429779): should not need @ApiRequirements as it's package-protected
    static PlatformVersion newInstance(String versionName, int majorVersion, int minorVersion) {
        return new PlatformVersion(versionName, majorVersion, minorVersion);
    }

    /**
     * Returns the current platform version with given {@code minorVersion}.
     */
    static PlatformVersion getCurrentPlatformVersionForMinor(String versionName, int minorVersion) {
        // For un-released version, CUR_DEVELOPMENT should be used instead of SDK_INT.
        // ex) VERSION_CODES.T is CUR_DEVELOPMENT first then becomes 33 (=SDK_INT) when SDK is
        // finalized.
        return new PlatformVersion(versionName,
                CODENAME_REL.equals(Build.VERSION.CODENAME) ? Build.VERSION.SDK_INT
                        : Build.VERSION_CODES.CUR_DEVELOPMENT, minorVersion);
    }
    /**
     * Creates a new instance with the given major and minor versions.
     */
    @NonNull
    public static PlatformVersion forMajorAndMinorVersions(int majorVersion, int minorVersion) {
        return new PlatformVersion(majorVersion, minorVersion);
    }

    /**
     * Creates a new instance for a major version (i.e., the minor version will be {@code 0}.
     */
    @NonNull
    public static PlatformVersion forMajorVersion(int majorVersion) {
        return new PlatformVersion(majorVersion, /* minorVersion= */ 0);
    }

    private PlatformVersion(String name, int majorVersion, int minorVersion) {
        super(name, majorVersion, minorVersion);
    }

    private PlatformVersion(int majorVersion, int minorVersion) {
        super(majorVersion, minorVersion);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        writeToParcel(dest);
    }

    @NonNull
    public static final Parcelable.Creator<PlatformVersion> CREATOR =
            new Parcelable.Creator<PlatformVersion>() {

        @Override
        public PlatformVersion createFromParcel(Parcel source) {
            return ApiVersion.readFromParcel(source,
                    (name, major, minor) -> new PlatformVersion(name, major, minor));
        }

        @Override
        public PlatformVersion[] newArray(int size) {
            return new PlatformVersion[size];
        }
    };
}
