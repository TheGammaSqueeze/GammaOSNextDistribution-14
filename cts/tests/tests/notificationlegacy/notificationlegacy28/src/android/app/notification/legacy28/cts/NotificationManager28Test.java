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

package android.app.notification.legacy28.cts;

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.Manifest.permission.REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL;
import static android.Manifest.permission.REVOKE_RUNTIME_PERMISSIONS;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.stubs.shared.NotificationHelper;
import android.app.stubs.shared.TestNotificationListener;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.permission.PermissionManager;
import android.permission.cts.PermissionUtils;
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
public class NotificationManager28Test {
    final String TAG = "LegacyNoManTest28";
    private static final String PKG = "android.app.notification.legacy28.cts";
    final String NOTIFICATION_CHANNEL_ID = "LegacyNoManTest28";
    private NotificationManager mNotificationManager;
    private Context mContext;
    private NotificationHelper mHelper;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        PermissionUtils.grantPermission(mContext.getPackageName(), POST_NOTIFICATIONS);
        mNotificationManager = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
        mNotificationManager.createNotificationChannel(new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, "name", NotificationManager.IMPORTANCE_DEFAULT));
        mHelper = new NotificationHelper(mContext);
        assertNotNull(mHelper.enableListener(PKG));
    }

    @After
    public void tearDown() throws Exception {
        mHelper.disableListener(PKG);
        // Use test API to prevent PermissionManager from killing the test process when revoking
        // permission.
        SystemUtil.runWithShellPermissionIdentity(
                () -> mContext.getSystemService(PermissionManager.class)
                        .revokePostNotificationPermissionWithoutKillForTest(
                                mContext.getPackageName(),
                                Process.myUserHandle().getIdentifier()),
                REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL,
                REVOKE_RUNTIME_PERMISSIONS);
    }

    @Test
    public void testPostFullScreenIntent_noPermission() {
        // No Full screen intent permission; but full screen intent should still be allowed
        int id = 6000;
        final Notification notification =
                new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(android.R.drawable.sym_def_app_icon)
                        .setWhen(System.currentTimeMillis())
                        .setFullScreenIntent(getPendingIntent(), true)
                        .setContentText("This is #FSI notification")
                        .setContentIntent(getPendingIntent())
                        .build();
        mNotificationManager.notify(id, notification);

        StatusBarNotification n = mHelper.findPostedNotification(
                null, id, NotificationHelper.SEARCH_TYPE.POSTED);
        assertNotNull(n);
        assertEquals(notification.fullScreenIntent, n.getNotification().fullScreenIntent);
    }

    private PendingIntent getPendingIntent() {
        return PendingIntent.getActivity(
                mContext, 0, new Intent(mContext, this.getClass()), PendingIntent.FLAG_IMMUTABLE);
    }
}
