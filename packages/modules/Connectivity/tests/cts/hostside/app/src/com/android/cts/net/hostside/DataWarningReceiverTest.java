/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.cts.net.hostside;

import static com.android.cts.net.hostside.NetworkPolicyTestUtils.clearSnoozeTimestamps;

import android.content.pm.PackageManager;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionPlan;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Direction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.UiAutomatorUtils2;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.time.Period;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;

public class DataWarningReceiverTest extends AbstractRestrictBackgroundNetworkTestCase {

    @Before
    public void setUp() throws Exception {
        super.setUp();

        clearSnoozeTimestamps();
        registerBroadcastReceiver();
        turnScreenOn();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testSnoozeWarningNotReceived() throws Exception {
        Assume.assumeTrue("Feature not supported: " + PackageManager.FEATURE_TELEPHONY,
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY));
        final SubscriptionManager sm = mContext.getSystemService(SubscriptionManager.class);
        final int subId = SubscriptionManager.getDefaultDataSubscriptionId();
        Assume.assumeTrue("Valid subId not found",
                subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        setSubPlanOwner(subId, TEST_PKG);
        final List<SubscriptionPlan> originalPlans = sm.getSubscriptionPlans(subId);
        try {
            // In NetworkPolicyManagerService class, we set the data warning bytes to 90% of
            // data limit bytes. So, create the subscription plan in such a way this data warning
            // threshold is already reached.
            final SubscriptionPlan plan = SubscriptionPlan.Builder
                    .createRecurring(ZonedDateTime.parse("2007-03-14T00:00:00.000Z"),
                            Period.ofMonths(1))
                    .setTitle("CTS")
                    .setDataLimit(1_000_000_000, SubscriptionPlan.LIMIT_BEHAVIOR_THROTTLED)
                    .setDataUsage(999_000_000, System.currentTimeMillis())
                    .build();
            sm.setSubscriptionPlans(subId, Arrays.asList(plan));
            final UiDevice uiDevice = UiDevice.getInstance(mInstrumentation);
            uiDevice.openNotification();
            try {
                final UiObject2 uiObject = UiAutomatorUtils2.waitFindObject(
                        By.text("Data warning"));
                Assume.assumeNotNull(uiObject);
                uiObject.wait(Until.clickable(true), 10_000L);
                uiObject.getParent().swipe(Direction.RIGHT, 1.0f);
            } catch (Throwable t) {
                Assume.assumeNoException(
                        "Error occurred while finding and swiping the notification", t);
            }
            assertSnoozeWarningNotReceived();
            uiDevice.pressHome();
        } finally {
            sm.setSubscriptionPlans(subId, originalPlans);
            setSubPlanOwner(subId, "");
        }
    }

    private static void setSubPlanOwner(int subId, String packageName) throws Exception {
        SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                "cmd netpolicy set sub-plan-owner " + subId + " " + packageName);
    }
}
