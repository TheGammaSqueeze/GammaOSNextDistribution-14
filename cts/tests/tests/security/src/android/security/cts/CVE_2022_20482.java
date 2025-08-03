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

package android.security.cts;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNoException;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
public class CVE_2022_20482 extends StsExtraBusinessLogicTestCase {

    /**
     * b/240422263
     * Vulnerable library   : services.jar
     * Vulnerable module    : Not applicable
     * Is Play managed      : No
     */
    @AsbSecurityTest(cveBugId = 240422263)
    @Test
    public void testPocCVE_2022_20482() {
        final int notificationChannelLimit = 10000; // 2 * NOTIFICATION_CHANNEL_COUNT_LIMIT
        final String notificationChannelId = "NotificationChannelId";
        final String notificationChannelName = "NotificationChannelName";
        boolean isVulnerable = true;
        int notificationChannelCount = 0;
        NotificationManager notificationManager = null;
        ArrayList<String> notificationChannelIds = new ArrayList<>();
        try {
            Context context = getApplicationContext();
            notificationManager = context.getSystemService(NotificationManager.class);

            // Store total number of notification channels present before test run
            notificationChannelCount = notificationManager.getNotificationChannels().size();

            // Create 'notificationChannelLimit' notification channels
            for (int i = 0; i < notificationChannelLimit; ++i) {
                String uniqueNotificationChannelId = notificationChannelId + i;
                NotificationChannel notificationChannel =
                        new NotificationChannel(uniqueNotificationChannelId,
                                notificationChannelName, NotificationManager.IMPORTANCE_DEFAULT);

                // Create notification channel
                notificationManager.createNotificationChannel(notificationChannel);

                // Add notification channel id in list(for deleting notification channel later)
                notificationChannelIds.add(uniqueNotificationChannelId);
            }
        } catch (Exception e) {
            isVulnerable = false;
            if (!(e instanceof IllegalStateException)
                    || !e.getMessage().contains("Limit exceed; cannot create more channels")) {
                assumeNoException("Unexpected exception occurred!", e);
            }
        } finally {
            try {
                // Retrieve total number of notification channels added by test so that the
                // test fails only if all notification channels from test were added successfully
                notificationChannelCount = notificationManager.getNotificationChannels().size()
                        - notificationChannelCount;
                boolean flagAllNotificationChannelsAdded =
                        notificationChannelCount == notificationChannelLimit;

                // Delete notification channels created earlier
                for (String id : notificationChannelIds) {
                    notificationManager.deleteNotificationChannel(id);
                }

                // Fail if all notification channels from test were added successfully without
                // any occurrence of IllegalStateException
                assertFalse(
                        "Device is vulnerable to b/240422263! Permanent denial of service"
                                + " possible via NotificationManager#createNotificationChannel",
                        isVulnerable && flagAllNotificationChannelsAdded);
            } catch (Exception ignoredException) {
            }
        }
    }
}
