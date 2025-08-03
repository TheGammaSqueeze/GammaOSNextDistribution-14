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
package com.android.server.appsearch.util;

import static android.app.appsearch.AppSearchResult.RESULT_RATE_LIMITED;
import static android.app.appsearch.AppSearchResult.throwableToFailedResult;

import android.Manifest;
import android.annotation.BinderThread;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.appsearch.AppSearchBatchResult;
import android.app.appsearch.AppSearchEnvironmentFactory;
import android.app.appsearch.AppSearchResult;
import android.app.appsearch.aidl.AppSearchAttributionSource;
import android.app.appsearch.aidl.AppSearchBatchResultParcel;
import android.app.appsearch.aidl.AppSearchResultParcel;
import android.app.appsearch.aidl.IAppSearchBatchResultCallback;
import android.app.appsearch.aidl.IAppSearchResultCallback;
import android.app.appsearch.annotation.CanIgnoreReturnValue;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArraySet;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.server.appsearch.AppSearchUserInstanceManager;
import com.android.server.appsearch.external.localstorage.stats.CallStats;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * Utilities to help with implementing AppSearch's services.
 * @hide
 */
public class ServiceImplHelper {
    private static final String TAG = "AppSearchServiceUtil";

    private final Context mContext;
    private final UserManager mUserManager;
    private final ExecutorManager mExecutorManager;
    private final AppSearchUserInstanceManager mAppSearchUserInstanceManager;

    // Cache of unlocked users so we don't have to query UserManager service each time. The "locked"
    // suffix refers to the fact that access to the field should be locked; unrelated to the
    // unlocked status of users.
    @GuardedBy("mUnlockedUsersLocked")
    private final Set<UserHandle> mUnlockedUsersLocked = new ArraySet<>();

    // Currently, only the main user can have an associated enterprise user, so the enterprise
    // parent will naturally always be the main user
    @GuardedBy("mUnlockedUsersLocked")
    @Nullable
    private UserHandle mEnterpriseParentUserLocked;

    @GuardedBy("mUnlockedUsersLocked")
    @Nullable
    private UserHandle mEnterpriseUserLocked;

    public ServiceImplHelper(@NonNull Context context, @NonNull ExecutorManager executorManager) {
        mContext = Objects.requireNonNull(context);
        mUserManager = context.getSystemService(UserManager.class);
        mExecutorManager = Objects.requireNonNull(executorManager);
        mAppSearchUserInstanceManager = AppSearchUserInstanceManager.getInstance();
    }

    public void setUserIsLocked(@NonNull UserHandle userHandle, boolean isLocked) {
        boolean isManagedProfile = mUserManager.isManagedProfile(userHandle.getIdentifier());
        UserHandle parentUser = isManagedProfile ? mUserManager.getProfileParent(userHandle)
                : null;
        synchronized (mUnlockedUsersLocked) {
            if (isLocked) {
                if (isManagedProfile) {
                    mEnterpriseParentUserLocked = null;
                    mEnterpriseUserLocked = null;
                }
                mUnlockedUsersLocked.remove(userHandle);
            } else {
                if (isManagedProfile) {
                    mEnterpriseParentUserLocked = parentUser;
                    mEnterpriseUserLocked = userHandle;
                }
                mUnlockedUsersLocked.add(userHandle);
            }
        }
    }

    public boolean isUserLocked(@NonNull UserHandle callingUser) {
        synchronized (mUnlockedUsersLocked) {
            // First, check the local copy.
            if (mUnlockedUsersLocked.contains(callingUser)) {
                return false;
            }
            // If the local copy says the user is locked, check with UM for the actual state,
            // since the user might just have been unlocked.
            return !mUserManager.isUserUnlockingOrUnlocked(callingUser);
        }
    }

    public void verifyUserUnlocked(@NonNull UserHandle callingUser) {
        if (isUserLocked(callingUser)) {
            throw new IllegalStateException(callingUser + " is locked or not running.");
        }
    }

    /**
     * Returns the target user's associated enterprise user or null if it does not exist. Note, the
     * enterprise user is not considered the associated enterprise user of itself.
     */
    @Nullable
    private UserHandle getEnterpriseUser(@NonNull UserHandle targetUser) {
        synchronized (mUnlockedUsersLocked) {
            if (mEnterpriseUserLocked == null || !targetUser.equals(mEnterpriseParentUserLocked)) {
                return null;
            }
            return mEnterpriseUserLocked;
        }
    }

