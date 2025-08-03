/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.car.content.pm;

import static android.car.Car.PERMISSION_CONTROL_APP_BLOCKING;
import static android.car.Car.PERMISSION_QUERY_DISPLAY_COMPATIBILITY;
import static android.car.CarLibLog.TAG_CAR;

import android.Manifest;
import android.annotation.FlaggedApi;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.annotation.TestApi;
import android.annotation.UserIdInt;
import android.app.PendingIntent;
import android.car.Car;
import android.car.CarManagerBase;
import android.car.CarVersion;
import android.car.feature.Flags;
import android.content.ComponentName;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.List;

/**
 * Provides car specific API related with package management.
 */
public final class CarPackageManager extends CarManagerBase {

    private static final String TAG = CarPackageManager.class.getSimpleName();

    /**
     * Flag for {@link #setAppBlockingPolicy(String, CarAppBlockingPolicy, int)}. When this
     * flag is set, the call will be blocked until policy is set to system. This can take time
     * and the flag cannot be used in main thread.
     *
     * @hide
     * @deprecated see the {@link #setAppBlockingPolicy(String, CarAppBlockingPolicy, int)}
     * documentation for alternative mechanism.
     */
    @SystemApi
    @Deprecated
    public static final int FLAG_SET_POLICY_WAIT_FOR_CHANGE = 0x1;
    /**
     * Flag for {@link #setAppBlockingPolicy(String, CarAppBlockingPolicy, int)}. When this
     * flag is set, passed policy is added to existing policy set from the current package.
     * If none of {@link #FLAG_SET_POLICY_ADD} or {@link #FLAG_SET_POLICY_REMOVE} is set, existing
     * policy is replaced. Note that policy per each package is always replaced and will not be
     * added.
     *
     * @hide
     * @deprecated see the {@link #setAppBlockingPolicy(String, CarAppBlockingPolicy, int)}
     * documentation for alternative mechanism.
     */
    @SystemApi
    @Deprecated
    public static final int FLAG_SET_POLICY_ADD = 0x2;
    /**
     * Flag for {@link #setAppBlockingPolicy(String, CarAppBlockingPolicy, int)}. When this
     * flag is set, passed policy is removed from existing policy set from the current package.
     * If none of {@link #FLAG_SET_POLICY_ADD} or {@link #FLAG_SET_POLICY_REMOVE} is set, existing
     * policy is replaced.
     *
     * @hide
     * @deprecated see the {@link #setAppBlockingPolicy(String, CarAppBlockingPolicy, int)}
     * documentation for alternative mechanism.
     */
    @SystemApi
    @Deprecated
    public static final int FLAG_SET_POLICY_REMOVE = 0x4;

    /**
     * Name of blocked activity.
     *
     * @hide
     */
    public static final String BLOCKING_INTENT_EXTRA_BLOCKED_ACTIVITY_NAME = "blocked_activity";
    /**
     * int task id of the blocked task.
     *
     * @hide
     */
    public static final String BLOCKING_INTENT_EXTRA_BLOCKED_TASK_ID = "blocked_task_id";
    /**
     * Name of root activity of blocked task.
     *
     * @hide
     */
    public static final String BLOCKING_INTENT_EXTRA_ROOT_ACTIVITY_NAME = "root_activity_name";
    /**
     * Boolean indicating whether the root activity is distraction-optimized (DO).
     * Blocking screen should show a button to restart the task if {@code true}.
     *
     * @hide
     */
    public static final String BLOCKING_INTENT_EXTRA_IS_ROOT_ACTIVITY_DO = "is_root_activity_do";

    /**
     * int display id of the blocked task.
     *
     * @hide
     */
    public static final String BLOCKING_INTENT_EXTRA_DISPLAY_ID = "display_id";

    /**
     * Represents support of all regions for driving safety.
     *
     * @hide
     */
    public static final String DRIVING_SAFETY_REGION_ALL = "android.car.drivingsafetyregion.all";

