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
 * Represents the API version of the {@code Car} SDK.
 */
public final class CarVersion extends ApiVersion<CarVersion> implements Parcelable {

    /**
     * Contains pre-defined versions matching Car releases.
     */
    public static class VERSION_CODES {

        /**
         * Helper object for main version of Android 13.
         */
        @NonNull
        public static final CarVersion TIRAMISU_0 =
                new CarVersion("TIRAMISU_0", Build.VERSION_CODES.TIRAMISU, 0);

        /**
         * Helper object for first minor upgrade of Android 13.
         */
        @NonNull
        public static final CarVersion TIRAMISU_1 =
                new CarVersion("TIRAMISU_1", Build.VERSION_CODES.TIRAMISU, 1);

        /**
         * Helper object for second minor upgrade of Android 13.
         */
        @NonNull
        public static final CarVersion TIRAMISU_2 =
                new CarVersion("TIRAMISU_2", Build.VERSION_CODES.TIRAMISU, 2);

        /**
         * Helper object for third minor upgrade of Android 13.
         */
        @NonNull
        public static final CarVersion TIRAMISU_3 =
                new CarVersion("TIRAMISU_3", Build.VERSION_CODES.TIRAMISU, 3);

        /**
         * Helper object for main version of Android 14.
         */
        @NonNull
        public static final CarVersion UPSIDE_DOWN_CAKE_0 =
                new CarVersion("UPSIDE_DOWN_CAKE_0", Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 0);

       /**
         * Helper object for first minor upgrade of Android 14.
         */
        @NonNull
        public static final CarVersion UPSIDE_DOWN_CAKE_1 =
                new CarVersion("UPSIDE_DOWN_CAKE_1", Build.VERSION_CODES.UPSIDE_DOWN_CAKE, 1);

        /**
         * Helper object for main version of Android 15.
         */
        @NonNull
        public static final CarVersion VANILLA_ICE_CREAM_0 =
                new CarVersion("VANILLA_ICE_CREAM_0", Build.VERSION_CODES.VANILLA_ICE_CREAM, 0);

        private VERSION_CODES() {
            throw new UnsupportedOperationException("Only provide constants");
        }
    }

    /**
     * Creates a named instance with the given major and minor versions.
     */
    static CarVersion newInstance(String versionName, int majorVersion, int minorVersion) {
        return new CarVersion(versionName, majorVersion, minorVersion);
    }

    /**
     * Creates a new instance with the given major and minor versions.
     */
    @NonNull
    public static CarVersion forMajorAndMinorVersions(int majorVersion, int minorVersion) {
        return new CarVersion(majorVersion, minorVersion);
    }

    /**
     * Creates a new instance for a major version (i.e., the minor version will be {@code 0}.
     */
    @NonNull
    public static CarVersion forMajorVersion(int majorVersion) {
        return new CarVersion(majorVersion, /* minorVersion= */ 0);
    }

    private CarVersion(String name, int majorVersion, int minorVersion) {
        super(name, majorVersion, minorVersion);
    }

    private CarVersion(int majorVersion, int minorVersion) {
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
    public static final Parcelable.Creator<CarVersion> CREATOR =
            new Parcelable.Creator<CarVersion>() {

        @Override
        public CarVersion createFromParcel(Parcel source) {
            return ApiVersion.readFromParcel(source,
                    (name, major, minor) -> new CarVersion(name, major, minor));
        }

        @Override
        public CarVersion[] newArray(int size) {
            return new CarVersion[size];
        }
    };
}
