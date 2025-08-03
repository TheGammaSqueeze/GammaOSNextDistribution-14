/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.car.systeminterface;

import static com.android.car.systeminterface.SystemPowerControlHelper.SUSPEND_RESULT_SUCCESS;

import android.car.builtin.power.PowerManagerHelper;
import android.car.builtin.util.Slogf;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.UserManager;
import android.util.Log;
import android.util.Pair;

import com.android.car.procfsinspector.ProcessInfo;
import com.android.car.procfsinspector.ProcfsInspector;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Interface that abstracts system status (booted, sleeping, ...) operations
 */
public interface SystemStateInterface {
    static final String TAG = SystemStateInterface.class.getSimpleName();
    void shutdown();
    /**
     * Put the device into Suspend to RAM mode
     * @return boolean true if suspend succeeded
     */
    boolean enterDeepSleep();

    /**
     * Puts the device into Suspend-to-disk (hibernation)
     *
     * @return boolean {@code true} if hibernation succeeded
     */
    boolean enterHibernation();

    /**
     * Schedules an action to run after delay after boot completed for car service user.
     *
     * If the boot is already completed, the action will be run after delay.
     *
     * @param action The action to run after boot comoplete.
     * @param delay The delay for the action. Can be 0.
     */
    default void scheduleActionForBootCompleted(Runnable action, Duration delay) {
        scheduleActionForBootCompleted(action, delay, /* delayRange= */ Duration.ZERO);
    }

    /**
     * Schedules an action to run after delay after boot completed for car service user.
     *
     * If the boot is already completed, the action will be run after delay. If delayRange is not
     * 0, then we will add randomness to the delay. The delay will be randomly picked between
     * [max(0, delay-delayRange), delay+delayRange). This is useful to prevent all the boot complete
     * actions to be executed at the same time.
     *
     * @param action The action to run after boot comoplete.
     * @param delay The delay for the action. Can be 0.
     * @param delayRange The range for the delay. If not 0, then delay will be randomized within
     *   the range.
     */
    void scheduleActionForBootCompleted(Runnable action, Duration delay, Duration delayRange);

    default boolean isWakeupCausedByTimer() {
        //TODO bug: 32061842, check wake up reason and do necessary operation information should
        // come from kernel. it can be either power on or wake up for maintenance
        // power on will involve GPIO trigger from power controller
        // its own wakeup will involve timer expiration.
        return false;
    }

    /**
     * Gets whether the device supports deep sleep
     */
    default boolean isSystemSupportingDeepSleep() {
        return true;
    }

    /**
     * Gets whether the device supports hibernation
     */
    default boolean isSystemSupportingHibernation() {
        return true;
    }

    /**
     * @deprecated see {@link ProcfsInspector}
     */
    @Deprecated
    default List<ProcessInfo> getRunningProcesses() {
        return ProcfsInspector.readProcessTable();
    }

    /**
     * Default implementation that is used internally.
     */
    @VisibleForTesting
    class DefaultImpl implements SystemStateInterface {
        private static final boolean DEBUG = Slogf.isLoggable(TAG, Log.DEBUG);

        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private ScheduledExecutorService mExecutorService;
        @GuardedBy("mLock")
        private List<Pair<Runnable, Duration>> mActionsList = new ArrayList<>();
        @GuardedBy("mLock")
        private boolean mBootCompleted;

        private final Context mContext;

