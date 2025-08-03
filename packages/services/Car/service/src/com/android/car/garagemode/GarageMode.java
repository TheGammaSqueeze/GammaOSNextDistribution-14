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

package com.android.car.garagemode;

import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_STOPPED;

import static com.android.car.CarServiceUtils.isEventOfType;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.Nullable;
import android.app.job.JobInfo;
import android.car.builtin.job.JobSchedulerHelper;
import android.car.builtin.util.EventLogHelper;
import android.car.builtin.util.Slogf;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserLifecycleEventFilter;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.ArraySet;
import android.util.SparseIntArray;

import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.CarStatsLogHelper;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.power.CarPowerManagementService;
import com.android.car.user.CarUserService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * Class that interacts with JobScheduler through JobSchedulerHelper, controls system idleness and
 * monitor jobs which are in GarageMode interest.
 */
class GarageMode {

    private static final String TAG = CarLog.tagFor(GarageMode.class) + "_"
            + GarageMode.class.getSimpleName();

    /**
     * When changing this field value, please update
     * {@link com.android.server.job.controllers.idle.CarIdlenessTracker} as well.
     */
    public static final String ACTION_GARAGE_MODE_ON =
            "com.android.server.jobscheduler.GARAGE_MODE_ON";

    /**
     * When changing this field value, please update
     * {@link com.android.server.job.controllers.idle.CarIdlenessTracker} as well.
     */
    public static final String ACTION_GARAGE_MODE_OFF =
            "com.android.server.jobscheduler.GARAGE_MODE_OFF";

    @VisibleForTesting
    static final long JOB_SNAPSHOT_INITIAL_UPDATE_MS = 10_000; // 10 seconds

    private static final long STOP_BACKGROUND_USER_TIMEOUT_MS = 60_000; // 60 seconds
    private static final long WAIT_FOR_USER_STOPPED_EVENT_TIMEOUT = 10_000; // 10 seconds

    private static final long JOB_SNAPSHOT_UPDATE_FREQUENCY_MS = 1_000; // 1 second
    private static final long USER_STOP_CHECK_INTERVAL_MS = 100; // 100 milliseconds
    private static final int ADDITIONAL_CHECKS_TO_DO = 1;
    // Values for eventlog (car_pwr_mgr_garage_mode)
    private static final int GARAGE_MODE_EVENT_LOG_START = 0;
    private static final int GARAGE_MODE_EVENT_LOG_FINISH = 1;
    private static final int GARAGE_MODE_EVENT_LOG_CANCELLED = 2;

    // The background user is started.
    private static final int USER_STARTED = 0;
    // The background user is being stopped.
    private static final int USER_STOPPING = 1;
    // The background user has stopped.
    private static final int USER_STOPPED = 2;

    private final Context mContext;
    private final GarageModeController mController;
    private final Object mLock = new Object();
    private final Handler mHandler;

    @GuardedBy("mLock")
    private boolean mGarageModeActive;
    @GuardedBy("mLock")
    private int mAdditionalChecksToDo = ADDITIONAL_CHECKS_TO_DO;
    @GuardedBy("mLock")
    private boolean mIdleCheckerIsRunning;

    private final GarageModeRecorder mGarageModeRecorder;

