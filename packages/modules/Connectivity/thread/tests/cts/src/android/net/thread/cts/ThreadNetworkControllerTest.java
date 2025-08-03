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

package android.net.thread.cts;

import static android.Manifest.permission.ACCESS_NETWORK_STATE;
import static android.net.thread.ThreadNetworkController.DEVICE_ROLE_CHILD;
import static android.net.thread.ThreadNetworkController.DEVICE_ROLE_LEADER;
import static android.net.thread.ThreadNetworkController.DEVICE_ROLE_ROUTER;
import static android.net.thread.ThreadNetworkController.DEVICE_ROLE_STOPPED;
import static android.net.thread.ThreadNetworkController.STATE_DISABLED;
import static android.net.thread.ThreadNetworkController.STATE_DISABLING;
import static android.net.thread.ThreadNetworkController.STATE_ENABLED;
import static android.net.thread.ThreadNetworkController.THREAD_VERSION_1_3;
import static android.net.thread.ThreadNetworkException.ERROR_ABORTED;
import static android.net.thread.ThreadNetworkException.ERROR_FAILED_PRECONDITION;
import static android.net.thread.ThreadNetworkException.ERROR_REJECTED_BY_PEER;
import static android.net.thread.ThreadNetworkException.ERROR_THREAD_DISABLED;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.testutils.TestPermissionUtil.runAsShell;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNotNull;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.thread.ActiveOperationalDataset;
import android.net.thread.OperationalDatasetTimestamp;
import android.net.thread.PendingOperationalDataset;
import android.net.thread.ThreadNetworkController;
import android.net.thread.ThreadNetworkController.OperationalDatasetCallback;
import android.net.thread.ThreadNetworkController.StateCallback;
import android.net.thread.ThreadNetworkException;
import android.net.thread.ThreadNetworkManager;
import android.net.thread.utils.TapTestNetworkTracker;
import android.os.Build;
import android.os.HandlerThread;
import android.os.OutcomeReceiver;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.LargeTest;

import com.android.net.module.util.ArrayTrackRecord;
import com.android.testutils.DevSdkIgnoreRule;
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo;
import com.android.testutils.DevSdkIgnoreRunner;
import com.android.testutils.FunctionalUtils.ThrowingRunnable;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/** CTS tests for {@link ThreadNetworkController}. */
@LargeTest
@RunWith(DevSdkIgnoreRunner.class)
@IgnoreUpTo(Build.VERSION_CODES.TIRAMISU) // Thread is available on only U+
public class ThreadNetworkControllerTest {
    private static final int JOIN_TIMEOUT_MILLIS = 30 * 1000;
    private static final int LEAVE_TIMEOUT_MILLIS = 2_000;
    private static final int MIGRATION_TIMEOUT_MILLIS = 40 * 1_000;
    private static final int NETWORK_CALLBACK_TIMEOUT_MILLIS = 10 * 1000;
    private static final int CALLBACK_TIMEOUT_MILLIS = 1_000;
    private static final int ENABLED_TIMEOUT_MILLIS = 2_000;
    private static final int SERVICE_DISCOVERY_TIMEOUT_MILLIS = 30_000;
    private static final String MESHCOP_SERVICE_TYPE = "_meshcop._udp";
    private static final String THREAD_NETWORK_PRIVILEGED =
            "android.permission.THREAD_NETWORK_PRIVILEGED";

    @Rule public DevSdkIgnoreRule mIgnoreRule = new DevSdkIgnoreRule();

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private ExecutorService mExecutor;
    private ThreadNetworkController mController;
    private NsdManager mNsdManager;

    private Set<String> mGrantedPermissions;
    private HandlerThread mHandlerThread;
    private TapTestNetworkTracker mTestNetworkTracker;

    @Before
    public void setUp() throws Exception {
        ThreadNetworkManager manager = mContext.getSystemService(ThreadNetworkManager.class);
        if (manager != null) {
            mController = manager.getAllThreadNetworkControllers().get(0);
        }

        // TODO: we will also need it in tearDown(), it's better to have a Rule to skip
        // tests if a feature is not available.
        assumeNotNull(mController);

        mGrantedPermissions = new HashSet<String>();
        mExecutor = Executors.newSingleThreadExecutor();
        mNsdManager = mContext.getSystemService(NsdManager.class);
        mHandlerThread = new HandlerThread(this.getClass().getSimpleName());
        mHandlerThread.start();

        setEnabledAndWait(mController, true);
    }

    @After
    public void tearDown() throws Exception {
        if (mController == null) {
            return;
        }
        dropAllPermissions();
        leaveAndWait(mController);
        tearDownTestNetwork();
    }

    @Test
    public void getThreadVersion_returnsAtLeastThreadVersion1P3() {
        assertThat(mController.getThreadVersion()).isAtLeast(THREAD_VERSION_1_3);
    }

    @Test
    public void registerStateCallback_permissionsGranted_returnsCurrentStates() throws Exception {
        CompletableFuture<Integer> deviceRole = new CompletableFuture<>();
        StateCallback callback = deviceRole::complete;

        try {
            runAsShell(
                    ACCESS_NETWORK_STATE,
                    () -> mController.registerStateCallback(mExecutor, callback));

            assertThat(deviceRole.get(CALLBACK_TIMEOUT_MILLIS, MILLISECONDS))
                    .isEqualTo(DEVICE_ROLE_STOPPED);
        } finally {
            runAsShell(ACCESS_NETWORK_STATE, () -> mController.unregisterStateCallback(callback));
        }
    }

    @Test
    public void registerStateCallback_returnsUpdatedEnabledStates() throws Exception {
        CompletableFuture<Void> setFuture1 = new CompletableFuture<>();
        CompletableFuture<Void> setFuture2 = new CompletableFuture<>();
        EnabledStateListener listener = new EnabledStateListener(mController);

        try {
            runAsShell(
                    THREAD_NETWORK_PRIVILEGED,
                    () -> {
                        mController.setEnabled(false, mExecutor, newOutcomeReceiver(setFuture1));
                    });
            setFuture1.get(ENABLED_TIMEOUT_MILLIS, MILLISECONDS);

            runAsShell(
                    THREAD_NETWORK_PRIVILEGED,
                    () -> {
                        mController.setEnabled(true, mExecutor, newOutcomeReceiver(setFuture2));
                    });
            setFuture2.get(ENABLED_TIMEOUT_MILLIS, MILLISECONDS);

            listener.expectThreadEnabledState(STATE_ENABLED);
            listener.expectThreadEnabledState(STATE_DISABLING);
            listener.expectThreadEnabledState(STATE_DISABLED);
            listener.expectThreadEnabledState(STATE_ENABLED);
        } finally {
            listener.unregisterStateCallback();
        }
    }

