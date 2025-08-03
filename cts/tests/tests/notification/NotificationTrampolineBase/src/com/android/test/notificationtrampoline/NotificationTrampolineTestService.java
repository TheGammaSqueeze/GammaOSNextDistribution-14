/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.test.notificationtrampoline;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.ArraySet;

import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.stream.Stream;

/**
 * This is a bound service used in conjunction with trampoline tests in NotificationManagerTest.
 */
public class NotificationTrampolineTestService extends Service {
    private static final String TAG = "TrampolineTestService";
    private static final String NOTIFICATION_CHANNEL_ID = "cts/" + TAG;
    private static final String EXTRA_CALLBACK = "callback";
    private static final String EXTRA_ACTIVITY_REF = "activity_ref";
    private static final String RECEIVER_ACTION = ".TRAMPOLINE";
    private static final int MESSAGE_BROADCAST_NOTIFICATION = 1;
    private static final int MESSAGE_SERVICE_NOTIFICATION = 2;
    private static final int MESSAGE_CLICK_NOTIFICATION = 3;
    private static final int MESSAGE_CANCEL_ALL_NOTIFICATIONS = 4;
    private static final int TEST_MESSAGE_BROADCAST_RECEIVED = 1;
    private static final int TEST_MESSAGE_SERVICE_STARTED = 2;
    private static final int TEST_MESSAGE_ACTIVITY_STARTED = 3;
    private static final int TEST_MESSAGE_NOTIFICATION_CLICKED = 4;
    private static final int PI_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
    private static final String GROUP_KEY = "com.android.test.notificationtrampoline.GROUP_KEY";
    private static final int GROUP_ID = 0;

    private final Handler mHandler = new ServiceHandler();
    private final ActivityReference mActivityRef = new ActivityReference();
    private final Set<Integer> mPostedNotifications = new ArraySet<>();
    private NotificationManager mNotificationManager;
    private Messenger mMessenger;
    private BroadcastReceiver mReceiver;
    private Messenger mCallback;
    private String mReceiverAction;
    private Notification mGroupSummary = null;

