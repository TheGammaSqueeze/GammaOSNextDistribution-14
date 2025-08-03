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

package android.telephony.ims.cts;

import static org.junit.Assert.fail;

import android.content.Context;
import android.os.Looper;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsManager;
import android.telephony.ims.ImsRcsManager;
import android.telephony.ims.ImsStateCallback;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ImsRcsManagerTest {

    private static int sTestSub = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

    @BeforeClass
    public static void beforeAllTests() {
        // assumeTrue() in @BeforeClass is not supported by our test runner.
        // Resort to the early exit.
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        sTestSub = ImsUtils.getPreferredActiveSubId();

        if (Looper.getMainLooper() == null) {
            Looper.prepareMainLooper();
        }
    }

    @Before
    public void beforeTest() {
        if (!ImsUtils.shouldTestImsService()) {
            return;
        }

        if (!SubscriptionManager.isValidSubscriptionId(sTestSub)) {
            fail("This test requires that there is a SIM in the device!");
        }
    }

    /**
     * Test Permissions on various APIs.
     */
    @Test
    public void testMethodPermissions() throws Exception {
        if (!ImsUtils.shouldTestTelephony()) {
            return;
        }

        // This verifies the permission checking in ITelephony,
        // not the IMS service's behavior.
        // Since SecurityException has the highest priority,
        // DEFAULT_SUBSCRIPTION_ID is enough to check permissions.
        // Though it throws an ImsException, we ignore that.
        if (sTestSub == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            sTestSub = SubscriptionManager.DEFAULT_SUBSCRIPTION_ID;
        }

        ImsManager imsManager = getContext().getSystemService(ImsManager.class);
        ImsRcsManager rcsManager = imsManager.getImsRcsManager(sTestSub);

        ImsStateCallback callback = new ImsStateCallback() {
            @Override
            public void onUnavailable(int reason) { }
            @Override
            public void onAvailable() { }
            @Override
            public void onError() { }
        };

        try {
            rcsManager.registerImsStateCallback(Runnable::run, callback);
            fail("registerImsStateCallback requires READ_PRECISE_PHONE_STATE, "
                    + "ACCESS_RCS_USER_CAPABILITY_EXCHANGE or "
                    + "READ_PRIVILEGED_PHONE_STATE permission.");
        } catch (SecurityException e) {
            //expected
        } catch (ImsException ie) {
            fail("registerImsStateCallback requires READ_PRECISE_PHONE_STATE, "
                    + "ACCESS_RCS_USER_CAPABILITY_EXCHANGE or "
                    + "READ_PRIVILEGED_PHONE_STATE permission.");
        }

        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(rcsManager,
                    m -> m.registerImsStateCallback(Runnable::run, callback),
                    ImsException.class, "android.permission.READ_PRECISE_PHONE_STATE");
        } catch (SecurityException e) {
            fail("registerImsStateCallback requires READ_PRECISE_PHONE_STATE permission.");
        } catch (ImsException ignore) {
            // don't care, permission check passed
        }

        try {
            rcsManager.unregisterImsStateCallback(callback);
        } catch (SecurityException e) {
            fail("uregisterImsStateCallback requires no permission.");
        }

        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(rcsManager,
                    m -> m.registerImsStateCallback(Runnable::run, callback),
                    ImsException.class, "android.permission.ACCESS_RCS_USER_CAPABILITY_EXCHANGE");
        } catch (SecurityException e) {
            fail("registerImsStateCallback requires "
                    + "ACCESS_RCS_USER_CAPABILITY_EXCHANGE permission.");
        } catch (ImsException ignore) {
            // don't care, permission check passed
        }

        try {
            rcsManager.unregisterImsStateCallback(callback);
        } catch (SecurityException e) {
            // unreachable, already passed permission check
            fail("uregisterImsStateCallback requires no permission.");
        }

        try {
            ShellIdentityUtils.invokeThrowableMethodWithShellPermissionsNoReturn(rcsManager,
                    m -> m.registerImsStateCallback(Runnable::run, callback),
                    ImsException.class, "android.permission.READ_PRIVILEGED_PHONE_STATE");
        } catch (SecurityException e) {
            fail("registerImsStateCallback requires READ_PRIVILEGED_PHONE_STATE permission.");
        } catch (ImsException ignore) {
            // don't care, permission check passed
        }

        try {
            rcsManager.unregisterImsStateCallback(callback);
        } catch (SecurityException e) {
            // unreachable, already passed permission check
            fail("uregisterImsStateCallback requires no permission.");
        }
    }

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }
}