    @Test
    public void registerStateCallback_noPermissions_throwsSecurityException() throws Exception {
        dropAllPermissions();

        assertThrows(
                SecurityException.class,
                () -> mController.registerStateCallback(mExecutor, role -> {}));
    }

    @Test
    public void registerStateCallback_alreadyRegistered_throwsIllegalArgumentException()
            throws Exception {
        grantPermissions(ACCESS_NETWORK_STATE);
        CompletableFuture<Integer> deviceRole = new CompletableFuture<>();
        StateCallback callback = role -> deviceRole.complete(role);

        mController.registerStateCallback(mExecutor, callback);

        assertThrows(
                IllegalArgumentException.class,
                () -> mController.registerStateCallback(mExecutor, callback));
    }

    @Test
    public void unregisterStateCallback_noPermissions_throwsSecurityException() throws Exception {
        CompletableFuture<Integer> deviceRole = new CompletableFuture<>();
        StateCallback callback = role -> deviceRole.complete(role);
        runAsShell(
                ACCESS_NETWORK_STATE, () -> mController.registerStateCallback(mExecutor, callback));

        try {
            dropAllPermissions();
            assertThrows(
                    SecurityException.class, () -> mController.unregisterStateCallback(callback));
        } finally {
            runAsShell(ACCESS_NETWORK_STATE, () -> mController.unregisterStateCallback(callback));
        }
    }

    @Test
    public void unregisterStateCallback_callbackRegistered_success() throws Exception {
        grantPermissions(ACCESS_NETWORK_STATE);
        CompletableFuture<Integer> deviceRole = new CompletableFuture<>();
        StateCallback callback = role -> deviceRole.complete(role);

        assertDoesNotThrow(() -> mController.registerStateCallback(mExecutor, callback));
        mController.unregisterStateCallback(callback);
    }

    @Test
    public void unregisterStateCallback_callbackNotRegistered_throwsIllegalArgumentException()
            throws Exception {
        CompletableFuture<Integer> deviceRole = new CompletableFuture<>();
        StateCallback callback = role -> deviceRole.complete(role);

        assertThrows(
                IllegalArgumentException.class,
                () -> mController.unregisterStateCallback(callback));
    }

    @Test
    public void unregisterStateCallback_alreadyUnregistered_throwsIllegalArgumentException()
            throws Exception {
        grantPermissions(ACCESS_NETWORK_STATE);
        CompletableFuture<Integer> deviceRole = new CompletableFuture<>();
        StateCallback callback = deviceRole::complete;
        mController.registerStateCallback(mExecutor, callback);
        mController.unregisterStateCallback(callback);

        assertThrows(
                IllegalArgumentException.class,
                () -> mController.unregisterStateCallback(callback));
    }

    @Test
    public void registerOperationalDatasetCallback_permissionsGranted_returnsCurrentStates()
            throws Exception {
        grantPermissions(ACCESS_NETWORK_STATE, THREAD_NETWORK_PRIVILEGED);
        CompletableFuture<ActiveOperationalDataset> activeFuture = new CompletableFuture<>();
        CompletableFuture<PendingOperationalDataset> pendingFuture = new CompletableFuture<>();
        var callback = newDatasetCallback(activeFuture, pendingFuture);

        try {
            mController.registerOperationalDatasetCallback(mExecutor, callback);

            assertThat(activeFuture.get(CALLBACK_TIMEOUT_MILLIS, MILLISECONDS)).isNull();
            assertThat(pendingFuture.get(CALLBACK_TIMEOUT_MILLIS, MILLISECONDS)).isNull();
        } finally {
            mController.unregisterOperationalDatasetCallback(callback);
        }
    }

    @Test
    public void registerOperationalDatasetCallback_noPermissions_throwsSecurityException()
            throws Exception {
        dropAllPermissions();
        CompletableFuture<ActiveOperationalDataset> activeFuture = new CompletableFuture<>();
        CompletableFuture<PendingOperationalDataset> pendingFuture = new CompletableFuture<>();
        var callback = newDatasetCallback(activeFuture, pendingFuture);

        assertThrows(
                SecurityException.class,
                () -> mController.registerOperationalDatasetCallback(mExecutor, callback));
    }

    @Test
    public void unregisterOperationalDatasetCallback_callbackRegistered_success() throws Exception {
        grantPermissions(ACCESS_NETWORK_STATE, THREAD_NETWORK_PRIVILEGED);
        CompletableFuture<ActiveOperationalDataset> activeFuture = new CompletableFuture<>();
        CompletableFuture<PendingOperationalDataset> pendingFuture = new CompletableFuture<>();
        var callback = newDatasetCallback(activeFuture, pendingFuture);
        mController.registerOperationalDatasetCallback(mExecutor, callback);

        assertDoesNotThrow(() -> mController.unregisterOperationalDatasetCallback(callback));
    }

    @Test
    public void unregisterOperationalDatasetCallback_noPermissions_throwsSecurityException()
            throws Exception {
        CompletableFuture<ActiveOperationalDataset> activeFuture = new CompletableFuture<>();
        CompletableFuture<PendingOperationalDataset> pendingFuture = new CompletableFuture<>();
        var callback = newDatasetCallback(activeFuture, pendingFuture);
        runAsShell(
                ACCESS_NETWORK_STATE,
                THREAD_NETWORK_PRIVILEGED,
                () -> mController.registerOperationalDatasetCallback(mExecutor, callback));

        try {
            dropAllPermissions();
            assertThrows(
                    SecurityException.class,
                    () -> mController.unregisterOperationalDatasetCallback(callback));
        } finally {
            runAsShell(
                    ACCESS_NETWORK_STATE,
                    THREAD_NETWORK_PRIVILEGED,
                    () -> mController.unregisterOperationalDatasetCallback(callback));
        }
    }

