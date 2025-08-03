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

package android.wearable.cts;

import static android.wearable.cts.CtsIsolatedWearableSensingService.ACTION_CLOSE_WEARABLE_CONNECTION;
import static android.wearable.cts.CtsIsolatedWearableSensingService.ACTION_RESET;
import static android.wearable.cts.CtsIsolatedWearableSensingService.ACTION_SEND_DATA_TO_WEARABLE;
import static android.wearable.cts.CtsIsolatedWearableSensingService.ACTION_SET_BOOLEAN_STATE;
import static android.wearable.cts.CtsIsolatedWearableSensingService.ACTION_VERIFY_BOOLEAN_STATE;
import static android.wearable.cts.CtsIsolatedWearableSensingService.ACTION_VERIFY_DATA_RECEIVED_FROM_WEARABLE;
import static android.wearable.cts.CtsIsolatedWearableSensingService.BUNDLE_ACTION_KEY;
import static android.wearable.cts.CtsIsolatedWearableSensingService.EXPECTED_STRING_FROM_WEARABLE_KEY;
import static android.wearable.cts.CtsIsolatedWearableSensingService.STRING_TO_SEND_KEY;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.Manifest;
import android.app.wearable.Flags;
import android.app.wearable.WearableSensingManager;
import android.companion.AssociationInfo;
import android.companion.AssociationRequest;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseInputStream;
import android.os.ParcelFileDescriptor.AutoCloseOutputStream;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.DeviceConfigStateChangerRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test the {@link WearableSensingManager} API using {@link CtsIsolatedWearableSensingService} as
 * the implementation for {@link WearableSensingService}.
 *
 * <p>Run with "atest CtsWearableSensingServiceTestCases".
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "PM will not recognize CtsIsolatedWearableSensingService in instantMode.")
public class WearableSensingManagerIsolatedServiceTest {
    private static final String TAG = "WSMIsolatedTest";
    private static final String CTS_PACKAGE_NAME =
            CtsIsolatedWearableSensingService.class.getPackage().getName();
    private static final String CTS_ISOLATED_SERVICE_NAME =
            CTS_PACKAGE_NAME + "/." + CtsIsolatedWearableSensingService.class.getSimpleName();
    private static final int USER_ID = UserHandle.myUserId();
    private static final int TEMPORARY_SERVICE_DURATION = 5000;
    private static final String NAMESPACE_WEARABLE_SENSING = "wearable_sensing";
    private static final String KEY_SERVICE_ENABLED = "service_enabled";
    // The CDM message types come from CompanionDeviceManager.MESSAGE_ONEWAY_*
    private static final int CDM_MESSAGE_ONEWAY_FROM_WEARABLE = 0x43708287;
    private static final int CDM_MESSAGE_ONEWAY_TO_WEARABLE = 0x43847987;
    private static final String CDM_ASSOCIATION_DISPLAY_NAME = "CDM_ASSOCIATION_DISPLAY_NAME";
    private static final String DATA_TO_WRITE = "DATA_TO_WRITE";

    private static final Executor EXECUTOR = InstrumentationRegistry.getContext().getMainExecutor();

    private Context mContext;
    private WearableSensingManager mWearableSensingManager;
    private CompanionDeviceManager mCompanionDeviceManager;
    private ParcelFileDescriptor[] mSocketPair;
    private Integer mCdmAssociationId;