    /**
     * Metadata which Activity can use to specify the driving safety regions it is supporting.
     *
     * <p>Definition of driving safety region is car OEM specific for now and only OEM apps
     * should use this. If there are multiple regions, it should be comma separated. Not specifying
     * this means supporting all regions.
     *
     * <p>Some examples are:
     *   <meta-data android:name="android.car.drivingsafetyregions"
     *   android:value="com.android.drivingsafetyregion.1,com.android.drivingsafetyregion.2"/>
     *
     * @hide
     */
    public static final String DRIVING_SAFETY_ACTIVITY_METADATA_REGIONS =
            "android.car.drivingsafetyregions";

    /**
     * Internal error code for throwing {@code NameNotFoundException} from service.
     *
     * @hide
     */
    public static final int ERROR_CODE_NO_PACKAGE = -100;

    /**
     * Manifest metadata used to specify the minimum major and minor Car API version an app is
     * targeting.
     *
     * <p>Format is in the form {@code major:minor} or {@code major}.
     *
     * <p>For example, for {@link android.os.Build.VERSION_CODES#TIRAMISU Android 13}, it would be:
     * <code><meta-data android:name="android.car.targetCarVersion" android:value="33"/></code>
     *
     * <p>Or:
     *
     * <code><meta-data android:name="android.car.targetCarVersion" android:value="33:0"/></code>
     *
     * <p>And for {@link android.os.Build.VERSION_CODES#TIRAMISU Android 13} first update:
     *
     * <code><meta-data android:name="android.car.targetCarVersion" android:value="33:1"/></code>
     */
    public static final String MANIFEST_METADATA_TARGET_CAR_VERSION =
            "android.car.targetCarVersion";


