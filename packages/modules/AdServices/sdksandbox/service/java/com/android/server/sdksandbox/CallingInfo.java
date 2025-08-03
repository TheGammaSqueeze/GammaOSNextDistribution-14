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

package com.android.server.sdksandbox;

import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;
import android.os.UserHandle;

import java.util.Objects;

/**
 * Representation of a caller for an SDK sandbox.
 * @hide
 */
public final class CallingInfo {

    private final int mUid;
    private final String mPackageName;
    private final @Nullable IBinder mAppProcessToken;
    private boolean mIsCallFromSdkSandbox;

    private static void enforceCallingPackageBelongsToUid(
            Context context, int uid, String packageName) {
        int packageUid;
        PackageManager pm =
                context.createContextAsUser(UserHandle.getUserHandleForUid(uid), 0)
                        .getPackageManager();
        try {
            packageUid = pm.getPackageUid(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new SecurityException(packageName + " not found");
        }
        if (packageUid != uid) {
            throw new SecurityException(packageName + " does not belong to uid " + uid);
        }
    }

    public CallingInfo(int uid, String packageName, @Nullable IBinder processTokenBinder) {
        mUid = uid;
        mPackageName = Objects.requireNonNull(packageName);
        mAppProcessToken = processTokenBinder;
        mIsCallFromSdkSandbox = Process.isSdkSandboxUid(mUid);
    }

    public CallingInfo(int uid, String packageName) {
        this(uid, packageName, null);
    }

    static CallingInfo fromExternal(Context context, int uid, String packageNameUnchecked) {
        enforceCallingPackageBelongsToUid(context, uid, packageNameUnchecked);
        CallingInfo callingInfo = new CallingInfo(uid, packageNameUnchecked);
        callingInfo.mIsCallFromSdkSandbox = true;
        return callingInfo;
    }

    static CallingInfo fromBinder(Context context, String packageNameUnchecked) {
        return fromBinderWithApplicationThread(context, packageNameUnchecked, null);
    }

    static CallingInfo fromBinderWithApplicationThread(
            Context context,
            String packageNameUnchecked,
            @Nullable IBinder callingApplicationThread) {
        final int uid = Binder.getCallingUid();
        enforceCallingPackageBelongsToUid(context, uid, packageNameUnchecked);

        return new CallingInfo(uid, packageNameUnchecked, callingApplicationThread);
    }

    public int getUid() {
        return mUid;
    }

    public @Nullable IBinder getAppProcessToken() {
        return mAppProcessToken;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public boolean isCallFromSdkSandbox() {
        return mIsCallFromSdkSandbox;
    }

    @Override
    public String toString() {
        return "CallingInfo{"
                + "mUid="
                + mUid
                + ", mPackageName='"
                + mPackageName
                + ", mAppProcessToken='"
                + mAppProcessToken
                + "'}";
    }

    @Override
    public boolean equals(Object o) {
        // Note that mApplicationThread is not part of the comparison, because it is not always set,
        // nor is it necessary to determine whether two instances refer to the same caller.
        if (this == o) return true;
        if (!(o instanceof CallingInfo)) return false;
        CallingInfo that = (CallingInfo) o;
        return mUid == that.mUid && mPackageName.equals(that.mPackageName);
    }

    @Override
    public int hashCode() {
        return mUid ^ mPackageName.hashCode();
    }
}