    private final Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            boolean garageModeActive;
            synchronized (mLock) {
                garageModeActive = mGarageModeActive;
            }
            if (!garageModeActive) {
                Slogf.d(TAG, "Garage Mode is inactive. Stopping the idle-job checker.");
                finish();
                return;
            }
            int numberRunning = JobSchedulerHelper.getRunningJobsAtIdle(mContext).size();
            if (numberRunning > 0) {
                Slogf.d(TAG, "%d jobs are still running. Need to wait more ...", numberRunning);
                synchronized (mLock) {
                    mAdditionalChecksToDo = ADDITIONAL_CHECKS_TO_DO;
                }
            } else {
                // No idle-mode jobs are running.
                // Are there any scheduled idle jobs that could run now?
                int numberReadyToRun = JobSchedulerHelper.getPendingJobs(mContext).size();
                if (numberReadyToRun == 0) {
                    Slogf.d(TAG, "No jobs are running. No jobs are pending. Exiting Garage Mode.");
                    finish();
                    return;
                }
                int numAdditionalChecks;
                synchronized (mLock) {
                    numAdditionalChecks = mAdditionalChecksToDo;
                    if (mAdditionalChecksToDo > 0) {
                        mAdditionalChecksToDo--;
                    }
                }
                if (numAdditionalChecks == 0) {
                    Slogf.d(TAG, "No jobs are running. Waited too long for %d pending jobs. Exiting"
                            + " Garage Mode.", numberReadyToRun);
                    finish();
                    return;
                }
                Slogf.d(TAG, "No jobs are running. Waiting %d more cycles for %d pending jobs.",
                        numAdditionalChecks, numberReadyToRun);
            }
            mHandler.postDelayed(mRunnable, JOB_SNAPSHOT_UPDATE_FREQUENCY_MS);
        }
    };

    private final Runnable mStopUserCheckRunnable = new Runnable() {

        @Override
        public void run() {
            boolean emptyStartedBackgroundUsers;
            synchronized (mLock) {
                emptyStartedBackgroundUsers = (mStartedBackgroundUsers.size() == 0);
            }
            if (emptyStartedBackgroundUsers) {
                runBackgroundUserStopCompletor();
                return;
            }
            // All jobs done or stopped.
            if (JobSchedulerHelper.getRunningJobsAtIdle(mContext).size() == 0) {
                synchronized (mLock) {
                    List<Integer> stoppedUsers = new ArrayList<>();
                    for (int i = 0; i < mStartedBackgroundUsers.size(); i++) {
                        int userId = mStartedBackgroundUsers.keyAt(i);
                        if (userId == UserHandle.SYSTEM.getIdentifier()) {
                            // This should not happen since we should not add systme user to the
                            // background users list.
                            Slogf.wtf(TAG, "System user: " + userId
                                    + "should not be added to background users list");
                            stoppedUsers.add(userId);
                            continue;
                        }
                        if (mStartedBackgroundUsers.valueAt(i) != USER_STARTED) {
                            Slogf.i(TAG, "user: " + userId + " is not in USER_STARTED mode, "
                                    + "skip stopping it");
                            continue;
                        }

                        Slogf.i(TAG, "Stopping background user:%d remaining users:%d",
                                userId, mStartedBackgroundUsers.size() - i - 1);
                        mStartedBackgroundUsers.put(userId, USER_STOPPING);
                        boolean result = CarLocalServices.getService(CarUserService.class)
                                .stopBackgroundUserInGagageMode(userId);
                        if (result) {
                            // If the user is stopped successfully, we should receive a user
                            // lifecycle event.
                            Slogf.i(TAG, "Waiting for user:%d to be stopped", userId);
                            waitForBackgroundUserStoppedLocked(userId);
                            Slogf.i(TAG, "Received user stopped event for user: " + userId);
                            stoppedUsers.add(userId);
                        } else {
                            Slogf.e(TAG, "Failed to stop started background user: " + userId);
                        }
                    }
                    for (int i = 0; i < stoppedUsers.size(); i++) {
                        mStartedBackgroundUsers.removeAt(mStartedBackgroundUsers.indexOfKey(
                                stoppedUsers.get(i)));
                    }
                }
                runBackgroundUserStopCompletor();
            } else {
                // Keep user until job scheduling is stopped. Otherwise, it can crash jobs.
                // Poll again later
                mHandler.postDelayed(mStopUserCheckRunnable, USER_STOP_CHECK_INTERVAL_MS);
            }
        }
    };

    // The callback to run when the operation to stop all started background users timeout.
    // This must be removed when the operation succeeded.
    private final Runnable mBackgroundUserStopTimeout = new Runnable() {

        @Override
        public void run() {
            Slogf.e(TAG, "Timeout while waiting for background users to stop");
            runBackgroundUserStopCompletor();
        }
    };

    @GuardedBy("mLock")
    private Runnable mFinishCompletor;
    @GuardedBy("mLock")
    private SparseIntArray mStartedBackgroundUsers = new SparseIntArray();

    @GuardedBy("mLock")
    private Runnable mBackgroundUserStopCompletor;

    GarageMode(Context context, GarageModeController controller) {
        mContext = context;
        mController = controller;
        mGarageModeActive = false;
        mHandler = controller.getHandler();
        mGarageModeRecorder = new GarageModeRecorder(Clock.systemUTC());
    }

    void init() {
        UserLifecycleEventFilter userStoppedEventFilter = new UserLifecycleEventFilter.Builder()
                .addEventType(USER_LIFECYCLE_EVENT_TYPE_STOPPED).build();
        CarLocalServices.getService(CarUserService.class)
                .addUserLifecycleListener(userStoppedEventFilter, mUserLifecycleListener);
    }

    void release() {
        CarLocalServices.getService(CarUserService.class)
                .removeUserLifecycleListener(mUserLifecycleListener);
    }

    /**
     * When background users are queued to stop, this user lifecycle listener will ensure to stop
     * them one by one by queuing next user when previous user is stopped.
     */
    private final UserLifecycleListener mUserLifecycleListener = new UserLifecycleListener() {
        @Override
        public void onEvent(UserLifecycleEvent event) {
            if (!isEventOfType(TAG, event, USER_LIFECYCLE_EVENT_TYPE_STOPPED)) {
                return;
            }

            int userId = event.getUserId();

            // This is using the CarUserService's internal handler and is different from mHandler,
            // so this can obtain the mLock which should be released from mLock.wait().
            synchronized (mLock) {
                if (mStartedBackgroundUsers.indexOfKey(userId) >= 0
                        && mStartedBackgroundUsers.get(userId) == USER_STOPPING) {
                    Slogf.i(TAG, "Background user stopped event received. User Id: %d", userId);

                    mStartedBackgroundUsers.put(userId, USER_STOPPED);
                    mLock.notifyAll();
                } else {
                    Slogf.i(TAG, "User Id: %d stopped, not in mStartedBackgroundUsers, ignore",
                            userId);
                }
            }
        }
    };

    boolean isGarageModeActive() {
        synchronized (mLock) {
            return mGarageModeActive;
        }
    }

    @VisibleForTesting
    ArraySet<Integer> getStartedBackgroundUsers() {
        ArraySet<Integer> users;
        synchronized (mLock) {
            int size = mStartedBackgroundUsers.size();
            users = new ArraySet<>(size);
            for (int i = 0; i < size; i++) {
                users.add(mStartedBackgroundUsers.keyAt(i));
            }
        }
        return users;
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(IndentingPrintWriter writer) {
        synchronized (mLock) {
            writer.printf("GarageMode is %sactive\n", (mGarageModeActive ? "" : "not "));
            mGarageModeRecorder.dump(writer);
            if (!mGarageModeActive) {
                return;
            }
            writer.printf("GarageMode idle checker is %srunning\n",
                    (mIdleCheckerIsRunning ? "" : "not "));
        }

        List<JobInfo> jobs = JobSchedulerHelper.getRunningJobsAtIdle(mContext);
        if (jobs.size() > 0) {
            writer.printf("GarageMode is waiting for %d jobs:\n", jobs.size());
            for (int idx = 0; idx < jobs.size(); idx++) {
                JobInfo jobinfo = jobs.get(idx);
                writer.printf("   %d: %s\n", idx + 1, jobinfo);
            }
        } else {
            jobs = JobSchedulerHelper.getPendingJobs(mContext);
            writer.printf("GarageMode is waiting for %d pending idle jobs:\n", jobs.size());
            for (int idx = 0; idx < jobs.size(); idx++) {
                JobInfo jobinfo = jobs.get(idx);
                writer.printf("   %d: %s\n", idx + 1, jobinfo);
            }
        }
    }

    // Must be scheduled in mHandler.
    void enterGarageMode(Runnable completor) {
        Slogf.i(TAG, "Entering GarageMode");
        CarPowerManagementService carPowerService = CarLocalServices.getService(
                CarPowerManagementService.class);
        if (carPowerService != null
                && carPowerService.garageModeShouldExitImmediately()) {
            if (completor != null) {
                completor.run();
            }
            synchronized (mLock) {
                mGarageModeActive = false;
            }
            Slogf.i(TAG, "GarageMode exits immediately");
            return;
        }
        synchronized (mLock) {
            mGarageModeActive = true;
            mFinishCompletor = completor;
        }
        broadcastSignalToJobScheduler(true);
        mGarageModeRecorder.startSession();
        CarStatsLogHelper.logGarageModeStart();
        EventLogHelper.writeGarageModeEvent(GARAGE_MODE_EVENT_LOG_START);
        startMonitoringThread();
        // If there is a previously scheduled stop bg user, don't do it.
        mHandler.removeCallbacks(mStopUserCheckRunnable);
        mHandler.removeCallbacks(mBackgroundUserStopTimeout);

        // Start all background users.
        ArrayList<Integer> startedUsers = CarLocalServices.getService(CarUserService.class)
                .startAllBackgroundUsersInGarageMode();
        Slogf.i(TAG, "Started background user during garage mode: %s", startedUsers);
        synchronized (mLock) {
            for (int user : startedUsers) {
                mStartedBackgroundUsers.put(user, USER_STARTED);
            }
        }
    }

    // Must be scheduled in mHandler.
    void cancel(@Nullable Runnable completor) {
        broadcastSignalToJobScheduler(false);
        cleanupGarageMode(() -> {
            Slogf.i(TAG, "GarageMode is cancelled");
            EventLogHelper.writeGarageModeEvent(GARAGE_MODE_EVENT_LOG_CANCELLED);
            mGarageModeRecorder.cancelSession();
            if (completor != null) {
                completor.run();
            }
            Runnable finishCompletor;
            synchronized (mLock) {
                finishCompletor = mFinishCompletor;
            }
            if (finishCompletor != null) {
                finishCompletor.run();
            }
        });
    }

    // Must be scheduled in mHandler.
    void finish() {
        synchronized (mLock) {
            if (!mIdleCheckerIsRunning) {
                Slogf.i(TAG, "Finishing Garage Mode. Idle checker is not running.");
                return;
            }
            mIdleCheckerIsRunning = false;
        }
        broadcastSignalToJobScheduler(false);
        EventLogHelper.writeGarageModeEvent(GARAGE_MODE_EVENT_LOG_FINISH);
        mGarageModeRecorder.finishSession();
        cleanupGarageMode(() -> {
            Slogf.i(TAG, "GarageMode is completed normally");
            Runnable finishCompletor;
            synchronized (mLock) {
                finishCompletor = mFinishCompletor;
            }
            if (finishCompletor != null) {
                finishCompletor.run();
            }
        });
    }

    private void runBackgroundUserStopCompletor() {
        mHandler.removeCallbacks(mBackgroundUserStopTimeout);
        Runnable completor;
        synchronized (mLock) {
            completor = mBackgroundUserStopCompletor;
            mBackgroundUserStopCompletor = null;
        }
        // Avoid running the completor inside the lock.
        if (completor != null) {
            completor.run();
        }
    }

    @GuardedBy("mLock")
    private void waitForBackgroundUserStoppedLocked(int userId) {
        // Use uptimeMillis to not count the time in sleep.
        long waitStartTime = SystemClock.uptimeMillis();
        while (mStartedBackgroundUsers.get(userId) != USER_STOPPED) {
            try {
                mLock.wait(WAIT_FOR_USER_STOPPED_EVENT_TIMEOUT);
                if (SystemClock.uptimeMillis() - waitStartTime
                        >= WAIT_FOR_USER_STOPPED_EVENT_TIMEOUT) {
                    Slogf.e(TAG, "Timeout waiting for all started background users to stop");
                    break;
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Slogf.e(TAG, "Interrupted while waiting for background user:" + userId + " to stop",
                        e);
                break;
            }
        }
    }

    private void cleanupGarageMode(Runnable completor) {
        synchronized (mLock) {
            if (!mGarageModeActive) {
                if (completor != null) {
                    completor.run();
                }
                Slogf.e(TAG, "Trying to cleanup garage mode when it is inactive. Request ignored");
                return;
            }
            Slogf.i(TAG, "Cleaning up GarageMode");
            mGarageModeActive = false;
            // Always update the completor with the latest completor.
            mBackgroundUserStopCompletor = completor;
        }
        stopMonitoringThread();
        CarStatsLogHelper.logGarageModeStop();
        Slogf.i(TAG, "Stopping of background user queued. Total background users to stop: "
                    + "%d", mStartedBackgroundUsers.size());
        mHandler.removeCallbacks(mStopUserCheckRunnable);
        mHandler.removeCallbacks(mBackgroundUserStopTimeout);
        mHandler.postDelayed(mBackgroundUserStopTimeout, STOP_BACKGROUND_USER_TIMEOUT_MS);
        // This function is already in mHandler, so we can directly run.
        mStopUserCheckRunnable.run();
    }

    private void broadcastSignalToJobScheduler(boolean enableGarageMode) {
        Intent i = new Intent();
        i.setAction(enableGarageMode ? ACTION_GARAGE_MODE_ON : ACTION_GARAGE_MODE_OFF);
        i.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY | Intent.FLAG_RECEIVER_NO_ABORT);
        mController.sendBroadcast(i);
    }

    private void startMonitoringThread() {
        synchronized (mLock) {
            mIdleCheckerIsRunning = true;
            mAdditionalChecksToDo = ADDITIONAL_CHECKS_TO_DO;
        }
        mHandler.postDelayed(mRunnable, JOB_SNAPSHOT_INITIAL_UPDATE_MS);
    }

    private void stopMonitoringThread() {
        mHandler.removeCallbacks(mRunnable);
    }
}
