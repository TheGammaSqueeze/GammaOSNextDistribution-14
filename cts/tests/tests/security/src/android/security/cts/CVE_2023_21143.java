/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.security.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.sts.common.SystemUtil.poll;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.platform.test.annotations.AsbSecurityTest;
import android.service.notification.StatusBarNotification;
import android.widget.RemoteViews;

import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2023_21143 extends StsExtraBusinessLogicTestCase {
    private Instrumentation mInstrumentation = null;

    @Test
    @AsbSecurityTest(cveBugId = 268193777)
    public void testPocCVE_2023_21143() {
        Context context = null;
        try {
            mInstrumentation = getInstrumentation();
            context = mInstrumentation.getContext();

            // Getting pid of com.android.systemui
            final String systemuiPidCommand = context.getString(R.string.systemuiPidCommand);
            final int initialPidOfSystemUI = getPid(systemuiPidCommand);
            assumeTrue(context.getString(R.string.noPidFound), initialPidOfSystemUI != -1);

            // Adding a remoteview with large size image to notification in order to reproduce the
            // vulnerability
            final String packageName = context.getPackageName();
            RemoteViews remoteView = new RemoteViews(packageName, R.layout.cve_2023_21143);
            NotificationChannel channel =
                    new NotificationChannel(
                            context.getString(R.string.channelId),
                            context.getString(R.string.channelName),
                            NotificationManager.IMPORTANCE_HIGH);
            NotificationManager notificationManager =
                    context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            Notification notification =
                    new Notification.Builder(context, context.getString(R.string.channelId))
                            .setContentText(context.getString(R.string.notificationText))
                            .setSmallIcon(Icon.createWithData(new byte[0], 0, 0))
                            .setCustomContentView(remoteView)
                            .build();
            notificationManager.notify(1, notification);

            // Assumption failure if notification has not been posted successfully.
            assumeTrue(
                    context.getString(R.string.notificationSendingFailed),
                    poll(
                            () -> {
                                for (StatusBarNotification sbn :
                                        notificationManager.getActiveNotifications()) {
                                    if (sbn.getPackageName().equals(packageName)) {
                                        return true;
                                    }
                                }
                                return false;
                            }));

            // Without fix, the systemui crashes and it's pid changes.
            // Fail test only if pid has changed and notification is still visible.
            boolean isDeviceVulnerable = false;
            if (poll(() -> getPid(systemuiPidCommand) != initialPidOfSystemUI)) {
                for (StatusBarNotification sbn : notificationManager.getActiveNotifications()) {
                    isDeviceVulnerable = sbn.getPackageName().equals(context.getPackageName());
                    if (isDeviceVulnerable) {
                        break;
                    }
                }
            }
            assertFalse(context.getString(R.string.failMsg), isDeviceVulnerable);
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                // In case of without fix, a crash window stays.
                SystemUtil.runShellCommand(
                        mInstrumentation, context.getString(R.string.shellCommandForHome));
            } catch (Exception ignore) {
                // ignore
            }
        }
    }

    private int getPid(String commandToFindPid) {
        try {
            // Getting pid of com.android.systemui
            return Integer.parseInt(
                    SystemUtil.runShellCommand(mInstrumentation, commandToFindPid).trim());
        } catch (Exception e) {
            // ignore and return -1
            return -1;
        }
    }
}
