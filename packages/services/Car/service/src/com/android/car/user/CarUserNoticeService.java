/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.car.hardware.power.CarPowerManager.CarPowerStateListener;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;

import static com.android.car.CarServiceUtils.getCommonHandlerThread;
import static com.android.car.CarServiceUtils.getContentResolverForUser;
import static com.android.car.CarServiceUtils.isEventOfType;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.car.CarNotConnectedException;
import android.car.builtin.app.KeyguardManagerHelper;
import android.car.builtin.content.pm.PackageManagerHelper;
import android.car.builtin.os.UserManagerHelper;
import android.car.builtin.util.Slogf;
import android.car.hardware.power.CarPowerManager;
import android.car.settings.CarSettings;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.IUserNotice;
import android.car.user.IUserNoticeUI;
import android.car.user.UserLifecycleEventFilter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.CarServiceBase;
import com.android.car.R;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

/**
 * Service to show initial notice UI to user. It only launches it when setting is enabled and
 * it is up to notice UI (=Service) to dismiss itself upon user's request.
 *
 * <p>Conditions to show notice UI are:
 * <ol>
 *   <li>Cold boot
 *   <li><User switching
 *   <li>Car power state change to ON (happens in wakeup from suspend to RAM)
 * </ol>
 */
public final class CarUserNoticeService implements CarServiceBase {

    @VisibleForTesting
    static final String TAG = CarLog.tagFor(CarUserNoticeService.class);

    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    // Keyguard unlocking can be only polled as we cannot dismiss keyboard.
    // Polling will stop when keyguard is unlocked.
    private static final long KEYGUARD_POLLING_INTERVAL_MS = 100;

    // Value of the settings when it's enabled
    private static final int INITIAL_NOTICE_SCREEN_TO_USER_ENABLED = 1;

    private final Context mContext;

    // null means feature disabled.
    @Nullable
    private final Intent mServiceIntent;

    private final Handler mCommonThreadHandler;

    private final Object mLock = new Object();

    // This one records if there is a service bound. This will be cleared as soon as service is
    // unbound (=UI dismissed)
    @GuardedBy("mLock")
    private boolean mServiceBound = false;

    // This one represents if UI is shown for the current session. This should be kept until
    // next event to show UI comes up.
    @GuardedBy("mLock")
    private boolean mUiShown = false;

    @GuardedBy("mLock")
    @UserIdInt
    private int mUserId = UserManagerHelper.USER_NULL;

    @GuardedBy("mLock")
    private CarPowerManager mCarPowerManager;

    @GuardedBy("mLock")
    private IUserNoticeUI mUiService;

    @GuardedBy("mLock")
    @UserIdInt
    private int mIgnoreUserId = UserManagerHelper.USER_NULL;

    private final UserLifecycleListener mUserLifecycleListener = event -> {
        if (!isEventOfType(TAG, event, USER_LIFECYCLE_EVENT_TYPE_SWITCHING)) {
            return;
        }

        int userId = event.getUserId();
        if (DBG) {
            Slogf.d(TAG, "User switch event received. Target User: %d", userId);
        }

        CarUserNoticeService.this.mCommonThreadHandler.post(() -> {
            stopUi(/* clearUiShown= */ true);
            synchronized (mLock) {
                // This should be the only place to change user
                mUserId = userId;
            }
            startNoticeUiIfNecessary();
        });
    };

    private final CarPowerStateListener mPowerStateListener = new CarPowerStateListener() {
        @Override
        public void onStateChanged(int state) {
            if (state == CarPowerManager.STATE_SHUTDOWN_PREPARE) {
                mCommonThreadHandler.post(() -> stopUi(/* clearUiShown= */ true));
            } else if (state == CarPowerManager.STATE_ON) {
                // Only ON can be relied on as car can restart while in garage mode.
                mCommonThreadHandler.post(() -> startNoticeUiIfNecessary());
            }
        }
    };