    @Override
    public void onCreate() {
        mNotificationManager = getSystemService(NotificationManager.class);
        mMessenger = new Messenger(mHandler);
        mReceiverAction = getPackageName() + RECEIVER_ACTION;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    @Override
    public void onDestroy() {
        if (mReceiver != null) {
            unregisterReceiver(mReceiver);
        }
        WeakReference<Activity> activityRef = mActivityRef.activity;
        Activity activity = (activityRef != null) ? activityRef.get() : null;
        if (activity != null) {
            activity.finish();
        }
        for (int notificationId : mPostedNotifications) {
            mNotificationManager.cancel(notificationId);
        }
        mHandler.removeCallbacksAndMessages(null);
    }

    /** Suppressing since all messages are short-lived and we clear the queue on exit. */
    @SuppressLint("HandlerLeak")
    private class ServiceHandler extends Handler {
        @Override
        public void handleMessage(Message message) {
            Context context = NotificationTrampolineTestService.this;
            mCallback = (Messenger) message.obj;
            int notificationId = message.arg1;
            switch (message.what) {
                case MESSAGE_BROADCAST_NOTIFICATION: {
                    mReceiver = new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent broadcastIntent) {
                            sendMessageToTest(mCallback, TEST_MESSAGE_BROADCAST_RECEIVED, true);
                            startTargetActivity();
                        }
                    };
                    registerReceiver(mReceiver, new IntentFilter(mReceiverAction),
                            Context.RECEIVER_EXPORTED_UNAUDITED);
                    Intent intent = new Intent(mReceiverAction);
                    postNotification(notificationId,
                            PendingIntent.getBroadcast(context, 0, intent, PI_FLAGS));
                    break;
                }
                case MESSAGE_SERVICE_NOTIFICATION: {
                    // We use this service to act as the trampoline since the bound lifecycle (which
                    // is as long as the test is being executed) outlives the started (used by the
                    // trampoline) in this case.
                    Intent intent = new Intent(context, NotificationTrampolineTestService.class);
                    postNotification(notificationId,
                            PendingIntent.getService(context, 0, intent, PI_FLAGS));
                    break;
                }
                case MESSAGE_CLICK_NOTIFICATION: {
                    long count = Stream.of(mNotificationManager.getActiveNotifications())
                            .filter(sb -> sb.getId() == notificationId)
                            .flatMap(sb -> Stream.of(sb.getNotification().contentIntent,
                                    sb.getNotification().publicVersion.contentIntent))
                            .peek(intent -> {
                                try {
                                    intent.send();
                                } catch (PendingIntent.CanceledException e) {
                                    throw new IllegalStateException("Notification PI cancelled", e);
                                }
                            }).count();
                    sendMessageToTest(mCallback, TEST_MESSAGE_NOTIFICATION_CLICKED, count == 2);
                    break;
                }
                case MESSAGE_CANCEL_ALL_NOTIFICATIONS: {
                    cancelAllNotifications();
                    break;
                }
                default:
                    throw new AssertionError("Unknown message " + message.what);
            }
        }
    }

    @Override
    public int onStartCommand(Intent serviceIntent, int flags, int startId) {
        sendMessageToTest(mCallback, TEST_MESSAGE_SERVICE_STARTED, true);
        startTargetActivity();
        stopSelf(startId);
        return START_REDELIVER_INTENT;
    }

    private void postNotification(int notificationId, PendingIntent intent) {
        NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_ID, NotificationManager.IMPORTANCE_DEFAULT);
        mNotificationManager.createNotificationChannel(notificationChannel);
        // If group summary hasn't been initialized, create one. All posted notifications will be
        // added to this group.
        if (mGroupSummary == null) {
            mGroupSummary = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.ic_info)
                            .setContentTitle("Summary Title")
                            .setContentText("Summary Text")
                            .setGroup(GROUP_KEY)
                            .setGroupSummary(true)
                            .build();
            mNotificationManager.notify(GROUP_ID, mGroupSummary);
            mPostedNotifications.add(GROUP_ID);
        }

        Notification publicNotification =
                new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_info)
                        .setContentIntent(intent)
                        .build();

        Notification notification =
                new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.ic_info)
                        .setContentIntent(intent)
                        .setPublicVersion(publicNotification)
                        .setGroup(GROUP_KEY)
                        .build();

        mNotificationManager.notify(notificationId, notification);
        mPostedNotifications.add(notificationId);
    }

    private void cancelAllNotifications() {
        mNotificationManager.cancelAll();
        mPostedNotifications.clear();
    }

    private void startTargetActivity() {
        Intent intent = new Intent(this, TargetActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Bundle extras = new Bundle();
        extras.putParcelable(EXTRA_CALLBACK, mCallback);
        extras.putBinder(EXTRA_ACTIVITY_REF, mActivityRef);
        intent.putExtras(extras);
        startActivity(intent);
    }

    private static void sendMessageToTest(Messenger callback, int message, boolean success) {
        try {
            callback.send(Message.obtain(null, message, success ? 0 : 1, 0));
        } catch (RemoteException e) {
            throw new IllegalStateException(
                    "Couldn't send message " + message + " to test process", e);
        }
    }

    /**
     * A holder object that extends from Binder just so I can send it around using startActivity()
     * and avoid using static state. Works since the communication is local.
     */
    private static class ActivityReference extends Binder {
        public WeakReference<Activity> activity;
    }

    public static class TargetActivity extends Activity {
        @Override
        protected void onResume() {
            super.onResume();
            Messenger callback = getIntent().getParcelableExtra(EXTRA_CALLBACK);
            IBinder activityRef = getIntent().getExtras().getBinder(EXTRA_ACTIVITY_REF);
            if (activityRef instanceof ActivityReference) {
                ((ActivityReference) activityRef).activity = new WeakReference<>(this);
            }
            sendMessageToTest(callback, TEST_MESSAGE_ACTIVITY_STARTED, true);
        }
    }
}
