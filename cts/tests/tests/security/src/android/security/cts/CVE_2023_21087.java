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

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;

import android.app.NotificationChannelGroup;
import android.app.NotificationManager;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2023_21087 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 261723753)
    @Test
    public void testPocCVE_2023_21087() {
        try {
            // Taking reference from the constant of the same name located at
            // path:services\core\java\com\android\server\notification\PreferencesHelper.java
            // and using twice it's original value as notificationChannelGroupCountLimit.
            final int notificationChannelGroupCountLimit = 12000;

            // Adding notification channel groups more than the set limit.
            NotificationManager notificationManager =
                    getApplicationContext().getSystemService(NotificationManager.class);
            try {
                for (int i = 0; i < notificationChannelGroupCountLimit; ++i) {
                    NotificationChannelGroup group =
                            new NotificationChannelGroup(String.valueOf(i), String.valueOf(i));
                    notificationManager.createNotificationChannelGroup(group);
                }
                fail(
                        "Device is vulnerable to b/261723753 !! Allowed to create notification"
                                + " channel groups more than the current count limit of 6000");
            } catch (IllegalStateException e) {

                // In the presence of fix adding notification channel group above the limit
                // will cause an IllegalStateException to occur so ignore.
            }
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
