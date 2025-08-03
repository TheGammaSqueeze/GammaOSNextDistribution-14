/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.networkstack.tethering;

import static android.Manifest.permission.ACCESS_NETWORK_STATE;
import static android.Manifest.permission.TETHER_PRIVILEGED;
import static android.Manifest.permission.WRITE_SETTINGS;
import static android.content.pm.PackageManager.PERMISSION_DENIED;
import static android.net.TetheringManager.TETHERING_WIFI;
import static android.net.TetheringManager.TETHER_ERROR_NO_ACCESS_TETHERING_PERMISSION;
import static android.net.TetheringManager.TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION;
import static android.net.TetheringManager.TETHER_ERROR_NO_ERROR;
import static android.net.TetheringManager.TETHER_ERROR_UNSUPPORTED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.app.UiAutomation;
import android.content.Intent;
import android.net.IIntResultListener;
import android.net.ITetheringConnector;
import android.net.ITetheringEventCallback;
import android.net.TetheringManager;
import android.net.TetheringRequestParcel;
import android.net.ip.IpServer;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.IBinder;
import android.os.ResultReceiver;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.rule.ServiceTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.net.module.util.CollectionUtils;
import com.android.networkstack.tethering.MockTetheringService.MockTetheringConnector;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.function.Supplier;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class TetheringServiceTest {
    private static final String TEST_IFACE_NAME = "test_wlan0";
    private static final String TEST_CALLER_PKG = "com.android.shell";
    private static final String TEST_ATTRIBUTION_TAG = null;
    @Mock private ITetheringEventCallback mITetheringEventCallback;
    @Rule public ServiceTestRule mServiceTestRule;
    private Tethering mTethering;
    private Intent mMockServiceIntent;
    private MockTetheringConnector mMockConnector;
    private ITetheringConnector mTetheringConnector;
    private UiAutomation mUiAutomation;

    private class TestTetheringResult extends IIntResultListener.Stub {
        private int mResult = -1; // Default value that does not match any result code.
        @Override
        public void onResult(final int resultCode) {
            mResult = resultCode;
        }

        public void assertResult(final int expected) {
            assertEquals(expected, mResult);
        }
    }

    private class MyResultReceiver extends ResultReceiver {
        MyResultReceiver(Handler handler) {
            super(handler);
        }
        private int mResult = -1; // Default value that does not match any result code.
        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            mResult = resultCode;
        }

        public void assertResult(int expected) {
            assertEquals(expected, mResult);
        }
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mServiceTestRule = new ServiceTestRule();
        mMockServiceIntent = new Intent(
                InstrumentationRegistry.getTargetContext(),
                MockTetheringService.class);
        mMockConnector = (MockTetheringConnector) mServiceTestRule.bindService(mMockServiceIntent);
        mTetheringConnector = ITetheringConnector.Stub.asInterface(mMockConnector.getIBinder());
        final MockTetheringService service = mMockConnector.getService();
        mTethering = service.getTethering();
    }

    @After
    public void tearDown() throws Exception {
        mServiceTestRule.unbindService();
        mUiAutomation.dropShellPermissionIdentity();
    }

    private interface TestTetheringCall {
        void runTetheringCall(TestTetheringResult result) throws Exception;
    }

    private void runAsNoPermission(final TestTetheringCall test) throws Exception {
        runTetheringCall(test, true /* isTetheringAllowed */, new String[0]);
    }

    private void runAsTetherPrivileged(final TestTetheringCall test) throws Exception {
        runTetheringCall(test, true /* isTetheringAllowed */, TETHER_PRIVILEGED);
    }

    private void runAsAccessNetworkState(final TestTetheringCall test) throws Exception {
        runTetheringCall(test, true /* isTetheringAllowed */, ACCESS_NETWORK_STATE);
    }

    private void runAsWriteSettings(final TestTetheringCall test) throws Exception {
        runTetheringCall(test, true /* isTetheringAllowed */, WRITE_SETTINGS);
    }

    private void runAsTetheringDisallowed(final TestTetheringCall test) throws Exception {
        runTetheringCall(test, false /* isTetheringAllowed */, TETHER_PRIVILEGED);
    }

    private void runTetheringCall(final TestTetheringCall test, boolean isTetheringAllowed,
            String... permissions) throws Exception {
        // Allow the test to run even if ACCESS_NETWORK_STATE was granted at the APK level
        if (!CollectionUtils.contains(permissions, ACCESS_NETWORK_STATE)) {
            mMockConnector.setPermission(ACCESS_NETWORK_STATE, PERMISSION_DENIED);
        }

        if (permissions.length > 0) mUiAutomation.adoptShellPermissionIdentity(permissions);
        try {
            when(mTethering.isTetheringSupported()).thenReturn(true);
            when(mTethering.isTetheringAllowed()).thenReturn(isTetheringAllowed);
            test.runTetheringCall(new TestTetheringResult());
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
            mMockConnector.setPermission(ACCESS_NETWORK_STATE, null);
        }
    }

    private void verifyNoMoreInteractionsForTethering() {
        verifyNoMoreInteractions(mTethering);
        verifyNoMoreInteractions(mITetheringEventCallback);
        reset(mTethering, mITetheringEventCallback);
    }

    private void runTether(final TestTetheringResult result) throws Exception {
        mTetheringConnector.tether(TEST_IFACE_NAME, TEST_CALLER_PKG, TEST_ATTRIBUTION_TAG, result);
        verify(mTethering).isTetheringSupported();
        verify(mTethering).isTetheringAllowed();
        verify(mTethering).tether(TEST_IFACE_NAME, IpServer.STATE_TETHERED, result);
    }

    @Test
    public void testTether() throws Exception {
        runAsNoPermission((result) -> {
            mTetheringConnector.tether(TEST_IFACE_NAME, TEST_CALLER_PKG, TEST_ATTRIBUTION_TAG,
                    result);
            verify(mTethering).isTetherProvisioningRequired();
            result.assertResult(TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION);
            verifyNoMoreInteractionsForTethering();
        });

        runAsTetherPrivileged((result) -> {
            runTether(result);
            verifyNoMoreInteractionsForTethering();
        });

        runAsWriteSettings((result) -> {
            runTether(result);
            verify(mTethering).isTetherProvisioningRequired();
            verifyNoMoreInteractionsForTethering();
        });

        runAsTetheringDisallowed((result) -> {
            mTetheringConnector.tether(TEST_IFACE_NAME, TEST_CALLER_PKG, TEST_ATTRIBUTION_TAG,
                    result);
            verify(mTethering).isTetheringSupported();
            verify(mTethering).isTetheringAllowed();
            result.assertResult(TETHER_ERROR_UNSUPPORTED);
            verifyNoMoreInteractionsForTethering();
        });
    }

    private void runUnTether(final TestTetheringResult result) throws Exception {
        mTetheringConnector.untether(TEST_IFACE_NAME, TEST_CALLER_PKG, TEST_ATTRIBUTION_TAG,
                result);
        verify(mTethering).isTetheringSupported();
        verify(mTethering).isTetheringAllowed();
        verify(mTethering).untether(eq(TEST_IFACE_NAME), eq(result));
    }

    @Test
    public void testUntether() throws Exception {
        runAsNoPermission((result) -> {
            mTetheringConnector.untether(TEST_IFACE_NAME, TEST_CALLER_PKG, TEST_ATTRIBUTION_TAG,
                    result);
            verify(mTethering).isTetherProvisioningRequired();
            result.assertResult(TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION);
            verifyNoMoreInteractionsForTethering();
        });

        runAsTetherPrivileged((result) -> {
            runUnTether(result);
            verifyNoMoreInteractionsForTethering();
        });

        runAsWriteSettings((result) -> {
            runUnTether(result);
            verify(mTethering).isTetherProvisioningRequired();
            verifyNoMoreInteractionsForTethering();
        });

        runAsTetheringDisallowed((result) -> {
            mTetheringConnector.untether(TEST_IFACE_NAME, TEST_CALLER_PKG, TEST_ATTRIBUTION_TAG,
                    result);
            verify(mTethering).isTetheringSupported();
            verify(mTethering).isTetheringAllowed();
            result.assertResult(TETHER_ERROR_UNSUPPORTED);
            verifyNoMoreInteractionsForTethering();
        });
    }

    private void runSetUsbTethering(final TestTetheringResult result) throws Exception {
        doAnswer((invocation) -> {
            final IIntResultListener listener = invocation.getArgument(1);
            listener.onResult(TETHER_ERROR_NO_ERROR);
            return null;
        }).when(mTethering).setUsbTethering(anyBoolean(), any(IIntResultListener.class));
        mTetheringConnector.setUsbTethering(true /* enable */, TEST_CALLER_PKG,
                TEST_ATTRIBUTION_TAG, result);
        verify(mTethering).isTetheringSupported();
        verify(mTethering).isTetheringAllowed();
        verify(mTethering).setUsbTethering(eq(true) /* enable */, any(IIntResultListener.class));
        result.assertResult(TETHER_ERROR_NO_ERROR);
    }

    @Test
    public void testSetUsbTethering() throws Exception {
        runAsNoPermission((result) -> {
            mTetheringConnector.setUsbTethering(true /* enable */, TEST_CALLER_PKG,
                    TEST_ATTRIBUTION_TAG, result);
            verify(mTethering).isTetherProvisioningRequired();
            result.assertResult(TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION);
            verifyNoMoreInteractionsForTethering();
        });

        runAsTetherPrivileged((result) -> {
            runSetUsbTethering(result);
            verifyNoMoreInteractionsForTethering();
        });

        runAsWriteSettings((result) -> {
            runSetUsbTethering(result);
            verify(mTethering).isTetherProvisioningRequired();
            verifyNoMoreInteractionsForTethering();
        });

        runAsTetheringDisallowed((result) -> {
            mTetheringConnector.setUsbTethering(true /* enable */, TEST_CALLER_PKG,
                    TEST_ATTRIBUTION_TAG, result);
            verify(mTethering).isTetheringSupported();
            verify(mTethering).isTetheringAllowed();
            result.assertResult(TETHER_ERROR_UNSUPPORTED);
            verifyNoMoreInteractionsForTethering();
        });
    }

    private void runStartTethering(final TestTetheringResult result,
            final TetheringRequestParcel request) throws Exception {
        mTetheringConnector.startTethering(request, TEST_CALLER_PKG, TEST_ATTRIBUTION_TAG,
                result);
        verify(mTethering).isTetheringSupported();
        verify(mTethering).isTetheringAllowed();
        verify(mTethering).startTethering(eq(request), eq(TEST_CALLER_PKG), eq(result));
    }

    @Test
    public void testStartTethering() throws Exception {
        final TetheringRequestParcel request = new TetheringRequestParcel();
        request.tetheringType = TETHERING_WIFI;

        runAsNoPermission((result) -> {
            mTetheringConnector.startTethering(request, TEST_CALLER_PKG, TEST_ATTRIBUTION_TAG,
                    result);
            verify(mTethering).isTetherProvisioningRequired();
            result.assertResult(TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION);
            verifyNoMoreInteractionsForTethering();
        });

        runAsTetherPrivileged((result) -> {
            runStartTethering(result, request);
            verifyNoMoreInteractionsForTethering();
        });

        runAsWriteSettings((result) -> {
            runStartTethering(result, request);
            verify(mTethering).isTetherProvisioningRequired();
            verifyNoMoreInteractionsForTethering();
        });

        runAsTetheringDisallowed((result) -> {
            mTetheringConnector.startTethering(request, TEST_CALLER_PKG, TEST_ATTRIBUTION_TAG,
                    result);
            verify(mTethering).isTetheringSupported();
            verify(mTethering).isTetheringAllowed();
            result.assertResult(TETHER_ERROR_UNSUPPORTED);
            verifyNoMoreInteractionsForTethering();
        });
    }

    private void runStartTetheringAndVerifyNoPermission(final TestTetheringResult result)
            throws Exception {
        final TetheringRequestParcel request = new TetheringRequestParcel();
        request.tetheringType = TETHERING_WIFI;
        request.exemptFromEntitlementCheck = true;
        mTetheringConnector.startTethering(request, TEST_CALLER_PKG, TEST_ATTRIBUTION_TAG,
                result);
        result.assertResult(TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION);
        verifyNoMoreInteractionsForTethering();
    }

    @Test
    public void testFailToBypassEntitlementWithoutNeworkStackPermission() throws Exception {
        final TetheringRequestParcel request = new TetheringRequestParcel();
        request.tetheringType = TETHERING_WIFI;
        request.exemptFromEntitlementCheck = true;

        runAsNoPermission((result) -> {
            runStartTetheringAndVerifyNoPermission(result);
        });

        runAsTetherPrivileged((result) -> {
            runStartTetheringAndVerifyNoPermission(result);
        });

        runAsWriteSettings((result) -> {
            runStartTetheringAndVerifyNoPermission(result);
        });
    }

    private void runStopTethering(final TestTetheringResult result) throws Exception {
        mTetheringConnector.stopTethering(TETHERING_WIFI, TEST_CALLER_PKG,
                TEST_ATTRIBUTION_TAG, result);
        verify(mTethering).isTetheringSupported();
        verify(mTethering).isTetheringAllowed();
        verify(mTethering).stopTethering(TETHERING_WIFI);
        result.assertResult(TETHER_ERROR_NO_ERROR);
    }

    @Test
    public void testStopTethering() throws Exception {
        runAsNoPermission((result) -> {
            mTetheringConnector.stopTethering(TETHERING_WIFI, TEST_CALLER_PKG,
                    TEST_ATTRIBUTION_TAG, result);
            verify(mTethering).isTetherProvisioningRequired();
            result.assertResult(TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION);
            verifyNoMoreInteractionsForTethering();
        });

        runAsTetherPrivileged((result) -> {
            runStopTethering(result);
            verifyNoMoreInteractionsForTethering();
        });

        runAsWriteSettings((result) -> {
            runStopTethering(result);
            verify(mTethering).isTetherProvisioningRequired();
            verifyNoMoreInteractionsForTethering();
        });

        runAsTetheringDisallowed((result) -> {
            mTetheringConnector.stopTethering(TETHERING_WIFI, TEST_CALLER_PKG,
                    TEST_ATTRIBUTION_TAG, result);
            verify(mTethering).isTetheringSupported();
            verify(mTethering).isTetheringAllowed();
            result.assertResult(TETHER_ERROR_UNSUPPORTED);
            verifyNoMoreInteractionsForTethering();
        });
    }

    private void runRequestLatestTetheringEntitlementResult() throws Exception {
        final MyResultReceiver result = new MyResultReceiver(null);
        mTetheringConnector.requestLatestTetheringEntitlementResult(TETHERING_WIFI, result,
                true /* showEntitlementUi */, TEST_CALLER_PKG, TEST_ATTRIBUTION_TAG);
        verify(mTethering).isTetheringSupported();
        verify(mTethering).isTetheringAllowed();
        verify(mTethering).requestLatestTetheringEntitlementResult(eq(TETHERING_WIFI),
                eq(result), eq(true) /* showEntitlementUi */);
    }

    @Test
    public void testRequestLatestTetheringEntitlementResult() throws Exception {
        // Run as no permission.
        final MyResultReceiver result = new MyResultReceiver(null);
        mTetheringConnector.requestLatestTetheringEntitlementResult(TETHERING_WIFI, result,
                true /* showEntitlementUi */, TEST_CALLER_PKG, TEST_ATTRIBUTION_TAG);
        verify(mTethering).isTetherProvisioningRequired();
        result.assertResult(TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION);
        verifyNoMoreInteractions(mTethering);

        runAsTetherPrivileged((none) -> {
            runRequestLatestTetheringEntitlementResult();
            verifyNoMoreInteractionsForTethering();
        });

        runAsWriteSettings((none) -> {
            runRequestLatestTetheringEntitlementResult();
            verify(mTethering).isTetherProvisioningRequired();
            verifyNoMoreInteractionsForTethering();
        });

        runAsTetheringDisallowed((none) -> {
            final MyResultReceiver receiver = new MyResultReceiver(null);
            mTetheringConnector.requestLatestTetheringEntitlementResult(TETHERING_WIFI, receiver,
                    true /* showEntitlementUi */, TEST_CALLER_PKG, TEST_ATTRIBUTION_TAG);
            verify(mTethering).isTetheringSupported();
            verify(mTethering).isTetheringAllowed();
            receiver.assertResult(TETHER_ERROR_UNSUPPORTED);
            verifyNoMoreInteractionsForTethering();
        });
    }

    private void runRegisterTetheringEventCallback() throws Exception {
        mTetheringConnector.registerTetheringEventCallback(mITetheringEventCallback,
                TEST_CALLER_PKG);
        verify(mTethering).registerTetheringEventCallback(eq(mITetheringEventCallback));
    }

    @Test
    public void testRegisterTetheringEventCallback() throws Exception {
        runAsNoPermission((result) -> {
            mTetheringConnector.registerTetheringEventCallback(mITetheringEventCallback,
                    TEST_CALLER_PKG);
            verify(mITetheringEventCallback).onCallbackStopped(
                    TETHER_ERROR_NO_ACCESS_TETHERING_PERMISSION);
            verifyNoMoreInteractionsForTethering();
        });

        runAsTetherPrivileged((none) -> {
            runRegisterTetheringEventCallback();
            verifyNoMoreInteractionsForTethering();
        });

        runAsAccessNetworkState((none) -> {
            runRegisterTetheringEventCallback();
            verifyNoMoreInteractionsForTethering();
        });

        // should still be able to register callback even tethering is restricted.
        runAsTetheringDisallowed((result) -> {
            runRegisterTetheringEventCallback();
            verifyNoMoreInteractionsForTethering();
        });
    }

    private void runUnregisterTetheringEventCallback() throws Exception {
        mTetheringConnector.unregisterTetheringEventCallback(mITetheringEventCallback,
                TEST_CALLER_PKG);
        verify(mTethering).unregisterTetheringEventCallback(eq(mITetheringEventCallback));
    }

    @Test
    public void testUnregisterTetheringEventCallback() throws Exception {
        runAsNoPermission((result) -> {
            mTetheringConnector.unregisterTetheringEventCallback(mITetheringEventCallback,
                    TEST_CALLER_PKG);
            verify(mITetheringEventCallback).onCallbackStopped(
                    TETHER_ERROR_NO_ACCESS_TETHERING_PERMISSION);
            verifyNoMoreInteractionsForTethering();
        });

        runAsTetherPrivileged((none) -> {
            runUnregisterTetheringEventCallback();
            verifyNoMoreInteractionsForTethering();
        });

        runAsAccessNetworkState((none) -> {
            runUnregisterTetheringEventCallback();
            verifyNoMoreInteractionsForTethering();
        });

        // should still be able to unregister callback even tethering is restricted.
        runAsTetheringDisallowed((result) -> {
            runUnregisterTetheringEventCallback();
            verifyNoMoreInteractionsForTethering();
        });

    }

    private void runStopAllTethering(final TestTetheringResult result) throws Exception {
        mTetheringConnector.stopAllTethering(TEST_CALLER_PKG, TEST_ATTRIBUTION_TAG, result);
        verify(mTethering).isTetheringSupported();
        verify(mTethering).isTetheringAllowed();
        verify(mTethering).untetherAll();
        result.assertResult(TETHER_ERROR_NO_ERROR);
    }

    @Test
    public void testStopAllTethering() throws Exception {
        runAsNoPermission((result) -> {
            mTetheringConnector.stopAllTethering(TEST_CALLER_PKG, TEST_ATTRIBUTION_TAG, result);
            verify(mTethering).isTetherProvisioningRequired();
            result.assertResult(TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION);
            verifyNoMoreInteractionsForTethering();
        });

        runAsTetherPrivileged((result) -> {
            runStopAllTethering(result);
            verifyNoMoreInteractionsForTethering();
        });

        runAsWriteSettings((result) -> {
            runStopAllTethering(result);
            verify(mTethering).isTetherProvisioningRequired();
            verifyNoMoreInteractionsForTethering();
        });

        runAsTetheringDisallowed((result) -> {
            mTetheringConnector.stopAllTethering(TEST_CALLER_PKG, TEST_ATTRIBUTION_TAG, result);
            verify(mTethering).isTetheringSupported();
            verify(mTethering).isTetheringAllowed();
            result.assertResult(TETHER_ERROR_UNSUPPORTED);
            verifyNoMoreInteractionsForTethering();
        });
    }

    private void runIsTetheringSupported(final TestTetheringResult result) throws Exception {
        mTetheringConnector.isTetheringSupported(TEST_CALLER_PKG, TEST_ATTRIBUTION_TAG, result);
        verify(mTethering).isTetheringSupported();
        verify(mTethering).isTetheringAllowed();
        result.assertResult(TETHER_ERROR_NO_ERROR);
    }

    @Test
    public void testIsTetheringSupported() throws Exception {
        runAsNoPermission((result) -> {
            mTetheringConnector.isTetheringSupported(TEST_CALLER_PKG, TEST_ATTRIBUTION_TAG,
                    result);
            verify(mTethering).isTetherProvisioningRequired();
            result.assertResult(TETHER_ERROR_NO_CHANGE_TETHERING_PERMISSION);
            verifyNoMoreInteractionsForTethering();
        });

        runAsTetherPrivileged((result) -> {
            runIsTetheringSupported(result);
            verifyNoMoreInteractionsForTethering();
        });

        runAsWriteSettings((result) -> {
            runIsTetheringSupported(result);
            verify(mTethering).isTetherProvisioningRequired();
            verifyNoMoreInteractionsForTethering();
        });

        runAsTetheringDisallowed((result) -> {
            mTetheringConnector.isTetheringSupported(TEST_CALLER_PKG, TEST_ATTRIBUTION_TAG,
                    result);
            verify(mTethering).isTetheringSupported();
            verify(mTethering).isTetheringAllowed();
            result.assertResult(TETHER_ERROR_UNSUPPORTED);
            verifyNoMoreInteractionsForTethering();
        });
    }

    private class ConnectorSupplier<T> implements Supplier<T> {
        private T mResult = null;

        public void set(T result) {
            mResult = result;
        }

        @Override
        public T get() {
            return mResult;
        }
    }

    private void forceGc() {
        System.gc();
        System.runFinalization();
        System.gc();
    }

    @Test
    public void testTetheringManagerLeak() throws Exception {
        runAsAccessNetworkState((none) -> {
            final ArrayList<ITetheringEventCallback> callbacks = new ArrayList<>();
            final ConditionVariable registeredCv = new ConditionVariable(false);
            doAnswer((invocation) -> {
                final Object[] args = invocation.getArguments();
                callbacks.add((ITetheringEventCallback) args[0]);
                registeredCv.open();
                return null;
            }).when(mTethering).registerTetheringEventCallback(any());

            doAnswer((invocation) -> {
                final Object[] args = invocation.getArguments();
                callbacks.remove((ITetheringEventCallback) args[0]);
                return null;
            }).when(mTethering).unregisterTetheringEventCallback(any());

            final ConnectorSupplier<IBinder> supplier = new ConnectorSupplier<>();

            TetheringManager tm = new TetheringManager(mMockConnector.getService(), supplier);
            assertNotNull(tm);
            assertEquals("Internal callback should not be registered", 0, callbacks.size());

            final WeakReference<TetheringManager> weakTm = new WeakReference(tm);
            assertNotNull(weakTm.get());

            // TetheringManager couldn't be GCed because pollingConnector thread implicitly
            // reference TetheringManager object.
            tm = null;
            forceGc();
            assertNotNull(weakTm.get());

            // After getting connector, pollingConnector thread stops and internal callback is
            // registered.
            supplier.set(mMockConnector.getIBinder());
            final long timeout = 500L;
            if (!registeredCv.block(timeout)) {
                fail("TetheringManager poll connector fail after " + timeout + " ms");
            }
            assertEquals("Internal callback is not registered", 1, callbacks.size());
            assertNotNull(weakTm.get());

            // Calling System.gc() or System.runFinalization() doesn't guarantee GCs or finalizers
            // are executed synchronously. The finalizer is called after GC on a separate thread.
            final int attempts = 600;
            final long waitIntervalMs = 50;
            for (int i = 0; i < attempts; i++) {
                forceGc();
                if (weakTm.get() == null && callbacks.size() == 0) break;

                Thread.sleep(waitIntervalMs);
            }
            assertNull("TetheringManager weak reference is not null", weakTm.get());
            assertEquals("Internal callback is not unregistered", 0, callbacks.size());
        });
    }
}
