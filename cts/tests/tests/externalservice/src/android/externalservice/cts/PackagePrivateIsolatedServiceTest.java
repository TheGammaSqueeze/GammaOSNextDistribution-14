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

package android.externalservice.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.flags.Flags;
import android.externalservice.common.RunningServiceInfo;
import android.os.ConditionVariable;
import android.os.IBinder;
import android.os.Messenger;
import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.support.test.uiautomator.UiDevice;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RequiresFlagsEnabled(Flags.FLAG_ENABLE_BIND_PACKAGE_ISOLATED_PROCESS)
@RunWith(AndroidJUnit4.class)
public class PackagePrivateIsolatedServiceTest {
    static final String sServicePackage = "android.externalservice.service";
    static final Context sContext = getInstrumentation().getTargetContext();
    static final String CTS = "android.externalservice.cts";
    private static final String TAG = "PackageIsolatedServiceTest";

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private final ConditionVariable mCondition = new ConditionVariable(false);
    private final Connection mConnection = new Connection(mCondition);

    private UiDevice mDevice;

    @Before
    public void setup() throws Exception {
        final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mDevice = UiDevice.getInstance(instrumentation);
    }

    /**
     * Teardown
     */
    @After
    public void tearDown() {
        if (mConnection.mService != null) {
            sContext.unbindService(mConnection);
        }
    }

