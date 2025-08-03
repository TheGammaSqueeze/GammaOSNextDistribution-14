/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static android.Manifest.permission.NETWORK_SETTINGS;
import static android.net.thread.ThreadNetworkController.DEVICE_ROLE_DETACHED;
import static android.net.thread.ThreadNetworkController.DEVICE_ROLE_LEADER;
import static android.net.thread.ThreadNetworkController.DEVICE_ROLE_STOPPED;
import static android.net.thread.ThreadNetworkManager.PERMISSION_THREAD_NETWORK_PRIVILEGED;
import static android.net.thread.utils.IntegrationTestUtils.CALLBACK_TIMEOUT;
import static android.net.thread.utils.IntegrationTestUtils.LEAVE_TIMEOUT;
import static android.net.thread.utils.IntegrationTestUtils.RESTART_JOIN_TIMEOUT;
import static android.net.thread.utils.IntegrationTestUtils.waitForStateAnyOf;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.testutils.TestPermissionUtil.runAsShell;

import static com.google.common.io.BaseEncoding.base16;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.MoreExecutors.directExecutor;

import static org.junit.Assume.assumeNotNull;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.annotation.Nullable;
import android.content.Context;
import android.net.thread.ThreadNetworkController.StateCallback;
import android.net.thread.utils.OtDaemonController;
import android.os.SystemClock;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.Inet6Address;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Tests for E2E Android Thread integration with ot-daemon, ConnectivityService, etc.. */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class ThreadIntegrationTest {
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private ThreadNetworkController mController;
    private OtDaemonController mOtCtl;

    // A valid Thread Active Operational Dataset generated from OpenThread CLI "dataset init new".
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
    public void setUp() throws Exception {
        final ThreadNetworkManager manager = mContext.getSystemService(ThreadNetworkManager.class);
        if (manager != null) {
            mController = manager.getAllThreadNetworkControllers().get(0);
        }

        // Run the tests on only devices where the Thread feature is available
        assumeNotNull(mController);

        mOtCtl = new OtDaemonController();
        leaveAndWait(mController);
    }

    @After
    public void tearDown() throws Exception {
        if (mController == null) {
            return;
        }

        setTestUpStreamNetworkAndWait(mController, null);
        leaveAndWait(mController);
    }

    @Test
    public void otDaemonRestart_notJoinedAndStopped_deviceRoleIsStopped() throws Exception {
        leaveAndWait(mController);

        runShellCommand("stop ot-daemon");
        // TODO(b/323331973): the sleep is needed to workaround the race conditions
        SystemClock.sleep(200);

        waitForStateAnyOf(mController, List.of(DEVICE_ROLE_STOPPED), CALLBACK_TIMEOUT);
    }

    @Test
    public void otDaemonRestart_JoinedNetworkAndStopped_autoRejoined() throws Exception {
        joinAndWait(mController, DEFAULT_DATASET);

        runShellCommand("stop ot-daemon");

        waitForStateAnyOf(mController, List.of(DEVICE_ROLE_DETACHED), CALLBACK_TIMEOUT);
        waitForStateAnyOf(mController, List.of(DEVICE_ROLE_LEADER), RESTART_JOIN_TIMEOUT);
    }

    @Test
    public void otDaemonFactoryReset_deviceRoleIsStopped() throws Exception {
        joinAndWait(mController, DEFAULT_DATASET);

        mOtCtl.factoryReset();

        assertThat(getDeviceRole(mController)).isEqualTo(DEVICE_ROLE_STOPPED);
    }

    @Test
    public void otDaemonFactoryReset_addressesRemoved() throws Exception {
        joinAndWait(mController, DEFAULT_DATASET);

        mOtCtl.factoryReset();
        String ifconfig = runShellCommand("ifconfig thread-wpan");

        assertThat(ifconfig).doesNotContain("inet6 addr");
    }

    @Test
    public void tunInterface_joinedNetwork_otAddressesAddedToTunInterface() throws Exception {
        joinAndWait(mController, DEFAULT_DATASET);

        String ifconfig = runShellCommand("ifconfig thread-wpan");
        List<Inet6Address> otAddresses = mOtCtl.getAddresses();
        assertThat(otAddresses).isNotEmpty();
        for (Inet6Address otAddress : otAddresses) {
            assertThat(ifconfig).contains(otAddress.getHostAddress());
        }
    }

    // TODO (b/323300829): add more tests for integration with linux platform and
    // ConnectivityService

    private static int getDeviceRole(ThreadNetworkController controller) throws Exception {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        StateCallback callback = future::complete;
        controller.registerStateCallback(directExecutor(), callback);
        try {
            return future.get(CALLBACK_TIMEOUT.toMillis(), MILLISECONDS);
        } finally {
            controller.unregisterStateCallback(callback);
        }
    }

    private static void joinAndWait(
            ThreadNetworkController controller, ActiveOperationalDataset activeDataset)
            throws Exception {
        runAsShell(
                PERMISSION_THREAD_NETWORK_PRIVILEGED,
                () -> controller.join(activeDataset, directExecutor(), result -> {}));
        waitForStateAnyOf(controller, List.of(DEVICE_ROLE_LEADER), RESTART_JOIN_TIMEOUT);
    }

    private static void leaveAndWait(ThreadNetworkController controller) throws Exception {
        CompletableFuture<Void> future = new CompletableFuture<>();
        runAsShell(
                PERMISSION_THREAD_NETWORK_PRIVILEGED,
                () -> controller.leave(directExecutor(), future::complete));
        future.get(LEAVE_TIMEOUT.toMillis(), MILLISECONDS);
    }

    private static void setTestUpStreamNetworkAndWait(
            ThreadNetworkController controller, @Nullable String networkInterfaceName)
            throws Exception {
        CompletableFuture<Void> future = new CompletableFuture<>();
        runAsShell(
                PERMISSION_THREAD_NETWORK_PRIVILEGED,
                NETWORK_SETTINGS,
                () -> {
                    controller.setTestNetworkAsUpstream(
                            networkInterfaceName, directExecutor(), future::complete);
                });
        future.get(CALLBACK_TIMEOUT.toMillis(), MILLISECONDS);
    }
}
