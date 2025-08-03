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

package com.android.adservices.service.signals;


import static com.android.adservices.service.signals.SignalsFixture.BASE64_KEY_1;
import static com.android.adservices.service.signals.SignalsFixture.BASE64_VALUE_1;
import static com.android.adservices.service.signals.SignalsFixture.KEY_1;
import static com.android.adservices.service.signals.SignalsFixture.VALUE_1;
import static com.android.adservices.service.signals.SignalsFixture.assertSignalsUnorderedListEqualsExceptIdAndTime;
import static com.android.adservices.service.signals.SignalsFixture.intToBase64;
import static com.android.adservices.service.signals.SignalsFixture.intToBytes;
import static com.android.adservices.service.signals.UpdateProcessingOrchestrator.COLLISION_ERROR;
import static com.android.adservices.service.signals.UpdatesDownloader.CONVERSION_ERROR_MSG;
import static com.android.adservices.service.signals.UpdatesDownloader.PACKAGE_NAME_HEADER;
import static com.android.adservices.service.signals.updateprocessors.Append.TOO_MANY_SIGNALS_ERROR;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.http.MockWebServerRule;
import android.adservices.signals.UpdateSignalsCallback;
import android.adservices.signals.UpdateSignalsInput;
import android.content.Context;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.data.signals.EncoderEndpointsDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.EncoderLogicMetadataDao;
import com.android.adservices.data.signals.EncoderPersistenceDao;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.ProtectedSignalsServiceFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.signals.evict.SignalEvictionController;
import com.android.adservices.service.signals.updateprocessors.UpdateEncoderEventHandler;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessorSelector;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.SettableFuture;
import com.google.mockwebserver.MockResponse;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class SignalsIntakeE2ETest {
    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("localhost");
    private static final Uri URI = Uri.parse("https://localhost");
    private static final long WAIT_TIME_SECONDS = 1L;

    private static final String SIGNALS_PATH = "/signals";

    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();

    @Rule
    public final AdServicesExtendedMockitoRule extendedMockito =
            new AdServicesExtendedMockitoRule.Builder(this).spyStatic(FlagsFactory.class).build();

    private final AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private AppImportanceFilter mAppImportanceFilterMock;
    @Mock private Throttler mMockThrottler;
    @Mock private AdServicesHttpsClient mAdServicesHttpsClientMock;
    @Mock private DevContextFilter mDevContextFilterMock;

    @Spy private final Context mContextSpy = ApplicationProvider.getApplicationContext();

    @Spy
    FledgeAllowListsFilter mFledgeAllowListsFilterSpy =
            new FledgeAllowListsFilter(FlagsFactory.getFlagsForTest(), mAdServicesLoggerMock);

    private ProtectedSignalsDao mSignalsDao;
    private EncoderEndpointsDao mEncoderEndpointsDao;
    private EncoderLogicMetadataDao mEncoderLogicMetadataDao;
    private ProtectedSignalsServiceImpl mService;
    private UpdateSignalsOrchestrator mUpdateSignalsOrchestrator;
    private UpdatesDownloader mUpdatesDownloader;
    private UpdateProcessingOrchestrator mUpdateProcessingOrchestrator;
    private UpdateProcessorSelector mUpdateProcessorSelector;
    private UpdateEncoderEventHandler mUpdateEncoderEventHandler;
    private SignalEvictionController mSignalEvictionController;
    private AdTechUriValidator mAdtechUriValidator;
    private FledgeAuthorizationFilter mFledgeAuthorizationFilter;
    private ProtectedSignalsServiceFilter mProtectedSignalsServiceFilter;
    private EncoderLogicHandler mEncoderLogicHandler;
    private EncoderPersistenceDao mEncoderPersistenceDao;
    private ExecutorService mLightweightExecutorService;
    private ListeningExecutorService mBackgroundExecutorService;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Before
    public void setup() {
        mSignalsDao =
                Room.inMemoryDatabaseBuilder(mContextSpy, ProtectedSignalsDatabase.class)
                        .build()
                        .protectedSignalsDao();
        mEncoderEndpointsDao =
                Room.inMemoryDatabaseBuilder(mContextSpy, ProtectedSignalsDatabase.class)
                        .build()
                        .getEncoderEndpointsDao();
        mEncoderLogicMetadataDao =
                Room.inMemoryDatabaseBuilder(mContextSpy, ProtectedSignalsDatabase.class)
                        .build()
                        .getEncoderLogicMetadataDao();
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mUpdateProcessorSelector = new UpdateProcessorSelector();
        mEncoderPersistenceDao = EncoderPersistenceDao.getInstance(mContextSpy);
        mEncoderLogicHandler =
                new EncoderLogicHandler(
                        mEncoderPersistenceDao,
                        mEncoderEndpointsDao,
                        mEncoderLogicMetadataDao,
                        mSignalsDao,
                        mAdServicesHttpsClientMock,
                        mBackgroundExecutorService);
        mUpdateEncoderEventHandler =
                new UpdateEncoderEventHandler(mEncoderEndpointsDao, mEncoderLogicHandler);
        int oversubscriptionBytesLimit =
                FlagsFactory.getFlagsForTest()
                        .getProtectedSignalsMaxSignalSizePerBuyerWithOversubsciptionBytes();
        mSignalEvictionController =
                new SignalEvictionController(
                        ImmutableList.of(),
                        FlagsFactory.getFlagsForTest()
                                .getProtectedSignalsMaxSignalSizePerBuyerBytes(),
                        oversubscriptionBytesLimit);
        mUpdateProcessingOrchestrator =
                new UpdateProcessingOrchestrator(
                        mSignalsDao,
                        mUpdateProcessorSelector,
                        mUpdateEncoderEventHandler,
                        mSignalEvictionController);
        mAdtechUriValidator = new AdTechUriValidator("", "", "", "");
        mFledgeAuthorizationFilter =
                ExtendedMockito.spy(
                        new FledgeAuthorizationFilter(
                                mContextSpy.getPackageManager(),
                                new EnrollmentDao(
                                        mContextSpy,
                                        DbTestUtil.getSharedDbHelperForTest(),
                                        FlagsFactory.getFlagsForTest()),
                                mAdServicesLoggerMock));
        mProtectedSignalsServiceFilter =
                new ProtectedSignalsServiceFilter(
                        mContextSpy,
                        mConsentManagerMock,
                        FlagsFactory.getFlagsForTest(),
                        mAppImportanceFilterMock,
                        mFledgeAuthorizationFilter,
                        mFledgeAllowListsFilterSpy,
                        mMockThrottler);
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(any()))
                .thenReturn(false);
        when(mConsentManagerMock.isPasFledgeConsentGiven()).thenReturn(true);
        when(mMockThrottler.tryAcquire(any(), any())).thenReturn(true);
        doReturn(DevContext.createForDevOptionsDisabled())
                .when(mDevContextFilterMock)
                .createDevContext();
    }

    private void setupService(boolean mockHttpClient) {
        if (mockHttpClient) {
            mUpdatesDownloader =
                    new UpdatesDownloader(mLightweightExecutorService, mAdServicesHttpsClientMock);
        } else {
            // Shorter timeouts so the test fails quickly if there are issues
            mUpdatesDownloader =
                    new UpdatesDownloader(
                            mLightweightExecutorService,
                            new AdServicesHttpsClient(
                                    mBackgroundExecutorService, 2000, 2000, 10000));
        }
        mUpdateSignalsOrchestrator =
                new UpdateSignalsOrchestrator(
                        mBackgroundExecutorService,
                        mUpdatesDownloader,
                        mUpdateProcessingOrchestrator,
                        mAdtechUriValidator,
                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI);
        mService =
                new ProtectedSignalsServiceImpl(
                        mContextSpy,
                        mUpdateSignalsOrchestrator,
                        mFledgeAuthorizationFilter,
                        mConsentManagerMock,
                        mDevContextFilterMock,
                        AdServicesExecutors.getBackgroundExecutor(),
                        AdServicesLoggerImpl.getInstance(),
                        FlagsFactory.getFlagsForTest(),
                        CallingAppUidSupplierProcessImpl.create(),
                        mProtectedSignalsServiceFilter);
    }

    @Test
    public void testPutNoNetwork() throws Exception {
        setupService(true);
        String json =
                "{" + "\"put\":{\"" + BASE64_KEY_1 + "\":\"" + BASE64_VALUE_1 + "\"" + "}" + "}";

        setupAndRunUpdateSignals(json);

        List<DBProtectedSignal> expected = new ArrayList<>();
        expected.add(
                DBProtectedSignal.builder()
                        .setBuyer(BUYER)
                        .setKey(KEY_1)
                        .setValue(VALUE_1)
                        .setPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .setCreationTime(Instant.now())
                        .build());
        List<DBProtectedSignal> actual = mSignalsDao.getSignalsByBuyer(BUYER);
        // TODO(b/298690010) Restructure code so time can be verified.
        assertSignalsUnorderedListEqualsExceptIdAndTime(expected, actual);
    }

    @Test
    public void testComplexJsonNoNetwork() throws Exception {
        setupService(true);
        String json1 =
                "{"
                        // Put two signals
                        + "\"put\":{\""
                        + intToBase64(1)
                        + "\":\""
                        + intToBase64(101)
                        + "\",\""
                        + intToBase64(2)
                        + "\":\""
                        + intToBase64(102)
                        + "\""
                        + "},"
                        // Append one signal
                        + "\"append\":{\""
                        + intToBase64(3)
                        + "\":"
                        + "{\"values\": [\""
                        + intToBase64(103)
                        + "\"]"
                        + ", \"max_signals\": 3}"
                        + "},"
                        // Put two more signals using put_if_not_present
                        + "\"put_if_not_present\":{\""
                        + intToBase64(4)
                        + "\":\""
                        + intToBase64(105)
                        + "\",\""
                        + intToBase64(5)
                        + "\":\""
                        + intToBase64(106)
                        + "\""
                        + "},"

                        // Removing a non-existent signals has no effect
                        + "\"remove\":[\""
                        + intToBase64(6)
                        + "\"]"
                        + ",\"update_encoder\": {}"
                        + "}";
        setupAndRunUpdateSignals(json1);

        List<DBProtectedSignal> expected1 =
                Arrays.asList(
                        generateSignal(1, 101),
                        generateSignal(2, 102),
                        generateSignal(3, 103),
                        generateSignal(4, 105),
                        generateSignal(5, 106));
        List<DBProtectedSignal> actual1 = mSignalsDao.getSignalsByBuyer(BUYER);
        assertSignalsUnorderedListEqualsExceptIdAndTime(expected1, actual1);

        String json2 =
                "{"
                        // Overwrite one of the previously put signals
                        + "\"put\":{\""
                        + intToBase64(1)
                        + "\":\""
                        + intToBase64(107)
                        + "\""
                        + "},"
                        // Append 3 signals, overwriting the previous appended one
                        + "\"append\":{\""
                        + intToBase64(3)
                        + "\":"
                        + "{\"values\": [\""
                        + intToBase64(108)
                        + "\",\""
                        + intToBase64(109)
                        + "\",\""
                        + intToBase64(110)
                        + "\"]"
                        + ", \"max_signals\": 3}"
                        + "},"
                        // Write will not occur since a signal is already present with that key
                        + "\"put_if_not_present\":{\""
                        + intToBase64(4)
                        + "\":\""
                        + intToBase64(111)
                        + "\""
                        + "},"
                        // Remove on of the previous signals added with put_if_not_present
                        + "\"remove\":[\""
                        + intToBase64(5)
                        + "\"]"
                        + ",\"update_encoder\": {}"
                        + "}";
        setupAndRunUpdateSignals(json2);

        List<DBProtectedSignal> expected2 =
                Arrays.asList(
                        generateSignal(1, 107),
                        generateSignal(2, 102),
                        generateSignal(3, 110),
                        generateSignal(3, 108),
                        generateSignal(3, 109),
                        generateSignal(4, 105));
        List<DBProtectedSignal> actual2 = mSignalsDao.getSignalsByBuyer(BUYER);
        assertSignalsUnorderedListEqualsExceptIdAndTime(expected2, actual2);
    }

    @Test
    public void testComplexJson() throws Exception {
        setupService(false);
        String json1 =
                "{"
                        // Put two signals
                        + "\"put\":{\""
                        + intToBase64(1)
                        + "\":\""
                        + intToBase64(101)
                        + "\",\""
                        + intToBase64(2)
                        + "\":\""
                        + intToBase64(102)
                        + "\""
                        + "},"
                        // Append one signal
                        + "\"append\":{\""
                        + intToBase64(3)
                        + "\":"
                        + "{\"values\": [\""
                        + intToBase64(103)
                        + "\"]"
                        + ", \"max_signals\": 3}"
                        + "},"
                        // Put two more signals using put_if_not_present
                        + "\"put_if_not_present\":{\""
                        + intToBase64(4)
                        + "\":\""
                        + intToBase64(105)
                        + "\",\""
                        + intToBase64(5)
                        + "\":\""
                        + intToBase64(106)
                        + "\""
                        + "}"
                        + ",\"update_encoder\": {}"
                        + "}";
        String json2 =
                "{"
                        // Overwrite one of the previously put signals
                        + "\"put\":{\""
                        + intToBase64(1)
                        + "\":\""
                        + intToBase64(107)
                        + "\""
                        + "},"
                        // Append 3 signals, overwriting the previous appended one
                        + "\"append\":{\""
                        + intToBase64(3)
                        + "\":"
                        + "{\"values\": [\""
                        + intToBase64(108)
                        + "\",\""
                        + intToBase64(109)
                        + "\",\""
                        + intToBase64(110)
                        + "\"]"
                        + ", \"max_signals\": 3}"
                        + "},"
                        // Write will not occur since a signal is already present with that key
                        + "\"put_if_not_present\":{\""
                        + intToBase64(4)
                        + "\":\""
                        + intToBase64(111)
                        + "\""
                        + "},"
                        // Remove on of the previous signals added with put_if_not_present
                        + "\"remove\":[\""
                        + intToBase64(5)
                        + "\"]"
                        + ",\"update_encoder\": {}"
                        + "}";
        Uri uri = mMockWebServerRule.uriForPath(SIGNALS_PATH);
        MockResponse response1 = new MockResponse().setBody(json1);
        MockResponse response2 = new MockResponse().setBody(json2);
        mMockWebServerRule.startMockWebServer(Arrays.asList(response1, response2));

        callForUri(uri);
        List<DBProtectedSignal> expected1 =
                Arrays.asList(
                        generateSignal(1, 101),
                        generateSignal(2, 102),
                        generateSignal(3, 103),
                        generateSignal(4, 105),
                        generateSignal(5, 106));
        List<DBProtectedSignal> actual1 = mSignalsDao.getSignalsByBuyer(BUYER);
        assertSignalsUnorderedListEqualsExceptIdAndTime(expected1, actual1);

        callForUri(uri);
        List<DBProtectedSignal> expected2 =
                Arrays.asList(
                        generateSignal(1, 107),
                        generateSignal(2, 102),
                        generateSignal(3, 110),
                        generateSignal(3, 108),
                        generateSignal(3, 109),
                        generateSignal(4, 105));
        List<DBProtectedSignal> actual2 = mSignalsDao.getSignalsByBuyer(BUYER);
        assertSignalsUnorderedListEqualsExceptIdAndTime(expected2, actual2);
    }

    @Test
    public void testOverwriteAppendWithPut() throws Exception {
        setupService(false);
        String json1 =
                "{"
                        // Append 3 signals to key 1
                        + "\"append\":{\""
                        + intToBase64(1)
                        + "\":"
                        + "{\"values\": [\""
                        + intToBase64(100)
                        + "\",\""
                        + intToBase64(101)
                        + "\",\""
                        + intToBase64(102)
                        + "\"]"
                        + ", \"max_signals\": 3}"
                        + "}}";

        String json2 =
                "{"
                        // Overwrite all the appended signals
                        + "\"put\":{\""
                        + intToBase64(1)
                        + "\":\""
                        + intToBase64(103)
                        + "\"}}";
        Uri uri = mMockWebServerRule.uriForPath(SIGNALS_PATH);
        MockResponse response1 = new MockResponse().setBody(json1);
        MockResponse response2 = new MockResponse().setBody(json2);
        mMockWebServerRule.startMockWebServer(Arrays.asList(response1, response2));

        callForUri(uri);
        List<DBProtectedSignal> expected1 =
                Arrays.asList(
                        generateSignal(1, 100), generateSignal(1, 101), generateSignal(1, 102));
        List<DBProtectedSignal> actual1 = mSignalsDao.getSignalsByBuyer(BUYER);
        assertSignalsUnorderedListEqualsExceptIdAndTime(expected1, actual1);

        callForUri(uri);
        List<DBProtectedSignal> expected2 = Arrays.asList(generateSignal(1, 103));
        List<DBProtectedSignal> actual2 = mSignalsDao.getSignalsByBuyer(BUYER);
        assertSignalsUnorderedListEqualsExceptIdAndTime(expected2, actual2);
    }

    @Test
    public void testAddToPutWithAppend() throws Exception {
        setupService(false);
        String json1 =
                "{"
                        // Overwrite all the appended signals
                        + "\"put\":{\""
                        + intToBase64(1)
                        + "\":\""
                        + intToBase64(100)
                        + "\"}}";
        String json2 =
                "{"
                        // Append 3 signals to key 1
                        + "\"append\":{\""
                        + intToBase64(1)
                        + "\":"
                        + "{\"values\": [\""
                        + intToBase64(101)
                        + "\"]"
                        + ", \"max_signals\": 2}"
                        + "}}";
        Uri uri = mMockWebServerRule.uriForPath(SIGNALS_PATH);
        MockResponse response1 = new MockResponse().setBody(json1);
        MockResponse response2 = new MockResponse().setBody(json2);
        mMockWebServerRule.startMockWebServer(Arrays.asList(response1, response2));

        callForUri(uri);
        List<DBProtectedSignal> expected1 = Arrays.asList(generateSignal(1, 100));
        List<DBProtectedSignal> actual1 = mSignalsDao.getSignalsByBuyer(BUYER);
        assertSignalsUnorderedListEqualsExceptIdAndTime(expected1, actual1);

        callForUri(uri);
        List<DBProtectedSignal> expected2 =
                Arrays.asList(generateSignal(1, 100), generateSignal(1, 101));
        List<DBProtectedSignal> actual2 = mSignalsDao.getSignalsByBuyer(BUYER);
        assertSignalsUnorderedListEqualsExceptIdAndTime(expected2, actual2);
    }

    @Test
    public void testBadAppend() throws Exception {
        setupService(false);
        String json =
                "{"
                        // Put two signals
                        + "\"put\":{\""
                        + intToBase64(1)
                        + "\":\""
                        + intToBase64(101)
                        + "\",\""
                        + intToBase64(2)
                        + "\":\""
                        + intToBase64(102)
                        + "\""
                        + "},"
                        // Try to append two signals with a limit of one
                        + "\"append\":{\""
                        + intToBase64(3)
                        + "\":"
                        + "{\"values\": [\""
                        + intToBase64(103)
                        + "\",\""
                        + intToBase64(106)
                        + "\"]"
                        + ", \"max_signals\": 1}"
                        + "}}";
        Uri uri = mMockWebServerRule.uriForPath(SIGNALS_PATH);
        MockResponse response = new MockResponse().setBody(json);
        mMockWebServerRule.startMockWebServer(Arrays.asList(response));

        UpdateSignalsInput input =
                new UpdateSignalsInput.Builder(uri, CommonFixture.TEST_PACKAGE_NAME).build();
        CallbackForTesting callback = new CallbackForTesting();
        mService.updateSignals(input, callback);
        assertTrue(callback.mFailureLatch.await(WAIT_TIME_SECONDS, TimeUnit.SECONDS));
        FledgeErrorResponse cause = callback.mFailureCause;
        assertEquals(AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, cause.getStatusCode());
        assertEquals(
                String.format(TOO_MANY_SIGNALS_ERROR, 2, 1),
                callback.mFailureCause.getErrorMessage());

        assertTrue(mSignalsDao.getSignalsByBuyer(BUYER).isEmpty());
    }

    @Test
    public void testCollision() throws Exception {
        setupService(false);
        String json =
                "{"
                        // Put two signals
                        + "\"put\":{\""
                        + intToBase64(1)
                        + "\":\""
                        + intToBase64(101)
                        + "\",\""
                        + intToBase64(3)
                        + "\":\""
                        + intToBase64(102)
                        + "\""
                        + "},"
                        // Try to append two signals with a limit of one
                        + "\"append\":{\""
                        + intToBase64(3)
                        + "\":"
                        + "{\"values\": [\""
                        + intToBase64(103)
                        + "\",\""
                        + intToBase64(106)
                        + "\"]"
                        + ", \"max_signals\": 3}"
                        + "}}";
        Uri uri = mMockWebServerRule.uriForPath(SIGNALS_PATH);
        MockResponse response = new MockResponse().setBody(json);
        mMockWebServerRule.startMockWebServer(Arrays.asList(response));

        UpdateSignalsInput input =
                new UpdateSignalsInput.Builder(uri, CommonFixture.TEST_PACKAGE_NAME).build();
        CallbackForTesting callback = new CallbackForTesting();
        mService.updateSignals(input, callback);
        assertTrue(callback.mFailureLatch.await(WAIT_TIME_SECONDS, TimeUnit.SECONDS));
        FledgeErrorResponse cause = callback.mFailureCause;
        assertEquals(AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, cause.getStatusCode());
        assertEquals(COLLISION_ERROR, callback.mFailureCause.getErrorMessage());

        assertTrue(mSignalsDao.getSignalsByBuyer(BUYER).isEmpty());
    }

    @Test
    public void testFailure() throws Exception {
        setupService(false);
        String badJson = "{";
        Uri uri = mMockWebServerRule.uriForPath(SIGNALS_PATH);
        MockResponse response1 = new MockResponse().setBody(badJson);
        mMockWebServerRule.startMockWebServer(Arrays.asList(response1));
        UpdateSignalsInput input =
                new UpdateSignalsInput.Builder(uri, CommonFixture.TEST_PACKAGE_NAME).build();
        CallbackForTesting callback = new CallbackForTesting();
        mService.updateSignals(input, callback);
        assertTrue(callback.mFailureLatch.await(WAIT_TIME_SECONDS, TimeUnit.SECONDS));
        FledgeErrorResponse cause = callback.mFailureCause;
        assertEquals(AdServicesStatusUtils.STATUS_INVALID_ARGUMENT, cause.getStatusCode());
        assertEquals(CONVERSION_ERROR_MSG, callback.mFailureCause.getErrorMessage());
    }

    @Test
    public void testNoConsentCallerPackageHasNoConsent() throws Exception {
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(any()))
                .thenReturn(true);
        when(mConsentManagerMock.isPasFledgeConsentGiven()).thenReturn(true);
        baseTestNoConsent();
    }

    @Test
    public void testNoConsentUserNotSeenNotification() throws Exception {
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(any()))
                .thenReturn(false);
        when(mConsentManagerMock.isPasFledgeConsentGiven()).thenReturn(false);
        baseTestNoConsent();
    }

    private void baseTestNoConsent() throws Exception {
        setupService(false);
        String json =
                "{" + "\"put\":{\"" + BASE64_KEY_1 + "\":\"" + BASE64_VALUE_1 + "\"" + "}" + "}";
        Uri uri = mMockWebServerRule.uriForPath(SIGNALS_PATH);
        MockResponse response1 = new MockResponse().setBody(json);
        mMockWebServerRule.startMockWebServer(Arrays.asList(response1));
        UpdateSignalsInput input =
                new UpdateSignalsInput.Builder(uri, CommonFixture.TEST_PACKAGE_NAME).build();
        CallbackForTesting callback = new CallbackForTesting();
        mService.updateSignals(input, callback);
        assertTrue(callback.mSuccessLatch.await(WAIT_TIME_SECONDS, TimeUnit.SECONDS));
        assertEquals(Collections.emptyList(), mSignalsDao.getSignalsByBuyer(BUYER));
    }

    private void callForUri(Uri uri) throws Exception {
        UpdateSignalsInput input =
                new UpdateSignalsInput.Builder(uri, CommonFixture.TEST_PACKAGE_NAME).build();
        CallbackForTesting callback = new CallbackForTesting();
        mService.updateSignals(input, callback);
        assertTrue(callback.mSuccessLatch.await(WAIT_TIME_SECONDS, TimeUnit.SECONDS));
    }

    private void setupAndRunUpdateSignals(String json) throws Exception {
        ImmutableMap<String, String> requestProperties =
                ImmutableMap.of(PACKAGE_NAME_HEADER, CommonFixture.TEST_PACKAGE_NAME);
        AdServicesHttpClientRequest expected =
                AdServicesHttpClientRequest.builder()
                        .setRequestProperties(requestProperties)
                        .setUri(URI)
                        .setDevContext(DevContext.createForDevOptionsDisabled())
                        .build();
        AdServicesHttpClientResponse response =
                AdServicesHttpClientResponse.builder().setResponseBody(json).build();
        SettableFuture<AdServicesHttpClientResponse> returnValue = SettableFuture.create();
        returnValue.set(response);
        when(mAdServicesHttpsClientMock.fetchPayload(expected)).thenReturn(returnValue);
        callForUri(URI);
    }

    static class CallbackForTesting implements UpdateSignalsCallback {
        CountDownLatch mSuccessLatch = new CountDownLatch(1);
        CountDownLatch mFailureLatch = new CountDownLatch(1);
        FledgeErrorResponse mFailureCause =
                new FledgeErrorResponse.Builder()
                        .setStatusCode(123456)
                        .setErrorMessage("INVALID")
                        .build();

        @Override
        public void onSuccess() throws RemoteException {
            mSuccessLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mFailureCause = fledgeErrorResponse;
            mFailureLatch.countDown();
        }

        @Override
        public IBinder asBinder() {
            throw new RuntimeException("Should not be called.");
        }
    }

    private DBProtectedSignal generateSignal(int key, int value) {
        return DBProtectedSignal.builder()
                .setBuyer(BUYER)
                .setKey(intToBytes(key))
                .setValue(intToBytes(value))
                .setCreationTime(Instant.now())
                .setPackageName(CommonFixture.TEST_PACKAGE_NAME)
                .build();
    }
}
