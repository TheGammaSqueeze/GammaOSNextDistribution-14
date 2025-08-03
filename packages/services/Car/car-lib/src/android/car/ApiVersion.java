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
import android.annotation.Nullable;
import android.os.Parcel;
import android.text.TextUtils;

import java.util.Objects;

/**
 * Abstraction of Android APIs.
 *
 * <p>This class is used to represent a pair of major / minor API versions: the "major" version
 * represents a "traditional" Android SDK release, while the "minor" is used to indicate incremental
 * releases for that major.
 *
 * <p>This class is needed because the standard Android SDK API versioning only supports major
 * releases, but {@code Car} APIs can now (starting on
 * {@link android.os.Build.VERSION_CODES#TIRAMISU Android 13}) be updated on minor releases
 * as well.
 *
 * @param <T> implementation type
 */
public abstract class ApiVersion<T extends ApiVersion<?>> {

    /**
     * When set, it's used on {@link #toString()} - useful for versions that are pre-defined
     * (like {@code TIRAMISU_1}).
     */
    @Nullable
    private final String mVersionName;

    private final int mMajorVersion;
    private final int mMinorVersion;

    ApiVersion(int majorVersion, int minorVersion) {
        this(/* name= */ null, majorVersion, minorVersion);
    }

    ApiVersion(String name, int majorVersion, int minorVersion) {
        mVersionName = name;
        mMajorVersion = majorVersion;
        mMinorVersion = minorVersion;
    }

    /**
     * Checks if this API version meets the required version.
     *
     * @param requiredVersion required major and minor version number
     * @return {@code true} if the {@link #getMajorVersion() major version} is newer than the
     *         {@code requiredVersion}'s major or if the {@link #getMajorVersion() major version} is
     *         the same as {@code requiredVersion}'s major with the {@link #getMinorVersion() minor
     *         version} the same or newer than {@code requiredVersion}'s minor
     * @throws IllegalArgumentException if {@code requiredVersion} is not an instance of the same
     *         class as this object
     */
    public final boolean isAtLeast(@NonNull T requiredVersion) {
        Objects.requireNonNull(requiredVersion);

        if (!this.getClass().isInstance(requiredVersion)) {
            throw new IllegalArgumentException("Cannot compare " + this.getClass().getName()
                    + " against " + requiredVersion.getClass().getName());
        }

        int requiredApiVersionMajor = requiredVersion.getMajorVersion();
        int requiredApiVersionMinor = requiredVersion.getMinorVersion();

        return (mMajorVersion > requiredApiVersionMajor)
                || (mMajorVersion == requiredApiVersionMajor
                        && mMinorVersion >= requiredApiVersionMinor);
    }

    /**
     * Gets the major version of the API represented by this object.
     */
    public final int getMajorVersion() {
        return mMajorVersion;
    }

    /**
     * Gets the minor version change of API for the same {@link #getMajorVersion()}.
     *
     * <p>It will reset to {@code 0} whenever {@link #getMajorVersion()} is updated
     * and will increase by {@code 1} if car builtin or other car platform part is changed with the
     * same {@link #getMajorVersion()}.
     *
     * <p>Client should check this version to use APIs which were added in a minor-only version
     * update.
     */
    public final int getMinorVersion() {
        return mMinorVersion;
    }

    /**
     * @hide
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        @SuppressWarnings("unchecked")
        ApiVersion<T> other = (ApiVersion<T>) obj;
        return (mMajorVersion == other.mMajorVersion) && (mMinorVersion == other.mMinorVersion);
    }

    /**
     * @hide
     */
    @Override
    public int hashCode() {
        int prime = 31;
        int result = 1;
        result = prime * result + mMajorVersion;
        result = prime * result + mMinorVersion;
        return result;
    }

    /**
     * @hide
     */
    @Override
    @NonNull
    public final String toString() {
        StringBuilder builder = new StringBuilder(getClass().getSimpleName()).append('[');
        if (!TextUtils.isEmpty(mVersionName)) {
            builder.append("name=").append(mVersionName).append(", ");
        }
        return builder
                .append("major=").append(mMajorVersion)
                .append(", minor=").append(mMinorVersion)
                .append(']').toString();
    }

    /**
     * @hide
     */
    protected void writeToParcel(Parcel dest) {
        dest.writeString(mVersionName);
        dest.writeInt(getMajorVersion());
        dest.writeInt(getMinorVersion());
    }

    /**
     * @hide
     */
    protected static <T extends ApiVersion<?>> T readFromParcel(Parcel source,
            ApiVersionFactory<T> factory) {
        String name = source.readString();
        int major = source.readInt();
        int minor = source.readInt();
        return factory.newInstance(name, major, minor);
    }

    /**
     * @hide
     */
    interface ApiVersionFactory<T extends ApiVersion<?>> {
        T newInstance(String name, int major, int minor);
    }
}
