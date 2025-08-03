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

import static android.telecom.cts.TestUtils.TEST_PHONE_ACCOUNT_HANDLE;

import android.Manifest;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.IBinder;
import android.os.RemoteException;
import android.telecom.cts.nonuiincallservicewoexport.CtsNonUiInCallServiceWoExportControl;
import android.telecom.cts.nonuiincallservicewoexport.ICtsNonUiInCallServiceWoExportControl;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import junit.framework.TestCase;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Test class for the package android.telecom.cts.NonUiInCallServiceWoExport
 */
public class NonUiInCallServiceWoExportTest extends BaseTelecomTestWithMockServices {

    private static final String TAG = NonUiInCallServiceWoExportTest.class.getSimpleName();
    private static final String TARGET_NON_UI_IN_CALL_SERVICE =
            "android.telecom.cts.nonuiincallservicewoexport";
    private static final String TELECOM_BASE_COMMAND =
            "telecom is-non-ui-in-call-service-bound";
    private static final String TELECOM_COMMAND_TO_QUERY_SERVICE =
            TELECOM_BASE_COMMAND + " " + TARGET_NON_UI_IN_CALL_SERVICE;

    private final ComponentName mControlComponentName =
            ComponentName.createRelative(
                    CtsNonUiInCallServiceWoExportControl.class.getPackage().getName(),
                    CtsNonUiInCallServiceWoExportControl.class.getName());

    private ServiceConnection mServiceConnection;
    ICtsNonUiInCallServiceWoExportControl mControlInterface;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        if (mShouldTestTelecom) {
            setupConnectionService(null, FLAG_REGISTER | FLAG_ENABLE);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (mShouldTestTelecom) {
            mTelecomManager.unregisterPhoneAccount(TEST_PHONE_ACCOUNT_HANDLE);
        }
        super.tearDown();
    }

    /**
     * Test that ensures the Telecom stack can bind to a non-ui InCallService that is NOT exported.
     *
     * This will catch Telecom stacks that are not a part of the android.uid.system process
     * AND set the exported property of a non-ui InCallService (in the AndroidManifest) to false.
     * Such services will NOT bind and potentially cause unwanted behavior.
     *
     * see b/198715680 for more details.
     *
     * @throws Exception
     */
    public void testTelecomCanBindToNonUiInCallServiceThatIsNotExported() throws Exception {
        if (!mShouldTestTelecom) {
            return;
        }

        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        try {
            automation.adoptShellPermissionIdentity(Manifest.permission.CONTROL_INCALL_EXPERIENCE);
            setUpControlForBindingInterface();
            // start a new incoming call to test if the telecom stack can
            // bind the non-exported non-ui InCallService under test
            addAndVerifyNewIncomingCall(createTestNumber(), new Bundle());

            // attempt to bind via the control interface...
            boolean hasBound = mControlInterface.waitForBindRequest();
            assertTrue("InCall was NOT bound to", hasBound);

            // assert that it was bound to successfully by querying the
            // non-exported non-ui-InCallServices from InCallController
            assertEquals(String.format("Unable to bind to the [%s] InCallService!",
                            TARGET_NON_UI_IN_CALL_SERVICE),
                    String.valueOf(true),
                    TestUtils.executeShellCommand(instrumentation,
                            TELECOM_COMMAND_TO_QUERY_SERVICE));

        } finally {
            automation.dropShellPermissionIdentity();
            cleanUpInterfaceAndService();
        }
    }

    private void setUpControlForBindingInterface() throws Exception {
        final Intent bindIntent = new Intent(
                CtsNonUiInCallServiceWoExportControl.CONTROL_INTERFACE_ACTION);
        LinkedBlockingQueue<ICtsNonUiInCallServiceWoExportControl> result =
                new LinkedBlockingQueue<>(1);

        ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                Log.i(TAG, "onServiceConnected: " + name);
                result.offer(ICtsNonUiInCallServiceWoExportControl.Stub.asInterface(service));
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                Log.i(TAG, "onServiceDisconnected: " + name);

            }
        };

        bindIntent.setComponent(mControlComponentName);

        if (!mContext.bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE)) {
            TestCase.fail("Failed to get control interface -- bind error");
        }

        mServiceConnection = serviceConnection;

        mControlInterface = result.poll(TestUtils.WAIT_FOR_STATE_CHANGE_TIMEOUT_MS,
                TimeUnit.MILLISECONDS);
    }

    private void cleanUpInterfaceAndService() throws RemoteException {
        try {
            mControlInterface.kill();
        } catch (DeadObjectException e) {
            //expected
        }
        mContext.unbindService(mServiceConnection);
    }
}