    /** @hide */
    @IntDef(flag = true,
            value = {FLAG_SET_POLICY_WAIT_FOR_CHANGE, FLAG_SET_POLICY_ADD, FLAG_SET_POLICY_REMOVE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface SetPolicyFlags {}

    private final ICarPackageManager mService;

    /** @hide */
    public CarPackageManager(Car car, IBinder service) {
        this(car, ICarPackageManager.Stub.asInterface(service));
    }

    /** @hide */
    @VisibleForTesting
    public CarPackageManager(Car car, ICarPackageManager service) {
        super(car);
        mService = service;
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {
        // nothing to do
    }

    /**
     * Set Application blocking policy for system app. {@link #FLAG_SET_POLICY_ADD} or
     * {@link #FLAG_SET_POLICY_REMOVE} flag allows adding or removing from already set policy. When
     * none of these flags are set, it will completely replace existing policy for each package
     * specified.
     * When {@link #FLAG_SET_POLICY_WAIT_FOR_CHANGE} flag is set, this call will be blocked
     * until the policy is set to system and become effective. Otherwise, the call will start
     * changing the policy but it will be completed asynchronously and the call will return
     * without waiting for system level policy change.
     *
     * @param packageName Package name of the client. If wrong package name is passed, exception
     *        will be thrown. This name is used to update the policy.
     * @param policy
     * @param flags
     * @throws SecurityException if caller has no permission.
     * @throws IllegalArgumentException For wrong or invalid arguments.
     * @throws IllegalStateException If {@link #FLAG_SET_POLICY_WAIT_FOR_CHANGE} is set while
     *         called from main thread.
     * @hide
     * @deprecated It is no longer possible to change the app blocking policy at runtime. The first
     * choice to mark an activity as safe for driving should always be to to include
     * {@code <meta-data android:name="distractionOptimized" android:value="true"/>} in its
     * manifest. All other activities will be blocked whenever driving restrictions are required. If
     * an activity's manifest cannot be changed, then you can explicitly make an exception to its
     * behavior using the build-time XML configuration. Allow or deny specific activities by
     * changing the appropriate value ({@code R.string.activityAllowlist},
     * {@code R.string.activityDenylist}) within the
     * {@code packages/services/Car/service/res/values/config.xml} overlay.
     */
    @SystemApi
    @Deprecated
    public void setAppBlockingPolicy(
            String packageName, CarAppBlockingPolicy policy, @SetPolicyFlags int flags) {
        if ((flags & FLAG_SET_POLICY_WAIT_FOR_CHANGE) != 0
                && Looper.getMainLooper().isCurrentThread()) {
            throw new IllegalStateException(
                    "FLAG_SET_POLICY_WAIT_FOR_CHANGE cannot be used in main thread");
        }
        try {
            mService.setAppBlockingPolicy(packageName, policy, flags);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Restarts the requested task. If task with {@code taskId} does not exist, do nothing.
     *
     * <p>This requires {@code android.permission.REAL_GET_TASKS} permission.
     *
     * @hide
     */
    public void restartTask(int taskId) {
        try {
            mService.restartTask(taskId);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Check if finishing Activity will lead into safe Activity (=allowed Activity) to be shown.
     * This can be used by unsafe activity blocking Activity to check if finishing itself can
     * lead into being launched again due to unsafe activity shown. Note that checking this does not
     * guarantee that blocking will not be done as driving state can change after this call is made.
     *
     * @param activityName
     * @return true if there is a safe Activity (or car is stopped) in the back of task stack
     *         so that finishing the Activity will not trigger another Activity blocking. If
     *         the given Activity is not in foreground, then it will return true as well as
     *         finishing the Activity will not make any difference.
     *
     * @hide
     */
    @SystemApi
    public boolean isActivityBackedBySafeActivity(ComponentName activityName) {
        try {
            return mService.isActivityBackedBySafeActivity(activityName);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, false);
        }
    }

    /**
     * Enable/Disable Activity Blocking.  This is to provide an option for toggling app blocking
     * behavior for development purposes.
     * @hide
     */
    @TestApi
    public void setEnableActivityBlocking(boolean enable) {
        try {
            mService.setEnableActivityBlocking(enable);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Returns whether an activity is distraction optimized, i.e, allowed in a restricted
     * driving state.
     *
     * @param packageName the activity's {@link android.content.pm.ActivityInfo#packageName}.
     * @param className the activity's {@link android.content.pm.ActivityInfo#name}.
     * @return true if the activity is distraction optimized, false if it isn't or if the value
     *         could not be determined.
     */
    public boolean isActivityDistractionOptimized(String packageName, String className) {
        try {
            return mService.isActivityDistractionOptimized(packageName, className);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, false);
        }
    }

    /**
     * Returns whether the given {@link PendingIntent} represents an activity that is distraction
     * optimized, i.e, allowed in a restricted driving state.
     *
     * @param pendingIntent the {@link PendingIntent} to check.
     * @return true if the pending intent represents an activity that is distraction optimized,
     *         false if it isn't or if the value could not be determined.
     */
    public boolean isPendingIntentDistractionOptimized(@NonNull PendingIntent pendingIntent) {
        try {
            return mService.isPendingIntentDistractionOptimized(pendingIntent);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, false);
        }
    }

    /**
     * Check if given service is distraction optimized, i.e, allowed in a restricted
     * driving state.
     *
     * @param packageName
     * @param className
     * @return
     */
    public boolean isServiceDistractionOptimized(String packageName, String className) {
        try {
            return mService.isServiceDistractionOptimized(packageName, className);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, false);
        }
    }

    /**
     * Returns the current driving safety region of the system. It will return OEM specific regions
     * or {@link #DRIVING_SAFETY_REGION_ALL} when all regions are supported.
     *
     * <p> System's driving safety region is static and does not change until system restarts.
     *
     * @hide
     */
    @RequiresPermission(anyOf = {PERMISSION_CONTROL_APP_BLOCKING,
            Car.PERMISSION_CAR_DRIVING_STATE})
    @NonNull
    public String getCurrentDrivingSafetyRegion() {
        try {
            return mService.getCurrentDrivingSafetyRegion();
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, DRIVING_SAFETY_REGION_ALL);
        }
    }

    /**
     * Enables or disables bypassing of unsafe {@code Activity} blocking for a specific
     * {@code Activity} temporarily.
     *
     * <p> Enabling bypassing only lasts until the user stops using the car or until a user
     * switching happens. Apps like launcher may ask user's consent to bypass. Note that bypassing
     * is done for the package for all android users including the current user and user 0.
     * <p> If bypassing is disabled and if the unsafe app is in foreground with driving state, the
     * app will be immediately blocked.
     *
     * @param packageName Target package name.
     * @param activityClassName Target Activity name (in full class name).
     * @param bypass Bypass {@code Activity} blocking when true. Do not bypass anymore when false.
     * @param userId User Id where the package is installed. Even if the bypassing is enabled for
     *               all android users, the package should be available for the specified user id.
     *
     * @throws NameNotFoundException If the given package / Activity class does not exist for the
     *         user.
     *
     * @hide
     */
    @RequiresPermission(allOf = {PERMISSION_CONTROL_APP_BLOCKING,
            android.Manifest.permission.QUERY_ALL_PACKAGES})
    public void controlTemporaryActivityBlockingBypassingAsUser(String packageName,
            String activityClassName, boolean bypass, @UserIdInt int userId)
            throws NameNotFoundException {
        try {
            mService.controlOneTimeActivityBlockingBypassingAsUser(packageName, activityClassName,
                    bypass, userId);
        } catch (ServiceSpecificException e) {
            handleServiceSpecificFromCarService(e, packageName, activityClassName, userId);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Returns all supported driving safety regions for the given Activity. If the Activity supports
     * all regions, it will only include {@link #DRIVING_SAFETY_REGION_ALL}.
     *
     * <p> The permission specification requires {@code PERMISSION_CONTROL_APP_BLOCKING} and
     * {@code QUERY_ALL_PACKAGES} but this API will also work if the client has
     * {@link Car#PERMISSION_CAR_DRIVING_STATE} and {@code QUERY_ALL_PACKAGES} permissions.
     *
     * @param packageName Target package name.
     * @param activityClassName Target Activity name (in full class name).
     * @param userId Android user Id to check the package.
     *
     * @return Empty list if the Activity does not support driving safety (=no
     *         {@code distractionOptimized} metadata). Otherwise returns full list of all supported
     *         regions.
     *
     * @throws NameNotFoundException If the given package / Activity class does not exist for the
     *         user.
     *
     * @hide
     */
    @RequiresPermission(allOf = {PERMISSION_CONTROL_APP_BLOCKING,
            android.Manifest.permission.QUERY_ALL_PACKAGES})
    @NonNull
    public List<String> getSupportedDrivingSafetyRegionsForActivityAsUser(String packageName,
            String activityClassName, @UserIdInt int userId) throws NameNotFoundException {
        try {
            return mService.getSupportedDrivingSafetyRegionsForActivityAsUser(packageName,
                    activityClassName, userId);
        } catch (ServiceSpecificException e) {
            handleServiceSpecificFromCarService(e, packageName, activityClassName, userId);
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, Collections.EMPTY_LIST);
        }
        return Collections.EMPTY_LIST; // cannot reach here but the compiler complains.
    }

    /**
     * Gets the Car API version targeted by the given package (as defined by
     * {@link #MANIFEST_METADATA_TARGET_CAR_VERSION}.
     *
     * <p>If the app manifest doesn't contain the {@link #MANIFEST_METADATA_TARGET_CAR_VERSION}
     * metadata attribute or if the attribute format is invalid, the returned {@code CarVersion}
     * will be using the
     * {@link android.content.pm.ApplicationInfo#targetSdkVersion target platform version} as major
     * and {@code 0} as minor instead.
     *
     * <p><b>Note: </b>to get the target {@link CarVersion} for your own app, use
     * {@link #getTargetCarVersion()} instead.
     * @return Car API version targeted by the given package (as described above).
     *
     * @throws NameNotFoundException If the given package does not exist for the user.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Manifest.permission.QUERY_ALL_PACKAGES)
    @NonNull
    public CarVersion getTargetCarVersion(@NonNull String packageName)
            throws NameNotFoundException {
        try {
            return mService.getTargetCarVersion(packageName);
        } catch (ServiceSpecificException e) {
            Log.w(TAG, "Failed to get CarVersion for " + packageName, e);
            handleServiceSpecificFromCarService(e, packageName);
        } catch (RemoteException e) {
            e.rethrowFromSystemServer();
        }
        return null; // cannot reach here but the compiler complains.
    }

    /**
     * Gets the Car API version targeted by app (as defined by
     * {@link #MANIFEST_METADATA_TARGET_CAR_VERSION}.
     *
     * <p>If the app manifest doesn't contain the {@link #MANIFEST_METADATA_TARGET_CAR_VERSION}
     * metadata attribute or if the attribute format is invalid, the returned {@code CarVersion}
     * will be using the {@link android.content.pm.ApplicationInfo#targetSdkVersion target platform
     * version} as major and {@code 0} as minor instead.
     *
     * @return targeted Car API version (as defined above)
     */
    @NonNull
    public CarVersion getTargetCarVersion() {
        String pkgName = mCar.getContext().getPackageName();
        try {
            return mService.getSelfTargetCarVersion(pkgName);
        } catch (RemoteException e) {
            Log.w(TAG_CAR, "Car service threw exception calling getTargetCarVersion(" + pkgName
                    + ")", e);
            e.rethrowFromSystemServer();
            return null;
        }
    }

    /**
     * @return true if a package requires launching in automotive display compatibility mode.
     * false otherwise.
     *
     * @hide
     */
    @FlaggedApi(Flags.FLAG_DISPLAY_COMPATIBILITY)
    @SystemApi
    @RequiresPermission(allOf = {PERMISSION_QUERY_DISPLAY_COMPATIBILITY,
            android.Manifest.permission.QUERY_ALL_PACKAGES})
    public boolean requiresDisplayCompat(@NonNull String packageName) throws NameNotFoundException {
        if (!Flags.displayCompatibility()) {
            return false;
        }
        try {
            return mService.requiresDisplayCompat(packageName);
        } catch (ServiceSpecificException e) {
            Log.w(TAG_CAR, "Car service threw exception calling requiresDisplayCompat("
                    + packageName + ")", e);
            if (e.errorCode == ERROR_CODE_NO_PACKAGE) {
                throw new NameNotFoundException("cannot find " + packageName);
            }
            throw new RuntimeException(e);
        } catch (SecurityException e) {
            Log.w(TAG_CAR, "Car service threw exception calling requiresDisplayCompat("
                    + packageName + ")", e);
            throw e;
        } catch (RemoteException e) {
            Log.w(TAG_CAR, "Car service threw exception calling requiresDisplayCompat("
                    + packageName + ")", e);
            e.rethrowFromSystemServer();
        }
        return false;
    }

    private void handleServiceSpecificFromCarService(ServiceSpecificException e,
            String packageName) throws NameNotFoundException {
        if (e.errorCode == ERROR_CODE_NO_PACKAGE) {
            throw new NameNotFoundException(
                    "cannot find " + packageName + " for user " + Process.myUserHandle());
        }
        // don't know what this is
        throw new IllegalStateException(e);
    }

    private static void handleServiceSpecificFromCarService(ServiceSpecificException e,
            String packageName, String activityClassName, @UserIdInt int userId)
            throws NameNotFoundException {
        if (e.errorCode == ERROR_CODE_NO_PACKAGE) {
            throw new NameNotFoundException(
                    "cannot find " + packageName + "/" + activityClassName + " for user id:"
                            + userId);
        }
        // don't know what this is
        throw new IllegalStateException(e);
    }
}
