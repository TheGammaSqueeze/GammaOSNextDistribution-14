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

package com.android.server.scheduling;

import android.Manifest;
import android.annotation.CurrentTimeMillisLong;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.TetheredClient;
import android.net.TetheringManager;
import android.net.TetheringManager.TetheringEventCallback;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.RemoteCallback;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.scheduling.IRebootReadinessManager;
import android.scheduling.IRequestRebootReadinessStatusListener;
import android.scheduling.RebootReadinessManager;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.HandlerExecutor;
import com.android.server.SystemService;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of service that analyzes device state to detect if the device is in a suitable
 * state to reboot.
 *
 * @hide
 */
public class RebootReadinessManagerService extends IRebootReadinessManager.Stub {
    private static final String TAG = "RebootReadinessManager";

    private final RemoteCallbackList<IRequestRebootReadinessStatusListener> mCallbacks =
            new RemoteCallbackList<IRequestRebootReadinessStatusListener>();
    private final Handler mHandler;
    private final Executor mExecutor;

    private final Object mLock = new Object();

    private final Context mContext;

    private final ActivityManager mActivityManager;

    private final AlarmManager mAlarmManager;

    private final RebootReadinessLogger mRebootReadinessLogger;

    // For testing purposes only. Listeners whose names start with this prefix will be able to
    // inform the reboot signal, even if subsystem checks are disabled for testing.
    private static final String TEST_CALLBACK_PREFIX = "TESTCOMPONENT";

    // DeviceConfig properties
    private static final String PROPERTY_ACTIVE_POLLING_INTERVAL_MS = "active_polling_interval_ms";
    private static final String PROPERTY_INTERACTIVITY_THRESHOLD_MS = "interactivity_threshold_ms";
    private static final String PROPERTY_DISABLE_INTERACTIVITY_CHECK =
            "disable_interactivity_check";
    private static final String PROPERTY_DISABLE_APP_ACTIVITY_CHECK = "disable_app_activity_check";
    private static final String PROPERTY_DISABLE_SUBSYSTEMS_CHECK = "disable_subsystems_check";
    private static final String PROPERTY_ALARM_CLOCK_THRESHOLD_MS = "alarm_clock_threshold_ms";
    private static final String PROPERTY_LOGGING_BLOCKING_ENTITY_THRESHOLD_MS =
            "logging_blocking_entity_threshold_ms";


    private static final long DEFAULT_POLLING_INTERVAL_WHILE_ACTIVE_MS =
            TimeUnit.MINUTES.toMillis(5);
    private static final long DEFAULT_INTERACTIVITY_THRESHOLD_MS = TimeUnit.MINUTES.toMillis(30);
    private static final long DEFAULT_ALARM_CLOCK_THRESHOLD_MS = TimeUnit.MINUTES.toMillis(10);
    private static final long DEFAULT_LOGGING_BLOCKING_ENTITY_THRESHOLD_MS =
            TimeUnit.HOURS.toMillis(1);

    @GuardedBy("mLock")
    private long mActivePollingIntervalMs = DEFAULT_POLLING_INTERVAL_WHILE_ACTIVE_MS;

    @GuardedBy("mLock")
    private long mInteractivityThresholdMs = DEFAULT_INTERACTIVITY_THRESHOLD_MS;

    @GuardedBy("mLock")
    private boolean mDisableInteractivityCheck = false;

    @GuardedBy("mLock")
    private boolean mReadyToReboot = false;

    @GuardedBy("mLock")
    private boolean mDisableAppActivityCheck = false;

    @GuardedBy("mLock")
    private boolean mDisableSubsystemsCheck = false;

    @GuardedBy("mLock")
    private long mAlarmClockThresholdMs = DEFAULT_ALARM_CLOCK_THRESHOLD_MS;

    @GuardedBy("mLock")
    private long mLoggingBlockingEntityThresholdMs = DEFAULT_LOGGING_BLOCKING_ENTITY_THRESHOLD_MS;

