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

package com.android.server.nearby.util.identity;

import android.annotation.Nullable;
import android.content.Context;
import android.os.Binder;
import android.os.Process;


import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;

/**
 * Identifying information on a caller.
 *
 * @hide
 */
public final class CallerIdentity {

    /**
     * Creates a CallerIdentity from the current binder identity, using the given package, feature
     * id, and listener id. The package will be checked to enforce it belongs to the calling uid,
     * and a security exception will be thrown if it is invalid.
     */
    public static CallerIdentity fromBinder(Context context, String packageName,
            @Nullable String attributionTag) {
        int uid = Binder.getCallingUid();
        if (!contains(context.getPackageManager().getPackagesForUid(uid), packageName)) {
            throw new SecurityException("invalid package \"" + packageName + "\" for uid " + uid);
        }
        return fromBinderUnsafe(packageName, attributionTag);
    }

    /**
     * Construct a CallerIdentity for test purposes.
     */
    @VisibleForTesting
    public static CallerIdentity forTest(int uid, int pid, String packageName,
            @Nullable String attributionTag) {
        return new CallerIdentity(uid, pid, packageName, attributionTag);
    }

    /**
     * Creates a CallerIdentity from the current binder identity, using the given package, feature
     * id, and listener id. The package will not be checked to enforce that it belongs to the
     * calling uid - this method should only be used if the package will be validated by some other
     * means, such as an appops call.
     */
    public static CallerIdentity fromBinderUnsafe(String packageName,
            @Nullable String attributionTag) {
        return new CallerIdentity(Binder.getCallingUid(), Binder.getCallingPid(),
                packageName, attributionTag);
    }

    private final int mUid;

    private final int mPid;

    private final String mPackageName;

    private final @Nullable String mAttributionTag;


    private CallerIdentity(int uid, int pid, String packageName,
            @Nullable String attributionTag) {
        this.mUid = uid;
        this.mPid = pid;
        this.mPackageName = Objects.requireNonNull(packageName);
        this.mAttributionTag = attributionTag;
    }

    /** The calling UID. */
    public int getUid() {
        return mUid;
    }

    /** The calling PID. */
    public int getPid() {
        return mPid;
    }

    /** The calling package name. */
    public String getPackageName() {
        return mPackageName;
    }

    /** The calling attribution tag. */
    public String getAttributionTag() {
        return mAttributionTag;
    }

    /** Returns true if this represents a system server identity. */
    public boolean isSystemServer() {
        return mUid == Process.SYSTEM_UID;
    }

    @Override
    public String toString() {
        int length = 10 + mPackageName.length();
        if (mAttributionTag != null) {
            length += mAttributionTag.length();
        }

        StringBuilder builder = new StringBuilder(length);
        builder.append(mUid).append("/").append(mPackageName);
        if (mAttributionTag != null) {
            builder.append("[");
            if (mAttributionTag.startsWith(mPackageName)) {
                builder.append(mAttributionTag.substring(mPackageName.length()));
            } else {
                builder.append(mAttributionTag);
            }
            builder.append("]");
        }
        return builder.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CallerIdentity)) {
            return false;
        }
        CallerIdentity that = (CallerIdentity) o;
        return mUid == that.mUid
                && mPid == that.mPid
                && mPackageName.equals(that.mPackageName)
                && Objects.equals(mAttributionTag, that.mAttributionTag);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mUid, mPid, mPackageName, mAttributionTag);
    }

    private static <T> boolean contains(@Nullable T[] array, T value) {
        return indexOf(array, value) != -1;
    }

    /**
     * Return first index of {@code value} in {@code array}, or {@code -1} if
     * not found.
     */
    private static <T> int indexOf(@Nullable T[] array, T value) {
        if (array == null) return -1;
        for (int i = 0; i < array.length; i++) {
            if (Objects.equals(array[i], value)) return i;
        }
        return -1;
    }
}