    private static <V> OutcomeReceiver<V, ThreadNetworkException> newOutcomeReceiver(
            CompletableFuture<V> future) {
        return new OutcomeReceiver<V, ThreadNetworkException>() {
            @Override
            public void onResult(V result) {
                future.complete(result);
            }

            @Override
            public void onError(ThreadNetworkException e) {
                future.completeExceptionally(e);
            }
        };
    }

    @Test
    public void join_withPrivilegedPermission_success() throws Exception {
        ActiveOperationalDataset activeDataset = newRandomizedDataset("TestNet", mController);
        CompletableFuture<Void> joinFuture = new CompletableFuture<>();

        runAsShell(
                THREAD_NETWORK_PRIVILEGED,
                () -> mController.join(activeDataset, mExecutor, newOutcomeReceiver(joinFuture)));
        joinFuture.get(JOIN_TIMEOUT_MILLIS, MILLISECONDS);

        assertThat(isAttached(mController)).isTrue();
        assertThat(getActiveOperationalDataset(mController)).isEqualTo(activeDataset);
    }

    @Test
    public void join_withoutPrivilegedPermission_throwsSecurityException() throws Exception {
        dropAllPermissions();
        ActiveOperationalDataset activeDataset = newRandomizedDataset("TestNet", mController);

        assertThrows(
                SecurityException.class, () -> mController.join(activeDataset, mExecutor, v -> {}));
    }

    @Test
    public void join_threadDisabled_failsWithErrorThreadDisabled() throws Exception {
        setEnabledAndWait(mController, false);
        ActiveOperationalDataset activeDataset = newRandomizedDataset("TestNet", mController);
        CompletableFuture<Void> joinFuture = new CompletableFuture<>();

        runAsShell(
                THREAD_NETWORK_PRIVILEGED,
                () -> mController.join(activeDataset, mExecutor, newOutcomeReceiver(joinFuture)));

        var thrown =
                assertThrows(
                        ExecutionException.class,
                        () -> joinFuture.get(JOIN_TIMEOUT_MILLIS, MILLISECONDS));
        var threadException = (ThreadNetworkException) thrown.getCause();
        assertThat(threadException.getErrorCode()).isEqualTo(ERROR_THREAD_DISABLED);
    }

