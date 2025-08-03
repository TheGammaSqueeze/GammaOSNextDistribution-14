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
package com.android.car;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.car.builtin.app.ActivityManagerHelper;
import android.car.builtin.app.ActivityManagerHelper.ProcessObserverCallback;
import android.car.builtin.os.ProcessHelper;
import android.car.builtin.os.UserManagerHelper;
import android.car.builtin.util.Slogf;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.proto.ProtoOutputStream;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service to monitor AMS for new Activity or Service launching.
 */
public class SystemActivityMonitoringService implements CarServiceBase {

    /** Injector for injecting some system related operations. */
    @VisibleForTesting
    /* package */ interface Injector {
        void registerProcessObserverCallback(ProcessObserverCallback callback);
        void unregisterProcessObserverCallback(ProcessObserverCallback callback);
        long getPassengerActivitySetProcessGroupRetryTimeoutMs();
    }

    // Passenger Activity might not be in top-app group in the 1st try. In that case, try
    // again after this time. Retry will happen only once.
    private static final long PASSENGER_ACTIVITY_SET_PROCESS_GROUP_RETRY_MS = 2_000;

    private final ProcessObserverCallback mProcessObserver = new ProcessObserver();

    private final HandlerThread mMonitorHandlerThread = CarServiceUtils.getHandlerThread(
            getClass().getSimpleName());
    private final ActivityMonitorHandler mHandler = new ActivityMonitorHandler(
            mMonitorHandlerThread.getLooper(), this);

    private final Context mContext;

    private final Injector mInjector;

    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final Map<Integer, Set<Integer>> mForegroundUidPids = new ArrayMap<>();

    @GuardedBy("mLock")
    private final List<ProcessObserverCallback> mCustomProcessObservers = new ArrayList<>();

    @GuardedBy("mLock")
    private boolean mAssignPassengerActivityToFgGroup;

    public SystemActivityMonitoringService(Context context) {
        this(context, new DefaultInjector());
    }

    @VisibleForTesting
    /*package*/ SystemActivityMonitoringService(Context context, Injector injector) {
        mContext = context;
        mInjector = injector;
    }

    @Override
    public void init() {
        boolean assignPassengerActivityToFgGroup = false;
        if (mContext.getResources().getBoolean(
                R.bool.config_assignPassengerActivityToForegroundCpuGroup)) {
            CarOccupantZoneService occupantService = CarLocalServices.getService(
                    CarOccupantZoneService.class);
            if (occupantService.hasDriverZone() && occupantService.hasPassengerZones()) {
                assignPassengerActivityToFgGroup = true;
            }
        }
        synchronized (mLock) {
            mAssignPassengerActivityToFgGroup = assignPassengerActivityToFgGroup;
        }
        // Monitoring both listeners are necessary as there are cases where one listener cannot
        // monitor activity change.
        mInjector.registerProcessObserverCallback(mProcessObserver);
    }