    /**
     * Tests that an external app can bind to an exported isolatedProcess service with
     * BIND_PACKAGE_ISOLATED_PROCESS
     */
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_BIND_PACKAGE_ISOLATED_PROCESS)
    @Test
    public void testBasicConnection() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage,
                sServicePackage + ".PackagePrivateExportedService"));
        mCondition.close();

        assertTrue(
                sContext
                        .bindIsolatedService(
                                intent,
                                Context.BIND_AUTO_CREATE
                                        | Context.BIND_PACKAGE_ISOLATED_PROCESS,
                                "testInstance",
                                getInstrumentation().getTargetContext().getMainExecutor(),
                                mConnection));

        assertTrue(mCondition.block(RunningServiceInfo.CONDITION_TIMEOUT));
        assertEquals(sContext.getPackageName(), CTS);
        assertEquals(sServicePackage, mConnection.mName.getPackageName());
    }

    /**
     * Tests that an external app can bind to an exported isolatedProcess service with
     * BIND_PACKAGE_ISOLATED_PROCESS even when allowSharedIsolatedProcess is false.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_BIND_PACKAGE_ISOLATED_PROCESS)
    @Test
    public void testBindExportedServiceSharedProcessNotAllowed() {
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(
                        sServicePackage,
                        sServicePackage + ".ExportedServiceSharedProcessNotAllowed"));
        mCondition.close();

        assertTrue(
                sContext
                        .bindIsolatedService(
                                intent,
                                Context.BIND_AUTO_CREATE
                                        | Context.BIND_PACKAGE_ISOLATED_PROCESS,
                                "testInstance",
                                sContext.getMainExecutor(),
                                mConnection));

        assertTrue(mCondition.block(RunningServiceInfo.CONDITION_TIMEOUT));
        assertEquals(getInstrumentation().getTargetContext().getPackageName(), CTS);
        assertEquals(sServicePackage, mConnection.mName.getPackageName());
    }

    /**
     * Tests that an external app can bind to multiple exported isolatedProcess service with
     * BIND_PACKAGE_ISOLATED_PROCESS
     */
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_BIND_PACKAGE_ISOLATED_PROCESS)
    @Test
    public void testBindMultipleExportedServiceS() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage,
                sServicePackage + ".PackagePrivateExportedService"));
        mCondition.close();

        assertTrue(
                sContext
                        .bindIsolatedService(
                                intent,
                                Context.BIND_AUTO_CREATE
                                        | Context.BIND_PACKAGE_ISOLATED_PROCESS,
                                "testInstance",
                                sContext.getMainExecutor(),
                                mConnection));

        assertTrue(mCondition.block(RunningServiceInfo.CONDITION_TIMEOUT));
        assertEquals(sContext.getPackageName(), CTS);
        assertEquals(sServicePackage, mConnection.mName.getPackageName());

        RunningServiceInfo serviceInfo1 = identifyService(new Messenger(mConnection.mService));

        PackagePrivateIsolatedServiceTest.Connection connection2 =
                new PackagePrivateIsolatedServiceTest.Connection(mCondition);
        intent = new Intent();
        intent.setComponent(
                new ComponentName(
                        sServicePackage,
                        sServicePackage + ".ExportedServiceSharedProcessNotAllowed"));
        mCondition.close();

        assertTrue(
                sContext
                        .bindIsolatedService(
                                intent,
                                Context.BIND_AUTO_CREATE
                                        | Context.BIND_PACKAGE_ISOLATED_PROCESS,
                                "testInstance",
                                sContext.getMainExecutor(),
                                connection2));

        assertTrue(mCondition.block(RunningServiceInfo.CONDITION_TIMEOUT));
        assertEquals(sContext.getPackageName(), CTS);
        assertEquals(sServicePackage, connection2.mName.getPackageName());

        RunningServiceInfo serviceInfo2 = identifyService(new Messenger(connection2.mService));

        assertThat(serviceInfo1).isNotNull();
        assertThat(serviceInfo2).isNotNull();
        assertEquals(serviceInfo1.pid, serviceInfo2.pid);
        assertEquals(serviceInfo1.uid, serviceInfo2.uid);
    }

    /**
     * Tests that BIND_PACKAGE_ISOLATED_PROCESS requires the service specify it's process and not
     * the default main process.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_BIND_PACKAGE_ISOLATED_PROCESS)
    @Test
    public void testFailBindDefaultProcessService() {
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(sServicePackage,
                        sServicePackage + ".ExportedService"));
        assertThrows(
                SecurityException.class,
                () ->
                        sContext
                                .bindService(
                                        intent,
                                        mConnection,
                                        Context.BIND_AUTO_CREATE
                                                | Context.BIND_PACKAGE_ISOLATED_PROCESS));
    }

    /**
     * Tests that BIND_PACKAGE_ISOLATED_PROCESS requires the service be an isolatedProcess.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_BIND_PACKAGE_ISOLATED_PROCESS)
    @Test
    public void testFailBindExportedNonIsolated() {
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(sServicePackage,
                        sServicePackage + ".ExportedNonIsolatedService"));
        assertThrows(
                SecurityException.class,
                () ->
                        sContext
                                .bindService(
                                        intent,
                                        mConnection,
                                        Context.BIND_AUTO_CREATE
                                                | Context.BIND_PACKAGE_ISOLATED_PROCESS));
    }

    /**
     * Tests that BIND_PACKAGE_ISOLATED_PROCESS requires the service be an isolatedProcess.
     * This should apply even if the service specifies a non default process.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_BIND_PACKAGE_ISOLATED_PROCESS)
    @Test
    public void testFailBindNonDefaultProcessExportedNonIsolated() {
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(sServicePackage,
                        sServicePackage + ".PackagePrivateExportedNonIsolatedService"));
        assertThrows(
                SecurityException.class,
                () ->
                        sContext
                                .bindService(
                                        intent,
                                        mConnection,
                                        Context.BIND_AUTO_CREATE
                                                | Context.BIND_PACKAGE_ISOLATED_PROCESS));
    }

    /**
     * Tests that BIND_PACKAGE_ISOLATED_PROCESS does not work with plain isolatedProcess services.
     * BIND_PACKAGE_ISOLATED_PROCESS should not work with a non exported & non external service.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_BIND_PACKAGE_ISOLATED_PROCESS)
    @Test
    public void testFailBindPlainIsolatedService() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage,
                sServicePackage + ".IsolatedService"));

        assertThrows(
                SecurityException.class,
                () ->
                        sContext
                                .bindIsolatedService(
                                        intent,
                                        Context.BIND_AUTO_CREATE
                                                | Context.BIND_PACKAGE_ISOLATED_PROCESS,
                                        "testInstance",
                                        sContext.getMainExecutor(),
                                        mConnection));
    }

    /**
     * Tests that BIND_PACKAGE_ISOLATED_PROCESS does not work with plain (non default process)
     * isolatedProcess services. BIND_PACKAGE_ISOLATED_PROCESS should not work with a non exported
     * & non external service.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_BIND_PACKAGE_ISOLATED_PROCESS)
    @Test
    public void testFailBindNonDefaultProcessIsolatedService() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage,
                sServicePackage + ".PackagePrivateIsolatedService"));

        assertThrows(
                SecurityException.class,
                () ->
                        sContext
                                .bindIsolatedService(
                                        intent,
                                        Context.BIND_AUTO_CREATE
                                                | Context.BIND_PACKAGE_ISOLATED_PROCESS,
                                        "testInstance",
                                        sContext.getMainExecutor(),
                                        mConnection));
    }

    /**
     * Tests that BIND_PACKAGE_ISOLATED_PROCESS does not work with external, non exported services.
     * Plain external services are not package private.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_BIND_PACKAGE_ISOLATED_PROCESS)
    @Test
    public void testFailBindPlainExternalIsolatedService() {
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(sServicePackage,
                        sServicePackage + ".ExternalNonExportedService"));

        assertThrows(
                SecurityException.class,
                () ->
                        sContext
                                .bindIsolatedService(
                                        intent,
                                        Context.BIND_AUTO_CREATE
                                                | Context.BIND_PACKAGE_ISOLATED_PROCESS,
                                        "testInstance",
                                        sContext.getMainExecutor(),
                                        mConnection));
    }

    /**
     * Tests that BIND_PACKAGE_ISOLATED_PROCESS does not work with external, non exported services.
     * Plain (non default process) external services are not package private.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_BIND_PACKAGE_ISOLATED_PROCESS)
    @Test
    public void testFailBindNonDefaultProcessExternalIsolatedService() {
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(sServicePackage,
                        sServicePackage + ".PackagePrivateExternalNonExportedService"));

        assertThrows(
                SecurityException.class,
                () ->
                        sContext
                                .bindIsolatedService(
                                        intent,
                                        Context.BIND_AUTO_CREATE
                                                | Context.BIND_PACKAGE_ISOLATED_PROCESS,
                                        "testInstance",
                                        sContext.getMainExecutor(),
                                        mConnection));
    }

    /**
     * Tests that BIND_PACKAGE_ISOLATED_PROCESS does not work with external, non exported services,
     * even when BIND_EXTERNAL_SERVICE is set.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_BIND_PACKAGE_ISOLATED_PROCESS)
    @Test
    public void testFailBindPlainExternalIsolatedServiceWithFlag() {
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(sServicePackage,
                        sServicePackage + ".ExternalNonExportedService"));

        assertThrows(
                SecurityException.class,
                () ->
                        sContext
                                .bindIsolatedService(
                                        intent,
                                        Context.BIND_AUTO_CREATE
                                                | Context.BIND_EXTERNAL_SERVICE
                                                | Context.BIND_PACKAGE_ISOLATED_PROCESS,
                                        "testInstance",
                                        sContext.getMainExecutor(),
                                        mConnection));
    }

    /**
     * Tests that BIND_PACKAGE_ISOLATED_PROCESS does not work with external, non exported services,
     * even when BIND_EXTERNAL_SERVICE is set.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_BIND_PACKAGE_ISOLATED_PROCESS)
    @Test
    public void testFailBindNonDefaultProcessExternalIsolatedServiceWithFlag() {
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(sServicePackage,
                        sServicePackage + ".PackagePrivateExternalNonExportedService"));

        assertThrows(
                SecurityException.class,
                () ->
                        sContext
                                .bindIsolatedService(
                                        intent,
                                        Context.BIND_AUTO_CREATE
                                                | Context.BIND_EXTERNAL_SERVICE
                                                | Context.BIND_PACKAGE_ISOLATED_PROCESS,
                                        "testInstance",
                                        sContext.getMainExecutor(),
                                        mConnection));
    }

    /**
     * Tests that BIND_PACKAGE_ISOLATED_PROCESS does not work with external, exported services.
     * External services must specify BIND_EXTERNAL_SERVICE.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_BIND_PACKAGE_ISOLATED_PROCESS)
    @Test
    public void testFailBindExportedExternalIsolatedService() {
        Intent intent = new Intent();
        intent.setComponent(
                new ComponentName(sServicePackage, sServicePackage + ".ExternalService"));

        assertThrows(
                SecurityException.class,
                () ->
                        sContext
                                .bindIsolatedService(
                                        intent,
                                        Context.BIND_AUTO_CREATE
                                                | Context.BIND_PACKAGE_ISOLATED_PROCESS
                                                | Context.BIND_EXTERNAL_SERVICE,
                                        "testInstance",
                                        sContext.getMainExecutor(),
                                        mConnection));
    }

    /**
     * Tests that both BIND_PACKAGE_ISOLATED_PROCESS and BIND_SHARED_ISOLATED_PROCESS cannot be set
     * at the same time.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_BIND_PACKAGE_ISOLATED_PROCESS)
    @Test
    public void testFailPrivateSharedAndPublicSharedNotAllowed() {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(sServicePackage,
                sServicePackage + ".ExportedService"));
        mCondition.close();

        assertThrows(
                SecurityException.class,
                () ->
                        sContext
                                .bindIsolatedService(
                                        intent,
                                        Context.BIND_AUTO_CREATE
                                                | Context.BIND_SHARED_ISOLATED_PROCESS
                                                | Context.BIND_PACKAGE_ISOLATED_PROCESS,
                                        "testInstance",
                                        sContext.getMainExecutor(),
                                        mConnection));
    }

    /**
     * Given a Messenger, this will message the service to retrieve its UID, PID, and package name.
     * On success, returns a RunningServiceInfo. On failure, returns null.
     */
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_BIND_PACKAGE_ISOLATED_PROCESS)
    private RunningServiceInfo identifyService(Messenger service) {
        try {
            return RunningServiceInfo.identifyService(service, TAG, mCondition);
        } catch (RemoteException e) {
            fail("Unexpected remote exception: " + e);
            return null;
        }
    }

    private static class Connection implements ServiceConnection {
        IBinder mService = null;
        ComponentName mName = null;
        ConditionVariable mConditionVariable;

        Connection(ConditionVariable conditionVariable) {
            mConditionVariable = conditionVariable;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected " + name);
            this.mService = service;
            this.mName = name;
            mConditionVariable.open();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected " + name);
        }
    }
}