    // A mapping of uid to package name for uids which have called markRebootPending. Reboot
    // readiness state changed broadcasts will only be sent to the values in this map.
    @GuardedBy("mLock")
    private final SparseArray<ArraySet<String>> mCallingUidToPackageMap = new SparseArray<>();

    // When true, reboot readiness checks should not be performed.
    @GuardedBy("mLock")
    private boolean mCanceled = false;

    // The last time the device stopped being in an interactive state, in relation to the time
    // since the system booted. If the device is currently interactive, this will be MAX_VALUE.
    @GuardedBy("mLock")
    private long mLastTimeNotInteractiveMs = Long.MAX_VALUE;


    // Metadata to be stored for use in metrics.
    @GuardedBy("mLock")
    @CurrentTimeMillisLong
    private long mPollingStartTimeMs;

    @GuardedBy("mLock")
    private int mTimesBlockedByInteractivity;

    @GuardedBy("mLock")
    private int mTimesBlockedBySubsystems;

    @GuardedBy("mLock")
    private int mTimesBlockedByAppActivity;

    @GuardedBy("mLock")
    private boolean mBlockedByTethering = false;

    private final TetheringEventCallback mTetheringEventCallback = new TetheringEventCallback() {
        @Override
        public void onClientsChanged(Collection<TetheredClient> clients) {
            synchronized (mLock) {
                mBlockedByTethering = clients.size() > 0;
            }
        }
    };

