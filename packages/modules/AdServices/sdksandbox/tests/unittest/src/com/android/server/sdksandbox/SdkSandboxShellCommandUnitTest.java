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

package com.android.server.sdksandbox;

import static com.android.server.sdksandbox.SdkSandboxShellCommand.ADSERVICES_CMD;

import static com.google.common.truth.Truth.assertThat;

import android.Manifest;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxLatencyInfo;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.UserHandle;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.build.SdkLevel;
import com.android.sdksandbox.ISdkSandboxService;
import com.android.server.wm.ActivityInterceptorCallbackRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.FileDescriptor;

public class SdkSandboxShellCommandUnitTest {

    private static final String DEBUGGABLE_PACKAGE = "android.app.debuggable";
    private static final String NON_DEBUGGABLE_PACKAGE = "android.app.nondebuggable";
    private static final int UID = 10214;
    private static final String INVALID_PACKAGE = "android.app.invalid";
    private Context mSpyContext;
    private FakeSdkSandboxManagerService mService;

    private final FileDescriptor mIn = FileDescriptor.in;
    private final FileDescriptor mOut = FileDescriptor.out;
    private final FileDescriptor mErr = FileDescriptor.err;

    private PackageManager mPackageManager;

    private MockitoSession mStaticMockSession;
    private Binder mAdServicesBinder;

    @Before
    public void setup() throws Exception {
        if (SdkLevel.isAtLeastU()) {
            mStaticMockSession =
                    ExtendedMockito.mockitoSession()
                            // TODO(b/267320397): Remove LENIENT to enable mock Exceptions.
                            .strictness(Strictness.LENIENT)
                            .mockStatic(ActivityInterceptorCallbackRegistry.class)
                            .startMocking();
            ActivityInterceptorCallbackRegistry registryMock =
                    Mockito.mock(ActivityInterceptorCallbackRegistry.class);
            ExtendedMockito.doReturn(registryMock)
                    .when(ActivityInterceptorCallbackRegistry::getInstance);
        }
        mAdServicesBinder = Mockito.mock(Binder.class);
        mSpyContext = Mockito.spy(InstrumentationRegistry.getInstrumentation().getContext());

        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.READ_DEVICE_CONFIG,
                        // Required for Context#registerReceiverForAllUsers
                        Manifest.permission.INTERACT_ACROSS_USERS_FULL);
        mService = Mockito.spy(new FakeSdkSandboxManagerService(mSpyContext, mAdServicesBinder));

        mPackageManager = Mockito.mock(PackageManager.class);

        Mockito.when(mSpyContext.getPackageManager()).thenReturn(mPackageManager);

        final ApplicationInfo debuggableInfo = Mockito.mock(ApplicationInfo.class);
        debuggableInfo.flags |= ApplicationInfo.FLAG_DEBUGGABLE;
        debuggableInfo.uid = UID;

        Mockito.doReturn(debuggableInfo)
                .when(mPackageManager)
                .getApplicationInfoAsUser(
                        Mockito.eq(DEBUGGABLE_PACKAGE),
                        Mockito.anyInt(),
                        Mockito.any(UserHandle.class));

        final ApplicationInfo nonDebuggableInfo = Mockito.mock(ApplicationInfo.class);
        nonDebuggableInfo.uid = UID;

        Mockito.doReturn(nonDebuggableInfo)
                .when(mPackageManager)
                .getApplicationInfoAsUser(
                        Mockito.eq(NON_DEBUGGABLE_PACKAGE),
                        Mockito.anyInt(),
                        Mockito.any(UserHandle.class));