    @Override
    public void release() {
        mInjector.unregisterProcessObserverCallback(mProcessObserver);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("*SystemActivityMonitoringService*");
        writer.println(" Top Tasks per display:");
        synchronized (mLock) {
            writer.println(" Foreground uid-pids:");
            for (Integer key : mForegroundUidPids.keySet()) {
                Set<Integer> pids = mForegroundUidPids.get(key);
                if (pids == null) {
                    continue;
                }
                writer.println("uid:" + key + ", pids:" + Arrays.toString(pids.toArray()));
            }
            writer.println(
                    "mAssignPassengerActivityToFgGroup:" + mAssignPassengerActivityToFgGroup);
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {}

    /**
     * Returns {@code true} if given pid-uid pair is in foreground.
     */
    public boolean isInForeground(int pid, int uid) {
        Set<Integer> pids = getPidsOfForegroudApp(uid);
        if (pids == null) {
            return false;
        }
        return pids.contains(pid);
    }

    /**
     * Returns PIDs of foreground apps launched from the given UID.
     */
    @Nullable
    public Set<Integer> getPidsOfForegroudApp(int uid) {
        synchronized (mLock) {
            return mForegroundUidPids.get(uid);
        }
    }

    /** Registers a callback to get notified when the running state of a process has changed. */
    public void registerProcessObserverCallback(ProcessObserverCallback callback) {
        synchronized (mLock) {
            mCustomProcessObservers.add(callback);
        }
    }

    /** Unregisters the ProcessObserverCallback, if there is any. */
    public void unregisterProcessObserverCallback(ProcessObserverCallback callback) {
        synchronized (mLock) {
            mCustomProcessObservers.remove(callback);
        }
    }

    private void handleForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
        synchronized (mLock) {
            for (int i = 0; i < mCustomProcessObservers.size(); i++) {
                ProcessObserverCallback callback = mCustomProcessObservers.get(i);
                callback.onForegroundActivitiesChanged(pid, uid, foregroundActivities);
            }
            if (foregroundActivities) {
                Set<Integer> pids = mForegroundUidPids.get(uid);
                if (pids == null) {
                    pids = new ArraySet<Integer>();
                    mForegroundUidPids.put(uid, pids);
                }
                pids.add(pid);
            } else {
                doHandlePidGoneLocked(pid, uid);
            }
        }
    }

    private void handleProcessDied(int pid, int uid) {
        synchronized (mLock) {
            for (int i = 0; i < mCustomProcessObservers.size(); i++) {
                ProcessObserverCallback callback = mCustomProcessObservers.get(i);
                callback.onProcessDied(pid, uid);
            }
            doHandlePidGoneLocked(pid, uid);
        }
    }

    @GuardedBy("mLock")
    private void doHandlePidGoneLocked(int pid, int uid) {
        Set<Integer> pids = mForegroundUidPids.get(uid);
        if (pids != null) {
            pids.remove(pid);
            if (pids.isEmpty()) {
                mForegroundUidPids.remove(uid);
            }
        }
    }

    /**
     * Updates the process group for given PID if it is passenger app and returns true if it should
     * be retried.
     */
    private boolean doHandleProcessGroupForFgApp(int pid, int uid) {
        synchronized (mLock) {
            if (!mAssignPassengerActivityToFgGroup) {
                return false;
            }
        }
        int userId = UserManagerHelper.getUserId(uid);
        // Current user will be driver. So do not touch it.
        // User 0 will be either current user or common system UI which should run with higher
        // priority.
        if (userId == ActivityManager.getCurrentUser() || userId == UserManagerHelper.USER_SYSTEM) {
            return false;
        }
        // TODO(b/261783537) ignore profile of the current user

        CarServiceHelperWrapper helper = CarServiceHelperWrapper.getInstance();
        boolean shouldRetry = false;
        try {
            int processGroup = helper.getProcessGroup(pid);
            switch (processGroup) {
                case ProcessHelper.THREAD_GROUP_FOREGROUND:
                    // already in FG group, ignore
                    break;
                case ProcessHelper.THREAD_GROUP_TOP_APP:
                    // Changing to FOREGROUND requires setting it to DEFAULT
                    helper.setProcessGroup(pid, ProcessHelper.THREAD_GROUP_DEFAULT);
                    break;
                default:
                    // not in top-app yet, should retry
                    shouldRetry = true;
                    break;
            }
        } catch (Exception e) {
            Slogf.w(CarLog.TAG_AM, e, "Process group manipulation failed for pid:%d uid:%d",
                    pid, uid);
            // no need to retry as this PID may be already invalid.
        }
        return shouldRetry;
    }

    private void handleProcessGroupForFgApp(int pid, int uid) {
        if (doHandleProcessGroupForFgApp(pid, uid)) {
            mHandler.postDelayed(() -> doHandleProcessGroupForFgApp(pid, uid),
                    mInjector.getPassengerActivitySetProcessGroupRetryTimeoutMs());
        }
    }

    private class ProcessObserver extends ProcessObserverCallback {
        @Override
        public void onForegroundActivitiesChanged(int pid, int uid, boolean foregroundActivities) {
            if (Slogf.isLoggable(CarLog.TAG_AM, Log.DEBUG)) {
                Slogf.d(CarLog.TAG_AM,
                        String.format("onForegroundActivitiesChanged uid %d pid %d fg %b",
                                uid, pid, foregroundActivities));
            }
            if (foregroundActivities) {
                handleProcessGroupForFgApp(pid, uid);
            }
            mHandler.requestForegroundActivitiesChanged(pid, uid, foregroundActivities);
        }

        @Override
        public void onProcessDied(int pid, int uid) {
            mHandler.requestProcessDied(pid, uid);
        }
    }

    private static final class ActivityMonitorHandler extends Handler {
        private static final String TAG = ActivityMonitorHandler.class.getSimpleName();

        private static final int MSG_FOREGROUND_ACTIVITIES_CHANGED = 1;
        private static final int MSG_PROCESS_DIED = 2;

        private final WeakReference<SystemActivityMonitoringService> mService;

        private ActivityMonitorHandler(Looper looper, SystemActivityMonitoringService service) {
            super(looper);
            mService = new WeakReference<SystemActivityMonitoringService>(service);
        }

        private void requestForegroundActivitiesChanged(int pid, int uid,
                boolean foregroundActivities) {
            Message msg = obtainMessage(MSG_FOREGROUND_ACTIVITIES_CHANGED, pid, uid,
                    Boolean.valueOf(foregroundActivities));
            sendMessage(msg);
        }

        private void requestProcessDied(int pid, int uid) {
            Message msg = obtainMessage(MSG_PROCESS_DIED, pid, uid);
            sendMessage(msg);
        }

        @Override
        public void handleMessage(Message msg) {
            SystemActivityMonitoringService service = mService.get();
            if (service == null) {
                Slogf.i(TAG, "handleMessage null service");
                return;
            }
            switch (msg.what) {
                case MSG_FOREGROUND_ACTIVITIES_CHANGED:
                    service.handleForegroundActivitiesChanged(msg.arg1, msg.arg2,
                            (Boolean) msg.obj);
                    break;
                case MSG_PROCESS_DIED:
                    service.handleProcessDied(msg.arg1, msg.arg2);
                    break;
                default:
                    break;
            }
        }
    }

    private static class DefaultInjector implements Injector {
        @Override
        public void registerProcessObserverCallback(ProcessObserverCallback callback) {
            ActivityManagerHelper.registerProcessObserverCallback(callback);
        }

        @Override
        public void unregisterProcessObserverCallback(ProcessObserverCallback callback) {
            ActivityManagerHelper.unregisterProcessObserverCallback(callback);
        }

        @Override
        public long getPassengerActivitySetProcessGroupRetryTimeoutMs() {
            return PASSENGER_ACTIVITY_SET_PROCESS_GROUP_RETRY_MS;
        }
    }
}
