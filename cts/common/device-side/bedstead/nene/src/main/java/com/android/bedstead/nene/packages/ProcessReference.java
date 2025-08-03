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

package com.android.bedstead.nene.packages;

import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.nene.utils.ShellCommand;

import java.util.Set;

@Experimental
public final class ProcessReference {

    private final Package mPackage;
    private final int mPid;
    private final int mUid;
    private final UserReference mUser;

    ProcessReference(Package pkg, int pid, int uid, UserReference user) {
        if (pkg == null) {
            throw new NullPointerException();
        }
        mPackage = pkg;
        mPid = pid;
        mUid = uid;
        mUser = user;
    }

    /**
     * Get the {@link Package} this process is associated with.
     */
    public Package pkg() {
        return mPackage;
    }

    /**
     * Get the pid of this process.
     */
    public int pid() {
        return mPid;
    }

    /**
     * Get the uid of this process.
     */
    public int uid() {
        return mUid;
    }

    /**
     * Get the {@link UserReference} this process is running on.
     */
    public UserReference user() {
        return mUser;
    }

    /**
     * Kill this process.
     */
    public void kill() {
        int pid = pid();

        // Disable and re-enable kills the process
        mPackage.disable(user());
        mPackage.enable(user());


//        // Removing a permission kills the process, so we can grant then remove an arbitrary
//        // permission
//        String permission = getGrantablePermission();
//
//        if (mPackage.hasPermission(mUser, permission)) {
//            mPackage.denyPermission(mUser, permission);
//            mPackage.grantPermission(mUser, permission);
//        } else {
//            mPackage.grantPermission(mUser, permission);
//            mPackage.denyPermission(mUser, permission);
//        }

        Poll.forValue("pid", () -> {
            ProcessReference other = mPackage.runningProcess(mUser);
            return other == null ? -1 : other.pid();
        }).toNotBeEqualTo(pid)
                .errorOnFail("Error killing process")
                .await();
    }

    /**
     * Crash this process.
     */
    public void crash() {
        ShellCommand.builder("am crash")
                .addOperand(mPackage.packageName())
                .validate(String::isEmpty)
                .executeOrThrowNeneException("Error crashing process");
    }

    private String getGrantablePermission() {
        Set<String> permissions = mPackage.requestedPermissions();
        for (String permission : permissions) {
            try {
                mPackage.checkCanGrantOrRevokePermission(mUser, permission);
                return permission;
            } catch (NeneException e) {
                // If we can't grant it we'll check the next one
            }
        }
        throw new NeneException("No grantable permission for package " + mPackage);
    }

    @Override
    public int hashCode() {
        return mPackage.hashCode() + mPid + mUser.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ProcessReference)) {
            return false;
        }

        ProcessReference other = (ProcessReference) obj;
        return other.mUser.equals(mUser)
                && other.mPid == mPid
                && other.mPackage.equals(mPackage);
    }

    @Override
    public String toString() {
        return "ProcessReference{package=" + mPackage
                + ", processId=" + mPid + ", user=" + mUser + "}";
    }
}