    /**
     * Verifies that the information about the caller matches Binder's settings, determines a final
     * user that the call is allowed to run as, and checks that the user is unlocked.
     *
     * <p>If these checks fail, returns {@code null} and sends the error to the given callback.
     *
     * <p>This method must be called on the binder thread.
     *
     * @return The result containing the final verified user that the call should run as, if all
     * checks pass. Otherwise return null.
     */
    @BinderThread
    @Nullable
    public UserHandle verifyIncomingCallWithCallback(
            @NonNull AppSearchAttributionSource callerAttributionSource,
            @NonNull UserHandle userHandle,
            @NonNull IAppSearchResultCallback errorCallback) {
        try {
            return verifyIncomingCall(callerAttributionSource, userHandle);
        } catch (Throwable t) {
            invokeCallbackOnResult(errorCallback, throwableToFailedResult(t));
            return null;
        }
    }

    /**
     * Verifies that the information about the caller matches Binder's settings, determines a final
     * user that the call is allowed to run as, and checks that the user is unlocked.
     *
     * <p>If these checks fail, returns {@code null} and sends the error to the given callback.
     *
     * <p>This method must be called on the binder thread.
     *
     * @return The result containing the final verified user that the call should run as, if all
     * checks pass. Otherwise return null.
     */
    @BinderThread
    @Nullable
    public UserHandle verifyIncomingCallWithCallback(
            @NonNull AppSearchAttributionSource callerAttributionSource,
            @NonNull UserHandle userHandle,
            @NonNull IAppSearchBatchResultCallback errorCallback) {
        try {
            return verifyIncomingCall(callerAttributionSource, userHandle);
        } catch (Throwable t) {
            invokeCallbackOnError(errorCallback, t);
            return null;
        }
    }

