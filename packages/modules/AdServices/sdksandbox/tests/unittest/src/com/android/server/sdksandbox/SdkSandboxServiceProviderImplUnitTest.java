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

package com.android.server.sdksandbox;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.app.sdksandbox.testutils.FakeSdkSandboxService;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.Process;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.LocalManagerRegistry;
import com.android.server.am.ActivityManagerLocal;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoSession;

/** Unit tests for {@link SdkSandboxServiceProviderImpl}. */
public class SdkSandboxServiceProviderImplUnitTest {

    private static final String TEST_PACKAGE = "com.android.server.sdksandbox.tests";

    private MockitoSession mStaticMockSession = null;
    private Context mSpyContext;
    private ActivityManagerLocal mAmLocal;
    private SdkSandboxServiceProviderImpl mServiceProvider;
    private CallingInfo mCallingInfo;
    private FakeServiceConnection mServiceConnection;

    @Before
    public void setup() throws Exception {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(LocalManagerRegistry.class)
                        .startMocking();

        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mSpyContext = Mockito.spy(context);

        mAmLocal = Mockito.spy(ActivityManagerLocal.class);
        ExtendedMockito.doReturn(mAmLocal)
                .when(() -> LocalManagerRegistry.getManager(ActivityManagerLocal.class));

        // Required for Context#registerReceiverForAllUsers and reading DeviceConfig.
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                        Manifest.permission.READ_DEVICE_CONFIG);