    @Rule
    public final DeviceConfigStateChangerRule mWearableSensingConfigRule =
            new DeviceConfigStateChangerRule(
                    getInstrumentation().getTargetContext(),
                    NAMESPACE_WEARABLE_SENSING,
                    KEY_SERVICE_ENABLED,
                    "true");

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        mContext = getInstrumentation().getContext();
        assumeFalse(isWatch(mContext));  // WearableSensingManagerService is not supported on WearOS
        // For an unknown reason, the CDM onTransportsChanged listener is not called on TV builds
        assumeFalse(isTelevision(mContext));
        // Sleep for 2 seconds to avoid flakiness until b/326256152 is fixed. The bug can cause
        // CDM to reuse the same associationId previously assigned to WearableSensingSecureChannel
        // after the previous association is disassociation. If async clean up of the CDM
        // secure channel for that previous association has not completed, it can affect the new
        // association with the reused associationId and causes tests to flake.
        SystemClock.sleep(2000);
        mWearableSensingManager =
                (WearableSensingManager)
                        mContext.getSystemService(Context.WEARABLE_SENSING_SERVICE);
        mCompanionDeviceManager = mContext.getSystemService(CompanionDeviceManager.class);
        mSocketPair = ParcelFileDescriptor.createSocketPair();
        clearTestableWearableSensingService();
        bindToIsolatedWearableSensingService();
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE);
        resetIsolatedWearableSensingServiceStates();
    }

    @After
    public void tearDown() throws Exception {
        clearTestableWearableSensingService();
        // Do not call mCompanionDeviceManager.disassociate(mCdmAssociationId) until b/326256152 is
        // fixed. Calling it can cause the CDM associationId to be reused in
        // WearableSensingSecureChannel, which causes the async CDM SecureChannel clean up in the
        // current test to close the WearableSensingSecureChannel used in the next test.
        // This issue is way more likely to reproduce on a freshly wiped device since CDM persists
        // association IDs on disk.
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PROVIDE_WEARABLE_CONNECTION_API)
    public void provideConnection_canReceiveStatusFromWss()
            throws Exception {
        AtomicInteger statusCodeRef = new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch statusCodeLatch = new CountDownLatch(1);

        mWearableSensingManager.provideConnection(
                mSocketPair[1],
                EXECUTOR,
                (statusCode) -> {
                    statusCodeRef.set(statusCode);
                    statusCodeLatch.countDown();
                });

        assertThat(statusCodeLatch.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef.get()).isEqualTo(WearableSensingManager.STATUS_SUCCESS);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PROVIDE_WEARABLE_CONNECTION_API)
    public void provideConnection_otherEndAttachedToCdm_canReceiveDataInWss()
            throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE,
                        Manifest.permission.REQUEST_COMPANION_SELF_MANAGED,
                        Manifest.permission.USE_COMPANION_TRANSPORTS);
        mCdmAssociationId = attachTransportToCdm(mSocketPair[0]);
        byte[] dataToWrite = DATA_TO_WRITE.getBytes(StandardCharsets.UTF_8);
        CountDownLatch statusCodeLatch = new CountDownLatch(1);

        mWearableSensingManager.provideConnection(
                mSocketPair[1], EXECUTOR, (statusCode) -> statusCodeLatch.countDown());
        assertThat(statusCodeLatch.await(3, SECONDS)).isTrue();
        mCompanionDeviceManager.sendMessage(
                CDM_MESSAGE_ONEWAY_FROM_WEARABLE, dataToWrite, new int[] {mCdmAssociationId});

        assertThat(statusCodeLatch.await(3, SECONDS)).isTrue();

        verifyDataReceivedFromWearable(DATA_TO_WRITE);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PROVIDE_WEARABLE_CONNECTION_API)
    public void
            provideConnection_otherEndAttachedToCdm_canReceiveDataFromWss()
                    throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE,
                        Manifest.permission.REQUEST_COMPANION_SELF_MANAGED,
                        Manifest.permission.USE_COMPANION_TRANSPORTS);
        mCdmAssociationId = attachTransportToCdm(mSocketPair[0]);
        CountDownLatch statusCodeLatch = new CountDownLatch(1);

        mWearableSensingManager.provideConnection(
                mSocketPair[1], EXECUTOR, (statusCode) -> statusCodeLatch.countDown());
        assertThat(statusCodeLatch.await(3, SECONDS)).isTrue();
        sendDataToWearableFromWss(DATA_TO_WRITE);

        byte[] dataReceived = receiveMessageFromCdm(mCdmAssociationId);
        assertThat(dataReceived).isEqualTo(DATA_TO_WRITE.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PROVIDE_WEARABLE_CONNECTION_API)
    public void
            provideConnection_wearableStreamClosedThenSendDataFromWss_channelErrorStatus()
                    throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE,
                        Manifest.permission.REQUEST_COMPANION_SELF_MANAGED,
                        Manifest.permission.USE_COMPANION_TRANSPORTS);
        AtomicInteger statusCodeRef = new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch successStatusLatch = new CountDownLatch(1);
        // Count down twice because we expect a success status followed by a channel error
        CountDownLatch channelErrorStatusLatch = new CountDownLatch(2);
        mCdmAssociationId = attachTransportToCdm(mSocketPair[0]);

        mWearableSensingManager.provideConnection(
                mSocketPair[1],
                EXECUTOR,
                (statusCode) -> {
                    statusCodeRef.set(statusCode);
                    successStatusLatch.countDown();
                    channelErrorStatusLatch.countDown();
                });
        assertThat(successStatusLatch.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef.get()).isEqualTo(WearableSensingManager.STATUS_SUCCESS);

        mSocketPair[0].close();
        sendDataToWearableFromWss(DATA_TO_WRITE);

        assertThat(channelErrorStatusLatch.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef.get()).isEqualTo(WearableSensingManager.STATUS_CHANNEL_ERROR);
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_PROVIDE_WEARABLE_CONNECTION_API,
        Flags.FLAG_ENABLE_RESTART_WSS_PROCESS
    })
    public void
            provideConnection_wearableStreamClosedThenSendDataFromWss_restartWssProcess()
                    throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE,
                        Manifest.permission.REQUEST_COMPANION_SELF_MANAGED,
                        Manifest.permission.USE_COMPANION_TRANSPORTS);
        CountDownLatch statusLatch = new CountDownLatch(1);
        mCdmAssociationId = attachTransportToCdm(mSocketPair[0]);

        mWearableSensingManager.provideConnection(
                mSocketPair[1], EXECUTOR, (statusCode) -> statusLatch.countDown());
        assertThat(statusLatch.await(3, SECONDS)).isTrue();
        setBooleanStateInWss();
        verifyBooleanStateInWss(true);

        // Trigger a channel error
        mSocketPair[0].close();
        sendDataToWearableFromWss(DATA_TO_WRITE);

        // wait until the RemoteWearableSensingService is notified of the Binder/process death
        SystemClock.sleep(2000);
        // This indirectly verifies that the process is restarted
        verifyBooleanStateInWss(false);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PROVIDE_WEARABLE_CONNECTION_API)
    public void provideConnection_wssStreamClosed_channelErrorStatus() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE,
                        Manifest.permission.REQUEST_COMPANION_SELF_MANAGED,
                        Manifest.permission.USE_COMPANION_TRANSPORTS);
        AtomicInteger statusCodeRef = new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch successStatusLatch = new CountDownLatch(1);
        // Count down twice because we expect a success status followed by a channel error
        CountDownLatch channelErrorStatusLatch = new CountDownLatch(2);
        mCdmAssociationId = attachTransportToCdm(mSocketPair[0]);

        mWearableSensingManager.provideConnection(
                mSocketPair[1],
                EXECUTOR,
                (statusCode) -> {
                    statusCodeRef.set(statusCode);
                    successStatusLatch.countDown();
                    channelErrorStatusLatch.countDown();
                });
        assertThat(successStatusLatch.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef.get()).isEqualTo(WearableSensingManager.STATUS_SUCCESS);

        closeWearableConnectionFromWss();

        assertThat(channelErrorStatusLatch.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef.get()).isEqualTo(WearableSensingManager.STATUS_CHANNEL_ERROR);
    }

    @Test
    @RequiresFlagsEnabled({
        Flags.FLAG_ENABLE_PROVIDE_WEARABLE_CONNECTION_API,
        Flags.FLAG_ENABLE_RESTART_WSS_PROCESS
    })
    public void provideConnection_restartsWssProcess() throws Exception {
        // The first call of provideConnection may not restart the process,
        // so we call once first, then set up and call it again
        AtomicInteger statusCodeRef1 = new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch statusCodeLatch1 = new CountDownLatch(1);
        mWearableSensingManager.provideConnection(
                mSocketPair[1],
                EXECUTOR,
                (statusCode) -> {
                    statusCodeRef1.set(statusCode);
                    statusCodeLatch1.countDown();
                });
        assertThat(statusCodeLatch1.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef1.get()).isEqualTo(WearableSensingManager.STATUS_SUCCESS);
        setBooleanStateInWss();
        // Verify that the state is true after setting it
        verifyBooleanStateInWss(true);
        AtomicInteger statusCodeRef2 = new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch statusCodeLatch2 = new CountDownLatch(1);

        mWearableSensingManager.provideConnection(
                mSocketPair[1],
                EXECUTOR,
                (statusCode) -> {
                    statusCodeRef2.set(statusCode);
                    statusCodeLatch2.countDown();
                });
        assertThat(statusCodeLatch2.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef2.get()).isEqualTo(WearableSensingManager.STATUS_SUCCESS);

        // This indirectly verifies that the process is restarted
        verifyBooleanStateInWss(false);
    }

    private int attachTransportToCdm(ParcelFileDescriptor pfd) throws Exception {
        CountDownLatch transportAvailableLatch = new CountDownLatch(1);
        AtomicInteger associationIdRef = new AtomicInteger();
        mCompanionDeviceManager.associate(
                new AssociationRequest.Builder()
                        .setDisplayName(CDM_ASSOCIATION_DISPLAY_NAME)
                        .setSelfManaged(true)
                        .build(),
                EXECUTOR,
                new CompanionDeviceManager.Callback() {
                    @Override
                    public void onAssociationCreated(AssociationInfo associationInfo) {
                        Log.d(TAG,
                                "#onAssociationCreated, associationId: " + associationInfo.getId());
                        associationIdRef.set(associationInfo.getId());
                        mCompanionDeviceManager.addOnTransportsChangedListener(
                                EXECUTOR,
                                associationInfos -> {
                                    if (associationInfos.stream()
                                            .anyMatch(
                                                    info ->
                                                            info.getId()
                                                                    == associationInfo.getId())) {
                                        transportAvailableLatch.countDown();
                                    }
                                });
                        mCompanionDeviceManager.attachSystemDataTransport(
                                associationInfo.getId(),
                                new AutoCloseInputStream(pfd),
                                new AutoCloseOutputStream(pfd));
                    }

                    @Override
                    public void onFailure(CharSequence error) {
                        Log.e(TAG, "Failed to create CDM association: " + error);
                    }
                });
        assertThat(transportAvailableLatch.await(3, SECONDS)).isTrue();
        return associationIdRef.get();
    }

    private byte[] receiveMessageFromCdm(int associationId) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<byte[]> messageRef = new AtomicReference<>();
        mCompanionDeviceManager.addOnMessageReceivedListener(
                EXECUTOR,
                CDM_MESSAGE_ONEWAY_TO_WEARABLE,
                (associationIdForMessage, messageBytes) -> {
                    if (associationIdForMessage == associationId) {
                        messageRef.set(messageBytes);
                        latch.countDown();
                    }
                });
        assertThat(latch.await(3, SECONDS)).isTrue();
        return messageRef.get();
    }

    private void clearTestableWearableSensingService() {
        runShellCommand("cmd wearable_sensing set-temporary-service %d", USER_ID);
    }

    private void bindToIsolatedWearableSensingService() {
        assertThat(getWearableSensingServiceComponent()).isNotEqualTo(CTS_ISOLATED_SERVICE_NAME);
        setTestableWearableSensingService(CTS_ISOLATED_SERVICE_NAME);
        assertThat(CTS_ISOLATED_SERVICE_NAME).contains(getWearableSensingServiceComponent());
    }

    private String getWearableSensingServiceComponent() {
        return runShellCommand("cmd wearable_sensing get-bound-package %d", USER_ID);
    }

    private void setTestableWearableSensingService(String service) {
        runShellCommand(
                "cmd wearable_sensing set-temporary-service %d %s %d",
                USER_ID, service, TEMPORARY_SERVICE_DURATION);
    }

    private void resetIsolatedWearableSensingServiceStates() throws Exception {
        sendActionToIsolatedWearableSensingServiceAndWait(ACTION_RESET);
    }

    private void verifyDataReceivedFromWearable(String expectedString) throws Exception {
        PersistableBundle instruction = new PersistableBundle();
        instruction.putString(BUNDLE_ACTION_KEY, ACTION_VERIFY_DATA_RECEIVED_FROM_WEARABLE);
        instruction.putString(EXPECTED_STRING_FROM_WEARABLE_KEY, expectedString);
        assertThat(sendInstructionToIsolatedWearableSensingServiceAndWait(instruction))
                .isEqualTo(WearableSensingManager.STATUS_SUCCESS);
    }

    private void sendDataToWearableFromWss(String dataToSend) throws Exception {
        PersistableBundle instruction = new PersistableBundle();
        instruction.putString(BUNDLE_ACTION_KEY, ACTION_SEND_DATA_TO_WEARABLE);
        instruction.putString(STRING_TO_SEND_KEY, dataToSend);
        assertThat(sendInstructionToIsolatedWearableSensingServiceAndWait(instruction))
                .isEqualTo(WearableSensingManager.STATUS_SUCCESS);
    }

    private void closeWearableConnectionFromWss() throws Exception {
        sendActionToIsolatedWearableSensingServiceAndWait(ACTION_CLOSE_WEARABLE_CONNECTION);
    }

    private void setBooleanStateInWss() throws Exception {
        sendActionToIsolatedWearableSensingServiceAndWait(ACTION_SET_BOOLEAN_STATE);
    }

    private void verifyBooleanStateInWss(boolean expectedState) throws Exception {
        int statusCode =
                sendActionToIsolatedWearableSensingServiceAndWait(ACTION_VERIFY_BOOLEAN_STATE);
        if (expectedState) {
            assertThat(statusCode).isEqualTo(WearableSensingManager.STATUS_SUCCESS);
        } else {
            assertThat(statusCode).isEqualTo(WearableSensingManager.STATUS_UNKNOWN);
        }
    }

    private int sendActionToIsolatedWearableSensingServiceAndWait(String action) throws Exception {
        PersistableBundle instruction = new PersistableBundle();
        instruction.putString(BUNDLE_ACTION_KEY, action);
        return sendInstructionToIsolatedWearableSensingServiceAndWait(instruction);
    }

    private int sendInstructionToIsolatedWearableSensingServiceAndWait(
            PersistableBundle instruction) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger statusRef = new AtomicInteger();
        mWearableSensingManager.provideData(
                instruction,
                null,
                EXECUTOR,
                (status) -> {
                    statusRef.set(status);
                    latch.countDown();
                });
        assertThat(latch.await(3, SECONDS)).isTrue();
        return statusRef.get();
    }

    private static boolean isTelevision(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    private static boolean isWatch(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_WATCH);
    }
}