    /**
     * Verifies that the information about the caller matches Binder's settings, determines a final
     * user that the call is allowed to run as, and checks that the user is unlocked.
     *
     * <p>This method must be called on the binder thread.
     *
     * @return The final verified user that the caller should act as
     * @throws RuntimeException if validation fails
     */
    @BinderThread
    @NonNull
    public UserHandle verifyIncomingCall(
            @NonNull AppSearchAttributionSource callerAttributionSource,
            @NonNull UserHandle userHandle) {
        Objects.requireNonNull(callerAttributionSource);
        Objects.requireNonNull(userHandle);

        int callingPid = Binder.getCallingPid();
        int callingUid = Binder.getCallingUid();
        long callingIdentity = Binder.clearCallingIdentity();
        try {
            verifyCaller(callingUid, callerAttributionSource);
            String callingPackageName =
                Objects.requireNonNull(callerAttributionSource.getPackageName());
            UserHandle targetUser =
                handleIncomingUser(callingPackageName, userHandle, callingPid, callingUid);
            verifyUserUnlocked(targetUser);
            return targetUser;
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
    }

    /**
     * Verify various aspects of the calling user.
     * @param callingUid Uid of the caller, usually retrieved from Binder for authenticity.
     * @param callerAttributionSource The permission identity of the caller
     */
    // enforceCallingUidAndPid is called on AttributionSource during deserialization.
    private void verifyCaller(int callingUid,
            @NonNull AppSearchAttributionSource callerAttributionSource) {
        // Obtain the user where the client is running in. Note that this could be different from
        // the userHandle where the client wants to run the AppSearch operation in.
        UserHandle callingUserHandle = UserHandle.getUserHandleForUid(callingUid);
        Context callingUserContext = AppSearchEnvironmentFactory
            .getEnvironmentInstance()
            .createContextAsUser(mContext, callingUserHandle);
        String callingPackageName =
            Objects.requireNonNull(callerAttributionSource.getPackageName());
        verifyCallingPackage(callingUserContext, callingUid, callingPackageName);
        verifyNotInstantApp(callingUserContext, callingPackageName);
    }

    /**
     * Check that the caller's supposed package name matches the uid making the call.
     *
     * @throws SecurityException if the package name and uid don't match.
     */
    private void verifyCallingPackage(
            @NonNull Context actualCallingUserContext,
            int actualCallingUid,
            @NonNull String claimedCallingPackage) {
        int claimedCallingUid = PackageUtil.getPackageUid(
                actualCallingUserContext, claimedCallingPackage);
        if (claimedCallingUid != actualCallingUid) {
            throw new SecurityException(
                    "Specified calling package ["
                            + claimedCallingPackage
                            + "] does not match the calling uid "
                            + actualCallingUid);
        }
    }

    /**
     * Ensure instant apps can't make calls to AppSearch.
     *
     * @throws SecurityException if the caller is an instant app.
     */
    private void verifyNotInstantApp(@NonNull Context userContext, @NonNull String packageName) {
        PackageManager callingPackageManager = userContext.getPackageManager();
        if (callingPackageManager.isInstantApp(packageName)) {
            throw new SecurityException("Caller not allowed to create AppSearch session"
                    + "; userHandle=" + userContext.getUser() + ", callingPackage=" + packageName);
        }
    }

    /**
     * Helper for dealing with incoming user arguments to system service calls.
     *
     * <p>Takes care of checking permissions and if the target is special user, this method will
     * simply throw.
     *
     * @param callingPackageName The package name of the caller.
     * @param targetUserHandle The user which the caller is requesting to execute as.
     * @param callingPid The actual pid of the caller as determined by Binder.
     * @param callingUid The actual uid of the caller as determined by Binder.
     *
     * @return the user handle that the call should run as. Will always be a concrete user.
     *
     * @throws IllegalArgumentException if the target user is a special user.
     * @throws SecurityException if caller trying to interact across user without
     * {@link Manifest.permission#INTERACT_ACROSS_USERS_FULL}
     */
    @NonNull
    private UserHandle handleIncomingUser(@NonNull String callingPackageName,
            @NonNull UserHandle targetUserHandle, int callingPid, int callingUid) {
        UserHandle callingUserHandle = UserHandle.getUserHandleForUid(callingUid);
        if (callingUserHandle.equals(targetUserHandle)) {
            return targetUserHandle;
        }

        // Duplicates UserController#ensureNotSpecialUser
        if (targetUserHandle.getIdentifier() < 0) {
            throw new IllegalArgumentException(
                    "Call does not support special user " + targetUserHandle);
        }

        if (mContext.checkPermission(
                Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                callingPid,
                callingUid) == PackageManager.PERMISSION_GRANTED) {try {
            // Normally if the calling package doesn't exist in the target user, user cannot
            // call AppSearch. But since the SDK side cannot be trusted, we still need to verify
            // the calling package exists in the target user.
            // We need to create the package context for the targetUser, and this call will fail
            // if the calling package doesn't exist in the target user.
            mContext.createPackageContextAsUser(callingPackageName, /*flags=*/0,
                    targetUserHandle);
        } catch (PackageManager.NameNotFoundException e) {
            throw new SecurityException(
                    "Package: " + callingPackageName + " haven't installed for user "
                            + targetUserHandle.getIdentifier());
        }
            return targetUserHandle;
        }
        throw new SecurityException(
                "Permission denied while calling from uid " + callingUid
                        + " with " + targetUserHandle + "; Requires permission: "
                        + Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    /**
     * Helper to execute the implementation of some AppSearch functionality on the executor for that
     * user.
     *
     * <p>You should first make sure the call is allowed to run using {@link #verifyCaller}.
     *
     * @param targetUser            The verified user the call should run as, as determined by
     *                              {@link #verifyCaller}.
     * @param errorCallback         Callback to complete with an error if starting the lambda fails.
     *                              Otherwise this callback is not triggered.
     * @param callingPackageName    Package making this lambda call.
     * @param apiType               Api type of this lambda call.
     * @param lambda                The lambda to execute on the user-provided executor.
     *
     * @return true if the call is accepted by the executor and false otherwise.
     */
    @BinderThread
    @CanIgnoreReturnValue
    public boolean executeLambdaForUserAsync(
            @NonNull UserHandle targetUser,
            @NonNull IAppSearchResultCallback errorCallback,
            @NonNull String callingPackageName,
            @CallStats.CallType int apiType,
            @NonNull Runnable lambda) {
        Objects.requireNonNull(targetUser);
        Objects.requireNonNull(errorCallback);
        Objects.requireNonNull(callingPackageName);
        Objects.requireNonNull(lambda);
        try {
            Executor executor = mExecutorManager.getOrCreateUserExecutor(targetUser);
            if (executor instanceof RateLimitedExecutor) {
                boolean callAccepted = ((RateLimitedExecutor) executor).execute(lambda,
                        callingPackageName, apiType);
                if (!callAccepted) {
                    invokeCallbackOnResult(errorCallback,
                            AppSearchResult.newFailedResult(RESULT_RATE_LIMITED,
                                    "AppSearch rate limit reached."));
                    return false;
                }
            } else {
                executor.execute(lambda);
            }
        } catch (RuntimeException e) {
            invokeCallbackOnResult(errorCallback, throwableToFailedResult(e));
        }
        return true;
    }

    /**
     * Helper to execute the implementation of some AppSearch functionality on the executor for that
     * user.
     *
     * <p>You should first make sure the call is allowed to run using {@link #verifyCaller}.
     *
     * @param targetUser            The verified user the call should run as, as determined by
     *                              {@link #verifyCaller}.
     * @param errorCallback         Callback to complete with an error if starting the lambda fails.
     *                              Otherwise this callback is not triggered.
     * @param callingPackageName    Package making this lambda call.
     * @param apiType               Api type of this lambda call.
     * @param lambda                The lambda to execute on the user-provided executor.
     *
     * @return true if the call is accepted by the executor and false otherwise.
     */
    @BinderThread
    public boolean executeLambdaForUserAsync(
            @NonNull UserHandle targetUser,
            @NonNull IAppSearchBatchResultCallback errorCallback,
            @NonNull String callingPackageName,
            @CallStats.CallType int apiType,
            @NonNull Runnable lambda) {
        Objects.requireNonNull(targetUser);
        Objects.requireNonNull(errorCallback);
        Objects.requireNonNull(callingPackageName);
        Objects.requireNonNull(lambda);
        try {
            Executor executor = mExecutorManager.getOrCreateUserExecutor(targetUser);
            if (executor instanceof RateLimitedExecutor) {
                boolean callAccepted = ((RateLimitedExecutor) executor).execute(lambda,
                        callingPackageName, apiType);
                if (!callAccepted) {
                    invokeCallbackOnError(errorCallback,
                            AppSearchResult.newFailedResult(RESULT_RATE_LIMITED,
                                    "AppSearch rate limit reached."));
                    return false;
                }
            } else {
                executor.execute(lambda);
            }
        } catch (RuntimeException e) {
            invokeCallbackOnError(errorCallback, e);
        }
        return true;
    }

    /**
     * Helper to execute the implementation of some AppSearch functionality on the executor for that
     * user, without invoking callback for the user.
     *
     * <p>You should first make sure the call is allowed to run using {@link #verifyCaller}.
     *
     * @param targetUser         The verified user the call should run as, as determined by
     *                           {@link #verifyCaller}.
     * @param callingPackageName Package making this lambda call.
     * @param apiType            Api type of this lambda call.
     * @param lambda             The lambda to execute on the user-provided executor.
     *
     * @return true if the call is accepted by the executor and false otherwise.
     */
    @BinderThread
    public boolean executeLambdaForUserNoCallbackAsync(
            @NonNull UserHandle targetUser,
            @NonNull String callingPackageName,
            @CallStats.CallType int apiType,
            @NonNull Runnable lambda) {
        Objects.requireNonNull(targetUser);
        Objects.requireNonNull(callingPackageName);
        Objects.requireNonNull(lambda);
        Executor executor = mExecutorManager.getOrCreateUserExecutor(targetUser);
        if (executor instanceof RateLimitedExecutor) {
            return ((RateLimitedExecutor) executor).execute(lambda, callingPackageName,
                    apiType);
        } else {
            executor.execute(lambda);
            return true;
        }
    }

    /**
     * Returns the target user of the query depending on whether the query is for enterprise access
     * or not. If the query is not enterprise, returns the original target user. If the query is
     * enterprise, gets the target user's associated enterprise user.
     */
    @Nullable
    public UserHandle getUserToQuery(boolean isForEnterprise, @NonNull UserHandle targetUser) {
        if (!isForEnterprise) {
            return targetUser;
        }
        UserHandle enterpriseUser = getEnterpriseUser(targetUser);
        // Do not return the enterprise user if its AppSearch instance does not exist
        if (enterpriseUser == null ||
                mAppSearchUserInstanceManager.getUserInstanceOrNull(enterpriseUser) == null) {
            return null;
        }
        return enterpriseUser;
    }

    /** Invokes the {@link IAppSearchResultCallback} with the result. */
    public static void invokeCallbackOnResult(
            IAppSearchResultCallback callback, AppSearchResult<?> result) {
        try {
            callback.onResult(new AppSearchResultParcel<>(result));
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to send result to the callback", e);
        }
    }

    /** Invokes the {@link IAppSearchBatchResultCallback} with the result. */
    public static void invokeCallbackOnResult(
            IAppSearchBatchResultCallback callback, AppSearchBatchResult<String, ?> result) {
        try {
            callback.onResult(new AppSearchBatchResultParcel<>(result));
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to send result to the callback", e);
        }
    }

    /**
     * Invokes the {@link IAppSearchBatchResultCallback} with an unexpected internal throwable.
     *
     * <p>The throwable is converted to {@link AppSearchResult}.
     */
    public static void invokeCallbackOnError(
            @NonNull IAppSearchBatchResultCallback callback, @NonNull Throwable throwable) {
        invokeCallbackOnError(callback, throwableToFailedResult(throwable));
    }

    /**
     * Invokes the {@link IAppSearchBatchResultCallback} with the error result.
     */
    public static void invokeCallbackOnError(
            @NonNull IAppSearchBatchResultCallback callback, @NonNull AppSearchResult<?> result) {
        try {
            callback.onSystemError(new AppSearchResultParcel<>(result));
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to send error to the callback", e);
        }
    }
}
