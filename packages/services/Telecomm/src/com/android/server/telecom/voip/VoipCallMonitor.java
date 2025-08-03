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

package com.android.server.telecom.voip;

import static android.app.ForegroundServiceDelegationOptions.DELEGATION_SERVICE_PHONE_CALL;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL;

import android.app.ActivityManager;
import android.app.ActivityManagerInternal;
import android.app.ForegroundServiceDelegationOptions;
import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.telecom.Log;
import android.telecom.PhoneAccountHandle;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.LocalServices;
import com.android.server.telecom.Call;

import com.android.server.telecom.CallsManagerListenerBase;
import com.android.server.telecom.LogUtils;
import com.android.server.telecom.LoggedHandlerExecutor;
import com.android.server.telecom.TelecomSystem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class VoipCallMonitor extends CallsManagerListenerBase {

    private final List<Call> mNotificationPendingCalls;
    // Same notification may be passed as different object in onNotificationPosted and
    // onNotificationRemoved. Use its string as key to cache ongoing notifications.
    private final Map<NotificationInfo, Call> mNotificationInfoToCallMap;
    private final Map<PhoneAccountHandle, Set<Call>> mAccountHandleToCallMap;
    private ActivityManagerInternal mActivityManagerInternal;
    private final Map<PhoneAccountHandle, ServiceConnection> mServices;
    private NotificationListenerService mNotificationListener;
    private final Object mLock = new Object();
    private final HandlerThread mHandlerThread;
    private final Handler mHandler;
    private final Context mContext;
    private List<NotificationInfo> mCachedNotifications;
    private TelecomSystem.SyncRoot mSyncRoot;

    public VoipCallMonitor(Context context, TelecomSystem.SyncRoot lock) {
        mSyncRoot = lock;
        mContext = context;
        mHandlerThread = new HandlerThread(this.getClass().getSimpleName());
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mNotificationPendingCalls = new ArrayList<>();
        mCachedNotifications = new ArrayList<>();
        mNotificationInfoToCallMap = new HashMap<>();
        mServices = new HashMap<>();
        mAccountHandleToCallMap = new HashMap<>();
        mActivityManagerInternal = LocalServices.getService(ActivityManagerInternal.class);

        mNotificationListener = new NotificationListenerService() {
            @Override
            public void onNotificationPosted(StatusBarNotification sbn) {
                synchronized (mLock) {
                    if (sbn.getNotification().isStyle(Notification.CallStyle.class)) {
                        NotificationInfo info = new NotificationInfo(sbn.getPackageName(),
                                sbn.getUser());
                        boolean sbnMatched = false;
                        for (Call call : mNotificationPendingCalls) {
                            if (info.matchesCall(call)) {
                                Log.i(this, "onNotificationPosted: found a pending "
                                                + "callId=[%s] for the call notification w/ "
                                                + "id=[%s]",
                                        call.getId(), sbn.getId());
                                mNotificationPendingCalls.remove(call);
                                mNotificationInfoToCallMap.put(info, call);
                                sbnMatched = true;
                                break;
                            }
                        }
                        if (!sbnMatched &&
                                !mCachedNotifications.contains(info) /* don't re-add if update */) {
                            Log.i(this, "onNotificationPosted: could not find a"
                                            + "call for the call notification w/ id=[%s]",
                                    sbn.getId());
                            // notification may post before we started to monitor the call, cache
                            // this notification and try to match it later with new added call.
                            mCachedNotifications.add(info);
                        }
                    }
                }
            }

            @Override
            public void onNotificationRemoved(StatusBarNotification sbn) {
                synchronized (mLock) {
                    NotificationInfo info = new NotificationInfo(sbn.getPackageName(),
                            sbn.getUser());
                    mCachedNotifications.remove(info);
                    if (mNotificationInfoToCallMap.isEmpty()) {
                        return;
                    }
                    Call call = mNotificationInfoToCallMap.getOrDefault(info, null);
                    if (call != null) {
                        // TODO: fix potential bug for multiple calls of same voip app.
                        mNotificationInfoToCallMap.remove(info, call);
                        stopFGSDelegation(call);
                    }
                }
            }
        };

    }

    public void startMonitor() {
        try {
            mNotificationListener.registerAsSystemService(mContext,
                    new ComponentName(this.getClass().getPackageName(),
                            this.getClass().getCanonicalName()), ActivityManager.getCurrentUser());
        } catch (RemoteException e) {
            Log.e(this, e, "Cannot register notification listener");
        }
    }

    public void stopMonitor() {
        try {
            mNotificationListener.unregisterAsSystemService();
        } catch (RemoteException e) {
            Log.e(this, e, "Cannot unregister notification listener");
        }
    }

    @Override
    public void onCallAdded(Call call) {
        if (!call.isTransactionalCall()) {
            return;
        }

        synchronized (mLock) {
            PhoneAccountHandle phoneAccountHandle = call.getTargetPhoneAccount();
            Set<Call> callList = mAccountHandleToCallMap.computeIfAbsent(phoneAccountHandle,
                    k -> new HashSet<>());
            callList.add(call);
            CompletableFuture.completedFuture(null).thenComposeAsync(
                    (x) -> {
                        startFGSDelegation(call.getCallingPackageIdentity().mCallingPackagePid,
                                call.getCallingPackageIdentity().mCallingPackageUid, call);
                        return null;
                    }, new LoggedHandlerExecutor(mHandler, "VCM.oCA", mSyncRoot));
        }
    }

    @Override
    public void onCallRemoved(Call call) {
        if (!call.isTransactionalCall()) {
            return;
        }

        synchronized (mLock) {
            stopMonitorWorks(call);
            PhoneAccountHandle phoneAccountHandle = call.getTargetPhoneAccount();
            Set<Call> callList = mAccountHandleToCallMap.computeIfAbsent(phoneAccountHandle,
                    k -> new HashSet<>());
            callList.remove(call);

            if (callList.isEmpty()) {
                stopFGSDelegation(call);
            }
        }
    }

    private void startFGSDelegation(int pid, int uid, Call call) {
        Log.i(this, "startFGSDelegation for call %s", call.getId());
        if (mActivityManagerInternal != null) {
            PhoneAccountHandle handle = call.getTargetPhoneAccount();
            ForegroundServiceDelegationOptions options = new ForegroundServiceDelegationOptions(pid,
                    uid, handle.getComponentName().getPackageName(), null /* clientAppThread */,
                    false /* isSticky */, String.valueOf(handle.hashCode()),
                    FOREGROUND_SERVICE_TYPE_PHONE_CALL |
                    FOREGROUND_SERVICE_TYPE_MICROPHONE |
                    FOREGROUND_SERVICE_TYPE_CAMERA |
                    FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE /* foregroundServiceTypes */,
                    DELEGATION_SERVICE_PHONE_CALL /* delegationService */);
            ServiceConnection fgsConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    mServices.put(handle, this);
                    startMonitorWorks(call);
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    mServices.remove(handle);
                }
            };
            try {
                if (mActivityManagerInternal
                        .startForegroundServiceDelegate(options, fgsConnection)) {
                    Log.addEvent(call, LogUtils.Events.GAINED_FGS_DELEGATION);
                } else {
                    Log.addEvent(call, LogUtils.Events.GAIN_FGS_DELEGATION_FAILED);
                }
            } catch (Exception e) {
                Log.i(this, "startForegroundServiceDelegate failed due to: " + e);
            }
        }
    }

    @VisibleForTesting
    public void stopFGSDelegation(Call call) {
        synchronized (mLock) {
            Log.i(this, "stopFGSDelegation of call %s", call);
            PhoneAccountHandle handle = call.getTargetPhoneAccount();
            Set<Call> calls = mAccountHandleToCallMap.get(handle);

            // Every call for the package that is losing foreground service delegation should be
            // removed from tracking maps/contains in this class
            if (calls != null) {
                for (Call c : calls) {
                    stopMonitorWorks(c); // remove the call from tacking in this class
                }
            }

            mAccountHandleToCallMap.remove(handle);

            if (mActivityManagerInternal != null) {
                ServiceConnection fgsConnection = mServices.get(handle);
                if (fgsConnection != null) {
                    mActivityManagerInternal.stopForegroundServiceDelegate(fgsConnection);
                    Log.addEvent(call, LogUtils.Events.LOST_FGS_DELEGATION);
                }
            }
        }
    }

    private void startMonitorWorks(Call call) {
        startMonitorNotification(call);
    }

    private void stopMonitorWorks(Call call) {
        stopMonitorNotification(call);
    }

    private void startMonitorNotification(Call call) {
        synchronized (mLock) {
            boolean sbnMatched = false;
            for (NotificationInfo info : mCachedNotifications) {
                if (info.matchesCall(call)) {
                    Log.i(this, "startMonitorNotification: found a cached call "
                            + "notification for call=[%s]", call);
                    mCachedNotifications.remove(info);
                    mNotificationInfoToCallMap.put(info, call);
                    sbnMatched = true;
                    break;
                }
            }
            if (!sbnMatched) {
                // Only continue to
                Log.i(this, "startMonitorNotification: could not find a call"
                        + " notification for the call=[%s];", call);
                mNotificationPendingCalls.add(call);
                CompletableFuture<Void> future = new CompletableFuture<>();
                mHandler.postDelayed(() -> future.complete(null), 5000L);
                future.thenComposeAsync(
                        (x) -> {
                            if (mNotificationPendingCalls.contains(call)) {
                                Log.i(this, "Notification for voip-call %s haven't "
                                        + "posted in time, stop delegation.", call.getId());
                                stopFGSDelegation(call);
                                mNotificationPendingCalls.remove(call);
                                return null;
                            }
                            return null;
                        }, new LoggedHandlerExecutor(mHandler, "VCM.sMN", mSyncRoot));
            }
        }
    }

    private void stopMonitorNotification(Call call) {
        mNotificationPendingCalls.remove(call);
    }

    @VisibleForTesting
    public void setActivityManagerInternal(ActivityManagerInternal ami) {
        mActivityManagerInternal = ami;
    }

    private static class NotificationInfo extends Object {
        private String mPackageName;
        private UserHandle mUserHandle;

        NotificationInfo(String packageName, UserHandle userHandle) {
            mPackageName = packageName;
            mUserHandle = userHandle;
        }

        boolean matchesCall(Call call) {
            PhoneAccountHandle accountHandle = call.getTargetPhoneAccount();
            return mPackageName != null && mPackageName.equals(
                    accountHandle.getComponentName().getPackageName())
                    && mUserHandle != null && mUserHandle.equals(accountHandle.getUserHandle());
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof NotificationInfo)) {
                return false;
            }
            NotificationInfo that = (NotificationInfo) obj;
            return Objects.equals(this.mPackageName, that.mPackageName)
                    && Objects.equals(this.mUserHandle, that.mUserHandle);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mPackageName, mUserHandle);
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("{ NotificationInfo: [mPackageName: ")
                    .append(mPackageName)
                    .append("], [mUserHandle=")
                    .append(mUserHandle)
                    .append("]  }");
            return sb.toString();
        }
    }

    @VisibleForTesting
    public void postNotification(StatusBarNotification statusBarNotification) {
        mNotificationListener.onNotificationPosted(statusBarNotification);
    }

    @VisibleForTesting
    public void removeNotification(StatusBarNotification statusBarNotification) {
        mNotificationListener.onNotificationRemoved(statusBarNotification);
    }

    @VisibleForTesting
    public Set<Call> getCallsForHandle(PhoneAccountHandle handle){
        return mAccountHandleToCallMap.get(handle);
    }
}
