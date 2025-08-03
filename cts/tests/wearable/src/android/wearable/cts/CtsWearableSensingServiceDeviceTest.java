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

import static android.app.wearable.WearableSensingDataRequest.getMaxRequestSize;
import static android.app.wearable.WearableSensingDataRequest.getRateLimit;
import static android.wearable.cts.CtsWearableSensingService.whenCallbackTriggeredRespondWithServiceStatus;
import static android.wearable.cts.CtsWearableSensingService.whenCallbackTriggeredRespondWithStatus;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import static java.util.concurrent.TimeUnit.SECONDS;

import android.Manifest;
import android.app.PendingIntent;
import android.app.ambientcontext.AmbientContextManager;
import android.app.wearable.Flags;
import android.app.wearable.WearableSensingDataRequest;
import android.app.wearable.WearableSensingManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.service.wearable.WearableSensingDataRequester;
import android.text.TextUtils;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.DeviceConfigStateChangerRule;

import com.google.common.collect.Iterables;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This suite of test ensures that WearableSensingManagerService behaves correctly when properly
 * bound to a WearableSensingService implementation.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(
        reason = "PM will not recognize CtsWearableSensingService in instantMode.")
public class CtsWearableSensingServiceDeviceTest {
    private static final String NAMESPACE_wearable_sensing = "wearable_sensing";
    private static final String NAMESPACE_ambient_context = "ambient_context";

    private static final String KEY_SERVICE_ENABLED = "service_enabled";
    private static final String FAKE_APP_PACKAGE = "foo.bar.baz";
    private static final String CTS_PACKAGE_NAME =
            CtsWearableSensingService.class.getPackage().getName();
    private static final String CTS_SERVICE_NAME = CTS_PACKAGE_NAME + "/."
            + CtsWearableSensingService.class.getSimpleName();
    private static final int USER_ID = UserHandle.myUserId();
    private static final int TEMPORARY_SERVICE_DURATION = 30000; // ms

    private static final String KEY_FOR_PROVIDE_DATA = "foo.bar.baz_key_for_provide_data";
    private static final int VALUE_TO_SEND = 1000;
    private static final String VALUE_TO_WRITE = "wearable_sensing";

    private static final int PLACEHOLDER_DATA_TYPE = 234;
    private static final String DATA_REQUEST_DETAILS_KEY_1 = "k1";
    private static final String DATA_REQUEST_DETAILS_KEY_2 = "k2";
    private static final String DATA_REQUEST_DETAILS_VALUE_1 = "v1";
    private static final int DATA_REQUEST_DETAILS_VALUE_2 = 98765;

    private final boolean mIsTestable =
            !TextUtils.isEmpty(getAmbientContextDetectionServiceComponent());
    private final Executor mExecutor = InstrumentationRegistry.getContext().getMainExecutor();
    private Context mContext;
    private WearableSensingManager mWearableSensingManager;
    private PendingIntent mDataRequestObserverPendingIntent;
    private WearableSensingDataRequest mDataRequest;
    private WearableSensingDataRequest mLargeDataRequest;

    @Rule
    public final DeviceConfigStateChangerRule mLookAllTheseRules =
            new DeviceConfigStateChangerRule(getInstrumentation().getTargetContext(),
                    NAMESPACE_wearable_sensing,
                    KEY_SERVICE_ENABLED,
                    "true");

