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

package android.telecom.cts;

import static android.telecom.cts.TestUtils.waitOnAllHandlers;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.app.role.RoleManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.telecom.TelecomManager;

import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class NullBindingCallScreeningServiceTest extends BaseTelecomTestWithMockServices {
    private static final int ASYNC_TIMEOUT = 10000;
    private static final String ROLE_CALL_SCREENING = RoleManager.ROLE_CALL_SCREENING;
    private static final Uri TEST_OUTGOING_NUMBER = Uri.fromParts("tel", "6505551212", null);

    private RoleManager mRoleManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (!mShouldTestTelecom) {
            return;
        }
        NullBindingCallScreeningService.enableNullBindingCallScreeningService(mContext);
        mRoleManager = (RoleManager) mContext.getSystemService(Context.ROLE_SERVICE);
        setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        // Ensure NullBindingCallScreeningService pkg holds the call screening role.
        addRoleHolder(ROLE_CALL_SCREENING,
                NullBindingCallScreeningService.class.getPackage().getName());
        NullBindingCallScreeningService.resetBindLatches();
    }

    @Override
    protected void tearDown() throws Exception {
        if (mShouldTestTelecom) {
            // Remove the app from the screening role.
            removeRoleHolder(ROLE_CALL_SCREENING,
                    NullBindingCallScreeningService.class.getPackage().getName());
            NullBindingCallScreeningService.disableNullBindingCallScreeningService(mContext);
        }
        super.tearDown();
    }

    public void testNullBindingOnIncomingCall() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }
        Uri testNumber = createRandomTestNumber();
        Bundle extras = new Bundle();
        extras.putParcelable(TelecomManager.EXTRA_INCOMING_CALL_ADDRESS, testNumber);

        // Verify that binding latch counts are reset for testing
        assertBindLatchInit();
        // Add a new incoming call
        mTelecomManager.addNewIncomingCall(TestUtils.TEST_PHONE_ACCOUNT_HANDLE, extras);
        // Assert unbind after onBind return a null service
        assertBindLatchCountDown();
        // Wait until the new incoming call is processed. Needed for proper tear down.
        waitOnAllHandlers(getInstrumentation());
    }

    public void testNullBindingOnOutgoingCall() throws Exception {
        if (!mShouldTestTelecom  || !TestUtils.hasTelephonyFeature(mContext)) {
            return;
        }
        Uri testNumber = createRandomTestNumber();
        Bundle extras = new Bundle();
        extras.putParcelable(TestUtils.EXTRA_PHONE_NUMBER, TEST_OUTGOING_NUMBER);
        extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                TestUtils.TEST_PHONE_ACCOUNT_HANDLE);

        // Verify that binding latch counts are reset for testing
        assertBindLatchInit();
        // Create a new outgoing call.
        mTelecomManager.placeCall(testNumber, extras);
        // Assert unbind after onBind return a null service
        assertBindLatchCountDown();
    }

    private void assertBindLatchInit() {
        assertTrue(NullBindingCallScreeningService.sUnbindLatch.getCount() == 1);
        assertTrue(NullBindingCallScreeningService.sBindLatch.getCount() == 1);
    }

    private void assertBindLatchCountDown() {
        assertTrue(TestUtils.waitForLatchCountDown(NullBindingCallScreeningService.sBindLatch));
        assertTrue(TestUtils.waitForLatchCountDown(NullBindingCallScreeningService.sUnbindLatch));
    }

    private void addRoleHolder(String roleName, String packageName)
            throws Exception {
        UserHandle user = Process.myUserHandle();
        Executor executor = mContext.getMainExecutor();
        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue(1);

        runWithShellPermissionIdentity(() -> mRoleManager.addRoleHolderAsUser(roleName,
                packageName, RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP, user, executor,
                successful -> {
                    try {
                        queue.put(successful);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }));
        boolean result = queue.poll(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
        assertTrue(result);
    }

    private void removeRoleHolder(String roleName, String packageName)
            throws Exception {
        UserHandle user = Process.myUserHandle();
        Executor executor = mContext.getMainExecutor();
        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue(1);

        runWithShellPermissionIdentity(() -> mRoleManager.removeRoleHolderAsUser(roleName,
                packageName, RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP, user, executor,
                successful -> {
                    try {
                        queue.put(successful);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }));
        boolean result = queue.poll(ASYNC_TIMEOUT, TimeUnit.MILLISECONDS);
        assertTrue(result);
    }
}