        private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
                    return;
                }
                Slogf.i(TAG, "Received ACTION_BOOT_COMPLETED intent");
                List<Pair<Runnable, Duration>> actionsListCopy;
                synchronized (mLock) {
                    // After mBootCompleted is set, no more actions will be added to mActionsList.
                    mBootCompleted = true;
                    actionsListCopy = new ArrayList<>(mActionsList);
                    mActionsList.clear();
                }
                for (int i = 0; i < actionsListCopy.size(); i++) {
                    runActionWithDelay(actionsListCopy.get(i).first, actionsListCopy.get(i).second);
                }
            }
        };

        private void runActionWithDelay(Runnable action, Duration delay) {
            long delayInMs = delay.toMillis();
            if (DEBUG) {
                Slogf.d(TAG, "Schedule bootup action after: " + delayInMs + "ms");
            }
            getExecutorService().schedule(action, delayInMs, TimeUnit.MILLISECONDS);
        }

        @SuppressWarnings("FutureReturnValueIgnored")
        private ScheduledExecutorService getExecutorService() {
            synchronized (mLock) {
                if (mExecutorService == null) {
                    mExecutorService = Executors.newScheduledThreadPool(/* corePoolSize= */ 1);
                }
                return mExecutorService;
            }
        }

        @GuardedBy("mLock")
        private boolean isBootCompletedLocked() {
            // There is no corresponding state for ACTION_BOOT_COMPLETED, so we use user unlock
            // instead. Technically isUserUnlocked might be true slightly before
            // ACTION_BOOT_COMPLETED intent is sent, however, the time difference is tiny and
            // as long as system user is unlocked, we should be okay.
            return mBootCompleted
                    || mContext.getSystemService(UserManager.class).isUserUnlocked();
        }

        @VisibleForTesting
        public DefaultImpl(Context context) {
            mContext = context;
        }

        @Override
        public void shutdown() {
            PowerManagerHelper.shutdown(mContext, /* confirm= */ false , /* reason= */ null,
                    /* wait= */ true);
        }

        @Override
        public boolean enterDeepSleep() {
            // TODO(b/32061842) Set wake up time via VHAL

            boolean deviceEnteredSleep = false;
            try {
                int retVal = SystemPowerControlHelper.forceDeepSleep();
                deviceEnteredSleep = retVal == SUSPEND_RESULT_SUCCESS;
            } catch (Exception e) {
                Slogf.e(TAG, "Unable to enter deep sleep", e);
            }
            return deviceEnteredSleep;
        }

        @Override
        public boolean enterHibernation() {
            boolean deviceHibernated = false;
            try {
                int retVal = SystemPowerControlHelper.forceHibernate();
                deviceHibernated = retVal == SUSPEND_RESULT_SUCCESS;
            } catch (Exception e) {
                Slogf.e(TAG, "Unable to enter hibernation", e);
            }
            return deviceHibernated;
        }

        @VisibleForTesting
        static Duration getRandomizedDelay(Duration delay, Duration delayRange) {
            if (delayRange == Duration.ZERO) {
                return delay;
            }
            Duration bootCompleteDelayMin = delay.minus(delayRange);
            if (bootCompleteDelayMin.isNegative()) {
                bootCompleteDelayMin = Duration.ZERO;
            }
            Duration bootCompleteDelayMax = delay.plus(delayRange);
            long minMillis = bootCompleteDelayMin.toMillis();
            long maxMillis = bootCompleteDelayMax.toMillis();
            long delayMillis = minMillis + (long) (Math.random() * (maxMillis - minMillis));
            return Duration.ofMillis(delayMillis);
        }

        /**
         * Schedule an action to be run with delay when this user (system) has finished booting.
         *
         * If the user has already finished booting, then the action will be executed with the
         * speicified delay.
         */
        @Override
        public void scheduleActionForBootCompleted(Runnable action, Duration delay,
                Duration delayRange) {
            Duration bootCompleteDelay = getRandomizedDelay(delay, delayRange);
            Slogf.i(TAG, "scheduleActionForBootCompleted, delay: " + bootCompleteDelay.toMillis()
                    + "ms");
            boolean receiverRegistered = false;
            synchronized (mLock) {
                if (isBootCompletedLocked()) {
                    Slogf.i(TAG, "The boot is already completed");
                    runActionWithDelay(action, bootCompleteDelay);
                    return;
                }
                Slogf.i(TAG, "The boot is not completed yet, waiting for boot to complete");
                if (!mActionsList.isEmpty()) {
                    receiverRegistered = true;
                }
                mActionsList.add(Pair.create(action, bootCompleteDelay));
            }
            if (!receiverRegistered) {
                IntentFilter intentFilter = new IntentFilter(Intent.ACTION_BOOT_COMPLETED);
                Slogf.i(TAG, "register ACTION_BOOT_COMPLETED receiver");
                mContext.registerReceiver(mBroadcastReceiver, intentFilter,
                        Context.RECEIVER_NOT_EXPORTED);
            }
        }

        @Override
        public boolean isSystemSupportingDeepSleep() {
            return SystemPowerControlHelper.isSystemSupportingDeepSleep();
        }

        @Override
        public boolean isSystemSupportingHibernation() {
            return SystemPowerControlHelper.isSystemSupportingHibernation();
        }
    }
}