    @Test
    public void join_concurrentRequests_firstOneIsAborted() throws Exception {
        final byte[] KEY_1 = new byte[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
        final byte[] KEY_2 = new byte[] {2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2};
        ActiveOperationalDataset activeDataset1 =
                new ActiveOperationalDataset.Builder(newRandomizedDataset("TestNet", mController))
                        .setNetworkKey(KEY_1)
                        .build();
        ActiveOperationalDataset activeDataset2 =
                new ActiveOperationalDataset.Builder(activeDataset1).setNetworkKey(KEY_2).build();
        CompletableFuture<Void> joinFuture1 = new CompletableFuture<>();
        CompletableFuture<Void> joinFuture2 = new CompletableFuture<>();

        runAsShell(
                THREAD_NETWORK_PRIVILEGED,
                () -> {
                    mController.join(activeDataset1, mExecutor, newOutcomeReceiver(joinFuture1));
                    mController.join(activeDataset2, mExecutor, newOutcomeReceiver(joinFuture2));
                });

        var thrown =
                assertThrows(
                        ExecutionException.class,
                        () -> joinFuture1.get(JOIN_TIMEOUT_MILLIS, MILLISECONDS));
        var threadException = (ThreadNetworkException) thrown.getCause();
        assertThat(threadException.getErrorCode()).isEqualTo(ERROR_ABORTED);
        joinFuture2.get(JOIN_TIMEOUT_MILLIS, MILLISECONDS);
        assertThat(isAttached(mController)).isTrue();
        assertThat(getActiveOperationalDataset(mController)).isEqualTo(activeDataset2);
    }

    @Test
    public void leave_withPrivilegedPermission_success() throws Exception {
        CompletableFuture<Void> leaveFuture = new CompletableFuture<>();
        joinRandomizedDatasetAndWait(mController);

        runAsShell(
                THREAD_NETWORK_PRIVILEGED,
                () -> mController.leave(mExecutor, newOutcomeReceiver(leaveFuture)));
        leaveFuture.get(LEAVE_TIMEOUT_MILLIS, MILLISECONDS);

        assertThat(getDeviceRole(mController)).isEqualTo(DEVICE_ROLE_STOPPED);
    }

    @Test
    public void leave_withoutPrivilegedPermission_throwsSecurityException() {
        dropAllPermissions();

        assertThrows(SecurityException.class, () -> mController.leave(mExecutor, v -> {}));
    }

    @Test
    public void leave_threadDisabled_success() throws Exception {
        setEnabledAndWait(mController, false);
        CompletableFuture<Void> leaveFuture = new CompletableFuture<>();

        leave(mController, newOutcomeReceiver(leaveFuture));
        leaveFuture.get(LEAVE_TIMEOUT_MILLIS, MILLISECONDS);

        assertThat(getDeviceRole(mController)).isEqualTo(DEVICE_ROLE_STOPPED);
    }

    @Test
    public void leave_concurrentRequests_bothSuccess() throws Exception {
        CompletableFuture<Void> leaveFuture1 = new CompletableFuture<>();
        CompletableFuture<Void> leaveFuture2 = new CompletableFuture<>();
        joinRandomizedDatasetAndWait(mController);

        runAsShell(
                THREAD_NETWORK_PRIVILEGED,
                () -> {
                    mController.leave(mExecutor, newOutcomeReceiver(leaveFuture1));
                    mController.leave(mExecutor, newOutcomeReceiver(leaveFuture2));
                });

        leaveFuture1.get(LEAVE_TIMEOUT_MILLIS, MILLISECONDS);
        leaveFuture2.get(LEAVE_TIMEOUT_MILLIS, MILLISECONDS);
        assertThat(getDeviceRole(mController)).isEqualTo(DEVICE_ROLE_STOPPED);
    }

    @Test
    public void scheduleMigration_withPrivilegedPermission_newDatasetApplied() throws Exception {
        grantPermissions(ACCESS_NETWORK_STATE, THREAD_NETWORK_PRIVILEGED);
        ActiveOperationalDataset activeDataset1 =
                new ActiveOperationalDataset.Builder(newRandomizedDataset("TestNet", mController))
                        .setActiveTimestamp(new OperationalDatasetTimestamp(1L, 0, false))
                        .setExtendedPanId(new byte[] {1, 1, 1, 1, 1, 1, 1, 1})
                        .build();
        ActiveOperationalDataset activeDataset2 =
                new ActiveOperationalDataset.Builder(activeDataset1)
                        .setActiveTimestamp(new OperationalDatasetTimestamp(2L, 0, false))
                        .setNetworkName("ThreadNet2")
                        .build();
        PendingOperationalDataset pendingDataset2 =
                new PendingOperationalDataset(
                        activeDataset2,
                        OperationalDatasetTimestamp.fromInstant(Instant.now()),
                        Duration.ofSeconds(30));
        CompletableFuture<Void> joinFuture = new CompletableFuture<>();
        CompletableFuture<Void> migrateFuture = new CompletableFuture<>();
        mController.join(activeDataset1, mExecutor, newOutcomeReceiver(joinFuture));
        joinFuture.get(JOIN_TIMEOUT_MILLIS, MILLISECONDS);

        mController.scheduleMigration(
                pendingDataset2, mExecutor, newOutcomeReceiver(migrateFuture));
        migrateFuture.get(CALLBACK_TIMEOUT_MILLIS, MILLISECONDS);

        CompletableFuture<Boolean> dataset2IsApplied = new CompletableFuture<>();
        CompletableFuture<Boolean> pendingDatasetIsRemoved = new CompletableFuture<>();
        OperationalDatasetCallback datasetCallback =
                new OperationalDatasetCallback() {
                    @Override
                    public void onActiveOperationalDatasetChanged(
                            ActiveOperationalDataset activeDataset) {
                        if (activeDataset.equals(activeDataset2)) {
                            dataset2IsApplied.complete(true);
                        }
                    }

                    @Override
                    public void onPendingOperationalDatasetChanged(
                            PendingOperationalDataset pendingDataset) {
                        if (pendingDataset == null) {
                            pendingDatasetIsRemoved.complete(true);
                        }
                    }
                };
        mController.registerOperationalDatasetCallback(directExecutor(), datasetCallback);
        try {
            assertThat(dataset2IsApplied.get(MIGRATION_TIMEOUT_MILLIS, MILLISECONDS)).isTrue();
            assertThat(pendingDatasetIsRemoved.get(CALLBACK_TIMEOUT_MILLIS, MILLISECONDS)).isTrue();
        } finally {
            mController.unregisterOperationalDatasetCallback(datasetCallback);
        }
    }

    @Test
    public void scheduleMigration_whenNotAttached_failWithPreconditionError() throws Exception {
        grantPermissions(ACCESS_NETWORK_STATE, THREAD_NETWORK_PRIVILEGED);
        PendingOperationalDataset pendingDataset =
                new PendingOperationalDataset(
                        newRandomizedDataset("TestNet", mController),
                        OperationalDatasetTimestamp.fromInstant(Instant.now()),
                        Duration.ofSeconds(30));
        CompletableFuture<Void> migrateFuture = new CompletableFuture<>();

        mController.scheduleMigration(pendingDataset, mExecutor, newOutcomeReceiver(migrateFuture));

        ThreadNetworkException thrown =
                (ThreadNetworkException)
                        assertThrows(ExecutionException.class, migrateFuture::get).getCause();
        assertThat(thrown.getErrorCode()).isEqualTo(ERROR_FAILED_PRECONDITION);
    }

    @Test
    public void scheduleMigration_secondRequestHasSmallerTimestamp_rejectedByLeader()
            throws Exception {
        grantPermissions(ACCESS_NETWORK_STATE, THREAD_NETWORK_PRIVILEGED);
        final ActiveOperationalDataset activeDataset =
                new ActiveOperationalDataset.Builder(newRandomizedDataset("testNet", mController))
                        .setActiveTimestamp(new OperationalDatasetTimestamp(1L, 0, false))
                        .build();
        ActiveOperationalDataset activeDataset1 =
                new ActiveOperationalDataset.Builder(activeDataset)
                        .setActiveTimestamp(new OperationalDatasetTimestamp(2L, 0, false))
                        .setNetworkName("testNet1")
                        .build();
        PendingOperationalDataset pendingDataset1 =
                new PendingOperationalDataset(
                        activeDataset1,
                        new OperationalDatasetTimestamp(100, 0, false),
                        Duration.ofSeconds(30));
        ActiveOperationalDataset activeDataset2 =
                new ActiveOperationalDataset.Builder(activeDataset)
                        .setActiveTimestamp(new OperationalDatasetTimestamp(3L, 0, false))
                        .setNetworkName("testNet2")
                        .build();
        PendingOperationalDataset pendingDataset2 =
                new PendingOperationalDataset(
                        activeDataset2,
                        new OperationalDatasetTimestamp(20, 0, false),
                        Duration.ofSeconds(30));
        CompletableFuture<Void> joinFuture = new CompletableFuture<>();
        CompletableFuture<Void> migrateFuture1 = new CompletableFuture<>();
        CompletableFuture<Void> migrateFuture2 = new CompletableFuture<>();
        mController.join(activeDataset, mExecutor, newOutcomeReceiver(joinFuture));
        joinFuture.get(JOIN_TIMEOUT_MILLIS, MILLISECONDS);

        mController.scheduleMigration(
                pendingDataset1, mExecutor, newOutcomeReceiver(migrateFuture1));
        migrateFuture1.get(CALLBACK_TIMEOUT_MILLIS, MILLISECONDS);
        mController.scheduleMigration(
                pendingDataset2, mExecutor, newOutcomeReceiver(migrateFuture2));

        ThreadNetworkException thrown =
                (ThreadNetworkException)
                        assertThrows(ExecutionException.class, migrateFuture2::get).getCause();
        assertThat(thrown.getErrorCode()).isEqualTo(ERROR_REJECTED_BY_PEER);
    }

    @Test
    public void scheduleMigration_secondRequestHasLargerTimestamp_newDatasetApplied()
            throws Exception {
        grantPermissions(ACCESS_NETWORK_STATE, THREAD_NETWORK_PRIVILEGED);
        final ActiveOperationalDataset activeDataset =
                new ActiveOperationalDataset.Builder(newRandomizedDataset("validName", mController))
                        .setActiveTimestamp(new OperationalDatasetTimestamp(1L, 0, false))
                        .build();
        ActiveOperationalDataset activeDataset1 =
                new ActiveOperationalDataset.Builder(activeDataset)
                        .setActiveTimestamp(new OperationalDatasetTimestamp(2L, 0, false))
                        .setNetworkName("testNet1")
                        .build();
        PendingOperationalDataset pendingDataset1 =
                new PendingOperationalDataset(
                        activeDataset1,
                        new OperationalDatasetTimestamp(100, 0, false),
                        Duration.ofSeconds(30));
        ActiveOperationalDataset activeDataset2 =
                new ActiveOperationalDataset.Builder(activeDataset)
                        .setActiveTimestamp(new OperationalDatasetTimestamp(3L, 0, false))
                        .setNetworkName("testNet2")
                        .build();
        PendingOperationalDataset pendingDataset2 =
                new PendingOperationalDataset(
                        activeDataset2,
                        new OperationalDatasetTimestamp(200, 0, false),
                        Duration.ofSeconds(30));
        CompletableFuture<Void> joinFuture = new CompletableFuture<>();
        CompletableFuture<Void> migrateFuture1 = new CompletableFuture<>();
        CompletableFuture<Void> migrateFuture2 = new CompletableFuture<>();
        mController.join(activeDataset, mExecutor, newOutcomeReceiver(joinFuture));
        joinFuture.get(JOIN_TIMEOUT_MILLIS, MILLISECONDS);

        mController.scheduleMigration(
                pendingDataset1, mExecutor, newOutcomeReceiver(migrateFuture1));
        migrateFuture1.get(CALLBACK_TIMEOUT_MILLIS, MILLISECONDS);
        mController.scheduleMigration(
                pendingDataset2, mExecutor, newOutcomeReceiver(migrateFuture2));
        migrateFuture2.get(CALLBACK_TIMEOUT_MILLIS, MILLISECONDS);

        CompletableFuture<Boolean> dataset2IsApplied = new CompletableFuture<>();
        CompletableFuture<Boolean> pendingDatasetIsRemoved = new CompletableFuture<>();
        OperationalDatasetCallback datasetCallback =
                new OperationalDatasetCallback() {
                    @Override
                    public void onActiveOperationalDatasetChanged(
                            ActiveOperationalDataset activeDataset) {
                        if (activeDataset.equals(activeDataset2)) {
                            dataset2IsApplied.complete(true);
                        }
                    }

                    @Override
                    public void onPendingOperationalDatasetChanged(
                            PendingOperationalDataset pendingDataset) {
                        if (pendingDataset == null) {
                            pendingDatasetIsRemoved.complete(true);
                        }
                    }
                };
        mController.registerOperationalDatasetCallback(directExecutor(), datasetCallback);
        try {
            assertThat(dataset2IsApplied.get(MIGRATION_TIMEOUT_MILLIS, MILLISECONDS)).isTrue();
            assertThat(pendingDatasetIsRemoved.get(CALLBACK_TIMEOUT_MILLIS, MILLISECONDS)).isTrue();
        } finally {
            mController.unregisterOperationalDatasetCallback(datasetCallback);
        }
    }

    @Test
    public void scheduleMigration_threadDisabled_failsWithErrorThreadDisabled() throws Exception {
        ActiveOperationalDataset activeDataset = newRandomizedDataset("TestNet", mController);
        PendingOperationalDataset pendingDataset =
                new PendingOperationalDataset(
                        activeDataset,
                        OperationalDatasetTimestamp.fromInstant(Instant.now()),
                        Duration.ofSeconds(30));
        joinRandomizedDatasetAndWait(mController);
        CompletableFuture<Void> migrationFuture = new CompletableFuture<>();

        setEnabledAndWait(mController, false);

        scheduleMigration(mController, pendingDataset, newOutcomeReceiver(migrationFuture));

        ThreadNetworkException thrown =
                (ThreadNetworkException)
                        assertThrows(ExecutionException.class, migrationFuture::get).getCause();
        assertThat(thrown.getErrorCode()).isEqualTo(ERROR_THREAD_DISABLED);
    }

    @Test
    public void createRandomizedDataset_wrongNetworkNameLength_throwsIllegalArgumentException() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mController.createRandomizedDataset("", mExecutor, dataset -> {}));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mController.createRandomizedDataset(
                                "ANetNameIs17Bytes", mExecutor, dataset -> {}));
    }

    @Test
    public void createRandomizedDataset_validNetworkName_success() throws Exception {
        ActiveOperationalDataset dataset = newRandomizedDataset("validName", mController);

        assertThat(dataset.getNetworkName()).isEqualTo("validName");
        assertThat(dataset.getPanId()).isLessThan(0xffff);
        assertThat(dataset.getChannelMask().size()).isAtLeast(1);
        assertThat(dataset.getExtendedPanId()).hasLength(8);
        assertThat(dataset.getNetworkKey()).hasLength(16);
        assertThat(dataset.getPskc()).hasLength(16);
        assertThat(dataset.getMeshLocalPrefix().getPrefixLength()).isEqualTo(64);
        assertThat(dataset.getMeshLocalPrefix().getRawAddress()[0]).isEqualTo((byte) 0xfd);
    }

    @Test
    public void setEnabled_permissionsGranted_succeeds() throws Exception {
        CompletableFuture<Void> setFuture1 = new CompletableFuture<>();
        CompletableFuture<Void> setFuture2 = new CompletableFuture<>();

        runAsShell(
                THREAD_NETWORK_PRIVILEGED,
                () -> mController.setEnabled(false, mExecutor, newOutcomeReceiver(setFuture1)));
        setFuture1.get(ENABLED_TIMEOUT_MILLIS, MILLISECONDS);
        waitForEnabledState(mController, booleanToEnabledState(false));

        runAsShell(
                THREAD_NETWORK_PRIVILEGED,
                () -> mController.setEnabled(true, mExecutor, newOutcomeReceiver(setFuture2)));
        setFuture2.get(ENABLED_TIMEOUT_MILLIS, MILLISECONDS);
        waitForEnabledState(mController, booleanToEnabledState(true));
    }

    @Test
    public void setEnabled_noPermissions_throwsSecurityException() throws Exception {
        CompletableFuture<Void> setFuture = new CompletableFuture<>();
        assertThrows(
                SecurityException.class,
                () -> mController.setEnabled(false, mExecutor, newOutcomeReceiver(setFuture)));
    }

    @Test
    public void setEnabled_disable_leavesThreadNetwork() throws Exception {
        joinRandomizedDatasetAndWait(mController);
        setEnabledAndWait(mController, false);
        assertThat(getDeviceRole(mController)).isEqualTo(DEVICE_ROLE_STOPPED);
    }

    @Test
    public void setEnabled_toggleAfterJoin_joinsThreadNetworkAgain() throws Exception {
        joinRandomizedDatasetAndWait(mController);

        setEnabledAndWait(mController, false);
        assertThat(getDeviceRole(mController)).isEqualTo(DEVICE_ROLE_STOPPED);
        setEnabledAndWait(mController, true);

        runAsShell(ACCESS_NETWORK_STATE, () -> waitForAttachedState(mController));
    }

    @Test
    public void setEnabled_enableFollowedByDisable_allSucceed() throws Exception {
        joinRandomizedDatasetAndWait(mController);
        CompletableFuture<Void> setFuture1 = new CompletableFuture<>();
        CompletableFuture<Void> setFuture2 = new CompletableFuture<>();
        EnabledStateListener listener = new EnabledStateListener(mController);
        listener.expectThreadEnabledState(STATE_ENABLED);

        runAsShell(
                THREAD_NETWORK_PRIVILEGED,
                () -> {
                    mController.setEnabled(true, mExecutor, newOutcomeReceiver(setFuture1));
                    mController.setEnabled(false, mExecutor, newOutcomeReceiver(setFuture2));
                });
        setFuture1.get(ENABLED_TIMEOUT_MILLIS, MILLISECONDS);
        setFuture2.get(ENABLED_TIMEOUT_MILLIS, MILLISECONDS);

        listener.expectThreadEnabledState(STATE_DISABLING);
        listener.expectThreadEnabledState(STATE_DISABLED);
        assertThat(getDeviceRole(mController)).isEqualTo(DEVICE_ROLE_STOPPED);
        // FIXME: this is not called when a exception is thrown after the creation of `listener`
        listener.unregisterStateCallback();
    }

    // TODO (b/322437869): add test case to verify when Thread is in DISABLING state, any commands
    // (join/leave/scheduleMigration/setEnabled) fail with ERROR_BUSY. This is not currently tested
    // because DISABLING has very short lifecycle, it's not possible to guarantee the command can be
    // sent before state changes to DISABLED.

    @Test
    public void threadNetworkCallback_deviceAttached_threadNetworkIsAvailable() throws Exception {
        CompletableFuture<Network> networkFuture = new CompletableFuture<>();
        ConnectivityManager cm = mContext.getSystemService(ConnectivityManager.class);
        NetworkRequest networkRequest =
                new NetworkRequest.Builder()
                        .addTransportType(NetworkCapabilities.TRANSPORT_THREAD)
                        .build();
        ConnectivityManager.NetworkCallback networkCallback =
                new ConnectivityManager.NetworkCallback() {
                    @Override
                    public void onAvailable(Network network) {
                        networkFuture.complete(network);
                    }
                };

        joinRandomizedDatasetAndWait(mController);
        runAsShell(
                ACCESS_NETWORK_STATE,
                () -> cm.registerNetworkCallback(networkRequest, networkCallback));

        assertThat(isAttached(mController)).isTrue();
        assertThat(networkFuture.get(NETWORK_CALLBACK_TIMEOUT_MILLIS, MILLISECONDS)).isNotNull();
    }

    private void grantPermissions(String... permissions) {
        for (String permission : permissions) {
            mGrantedPermissions.add(permission);
        }
        String[] allPermissions = new String[mGrantedPermissions.size()];
        mGrantedPermissions.toArray(allPermissions);
        getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(allPermissions);
    }

    @Test
    public void meshcopService_threadEnabledButNotJoined_discoveredButNoNetwork() throws Exception {
        setUpTestNetwork();

        setEnabledAndWait(mController, true);
        leaveAndWait(mController);

        NsdServiceInfo serviceInfo =
                expectServiceResolved(
                        MESHCOP_SERVICE_TYPE,
                        SERVICE_DISCOVERY_TIMEOUT_MILLIS,
                        s -> s.getAttributes().get("at") == null);

        Map<String, byte[]> txtMap = serviceInfo.getAttributes();

        assertThat(txtMap.get("rv")).isNotNull();
        assertThat(txtMap.get("tv")).isNotNull();
        assertThat(txtMap.get("sb")).isNotNull();
    }

    @Test
    public void meshcopService_joinedNetwork_discoveredHasNetwork() throws Exception {
        setUpTestNetwork();

        String networkName = "TestNet" + new Random().nextInt(10_000);
        joinRandomizedDatasetAndWait(mController, networkName);

        Predicate<NsdServiceInfo> predicate =
                serviceInfo ->
                        serviceInfo.getAttributes().get("at") != null
                                && Arrays.equals(
                                        serviceInfo.getAttributes().get("nn"),
                                        networkName.getBytes(StandardCharsets.UTF_8));

        NsdServiceInfo resolvedService =
                expectServiceResolved(
                        MESHCOP_SERVICE_TYPE, SERVICE_DISCOVERY_TIMEOUT_MILLIS, predicate);

        Map<String, byte[]> txtMap = resolvedService.getAttributes();
        assertThat(txtMap.get("rv")).isNotNull();
        assertThat(txtMap.get("tv")).isNotNull();
        assertThat(txtMap.get("sb")).isNotNull();
        assertThat(txtMap.get("id").length).isEqualTo(16);
    }

    @Test
    public void meshcopService_threadDisabled_notDiscovered() throws Exception {
        setUpTestNetwork();

        CompletableFuture<NsdServiceInfo> serviceLostFuture = new CompletableFuture<>();
        NsdManager.DiscoveryListener listener =
                discoverForServiceLost(MESHCOP_SERVICE_TYPE, serviceLostFuture);
        setEnabledAndWait(mController, false);
        try {
            serviceLostFuture.get(SERVICE_DISCOVERY_TIMEOUT_MILLIS, MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException ignored) {
            // It's fine if the service lost event didn't show up. The service may not ever be
            // advertised.
        } finally {
            mNsdManager.stopServiceDiscovery(listener);
        }

        assertThrows(TimeoutException.class, () -> discoverService(MESHCOP_SERVICE_TYPE));
    }

    private static void dropAllPermissions() {
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    private static ActiveOperationalDataset newRandomizedDataset(
            String networkName, ThreadNetworkController controller) throws Exception {
        CompletableFuture<ActiveOperationalDataset> future = new CompletableFuture<>();
        controller.createRandomizedDataset(networkName, directExecutor(), future::complete);
        return future.get(CALLBACK_TIMEOUT_MILLIS, MILLISECONDS);
    }

    private static boolean isAttached(ThreadNetworkController controller) throws Exception {
        return ThreadNetworkController.isAttached(getDeviceRole(controller));
    }

    private static int getDeviceRole(ThreadNetworkController controller) throws Exception {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        StateCallback callback = future::complete;
        runAsShell(
                ACCESS_NETWORK_STATE,
                () -> controller.registerStateCallback(directExecutor(), callback));
        try {
            return future.get(CALLBACK_TIMEOUT_MILLIS, MILLISECONDS);
        } finally {
            runAsShell(ACCESS_NETWORK_STATE, () -> controller.unregisterStateCallback(callback));
        }
    }

    private static int waitForAttachedState(ThreadNetworkController controller) throws Exception {
        List<Integer> attachedRoles = new ArrayList<>();
        attachedRoles.add(DEVICE_ROLE_CHILD);
        attachedRoles.add(DEVICE_ROLE_ROUTER);
        attachedRoles.add(DEVICE_ROLE_LEADER);
        return waitForStateAnyOf(controller, attachedRoles);
    }

    private static int waitForStateAnyOf(
            ThreadNetworkController controller, List<Integer> deviceRoles) throws Exception {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        StateCallback callback =
                newRole -> {
                    if (deviceRoles.contains(newRole)) {
                        future.complete(newRole);
                    }
                };
        controller.registerStateCallback(directExecutor(), callback);
        int role = future.get(JOIN_TIMEOUT_MILLIS, MILLISECONDS);
        controller.unregisterStateCallback(callback);
        return role;
    }

    private static void waitForEnabledState(ThreadNetworkController controller, int state)
            throws Exception {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        StateCallback callback =
                new ThreadNetworkController.StateCallback() {
                    @Override
                    public void onDeviceRoleChanged(int r) {}

                    @Override
                    public void onThreadEnableStateChanged(int enabled) {
                        if (enabled == state) {
                            future.complete(enabled);
                        }
                    }
                };
        runAsShell(
                ACCESS_NETWORK_STATE,
                () -> controller.registerStateCallback(directExecutor(), callback));
        future.get(ENABLED_TIMEOUT_MILLIS, MILLISECONDS);
        runAsShell(ACCESS_NETWORK_STATE, () -> controller.unregisterStateCallback(callback));
    }

    private void leave(
            ThreadNetworkController controller,
            OutcomeReceiver<Void, ThreadNetworkException> receiver) {
        runAsShell(THREAD_NETWORK_PRIVILEGED, () -> controller.leave(mExecutor, receiver));
    }

    private void leaveAndWait(ThreadNetworkController controller) throws Exception {
        CompletableFuture<Void> future = new CompletableFuture<>();
        leave(controller, future::complete);
        future.get(LEAVE_TIMEOUT_MILLIS, MILLISECONDS);
    }

    private void scheduleMigration(
            ThreadNetworkController controller,
            PendingOperationalDataset pendingDataset,
            OutcomeReceiver<Void, ThreadNetworkException> receiver) {
        runAsShell(
                THREAD_NETWORK_PRIVILEGED,
                () -> controller.scheduleMigration(pendingDataset, mExecutor, receiver));
    }

    private class EnabledStateListener {
        private ArrayTrackRecord<Integer> mEnabledStates = new ArrayTrackRecord<>();
        private final ArrayTrackRecord<Integer>.ReadHead mReadHead = mEnabledStates.newReadHead();
        ThreadNetworkController mController;
        StateCallback mCallback =
                new ThreadNetworkController.StateCallback() {
                    @Override
                    public void onDeviceRoleChanged(int r) {}

                    @Override
                    public void onThreadEnableStateChanged(int enabled) {
                        mEnabledStates.add(enabled);
                    }
                };

        EnabledStateListener(ThreadNetworkController controller) {
            this.mController = controller;
            runAsShell(
                    ACCESS_NETWORK_STATE,
                    () -> controller.registerStateCallback(mExecutor, mCallback));
        }

        public void expectThreadEnabledState(int enabled) {
            assertNotNull(mReadHead.poll(ENABLED_TIMEOUT_MILLIS, e -> (e == enabled)));
        }

        public void unregisterStateCallback() {
            runAsShell(ACCESS_NETWORK_STATE, () -> mController.unregisterStateCallback(mCallback));
        }
    }

    private int booleanToEnabledState(boolean enabled) {
        return enabled ? STATE_ENABLED : STATE_DISABLED;
    }

    private void setEnabledAndWait(ThreadNetworkController controller, boolean enabled)
            throws Exception {
        CompletableFuture<Void> setFuture = new CompletableFuture<>();
        runAsShell(
                THREAD_NETWORK_PRIVILEGED,
                () -> controller.setEnabled(enabled, mExecutor, newOutcomeReceiver(setFuture)));
        setFuture.get(ENABLED_TIMEOUT_MILLIS, MILLISECONDS);
        waitForEnabledState(controller, booleanToEnabledState(enabled));
    }

    private CompletableFuture joinRandomizedDataset(
            ThreadNetworkController controller, String networkName) throws Exception {
        ActiveOperationalDataset activeDataset = newRandomizedDataset(networkName, controller);
        CompletableFuture<Void> joinFuture = new CompletableFuture<>();
        runAsShell(
                THREAD_NETWORK_PRIVILEGED,
                () -> controller.join(activeDataset, mExecutor, newOutcomeReceiver(joinFuture)));
        return joinFuture;
    }

    private void joinRandomizedDatasetAndWait(ThreadNetworkController controller) throws Exception {
        joinRandomizedDatasetAndWait(controller, "TestNet");
    }

    private void joinRandomizedDatasetAndWait(
            ThreadNetworkController controller, String networkName) throws Exception {
        CompletableFuture<Void> joinFuture = joinRandomizedDataset(controller, networkName);
        joinFuture.get(JOIN_TIMEOUT_MILLIS, MILLISECONDS);
        assertThat(isAttached(controller)).isTrue();
    }

    private static ActiveOperationalDataset getActiveOperationalDataset(
            ThreadNetworkController controller) throws Exception {
        CompletableFuture<ActiveOperationalDataset> future = new CompletableFuture<>();
        OperationalDatasetCallback callback = future::complete;
        runAsShell(
                ACCESS_NETWORK_STATE,
                THREAD_NETWORK_PRIVILEGED,
                () -> controller.registerOperationalDatasetCallback(directExecutor(), callback));
        try {
            return future.get(CALLBACK_TIMEOUT_MILLIS, MILLISECONDS);
        } finally {
            runAsShell(
                    ACCESS_NETWORK_STATE,
                    THREAD_NETWORK_PRIVILEGED,
                    () -> controller.unregisterOperationalDatasetCallback(callback));
        }
    }

    private static PendingOperationalDataset getPendingOperationalDataset(
            ThreadNetworkController controller) throws Exception {
        CompletableFuture<ActiveOperationalDataset> activeFuture = new CompletableFuture<>();
        CompletableFuture<PendingOperationalDataset> pendingFuture = new CompletableFuture<>();
        controller.registerOperationalDatasetCallback(
                directExecutor(), newDatasetCallback(activeFuture, pendingFuture));
        return pendingFuture.get(CALLBACK_TIMEOUT_MILLIS, MILLISECONDS);
    }

    private static OperationalDatasetCallback newDatasetCallback(
            CompletableFuture<ActiveOperationalDataset> activeFuture,
            CompletableFuture<PendingOperationalDataset> pendingFuture) {
        return new OperationalDatasetCallback() {
            @Override
            public void onActiveOperationalDatasetChanged(
                    ActiveOperationalDataset activeOpDataset) {
                activeFuture.complete(activeOpDataset);
            }

            @Override
            public void onPendingOperationalDatasetChanged(
                    PendingOperationalDataset pendingOpDataset) {
                pendingFuture.complete(pendingOpDataset);
            }
        };
    }

    private static void assertDoesNotThrow(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable e) {
            fail("Should not have thrown " + e);
        }
    }

    // Return the first discovered service instance.
    private NsdServiceInfo discoverService(String serviceType) throws Exception {
        CompletableFuture<NsdServiceInfo> serviceInfoFuture = new CompletableFuture<>();
        NsdManager.DiscoveryListener listener =
                new DefaultDiscoveryListener() {
                    @Override
                    public void onServiceFound(NsdServiceInfo serviceInfo) {
                        serviceInfoFuture.complete(serviceInfo);
                    }
                };
        mNsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener);
        try {
            serviceInfoFuture.get(SERVICE_DISCOVERY_TIMEOUT_MILLIS, MILLISECONDS);
        } finally {
            mNsdManager.stopServiceDiscovery(listener);
        }

        return serviceInfoFuture.get();
    }

    private NsdManager.DiscoveryListener discoverForServiceLost(
            String serviceType, CompletableFuture<NsdServiceInfo> serviceInfoFuture) {
        NsdManager.DiscoveryListener listener =
                new DefaultDiscoveryListener() {
                    @Override
                    public void onServiceLost(NsdServiceInfo serviceInfo) {
                        serviceInfoFuture.complete(serviceInfo);
                    }
                };
        mNsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener);
        return listener;
    }

    private NsdServiceInfo expectServiceResolved(
            String serviceType, int timeoutMilliseconds, Predicate<NsdServiceInfo> predicate)
            throws Exception {
        NsdServiceInfo discoveredServiceInfo = discoverService(serviceType);
        CompletableFuture<NsdServiceInfo> future = new CompletableFuture<>();
        NsdManager.ServiceInfoCallback callback =
                new DefaultServiceInfoCallback() {
                    @Override
                    public void onServiceUpdated(@NonNull NsdServiceInfo serviceInfo) {
                        if (predicate.test(serviceInfo)) {
                            future.complete(serviceInfo);
                        }
                    }
                };
        mNsdManager.registerServiceInfoCallback(discoveredServiceInfo, mExecutor, callback);
        try {
            return future.get(timeoutMilliseconds, MILLISECONDS);
        } finally {
            mNsdManager.unregisterServiceInfoCallback(callback);
        }
    }

    private void setUpTestNetwork() {
        assertThat(mTestNetworkTracker).isNull();
        mTestNetworkTracker = new TapTestNetworkTracker(mContext, mHandlerThread.getLooper());
    }

    private void tearDownTestNetwork() throws InterruptedException {
        if (mTestNetworkTracker != null) {
            mTestNetworkTracker.tearDown();
        }
        mHandlerThread.quitSafely();
        mHandlerThread.join();
    }

    private static class DefaultDiscoveryListener implements NsdManager.DiscoveryListener {
        @Override
        public void onStartDiscoveryFailed(String serviceType, int errorCode) {}

        @Override
        public void onStopDiscoveryFailed(String serviceType, int errorCode) {}

        @Override
        public void onDiscoveryStarted(String serviceType) {}

        @Override
        public void onDiscoveryStopped(String serviceType) {}

        @Override
        public void onServiceFound(NsdServiceInfo serviceInfo) {}

        @Override
        public void onServiceLost(NsdServiceInfo serviceInfo) {}
    }

    private static class DefaultServiceInfoCallback implements NsdManager.ServiceInfoCallback {
        @Override
        public void onServiceInfoCallbackRegistrationFailed(int errorCode) {}

        @Override
        public void onServiceUpdated(@NonNull NsdServiceInfo serviceInfo) {}

        @Override
        public void onServiceLost() {}

        @Override
        public void onServiceInfoCallbackUnregistered() {}
    }
}