    @Rule
    public final DeviceConfigStateChangerRule mAmbientContextRules =
            new DeviceConfigStateChangerRule(getInstrumentation().getTargetContext(),
                    NAMESPACE_ambient_context,
                    KEY_SERVICE_ENABLED,
                    "true");

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        assumeTrue("VERSION.SDK_INT=" + VERSION.SDK_INT,
                VERSION.SDK_INT >= VERSION_CODES.UPSIDE_DOWN_CAKE);
        mContext = getInstrumentation().getContext();
        assumeFalse(isWatch(mContext));  // WearableSensingManagerService is not supported on WearOS
        mWearableSensingManager = mContext.getSystemService(WearableSensingManager.class);
        mDataRequestObserverPendingIntent = createDataRequestPendingIntent(mContext);
        PersistableBundle dataRequestDetails = new PersistableBundle();
        dataRequestDetails.putString(DATA_REQUEST_DETAILS_KEY_1, DATA_REQUEST_DETAILS_VALUE_1);
        dataRequestDetails.putInt(DATA_REQUEST_DETAILS_KEY_2, DATA_REQUEST_DETAILS_VALUE_2);
        mDataRequest =
                new WearableSensingDataRequest.Builder(PLACEHOLDER_DATA_TYPE)
                        .setRequestDetails(dataRequestDetails)
                        .build();
        PersistableBundle largeDataRequestDetails = new PersistableBundle();
        largeDataRequestDetails.putString(
                "myVeryVeryVeryVeryLargeKey1", "myVeryVeryVeryVeryLargeValue1");
        largeDataRequestDetails.putString(
                "myVeryVeryVeryVeryLargeKey2", "myVeryVeryVeryVeryLargeValue2");
        mLargeDataRequest =
                new WearableSensingDataRequest.Builder(PLACEHOLDER_DATA_TYPE)
                        .setRequestDetails(largeDataRequestDetails)
                        .build();
        CtsWearableSensingService.reset();
        CtsWearableSensingDataRequestBroadcastReceiver.reset();
        clearTestableWearableSensingService();
        destroyDataStream();
        bindToTestableWearableSensingService();
        createDataStream();
    }

    @Test
    public void testProvideDataStream_success() {
        CtsWearableSensingService.whenCallbackTriggeredRespondWithStatus(
                WearableSensingManager.STATUS_SUCCESS);

        provideDataStream();
        CtsWearableSensingService.awaitResult();

        assertThat(getLastStatusCode()).isEqualTo(WearableSensingManager.STATUS_SUCCESS);
    }

    @Test
    public void testProvideDataStream_failure() {
        whenCallbackTriggeredRespondWithStatus(WearableSensingManager.STATUS_SERVICE_UNAVAILABLE);

        provideDataStream();
        CtsWearableSensingService.awaitResult();

        assertThat(getLastStatusCode()).isEqualTo(
                WearableSensingManager.STATUS_SERVICE_UNAVAILABLE);
    }

    @Test
    public void testProvideDataStream_writeData_reads() throws Exception {
        whenCallbackTriggeredRespondWithStatus(WearableSensingManager.STATUS_SUCCESS);

        provideDataStream();
        writeDataToStream();
        CtsWearableSensingService.awaitResult();

        assertThat(readDataStream()).isEqualTo(VALUE_TO_WRITE);
    }

    @Test
    public void testProvideData_success() {
        whenCallbackTriggeredRespondWithStatus(WearableSensingManager.STATUS_SUCCESS);

        provideData();
        CtsWearableSensingService.awaitResult();

        assertThat(getLastStatusCode()).isEqualTo(WearableSensingManager.STATUS_SUCCESS);
    }

    @Test
    public void testProvideData_failure() {
        whenCallbackTriggeredRespondWithStatus(WearableSensingManager.STATUS_SERVICE_UNAVAILABLE);

        provideData();
        CtsWearableSensingService.awaitResult();

        assertThat(getLastStatusCode()).isEqualTo(
                WearableSensingManager.STATUS_SERVICE_UNAVAILABLE);
    }

    @Test
    public void testProvideData_getsRightData() {
        whenCallbackTriggeredRespondWithStatus(WearableSensingManager.STATUS_SUCCESS);

        provideData();
        CtsWearableSensingService.awaitResult();

        assertThat(CtsWearableSensingService.getData().getInt(KEY_FOR_PROVIDE_DATA))
                .isEqualTo(VALUE_TO_SEND);
    }

    @Test
    public void startDetection_wearable_getsCall() {
        assumeTrue("Feature not available on this device. Skipping test.", mIsTestable);

        setTestableAmbientContextDetectionService(CTS_SERVICE_NAME);
        whenCallbackTriggeredRespondWithServiceStatus(AmbientContextManager.STATUS_SUCCESS);

        callStartDetection();
        CtsWearableSensingService.awaitResultAmbientContextDetectionService();

        assertThat(getLastStatusCodeAmbientDetectionService())
                .isEqualTo(AmbientContextManager.STATUS_SUCCESS);
    }

    // Requesting for a detection start with mixed events will not be
    @Test
    public void startDetection_mixed_doesNotGetCall() {
        setTestableAmbientContextDetectionService(CTS_SERVICE_NAME);

        callStartDetectionMixed();

        CtsWearableSensingService.expectTimeOut();
    }

    @Test
    public void stopDetection_wearable_getsCall() {
        assumeTrue("Feature not available on this device. Skipping test.", mIsTestable);

        setTestableAmbientContextDetectionService(CTS_SERVICE_NAME);
        whenCallbackTriggeredRespondWithServiceStatus(AmbientContextManager.STATUS_SUCCESS);
        callStartDetection();
        CtsWearableSensingService.awaitResultAmbientContextDetectionService();

        callStopDetection();
        CtsWearableSensingService.awaitResultAmbientContextDetectionService();

        assertThat(CtsWearableSensingService.getLastCallingPackage()).isEqualTo(FAKE_APP_PACKAGE);
    }

    @Test
    public void stopDetection_notWearable_doesNotGetCall() {
        setTestableAmbientContextDetectionService(CTS_SERVICE_NAME);
        callStartDetectionDefault();
        CtsWearableSensingService.expectTimeOut();

        callStopDetection();
        CtsWearableSensingService.expectTimeOut();

        assertThat(CtsWearableSensingService.getLastCallingPackage()).isNull();
    }

    @Test
    public void queryServiceStatus_available() {
        assumeTrue("Feature not available on this device. Skipping test.", mIsTestable);

        setTestableAmbientContextDetectionService(CTS_SERVICE_NAME);
        whenCallbackTriggeredRespondWithServiceStatus(AmbientContextManager.STATUS_SUCCESS);

        callQueryWearableServiceStatus();
        CtsWearableSensingService.awaitResultAmbientContextDetectionService();

        assertThat(getLastStatusCodeAmbientDetectionService())
                .isEqualTo(AmbientContextManager.STATUS_SUCCESS);
    }

    @Test
    public void queryDefaultServiceStatus_notCalled() {
        setTestableAmbientContextDetectionService(CTS_SERVICE_NAME);
        whenCallbackTriggeredRespondWithServiceStatus(AmbientContextManager.STATUS_SUCCESS);

        callQueryServiceStatus();

        CtsWearableSensingService.expectTimeOut();
    }

    @Test
    public void queryMixedServiceStatus_notCalled() {
        setTestableAmbientContextDetectionService(CTS_SERVICE_NAME);
        whenCallbackTriggeredRespondWithServiceStatus(AmbientContextManager.STATUS_SUCCESS);

        callQueryMixedServiceStatus();

        CtsWearableSensingService.expectTimeOut();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_DATA_REQUEST_OBSERVER_API)
    public void sendDataRequest_isReceivedByObserver() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE);
        AtomicInteger dataRequestStatusRef =
                new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch dataRequestStatusLatch = new CountDownLatch(1);

        // send a request from WearableSensingService
        registerAndGetDataRequester()
                .requestData(
                        mDataRequest,
                        (status) -> {
                            dataRequestStatusRef.set(status);
                            dataRequestStatusLatch.countDown();
                        });

        CtsWearableSensingDataRequestBroadcastReceiver.awaitResult();
        // assert that the broadcast receiver can receive the result
        WearableSensingDataRequest receivedDataRequest =
                CtsWearableSensingDataRequestBroadcastReceiver.getLatestDataRequest();
        assertThat(receivedDataRequest).isNotNull();
        assertThat(receivedDataRequest.getDataType()).isEqualTo(PLACEHOLDER_DATA_TYPE);
        PersistableBundle receivedDataRequestDetails = receivedDataRequest.getRequestDetails();
        assertThat(receivedDataRequestDetails.getString(DATA_REQUEST_DETAILS_KEY_1))
                .isEqualTo(DATA_REQUEST_DETAILS_VALUE_1);
        assertThat(receivedDataRequestDetails.getInt(DATA_REQUEST_DETAILS_KEY_2))
                .isEqualTo(DATA_REQUEST_DETAILS_VALUE_2);
        assertThat(dataRequestStatusLatch.await(3, SECONDS)).isTrue();
        assertThat(dataRequestStatusRef.get())
                .isEqualTo(WearableSensingDataRequester.STATUS_SUCCESS);
        // Reset the rate limit to avoid interfering with other tests
        resetDataRequestRateLimitWindowSize();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_DATA_REQUEST_OBSERVER_API)
    public void sendDataRequest_requestTooLarge_notReceivedByObserver() throws Exception {
        assumeTrue(
                "Data request is not larger than size limit, skipping test.",
                mLargeDataRequest.getDataSize() > getMaxRequestSize());
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE);
        AtomicInteger dataRequestStatusRef =
                new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch dataRequestStatusLatch = new CountDownLatch(1);

        // send a large request from WearableSensingService
        registerAndGetDataRequester()
                .requestData(
                        mLargeDataRequest,
                        (status) -> {
                            dataRequestStatusRef.set(status);
                            dataRequestStatusLatch.countDown();
                        });

        // CtsWearableSensingDataRequestBroadcastReceiver throws an AssertionError on timeout
        assertThrows(
                AssertionError.class,
                () -> CtsWearableSensingDataRequestBroadcastReceiver.awaitResult());
        assertThat(dataRequestStatusLatch.await(3, SECONDS)).isTrue();
        assertThat(dataRequestStatusRef.get())
                .isEqualTo(WearableSensingDataRequester.STATUS_TOO_LARGE);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_DATA_REQUEST_OBSERVER_API)
    public void sendDataRequestsAtRateLimit_allReceivedByObserver() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE);
        CtsWearableSensingDataRequestBroadcastReceiver.setResultCountToAwait(
                getRateLimit());
        WearableSensingDataRequester dataRequester = registerAndGetDataRequester();

        for (int i = 0; i < getRateLimit(); i++) {
            dataRequester.requestData(mDataRequest, status -> {});
        }

        CtsWearableSensingDataRequestBroadcastReceiver.awaitResult();
        // no exception means all requests are received before timeout
        // Reset the rate limit to avoid interfering with other tests
        resetDataRequestRateLimitWindowSize();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_DATA_REQUEST_OBSERVER_API)
    public void sendDataRequests_tooFrequent_notReceivedByObserver() throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE);
        CtsWearableSensingDataRequestBroadcastReceiver.setResultCountToAwait(
                getRateLimit());
        WearableSensingDataRequester dataRequester = registerAndGetDataRequester();
        AtomicInteger dataRequestStatusRef =
                new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
        CountDownLatch dataRequestStatusLatch = new CountDownLatch(1);

        // Reach the limit
        for (int i = 0; i < getRateLimit(); i++) {
            dataRequester.requestData(mDataRequest, status -> {});
        }
        CtsWearableSensingDataRequestBroadcastReceiver.awaitResult();
        // Send one more
        CtsWearableSensingDataRequestBroadcastReceiver.setResultCountToAwait(1);
        dataRequester.requestData(
                mDataRequest,
                (status) -> {
                        dataRequestStatusRef.set(status);
                        dataRequestStatusLatch.countDown();
                });

        // CtsWearableSensingDataRequestBroadcastReceiver throws an AssertionError on timeout
        assertThrows(
                AssertionError.class,
                () -> CtsWearableSensingDataRequestBroadcastReceiver.awaitResult());
        assertThat(dataRequestStatusLatch.await(3, SECONDS)).isTrue();
        assertThat(dataRequestStatusRef.get())
                .isEqualTo(WearableSensingDataRequester.STATUS_TOO_FREQUENT);
        // Reset the rate limit to avoid interfering other tests
        resetDataRequestRateLimitWindowSize();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_DATA_REQUEST_OBSERVER_API)
    public void sendDataRequestsAtRateLimit_waitForOnePeriodThenSendAgain_isReceivedByObserver()
            throws Exception {
        getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.MANAGE_WEARABLE_SENSING_SERVICE);
        // Allows this test to wait just 20 seconds instead of the full getRateLimitWindowSize()
        // The window cannot be smaller than 20 seconds. It is lower-bounded by
        // com.android.server.utils.quota.QuotaTracker#MIN_WINDOW_SIZE_MS
        setDataRequestRateLimitWindowSizeToTwentySeconds();
        try {
            CtsWearableSensingDataRequestBroadcastReceiver.setResultCountToAwait(getRateLimit());
            WearableSensingDataRequester dataRequester = registerAndGetDataRequester();
            AtomicInteger dataRequestStatusRef =
                    new AtomicInteger(WearableSensingManager.STATUS_UNKNOWN);
            CountDownLatch dataRequestStatusLatch = new CountDownLatch(1);

            // Reach the limit
            for (int i = 0; i < getRateLimit(); i++) {
                dataRequester.requestData(mDataRequest, status -> {});
            }
            CtsWearableSensingDataRequestBroadcastReceiver.awaitResult();
            // Wait for one window size (overridden to 20 seconds above) plus some buffer
            SystemClock.sleep(20200);
            // Send one more
            CtsWearableSensingDataRequestBroadcastReceiver.setResultCountToAwait(1);
            dataRequester.requestData(
                    mDataRequest,
                    (status) -> {
                        dataRequestStatusRef.set(status);
                        dataRequestStatusLatch.countDown();
                    });

            CtsWearableSensingDataRequestBroadcastReceiver.awaitResult();
            assertThat(dataRequestStatusLatch.await(3, SECONDS)).isTrue();
            assertThat(dataRequestStatusRef.get())
                    .isEqualTo(WearableSensingDataRequester.STATUS_SUCCESS);
        } finally {
            resetDataRequestRateLimitWindowSize();
        }
    }

    @After
    public void tearDown() {
        clearTestableWearableSensingService();
        clearTestableAmbientContextDetectionService();
        destroyDataStream();
        getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    private void bindToTestableWearableSensingService() {
        // On Manager, bind to test service
        assertThat(getWearableSensingServiceComponent()).isNotEqualTo(CTS_SERVICE_NAME);
        setTestableWearableSensingService(CTS_SERVICE_NAME);
        assertThat(CTS_SERVICE_NAME).contains(getWearableSensingServiceComponent());
    }

    private String getWearableSensingServiceComponent() {
        return runShellCommand("cmd wearable_sensing get-bound-package %d", USER_ID);
    }

    private void setTestableWearableSensingService(String service) {
        runShellCommand("cmd wearable_sensing set-temporary-service %d %s %d",
                USER_ID, service, TEMPORARY_SERVICE_DURATION);
    }

    private void clearTestableWearableSensingService() {
        runShellCommand("cmd wearable_sensing set-temporary-service %d", USER_ID);
    }

    private void callStartDetection() {
        runShellCommand("cmd ambient_context start-detection-wearable %d %s",
                USER_ID, FAKE_APP_PACKAGE);
    }

    private void callStartDetectionMixed() {
        runShellCommand("cmd ambient_context start-detection-mixed %d %s",
                USER_ID, FAKE_APP_PACKAGE);
    }

    private void callStartDetectionDefault() {
        runShellCommand("cmd ambient_context start-detection %d %s",
                USER_ID, FAKE_APP_PACKAGE);
    }

    private void createDataStream() {
        runShellCommand("cmd wearable_sensing create-data-stream");
    }

    private void destroyDataStream() {
        runShellCommand("cmd wearable_sensing destroy-data-stream");
    }

    private void callStopDetection() {
        runShellCommand("cmd ambient_context stop-detection %d %s",
                USER_ID, FAKE_APP_PACKAGE);
    }

    private void provideDataStream() {
        runShellCommand("cmd wearable_sensing provide-data-stream %d", USER_ID);
    }

    private void provideData() {
        runShellCommand("cmd wearable_sensing provide-data %d %s %d",
                USER_ID, KEY_FOR_PROVIDE_DATA, VALUE_TO_SEND);
    }

    private void writeDataToStream() {
        runShellCommand("cmd wearable_sensing write-to-data-stream %s", VALUE_TO_WRITE);
    }

    private String readDataStream() throws Exception {
        ParcelFileDescriptor pipe = CtsWearableSensingService.getParcelFileDescriptor();
        InputStream is = new ParcelFileDescriptor.AutoCloseInputStream(pipe);
        byte[] bytes = new byte[VALUE_TO_WRITE.getBytes().length];
        is.read(bytes, 0, bytes.length);
        return new String(bytes);
    }

    private int getLastStatusCode() {
        return Integer.parseInt(runShellCommand(
                "cmd wearable_sensing get-last-status-code"));
    }

    // This method will also reset the rate limit
    private void setDataRequestRateLimitWindowSizeToTwentySeconds() {
        runShellCommand("cmd wearable_sensing set-data-request-rate-limit-window-size 20");
    }

    // This method will also reset the rate limit
    private void resetDataRequestRateLimitWindowSize() {
        runShellCommand("cmd wearable_sensing set-data-request-rate-limit-window-size 0");
    }

    private int getLastStatusCodeAmbientDetectionService() {
        return Integer.parseInt(runShellCommand(
                "cmd ambient_context get-last-status-code"));
    }

    private void setTestableAmbientContextDetectionService(String service) {
        runShellCommand("cmd ambient_context set-temporary-services %d %s %s %d",
                USER_ID, service, service, TEMPORARY_SERVICE_DURATION);
    }

    private void callQueryServiceStatus() {
        runShellCommand("cmd ambient_context query-service-status %d %s",
                USER_ID, FAKE_APP_PACKAGE);
    }

    private void callQueryWearableServiceStatus() {
        runShellCommand("cmd ambient_context query-wearable-service-status %d %s",
                USER_ID, FAKE_APP_PACKAGE);
    }

    private void callQueryMixedServiceStatus() {
        runShellCommand("cmd ambient_context query-mixed-service-status %d %s",
                USER_ID, FAKE_APP_PACKAGE);
    }

    private void clearTestableAmbientContextDetectionService() {
        runShellCommand("cmd ambient_context set-temporary-service %d", USER_ID);
    }

    private String getAmbientContextDetectionServiceComponent() {
        return runShellCommand("cmd ambient_context get-bound-package %s", USER_ID);
    }

    private WearableSensingDataRequester registerAndGetDataRequester() {
        mWearableSensingManager.registerDataRequestObserver(
                PLACEHOLDER_DATA_TYPE,
                mDataRequestObserverPendingIntent,
                mExecutor,
                (dataRequestObserverRegistrationStatus) -> {});
        CtsWearableSensingService.awaitResult();
        assertThat(CtsWearableSensingService.getDataRequesters(PLACEHOLDER_DATA_TYPE)).hasSize(1);
        return Iterables.getOnlyElement(
                CtsWearableSensingService.getDataRequesters(PLACEHOLDER_DATA_TYPE));
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