    private final BroadcastReceiver mUserPresentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handleUserPresent();
        }
    };

    private final AlarmManager.OnAlarmListener mPollStateListener = () -> {
        synchronized (mLock) {
            if (!mCanceled) {
                pollRebootReadinessState();
            } else {
                Log.w(TAG, "Received poll state callback while canceled.");
            }
        }
    };

    RebootReadinessManagerService(Context context) {
        this(context, new RebootReadinessLogger(context));
    }

    @VisibleForTesting
    RebootReadinessManagerService(Context context, RebootReadinessLogger logger) {
        // TODO(b/161353402): Consolidate mHandler and mExecutor
        mHandler = new Handler(Looper.getMainLooper());
        mExecutor = new HandlerExecutor(mHandler);
        mRebootReadinessLogger = logger;
        updateConfigs();
        DeviceConfig.addOnPropertiesChangedListener(DeviceConfig.NAMESPACE_REBOOT_READINESS,
                mExecutor, properties -> updateConfigs());
        BroadcastReceiver interactivityChangedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(Intent.ACTION_SCREEN_ON)) {
                    noteInteractivityStateChanged(true);
                } else if (action.equals(Intent.ACTION_SCREEN_OFF)) {
                    noteInteractivityStateChanged(false);
                }
            }
        };
        IntentFilter interactivityFilter = new IntentFilter();
        interactivityFilter.addAction(Intent.ACTION_SCREEN_ON);
        interactivityFilter.addAction(Intent.ACTION_SCREEN_OFF);
        context.registerReceiver(interactivityChangedReceiver, interactivityFilter);
        PowerManager powerManager = context.getSystemService(PowerManager.class);
        if (powerManager != null) {
            noteInteractivityStateChanged(powerManager.isInteractive());
        }

        IntentFilter userPresentFilter = new IntentFilter();
        userPresentFilter.addAction(Intent.ACTION_USER_PRESENT);
        context.registerReceiver(mUserPresentReceiver, userPresentFilter);
        mActivityManager = context.getSystemService(ActivityManager.class);
        mAlarmManager = context.getSystemService(AlarmManager.class);
        TetheringManager mTetheringManager = context.getSystemService(TetheringManager.class);
        if (mTetheringManager != null) {
            mTetheringManager.registerTetheringEventCallback(mExecutor, mTetheringEventCallback);
        }
        mHandler.post(mRebootReadinessLogger::readMetricsPostReboot);
        mContext = context;
    }

    @Override
    public int handleShellCommand(ParcelFileDescriptor in, ParcelFileDescriptor out,
            ParcelFileDescriptor err, String[] args) {
        return new RebootReadinessShellCommand(this, mContext).exec(this, in.getFileDescriptor(),
                out.getFileDescriptor(), err.getFileDescriptor(), args);
    }

    /**
     * Lifecycle class for RebootReadinessManagerService.
     */
    public static class Lifecycle extends SystemService {

        public Lifecycle(Context context) {
            super(context);
        }

        @Override
        public void onStart() {
            RebootReadinessManagerService rebootReadinessManagerService =
                    new RebootReadinessManagerService(getContext());
            publishBinderService(Context.REBOOT_READINESS_SERVICE, rebootReadinessManagerService);
        }
    }

    @Override
    public void markRebootPending(String callingPackage) {
        mContext.enforceCallingPermission(Manifest.permission.REBOOT,
                "Caller does not have REBOOT permission.");
        synchronized (mLock) {
            Log.i(TAG, "Starting reboot readiness checks for package: " + callingPackage);
            // If there are existing clients waiting for a broadcast, reboot readiness checks
            // are already ongoing.
            if (mCallingUidToPackageMap.size() == 0) {
                mCanceled = false;
                resetMetrics();
                mHandler.removeCallbacksAndMessages(null);
                mHandler.post(this::pollRebootReadinessState);
            } else {
                sendRebootReadyBroadcast(callingPackage,
                        Binder.getCallingUserHandle(), mReadyToReboot);
            }
            ArraySet<String> packagesForUid =
                    mCallingUidToPackageMap.get(Binder.getCallingUid(), new ArraySet<>());
            packagesForUid.add(callingPackage);
            mCallingUidToPackageMap.put(Binder.getCallingUid(), packagesForUid);
        }
    }

    @Override
    public void cancelPendingReboot(String callingPackage) {
        mContext.enforceCallingPermission(Manifest.permission.REBOOT,
                "Caller does not have REBOOT permission");
        final int callingUid = Binder.getCallingUid();
        synchronized (mLock) {
            ArraySet<String> packagesForUid =
                    mCallingUidToPackageMap.get(callingUid, new ArraySet<>());
            if (packagesForUid.contains(callingPackage)) {
                Log.i(TAG, "Canceling reboot readiness checks for package: " + callingPackage);
                packagesForUid.remove(callingPackage);
                if (packagesForUid.size() == 0) {
                    // No remaining clients exist for this calling uid
                    mCallingUidToPackageMap.remove(callingUid);
                }

                // Only cancel readiness checks if there are no more uids with packages
                // waiting for broadcasts
                if (mCallingUidToPackageMap.size() == 0) {
                    mHandler.removeCallbacksAndMessages(null);
                    mAlarmManager.cancel(mPollStateListener);
                    mCanceled = true;
                    // Delete any logging information if the device is ready to reboot, since an
                    // unattended reboot should not take place if the checks are cancelled.
                    if (mReadyToReboot) {
                        mRebootReadinessLogger.deleteLoggingInformation();
                    }
                    mReadyToReboot = false;
                }
            } else {
                Log.w(TAG, "Package " + callingPackage + " tried to cancel reboot readiness"
                        + " checks but was not a client of this service.");
            }
        }
    }

    @Override
    public boolean isReadyToReboot() {
        mContext.enforceCallingPermission(Manifest.permission.REBOOT,
                "Caller does not have REBOOT permission.");
        synchronized (mLock) {
            return mReadyToReboot;
        }
    }

    @Override
    public void addRequestRebootReadinessStatusListener(
            IRequestRebootReadinessStatusListener callback) {
        mContext.enforceCallingPermission(Manifest.permission.SIGNAL_REBOOT_READINESS,
                "Caller does not have SIGNAL_REBOOT_READINESS permission.");
        mCallbacks.register(callback);
        try {
            callback.asBinder().linkToDeath(
                    () -> removeRequestRebootReadinessStatusListener(callback), 0);
        } catch (RemoteException e) {
            removeRequestRebootReadinessStatusListener(callback);
        }
    }

    @Override
    public void removeRequestRebootReadinessStatusListener(
            IRequestRebootReadinessStatusListener callback) {
        mContext.enforceCallingPermission(Manifest.permission.SIGNAL_REBOOT_READINESS,
                "Caller does not have SIGNAL_REBOOT_READINESS permission.");
        mCallbacks.unregister(callback);
    }

    private void pollRebootReadinessState() {
        synchronized (mLock) {
            final boolean previousRebootReadiness = mReadyToReboot;
            final boolean currentRebootReadiness = getRebootReadinessLocked();
            if (previousRebootReadiness != currentRebootReadiness) {
                noteRebootReadinessStateChanged(currentRebootReadiness);
            }
            if (!mCanceled && !currentRebootReadiness) {
                mAlarmManager.setExact(AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis()
                                + mActivePollingIntervalMs,
                        "poll_reboot_readiness", mPollStateListener, mHandler);
            }
        }
    }

    @GuardedBy("mLock")
    private boolean getRebootReadinessLocked() {
        if (!(mDisableInteractivityCheck || checkDeviceInteractivity())) {
            mTimesBlockedByInteractivity++;
            Log.v(TAG, "Reboot blocked by device interactivity");
            return false;
        }

        if (!checkSystemComponentsState()) {
            mTimesBlockedBySubsystems++;
            return false;
        }

        if (!(mDisableAppActivityCheck || checkBackgroundAppActivity())) {
            mTimesBlockedByAppActivity++;
            return false;
        }

        return true;
    }

    @VisibleForTesting
    @GuardedBy("mLock")
    boolean checkSystemComponentsState() {
        if (!mDisableSubsystemsCheck) {
            if (mBlockedByTethering) {
                return false;
            }

            AlarmManager.AlarmClockInfo alarmClockInfo = mAlarmManager.getNextAlarmClock();
            final long now = System.currentTimeMillis();
            if (alarmClockInfo != null
                    && (alarmClockInfo.getTriggerTime() - now) < mAlarmClockThresholdMs) {
                return false;
            }
        }

        final List<IRequestRebootReadinessStatusListener> blockingCallbacks = new ArrayList<>();
        final List<String> blockingCallbackNames = new ArrayList<>();
        int i = mCallbacks.beginBroadcast();
        CountDownLatch latch = new CountDownLatch(i);
        while (i > 0) {
            i--;
            final IRequestRebootReadinessStatusListener callback = mCallbacks.getBroadcastItem(i);
            try {
                RemoteCallback remoteCallback = new RemoteCallback(
                        result -> {
                            boolean isReadyToReboot = result.getBoolean(
                                    RebootReadinessManager.IS_REBOOT_READY_KEY);
                            String name = result.getString(
                                    RebootReadinessManager.SUBSYSTEM_NAME_KEY);
                            if (!isReadyToReboot && (!mDisableSubsystemsCheck
                                    || name.startsWith(TEST_CALLBACK_PREFIX))) {
                                blockingCallbacks.add(callback);
                                blockingCallbackNames.add(name);
                            }
                            latch.countDown();
                        }
                );
                callback.onRequestRebootReadinessStatus(remoteCallback);
            } catch (RemoteException e) {
                Log.e(TAG, "Could not resolve state of RebootReadinessCallback: " + e);
                return false;
            }
        }
        try {
            latch.await(1, TimeUnit.MINUTES);
        } catch (InterruptedException ignore) {
        }
        mCallbacks.finishBroadcast();
        mRebootReadinessLogger.maybeLogLongBlockingComponents(blockingCallbackNames,
                mLoggingBlockingEntityThresholdMs);
        if (blockingCallbacks.size() > 0) {
            Log.v(TAG, "Reboot blocked by subsystems: " + String.join(",", blockingCallbackNames));
            return false;
        }
        return true;
    }

    @VisibleForTesting
    boolean checkDeviceInteractivity() {
        final long now = SystemClock.elapsedRealtime();
        synchronized (mLock) {
            return (now - mLastTimeNotInteractiveMs) > mInteractivityThresholdMs;
        }
    }

    /**
     * Check for important app activity in the background by querying the running services on the
     * device.
     */
    @VisibleForTesting
    boolean checkBackgroundAppActivity() {
        if (mActivityManager != null) {
            final List<RunningServiceInfo> serviceInfos =
                    mActivityManager.getRunningServices(Integer.MAX_VALUE);
            List<Integer> blockingUids = new ArrayList<>();
            for (int i = 0; i < serviceInfos.size(); i++) {
                RunningServiceInfo info = serviceInfos.get(i);
                if (info.foreground) {
                    blockingUids.add(info.uid);
                }
            }
            mRebootReadinessLogger.maybeLogLongBlockingApps(blockingUids,
                    mLoggingBlockingEntityThresholdMs);
            if (blockingUids.size() > 0) {
                Log.v(TAG, "Reboot blocked by app uids: " + blockingUids.toString());
                return false;
            }
            return true;
        }
        return false;
    }

    private void noteRebootReadinessStateChanged(boolean isReadyToReboot) {
        synchronized (mLock) {
            Log.i(TAG, "Reboot readiness state changed to " + isReadyToReboot);
            mReadyToReboot = isReadyToReboot;

            // Send state change broadcast to any packages which have a pending update
            for (int i = 0; i < mCallingUidToPackageMap.size(); i++) {
                UserHandle user = UserHandle.getUserHandleForUid(mCallingUidToPackageMap.keyAt(i));
                ArraySet<String> packageNames = mCallingUidToPackageMap.valueAt(i);
                for (int j = 0; j < packageNames.size(); j++) {
                    sendRebootReadyBroadcast(packageNames.valueAt(j), user, isReadyToReboot);
                }
            }
            if (mReadyToReboot) {
                mRebootReadinessLogger.writeAfterRebootReadyBroadcast(
                        mPollingStartTimeMs, System.currentTimeMillis(),
                        mTimesBlockedByInteractivity, mTimesBlockedBySubsystems,
                        mTimesBlockedByAppActivity);

                AlarmManager.AlarmClockInfo alarmClockInfo = mAlarmManager.getNextAlarmClock();
                if (alarmClockInfo != null) {
                    // Schedule a state check before the next alarm clock is triggered. This check
                    // is triggered within the alarm clock threshold window (plus a small tolerance)
                    // to ensure that this alarm clock will block the reboot at that time.
                    long stateCheckTriggerTime = alarmClockInfo.getTriggerTime()
                            - (mAlarmClockThresholdMs - TimeUnit.SECONDS.toMillis(1));
                    if (stateCheckTriggerTime > System.currentTimeMillis()) {
                        mAlarmManager.setExact(AlarmManager.RTC_WAKEUP,
                                stateCheckTriggerTime, "poll_reboot_readiness",
                                mPollStateListener, mHandler);
                    }
                }
            } else {
                mRebootReadinessLogger.writeAfterNotRebootReadyBroadcast();
            }
        }
    }

    @GuardedBy("mLock")
    private void sendRebootReadyBroadcast(String packageName, UserHandle user,
            boolean isReadyToReboot) {
        Log.i(TAG, "Sending REBOOT_READY broadcast to package " + packageName
                + " for user " + user.getIdentifier());
        Intent intent = new Intent(RebootReadinessManager.ACTION_REBOOT_READY);
        intent.putExtra(RebootReadinessManager.EXTRA_IS_READY_TO_REBOOT, isReadyToReboot);
        intent.setPackage(packageName);
        mContext.sendBroadcastAsUser(intent, user, Manifest.permission.REBOOT);
    }

    private void updateConfigs() {
        synchronized (mLock) {
            mActivePollingIntervalMs = DeviceConfig.getLong(DeviceConfig.NAMESPACE_REBOOT_READINESS,
                    PROPERTY_ACTIVE_POLLING_INTERVAL_MS, DEFAULT_POLLING_INTERVAL_WHILE_ACTIVE_MS);
            mInteractivityThresholdMs = DeviceConfig.getLong(
                    DeviceConfig.NAMESPACE_REBOOT_READINESS, PROPERTY_INTERACTIVITY_THRESHOLD_MS,
                    DEFAULT_INTERACTIVITY_THRESHOLD_MS);
            mDisableInteractivityCheck = DeviceConfig.getBoolean(
                    DeviceConfig.NAMESPACE_REBOOT_READINESS,
                    PROPERTY_DISABLE_INTERACTIVITY_CHECK, false);
            mDisableAppActivityCheck = DeviceConfig.getBoolean(
                    DeviceConfig.NAMESPACE_REBOOT_READINESS,
                    PROPERTY_DISABLE_APP_ACTIVITY_CHECK, false);
            mDisableSubsystemsCheck = DeviceConfig.getBoolean(
                    DeviceConfig.NAMESPACE_REBOOT_READINESS,
                    PROPERTY_DISABLE_SUBSYSTEMS_CHECK, false);
            mAlarmClockThresholdMs = DeviceConfig.getLong(
                    DeviceConfig.NAMESPACE_REBOOT_READINESS,
                    PROPERTY_ALARM_CLOCK_THRESHOLD_MS, DEFAULT_ALARM_CLOCK_THRESHOLD_MS);
            mLoggingBlockingEntityThresholdMs = DeviceConfig.getLong(
                    DeviceConfig.NAMESPACE_REBOOT_READINESS,
                    PROPERTY_LOGGING_BLOCKING_ENTITY_THRESHOLD_MS,
                    DEFAULT_LOGGING_BLOCKING_ENTITY_THRESHOLD_MS);
        }
    }

    private void noteInteractivityStateChanged(boolean isInteractive) {
        synchronized (mLock) {
            if (isInteractive) {
                mLastTimeNotInteractiveMs = Long.MAX_VALUE;
                if (!mCanceled && mReadyToReboot) {
                    Log.i(TAG, "Device became interactive while reboot-ready");
                    pollRebootReadinessState();
                }
            } else {
                mLastTimeNotInteractiveMs = SystemClock.elapsedRealtime();
            }
        }
    }

    private void handleUserPresent() {
        mContext.unregisterReceiver(mUserPresentReceiver);
        mRebootReadinessLogger.writePostRebootMetrics();
    }

    @GuardedBy("mLock")
    private void resetMetrics() {
        mPollingStartTimeMs = System.currentTimeMillis();
        mTimesBlockedByInteractivity = 0;
        mTimesBlockedBySubsystems = 0;
        mTimesBlockedByAppActivity = 0;
    }

    @VisibleForTesting
    SparseArray<ArraySet<String>> getCallingPackages() {
        synchronized (mLock) {
            return mCallingUidToPackageMap;
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        synchronized (mLock) {
            mRebootReadinessLogger.dump(pw);
            if (mCallingUidToPackageMap.size() > 0) {
                pw.print("Packages awaiting REBOOT_READY broadcast:");
                for (int i = 0; i < mCallingUidToPackageMap.size(); i++) {
                    ArraySet<String> packageNames = mCallingUidToPackageMap.valueAt(i);
                    for (int j = 0; j < packageNames.size(); j++) {
                        pw.print(" " + packageNames.valueAt(j));
                    }
                }
                pw.println();
                pw.println("Current reboot readiness state: " + mReadyToReboot);
            }
        }
    }

    /** Writes information about any UIDs which are blocking the reboot. */
    void writeBlockingUids(PrintWriter pw) {
        mRebootReadinessLogger.writeBlockingUids(pw);
    }

    /** Writes information about any subsystems which are blocking the reboot. */
    void writeBlockingSubsystems(PrintWriter pw) {
        mRebootReadinessLogger.writeBlockingSubsystems(pw);
    }
}
