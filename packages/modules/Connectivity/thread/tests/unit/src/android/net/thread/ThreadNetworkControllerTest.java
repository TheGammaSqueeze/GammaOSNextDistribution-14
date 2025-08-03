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

package android.net.thread;

import static android.net.thread.ThreadNetworkController.DEVICE_ROLE_CHILD;
import static android.net.thread.ThreadNetworkException.ERROR_UNAVAILABLE;
import static android.net.thread.ThreadNetworkException.ERROR_UNSUPPORTED_CHANNEL;
import static android.os.Process.SYSTEM_UID;

import static com.google.common.io.BaseEncoding.base16;
import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

import android.net.thread.ThreadNetworkController.OperationalDatasetCallback;
import android.net.thread.ThreadNetworkController.StateCallback;
import android.os.Binder;
import android.os.OutcomeReceiver;
import android.os.Process;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/** Unit tests for {@link ThreadNetworkController}. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public final class ThreadNetworkControllerTest {

    @Mock private IThreadNetworkController mMockService;
    private ThreadNetworkController mController;

    // A valid Thread Active Operational Dataset generated from OpenThread CLI "dataset new":
    // Active Timestamp: 1
    // Channel: 19
    // Channel Mask: 0x07FFF800
    // Ext PAN ID: ACC214689BC40BDF
    // Mesh Local Prefix: fd64:db12:25f4:7e0b::/64
    // Network Key: F26B3153760F519A63BAFDDFFC80D2AF
    // Network Name: OpenThread-d9a0
    // PAN ID: 0xD9A0
    // PSKc: A245479C836D551B9CA557F7B9D351B4
    // Security Policy: 672 onrcb
    private static final byte[] DEFAULT_DATASET_TLVS =
            base16().decode(
                            "0E080000000000010000000300001335060004001FFFE002"
                                    + "08ACC214689BC40BDF0708FD64DB1225F47E0B0510F26B31"
                                    + "53760F519A63BAFDDFFC80D2AF030F4F70656E5468726561"
                                    + "642D643961300102D9A00410A245479C836D551B9CA557F7"
                                    + "B9D351B40C0402A0FFF8");

    private static final ActiveOperationalDataset DEFAULT_DATASET =
            ActiveOperationalDataset.fromThreadTlvs(DEFAULT_DATASET_TLVS);

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mController = new ThreadNetworkController(mMockService);
    }

    private static void setBinderUid(int uid) {
        // TODO: generally, it's not a good practice to depend on the implementation detail to set
        // a custom UID, but Connectivity, Wifi, UWB and etc modules are using this trick. Maybe
        // define a interface (e.b. CallerIdentityInjector) for easier mocking.
        Binder.restoreCallingIdentity((((long) uid) << 32) | Binder.getCallingPid());
    }

    private static IStateCallback getStateCallback(InvocationOnMock invocation) {
        return (IStateCallback) invocation.getArguments()[0];
    }

    private static IOperationReceiver getOperationReceiver(InvocationOnMock invocation) {
        return (IOperationReceiver) invocation.getArguments()[0];
    }

    private static IOperationReceiver getJoinReceiver(InvocationOnMock invocation) {
        return (IOperationReceiver) invocation.getArguments()[1];
    }

    private static IOperationReceiver getScheduleMigrationReceiver(InvocationOnMock invocation) {
        return (IOperationReceiver) invocation.getArguments()[1];
    }

    private static IOperationReceiver getSetTestNetworkAsUpstreamReceiver(
            InvocationOnMock invocation) {
        return (IOperationReceiver) invocation.getArguments()[1];
    }

    private static IActiveOperationalDatasetReceiver getCreateDatasetReceiver(
            InvocationOnMock invocation) {
        return (IActiveOperationalDatasetReceiver) invocation.getArguments()[1];
    }

    private static IOperationalDatasetCallback getOperationalDatasetCallback(
            InvocationOnMock invocation) {
        return (IOperationalDatasetCallback) invocation.getArguments()[0];
    }

    @Test
    public void registerStateCallback_callbackIsInvokedWithCallingAppIdentity() throws Exception {
        setBinderUid(SYSTEM_UID);
        doAnswer(
                        invoke -> {
                            getStateCallback(invoke).onDeviceRoleChanged(DEVICE_ROLE_CHILD);
                            return null;
                        })
                .when(mMockService)
                .registerStateCallback(any(IStateCallback.class));
        AtomicInteger callbackUid = new AtomicInteger(0);
        StateCallback callback = state -> callbackUid.set(Binder.getCallingUid());

        try {
            mController.registerStateCallback(Runnable::run, callback);

            assertThat(callbackUid.get()).isNotEqualTo(SYSTEM_UID);
            assertThat(callbackUid.get()).isEqualTo(Process.myUid());
        } finally {
            mController.unregisterStateCallback(callback);
        }
    }

    @Test
    public void registerOperationalDatasetCallback_callbackIsInvokedWithCallingAppIdentity()
            throws Exception {
        setBinderUid(SYSTEM_UID);
        doAnswer(
                        invoke -> {
                            getOperationalDatasetCallback(invoke)
                                    .onActiveOperationalDatasetChanged(null);
                            getOperationalDatasetCallback(invoke)
                                    .onPendingOperationalDatasetChanged(null);
                            return null;
                        })
                .when(mMockService)
                .registerOperationalDatasetCallback(any(IOperationalDatasetCallback.class));
        AtomicInteger activeCallbackUid = new AtomicInteger(0);
        AtomicInteger pendingCallbackUid = new AtomicInteger(0);
        OperationalDatasetCallback callback =
                new OperationalDatasetCallback() {
                    @Override
                    public void onActiveOperationalDatasetChanged(
                            ActiveOperationalDataset dataset) {
                        activeCallbackUid.set(Binder.getCallingUid());
                    }

                    @Override
                    public void onPendingOperationalDatasetChanged(
                            PendingOperationalDataset dataset) {
                        pendingCallbackUid.set(Binder.getCallingUid());
                    }
                };

        try {
            mController.registerOperationalDatasetCallback(Runnable::run, callback);

            assertThat(activeCallbackUid.get()).isNotEqualTo(SYSTEM_UID);
            assertThat(activeCallbackUid.get()).isEqualTo(Process.myUid());
            assertThat(pendingCallbackUid.get()).isNotEqualTo(SYSTEM_UID);
            assertThat(pendingCallbackUid.get()).isEqualTo(Process.myUid());
        } finally {
            mController.unregisterOperationalDatasetCallback(callback);
        }
    }

    @Test
    public void createRandomizedDataset_callbackIsInvokedWithCallingAppIdentity() throws Exception {
        setBinderUid(SYSTEM_UID);
        AtomicInteger successCallbackUid = new AtomicInteger(0);
        AtomicInteger errorCallbackUid = new AtomicInteger(0);

        doAnswer(
                        invoke -> {
                            getCreateDatasetReceiver(invoke).onSuccess(DEFAULT_DATASET);
                            return null;
                        })
                .when(mMockService)
                .createRandomizedDataset(anyString(), any(IActiveOperationalDatasetReceiver.class));
        mController.createRandomizedDataset(
                "TestNet",
                Runnable::run,
                dataset -> successCallbackUid.set(Binder.getCallingUid()));
        doAnswer(
                        invoke -> {
                            getCreateDatasetReceiver(invoke).onError(ERROR_UNSUPPORTED_CHANNEL, "");
                            return null;
                        })
                .when(mMockService)
                .createRandomizedDataset(anyString(), any(IActiveOperationalDatasetReceiver.class));
        mController.createRandomizedDataset(
                "TestNet",
                Runnable::run,
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(ActiveOperationalDataset dataset) {}

                    @Override
                    public void onError(ThreadNetworkException e) {
                        errorCallbackUid.set(Binder.getCallingUid());
                    }
                });

        assertThat(successCallbackUid.get()).isNotEqualTo(SYSTEM_UID);
        assertThat(successCallbackUid.get()).isEqualTo(Process.myUid());
        assertThat(errorCallbackUid.get()).isNotEqualTo(SYSTEM_UID);
        assertThat(errorCallbackUid.get()).isEqualTo(Process.myUid());
    }

    @Test
    public void join_callbackIsInvokedWithCallingAppIdentity() throws Exception {
        setBinderUid(SYSTEM_UID);
        AtomicInteger successCallbackUid = new AtomicInteger(0);
        AtomicInteger errorCallbackUid = new AtomicInteger(0);

        doAnswer(
                        invoke -> {
                            getJoinReceiver(invoke).onSuccess();
                            return null;
                        })
                .when(mMockService)
                .join(any(ActiveOperationalDataset.class), any(IOperationReceiver.class));
        mController.join(
                DEFAULT_DATASET,
                Runnable::run,
                v -> successCallbackUid.set(Binder.getCallingUid()));
        doAnswer(
                        invoke -> {
                            getJoinReceiver(invoke).onError(ERROR_UNAVAILABLE, "");
                            return null;
                        })
                .when(mMockService)
                .join(any(ActiveOperationalDataset.class), any(IOperationReceiver.class));
        mController.join(
                DEFAULT_DATASET,
                Runnable::run,
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Void unused) {}

                    @Override
                    public void onError(ThreadNetworkException e) {
                        errorCallbackUid.set(Binder.getCallingUid());
                    }
                });

        assertThat(successCallbackUid.get()).isNotEqualTo(SYSTEM_UID);
        assertThat(successCallbackUid.get()).isEqualTo(Process.myUid());
        assertThat(errorCallbackUid.get()).isNotEqualTo(SYSTEM_UID);
        assertThat(errorCallbackUid.get()).isEqualTo(Process.myUid());
    }

    @Test
    public void scheduleMigration_callbackIsInvokedWithCallingAppIdentity() throws Exception {
        setBinderUid(SYSTEM_UID);
        final PendingOperationalDataset pendingDataset =
                new PendingOperationalDataset(
                        DEFAULT_DATASET,
                        new OperationalDatasetTimestamp(100, 0, false),
                        Duration.ZERO);
        AtomicInteger successCallbackUid = new AtomicInteger(0);
        AtomicInteger errorCallbackUid = new AtomicInteger(0);

        doAnswer(
                        invoke -> {
                            getScheduleMigrationReceiver(invoke).onSuccess();
                            return null;
                        })
                .when(mMockService)
                .scheduleMigration(
                        any(PendingOperationalDataset.class), any(IOperationReceiver.class));
        mController.scheduleMigration(
                pendingDataset, Runnable::run, v -> successCallbackUid.set(Binder.getCallingUid()));
        doAnswer(
                        invoke -> {
                            getScheduleMigrationReceiver(invoke).onError(ERROR_UNAVAILABLE, "");
                            return null;
                        })
                .when(mMockService)
                .scheduleMigration(
                        any(PendingOperationalDataset.class), any(IOperationReceiver.class));
        mController.scheduleMigration(
                pendingDataset,
                Runnable::run,
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Void unused) {}

                    @Override
                    public void onError(ThreadNetworkException e) {
                        errorCallbackUid.set(Binder.getCallingUid());
                    }
                });

        assertThat(successCallbackUid.get()).isNotEqualTo(SYSTEM_UID);
        assertThat(successCallbackUid.get()).isEqualTo(Process.myUid());
        assertThat(errorCallbackUid.get()).isNotEqualTo(SYSTEM_UID);
        assertThat(errorCallbackUid.get()).isEqualTo(Process.myUid());
    }

    @Test
    public void leave_callbackIsInvokedWithCallingAppIdentity() throws Exception {
        setBinderUid(SYSTEM_UID);
        AtomicInteger successCallbackUid = new AtomicInteger(0);
        AtomicInteger errorCallbackUid = new AtomicInteger(0);

        doAnswer(
                        invoke -> {
                            getOperationReceiver(invoke).onSuccess();
                            return null;
                        })
                .when(mMockService)
                .leave(any(IOperationReceiver.class));
        mController.leave(Runnable::run, v -> successCallbackUid.set(Binder.getCallingUid()));
        doAnswer(
                        invoke -> {
                            getOperationReceiver(invoke).onError(ERROR_UNAVAILABLE, "");
                            return null;
                        })
                .when(mMockService)
                .leave(any(IOperationReceiver.class));
        mController.leave(
                Runnable::run,
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Void unused) {}

                    @Override
                    public void onError(ThreadNetworkException e) {
                        errorCallbackUid.set(Binder.getCallingUid());
                    }
                });

        assertThat(successCallbackUid.get()).isNotEqualTo(SYSTEM_UID);
        assertThat(successCallbackUid.get()).isEqualTo(Process.myUid());
        assertThat(errorCallbackUid.get()).isNotEqualTo(SYSTEM_UID);
        assertThat(errorCallbackUid.get()).isEqualTo(Process.myUid());
    }

    @Test
    public void setTestNetworkAsUpstream_callbackIsInvokedWithCallingAppIdentity()
            throws Exception {
        setBinderUid(SYSTEM_UID);

        AtomicInteger callbackUid = new AtomicInteger(0);

        doAnswer(
                        invoke -> {
                            getSetTestNetworkAsUpstreamReceiver(invoke).onSuccess();
                            return null;
                        })
                .when(mMockService)
                .setTestNetworkAsUpstream(anyString(), any(IOperationReceiver.class));
        mController.setTestNetworkAsUpstream(
                null, Runnable::run, v -> callbackUid.set(Binder.getCallingUid()));
        mController.setTestNetworkAsUpstream(
                new String("test0"), Runnable::run, v -> callbackUid.set(Binder.getCallingUid()));

        assertThat(callbackUid.get()).isNotEqualTo(SYSTEM_UID);
        assertThat(callbackUid.get()).isEqualTo(Process.myUid());
    }
}
