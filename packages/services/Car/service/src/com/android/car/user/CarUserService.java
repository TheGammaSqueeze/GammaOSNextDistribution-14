/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.user;

import static android.Manifest.permission.CREATE_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.MANAGE_USERS;
import static android.car.builtin.os.UserManagerHelper.USER_NULL;
import static android.car.drivingstate.CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP;

import static com.android.car.CarServiceUtils.getHandlerThread;
import static com.android.car.CarServiceUtils.isMultipleUsersOnMultipleDisplaysSupported;
import static com.android.car.CarServiceUtils.isVisibleBackgroundUsersOnDefaultDisplaySupported;
import static com.android.car.CarServiceUtils.startHomeForUserAndDisplay;
import static com.android.car.CarServiceUtils.startSystemUiForUser;
import static com.android.car.CarServiceUtils.stopSystemUiForUser;
import static com.android.car.CarServiceUtils.toIntArray;
import static com.android.car.PermissionHelper.checkHasAtLeastOnePermissionGranted;
import static com.android.car.PermissionHelper.checkHasDumpPermissionGranted;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.CarVersion;
import android.car.ICarOccupantZoneCallback;
import android.car.ICarResultReceiver;
import android.car.ICarUserService;
import android.car.PlatformVersion;
import android.car.VehicleAreaSeat;
import android.car.builtin.app.ActivityManagerHelper;
import android.car.builtin.content.pm.PackageManagerHelper;
import android.car.builtin.os.TraceHelper;
import android.car.builtin.os.UserManagerHelper;
import android.car.builtin.util.EventLogHelper;
import android.car.builtin.util.Slogf;
import android.car.builtin.util.TimingsTraceLog;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.ICarUxRestrictionsChangeListener;
import android.car.settings.CarSettings;
import android.car.user.CarUserManager;
import android.car.user.CarUserManager.UserIdentificationAssociationSetValue;
import android.car.user.CarUserManager.UserIdentificationAssociationType;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserCreationRequest;
import android.car.user.UserCreationResult;
import android.car.user.UserIdentificationAssociationResponse;
import android.car.user.UserLifecycleEventFilter;
import android.car.user.UserRemovalResult;
import android.car.user.UserStartRequest;
import android.car.user.UserStartResponse;
import android.car.user.UserStartResult;
import android.car.user.UserStopRequest;
import android.car.user.UserStopResponse;
import android.car.user.UserStopResult;
import android.car.user.UserSwitchResult;
import android.car.util.concurrent.AndroidFuture;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.hardware.automotive.vehicle.CreateUserRequest;
import android.hardware.automotive.vehicle.CreateUserStatus;
import android.hardware.automotive.vehicle.InitialUserInfoRequestType;
import android.hardware.automotive.vehicle.InitialUserInfoResponseAction;
import android.hardware.automotive.vehicle.RemoveUserRequest;
import android.hardware.automotive.vehicle.SwitchUserRequest;
import android.hardware.automotive.vehicle.SwitchUserStatus;
import android.hardware.automotive.vehicle.UserIdentificationGetRequest;
import android.hardware.automotive.vehicle.UserIdentificationResponse;
import android.hardware.automotive.vehicle.UserIdentificationSetAssociation;
import android.hardware.automotive.vehicle.UserIdentificationSetRequest;
import android.hardware.automotive.vehicle.UserInfo;
import android.hardware.automotive.vehicle.UsersInfo;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.NewUserRequest;
import android.os.NewUserResponse;
import android.os.Process;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.util.proto.ProtoOutputStream;
import android.view.Display;

import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.CarOccupantZoneService;
import com.android.car.CarServiceBase;
import com.android.car.CarServiceHelperWrapper;
import com.android.car.CarUxRestrictionsManagerService;
import com.android.car.R;
import com.android.car.am.CarActivityService;
import com.android.car.hal.HalCallback;
import com.android.car.hal.UserHalHelper;
import com.android.car.hal.UserHalService;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.ResultCallbackImpl;
import com.android.car.internal.common.CommonConstants.UserLifecycleEventType;
import com.android.car.internal.common.UserHelperLite;
import com.android.car.internal.os.CarSystemProperties;
import com.android.car.internal.util.ArrayUtils;
import com.android.car.internal.util.DebugUtils;
import com.android.car.internal.util.FunctionalUtils;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.pm.CarPackageManagerService;
import com.android.car.power.CarPowerManagementService;
import com.android.car.user.InitialUserSetter.InitialUserInfo;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * User service for cars.
 */
public final class CarUserService extends ICarUserService.Stub implements CarServiceBase {

    /**
     * When this is positive, create specified number of users and assign them to passenger zones.
     *
     * <p>If there are other users in the system, those users will be reused. This is only used
     * for non-user build for development purpose.
     */
    @VisibleForTesting
    static final String PROP_NUMBER_AUTO_POPULATED_USERS =
            "com.android.car.internal.debug.num_auto_populated_users";

    @VisibleForTesting
    static final String TAG = CarLog.tagFor(CarUserService.class);

    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    /** {@code int} extra used to represent a user id in a {@link ICarResultReceiver} response. */
    public static final String BUNDLE_USER_ID = "user.id";
    /** {@code int} extra used to represent user flags in a {@link ICarResultReceiver} response. */
    public static final String BUNDLE_USER_FLAGS = "user.flags";
    /**
     * {@code String} extra used to represent a user name in a {@link ICarResultReceiver} response.
     */
    public static final String BUNDLE_USER_NAME = "user.name";
    /**
     * {@code int} extra used to represent the user locales in a {@link ICarResultReceiver}
     * response.
     */
    public static final String BUNDLE_USER_LOCALES = "user.locales";
    /**
     * {@code int} extra used to represent the info action in a {@link ICarResultReceiver} response.
     */
    public static final String BUNDLE_INITIAL_INFO_ACTION = "initial_info.action";

    public static final String VEHICLE_HAL_NOT_SUPPORTED = "Vehicle Hal not supported.";

    public static final String HANDLER_THREAD_NAME = "UserService";

    // Constants below must match value of same constants defined by ActivityManager
    public static final int USER_OP_SUCCESS = 0;
    public static final int USER_OP_UNKNOWN_USER = -1;
    public static final int USER_OP_IS_CURRENT = -2;
    public static final int USER_OP_ERROR_IS_SYSTEM = -3;
    public static final int USER_OP_ERROR_RELATED_USERS_CANNOT_STOP = -4;

    @VisibleForTesting
    static final String ERROR_TEMPLATE_NON_ADMIN_CANNOT_CREATE_ADMIN_USERS =
            "Non-admin user %d can only create non-admin users";

    @VisibleForTesting
    static final String ERROR_TEMPLATE_INVALID_USER_TYPE_AND_FLAGS_COMBINATION =
            "Invalid combination of user type(%s) and flags (%d) for caller with restrictions";

    @VisibleForTesting
    static final String ERROR_TEMPLATE_INVALID_FLAGS_FOR_GUEST_CREATION =
            "Invalid flags %d specified when creating a guest user %s";

    @VisibleForTesting
    static final String ERROR_TEMPLATE_DISALLOW_ADD_USER =
            "Cannot create user because calling user %s has the '%s' restriction";

    /** Timeout for pre-populating users. */
    private static final int USER_CREATION_TIMEOUT_MS = 5_000;

    private static final String BG_HANDLER_THREAD_NAME = "UserService.BG";

    private final Context mContext;
    private final ActivityManager mAm;
    private final UserManager mUserManager;
    private final DevicePolicyManager mDpm;
    private final int mMaxRunningUsers;
    private final InitialUserSetter mInitialUserSetter;

    private final Object mLockUser = new Object();
    @GuardedBy("mLockUser")
    private boolean mUser0Unlocked;
    @GuardedBy("mLockUser")
    private final ArrayList<Runnable> mUser0UnlockTasks = new ArrayList<>();
    /** A queue for createUser tasks, to prevent creating multiple users concurrently. */
    @GuardedBy("mLockUser")
    private final ArrayDeque<Runnable> mCreateUserQueue;
    /**
     * Background users that will be restarted in garage mode. This list can include the
     * current foreground user but the current foreground user should not be restarted.
     */
    @GuardedBy("mLockUser")
    private final ArrayList<Integer> mBackgroundUsersToRestart = new ArrayList<>();
    /**
     * Keep the list of background users started here. This is wholly for debugging purpose.
     */
    @GuardedBy("mLockUser")
    private final ArrayList<Integer> mBackgroundUsersRestartedHere = new ArrayList<>();

    /**
     * The list of users that are starting but not visible at the time of starting excluding system
     * user or current user.
     *
     * <p>Only applicable to devices that support
     * {@link UserManager#isVisibleBackgroundUsersSupported()} background users on secondary
     * displays.
     *
     * <p>Users will be added to this list if they are not visible at the time of starting.
     * Users in this list will be removed the first time they become visible since starting.
     */
    @GuardedBy("mLockUser")
    private final ArrayList<Integer> mNotVisibleAtStartingUsers = new ArrayList<>();

    private final UserHalService mHal;

    private final HandlerThread mHandlerThread = getHandlerThread(HANDLER_THREAD_NAME);
    private final Handler mHandler;

    /** This Handler is for running background tasks which can wait. */
    @VisibleForTesting
    final Handler mBgHandler = new Handler(getHandlerThread(BG_HANDLER_THREAD_NAME).getLooper());

    /**
     * Internal listeners to be notified on new user activities events.
     *
     * <p>This collection should be accessed and manipulated by {@code mHandlerThread} only.
     */
    private final List<InternalLifecycleListener> mUserLifecycleListeners = new ArrayList<>();

    /**
     * App listeners to be notified on new user activities events.
     *
     * <p>This collection should be accessed and manipulated by {@code mHandlerThread} only.
     */
    private final ArrayMap<IBinder, AppLifecycleListener> mAppLifecycleListeners =
            new ArrayMap<>();

    /**
     * User Id for the user switch in process, if any.
     */
    @GuardedBy("mLockUser")
    private int mUserIdForUserSwitchInProcess = USER_NULL;
    /**
     * Request Id for the user switch in process, if any.
     */
    @GuardedBy("mLockUser")
    private int mRequestIdForUserSwitchInProcess;
    private final int mHalTimeoutMs = CarSystemProperties.getUserHalTimeout().orElse(5_000);

    // TODO(b/163566866): Use mSwitchGuestUserBeforeSleep for new create guest request
    private final boolean mSwitchGuestUserBeforeSleep;

    @Nullable
    @GuardedBy("mLockUser")
    private UserHandle mInitialUser;

    private ICarResultReceiver mUserSwitchUiReceiver;

    private final CarUxRestrictionsManagerService mCarUxRestrictionService;

    private final CarPackageManagerService mCarPackageManagerService;

    private final CarOccupantZoneService mCarOccupantZoneService;

    /**
     * Whether some operations - like user switch - are restricted by driving safety constraints.
     */
    @GuardedBy("mLockUser")
    private boolean mUxRestricted;

    /**
     * If {@code false}, garage mode operations (background users start at garage mode entry and
     * background users stop at garage mode exit) will be skipped. Controlled using car shell
     * command {@code adb shell set-start-bg-users-on-garage-mode [true|false]}
     * Purpose: Garage mode testing and simulation
     */
    @GuardedBy("mLockUser")
    private boolean mStartBackgroundUsersOnGarageMode = true;

    // Whether visible background users are supported on the default display, a.k.a. passenger only
    // systems.
    private final boolean mIsVisibleBackgroundUsersOnDefaultDisplaySupported;

    private final ICarUxRestrictionsChangeListener mCarUxRestrictionsChangeListener =
            new ICarUxRestrictionsChangeListener.Stub() {
        @Override
        public void onUxRestrictionsChanged(CarUxRestrictions restrictions) {
            setUxRestrictions(restrictions);
        }
    };

    /** Map used to avoid calling UserHAL when a user was removed because HAL creation failed. */
    @GuardedBy("mLockUser")
    private final SparseBooleanArray mFailedToCreateUserIds = new SparseBooleanArray(1);

    private final UserHandleHelper mUserHandleHelper;

    public CarUserService(@NonNull Context context, @NonNull UserHalService hal,
            @NonNull UserManager userManager,
            int maxRunningUsers,
            @NonNull CarUxRestrictionsManagerService uxRestrictionService,
            @NonNull CarPackageManagerService carPackageManagerService,
            @NonNull CarOccupantZoneService carOccupantZoneService) {
        this(context, hal, userManager, new UserHandleHelper(context, userManager),
                context.getSystemService(DevicePolicyManager.class),
                context.getSystemService(ActivityManager.class), maxRunningUsers,
                /* initialUserSetter= */ null, uxRestrictionService, /* handler= */ null,
                carPackageManagerService, carOccupantZoneService);
    }

    @VisibleForTesting
    CarUserService(@NonNull Context context, @NonNull UserHalService hal,
            @NonNull UserManager userManager,
            @NonNull UserHandleHelper userHandleHelper,
            @NonNull DevicePolicyManager dpm,
            @NonNull ActivityManager am,
            int maxRunningUsers,
            @Nullable InitialUserSetter initialUserSetter,
            @NonNull CarUxRestrictionsManagerService uxRestrictionService,
            @Nullable Handler handler,
            @NonNull CarPackageManagerService carPackageManagerService,
            @NonNull CarOccupantZoneService carOccupantZoneService) {
        Slogf.d(TAG, "CarUserService(): DBG=%b, user=%s", DBG, context.getUser());
        mContext = context;
        mHal = hal;
        mAm = am;
        mMaxRunningUsers = maxRunningUsers;
        mUserManager = userManager;
        mDpm = dpm;
        mUserHandleHelper = userHandleHelper;
        mHandler = handler == null ? new Handler(mHandlerThread.getLooper()) : handler;
        mInitialUserSetter =
                initialUserSetter == null ? new InitialUserSetter(context, this,
                        (u) -> setInitialUser(u), mUserHandleHelper) : initialUserSetter;
        Resources resources = context.getResources();
        mSwitchGuestUserBeforeSleep = resources.getBoolean(
                R.bool.config_switchGuestUserBeforeGoingSleep);
        mCarUxRestrictionService = uxRestrictionService;
        mCarPackageManagerService = carPackageManagerService;
        mIsVisibleBackgroundUsersOnDefaultDisplaySupported =
                isVisibleBackgroundUsersOnDefaultDisplaySupported(mUserManager);
        // Set the initial capacity of the user creation queue to avoid potential resizing.
        // The max number of running users can be a good estimate because CreateUser request comes
        // from a running user.
        mCreateUserQueue = new ArrayDeque<>(UserManagerHelper.getMaxRunningUsers(context));
        mCarOccupantZoneService = carOccupantZoneService;
    }

