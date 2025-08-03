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

package android.app.notification.legacy20.cts;

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.Manifest.permission.REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL;
import static android.Manifest.permission.REVOKE_RUNTIME_PERMISSIONS;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.stubs.shared.NotificationHelper;
import android.app.stubs.shared.TestNotificationListener;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.permission.PermissionManager;
import android.permission.cts.PermissionUtils;
import android.provider.Telephony.Threads;
import android.service.notification.StatusBarNotification;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Home for tests that need to verify behavior for apps that target old sdk versions.
 */
@RunWith(AndroidJUnit4.class)
public class LegacyNotificationManager20Test {
    final String TAG = "LegacyNoMan20Test";

    private static final String PKG = "android.app.notification.legacy20.cts";

    private PackageManager mPackageManager;
    final String NOTIFICATION_CHANNEL_ID = "LegacyNotificationManagerTest";
    private NotificationManager mNotificationManager;
    private Context mContext;

    private TestNotificationListener mListener;
    private NotificationHelper mHelper;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        PermissionUtils.grantPermission(mContext.getPackageName(), POST_NOTIFICATIONS);
        mNotificationManager = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
        mNotificationManager.createNotificationChannel(new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, "name", NotificationManager.IMPORTANCE_DEFAULT));
        mPackageManager = mContext.getPackageManager();
        mHelper = new NotificationHelper(mContext);
        mHelper.disableListener(PKG);
    }

    @After
    public void tearDown() throws Exception {
        // Use test API to prevent PermissionManager from killing the test process when revoking
        // permission.
        SystemUtil.runWithShellPermissionIdentity(
                () -> mContext.getSystemService(PermissionManager.class)
                        .revokePostNotificationPermissionWithoutKillForTest(
                                mContext.getPackageName(),
                                Process.myUserHandle().getIdentifier()),
                REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL,
                REVOKE_RUNTIME_PERMISSIONS);
        mHelper.disableListener(PKG);
    }

    @Test
    public void testNotificationListener_cancelNotifications() throws Exception {
        mListener = mHelper.enableListener(PKG);
        assertNotNull(mListener);
        final int notificationId = 1;

        sendNotification(notificationId, R.drawable.icon_black);
        Thread.sleep(500); // wait for notification listener to receive notification

        StatusBarNotification sbn = mHelper.findPostedNotification(
                null, notificationId, NotificationHelper.SEARCH_TYPE.APP);

        mListener.cancelNotification(sbn.getPackageName(), sbn.getTag(), sbn.getId());
        if (mContext.getApplicationInfo().targetSdkVersion < Build.VERSION_CODES.LOLLIPOP) {
            assertTrue(mHelper.isNotificationGone(
                    notificationId, NotificationHelper.SEARCH_TYPE.APP));
        }

        sendNotification(notificationId, R.drawable.icon_black);
        Thread.sleep(500); // wait for notification listener to receive notification

        sbn = mHelper.findPostedNotification(
                null, notificationId, NotificationHelper.SEARCH_TYPE.APP);

        mListener.cancelNotifications(new String[]{ sbn.getKey() });
        assertTrue(mHelper.isNotificationGone(notificationId, NotificationHelper.SEARCH_TYPE.APP));
    }

    private void sendNotification(final int id, final int icon) throws Exception {
        sendNotification(id, null, icon);
    }

    private void sendNotification(final int id, String groupKey, final int icon) throws Exception {
        final Intent intent = new Intent(Intent.ACTION_MAIN, Threads.CONTENT_URI);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setAction(Intent.ACTION_MAIN);
        intent.setPackage(mContext.getPackageName());

        final PendingIntent pendingIntent =
                PendingIntent.getActivity(mContext, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        final Notification notification =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(icon)
                        .setWhen(System.currentTimeMillis())
                        .setContentTitle("notify#" + id)
                        .setContentText("This is #" + id + "notification  ")
                        .setContentIntent(pendingIntent)
                        .setGroup(groupKey)
                        .build();
        mNotificationManager.notify(id, notification);
    }
}