        Mockito.doThrow(new PackageManager.NameNotFoundException())
                .when(mPackageManager)
                .getApplicationInfoAsUser(
                        Mockito.eq(INVALID_PACKAGE),
                        Mockito.anyInt(),
                        Mockito.any(UserHandle.class));
    }

    @After
    public void tearDown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testCommandFailsIfCallerNotShellOrRoot() {
        final SdkSandboxShellCommand.Injector injector =
                new SdkSandboxShellCommand.Injector() {
                    @Override
                    int getCallingUid() {
                        return UserHandle.USER_ALL;
                    }
                };
        final SdkSandboxShellCommand cmd =
                new SdkSandboxShellCommand(mService, mSpyContext, injector);

        assertThat(cmd.exec(mService, mIn, mOut, mErr, new String[] {"start", DEBUGGABLE_PACKAGE}))
                .isEqualTo(-1);
    }

    @Test
    public void testStartFailsForInvalidPackage() throws Exception {
        final SdkSandboxShellCommand cmd =
                new SdkSandboxShellCommand(mService, mSpyContext, new ShellInjector());

        assertThat(cmd.exec(mService, mIn, mOut, mErr, new String[] {"start", INVALID_PACKAGE}))
                .isEqualTo(-1);

        Mockito.verify(mPackageManager)
                .getApplicationInfoAsUser(
                        Mockito.eq(INVALID_PACKAGE),
                        Mockito.anyInt(),
                        Mockito.any(UserHandle.class));
    }

    @Test
    public void testStartFailsWhenSdkSandboxDisabled() {
        final CallingInfo callingInfo = new CallingInfo(UID, DEBUGGABLE_PACKAGE);
        Mockito.doReturn(false).when(mService).isSdkSandboxServiceRunning(callingInfo);
        final SdkSandboxShellCommand cmd =
                new SdkSandboxShellCommand(mService, mSpyContext, new ShellInjector());
        mService.setIsSdkSandboxDisabledResponse(true);

        assertThat(cmd.exec(mService, mIn, mOut, mErr, new String[] {"start", DEBUGGABLE_PACKAGE}))
                .isEqualTo(-1);

        Mockito.verify(mService)
                .stopSdkSandboxService(
                        Mockito.any(CallingInfo.class),
                        Mockito.eq(
                                "Shell command `sdk_sandbox start` failed due to sandbox"
                                        + " disabled."));
    }

    @Test
    public void testStartFailsForNonDebuggablePackage() throws Exception {
        final SdkSandboxShellCommand cmd =
                new SdkSandboxShellCommand(mService, mSpyContext, new ShellInjector());

        assertThat(
                        cmd.exec(
                                mService,
                                mIn,
                                mOut,
                                mErr,
                                new String[] {"start", NON_DEBUGGABLE_PACKAGE}))
                .isEqualTo(-1);

        Mockito.verify(mPackageManager)
                .getApplicationInfoAsUser(
                        Mockito.eq(NON_DEBUGGABLE_PACKAGE),
                        Mockito.anyInt(),
                        Mockito.any(UserHandle.class));

        Mockito.verify(mService, Mockito.never())
                .startSdkSandboxIfNeeded(
                        Mockito.any(CallingInfo.class),
                        Mockito.any(SdkSandboxManagerService.SandboxBindingCallback.class),
                        Mockito.any(SandboxLatencyInfo.class));
    }

    @Test
    public void testStartFailsWhenSandboxAlreadyRunning() throws Exception {
        final CallingInfo callingInfo = new CallingInfo(UID, DEBUGGABLE_PACKAGE);
        Mockito.doReturn(true).when(mService).isSdkSandboxServiceRunning(callingInfo);

        final SdkSandboxShellCommand cmd =
                new SdkSandboxShellCommand(mService, mSpyContext, new ShellInjector());

        assertThat(cmd.exec(mService, mIn, mOut, mErr, new String[] {"start", DEBUGGABLE_PACKAGE}))
                .isEqualTo(-1);

        Mockito.verify(mPackageManager)
                .getApplicationInfoAsUser(
                        Mockito.eq(DEBUGGABLE_PACKAGE),
                        Mockito.anyInt(),
                        Mockito.any(UserHandle.class));

        Mockito.verify(mService).isSdkSandboxServiceRunning(callingInfo);

        Mockito.verify(mService, Mockito.never())
                .startSdkSandboxIfNeeded(
                        Mockito.any(CallingInfo.class),
                        Mockito.any(SdkSandboxManagerService.SandboxBindingCallback.class),
                        Mockito.any(SandboxLatencyInfo.class));
    }

    @Test
    public void testStartSucceedsForDebuggablePackageWhenNotAlreadyRunning() throws Exception {
        final CallingInfo callingInfo = new CallingInfo(UID, DEBUGGABLE_PACKAGE);
        Mockito.doReturn(false).when(mService).isSdkSandboxServiceRunning(callingInfo);

        final SdkSandboxShellCommand cmd =
                new SdkSandboxShellCommand(mService, mSpyContext, new ShellInjector());

        assertThat(cmd.exec(mService, mIn, mOut, mErr, new String[] {"start", DEBUGGABLE_PACKAGE}))
                .isEqualTo(0);

        Mockito.verify(mPackageManager)
                .getApplicationInfoAsUser(
                        Mockito.eq(DEBUGGABLE_PACKAGE),
                        Mockito.anyInt(),
                        Mockito.any(UserHandle.class));

        Mockito.verify(mService).isSdkSandboxServiceRunning(callingInfo);

        Mockito.verify(mService)
                .startSdkSandboxIfNeeded(
                        Mockito.eq(callingInfo),
                        Mockito.any(SdkSandboxManagerService.SandboxBindingCallback.class),
                        Mockito.any(SandboxLatencyInfo.class));
    }

    @Test
    public void testStartFailsWhenBindingSandboxFails() throws Exception {
        mService.setBindingSuccessful(false);

        final CallingInfo callingInfo = new CallingInfo(UID, DEBUGGABLE_PACKAGE);
        Mockito.doReturn(false).when(mService).isSdkSandboxServiceRunning(callingInfo);

        final SdkSandboxShellCommand cmd =
                new SdkSandboxShellCommand(mService, mSpyContext, new ShellInjector());

        assertThat(cmd.exec(mService, mIn, mOut, mErr, new String[] {"start", DEBUGGABLE_PACKAGE}))
                .isEqualTo(-1);

        Mockito.verify(mPackageManager)
                .getApplicationInfoAsUser(
                        Mockito.eq(DEBUGGABLE_PACKAGE),
                        Mockito.anyInt(),
                        Mockito.any(UserHandle.class));

        Mockito.verify(mService).isSdkSandboxServiceRunning(callingInfo);

        Mockito.verify(mService)
                .startSdkSandboxIfNeeded(
                        Mockito.eq(callingInfo),
                        Mockito.any(SdkSandboxManagerService.SandboxBindingCallback.class),
                        Mockito.any(SandboxLatencyInfo.class));
    }

    @Test
    public void testStopFailsForInvalidPackage() throws Exception {
        final SdkSandboxShellCommand cmd =
                new SdkSandboxShellCommand(mService, mSpyContext, new ShellInjector());

        assertThat(cmd.exec(mService, mIn, mOut, mErr, new String[] {"stop", INVALID_PACKAGE}))
                .isEqualTo(-1);

        Mockito.verify(mPackageManager)
                .getApplicationInfoAsUser(
                        Mockito.eq(INVALID_PACKAGE),
                        Mockito.anyInt(),
                        Mockito.any(UserHandle.class));

        Mockito.verify(mService, Mockito.never())
                .stopSdkSandboxService(Mockito.any(CallingInfo.class), Mockito.anyString());
    }

    @Test
    public void testStopFailsForNonDebuggablePackage() throws Exception {
        final SdkSandboxShellCommand cmd =
                new SdkSandboxShellCommand(mService, mSpyContext, new ShellInjector());

        assertThat(
                        cmd.exec(
                                mService,
                                mIn,
                                mOut,
                                mErr,
                                new String[] {"stop", NON_DEBUGGABLE_PACKAGE}))
                .isEqualTo(-1);

        Mockito.verify(mPackageManager)
                .getApplicationInfoAsUser(
                        Mockito.eq(NON_DEBUGGABLE_PACKAGE),
                        Mockito.anyInt(),
                        Mockito.any(UserHandle.class));

        Mockito.verify(mService, Mockito.never())
                .stopSdkSandboxService(Mockito.any(CallingInfo.class), Mockito.anyString());
    }

    @Test
    public void testStopFailsWhenSandboxIsNotRunning() throws Exception {
        final CallingInfo callingInfo = new CallingInfo(UID, DEBUGGABLE_PACKAGE);
        Mockito.doReturn(false).when(mService).isSdkSandboxServiceRunning(callingInfo);

        final SdkSandboxShellCommand cmd =
                new SdkSandboxShellCommand(mService, mSpyContext, new ShellInjector());

        assertThat(cmd.exec(mService, mIn, mOut, mErr, new String[] {"stop", DEBUGGABLE_PACKAGE}))
                .isEqualTo(-1);

        Mockito.verify(mPackageManager)
                .getApplicationInfoAsUser(
                        Mockito.eq(DEBUGGABLE_PACKAGE),
                        Mockito.anyInt(),
                        Mockito.any(UserHandle.class));

        Mockito.verify(mService).isSdkSandboxServiceRunning(callingInfo);

        Mockito.verify(mService, Mockito.never())
                .stopSdkSandboxService(Mockito.any(CallingInfo.class), Mockito.anyString());
    }

    @Test
    public void testStopSucceedsForDebuggablePackageWhenAlreadyRunning() throws Exception {
        final CallingInfo callingInfo = new CallingInfo(UID, DEBUGGABLE_PACKAGE);
        Mockito.doReturn(true).when(mService).isSdkSandboxServiceRunning(callingInfo);

        final SdkSandboxShellCommand cmd =
                new SdkSandboxShellCommand(mService, mSpyContext, new ShellInjector());

        assertThat(cmd.exec(mService, mIn, mOut, mErr, new String[] {"stop", DEBUGGABLE_PACKAGE}))
                .isEqualTo(0);

        Mockito.verify(mPackageManager)
                .getApplicationInfoAsUser(
                        Mockito.eq(DEBUGGABLE_PACKAGE),
                        Mockito.anyInt(),
                        Mockito.any(UserHandle.class));

        Mockito.verify(mService).isSdkSandboxServiceRunning(callingInfo);

        Mockito.verify(mService)
                .stopSdkSandboxService(callingInfo, "Shell command 'sdk_sandbox stop' issued");
    }

    @Test
    public void testRunAdServicesShellCommand_supportsAdServicesShellCmd() throws Exception {
        String[] args = new String[] {ADSERVICES_CMD, "echo", "hello"};
        String[] realArgs = new String[] {"echo", "hello"};
        Mockito.when(
                        mAdServicesBinder.handleShellCommand(
                                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.eq(realArgs)))
                .thenReturn(1);
        SdkSandboxShellCommand cmd =
                new SdkSandboxShellCommand(
                        mService,
                        mSpyContext,
                        /* supportsAdServicesShellCmd= */ true,
                        new ShellInjector());

        assertThat(cmd.exec(mService, mIn, mOut, mErr, args)).isEqualTo(1);
    }

    @Test
    public void testRunAdServicesShellCommand_doesNotSupportAdServicesShellCmd() throws Exception {
        String[] args = new String[] {ADSERVICES_CMD, "echo", "hello"};
        SdkSandboxShellCommand cmd =
                new SdkSandboxShellCommand(mService, mSpyContext, new ShellInjector());

        assertThat(cmd.exec(mService, mIn, mOut, mErr, args)).isEqualTo(-1);
        Mockito.verify(mAdServicesBinder, Mockito.never())
                .handleShellCommand(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    private static class ShellInjector extends SdkSandboxShellCommand.Injector {

        @Override
        int getCallingUid() {
            return Process.SHELL_UID;
        }
    }

    private static class FakeSdkSandboxManagerService extends SdkSandboxManagerService {

        private boolean mBindingSuccessful = true;
        private boolean mIsDisabledResponse = false;
        private final IBinder mAdServicesBinder;

        FakeSdkSandboxManagerService(Context context, IBinder adServicesBinder) {
            super(context);
            mAdServicesBinder = adServicesBinder;
        }

        @Override
        void startSdkSandboxIfNeeded(
                CallingInfo callingInfo,
                SandboxBindingCallback callback,
                SandboxLatencyInfo sandboxLatencyInfo) {
            if (mBindingSuccessful) {
                callback.onBindingSuccessful(
                        Mockito.mock(ISdkSandboxService.class), sandboxLatencyInfo);
            } else {
                callback.onBindingFailed(
                        new LoadSdkException(null, new Bundle()), sandboxLatencyInfo);
            }
        }

        @Override
        void stopSdkSandboxService(CallingInfo callingInfo, String reason) {}

        @Override
        boolean isSdkSandboxServiceRunning(CallingInfo callingInfo) {
            // Must be mocked in the tests accordingly
            throw new RuntimeException();
        }

        @Override
        boolean isSdkSandboxDisabled() {
            return mIsDisabledResponse;
        }

        @Override
        public IBinder getAdServicesManager() {
            return mAdServicesBinder;
        }

        private void setIsSdkSandboxDisabledResponse(boolean response) {
            mIsDisabledResponse = response;
        }

        private void setBindingSuccessful(boolean successful) {
            mBindingSuccessful = successful;
        }
    }
}
