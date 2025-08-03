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

package android.platform.tests;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.platform.helpers.HelperAccessor;
import android.platform.helpers.IAutoNotificationHelper;
import android.platform.helpers.IAutoNotificationMockingHelper;
import android.platform.test.rules.ConditionalIgnore;
import android.platform.test.rules.ConditionalIgnoreRule;
import android.platform.test.rules.IgnoreOnPortrait;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class NotificationTest {
    @Rule public ConditionalIgnoreRule rule = new ConditionalIgnoreRule();

    private HelperAccessor<IAutoNotificationHelper> mNotificationHelper;
    private HelperAccessor<IAutoNotificationMockingHelper> mNotificationMockingHelper;

    private static String NOTIFICATION_TITLE = "AUTO TEST NOTIFICATION";

    public NotificationTest() {
        mNotificationHelper = new HelperAccessor<>(IAutoNotificationHelper.class);
        mNotificationMockingHelper = new HelperAccessor<>(IAutoNotificationMockingHelper.class);
    }


    @Before
    public void clearAllNotification() {
        mNotificationMockingHelper.get().clearAllNotification();
    }

    @After
    public void exit() {
        mNotificationHelper.get().exit();
    }

    @Test
    public void testOpenCloseNotification() {
        mNotificationHelper.get().open();
        assertTrue("Notification did not open.", mNotificationHelper.get().isAppInForeground());
        mNotificationHelper.get().exit();
        assertFalse("Notification did not close.", mNotificationHelper.get().isAppInForeground());
    }

    @Test
    public void testClearAllNotification() {
        mNotificationMockingHelper.get().postNotifications(1);
        mNotificationHelper.get().tapClearAllBtn();
        mNotificationHelper.get().exit();
        assertFalse(
                "Notifications were not cleared.",
                mNotificationHelper.get().checkNotificationExists(NOTIFICATION_TITLE));
    }

    @Test
    public void testPostNotification() {
        mNotificationMockingHelper.get().postNotifications(1);
        assertTrue(
                "Unable to find posted notification.",
                mNotificationHelper.get().checkNotificationExists(NOTIFICATION_TITLE));
    }

    @Test
    @ConditionalIgnore(condition = IgnoreOnPortrait.class)
    public void testSwipeAwayNotification() {
        mNotificationHelper.get().tapClearAllBtn();
        mNotificationMockingHelper.get().postNotifications(1);
        assertTrue(
                "Unable to find posted notification.",
                mNotificationHelper.get().checkNotificationExists(NOTIFICATION_TITLE));
        mNotificationHelper.get().removeNotification(NOTIFICATION_TITLE);
        assertFalse(
                "Notifications were not cleared.",
                mNotificationHelper.get().checkNotificationExists(NOTIFICATION_TITLE));
    }

    @Test
    public void testManageButton() {
        mNotificationMockingHelper.get().postNotifications(1);
        mNotificationHelper.get().clickManageBtn();
        assertTrue(
                "Notification Settings did not open.",
                mNotificationHelper.get().isNotificationSettingsOpened());
    }

    @Test
    @ConditionalIgnore(condition = IgnoreOnPortrait.class)
    public void testRecentAndOlderNotifications() {
        mNotificationHelper.get().tapClearAllBtn();
        mNotificationMockingHelper.get().postNotifications(1);
        mNotificationHelper.get().open();
        assertTrue(
                "Notification are not present under recent category",
                mNotificationHelper.get().isRecentNotification());
        mNotificationHelper.get().exit();
        mNotificationHelper.get().open();
        assertTrue(
                "Notification are not present under older category",
                mNotificationHelper.get().isOlderNotification());
    }
}
