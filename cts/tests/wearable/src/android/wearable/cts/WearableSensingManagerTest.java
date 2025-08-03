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

package android.wearable.cts;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.Manifest;
import android.app.PendingIntent;
import android.app.wearable.Flags;
import android.app.wearable.WearableSensingManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.DeviceConfigStateChangerRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/** Test the WearableSensingManager API. Run with "atest CtsWearableSensingServiceTestCases". */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "PM will not recognize CtsWearableSensingService in instantMode.")
public class WearableSensingManagerTest {
    private static final String CTS_PACKAGE_NAME =
            CtsWearableSensingService.class.getPackage().getName();
    private static final String CTS_SERVICE_NAME =
            CTS_PACKAGE_NAME + "/." + CtsWearableSensingService.class.getSimpleName();
    private static final int USER_ID = UserHandle.myUserId();
    private static final int TEMPORARY_SERVICE_DURATION = 5000;
    private static final String NAMESPACE_WEARABLE_SENSING = "wearable_sensing";
    private static final String KEY_SERVICE_ENABLED = "service_enabled";

    private static final Executor EXECUTOR = InstrumentationRegistry.getContext().getMainExecutor();
    private static final int PLACEHOLDER_DATA_TYPE = 123;

    private Context mContext;
    private WearableSensingManager mWearableSensingManager;
    private ParcelFileDescriptor[] mPipe;
    private PendingIntent mDataRequestObserverPendingIntent;

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
        mWearableSensingManager =
                (WearableSensingManager)
                        mContext.getSystemService(Context.WEARABLE_SENSING_SERVICE);
        mPipe = ParcelFileDescriptor.createPipe();
        mDataRequestObserverPendingIntent = createDataRequestPendingIntent(mContext);
        CtsWearableSensingService.reset();
        CtsWearableSensingDataRequestBroadcastReceiver.reset();
        clearTestableWearableSensingService();
        bindToTestableWearableSensingService();
    }

    @After
    public void tearDown() throws Exception {
        clearTestableWearableSensingService();
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    @Test
    public void noAccessWhenAttemptingprovideDataStream() {
        assertEquals(PackageManager.PERMISSION_DENIED, mContext.checkCallingOrSelfPermission(
                Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE));

        // Test non system app throws SecurityException
        assertThrows("no access to provideDataStream from non system component",
                SecurityException.class,
                () -> mWearableSensingManager.provideDataStream(
                        mPipe[0], EXECUTOR, (result) -> {}));
    }

    @Test
    public void noAccessWhenAttemptingProvideData() {
        assertEquals(PackageManager.PERMISSION_DENIED, mContext.checkCallingOrSelfPermission(
                Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE));

        // Test non system app throws SecurityException
        assertThrows("no access to provideData from non system component",
                SecurityException.class,
                () -> mWearableSensingManager.provideData(new PersistableBundle(), null,
                        EXECUTOR, (result) -> {}));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_PROVIDE_WEARABLE_CONNECTION_API)
    public void noAccessWhenAttemptingProvideConnection() {
        assertEquals(
                PackageManager.PERMISSION_DENIED,
                mContext.checkCallingOrSelfPermission(
                        Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE));

        // Test non system app throws SecurityException
        assertThrows(
                "no access to provideConnection from non system component",
                SecurityException.class,
                () ->
                        mWearableSensingManager.provideConnection(
                                mPipe[0], EXECUTOR, (result) -> {}));
    }

    // Other tests for provideConnection are in WearableSensingManagerIsolatedServiceTest
    // because this API will restart the WSS process and hence requires WSS to be in a different
    // process from the test runner.

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_DATA_REQUEST_OBSERVER_API)
    public void noAccessWhenAttemptingRegisterDataRequestObserver() {
        assertEquals(
                PackageManager.PERMISSION_DENIED,
                mContext.checkCallingOrSelfPermission(
                        Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE));

        // Test non system app throws SecurityException
        assertThrows(
                "no access to registerDataRequestObserver from non system component",
                SecurityException.class,
                () ->
                        mWearableSensingManager.registerDataRequestObserver(
                                PLACEHOLDER_DATA_TYPE,
                                mDataRequestObserverPendingIntent,
                                EXECUTOR,
                                (result) -> {}));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_DATA_REQUEST_OBSERVER_API)
    public void noAccessWhenAttemptingUnregisterDataRequestObserver() {
        assertEquals(
                PackageManager.PERMISSION_DENIED,
                mContext.checkCallingOrSelfPermission(
                        Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE));

        // Test non system app throws SecurityException
        assertThrows(
                "no access to unregisterDataRequestObserver from non system component",
                SecurityException.class,
                () ->
                        mWearableSensingManager.unregisterDataRequestObserver(
                                PLACEHOLDER_DATA_TYPE,
                                mDataRequestObserverPendingIntent,
                                EXECUTOR,
                                (result) -> {}));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_HOTWORD_WEARABLE_SENSING_API)
    public void noAccessWhenAttemptingStartHotwordRecognition() {
        assertEquals(
                PackageManager.PERMISSION_DENIED,
                mContext.checkCallingOrSelfPermission(
                        Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE));

        // Test non system app throws SecurityException
        assertThrows(
                "no access to startHotwordRecognition from non system component",
                SecurityException.class,
                () -> mWearableSensingManager.startHotwordRecognition(
                        new ComponentName("my.package", "my.Class"), EXECUTOR, (result) -> {}));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_HOTWORD_WEARABLE_SENSING_API)
    public void noAccessWhenAttemptingStopHotwordRecognition() {
        assertEquals(
                PackageManager.PERMISSION_DENIED,
                mContext.checkCallingOrSelfPermission(
                        Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE));

        // Test non system app throws SecurityException
        assertThrows(
                "no access to stopHotwordRecognition from non system component",
                SecurityException.class,
                () -> mWearableSensingManager.stopHotwordRecognition(EXECUTOR, (result) -> {}));
    }

    // The tests for sending data requests from WearableSensingService are in
    // CtsWearableSensingServiceDeviceTest

    @Test
    @RequiresFlagsEnabled({
            Flags.FLAG_ENABLE_DATA_REQUEST_OBSERVER_API,
            Flags.FLAG_ENABLE_UNSUPPORTED_OPERATION_STATUS_CODE})
    public void registerDataRequestObserver_defaultWssImpl_receivesUnsupportedOperationStatus()
            throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE);
        CtsWearableSensingService.configureMethodsToCallParentAndReturn();
        AtomicInteger statusCodeRef = new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch statusLatch = new CountDownLatch(1);

        mWearableSensingManager.registerDataRequestObserver(
                PLACEHOLDER_DATA_TYPE,
                mDataRequestObserverPendingIntent,
                EXECUTOR,
                (status) -> {
                    statusCodeRef.set(status);
                    statusLatch.countDown();
                });

        assertThat(statusLatch.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef.get())
                .isEqualTo(WearableSensingManager.STATUS_UNSUPPORTED_OPERATION);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_DATA_REQUEST_OBSERVER_API)
    public void registerDataRequestObserver_withPermission_success() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE);
        CtsWearableSensingService.whenCallbackTriggeredRespondWithStatus(
                WearableSensingManager.STATUS_SUCCESS);
        AtomicInteger statusCodeRef = new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch statusLatch = new CountDownLatch(1);

        mWearableSensingManager.registerDataRequestObserver(
                PLACEHOLDER_DATA_TYPE,
                mDataRequestObserverPendingIntent,
                EXECUTOR,
                (status) -> {
                    statusCodeRef.set(status);
                    statusLatch.countDown();
                });

        CtsWearableSensingService.awaitResult();
        assertThat(statusLatch.await(3, SECONDS)).isTrue();
        // The success status code is returned by CtsWearableSensingService
        assertThat(statusCodeRef.get()).isEqualTo(WearableSensingManager.STATUS_SUCCESS);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_DATA_REQUEST_OBSERVER_API)
    public void
            registerDataRequestObserver_withPermission_registersListenerToWearableSensingService() {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE);

        mWearableSensingManager.registerDataRequestObserver(
                PLACEHOLDER_DATA_TYPE, mDataRequestObserverPendingIntent, EXECUTOR, (status) -> {});

        CtsWearableSensingService.awaitResult();
        assertThat(CtsWearableSensingService.getDataRequesters(PLACEHOLDER_DATA_TYPE)).hasSize(1);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_DATA_REQUEST_OBSERVER_API)
    public void registerDataRequestObserver_withPermission_canRegisterTwoObservers() {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE);
        Intent anotherIntent =
                new Intent("not.the.expected.action")
                        .setClass(mContext, CtsWearableSensingDataRequestBroadcastReceiver.class);
        int unusedRequestCode = 0;
        PendingIntent anotherPendingIntent =
                PendingIntent.getBroadcast(
                        mContext, unusedRequestCode, anotherIntent, PendingIntent.FLAG_MUTABLE);

        mWearableSensingManager.registerDataRequestObserver(
                PLACEHOLDER_DATA_TYPE, mDataRequestObserverPendingIntent, EXECUTOR, (status) -> {});
        CtsWearableSensingService.awaitResult();
        mWearableSensingManager.registerDataRequestObserver(
                PLACEHOLDER_DATA_TYPE, anotherPendingIntent, EXECUTOR, (status) -> {});
        CtsWearableSensingService.awaitResult();

        // CtsWearableSensingService.getDataRequesters returns a set, so size 2 means the two
        // data requesters are different.
        assertThat(CtsWearableSensingService.getDataRequesters(PLACEHOLDER_DATA_TYPE)).hasSize(2);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_DATA_REQUEST_OBSERVER_API)
    public void
            registerDataRequestObserver_withPermission_sendsPackageNameToWearableSensingService() {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE);

        mWearableSensingManager.registerDataRequestObserver(
                PLACEHOLDER_DATA_TYPE, mDataRequestObserverPendingIntent, EXECUTOR, (status) -> {});
        CtsWearableSensingService.awaitResult();

        assertThat(CtsWearableSensingService.getLastCallingPackage())
                .isEqualTo(mContext.getPackageName());
    }

    @Test
    @RequiresFlagsEnabled({
            Flags.FLAG_ENABLE_DATA_REQUEST_OBSERVER_API,
            Flags.FLAG_ENABLE_UNSUPPORTED_OPERATION_STATUS_CODE})
    public void unregisterDataRequestObserver_defaultWssImpl_receivesUnsupportedOperationStatus()
            throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE);
        CtsWearableSensingService.configureMethodsToCallParentAndReturn();
        mWearableSensingManager.registerDataRequestObserver(
                PLACEHOLDER_DATA_TYPE, mDataRequestObserverPendingIntent, EXECUTOR, (status) -> {});
        AtomicInteger statusCodeRef = new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch statusLatch = new CountDownLatch(1);

        mWearableSensingManager.unregisterDataRequestObserver(
                PLACEHOLDER_DATA_TYPE,
                mDataRequestObserverPendingIntent,
                EXECUTOR,
                (status) -> {
                    statusCodeRef.set(status);
                    statusLatch.countDown();
                });

        assertThat(statusLatch.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef.get())
                .isEqualTo(WearableSensingManager.STATUS_UNSUPPORTED_OPERATION);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_DATA_REQUEST_OBSERVER_API)
    public void unregisterDataRequestObserver_withPermission_unregistersObserverFromWSS() {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE);
        mWearableSensingManager.registerDataRequestObserver(
                PLACEHOLDER_DATA_TYPE, mDataRequestObserverPendingIntent, EXECUTOR, (status) -> {});
        CtsWearableSensingService.awaitResult();

        mWearableSensingManager.unregisterDataRequestObserver(
                PLACEHOLDER_DATA_TYPE, mDataRequestObserverPendingIntent, EXECUTOR, (status) -> {});
        CtsWearableSensingService.awaitResult();

        // CtsWearableSensingService removes the stored requester by reference. This makes sure the
        // same PendingIntent causes the same WearableSensingDataRequester to be passed to
        // WearableSensingManager#onDataRequestObserverUnregistered
        assertThat(CtsWearableSensingService.getDataRequesters(PLACEHOLDER_DATA_TYPE)).isEmpty();
        assertThat(CtsWearableSensingService.getLastCallingPackage())
                .isEqualTo(mContext.getPackageName());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_DATA_REQUEST_OBSERVER_API)
    public void unregisterDataRequestObserver_withPermission_receivesSuccessStatus()
            throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE);
        mWearableSensingManager.registerDataRequestObserver(
                PLACEHOLDER_DATA_TYPE, mDataRequestObserverPendingIntent, EXECUTOR, (status) -> {});
        CtsWearableSensingService.awaitResult();
        CtsWearableSensingService.whenCallbackTriggeredRespondWithStatus(
                WearableSensingManager.STATUS_SUCCESS);
        AtomicInteger statusCodeRef = new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch statusLatch = new CountDownLatch(1);

        mWearableSensingManager.unregisterDataRequestObserver(
                PLACEHOLDER_DATA_TYPE,
                mDataRequestObserverPendingIntent,
                EXECUTOR,
                (status) -> {
                    statusCodeRef.set(status);
                    statusLatch.countDown();
                });

        CtsWearableSensingService.awaitResult();
        assertThat(statusLatch.await(3, SECONDS)).isTrue();
        // The success status code is returned by CtsWearableSensingService
        assertThat(statusCodeRef.get()).isEqualTo(WearableSensingManager.STATUS_SUCCESS);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_DATA_REQUEST_OBSERVER_API)
    public void
            unregisterDataRequestObserver_withPermission_notPreviouslyRegistered_doesNotCallWSS() {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE);

        mWearableSensingManager.unregisterDataRequestObserver(
                PLACEHOLDER_DATA_TYPE, mDataRequestObserverPendingIntent, EXECUTOR, (status) -> {});

        // CtsWearableSensingService#awaitResult throws an AssertionError on time out.
        assertThrows(AssertionError.class, () -> CtsWearableSensingService.awaitResult());
    }

    // Tests for the other hotword APIs are put in
    // android.voiceinteraction.cts.HotwordDetectionServiceBasicTest so that we can test their
    // interactions with voice interaction components
    @Test
    @RequiresFlagsEnabled({
            Flags.FLAG_ENABLE_HOTWORD_WEARABLE_SENSING_API,
            Flags.FLAG_ENABLE_UNSUPPORTED_OPERATION_STATUS_CODE})
    public void stopHotwordRecognition_defaultWssImpl_receivesUnsupportedOperationStatus()
            throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE);
        CtsWearableSensingService.configureMethodsToCallParentAndReturn();
        AtomicInteger statusCodeRef = new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch statusLatch = new CountDownLatch(1);

        mWearableSensingManager.stopHotwordRecognition(
                EXECUTOR,
                (status) -> {
                    statusCodeRef.set(status);
                    statusLatch.countDown();
                });

        assertThat(statusLatch.await(3, SECONDS)).isTrue();
        assertThat(statusCodeRef.get())
                .isEqualTo(WearableSensingManager.STATUS_UNSUPPORTED_OPERATION);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_HOTWORD_WEARABLE_SENSING_API)
    public void stopHotwordRecognition_callsOnStopHotwordRecognitionInWearableSensingService() {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE);
        CtsWearableSensingService.whenCallbackTriggeredRespondWithStatus(
                WearableSensingManager.STATUS_SUCCESS);

        mWearableSensingManager.stopHotwordRecognition(EXECUTOR, (status) -> {});

        CtsWearableSensingService.awaitResult();
        assertThat(CtsWearableSensingService.getOnStopHotwordRecognitionCalled()).isTrue();
    }

    private void clearTestableWearableSensingService() {
        runShellCommand("cmd wearable_sensing set-temporary-service %d", USER_ID);
    }

    private void bindToTestableWearableSensingService() {
        assertThat(getWearableSensingServiceComponent()).isNotEqualTo(CTS_SERVICE_NAME);
        setTestableWearableSensingService(CTS_SERVICE_NAME);
        assertThat(CTS_SERVICE_NAME).contains(getWearableSensingServiceComponent());
    }

    private String getWearableSensingServiceComponent() {
        return runShellCommand("cmd wearable_sensing get-bound-package %d", USER_ID);
    }

    private void setTestableWearableSensingService(String service) {
        runShellCommand(
                "cmd wearable_sensing set-temporary-service %d %s %d",
                USER_ID, service, TEMPORARY_SERVICE_DURATION);
    }

    private static PendingIntent createDataRequestPendingIntent(Context context) {
        Intent intent =
                new Intent("cts.android.service.wearable.DataRequestReceiverAction")
                        .setClass(context, CtsWearableSensingDataRequestBroadcastReceiver.class);
        int unusedRequestCode = 0;
        return PendingIntent.getBroadcast(
                context, unusedRequestCode, intent, PendingIntent.FLAG_MUTABLE);
    }

    private static boolean isWatch(Context context) {
        PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_WATCH);
    }
}