        mServiceProvider = new SdkSandboxServiceProviderImpl(mSpyContext);
        mCallingInfo = new CallingInfo(Process.myUid(), TEST_PACKAGE);
        mServiceConnection = new FakeServiceConnection(mCallingInfo);
    }

    @After
    public void tearDown() {
        mStaticMockSession.finishMocking();
    }

    @Test
    public void testGetStatusWithoutBindingSandbox() {
        assertEquals(
                mServiceProvider.getSandboxStatusForApp(mCallingInfo),
                SdkSandboxServiceProvider.NON_EXISTENT);
    }

    @Test
    public void testBindSandbox() throws Exception {
        bindService(mCallingInfo, mServiceConnection);
        assertEquals(
                mServiceProvider.getSandboxStatusForApp(mCallingInfo),
                SdkSandboxServiceProvider.CREATE_PENDING);

        verifyBindServiceInvocation(1);
        bindService(mCallingInfo, mServiceConnection);
        // The number of times the sandbox is bound should not increase when sandbox creation
        // is pending.
        verifyBindServiceInvocation(1);

        mServiceConnection.onServiceConnected(null, null);
        assertEquals(
                mServiceProvider.getSandboxStatusForApp(mCallingInfo),
                SdkSandboxServiceProvider.CREATED);
        assertThat(mServiceProvider.getSdkSandboxServiceForApp(mCallingInfo)).isNotNull();

        bindService(mCallingInfo, mServiceConnection);
        // The number of times the sandbox is bound should not increase after sandbox creation.
        verifyBindServiceInvocation(1);
    }

    @Test
    public void testBindSandboxForMultipleApps() throws Exception {
        bindService(mCallingInfo, mServiceConnection);

        // Create another random calling info and verify that the sandbox does not exist for this
        // one.
        CallingInfo otherCallingInfo = new CallingInfo(-1, "");
        assertEquals(
                mServiceProvider.getSandboxStatusForApp(otherCallingInfo),
                SdkSandboxServiceProvider.NON_EXISTENT);
    }

    // Checks behaviour when binding fails for the sandbox.
    @Test
    public void testBindSandboxFailure() throws Exception {
        Mockito.lenient().doNothing().when(mSpyContext).unbindService(Mockito.any());
        bindService(mCallingInfo, mServiceConnection, false);
        verifyUnbindServiceInvocation(1);

        assertEquals(
                mServiceProvider.getSandboxStatusForApp(mCallingInfo),
                SdkSandboxServiceProvider.NON_EXISTENT);
        assertThat(mServiceConnection.wasNullBinding).isTrue();
    }

    @Test
    public void testUnbindSandbox_SandboxDoesNotExist() {
        unbindService(mCallingInfo);
        verifyUnbindServiceInvocation(0);
    }

    @Test
    public void testUnbindSandboxAfterBinding() throws Exception {
        bindService(mCallingInfo, mServiceConnection);
        mServiceConnection.onServiceConnected(null, null);
        unbindService(mCallingInfo);

        assertEquals(
                mServiceProvider.getSandboxStatusForApp(mCallingInfo),
                SdkSandboxServiceProvider.CREATED);
        verifyUnbindServiceInvocation(1);
    }

    @Test
    public void testSandboxDiedWhileBound() throws Exception {
        bindService(mCallingInfo, mServiceConnection);
        mServiceProvider.onSandboxDeath(mCallingInfo);

        if (SdkLevel.isAtLeastU()) {
            // If the sandbox died while bound, it will not restart in U+ and its status should be
            // non-existent.
            assertEquals(
                    mServiceProvider.getSandboxStatusForApp(mCallingInfo),
                    SdkSandboxServiceProvider.NON_EXISTENT);
        } else {
            // In T, the sandbox will restart if it died while bound, and its status should be
            // pending create.
            assertEquals(
                    mServiceProvider.getSandboxStatusForApp(mCallingInfo),
                    SdkSandboxServiceProvider.CREATE_PENDING);

            // Verify that binding cannot happen again.
            bindService(mCallingInfo, mServiceConnection);
            verifyBindServiceInvocation(1);
        }
    }

    @Test
    public void testSandboxDiedWhileUnbound() throws Exception {
        bindService(mCallingInfo, mServiceConnection);
        unbindService(mCallingInfo);
        mServiceProvider.onSandboxDeath(mCallingInfo);
        // If the sandbox died while bound, it should be marked as dead for good.
        assertEquals(
                mServiceProvider.getSandboxStatusForApp(mCallingInfo),
                SdkSandboxServiceProvider.NON_EXISTENT);

        // Verify that binding can happen again.
        bindService(mCallingInfo, mServiceConnection);
        verifyBindServiceInvocation(2);
    }

    @Test
    public void testBindSandboxAfterDeath_SandboxWasUnbound() throws Exception {
        bindService(mCallingInfo, mServiceConnection);
        unbindService(mCallingInfo);
        mServiceProvider.onSandboxDeath(mCallingInfo);

        bindService(mCallingInfo, mServiceConnection);
        verifyBindServiceInvocation(2);
    }

    @Test
    public void testCannotAccessSandboxServiceAfterDisconnect() throws Exception {
        bindService(mCallingInfo, mServiceConnection);
        mServiceProvider.onServiceDisconnected(mCallingInfo);
        assertThat(mServiceProvider.getSdkSandboxServiceForApp(mCallingInfo)).isNull();
    }

    @Test
    public void testSandboxProcessNameForInstrumentation()
            throws PackageManager.NameNotFoundException {
        assertThat(mServiceProvider.toSandboxProcessNameForInstrumentation(mCallingInfo))
                .isEqualTo(
                        TEST_PACKAGE + SdkSandboxServiceProvider.SANDBOX_INSTR_PROCESS_NAME_SUFFIX);
    }

    @Test
    public void testSandboxProcessName_nonExistentPackage() {
        String nonExistingPackageName = "does.not.exist";
        PackageManager.NameNotFoundException thrown =
                assertThrows(
                        PackageManager.NameNotFoundException.class,
                        () ->
                                mServiceProvider.toSandboxProcessName(
                                        new CallingInfo(
                                                mCallingInfo.getUid(), nonExistingPackageName)));
        assertThat(thrown).hasMessageThat().isEqualTo(nonExistingPackageName);
    }

    private void bindService(CallingInfo callingInfo, FakeServiceConnection serviceConnection)
            throws Exception {
        bindService(callingInfo, serviceConnection, true);
    }

    private void bindService(
            CallingInfo callingInfo,
            FakeServiceConnection serviceConnection,
            boolean shouldBindSucceed)
            throws Exception {
        // Do nothing while binding the sandbox to prevent extra processes being created.
        if (SdkLevel.isAtLeastU()) {
            Mockito.lenient()
                    .doReturn(new ComponentName("", ""))
                    .when(mAmLocal)
                    .startSdkSandboxService(
                            Mockito.any(),
                            Mockito.anyInt(),
                            Mockito.anyString(),
                            Mockito.anyString());
            Mockito.lenient()
                    .doReturn(shouldBindSucceed)
                    .when(mAmLocal)
                    .bindSdkSandboxService(
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.anyInt(),
                            Mockito.any(),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.eq(0));
        } else {
            Mockito.lenient()
                    .doReturn(shouldBindSucceed)
                    .when(mAmLocal)
                    .bindSdkSandboxService(
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.anyInt(),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyInt());
        }

        mServiceProvider.bindService(callingInfo, serviceConnection);
    }

    private void unbindService(CallingInfo callingInfo) {
        Mockito.lenient().doNothing().when(mSpyContext).unbindService(Mockito.any());
        mServiceProvider.unbindService(callingInfo);
    }

    // Verifies that ActivityManagerLocal.bindSdkSandboxService() was called.
    private void verifyBindServiceInvocation(int wantedNumberOfInvocations) throws Exception {
        if (SdkLevel.isAtLeastU()) {
            Mockito.verify(mAmLocal, Mockito.times(wantedNumberOfInvocations))
                    .bindSdkSandboxService(
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.anyInt(),
                            Mockito.any(),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyInt());
        } else {
            Mockito.verify(mAmLocal, Mockito.times(wantedNumberOfInvocations))
                    .bindSdkSandboxService(
                            Mockito.any(),
                            Mockito.any(),
                            Mockito.anyInt(),
                            Mockito.anyString(),
                            Mockito.anyString(),
                            Mockito.anyInt());
        }
    }

    // Verifies that Context.unbindService() was called.
    private void verifyUnbindServiceInvocation(int wantedNumberOfInvocations) {
        Mockito.verify(mSpyContext, Mockito.times(wantedNumberOfInvocations))
                .unbindService(Mockito.any());
    }

    private class FakeServiceConnection implements ServiceConnection {
        private CallingInfo mCallingInfo;
        public boolean wasNullBinding = false;

        FakeServiceConnection(CallingInfo callingInfo) {
            mCallingInfo = callingInfo;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mServiceProvider.onServiceConnected(mCallingInfo, new FakeSdkSandboxService());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServiceProvider.onServiceDisconnected(mCallingInfo);
        }

        @Override
        public void onBindingDied(ComponentName name) {}

        @Override
        public void onNullBinding(ComponentName name) {
            wasNullBinding = true;
        }
    }
}