    private final BroadcastReceiver mDisplayBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Runs in main thread, so do not use Handler.
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                if (isDisplayOn()) {
                    Slogf.i(TAG, "SCREEN_OFF while display is already on");
                    return;
                }
                Slogf.i(TAG, "Display off, stopping UI");
                stopUi(/* clearUiShown= */ true);
            } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                if (!isDisplayOn()) {
                    Slogf.i(TAG, "SCREEN_ON while display is already off");
                    return;
                }
                Slogf.i(TAG, "Display on, starting UI");
                startNoticeUiIfNecessary();
            }
        }
    };

    private final IUserNotice.Stub mIUserNotice = new IUserNotice.Stub() {
        @Override
        public void onDialogDismissed() {
            mCommonThreadHandler.post(() -> stopUi(/* clearUiShown= */ false));
        }
    };

    private final ServiceConnection mUiServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (mLock) {
                if (!mServiceBound) {
                    // already unbound but passed due to timing. This should be just ignored.
                    return;
                }
            }
            IUserNoticeUI binder = IUserNoticeUI.Stub.asInterface(service);
            try {
                binder.setCallbackBinder(mIUserNotice);
            } catch (RemoteException e) {
                Slogf.w(TAG, "UserNoticeUI Service died", e);
                // Wait for reconnect
                binder = null;
            }
            synchronized (mLock) {
                mUiService = binder;
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // UI crashed. Stop it so that it does not come again.
            stopUi(/* clearUiShown= */ true);
        }
    };

    // added for debugging purpose
    @GuardedBy("mLock")
    private int mKeyguardPollingCounter;

    private final Runnable mKeyguardPollingRunnable = () -> {
        synchronized (mLock) {
            mKeyguardPollingCounter++;
        }
        startNoticeUiIfNecessary();
    };

    public CarUserNoticeService(Context context) {
        this(context, new Handler(getCommonHandlerThread().getLooper()));
    }

    @VisibleForTesting
    CarUserNoticeService(Context context, Handler handler) {
        mCommonThreadHandler = handler;
        Resources res = context.getResources();
        String componentName = res.getString(R.string.config_userNoticeUiService);
        if (componentName.isEmpty()) {
            // feature disabled
            mContext = null;
            mServiceIntent = null;
            return;
        }
        mContext = context;
        mServiceIntent = new Intent();
        mServiceIntent.setComponent(ComponentName.unflattenFromString(componentName));
    }

    public void ignoreUserNotice(int userId) {
        synchronized (mLock) {
            mIgnoreUserId = userId;
        }
    }

    private boolean checkKeyguardLockedWithPolling() {
        removeCallbacks();
        boolean locked = KeyguardManagerHelper.isKeyguardLocked();
        if (locked) {
            mCommonThreadHandler.postDelayed(mKeyguardPollingRunnable,
                    KEYGUARD_POLLING_INTERVAL_MS);
        }
        return locked;
    }

    @VisibleForTesting
    void removeCallbacks() {
        mCommonThreadHandler.removeCallbacks(mKeyguardPollingRunnable);
    }

    private boolean isNoticeScreenEnabledInSetting(@UserIdInt int userId) {
        return Settings.Secure.getInt(getContentResolverForUser(mContext, userId),
                CarSettings.Secure.KEY_ENABLE_INITIAL_NOTICE_SCREEN_TO_USER,
                INITIAL_NOTICE_SCREEN_TO_USER_ENABLED) == INITIAL_NOTICE_SCREEN_TO_USER_ENABLED;
    }

    private boolean isDisplayOn() {
        PowerManager pm = mContext.getSystemService(PowerManager.class);
        if (pm == null) {
            return false;
        }
        return pm.isInteractive();
    }

    private boolean grantSystemAlertWindowPermission(@UserIdInt int userId) {
        AppOpsManager appOpsManager = mContext.getSystemService(AppOpsManager.class);
        if (appOpsManager == null) {
            Slogf.w(TAG, "AppOpsManager not ready yet");
            return false;
        }
        String packageName = mServiceIntent.getComponent().getPackageName();
        int packageUid;
        try {
            packageUid = PackageManagerHelper.getPackageUidAsUser(mContext.getPackageManager(),
                    packageName, userId);
        } catch (PackageManager.NameNotFoundException e) {
            Slogf.wtf(TAG, "Target package for config_userNoticeUiService not found:"
                    + packageName + " userId:" + userId);
            return false;
        }
        appOpsManager.setMode(AppOpsManager.OPSTR_SYSTEM_ALERT_WINDOW, packageUid, packageName,
                AppOpsManager.MODE_ALLOWED);
        Slogf.i(TAG, "Granted SYSTEM_ALERT_WINDOW permission to package:" + packageName
                + " package uid:" + packageUid);
        return true;
    }

    private void startNoticeUiIfNecessary() {
        int userId;
        synchronized (mLock) {
            if (mUiShown || mServiceBound) {
                if (DBG) {
                    Slogf.d(TAG, "Notice UI not necessary: mUiShown " + mUiShown + " mServiceBound "
                            + mServiceBound);
                }
                return;
            }
            userId = mUserId;
            if (mIgnoreUserId == userId) {
                if (DBG) {
                    Slogf.d(TAG, "Notice UI not necessary: mIgnoreUserId " + mIgnoreUserId
                            + " userId " + userId);
                }
                return;
            } else {
                mIgnoreUserId = UserManagerHelper.USER_NULL;
            }
        }
        if (userId == UserManagerHelper.USER_NULL) {
            if (DBG) Slogf.d(TAG, "Notice UI not necessary: userId " + userId);
            return;
        }
        // headless user 0 is ignored.
        if (userId == UserHandle.SYSTEM.getIdentifier()) {
            if (DBG) Slogf.d(TAG, "Notice UI not necessary: userId " + userId);
            return;
        }
        if (!isNoticeScreenEnabledInSetting(userId)) {
            if (DBG) {
                Slogf.d(TAG, "Notice UI not necessary as notice screen not enabled in settings.");
            }
            return;
        }
        if (userId != ActivityManager.getCurrentUser()) {
            if (DBG) {
                Slogf.d(TAG, "Notice UI not necessary as user has switched. will be handled by user"
                                + " switch callback.");
            }
            return;
        }
        // Dialog can be not shown if display is off.
        // DISPLAY_ON broadcast will handle this later.
        if (!isDisplayOn()) {
            if (DBG) Slogf.d(TAG, "Notice UI not necessary as display is off.");
            return;
        }
        // Do not show it until keyguard is dismissed.
        if (checkKeyguardLockedWithPolling()) {
            if (DBG) Slogf.d(TAG, "Notice UI not necessary as keyguard is not dismissed.");
            return;
        }
        if (!grantSystemAlertWindowPermission(userId)) {
            if (DBG) {
                Slogf.d(TAG, "Notice UI not necessary as System Alert Window Permission not"
                        + " granted.");
            }
            return;
        }
        boolean bound = mContext.bindServiceAsUser(mServiceIntent, mUiServiceConnection,
                Context.BIND_AUTO_CREATE, UserHandle.of(userId));
        if (bound) {
            Slogf.i(TAG, "Bound UserNoticeUI Service: " + mServiceIntent);
            synchronized (mLock) {
                mServiceBound = true;
                mUiShown = true;
            }
        } else {
            Slogf.w(TAG, "Cannot bind to UserNoticeUI Service Service" + mServiceIntent);
        }
    }

    private void stopUi(boolean clearUiShown) {
        removeCallbacks();
        boolean serviceBound;
        synchronized (mLock) {
            mUiService = null;
            serviceBound = mServiceBound;
            mServiceBound = false;
            if (clearUiShown) {
                mUiShown = false;
            }
        }
        if (serviceBound) {
            Slogf.i(TAG, "Unbound UserNoticeUI Service");
            mContext.unbindService(mUiServiceConnection);
        }
    }

    @Override
    public void init() {
        if (mServiceIntent == null) {
            // feature disabled
            return;
        }

        CarPowerManager carPowerManager;
        synchronized (mLock) {
            mCarPowerManager = CarLocalServices.createCarPowerManager(mContext);
            carPowerManager = mCarPowerManager;
        }
        try {
            carPowerManager.setListener(mContext.getMainExecutor(), mPowerStateListener);
        } catch (CarNotConnectedException e) {
            // should not happen
            throw new RuntimeException("CarNotConnectedException from CarPowerManager", e);
        }
        CarUserService userService = CarLocalServices.getService(CarUserService.class);
        UserLifecycleEventFilter userSwitchingEventFilter = new UserLifecycleEventFilter.Builder()
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_SWITCHING).build();
        userService.addUserLifecycleListener(userSwitchingEventFilter, mUserLifecycleListener);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        mContext.registerReceiver(mDisplayBroadcastReceiver, intentFilter,
                Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public void release() {
        if (mServiceIntent == null) {
            // feature disabled
            return;
        }
        mContext.unregisterReceiver(mDisplayBroadcastReceiver);
        CarUserService userService = CarLocalServices.getService(CarUserService.class);
        userService.removeUserLifecycleListener(mUserLifecycleListener);
        CarPowerManager carPowerManager;
        synchronized (mLock) {
            carPowerManager = mCarPowerManager;
            mUserId = UserManagerHelper.USER_NULL;
        }
        carPowerManager.clearListener();
        stopUi(/* clearUiShown= */ true);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        synchronized (mLock) {
            if (mServiceIntent == null) {
                writer.println("*CarUserNoticeService* disabled");
                return;
            }
            if (mUserId == UserManagerHelper.USER_NULL) {
                writer.println("*CarUserNoticeService* User not started yet.");
                return;
            }
            writer.println("*CarUserNoticeService* mServiceIntent:" + mServiceIntent
                    + ", mUserId:" + mUserId
                    + ", mUiShown:" + mUiShown
                    + ", mServiceBound:" + mServiceBound
                    + ", mKeyguardPollingCounter:" + mKeyguardPollingCounter
                    + ", Setting enabled:" + isNoticeScreenEnabledInSetting(mUserId)
                    + ", Ignore User: " + mIgnoreUserId);
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {}
}