    /**
     * Priority init for setting boot user. Only HAL is ready at this time. Other components have
     * not done init yet.
     */
    public void priorityInit() {
        mHandler.post(() -> initBootUser(getInitialUserInfoRequestType()));
    }

    @Override
    public void init() {
        if (DBG) {
            Slogf.d(TAG, "init()");
        }

        mCarUxRestrictionService.registerUxRestrictionsChangeListener(
                mCarUxRestrictionsChangeListener, Display.DEFAULT_DISPLAY);
        // Currently mOccupantZoneCallback does the task to bring up UserPicker only when displays
        // and user assignments are changed. So it's safe not to register if visible background
        // users are disabled. But, if we'll add more functionalies in the callback, consider to
        // move the condition into the callback.
        if (mIsVisibleBackgroundUsersOnDefaultDisplaySupported) {
            mCarOccupantZoneService.registerCallback(mOccupantZoneCallback);
        }
        CarServiceHelperWrapper.getInstance().runOnConnection(() ->
                setUxRestrictions(mCarUxRestrictionService.getCurrentUxRestrictions()));
    }

    private final ICarOccupantZoneCallback mOccupantZoneCallback =
            new ICarOccupantZoneCallback.Stub() {
                @Override
                public void onOccupantZoneConfigChanged(int flags) throws RemoteException {
                    // Listen for changes to displays and user->display assignments and launch
                    // user picker when there is no user assigned to a display. This may be a no-op
                    // for certain cases, such as a user getting assigned to a display.
                    if ((flags & (CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_DISPLAY
                            | CarOccupantZoneManager.ZONE_CONFIG_CHANGE_FLAG_USER)) != 0) {
                        if (DBG) {
                            String flagString = DebugUtils.flagsToString(
                                    CarOccupantZoneManager.class, "ZONE_CONFIG_CHANGE_FLAG_",
                                    flags);
                            Slogf.d(TAG, "onOccupantZoneConfigChanged: zone change flag=%s",
                                    flagString);
                        }
                        startUserPicker();
                    }
                }
            };

    @Override
    public void release() {
        if (DBG) {
            Slogf.d(TAG, "release()");
        }

        mCarUxRestrictionService
                .unregisterUxRestrictionsChangeListener(mCarUxRestrictionsChangeListener);

        mCarOccupantZoneService.unregisterCallback(mOccupantZoneCallback);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(@NonNull IndentingPrintWriter writer) {
        checkHasDumpPermissionGranted(mContext, "dump()");

        writer.println("*CarUserService*");
        writer.printf("DBG=%b\n", DBG);
        handleDumpListeners(writer);
        writer.printf("User switch UI receiver %s\n", mUserSwitchUiReceiver);
        synchronized (mLockUser) {
            writer.println("User0Unlocked: " + mUser0Unlocked);
            writer.println("BackgroundUsersToRestart: " + mBackgroundUsersToRestart);
            writer.println("BackgroundUsersRestarted: " + mBackgroundUsersRestartedHere);
            if (mFailedToCreateUserIds.size() > 0) {
                writer.println("FailedToCreateUserIds: " + mFailedToCreateUserIds);
            }
            writer.printf("Is UX restricted: %b\n", mUxRestricted);
            writer.printf("Start Background Users On Garage Mode=%s\n",
                    mStartBackgroundUsersOnGarageMode);
            writer.printf("Initial user: %s\n", mInitialUser);
            writer.println("Users not visible at starting: " + mNotVisibleAtStartingUsers);
            writer.println("createUser queue size: " + mCreateUserQueue.size());
        }
        writer.println("SwitchGuestUserBeforeSleep: " + mSwitchGuestUserBeforeSleep);

        writer.println("MaxRunningUsers: " + mMaxRunningUsers);
        writer.printf("User HAL: supported=%b, timeout=%dms\n", isUserHalSupported(),
                mHalTimeoutMs);

        writer.println("Relevant overlayable properties");
        Resources res = mContext.getResources();
        writer.increaseIndent();
        writer.printf("owner_name=%s\n", UserManagerHelper.getDefaultUserName(mContext));
        writer.printf("default_guest_name=%s\n", res.getString(R.string.default_guest_name));
        writer.printf("config_multiuserMaxRunningUsers=%d\n",
                UserManagerHelper.getMaxRunningUsers(mContext));
        writer.decreaseIndent();
        writer.printf("User switch in process=%d\n", mUserIdForUserSwitchInProcess);
        writer.printf("Request Id for the user switch in process=%d\n ",
                    mRequestIdForUserSwitchInProcess);
        writer.printf("System UI package name=%s\n",
                PackageManagerHelper.getSystemUiPackageName(mContext));

        writer.println("Relevant Global settings");
        writer.increaseIndent();
        dumpGlobalProperty(writer, CarSettings.Global.LAST_ACTIVE_USER_ID);
        dumpGlobalProperty(writer, CarSettings.Global.LAST_ACTIVE_PERSISTENT_USER_ID);
        writer.decreaseIndent();

        mInitialUserSetter.dump(writer);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {}

    // TODO(b/248608281): clean up.
    @Nullable
    private OccupantZoneInfo getOccupantZoneForDisplayId(int displayId) {
        List<OccupantZoneInfo> occupantZoneInfos = mCarOccupantZoneService.getAllOccupantZones();
        for (int index = 0; index < occupantZoneInfos.size(); index++) {
            OccupantZoneInfo occupantZoneInfo = occupantZoneInfos.get(index);
            int[] displays = mCarOccupantZoneService.getAllDisplaysForOccupantZone(
                    occupantZoneInfo.zoneId);
            for (int displayIndex = 0; displayIndex < displays.length; displayIndex++) {
                if (displays[displayIndex] == displayId) {
                    return occupantZoneInfo;
                }
            }
        }
        return null;
    }

    private void dumpGlobalProperty(IndentingPrintWriter writer, String property) {
        String value = Settings.Global.getString(mContext.getContentResolver(), property);
        writer.printf("%s=%s\n", property, value);
    }

    private void handleDumpListeners(IndentingPrintWriter writer) {
        writer.increaseIndent();
        CountDownLatch latch = new CountDownLatch(1);
        mHandler.post(() -> {
            handleDumpServiceLifecycleListeners(writer);
            handleDumpAppLifecycleListeners(writer);
            latch.countDown();
        });
        int timeout = 5;
        try {
            if (!latch.await(timeout, TimeUnit.SECONDS)) {
                writer.printf("Handler thread didn't respond in %ds when dumping listeners\n",
                        timeout);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writer.println("Interrupted waiting for handler thread to dump app and user listeners");
        }
        writer.decreaseIndent();
    }

    private void handleDumpServiceLifecycleListeners(PrintWriter writer) {
        if (mUserLifecycleListeners.isEmpty()) {
            writer.println("No lifecycle listeners for internal services");
            return;
        }
        int size = mUserLifecycleListeners.size();
        writer.printf("%d lifecycle listener%s for services\n", size, size == 1 ? "" : "s");
        String indent = "  ";
        for (int i = 0; i < size; i++) {
            InternalLifecycleListener listener = mUserLifecycleListeners.get(i);
            writer.printf("%slistener=%s, filter=%s\n", indent,
                    FunctionalUtils.getLambdaName(listener.listener), listener.filter);
        }
    }

    private void handleDumpAppLifecycleListeners(IndentingPrintWriter writer) {
        int size = mAppLifecycleListeners.size();
        if (size == 0) {
            writer.println("No lifecycle listeners for apps");
            return;
        }
        writer.printf("%d lifecycle listener%s for apps\n", size, size == 1 ? "" : "s");
        writer.increaseIndent();
        for (int i = 0; i < size; i++) {
            mAppLifecycleListeners.valueAt(i).dump(writer);
        }
        writer.decreaseIndent();
    }

    @Override
    public void setLifecycleListenerForApp(String packageName, UserLifecycleEventFilter filter,
            ICarResultReceiver receiver) {
        int uid = Binder.getCallingUid();
        EventLogHelper.writeCarUserServiceSetLifecycleListener(uid, packageName);
        checkInteractAcrossUsersPermission("setLifecycleListenerForApp-" + uid + "-" + packageName);

        IBinder receiverBinder = receiver.asBinder();
        mHandler.post(() -> {
            AppLifecycleListener listener = mAppLifecycleListeners.get(receiverBinder);
            if (listener == null) {
                listener = new AppLifecycleListener(uid, packageName, receiver, filter,
                        (l) -> onListenerDeath(l));
                Slogf.d(TAG, "Adding %s (using binder %s) with filter %s",
                        listener, receiverBinder, filter);
                mAppLifecycleListeners.put(receiverBinder, listener);
            } else {
                // Same listener already exists. Only add the additional filter.
                Slogf.d(TAG, "Adding filter %s to the listener %s (for binder %s)", filter,
                        listener, receiverBinder);
                listener.addFilter(filter);
            }
        });
    }

    private void onListenerDeath(AppLifecycleListener listener) {
        Slogf.i(TAG, "Removing listener %s on binder death", listener);
        mHandler.post(() -> mAppLifecycleListeners.remove(listener.receiver.asBinder()));
    }

    @Override
    public void resetLifecycleListenerForApp(ICarResultReceiver receiver) {
        int uid = Binder.getCallingUid();
        checkInteractAcrossUsersPermission("resetLifecycleListenerForApp-" + uid);
        IBinder receiverBinder = receiver.asBinder();
        mHandler.post(() -> {
            AppLifecycleListener listener = mAppLifecycleListeners.get(receiverBinder);
            if (listener == null) {
                Slogf.e(TAG, "resetLifecycleListenerForApp(uid=%d): no listener for receiver", uid);
                return;
            }
            if (listener.uid != uid) {
                Slogf.e(TAG, "resetLifecycleListenerForApp(): uid mismatch (called by %d) for "
                        + "listener %s", uid, listener);
            }
            EventLogHelper.writeCarUserServiceResetLifecycleListener(uid,
                    listener.packageName);
            if (DBG) {
                Slogf.d(TAG, "Removing %s (using binder %s)", listener, receiverBinder);
            }
            mAppLifecycleListeners.remove(receiverBinder);

            listener.onDestroy();
        });
    }

    /**
     * Gets the initial foreground user after the device boots or resumes from suspension.
     *
     * <p>When the OEM supports the User HAL, the initial user won't be available until the HAL
     * returns the initial value to {@code CarService} - if HAL takes too long or times out, this
     * method returns {@code null}.
     *
     * <p>If the HAL eventually times out, {@code CarService} will fallback to its default behavior
     * (like switching to the last active user), and this method will return the result of such
     * operation.
     *
     * <p>Notice that if {@code CarService} crashes, subsequent calls to this method will return
     * {@code null}.
     *
     * @hide
     */
    @Nullable
    public UserHandle getInitialUser() {
        checkInteractAcrossUsersPermission("getInitialUser");
        synchronized (mLockUser) {
            return mInitialUser;
        }
    }

    /**
     * Sets the initial foreground user after the device boots or resumes from suspension.
     */
    public void setInitialUser(@Nullable UserHandle user) {
        EventLogHelper
                .writeCarUserServiceSetInitialUser(user == null ? USER_NULL : user.getIdentifier());
        synchronized (mLockUser) {
            mInitialUser = user;
        }
        if (user == null) {
            // This mean InitialUserSetter failed and could not fallback, so the initial user was
            // not switched (and most likely is SYSTEM_USER).
            // TODO(b/153104378): should we set it to ActivityManager.getCurrentUser() instead?
            Slogf.wtf(TAG, "Initial user set to null");
            return;
        }
        sendInitialUserToSystemServer(user);
    }

    /**
     * Sets the initial foreground user after car service is crashed and reconnected.
     */
    public void setInitialUserFromSystemServer(@Nullable UserHandle user) {
        if (user == null || user.getIdentifier() == USER_NULL) {
            Slogf.e(TAG,
                    "setInitialUserFromSystemServer: Not setting initial user as user is NULL ");
            return;
        }

        if (DBG) {
            Slogf.d(TAG, "setInitialUserFromSystemServer: initial User: %s", user);
        }

        synchronized (mLockUser) {
            mInitialUser = user;
        }
    }

    private void sendInitialUserToSystemServer(UserHandle user) {
        CarServiceHelperWrapper.getInstance().sendInitialUser(user);
    }

    private void initResumeReplaceGuest() {
        int currentUserId = ActivityManager.getCurrentUser();
        UserHandle currentUser = mUserHandleHelper.getExistingUserHandle(currentUserId);

        if (currentUser == null) {
            Slogf.wtf(TAG, "Current user (%d) doesn't exist", currentUserId);
        }

        if (!mInitialUserSetter.canReplaceGuestUser(currentUser)) return; // Not a guest

        InitialUserInfo info =
                new InitialUserSetter.Builder(InitialUserSetter.TYPE_REPLACE_GUEST).build();

        mInitialUserSetter.set(info);
    }

    /**
     * Calls to switch user at the power suspend.
     *
     * <p><b>Note:</b> Should be used only by {@link CarPowerManagementService}
     *
     */
    public void onSuspend() {
        if (DBG) {
            Slogf.d(TAG, "onSuspend called.");
        }

        if (mSwitchGuestUserBeforeSleep) {
            initResumeReplaceGuest();
        }
    }

    /**
     * Calls to switch user at the power resume.
     *
     * <p>
     * <b>Note:</b> Should be used only by {@link CarPowerManagementService}
     *
     */
    public void onResume() {
        if (DBG) {
            Slogf.d(TAG, "onResume called.");
        }

        mHandler.post(() -> initBootUser(InitialUserInfoRequestType.RESUME));
    }

    private void initBootUser(int requestType) {
        boolean replaceGuest =
                requestType == InitialUserInfoRequestType.RESUME && !mSwitchGuestUserBeforeSleep;
        checkManageUsersPermission("startInitialUser");

        // TODO(b/266473227): Fix isUserHalSupported() for Multi User No driver.
        if (!isUserHalSupported() || mIsVisibleBackgroundUsersOnDefaultDisplaySupported) {
            fallbackToDefaultInitialUserBehavior(/* userLocales= */ null, replaceGuest,
                    /* supportsOverrideUserIdProperty= */ true, requestType);
            EventLogHelper.writeCarUserServiceInitialUserInfoReqComplete(requestType);
            return;
        }

        UsersInfo usersInfo = UserHalHelper.newUsersInfo(mUserManager, mUserHandleHelper);
        EventLogHelper.writeCarUserServiceInitialUserInfoReq(requestType,
                mHalTimeoutMs, usersInfo.currentUser.userId, usersInfo.currentUser.flags,
                usersInfo.numberUsers);

        mHal.getInitialUserInfo(requestType, mHalTimeoutMs, usersInfo, (status, resp) -> {
            if (resp != null) {
                EventLogHelper.writeCarUserServiceInitialUserInfoResp(
                        status, resp.action, resp.userToSwitchOrCreate.userId,
                        resp.userToSwitchOrCreate.flags, resp.userNameToCreate, resp.userLocales);

                String userLocales = resp.userLocales;
                InitialUserInfo info;
                switch(resp.action) {
                    case InitialUserInfoResponseAction.SWITCH:
                        int userId = resp.userToSwitchOrCreate.userId;
                        if (userId <= 0) {
                            Slogf.w(TAG, "invalid (or missing) user id sent by HAL: %d", userId);
                            fallbackToDefaultInitialUserBehavior(userLocales, replaceGuest,
                                    /* supportsOverrideUserIdProperty= */ false, requestType);
                            break;
                        }
                        info = new InitialUserSetter.Builder(InitialUserSetter.TYPE_SWITCH)
                                .setRequestType(requestType)
                                .setUserLocales(userLocales)
                                .setSwitchUserId(userId)
                                .setReplaceGuest(replaceGuest)
                                .build();
                        mInitialUserSetter.set(info);
                        break;

                    case InitialUserInfoResponseAction.CREATE:
                        int halFlags = resp.userToSwitchOrCreate.flags;
                        String userName =  resp.userNameToCreate;
                        info = new InitialUserSetter.Builder(InitialUserSetter.TYPE_CREATE)
                                .setRequestType(requestType)
                                .setUserLocales(userLocales)
                                .setNewUserName(userName)
                                .setNewUserFlags(halFlags)
                                .build();
                        mInitialUserSetter.set(info);
                        break;

                    case InitialUserInfoResponseAction.DEFAULT:
                        fallbackToDefaultInitialUserBehavior(userLocales, replaceGuest,
                                /* supportsOverrideUserIdProperty= */ false, requestType);
                        break;
                    default:
                        Slogf.w(TAG, "invalid response action on %s", resp);
                        fallbackToDefaultInitialUserBehavior(/* userLocales= */ null, replaceGuest,
                                /* supportsOverrideUserIdProperty= */ false, requestType);
                        break;

                }
            } else {
                EventLogHelper.writeCarUserServiceInitialUserInfoResp(status, /* action= */ 0,
                        /* userId= */ 0, /* flags= */ 0,
                        /* safeName= */ "", /* userLocales= */ "");
                fallbackToDefaultInitialUserBehavior(/* user locale */ null, replaceGuest,
                        /* supportsOverrideUserIdProperty= */ false, requestType);
            }
            EventLogHelper.writeCarUserServiceInitialUserInfoReqComplete(requestType);
        });
    }

    private void fallbackToDefaultInitialUserBehavior(String userLocales, boolean replaceGuest,
            boolean supportsOverrideUserIdProperty, int requestType) {
        InitialUserInfo info = new InitialUserSetter.Builder(
                InitialUserSetter.TYPE_DEFAULT_BEHAVIOR)
                .setRequestType(requestType)
                .setUserLocales(userLocales)
                .setReplaceGuest(replaceGuest)
                .setSupportsOverrideUserIdProperty(supportsOverrideUserIdProperty)
                .build();
        mInitialUserSetter.set(info);
    }

    @VisibleForTesting
    int getInitialUserInfoRequestType() {
        if (!mInitialUserSetter.hasInitialUser()) {
            return InitialUserInfoRequestType.FIRST_BOOT;
        }
        if (mContext.getPackageManager().isDeviceUpgrading()) {
            return InitialUserInfoRequestType.FIRST_BOOT_AFTER_OTA;
        }
        return InitialUserInfoRequestType.COLD_BOOT;
    }

    private void setUxRestrictions(@Nullable CarUxRestrictions restrictions) {
        boolean restricted = restrictions != null
                && (restrictions.getActiveRestrictions() & UX_RESTRICTIONS_NO_SETUP)
                        == UX_RESTRICTIONS_NO_SETUP;
        if (DBG) {
            Slogf.d(TAG, "setUxRestrictions(%s): restricted=%b", restrictions, restricted);
        } else {
            Slogf.i(TAG, "Setting UX restricted to %b", restricted);
        }

        synchronized (mLockUser) {
            mUxRestricted = restricted;
        }
        CarServiceHelperWrapper.getInstance().setSafetyMode(!restricted);
    }

    private boolean isUxRestricted() {
        synchronized (mLockUser) {
            return mUxRestricted;
        }
    }

    /**
     * Calls the {@link UserHalService} and {@link ActivityManager} for user switch.
     *
     * <p>
     * When everything works well, the workflow is:
     * <ol>
     *   <li> {@link UserHalService} is called for HAL user switch with ANDROID_SWITCH request
     *   type, current user id, target user id, and a callback.
     *   <li> HAL called back with SUCCESS.
     *   <li> {@link ActivityManager} is called for Android user switch.
     *   <li> Receiver would receive {@code STATUS_SUCCESSFUL}.
     *   <li> Once user is unlocked, {@link UserHalService} is again called with ANDROID_POST_SWITCH
     *   request type, current user id, and target user id. In this case, the current and target
     *   user IDs would be same.
     * <ol/>
     *
     * <p>
     * Corner cases:
     * <ul>
     *   <li> If target user is already the current user, no user switch is performed and receiver
     *   would receive {@code STATUS_OK_USER_ALREADY_IN_FOREGROUND} right away.
     *   <li> If HAL user switch call fails, no Android user switch. Receiver would receive
     *   {@code STATUS_HAL_INTERNAL_FAILURE}.
     *   <li> If HAL user switch call is successful, but android user switch call fails,
     *   {@link UserHalService} is again called with request type POST_SWITCH, current user id, and
     *   target user id, but in this case the current and target user IDs would be different.
     *   <li> If another user switch request for the same target user is received while previous
     *   request is in process, receiver would receive
     *   {@code STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO} for the new request right away.
     *   <li> If a user switch request is received while another user switch request for different
     *   target user is in process, the previous request would be abandoned and new request will be
     *   processed. No POST_SWITCH would be sent for the previous request.
     * <ul/>
     *
     * @param targetUserId - target user Id
     * @param timeoutMs - timeout for HAL to wait
     * @param receiver - receiver for the results
     */
    @Override
    public void switchUser(@UserIdInt int targetUserId, int timeoutMs,
            @NonNull ResultCallbackImpl<UserSwitchResult> callback, boolean ignoreUxRestriction) {
        EventLogHelper.writeCarUserServiceSwitchUserReq(targetUserId, timeoutMs);
        checkManageOrCreateUsersPermission("switchUser");
        Objects.requireNonNull(callback);
        UserHandle targetUser = mUserHandleHelper.getExistingUserHandle(targetUserId);
        if (targetUser == null) {
            sendUserSwitchResult(callback, /* isLogout= */ false,
                    UserSwitchResult.STATUS_INVALID_REQUEST);
            return;
        }
        if (mUserManager.getUserSwitchability() != UserManager.SWITCHABILITY_STATUS_OK) {
            sendUserSwitchResult(callback, /* isLogout= */ false,
                    UserSwitchResult.STATUS_NOT_SWITCHABLE);
            return;
        }
        mHandler.post(() -> handleSwitchUser(targetUser, timeoutMs, callback,
                /* isLogout= */ false, ignoreUxRestriction));
    }

    @Override
    public void logoutUser(int timeoutMs, @NonNull ResultCallbackImpl<UserSwitchResult> callback) {
        checkManageOrCreateUsersPermission("logoutUser");
        Objects.requireNonNull(callback);

        UserHandle targetUser = mDpm.getLogoutUser();
        int logoutUserId = targetUser == null ? UserManagerHelper.USER_NULL
                : targetUser.getIdentifier();
        EventLogHelper.writeCarUserServiceLogoutUserReq(logoutUserId, timeoutMs);

        if (targetUser == null) {
            Slogf.w(TAG, "logoutUser() called when current user is not logged in");
            sendUserSwitchResult(callback, /* isLogout= */ true,
                    UserSwitchResult.STATUS_NOT_LOGGED_IN);
            return;
        }

        mHandler.post(() -> handleSwitchUser(targetUser, timeoutMs, callback,
                /* isLogout= */ true,  /* ignoreUxRestriction= */ false));
    }

    private void handleSwitchUser(@NonNull UserHandle targetUser, int timeoutMs,
            @NonNull ResultCallbackImpl<UserSwitchResult> callback, boolean isLogout,
            boolean ignoreUxRestriction) {
        int currentUser = ActivityManager.getCurrentUser();
        int targetUserId = targetUser.getIdentifier();
        if (currentUser == targetUserId) {
            if (DBG) {
                Slogf.d(TAG, "Current user is same as requested target user: %d", targetUserId);
            }
            int resultStatus = UserSwitchResult.STATUS_OK_USER_ALREADY_IN_FOREGROUND;
            sendUserSwitchResult(callback, isLogout, resultStatus);
            return;
        }

        if (!ignoreUxRestriction && isUxRestricted()) {
            sendUserSwitchResult(callback, isLogout,
                    UserSwitchResult.STATUS_UX_RESTRICTION_FAILURE);
            return;
        }

        // If User Hal is not supported, just android user switch.
        if (!isUserHalSupported()) {
            int result = switchOrLogoutUser(targetUser, isLogout);
            if (result == UserManager.USER_OPERATION_SUCCESS) {
                sendUserSwitchResult(callback, isLogout, UserSwitchResult.STATUS_SUCCESSFUL);
                return;
            }
            sendUserSwitchResult(callback, isLogout, HalCallback.STATUS_INVALID,
                    UserSwitchResult.STATUS_ANDROID_FAILURE, result, /* errorMessage= */ null);
            return;
        }

        synchronized (mLockUser) {
            if (DBG) {
                Slogf.d(TAG, "handleSwitchUser(%d): currentuser=%s, isLogout=%b, "
                        + "mUserIdForUserSwitchInProcess=%b", targetUserId, currentUser, isLogout,
                        mUserIdForUserSwitchInProcess);
            }

            // If there is another request for the same target user, return another request in
            // process, else {@link mUserIdForUserSwitchInProcess} is updated and {@link
            // mRequestIdForUserSwitchInProcess} is reset. It is possible that there may be another
            // user switch request in process for different target user, but that request is now
            // ignored.
            if (mUserIdForUserSwitchInProcess == targetUserId) {
                Slogf.w(TAG, "switchUser(%s): another user switch request (id=%d) in process for "
                        + "that user", targetUser, mRequestIdForUserSwitchInProcess);
                int resultStatus = UserSwitchResult.STATUS_TARGET_USER_ALREADY_BEING_SWITCHED_TO;
                sendUserSwitchResult(callback, isLogout, resultStatus);
                return;
            } else {
                if (DBG) {
                    Slogf.d(TAG, "Changing mUserIdForUserSwitchInProcess from %d to %d",
                            mUserIdForUserSwitchInProcess, targetUserId);
                }
                mUserIdForUserSwitchInProcess = targetUserId;
                mRequestIdForUserSwitchInProcess = 0;
            }
        }

        UsersInfo usersInfo = UserHalHelper.newUsersInfo(mUserManager, mUserHandleHelper);
        SwitchUserRequest request = createUserSwitchRequest(targetUserId, usersInfo);

        if (DBG) {
            Slogf.d(TAG, "calling mHal.switchUser(%s)", request);
        }
        mHal.switchUser(request, timeoutMs, (halCallbackStatus, resp) -> {
            if (DBG) {
                Slogf.d(TAG, "switch response: status=%s, resp=%s",
                        Integer.toString(halCallbackStatus), resp);
            }

            int resultStatus = UserSwitchResult.STATUS_HAL_INTERNAL_FAILURE;
            Integer androidFailureStatus = null;

            synchronized (mLockUser) {
                if (halCallbackStatus != HalCallback.STATUS_OK || resp == null) {
                    Slogf.w(TAG, "invalid callback status (%s) or null response (%s)",
                            Integer.toString(halCallbackStatus), resp);
                    sendUserSwitchResult(callback, isLogout, resultStatus);
                    mUserIdForUserSwitchInProcess = USER_NULL;
                    return;
                }

                if (mUserIdForUserSwitchInProcess != targetUserId) {
                    // Another user switch request received while HAL responded. No need to
                    // process this request further
                    Slogf.w(TAG, "Another user switch received while HAL responsed. Request"
                            + " abandoned for user %d. Current user in process: %d", targetUserId,
                            mUserIdForUserSwitchInProcess);
                    resultStatus =
                            UserSwitchResult.STATUS_TARGET_USER_ABANDONED_DUE_TO_A_NEW_REQUEST;
                    sendUserSwitchResult(callback, isLogout, resultStatus);
                    mUserIdForUserSwitchInProcess = USER_NULL;
                    return;
                }

                switch (resp.status) {
                    case SwitchUserStatus.SUCCESS:
                        int result = switchOrLogoutUser(targetUser, isLogout);
                        if (result == UserManager.USER_OPERATION_SUCCESS) {
                            sendUserSwitchUiCallback(targetUserId);
                            resultStatus = UserSwitchResult.STATUS_SUCCESSFUL;
                            mRequestIdForUserSwitchInProcess = resp.requestId;
                        } else {
                            resultStatus = UserSwitchResult.STATUS_ANDROID_FAILURE;
                            if (isLogout) {
                                // Send internal result (there's no point on sending for regular
                                // switch as it will always be UNKNOWN_ERROR
                                androidFailureStatus = result;
                            }
                            postSwitchHalResponse(resp.requestId, targetUserId);
                        }
                        break;
                    case SwitchUserStatus.FAILURE:
                        // HAL failed to switch user
                        resultStatus = UserSwitchResult.STATUS_HAL_FAILURE;
                        break;
                    default:
                        // Shouldn't happen because UserHalService validates the status
                        Slogf.wtf(TAG, "Received invalid user switch status from HAL: %s", resp);
                }

                if (mRequestIdForUserSwitchInProcess == 0) {
                    mUserIdForUserSwitchInProcess = USER_NULL;
                }
            }
            sendUserSwitchResult(callback, isLogout, halCallbackStatus, resultStatus,
                    androidFailureStatus, resp.errorMessage);
        });
    }

    private int switchOrLogoutUser(UserHandle targetUser, boolean isLogout) {
        if (isLogout) {
            int result = mDpm.logoutUser();
            if (result != UserManager.USER_OPERATION_SUCCESS) {
                Slogf.w(TAG, "failed to logout to user %s using DPM: result=%s", targetUser,
                        userOperationErrorToString(result));
            }
            return result;
        }

        if (!mAm.switchUser(targetUser)) {
            Slogf.w(TAG, "failed to switch to user %s using AM", targetUser);
            return UserManager.USER_OPERATION_ERROR_UNKNOWN;
        }

        return UserManager.USER_OPERATION_SUCCESS;
    }

    @Override
    public void removeUser(@UserIdInt int userId, ResultCallbackImpl<UserRemovalResult> callback) {
        removeUser(userId, /* hasCallerRestrictions= */ false, callback);
    }

    /**
     * Internal implementation of {@code removeUser()}, which is used by both
     * {@code ICarUserService} and {@code ICarDevicePolicyService}.
     *
     * @param userId user to be removed
     * @param hasCallerRestrictions when {@code true}, if the caller user is not an admin, it can
     * only remove itself.
     * @param callback to post results
     */
    public void removeUser(@UserIdInt int userId, boolean hasCallerRestrictions,
            ResultCallbackImpl<UserRemovalResult> callback) {
        checkManageOrCreateUsersPermission("removeUser");
        EventLogHelper.writeCarUserServiceRemoveUserReq(userId,
                hasCallerRestrictions ? 1 : 0);

        if (hasCallerRestrictions) {
            // Restrictions: non-admin user can only remove itself, admins have no restrictions
            int callingUserId = Binder.getCallingUserHandle().getIdentifier();
            if (!mUserHandleHelper.isAdminUser(UserHandle.of(callingUserId))
                    && userId != callingUserId) {
                throw new SecurityException("Non-admin user " + callingUserId
                        + " can only remove itself");
            }
        }
        mHandler.post(() -> handleRemoveUser(userId, hasCallerRestrictions, callback));
    }

    private void handleRemoveUser(@UserIdInt int userId, boolean hasCallerRestrictions,
            ResultCallbackImpl<UserRemovalResult> callback) {
        UserHandle user = mUserHandleHelper.getExistingUserHandle(userId);
        if (user == null) {
            sendUserRemovalResult(userId, UserRemovalResult.STATUS_USER_DOES_NOT_EXIST, callback);
            return;
        }
        UserInfo halUser = new UserInfo();
        halUser.userId = user.getIdentifier();
        halUser.flags = UserHalHelper.convertFlags(mUserHandleHelper, user);
        UsersInfo usersInfo = UserHalHelper.newUsersInfo(mUserManager, mUserHandleHelper);

        // check if the user is last admin user.
        boolean isLastAdmin = false;
        if (UserHalHelper.isAdmin(halUser.flags)) {
            int size = usersInfo.existingUsers.length;
            int totalAdminUsers = 0;
            for (int i = 0; i < size; i++) {
                if (UserHalHelper.isAdmin(usersInfo.existingUsers[i].flags)) {
                    totalAdminUsers++;
                }
            }
            if (totalAdminUsers == 1) {
                isLastAdmin = true;
            }
        }

        // First remove user from android and then remove from HAL because HAL remove user is one
        // way call.
        // TODO(b/170887769): rename hasCallerRestrictions to fromCarDevicePolicyManager (or use an
        // int / enum to indicate if it's called from CarUserManager or CarDevicePolicyManager), as
        // it's counter-intuitive that it's "allowed even when disallowed" when it
        // "has caller restrictions"
        boolean overrideDevicePolicy = hasCallerRestrictions;
        int result = mUserManager.removeUserWhenPossible(user, overrideDevicePolicy);
        if (!UserManager.isRemoveResultSuccessful(result)) {
            sendUserRemovalResult(userId, UserRemovalResult.STATUS_ANDROID_FAILURE, callback);
            return;
        }

        if (isLastAdmin) {
            Slogf.w(TAG, "Last admin user successfully removed or set ephemeral. User Id: %d",
                    userId);
        }

        switch (result) {
            case UserManager.REMOVE_RESULT_REMOVED:
            case UserManager.REMOVE_RESULT_ALREADY_BEING_REMOVED:
                sendUserRemovalResult(userId,
                        isLastAdmin ? UserRemovalResult.STATUS_SUCCESSFUL_LAST_ADMIN_REMOVED
                                : UserRemovalResult.STATUS_SUCCESSFUL, callback);
                break;
            case UserManager.REMOVE_RESULT_DEFERRED:
                sendUserRemovalResult(userId,
                        isLastAdmin ? UserRemovalResult.STATUS_SUCCESSFUL_LAST_ADMIN_SET_EPHEMERAL
                                : UserRemovalResult.STATUS_SUCCESSFUL_SET_EPHEMERAL, callback);
                break;
            default:
                sendUserRemovalResult(userId, UserRemovalResult.STATUS_ANDROID_FAILURE, callback);
        }
    }

    /**
     * Should be called by {@code ICarImpl} only.
     */
    public void onUserRemoved(@NonNull UserHandle user) {
        if (DBG) {
            Slogf.d(TAG, "onUserRemoved: %s", user);
        }
        notifyHalUserRemoved(user);
    }

    private void notifyHalUserRemoved(@NonNull UserHandle user) {
        if (!isUserHalSupported()) return;

        if (user == null) {
            Slogf.wtf(TAG, "notifyHalUserRemoved() called for null user");
            return;
        }

        int userId = user.getIdentifier();

        if (userId == USER_NULL) {
            Slogf.wtf(TAG, "notifyHalUserRemoved() called for USER_NULL");
            return;
        }

        synchronized (mLockUser) {
            if (mFailedToCreateUserIds.get(userId)) {
                if (DBG) {
                    Slogf.d(TAG, "notifyHalUserRemoved(): skipping user %d", userId);
                }
                mFailedToCreateUserIds.delete(userId);
                return;
            }
        }

        UserInfo halUser = new UserInfo();
        halUser.userId = userId;
        halUser.flags = UserHalHelper.convertFlags(mUserHandleHelper, user);

        RemoveUserRequest request = UserHalHelper.emptyRemoveUserRequest();
        request.removedUserInfo = halUser;
        request.usersInfo = UserHalHelper.newUsersInfo(mUserManager, mUserHandleHelper);
        mHal.removeUser(request);
    }

    private void sendUserRemovalResult(@UserIdInt int userId, @UserRemovalResult.Status int result,
            ResultCallbackImpl<UserRemovalResult> callback) {
        EventLogHelper.writeCarUserServiceRemoveUserResp(userId, result);
        callback.complete(new UserRemovalResult(result));
    }

    private void sendUserSwitchUiCallback(@UserIdInt int targetUserId) {
        if (mUserSwitchUiReceiver == null) {
            Slogf.w(TAG, "No User switch UI receiver.");
            return;
        }

        EventLogHelper.writeCarUserServiceSwitchUserUiReq(targetUserId);
        try {
            mUserSwitchUiReceiver.send(targetUserId, null);
        } catch (RemoteException e) {
            Slogf.e(TAG, "Error calling user switch UI receiver.", e);
        }
    }

    /**
     * Used to create the initial user, even when it's disallowed by {@code DevicePolicyManager}.
     */
    @Nullable
    UserHandle createUserEvenWhenDisallowed(@Nullable String name, @NonNull String userType,
            int flags) {
        return CarServiceHelperWrapper.getInstance().createUserEvenWhenDisallowed(name, userType,
                flags);
    }

    /**
     * Same as {@link UserManager#isUserVisible()}, but passing the user id.
     */
    public boolean isUserVisible(@UserIdInt int userId) {
        Set<UserHandle> visibleUsers = mUserManager.getVisibleUsers();
        return visibleUsers.contains(UserHandle.of(userId));
    }

    // TODO(b/244370727): Remove once the lifecycle event callbacks provide the display id.
    /**
     * Same as {@link UserManager#getMainDisplayIdAssignedToUser()}.
     */
    public int getMainDisplayAssignedToUser(int userId) {
        return CarServiceHelperWrapper.getInstance().getMainDisplayAssignedToUser(userId);
    }

    @Override
    public void createUser(@NonNull UserCreationRequest userCreationRequest, int timeoutMs,
            ResultCallbackImpl<UserCreationResult> callback) {
        String name = userCreationRequest.getName();
        String userType = userCreationRequest.isGuest() ? UserManager.USER_TYPE_FULL_GUEST
                : UserManager.USER_TYPE_FULL_SECONDARY;
        int flags = 0;
        flags |= userCreationRequest.isAdmin() ? UserManagerHelper.FLAG_ADMIN : 0;
        flags |= userCreationRequest.isEphemeral() ? UserManagerHelper.FLAG_EPHEMERAL : 0;

        createUser(name, userType, flags, timeoutMs, callback, /* hasCallerRestrictions= */ false);
    }

    /**
     * Internal implementation of {@code createUser()}, which is used by both
     * {@code ICarUserService} and {@code ICarDevicePolicyService}.
     *
     * @param hasCallerRestrictions when {@code true}, if the caller user is not an admin, it can
     * only create admin users
     */
    public void createUser(@Nullable String name, @NonNull String userType, int flags,
            int timeoutMs, @NonNull ResultCallbackImpl<UserCreationResult> callback,
            boolean hasCallerRestrictions) {
        Objects.requireNonNull(userType, "user type cannot be null");
        Objects.requireNonNull(callback, "receiver cannot be null");
        checkManageOrCreateUsersPermission(flags);
        EventLogHelper.writeCarUserServiceCreateUserReq(UserHelperLite.safeName(name), userType,
                flags, timeoutMs, hasCallerRestrictions ? 1 : 0);

        UserHandle callingUser = Binder.getCallingUserHandle();
        if (mUserManager.hasUserRestrictionForUser(UserManager.DISALLOW_ADD_USER, callingUser)) {
            String internalErrorMessage = String.format(ERROR_TEMPLATE_DISALLOW_ADD_USER,
                    callingUser, UserManager.DISALLOW_ADD_USER);
            Slogf.w(TAG, internalErrorMessage);
            sendUserCreationFailure(callback, UserCreationResult.STATUS_ANDROID_FAILURE,
                    internalErrorMessage);
            return;
        }

        // We use a queue to avoid concurrent user creations. Just posting the tasks to the handler
        // will not work here because handleCreateUser() calls UserHalService#createUser(),
        // which is an asynchronous call. Two consecutive createUser requests would result in
        // STATUS_CONCURRENT_OPERATION error from UserHalService.
        enqueueCreateUser(() -> handleCreateUser(name, userType, flags, timeoutMs, callback,
                callingUser, hasCallerRestrictions));
    }

    private void enqueueCreateUser(Runnable runnable) {
        // If the createUser queue is empty, add the task to the queue and post it to handler.
        // Otherwise, just add it to the queue. It will be handled once the current task finishes.
        synchronized (mLockUser) {
            if (mCreateUserQueue.isEmpty()) {
                // We need to push the current job to the queue and keep it in the queue until it
                // finishes, so that we can know the service is busy when the next job arrives.
                mCreateUserQueue.offer(runnable);
                mHandler.post(runnable);
            } else {
                mCreateUserQueue.offer(runnable);
                if (DBG) {
                    Slogf.d(TAG, "createUser: Another user is currently being created."
                            + " The request is queued for later execution.");
                }
            }
        }
    }

    private void postNextCreateUserIfAvailable() {
        synchronized (mLockUser) {
            // Remove the current job from the queue.
            mCreateUserQueue.poll();

            // Post the next job if there is any left in the queue.
            Runnable runnable = mCreateUserQueue.peek();
            if (runnable != null) {
                mHandler.post(runnable);
                if (DBG) {
                    Slogf.d(TAG, "createUser: A previously queued request is now being executed.");
                }
            }
        }
    }

    private void handleCreateUser(@Nullable String name, @NonNull String userType,
            int flags, int timeoutMs, @NonNull ResultCallbackImpl<UserCreationResult> callback,
            @NonNull UserHandle callingUser, boolean hasCallerRestrictions) {
        if (userType.equals(UserManager.USER_TYPE_FULL_GUEST) && flags != 0) {
            // Non-zero flags are not allowed when creating a guest user.
            String internalErroMessage = String
                    .format(ERROR_TEMPLATE_INVALID_FLAGS_FOR_GUEST_CREATION, flags, name);
            Slogf.e(TAG, internalErroMessage);
            sendUserCreationFailure(callback, UserCreationResult.STATUS_INVALID_REQUEST,
                    internalErroMessage);
            return;
        }
        if (hasCallerRestrictions) {
            // Restrictions:
            // - type/flag can only be normal user, admin, or guest
            // - non-admin user can only create non-admin users

            boolean validCombination;
            switch (userType) {
                case UserManager.USER_TYPE_FULL_SECONDARY:
                    validCombination = flags == 0
                        || (flags & UserManagerHelper.FLAG_ADMIN) == UserManagerHelper.FLAG_ADMIN;
                    break;
                case UserManager.USER_TYPE_FULL_GUEST:
                    validCombination = true;
                    break;
                default:
                    validCombination = false;
            }
            if (!validCombination) {
                String internalErrorMessage = String.format(
                        ERROR_TEMPLATE_INVALID_USER_TYPE_AND_FLAGS_COMBINATION, userType, flags);

                Slogf.d(TAG, internalErrorMessage);
                sendUserCreationFailure(callback, UserCreationResult.STATUS_INVALID_REQUEST,
                        internalErrorMessage);
                return;
            }

            if (!mUserHandleHelper.isAdminUser(callingUser)
                    && (flags & UserManagerHelper.FLAG_ADMIN) == UserManagerHelper.FLAG_ADMIN) {
                String internalErrorMessage = String
                        .format(ERROR_TEMPLATE_NON_ADMIN_CANNOT_CREATE_ADMIN_USERS,
                                callingUser.getIdentifier());
                Slogf.d(TAG, internalErrorMessage);
                sendUserCreationFailure(callback, UserCreationResult.STATUS_INVALID_REQUEST,
                        internalErrorMessage);
                return;
            }
        }

        NewUserRequest newUserRequest;
        try {
            newUserRequest = getCreateUserRequest(name, userType, flags);
        } catch (Exception e) {
            Slogf.e(TAG, e, "Error creating new user request. name: %s UserType: %s and flags: %s",
                    name, userType, flags);
            sendUserCreationResult(callback, UserCreationResult.STATUS_ANDROID_FAILURE,
                    UserManager.USER_OPERATION_ERROR_UNKNOWN, /* user= */ null,
                    /* errorMessage= */ null, e.toString());
            return;
        }

        UserHandle newUser;
        try {
            NewUserResponse newUserResponse = mUserManager.createUser(newUserRequest);

            if (!newUserResponse.isSuccessful()) {
                if (DBG) {
                    Slogf.d(TAG, "um.createUser() returned null for user of type %s and flags %d",
                            userType, flags);
                }
                sendUserCreationResult(callback, UserCreationResult.STATUS_ANDROID_FAILURE,
                        newUserResponse.getOperationResult(), /* user= */ null,
                        /* errorMessage= */ null, /* internalErrorMessage= */ null);
                return;
            }

            newUser = newUserResponse.getUser();

            if (DBG) {
                Slogf.d(TAG, "Created user: %s", newUser);
            }
            EventLogHelper.writeCarUserServiceCreateUserUserCreated(newUser.getIdentifier(), name,
                    userType, flags);
        } catch (RuntimeException e) {
            Slogf.e(TAG, e, "Error creating user of type %s and flags %d", userType, flags);
            sendUserCreationResult(callback, UserCreationResult.STATUS_ANDROID_FAILURE,
                    UserManager.USER_OPERATION_ERROR_UNKNOWN, /* user= */ null,
                    /* errorMessage= */ null, e.toString());
            return;
        }

        if (!isUserHalSupported()) {
            sendUserCreationResult(callback, UserCreationResult.STATUS_SUCCESSFUL,
                    /* androidFailureStatus= */ null , newUser, /* errorMessage= */ null,
                    /* internalErrorMessage= */ null);
            return;
        }

        CreateUserRequest request = UserHalHelper.emptyCreateUserRequest();
        request.usersInfo = UserHalHelper.newUsersInfo(mUserManager, mUserHandleHelper);
        if (!TextUtils.isEmpty(name)) {
            request.newUserName = name;
        }
        request.newUserInfo.userId = newUser.getIdentifier();
        request.newUserInfo.flags = UserHalHelper.convertFlags(mUserHandleHelper, newUser);
        if (DBG) {
            Slogf.d(TAG, "Create user request: %s", request);
        }

        try {
            mHal.createUser(request, timeoutMs, (status, resp) -> {
                String errorMessage = resp != null ? resp.errorMessage : null;
                int resultStatus = UserCreationResult.STATUS_HAL_INTERNAL_FAILURE;
                if (DBG) {
                    Slogf.d(TAG, "createUserResponse: status=%s, resp=%s",
                            UserHalHelper.halCallbackStatusToString(status), resp);
                }
                UserHandle user = null; // user returned in the result
                if (status != HalCallback.STATUS_OK || resp == null) {
                    Slogf.w(TAG, "invalid callback status (%s) or null response (%s)",
                            UserHalHelper.halCallbackStatusToString(status), resp);
                    EventLogHelper.writeCarUserServiceCreateUserResp(status, resultStatus,
                            errorMessage);
                    removeCreatedUser(newUser, "HAL call failed with "
                            + UserHalHelper.halCallbackStatusToString(status));
                    sendUserCreationResult(callback, resultStatus, /* androidFailureStatus= */ null,
                            user, errorMessage,  /* internalErrorMessage= */ null);
                    return;
                }

                switch (resp.status) {
                    case CreateUserStatus.SUCCESS:
                        resultStatus = UserCreationResult.STATUS_SUCCESSFUL;
                        user = newUser;
                        break;
                    case CreateUserStatus.FAILURE:
                        // HAL failed to switch user
                        resultStatus = UserCreationResult.STATUS_HAL_FAILURE;
                        break;
                    default:
                        // Shouldn't happen because UserHalService validates the status
                        Slogf.wtf(TAG, "Received invalid user switch status from HAL: %s", resp);
                }
                EventLogHelper.writeCarUserServiceCreateUserResp(status, resultStatus,
                        errorMessage);
                if (user == null) {
                    removeCreatedUser(newUser, "HAL returned "
                            + UserCreationResult.statusToString(resultStatus));
                }
                sendUserCreationResult(callback, resultStatus, /* androidFailureStatus= */ null,
                        user, errorMessage, /* internalErrorMessage= */ null);
            });
        } catch (Exception e) {
            Slogf.w(TAG, e, "mHal.createUser(%s) failed", request);
            removeCreatedUser(newUser, "mHal.createUser() failed");
            sendUserCreationFailure(callback, UserCreationResult.STATUS_HAL_INTERNAL_FAILURE,
                    e.toString());
        }
    }

    private NewUserRequest getCreateUserRequest(String name, String userType, int flags) {
        NewUserRequest.Builder builder = new NewUserRequest.Builder().setName(name)
                .setUserType(userType);
        if ((flags & UserManagerHelper.FLAG_ADMIN) == UserManagerHelper.FLAG_ADMIN) {
            builder.setAdmin();
        }

        if ((flags & UserManagerHelper.FLAG_EPHEMERAL) == UserManagerHelper.FLAG_EPHEMERAL) {
            builder.setEphemeral();
        }

        return builder.build();
    }

    private void removeCreatedUser(@NonNull UserHandle user, @NonNull String reason) {
        Slogf.i(TAG, "removing user %s reason: %s", user, reason);

        int userId = user.getIdentifier();
        EventLogHelper.writeCarUserServiceCreateUserUserRemoved(userId, reason);

        synchronized (mLockUser) {
            mFailedToCreateUserIds.put(userId, true);
        }

        try {
            if (!mUserManager.removeUser(user)) {
                Slogf.w(TAG, "Failed to remove user %s", user);
            }
        } catch (Exception e) {
            Slogf.e(TAG, e, "Failed to remove user %s", user);
        }
    }

    @Override
    public UserIdentificationAssociationResponse getUserIdentificationAssociation(
            @UserIdentificationAssociationType int[] types) {
        if (!isUserHalUserAssociationSupported()) {
            return UserIdentificationAssociationResponse.forFailure(VEHICLE_HAL_NOT_SUPPORTED);
        }

        Preconditions.checkArgument(!ArrayUtils.isEmpty(types), "must have at least one type");
        checkManageOrCreateUsersPermission("getUserIdentificationAssociation");

        int uid = getCallingUid();
        int userId = getCallingUserHandle().getIdentifier();
        EventLogHelper.writeCarUserServiceGetUserAuthReq(uid, userId, types.length);

        UserIdentificationGetRequest request = UserHalHelper.emptyUserIdentificationGetRequest();
        request.userInfo.userId = userId;
        request.userInfo.flags = getHalUserInfoFlags(userId);

        request.numberAssociationTypes = types.length;
        ArrayList<Integer> associationTypes = new ArrayList<>(types.length);
        for (int i = 0; i < types.length; i++) {
            associationTypes.add(types[i]);
        }
        request.associationTypes = toIntArray(associationTypes);

        UserIdentificationResponse halResponse = mHal.getUserAssociation(request);
        if (halResponse == null) {
            Slogf.w(TAG, "getUserIdentificationAssociation(): HAL returned null for %s",
                    Arrays.toString(types));
            return UserIdentificationAssociationResponse.forFailure();
        }

        int[] values = new int[halResponse.associations.length];
        for (int i = 0; i < values.length; i++) {
            values[i] = halResponse.associations[i].value;
        }
        EventLogHelper.writeCarUserServiceGetUserAuthResp(values.length);

        return UserIdentificationAssociationResponse.forSuccess(values, halResponse.errorMessage);
    }

    @Override
    public void setUserIdentificationAssociation(int timeoutMs,
            @UserIdentificationAssociationType int[] types,
            @UserIdentificationAssociationSetValue int[] values,
            AndroidFuture<UserIdentificationAssociationResponse> result) {
        if (!isUserHalUserAssociationSupported()) {
            result.complete(
                    UserIdentificationAssociationResponse.forFailure(VEHICLE_HAL_NOT_SUPPORTED));
            return;
        }

        Preconditions.checkArgument(!ArrayUtils.isEmpty(types), "must have at least one type");
        Preconditions.checkArgument(!ArrayUtils.isEmpty(values), "must have at least one value");
        if (types.length != values.length) {
            throw new IllegalArgumentException("types (" + Arrays.toString(types) + ") and values ("
                    + Arrays.toString(values) + ") should have the same length");
        }
        checkManageOrCreateUsersPermission("setUserIdentificationAssociation");

        int uid = getCallingUid();
        int userId = getCallingUserHandle().getIdentifier();
        EventLogHelper.writeCarUserServiceSetUserAuthReq(uid, userId, types.length);

        UserIdentificationSetRequest request = UserHalHelper.emptyUserIdentificationSetRequest();
        request.userInfo.userId = userId;
        request.userInfo.flags = getHalUserInfoFlags(userId);

        request.numberAssociations = types.length;
        ArrayList<UserIdentificationSetAssociation> associations = new ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            UserIdentificationSetAssociation association = new UserIdentificationSetAssociation();
            association.type = types[i];
            association.value = values[i];
            associations.add(association);
        }
        request.associations =
                associations.toArray(new UserIdentificationSetAssociation[associations.size()]);

        mHal.setUserAssociation(timeoutMs, request, (status, resp) -> {
            if (status != HalCallback.STATUS_OK || resp == null) {
                Slogf.w(TAG, "setUserIdentificationAssociation(): invalid callback status (%s) for "
                        + "response %s", UserHalHelper.halCallbackStatusToString(status), resp);
                if (resp == null || TextUtils.isEmpty(resp.errorMessage)) {
                    EventLogHelper.writeCarUserServiceSetUserAuthResp(0, /* errorMessage= */ "");
                    result.complete(UserIdentificationAssociationResponse.forFailure());
                    return;
                }
                EventLogHelper.writeCarUserServiceSetUserAuthResp(0, resp.errorMessage);
                result.complete(
                        UserIdentificationAssociationResponse.forFailure(resp.errorMessage));
                return;
            }
            int respSize = resp.associations.length;
            EventLogHelper.writeCarUserServiceSetUserAuthResp(respSize, resp.errorMessage);

            int[] responseTypes = new int[respSize];
            for (int i = 0; i < respSize; i++) {
                responseTypes[i] = resp.associations[i].value;
            }
            UserIdentificationAssociationResponse response = UserIdentificationAssociationResponse
                    .forSuccess(responseTypes, resp.errorMessage);
            if (DBG) {
                Slogf.d(TAG, "setUserIdentificationAssociation(): resp=%s, converted=%s", resp,
                        response);
            }
            result.complete(response);
        });
    }

    /**
     * Gets the User HAL flags for the given user.
     *
     * @throws IllegalArgumentException if the user does not exist.
     */
    private int getHalUserInfoFlags(@UserIdInt int userId) {
        UserHandle user = mUserHandleHelper.getExistingUserHandle(userId);
        Preconditions.checkArgument(user != null, "no user for id %d", userId);
        return UserHalHelper.convertFlags(mUserHandleHelper, user);
    }

    static void sendUserSwitchResult(@NonNull ResultCallbackImpl<UserSwitchResult> callback,
            boolean isLogout, @UserSwitchResult.Status int userSwitchStatus) {
        sendUserSwitchResult(callback, isLogout, HalCallback.STATUS_INVALID, userSwitchStatus,
                /* androidFailureStatus= */ null, /* errorMessage= */ null);
    }

    static void sendUserSwitchResult(@NonNull ResultCallbackImpl<UserSwitchResult> callback,
            boolean isLogout, @HalCallback.HalCallbackStatus int halCallbackStatus,
            @UserSwitchResult.Status int userSwitchStatus, @Nullable Integer androidFailureStatus,
            @Nullable String errorMessage) {
        if (isLogout) {
            EventLogHelper.writeCarUserServiceLogoutUserResp(halCallbackStatus, userSwitchStatus,
                    errorMessage);
        } else {
            EventLogHelper.writeCarUserServiceSwitchUserResp(halCallbackStatus, userSwitchStatus,
                    errorMessage);
        }
        callback.complete(
                new UserSwitchResult(userSwitchStatus, androidFailureStatus, errorMessage));
    }

    void sendUserCreationFailure(ResultCallbackImpl<UserCreationResult> callback,
            @UserCreationResult.Status int status, String internalErrorMessage) {
        sendUserCreationResult(callback, status, /* androidFailureStatus= */ null, /* user= */ null,
                /* errorMessage= */ null, internalErrorMessage);
    }

    private void sendUserCreationResult(ResultCallbackImpl<UserCreationResult> callback,
            @UserCreationResult.Status int status, @Nullable Integer androidFailureStatus,
            @NonNull UserHandle user, @Nullable String errorMessage,
            @Nullable String internalErrorMessage) {
        if (TextUtils.isEmpty(errorMessage)) {
            errorMessage = null;
        }
        if (TextUtils.isEmpty(internalErrorMessage)) {
            internalErrorMessage = null;
        }

        callback.complete(new UserCreationResult(status, androidFailureStatus, user, errorMessage,
                internalErrorMessage));

        // When done creating a user, post the next user creation task from the queue, if any.
        postNextCreateUserIfAvailable();
    }

    /**
     * Calls activity manager for user switch.
     *
     * <p><b>NOTE</b> This method is meant to be called just by UserHalService.
     *
     * @param requestId for the user switch request
     * @param targetUserId of the target user
     *
     * @hide
     */
    public void switchAndroidUserFromHal(int requestId, @UserIdInt int targetUserId) {
        EventLogHelper.writeCarUserServiceSwitchUserFromHalReq(requestId, targetUserId);
        Slogf.i(TAG, "User hal requested a user switch. Target user id is %d", targetUserId);

        boolean result = mAm.switchUser(UserHandle.of(targetUserId));
        if (result) {
            updateUserSwitchInProcess(requestId, targetUserId);
        } else {
            postSwitchHalResponse(requestId, targetUserId);
        }
    }

    private void updateUserSwitchInProcess(int requestId, @UserIdInt int targetUserId) {
        synchronized (mLockUser) {
            if (mUserIdForUserSwitchInProcess != USER_NULL) {
                // Some other user switch is in process.
                Slogf.w(TAG, "User switch for user id %d is in process. Abandoning it as a new user"
                        + " switch is requested for the target user %d",
                        mUserIdForUserSwitchInProcess, targetUserId);
            }
            mUserIdForUserSwitchInProcess = targetUserId;
            mRequestIdForUserSwitchInProcess = requestId;
        }
    }

    private void postSwitchHalResponse(int requestId, @UserIdInt int targetUserId) {
        if (!isUserHalSupported()) return;

        UsersInfo usersInfo = UserHalHelper.newUsersInfo(mUserManager, mUserHandleHelper);
        EventLogHelper.writeCarUserServicePostSwitchUserReq(targetUserId,
                usersInfo.currentUser.userId);
        SwitchUserRequest request = createUserSwitchRequest(targetUserId, usersInfo);
        request.requestId = requestId;
        mHal.postSwitchResponse(request);
    }

    private SwitchUserRequest createUserSwitchRequest(@UserIdInt int targetUserId,
            @NonNull UsersInfo usersInfo) {
        UserHandle targetUser = mUserHandleHelper.getExistingUserHandle(targetUserId);
        UserInfo halTargetUser = new UserInfo();
        halTargetUser.userId = targetUser.getIdentifier();
        halTargetUser.flags = UserHalHelper.convertFlags(mUserHandleHelper, targetUser);
        SwitchUserRequest request = UserHalHelper.emptySwitchUserRequest();
        request.targetUser = halTargetUser;
        request.usersInfo = usersInfo;
        return request;
    }

    /**
     * Checks if the User HAL is supported.
     */
    public boolean isUserHalSupported() {
        return mHal.isSupported();
    }

    /**
     * Checks if the User HAL user association is supported.
     */
    @Override
    public boolean isUserHalUserAssociationSupported() {
        return mHal.isUserAssociationSupported();
    }

    /**
     * Sets a callback which is invoked before user switch.
     *
     * <p>
     * This method should only be called by the Car System UI. The purpose of this call is to notify
     * Car System UI to show the user switch UI before the user switch.
     */
    @Override
    public void setUserSwitchUiCallback(@NonNull ICarResultReceiver receiver) {
        checkManageUsersPermission("setUserSwitchUiCallback");

        // Confirm that caller is system UI.
        String systemUiPackageName = PackageManagerHelper.getSystemUiPackageName(mContext);

        try {
            int systemUiUid = mContext
                    .createContextAsUser(UserHandle.SYSTEM, /* flags= */ 0).getPackageManager()
                    .getPackageUid(systemUiPackageName, PackageManager.MATCH_SYSTEM_ONLY);
            int callerUid = Binder.getCallingUid();
            if (systemUiUid != callerUid) {
                throw new SecurityException("Invalid caller. Only" + systemUiPackageName
                        + " is allowed to make this call");
            }
        } catch (NameNotFoundException e) {
            throw new IllegalStateException("Package " + systemUiPackageName + " not found", e);
        }

        mUserSwitchUiReceiver = receiver;
    }

    private void updateDefaultUserRestriction() {
        // We want to set restrictions on system and guest users only once. These are persisted
        // onto disk, so it's sufficient to do it once + we minimize the number of disk writes.
        if (Settings.Global.getInt(mContext.getContentResolver(),
                CarSettings.Global.DEFAULT_USER_RESTRICTIONS_SET, /* default= */ 0) != 0) {
            return;
        }
        // Only apply the system user restrictions if the system user is headless.
        if (UserManager.isHeadlessSystemUserMode()) {
            setSystemUserRestrictions();
        }
        Settings.Global.putInt(mContext.getContentResolver(),
                CarSettings.Global.DEFAULT_USER_RESTRICTIONS_SET, 1);
    }

    private boolean isPersistentUser(@UserIdInt int userId) {
        return !mUserHandleHelper.isEphemeralUser(UserHandle.of(userId));
    }

    /**
     * Adds a new {@link UserLifecycleListener} with {@code filter} to selectively listen to user
     * activity events.
     */
    public void addUserLifecycleListener(@Nullable UserLifecycleEventFilter filter,
            @NonNull UserLifecycleListener listener) {
        Objects.requireNonNull(listener, "listener cannot be null");
        mHandler.post(() -> mUserLifecycleListeners.add(
                new InternalLifecycleListener(listener, filter)));
    }

    /**
     * Removes previously added {@link UserLifecycleListener}.
     */
    public void removeUserLifecycleListener(@NonNull UserLifecycleListener listener) {
        Objects.requireNonNull(listener, "listener cannot be null");
        mHandler.post(() -> {
            for (int i = 0; i < mUserLifecycleListeners.size(); i++) {
                if (listener.equals(mUserLifecycleListeners.get(i).listener)) {
                    mUserLifecycleListeners.remove(i);
                }
            }
        });
    }

    private void onUserUnlocked(@UserIdInt int userId) {
        ArrayList<Runnable> tasks = null;
        synchronized (mLockUser) {
            sendPostSwitchToHalLocked(userId);
            if (userId == UserHandle.SYSTEM.getIdentifier()) {
                if (!mUser0Unlocked) { // user 0, unlocked, do this only once
                    updateDefaultUserRestriction();
                    tasks = new ArrayList<>(mUser0UnlockTasks);
                    mUser0UnlockTasks.clear();
                    mUser0Unlocked = true;
                }
            } else { // none user0
                Integer user = userId;
                if (isPersistentUser(userId)) {
                    // current foreground user should stay in top priority.
                    if (userId == ActivityManager.getCurrentUser()) {
                        mBackgroundUsersToRestart.remove(user);
                        mBackgroundUsersToRestart.add(0, user);
                    }
                    // -1 for user 0
                    if (mBackgroundUsersToRestart.size() > (mMaxRunningUsers - 1)) {
                        int userToDrop = mBackgroundUsersToRestart.get(
                                mBackgroundUsersToRestart.size() - 1);
                        Slogf.i(TAG, "New user (%d) unlocked, dropping least recently user from "
                                + "restart list (%s)", userId, userToDrop);
                        // Drop the least recently used user.
                        mBackgroundUsersToRestart.remove(mBackgroundUsersToRestart.size() - 1);
                    }
                }
            }
        }
        if (tasks != null) {
            int tasksSize = tasks.size();
            if (tasksSize > 0) {
                Slogf.d(TAG, "User0 unlocked, run queued tasks size: %d", tasksSize);
                for (int i = 0; i < tasksSize; i++) {
                    tasks.get(i).run();
                }
            }
        }
        startUsersOrHomeOnSecondaryDisplays(userId);
    }

    private void onUserStarting(@UserIdInt int userId) {
        if (DBG) {
            Slogf.d(TAG, "onUserStarting: user %d", userId);
        }

        if (!isMultipleUsersOnMultipleDisplaysSupported(mUserManager)
                || isSystemUserInHeadlessSystemUserMode(userId)) {
            return;
        }

        // Non-current user only
        // TODO(b/270719791): Keep track of the current user to avoid IPC to AM.
        if (userId == ActivityManager.getCurrentUser()) {
            if (DBG) {
                Slogf.d(TAG, "onUserStarting: user %d is the current user, skipping", userId);
            }
            return;
        }

        // TODO(b/273015292): Handling both "user visible" before "user starting" and
        // "user starting" before "user visible" for now because
        // UserController / UserVisibilityMediator don't sync the callbacks.
        if (isUserVisible(userId)) {
            if (DBG) {
                Slogf.d(TAG, "onUserStarting: user %d is already visible", userId);
            }

            // If the user is already visible, do zone assignment and start SysUi.
            // This addresses the most common scenario that "user starting" event occurs after
            // "user visible" event.
            assignVisibleUserToZone(userId);
            startSystemUIForVisibleUser(userId);
        } else {
            // If the user is not visible at this point, they might become visible at a later point.
            // So we save this user in 'mNotVisibleAtStartingUsers' for them to be checked in
            // onUserVisible.
            // This is the first half of addressing the scenario that "user visible" event occurs
            // after "user starting" event.
            if (DBG) {
                Slogf.d(TAG, "onUserStarting: user %d is not visible, "
                        + "adding to starting user queue", userId);
            }
            synchronized (mLockUser) {
                if (!mNotVisibleAtStartingUsers.contains(userId)) {
                    mNotVisibleAtStartingUsers.add(userId);
                } else {
                    // This is likely the case that this user started, but never became visible,
                    // then stopped in the past before starting again and becoming visible.
                    Slogf.i(TAG, "onUserStarting: user %d might start and stop in the past before "
                            + "starting again, reusing the user", userId);
                }
            }
        }
    }

    private void onUserVisible(@UserIdInt int userId) {
        if (DBG) {
            Slogf.d(TAG, "onUserVisible: user %d", userId);
        }

        // TODO(b/270719791): Keep track of the current user to avoid IPC to AM.
        if (!isMultipleUsersOnMultipleDisplaysSupported(mUserManager)
                || isSystemUserInHeadlessSystemUserMode(userId)) {
            return;
        }

        // Non-current user only
        // TODO(b/270719791): Keep track of the current user to avoid IPC to AM.
        if (userId == ActivityManager.getCurrentUser()) {
            if (DBG) {
                Slogf.d(TAG, "onUserVisible: user %d is the current user, skipping", userId);
            }
            return;
        }

        boolean isUserRunning = mUserManager.isUserRunning(UserHandle.of(userId));
        // If the user is found in 'mNotVisibleAtStartingUsers' and is running,
        // do occupant zone assignment and start SysUi.
        // Then remove the user from the 'mNotVisibleAtStartingUsers'.
        // This is the second half of addressing the scenario that "user visible" event occurs after
        // "user starting" event.
        synchronized (mLockUser) {
            if (mNotVisibleAtStartingUsers.contains(userId)) {
                if (DBG) {
                    Slogf.d(TAG, "onUserVisible: found user %d in the list of users not visible at"
                            + " starting", userId);
                }
                if (!isUserRunning) {
                    if (DBG) {
                        Slogf.d(TAG, "onUserVisible: user %d is not running", userId);
                    }
                    // If the user found in 'mNotVisibleAtStartingUsers' is not running,
                    // this is likely the case that this user started, but never became visible,
                    // then stopped in the past before becoming visible and starting again.
                    // Take this opportunity to clean this user up.
                    mNotVisibleAtStartingUsers.remove(Integer.valueOf(userId));
                    return;
                }

                // If the user found in 'mNotVisibleAtStartingUsers' is running, this is the case
                // that user starting occurred earlier than user visible.
                if (DBG) {
                    Slogf.d(TAG, "onUserVisible: assigning user %d to occupant zone and starting "
                            + "SysUi.", userId);
                }
                assignVisibleUserToZone(userId);
                startSystemUIForVisibleUser(userId);
                // The user will be cleared from 'mNotVisibleAtStartingUsers' the first time it
                // becomes visible since starting.
                mNotVisibleAtStartingUsers.remove(Integer.valueOf(userId));
            }
        }
    }

    private void onUserInvisible(@UserIdInt int userId) {
        if (!isMultipleUsersOnMultipleDisplaysSupported(mUserManager)) {
            return;
        }

        if (isSystemUserInHeadlessSystemUserMode(userId)) {
            return;
        }

        stopSystemUiForUser(mContext, userId);
        unassignInvisibleUserFromZone(userId);
    }

    private void startUsersOrHomeOnSecondaryDisplays(@UserIdInt int userId) {
        if (!isMultipleUsersOnMultipleDisplaysSupported(mUserManager)) {
            if (DBG) {
                Slogf.d(TAG, "startUsersOrHomeOnSecondaryDisplays(%d): not supported", userId);
            }
            return;
        }

        // Run from here only when CMUMD is supported.
        if (userId == ActivityManager.getCurrentUser()) {
            mBgHandler.post(() -> startUserPickerOnOtherDisplays(/* currentUserId= */ userId));
        } else {
            mBgHandler.post(() -> startLauncherForVisibleUser(userId));
        }
    }

    /**
     * Starts the specified user.
     *
     * <p>If a valid display ID is specified in the {@code request}, then start the user visible on
     *    the display.
     */
    @Override
    public void startUser(UserStartRequest request,
            ResultCallbackImpl<UserStartResponse> callback) {
        if (!hasManageUsersOrPermission(android.Manifest.permission.INTERACT_ACROSS_USERS)) {
            throw new SecurityException("startUser: You need one of " + MANAGE_USERS
                    + ", or " + INTERACT_ACROSS_USERS);
        }
        int userId = request.getUserHandle().getIdentifier();
        int displayId = request.getDisplayId();
        EventLogHelper.writeCarUserServiceStartUserVisibleOnDisplayReq(userId, displayId);
        mHandler.post(() -> handleStartUser(userId, displayId, callback));
    }

    private void handleStartUser(@UserIdInt int userId, int displayId,
            ResultCallbackImpl<UserStartResponse> callback) {
        @UserStartResponse.Status int userStartStatus = startUserInternal(userId, displayId);
        sendUserStartUserResponse(userId, displayId, userStartStatus, callback);
    }

    private void sendUserStartUserResponse(@UserIdInt int userId, int displayId,
            @UserStartResponse.Status int result,
            @NonNull ResultCallbackImpl<UserStartResponse> callback) {
        EventLogHelper.writeCarUserServiceStartUserVisibleOnDisplayResp(userId, displayId,
                    result);
        callback.complete(new UserStartResponse(result));
    }

    private @UserStartResponse.Status int startUserInternal(@UserIdInt int userId, int displayId) {
        if (displayId == Display.INVALID_DISPLAY) {
            // For an invalid display ID, start the user in background without a display.
            int status = startUserInBackgroundInternal(userId);
            // This works because the status code of UserStartResponse is a superset of
            // UserStartResult.
            return status;
        }

        // If the requested user is the system user.
        if (userId == UserHandle.SYSTEM.getIdentifier()) {
            return UserStartResponse.STATUS_USER_INVALID;
        }
        // If the requested user does not exist.
        if (mUserHandleHelper.getExistingUserHandle(userId) == null) {
            return UserStartResponse.STATUS_USER_DOES_NOT_EXIST;
        }

        // If the specified display is not a valid display for assigning user to.
        // Note: In passenger only system, users will be allowed on the DEFAULT_DISPLAY.
        if (displayId == Display.DEFAULT_DISPLAY) {
            if (!mIsVisibleBackgroundUsersOnDefaultDisplaySupported) {
                return UserStartResponse.STATUS_DISPLAY_INVALID;
            } else {
                if (DBG) {
                    Slogf.d(TAG, "startUserVisibleOnDisplayInternal: allow starting user on the "
                            + "default display under Multi User No Driver mode");
                }
            }
        }
        // If the specified display is not available to start a user on.
        if (mCarOccupantZoneService.getUserForDisplayId(displayId)
                != CarOccupantZoneManager.INVALID_USER_ID) {
            return UserStartResponse.STATUS_DISPLAY_UNAVAILABLE;
        }

        int curDisplayIdAssignedToUser = getMainDisplayAssignedToUser(userId);
        if (curDisplayIdAssignedToUser == displayId) {
            // If the user is already visible on the display, do nothing and return success.
            return UserStartResponse.STATUS_SUCCESSFUL_USER_ALREADY_VISIBLE_ON_DISPLAY;
        }
        if (curDisplayIdAssignedToUser != Display.INVALID_DISPLAY) {
            // If the specified user is assigned to another display, the user has to be stopped
            // before it can start on another display.
            return UserStartResponse.STATUS_USER_ASSIGNED_TO_ANOTHER_DISPLAY;
        }

        return ActivityManagerHelper.startUserInBackgroundVisibleOnDisplay(userId, displayId)
                ? UserStartResponse.STATUS_SUCCESSFUL : UserStartResponse.STATUS_ANDROID_FAILURE;
    }

    /**
     * Starts the specified user in the background.
     *
     * @param userId user to start in background
     * @param receiver to post results
     */
    public void startUserInBackground(@UserIdInt int userId,
            @NonNull AndroidFuture<UserStartResult> receiver) {
        checkManageOrCreateUsersPermission("startUserInBackground");
        EventLogHelper.writeCarUserServiceStartUserInBackgroundReq(userId);

        mHandler.post(() -> handleStartUserInBackground(userId, receiver));
    }

    private void handleStartUserInBackground(@UserIdInt int userId,
            AndroidFuture<UserStartResult> receiver) {
        int result = startUserInBackgroundInternal(userId);
        sendUserStartResult(userId, result, receiver);
    }

    private @UserStartResult.Status int startUserInBackgroundInternal(@UserIdInt int userId) {
        // If the requested user is the current user, do nothing and return success.
        if (ActivityManager.getCurrentUser() == userId) {
            return UserStartResult.STATUS_SUCCESSFUL_USER_IS_CURRENT_USER;
        }
        // If requested user does not exist, return error.
        if (mUserHandleHelper.getExistingUserHandle(userId) == null) {
            Slogf.w(TAG, "User %d does not exist", userId);
            return UserStartResult.STATUS_USER_DOES_NOT_EXIST;
        }

        if (!ActivityManagerHelper.startUserInBackground(userId)) {
            Slogf.w(TAG, "Failed to start user %d in background", userId);
            return UserStartResult.STATUS_ANDROID_FAILURE;
        }

        // TODO(b/181331178): We are not updating mBackgroundUsersToRestart or
        // mBackgroundUsersRestartedHere, which were only used for the garage mode. Consider
        // renaming them to make it more clear.
        return UserStartResult.STATUS_SUCCESSFUL;
    }

    private void sendUserStartResult(@UserIdInt int userId, @UserStartResult.Status int result,
            @NonNull AndroidFuture<UserStartResult> receiver) {
        EventLogHelper.writeCarUserServiceStartUserInBackgroundResp(userId, result);
        receiver.complete(new UserStartResult(result));
    }

    /**
     * Starts all background users that were active in system.
     *
     * @return list of background users started successfully.
     */
    @NonNull
    public ArrayList<Integer> startAllBackgroundUsersInGarageMode() {
        synchronized (mLockUser) {
            if (!mStartBackgroundUsersOnGarageMode) {
                Slogf.i(TAG, "Background users are not started as mStartBackgroundUsersOnGarageMode"
                        + " is false.");
                return new ArrayList<>();
            }
        }

        ArrayList<Integer> users;
        synchronized (mLockUser) {
            users = new ArrayList<>(mBackgroundUsersToRestart);
            mBackgroundUsersRestartedHere.clear();
            mBackgroundUsersRestartedHere.addAll(mBackgroundUsersToRestart);
        }
        ArrayList<Integer> startedUsers = new ArrayList<>();
        for (Integer user : users) {
            if (user == ActivityManager.getCurrentUser()) {
                continue;
            }
            if (ActivityManagerHelper.startUserInBackground(user)) {
                if (mUserManager.isUserUnlockingOrUnlocked(UserHandle.of(user))) {
                    // already unlocked / unlocking. No need to unlock.
                    startedUsers.add(user);
                } else if (ActivityManagerHelper.unlockUser(user)) {
                    startedUsers.add(user);
                } else { // started but cannot unlock
                    Slogf.w(TAG, "Background user started but cannot be unlocked: %s", user);
                    if (mUserManager.isUserRunning(UserHandle.of(user))) {
                        // add to started list so that it can be stopped later.
                        startedUsers.add(user);
                    }
                }
            }
        }
        // Keep only users that were re-started in mBackgroundUsersRestartedHere
        synchronized (mLockUser) {
            ArrayList<Integer> usersToRemove = new ArrayList<>();
            for (Integer user : mBackgroundUsersToRestart) {
                if (!startedUsers.contains(user)) {
                    usersToRemove.add(user);
                }
            }
            mBackgroundUsersRestartedHere.removeAll(usersToRemove);
        }
        return startedUsers;
    }

    /**
     * Stops the specified background user.
     *
     * @param userId user to stop
     * @param receiver to post results
     *
     * @deprecated Use {@link #stopUser(UserStopRequest, ResultCallbackImpl<UserStopResponse>)}
     *            instead.
     */
    // TODO(b/279793766) Clean up this method.
    public void stopUser(@UserIdInt int userId, @NonNull AndroidFuture<UserStopResult> receiver) {
        checkManageOrCreateUsersPermission("stopUser");
        EventLogHelper.writeCarUserServiceStopUserReq(userId);

        mHandler.post(() -> handleStopUser(userId, receiver));
    }

    private void handleStopUser(@UserIdInt int userId, AndroidFuture<UserStopResult> receiver) {
        @UserStopResult.Status int result = stopBackgroundUserInternal(userId,
                /* forceStop= */ true, /* withDelayedLocking= */ true);
        EventLogHelper.writeCarUserServiceStopUserResp(userId, result);
        receiver.complete(new UserStopResult(result));
    }

    /**
     * Stops the specified background user.
     */
    @Override
    public void stopUser(UserStopRequest request,
            ResultCallbackImpl<UserStopResponse> callback) {
        if (!hasManageUsersOrPermission(android.Manifest.permission.INTERACT_ACROSS_USERS)) {
            throw new SecurityException("stopUser: You need one of " + MANAGE_USERS + ", or "
                    + INTERACT_ACROSS_USERS);
        }
        int userId = request.getUserHandle().getIdentifier();
        boolean withDelayedLocking = request.isWithDelayedLocking();
        boolean forceStop = request.isForce();
        EventLogHelper.writeCarUserServiceStopUserReq(userId);
        mHandler.post(() -> handleStopUser(userId, forceStop, withDelayedLocking, callback));
    }

    private void handleStopUser(@UserIdInt int userId, boolean forceStop,
            boolean withDelayedLocking, ResultCallbackImpl<UserStopResponse> callback) {
        @UserStopResponse.Status int userStopStatus =
                stopBackgroundUserInternal(userId, forceStop, withDelayedLocking);
        sendUserStopResult(userId, userStopStatus, callback);
    }

    private void sendUserStopResult(@UserIdInt int userId, @UserStopResponse.Status int result,
            ResultCallbackImpl<UserStopResponse> callback) {
        EventLogHelper.writeCarUserServiceStopUserResp(userId, result);
        callback.complete(new UserStopResponse(result));
    }

    private @UserStopResult.Status int stopBackgroundUserInternal(@UserIdInt int userId,
            boolean forceStop, boolean withDelayedLocking) {
        int r;
        try {
            if (withDelayedLocking) {
                r = ActivityManagerHelper.stopUserWithDelayedLocking(userId, forceStop);
            } else {
                r = ActivityManagerHelper.stopUser(userId, forceStop);
            }
        } catch (RuntimeException e) {
            Slogf.e(TAG, e, "Exception calling am.stopUser(%d, true)", userId);
            return UserStopResult.STATUS_ANDROID_FAILURE;
        }
        switch(r) {
            case USER_OP_SUCCESS:
                return UserStopResult.STATUS_SUCCESSFUL;
            case USER_OP_ERROR_IS_SYSTEM:
                Slogf.w(TAG, "Cannot stop the system user: %d", userId);
                return UserStopResult.STATUS_FAILURE_SYSTEM_USER;
            case USER_OP_IS_CURRENT:
                Slogf.w(TAG, "Cannot stop the current user: %d", userId);
                return UserStopResult.STATUS_FAILURE_CURRENT_USER;
            case USER_OP_UNKNOWN_USER:
                Slogf.w(TAG, "Cannot stop the user that does not exist: %d", userId);
                return UserStopResult.STATUS_USER_DOES_NOT_EXIST;
            default:
                Slogf.w(TAG, "stopUser failed, user: %d, err: %d", userId, r);
        }
        return UserStopResult.STATUS_ANDROID_FAILURE;
    }

    /**
     * Sets boolean to control background user operations during garage mode.
     */
    public void setStartBackgroundUsersOnGarageMode(boolean enable) {
        synchronized (mLockUser) {
            mStartBackgroundUsersOnGarageMode = enable;
        }
    }

    /**
     * Stops a background user.
     *
     * @return whether stopping succeeds.
     */
    public boolean stopBackgroundUserInGagageMode(@UserIdInt int userId) {
        synchronized (mLockUser) {
            if (!mStartBackgroundUsersOnGarageMode) {
                Slogf.i(TAG, "Background users are not stopped as mStartBackgroundUsersOnGarageMode"
                        + " is false.");
                return false;
            }
        }

        @UserStopResult.Status int userStopStatus = stopBackgroundUserInternal(userId,
                /* forceStop= */ true, /* withDelayedLocking= */ true);
        if (UserStopResult.isSuccess(userStopStatus)) {
            // Remove the stopped user from the mBackgroundUserRestartedHere list.
            synchronized (mLockUser) {
                mBackgroundUsersRestartedHere.remove(Integer.valueOf(userId));
            }
            return true;
        }
        return false;
    }

    /**
     * Notifies all registered {@link UserLifecycleListener} with the event passed as argument.
     */
    public void onUserLifecycleEvent(@UserLifecycleEventType int eventType,
            @UserIdInt int fromUserId, @UserIdInt int toUserId) {
        if (DBG) {
            Slogf.d(TAG, "onUserLifecycleEvent(): event=%d, from=%d, to=%d", eventType, fromUserId,
                    toUserId);
        }
        int userId = toUserId;

        // Handle special cases first...
        switch (eventType) {
            case CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING:
                onUserSwitching(fromUserId, toUserId);
                break;
            case CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED:
                onUserUnlocked(userId);
                break;
            case CarUserManager.USER_LIFECYCLE_EVENT_TYPE_REMOVED:
                onUserRemoved(UserHandle.of(userId));
                break;
            case CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STARTING:
                onUserStarting(userId);
                break;
            case CarUserManager.USER_LIFECYCLE_EVENT_TYPE_VISIBLE:
                onUserVisible(userId);
                break;
            case CarUserManager.USER_LIFECYCLE_EVENT_TYPE_INVISIBLE:
                onUserInvisible(userId);
                break;
            default:
        }

        // ...then notify listeners.
        UserLifecycleEvent event = new UserLifecycleEvent(eventType, fromUserId, userId);

        mHandler.post(() -> {
            handleNotifyServiceUserLifecycleListeners(event);
            // POST_UNLOCKED event is meant only for internal service listeners. Skip sending it to
            // app listeners.
            if (eventType != CarUserManager.USER_LIFECYCLE_EVENT_TYPE_POST_UNLOCKED) {
                handleNotifyAppUserLifecycleListeners(event);
            }
        });
    }

    // value format: , separated zoneId:userId
    @VisibleForTesting
    SparseIntArray parseUserAssignmentSettingValue(String settingKey, String value) {
        Slogf.d(TAG, "Use %s for starting users", settingKey);
        SparseIntArray mapping = new SparseIntArray();
        try {
            String[] entries = value.split(",");
            for (String entry : entries) {
                String[] pair = entry.split(":");
                if (pair.length != 2) {
                    throw new IllegalArgumentException("Expecting zoneId:userId");
                }
                int zoneId = Integer.parseInt(pair[0]);
                int userId = Integer.parseInt(pair[1]);
                if (mapping.indexOfKey(zoneId) >= 0) {
                    throw new IllegalArgumentException("Multiple use of zone id:" + zoneId);
                }
                if (mapping.indexOfValue(userId) >= 0) {
                    throw new IllegalArgumentException("Multiple use of user id:" + userId);
                }
                mapping.append(zoneId, userId);
            }
        } catch (Exception e) {
            Slogf.w(TAG, e, "Setting %s has invalid value: ", settingKey, value);
            // Parsing error, ignore all.
            mapping.clear();
        }
        return mapping;
    }

    private boolean isSystemUserInHeadlessSystemUserMode(@UserIdInt int userId) {
        return userId == UserHandle.SYSTEM.getIdentifier()
                && mUserManager.isHeadlessSystemUserMode();
    }

    // starts user picker on displays without user allocation exception for on driver main display.
    void startUserPicker() {
        int driverZoneId = OccupantZoneInfo.INVALID_ZONE_ID;
        boolean hasDriverZone = mCarOccupantZoneService.hasDriverZone();
        if (hasDriverZone) {
            driverZoneId = mCarOccupantZoneService.getOccupantZone(
                    CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER,
                    VehicleAreaSeat.SEAT_UNKNOWN).zoneId;
        }

        // Start user picker on displays without user allocation.
        List<OccupantZoneInfo> occupantZoneInfos =
                mCarOccupantZoneService.getAllOccupantZones();
        for (int i = 0; i < occupantZoneInfos.size(); i++) {
            OccupantZoneInfo occupantZoneInfo = occupantZoneInfos.get(i);
            int zoneId = occupantZoneInfo.zoneId;
            // Skip driver zone when the driver zone exists.
            if (hasDriverZone && zoneId == driverZoneId) {
                continue;
            }

            int userId = mCarOccupantZoneService.getUserForOccupant(zoneId);
            if (userId != CarOccupantZoneManager.INVALID_USER_ID) {
                // If there is already a user allocated to the zone, skip.
                continue;
            }

            int displayId = mCarOccupantZoneService.getDisplayForOccupant(zoneId,
                    CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
            if (displayId == Display.INVALID_DISPLAY) {
                Slogf.e(TAG, "No main display for occupant zone:%d", zoneId);
                continue;
            }
            CarLocalServices.getService(CarActivityService.class)
                    .startUserPickerOnDisplay(displayId);
        }
    }

    @VisibleForTesting
    void startUserPickerOnOtherDisplays(@UserIdInt int currentUserId) {
        if (!isMultipleUsersOnMultipleDisplaysSupported(mUserManager)) {
            return;
        }
        if (isSystemUserInHeadlessSystemUserMode(currentUserId)
                && !mIsVisibleBackgroundUsersOnDefaultDisplaySupported) {
            return;
        }

        startUserPicker();
    }

    // Assigns the non-current visible user to the occupant zone that has the display the user is
    // on.
    private void assignVisibleUserToZone(@UserIdInt int userId) {

        int displayId = getMainDisplayAssignedToUser(userId);
        if (displayId == Display.INVALID_DISPLAY) {
            Slogf.w(TAG, "Cannot get display assigned to visible user %d", userId);
            return;
        }

        OccupantZoneInfo zoneInfo = getOccupantZoneForDisplayId(displayId);
        if (zoneInfo == null) {
            Slogf.w(TAG, "Cannot get occupant zone info associated with display %d for user %d",
                    displayId, userId);
            return;
        }

        int zoneId = zoneInfo.zoneId;
        int assignResult = mCarOccupantZoneService.assignVisibleUserToOccupantZone(zoneId,
                UserHandle.of(userId));
        if (assignResult != CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_OK) {
            Slogf.w(TAG,
                    "assignVisibleUserToZone: failed to assign user %d to zone %d, result %d",
                    userId, zoneId, assignResult);
            stopUser(userId, new AndroidFuture<UserStopResult>());
            return;
        }
    }

    // Unassigns the invisible user from the occupant zone.
    private void unassignInvisibleUserFromZone(@UserIdInt int userId) {
        CarOccupantZoneManager.OccupantZoneInfo zoneInfo =
                mCarOccupantZoneService.getOccupantZoneForUser(UserHandle.of(userId));
        if (zoneInfo == null) {
            Slogf.e(TAG, "unassignInvisibleUserFromZone: cannot find occupant zone for user %d",
                    userId);
            return;
        }

        int result = mCarOccupantZoneService.unassignOccupantZone(zoneInfo.zoneId);
        if (result != CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_OK) {
            Slogf.e(TAG,
                    "unassignInvisibleUserFromZone: failed to unassign user %d from zone %d,"
                    + " result %d",
                    userId, zoneInfo.zoneId, result);
        }
    }

    /** Should be called for non-current user only */
    private void startSystemUIForVisibleUser(@UserIdInt int userId) {
        if (!isMultipleUsersOnMultipleDisplaysSupported(mUserManager)) {
            return;
        }
        if (userId == UserHandle.SYSTEM.getIdentifier()
                || userId == ActivityManager.getCurrentUser()) {
            Slogf.w(TAG, "Cannot start SystemUI for current or system user (userId=%d)", userId);
            return;
        }

        if (isVisibleBackgroundUsersOnDefaultDisplaySupported(mUserManager)) {
            int displayId = getMainDisplayAssignedToUser(userId);
            if (displayId == Display.DEFAULT_DISPLAY) {
                // System user SystemUI is responsible for users running on the default display
                Slogf.d(TAG, "Skipping starting SystemUI for passenger user %d on default display",
                        userId);
                return;
            }
        }
        startSystemUiForUser(mContext, userId);
    }

    /** Should be called for non-current user only */
    private void startLauncherForVisibleUser(@UserIdInt int userId) {
        if (!isMultipleUsersOnMultipleDisplaysSupported(mUserManager)) {
            return;
        }
        if (isSystemUserInHeadlessSystemUserMode(userId)) {
            return;
        }

        int displayId = getMainDisplayAssignedToUser(userId);
        if (displayId == Display.INVALID_DISPLAY) {
            Slogf.w(TAG, "Cannot get display assigned to visible user %d", userId);
            return;
        }

        boolean result = startHomeForUserAndDisplay(mContext, userId, displayId);
        if (!result) {
            Slogf.w(TAG,
                    "Cannot launch home for assigned user %d, display %d, will stop the user",
                    userId, displayId);
            stopUser(userId, new AndroidFuture<UserStopResult>());
        }
    }

    private void sendPostSwitchToHalLocked(@UserIdInt int userId) {
        int userIdForUserSwitchInProcess;
        int requestIdForUserSwitchInProcess;
        synchronized (mLockUser) {
            if (mUserIdForUserSwitchInProcess == USER_NULL
                    || mUserIdForUserSwitchInProcess != userId
                    || mRequestIdForUserSwitchInProcess == 0) {
                Slogf.d(TAG, "No user switch request Id. No android post switch sent.");
                return;
            }
            userIdForUserSwitchInProcess = mUserIdForUserSwitchInProcess;
            requestIdForUserSwitchInProcess = mRequestIdForUserSwitchInProcess;

            mUserIdForUserSwitchInProcess = USER_NULL;
            mRequestIdForUserSwitchInProcess = 0;
        }
        postSwitchHalResponse(requestIdForUserSwitchInProcess, userIdForUserSwitchInProcess);
    }

    private void handleNotifyAppUserLifecycleListeners(UserLifecycleEvent event) {
        int listenersSize = mAppLifecycleListeners.size();
        if (listenersSize == 0) {
            Slogf.d(TAG, "No app listener to be notified of %s", event);
            return;
        }
        // Must use a different TimingsTraceLog because it's another thread
        if (DBG) {
            Slogf.d(TAG, "Notifying %d app listeners of %s", listenersSize, event);
        }
        int userId = event.getUserId();
        TimingsTraceLog t = new TimingsTraceLog(TAG, TraceHelper.TRACE_TAG_CAR_SERVICE);
        int eventType = event.getEventType();
        t.traceBegin("notify-app-listeners-user-" + userId + "-event-" + eventType);
        for (int i = 0; i < listenersSize; i++) {
            AppLifecycleListener listener = mAppLifecycleListeners.valueAt(i);
            if (eventType == CarUserManager.USER_LIFECYCLE_EVENT_TYPE_CREATED
                    || eventType == CarUserManager.USER_LIFECYCLE_EVENT_TYPE_REMOVED) {
                PlatformVersion platformVersion = Car.getPlatformVersion();
                // Perform platform version check to ensure the support for these new events
                // is consistent with the platform version declared in their ApiRequirements.
                if (!platformVersion.isAtLeast(PlatformVersion.VERSION_CODES.TIRAMISU_1)) {
                    if (DBG) {
                        Slogf.d(TAG, "Skipping app listener %s for event %s due to unsupported"
                                + " car platform version %s.", listener, event, platformVersion);
                    }
                    continue;
                }
                // Perform target car version check to ensure only apps expecting the new
                // lifecycle event types will have the events sent to them.
                // TODO(b/235524989): Cache the target car version for packages in
                // CarPackageManagerService.
                CarVersion targetCarVersion = mCarPackageManagerService.getTargetCarVersion(
                        listener.packageName);
                if (!targetCarVersion.isAtLeast(CarVersion.VERSION_CODES.TIRAMISU_1)) {
                    if (DBG) {
                        Slogf.d(TAG, "Skipping app listener %s for event %s due to incompatible"
                                + " target car version %s.", listener, event, targetCarVersion);
                    }
                    continue;
                }
            }
            if (!listener.applyFilters(event)) {
                if (DBG) {
                    Slogf.d(TAG, "Skipping app listener %s for event %s due to the filters"
                            + " evaluated to false.", listener, event);
                }
                continue;
            }
            Bundle data = new Bundle();
            data.putInt(CarUserManager.BUNDLE_PARAM_ACTION, eventType);

            int fromUserId = event.getPreviousUserId();
            if (fromUserId != USER_NULL) {
                data.putInt(CarUserManager.BUNDLE_PARAM_PREVIOUS_USER_ID, fromUserId);
            }
            Slogf.d(TAG, "Notifying app listener %s", listener);
            EventLogHelper.writeCarUserServiceNotifyAppLifecycleListener(listener.uid,
                    listener.packageName, eventType, fromUserId, userId);
            try {
                t.traceBegin("notify-app-listener-" + listener.toShortString());
                listener.receiver.send(userId, data);
            } catch (RemoteException e) {
                Slogf.e(TAG, e, "Error calling lifecycle listener %s", listener);
            } finally {
                t.traceEnd();
            }
        }
        t.traceEnd(); // notify-app-listeners-user-USERID-event-EVENT_TYPE
    }

    private void handleNotifyServiceUserLifecycleListeners(UserLifecycleEvent event) {
        TimingsTraceLog t = new TimingsTraceLog(TAG, TraceHelper.TRACE_TAG_CAR_SERVICE);
        if (mUserLifecycleListeners.isEmpty()) {
            Slogf.w(TAG, "No internal UserLifecycleListeners registered to notify event %s",
                    event);
            return;
        }
        int userId = event.getUserId();
        int eventType = event.getEventType();
        t.traceBegin("notify-listeners-user-" + userId + "-event-" + eventType);
        for (InternalLifecycleListener listener : mUserLifecycleListeners) {
            String listenerName = FunctionalUtils.getLambdaName(listener);
            UserLifecycleEventFilter filter = listener.filter;
            if (filter != null && !filter.apply(event)) {
                if (DBG) {
                    Slogf.d(TAG, "Skipping service listener %s for event %s due to the filter %s"
                            + " evaluated to false", listenerName, event, filter);
                }
                continue;
            }
            if (DBG) {
                Slogf.d(TAG, "Notifying %d service listeners of %s", mUserLifecycleListeners.size(),
                        event);
            }
            EventLogHelper.writeCarUserServiceNotifyInternalLifecycleListener(listenerName,
                    eventType, event.getPreviousUserId(), userId);
            try {
                t.traceBegin("notify-listener-" + listenerName);
                listener.listener.onEvent(event);
            } catch (RuntimeException e) {
                Slogf.e(TAG, e , "Exception raised when invoking onEvent for %s", listenerName);
            } finally {
                t.traceEnd();
            }
        }
        t.traceEnd(); // notify-listeners-user-USERID-event-EVENT_TYPE
    }

    private void onUserSwitching(@UserIdInt int fromUserId, @UserIdInt int toUserId) {
        if (DBG) {
            Slogf.i(TAG, "onUserSwitching(from=%d, to=%d)", fromUserId, toUserId);
        }
        TimingsTraceLog t = new TimingsTraceLog(TAG, TraceHelper.TRACE_TAG_CAR_SERVICE);
        t.traceBegin("onUserSwitching-" + toUserId);

        notifyLegacyUserSwitch(fromUserId, toUserId);

        mInitialUserSetter.setLastActiveUser(toUserId);

        t.traceEnd();
    }

    private void notifyLegacyUserSwitch(@UserIdInt int fromUserId, @UserIdInt int toUserId) {
        synchronized (mLockUser) {
            if (DBG) {
                Slogf.d(TAG, "notifyLegacyUserSwitch(%d, %d): mUserIdForUserSwitchInProcess=%d",
                        fromUserId, toUserId, mUserIdForUserSwitchInProcess);
            }
            if (mUserIdForUserSwitchInProcess != USER_NULL) {
                if (mUserIdForUserSwitchInProcess == toUserId) {
                    if (DBG) {
                        Slogf.d(TAG, "Ignoring, not legacy");
                    }
                    return;
                }
                if (DBG) {
                    Slogf.d(TAG, "Resetting mUserIdForUserSwitchInProcess");
                }
                mUserIdForUserSwitchInProcess = USER_NULL;
                mRequestIdForUserSwitchInProcess = 0;
            }
        }

        sendUserSwitchUiCallback(toUserId);

        // Switch HAL users if user switch is not requested by CarUserService
        notifyHalLegacySwitch(fromUserId, toUserId);
    }

    private void notifyHalLegacySwitch(@UserIdInt int fromUserId, @UserIdInt int toUserId) {
        if (!isUserHalSupported()) {
            if (DBG) {
                Slogf.d(TAG, "notifyHalLegacySwitch(): not calling UserHal (not supported)");
            }
            return;
        }

        // switch HAL user
        UsersInfo usersInfo = UserHalHelper.newUsersInfo(mUserManager, mUserHandleHelper,
                fromUserId);
        SwitchUserRequest request = createUserSwitchRequest(toUserId, usersInfo);
        mHal.legacyUserSwitch(request);
    }

    /**
     * Runs the given runnable when user 0 is unlocked. If user 0 is already unlocked, it is
     * run inside this call.
     *
     * @param r Runnable to run.
     */
    public void runOnUser0Unlock(@NonNull Runnable r) {
        Objects.requireNonNull(r, "runnable cannot be null");
        boolean runNow = false;
        synchronized (mLockUser) {
            if (mUser0Unlocked) {
                runNow = true;
            } else {
                mUser0UnlockTasks.add(r);
            }
        }
        if (runNow) {
            r.run();
        }
    }

    @VisibleForTesting
    @NonNull
    ArrayList<Integer> getBackgroundUsersToRestart() {
        ArrayList<Integer> backgroundUsersToRestart = null;
        synchronized (mLockUser) {
            backgroundUsersToRestart = new ArrayList<>(mBackgroundUsersToRestart);
        }
        return backgroundUsersToRestart;
    }

    private void setSystemUserRestrictions() {
        // Disable Location service for system user.
        LocationManager locationManager =
                (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
        locationManager.setLocationEnabledForUser(
                /* enabled= */ false, UserHandle.SYSTEM);
        locationManager.setAdasGnssLocationEnabled(false);
    }

    private void checkInteractAcrossUsersPermission(String message) {
        checkHasAtLeastOnePermissionGranted(mContext, message,
                android.Manifest.permission.INTERACT_ACROSS_USERS,
                android.Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    // TODO(b/167698977): members below were copied from UserManagerService; it would be better to
    // move them to some internal android.os class instead.
    private static final int ALLOWED_FLAGS_FOR_CREATE_USERS_PERMISSION =
            UserManagerHelper.FLAG_MANAGED_PROFILE
            | UserManagerHelper.FLAG_PROFILE
            | UserManagerHelper.FLAG_EPHEMERAL
            | UserManagerHelper.FLAG_RESTRICTED
            | UserManagerHelper.FLAG_GUEST
            | UserManagerHelper.FLAG_DEMO
            | UserManagerHelper.FLAG_FULL;

    static void checkManageUsersPermission(String message) {
        if (!hasManageUsersPermission()) {
            throw new SecurityException("You need " + MANAGE_USERS + " permission to: " + message);
        }
    }

    private static void checkManageOrCreateUsersPermission(String message) {
        if (!hasManageOrCreateUsersPermission()) {
            throw new SecurityException(
                    "You either need " + MANAGE_USERS + " or " + CREATE_USERS + " permission to: "
            + message);
        }
    }

    private static void checkManageOrCreateUsersPermission(int creationFlags) {
        if ((creationFlags & ~ALLOWED_FLAGS_FOR_CREATE_USERS_PERMISSION) == 0) {
            if (!hasManageOrCreateUsersPermission()) {
                throw new SecurityException("You either need " + MANAGE_USERS + " or "
                        + CREATE_USERS + "permission to create a user with flags "
                        + creationFlags);
            }
        } else if (!hasManageUsersPermission()) {
            throw new SecurityException("You need " + MANAGE_USERS + " permission to create a user"
                    + " with flags " + creationFlags);
        }
    }

    private static boolean hasManageUsersPermission() {
        final int callingUid = Binder.getCallingUid();
        return isSameApp(callingUid, Process.SYSTEM_UID)
                || callingUid == Process.ROOT_UID
                || hasPermissionGranted(MANAGE_USERS, callingUid);
    }

    private static boolean hasManageUsersOrPermission(String alternativePermission) {
        final int callingUid = Binder.getCallingUid();
        return isSameApp(callingUid, Process.SYSTEM_UID)
                || callingUid == Process.ROOT_UID
                || hasPermissionGranted(MANAGE_USERS, callingUid)
                || hasPermissionGranted(alternativePermission, callingUid);
    }

    private static boolean isSameApp(int uid1, int uid2) {
        return UserHandle.getAppId(uid1) == UserHandle.getAppId(uid2);
    }

    private static boolean hasManageOrCreateUsersPermission() {
        return hasManageUsersOrPermission(CREATE_USERS);
    }

    private static boolean hasPermissionGranted(String permission, int uid) {
        return ActivityManagerHelper.checkComponentPermission(permission, uid, /* owningUid= */ -1,
                /* exported= */ true) == PackageManager.PERMISSION_GRANTED;
    }

    private static String userOperationErrorToString(int error) {
        return DebugUtils.constantToString(UserManager.class, "USER_OPERATION_", error);
    }
}
