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

import static com.android.adservices.service.signals.SignalsFixture.assertSignalsUnorderedListEqualsExceptIdAndTime;
import static com.android.adservices.service.signals.SignalsFixture.intToBase64;
import static com.android.adservices.service.signals.SignalsFixture.intToBytes;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.http.MockWebServerRule;
import android.adservices.signals.UpdateSignalsInput;
import android.content.Context;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.common.SupportedByConditionRule;
import com.android.adservices.common.WebViewSupportUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.data.signals.DBProtectedSignal;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.EncoderEndpointsDao;
import com.android.adservices.data.signals.EncoderLogicHandler;
import com.android.adservices.data.signals.EncoderLogicMetadataDao;
import com.android.adservices.data.signals.EncoderPersistenceDao;
import com.android.adservices.data.signals.ProtectedSignalsDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.adselection.AdCounterKeyCopierNoOpImpl;
import com.android.adservices.service.adselection.AdSelectionScriptEngine;
import com.android.adservices.service.adselection.DebugReportingScriptDisabledStrategy;
import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.NoOpRetryStrategyImpl;
import com.android.adservices.service.common.ProtectedSignalsServiceFilter;
import com.android.adservices.service.common.RetryStrategy;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.js.IsolateSettings;
import com.android.adservices.service.signals.evict.SignalEvictionController;
import com.android.adservices.service.signals.updateprocessors.UpdateEncoderEventHandler;
import com.android.adservices.service.signals.updateprocessors.UpdateProcessorSelector;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.RecordedRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SignalsEncodingE2ETest {
    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("localhost");
    private static final long WAIT_TIME_SECONDS = 10L;

    private static final String SIGNALS_PATH = "/signals";
    private static final String ENCODER_PATH = "/encoder";

    private MockitoSession mStaticMockSession = null;

    @Spy private final Context mContextSpy = ApplicationProvider.getApplicationContext();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    // Every test in this class requires that the JS Sandbox be available. The JS Sandbox
    // availability depends on an external component (the system webview) being higher than a
    // certain minimum version.
    @Rule(order = 1)
    public final SupportedByConditionRule webViewSupportsJSSandbox =
            WebViewSupportUtil.createJSSandboxAvailableRule(
                    ApplicationProvider.getApplicationContext());

    @Rule(order = 2)
    public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();

    private final AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private AppImportanceFilter mAppImportanceFilterMock;
    @Mock private Throttler mMockThrottler;
    @Mock private DevContextFilter mDevContextFilterMock;

    private FlagsWithEnabledPeriodicEncoding mFlagsWithProtectedSignalsAndEncodingEnabled =
            new FlagsWithEnabledPeriodicEncoding();

    @Spy
    FledgeAllowListsFilter mFledgeAllowListsFilterSpy =
            new FledgeAllowListsFilter(
                    mFlagsWithProtectedSignalsAndEncodingEnabled, mAdServicesLoggerMock);

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
    private EncodedPayloadDao mEncodedPayloadDao;
    private SignalsProviderImpl mSignalStorageManager;
    private PeriodicEncodingJobWorker mPeriodicEncodingJobWorker;
    private AdSelectionScriptEngine mAdSelectionScriptEngine;

    private AdTechUriValidator mAdtechUriValidator;
    private FledgeAuthorizationFilter mFledgeAuthorizationFilter;
    private ProtectedSignalsServiceFilter mProtectedSignalsServiceFilter;
    private EncoderLogicHandler mEncoderLogicHandler;
    private EncoderPersistenceDao mEncoderPersistenceDao;
    private ListeningExecutorService mLightweightExecutorService;
    private ListeningExecutorService mBackgroundExecutorService;
    private AdServicesHttpsClient mAdServicesHttpsClient;

    @Before
    public void setup() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(PeriodicEncodingJobService.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();
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
        mEncodedPayloadDao =
                Room.inMemoryDatabaseBuilder(mContextSpy, ProtectedSignalsDatabase.class)
                        .build()
                        .getEncodedPayloadDao();
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mUpdateProcessorSelector = new UpdateProcessorSelector();
        mEncoderPersistenceDao = EncoderPersistenceDao.getInstance(mContextSpy);

        mAdServicesHttpsClient =
                new AdServicesHttpsClient(mBackgroundExecutorService, 2000, 2000, 10000);
        mEncoderLogicHandler =
                new EncoderLogicHandler(
                        mEncoderPersistenceDao,
                        mEncoderEndpointsDao,
                        mEncoderLogicMetadataDao,
                        mSignalsDao,
                        mAdServicesHttpsClient,
                        mBackgroundExecutorService);
        mUpdateEncoderEventHandler =
                new UpdateEncoderEventHandler(mEncoderEndpointsDao, mEncoderLogicHandler);
        mSignalEvictionController = new SignalEvictionController(ImmutableList.of(), 0, 0);
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
                                        mFlagsWithProtectedSignalsAndEncodingEnabled),
                                mAdServicesLoggerMock));
        mProtectedSignalsServiceFilter =
                new ProtectedSignalsServiceFilter(
                        mContextSpy,
                        mConsentManagerMock,
                        mFlagsWithProtectedSignalsAndEncodingEnabled,
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

        mUpdatesDownloader =
                new UpdatesDownloader(mLightweightExecutorService, mAdServicesHttpsClient);

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
                        mFlagsWithProtectedSignalsAndEncodingEnabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mProtectedSignalsServiceFilter);

        mSignalStorageManager = new SignalsProviderImpl(mSignalsDao);
        RetryStrategy retryStrategy = new NoOpRetryStrategyImpl();
        mAdSelectionScriptEngine =
                new AdSelectionScriptEngine(
                        mContextSpy,
                        () ->
                                IsolateSettings.forMaxHeapSizeEnforcementDisabled()
                                        .getEnforceMaxHeapSizeFeature(),
                        () ->
                                IsolateSettings.forMaxHeapSizeEnforcementDisabled()
                                        .getMaxHeapSizeBytes(),
                        new AdCounterKeyCopierNoOpImpl(),
                        new DebugReportingScriptDisabledStrategy(),
                        false,
                        retryStrategy);

        mPeriodicEncodingJobWorker =
                new PeriodicEncodingJobWorker(
                        mEncoderLogicHandler,
                        mEncoderLogicMetadataDao,
                        mEncodedPayloadDao,
                        mSignalStorageManager,
                        mSignalsDao,
                        mAdSelectionScriptEngine,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mDevContextFilterMock,
                        mFlagsWithProtectedSignalsAndEncodingEnabled);

        doNothing()
                .when(
                        () ->
                                PeriodicEncodingJobService.scheduleIfNeeded(
                                        any(), any(), anyBoolean()));
    }

    @After
    public void teardown() {
        if (mEncoderPersistenceDao != null) {
            mEncoderPersistenceDao.deleteAllEncoders();
        }

        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testSignalsEncoding_Success() throws Exception {
        String encodeSignalsJS =
                "\nfunction encodeSignals(signals, maxSize) {\n"
                        + "   return {'status': 0, 'results': new Uint8Array([signals.size])};\n"
                        + "}\n";
        Uri encoderUri = mMockWebServerRule.uriForPath(ENCODER_PATH);
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
                        // Add an encoder registration event
                        + "\"update_encoder\" : {\n"
                        + "\t\"action\" : \"REGISTER\",\n"
                        + "\t\"endpoint\" : \""
                        + encoderUri.toString()
                        + "\"\n"
                        + "  }"
                        + "}";
        MockResponse signalsResponse = new MockResponse().setBody(json);
        MockResponse encoderResponse = new MockResponse().setBody(encodeSignalsJS);

        // Wire signals and encoder endpoint to respective responses
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request)
                            throws InterruptedException {
                        switch (request.getPath()) {
                            case SIGNALS_PATH:
                                return signalsResponse;
                            case ENCODER_PATH:
                                return encoderResponse;
                            default:
                                return new MockResponse().setResponseCode(404);
                        }
                    }
                });
        Uri uri = mMockWebServerRule.uriForPath(SIGNALS_PATH);

        CountDownLatch updateEventLatch = new CountDownLatch(1);
        SignalsEncodingE2ETest.EncoderUpdateEventTestObserver observer =
                new EncoderUpdateEventTestObserver(updateEventLatch, BUYER, true);
        mUpdateEncoderEventHandler.addObserver(observer);

        callForUri(uri);
        List<DBProtectedSignal> expected =
                Arrays.asList(
                        generateSignal(1, 101),
                        generateSignal(2, 102),
                        generateSignal(3, 103),
                        generateSignal(4, 105),
                        generateSignal(5, 106));
        List<DBProtectedSignal> actual = mSignalsDao.getSignalsByBuyer(BUYER);
        assertSignalsUnorderedListEqualsExceptIdAndTime(expected, actual);

        // Verify that encoder gets registered, downloaded and persisted
        assertEquals(
                "Encoder endpoint should have been registered",
                encoderUri,
                mEncoderEndpointsDao.getEndpoint(BUYER).getDownloadUri());

        // Wait for the update event to be completed
        updateEventLatch.await(WAIT_TIME_SECONDS, TimeUnit.SECONDS);
        assertEquals("Latch timed out but did not countdown", 0, updateEventLatch.getCount());

        assertEquals(
                "Downloaded encoder logic should have been same as one wired with encoder uri",
                encodeSignalsJS,
                mEncoderPersistenceDao.getEncoder(BUYER));

        // Validate that the periodic job for encoding would have been scheduled
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(1));

        // Manually trigger encoding job worker to validate encoding gets done
        mPeriodicEncodingJobWorker.encodeProtectedSignals().get(5, TimeUnit.SECONDS);

        // Validate that the encoded results are correctly persisted
        byte[] payload = mEncodedPayloadDao.getEncodedPayload(BUYER).getEncodedPayload();
        assertArrayEquals(
                "Encoding JS should have returned size of signals as result",
                new byte[] {(byte) expected.size()},
                payload);
    }

    /**
     * We test the behavior of when an encoder gets updated. We can also reverse map which encoder
     * was used in encoding just by looking at the encoded payload output.
     */
    @Test
    public void testSecondUpdateEncoderDoesNotDownloadEncodingLogic() throws Exception {
        String encodeSignalsJS1 =
                "\nfunction encodeSignals(signals, maxSize) {\n"
                        + "    return {'status' : 0, 'results' : new Uint8Array( [0x01] ) };\n"
                        + "}\n";
        Uri encoderUri1 = mMockWebServerRule.uriForPath(ENCODER_PATH + "1");
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
                        // Add an encoder registration event
                        + "\"update_encoder\" : {\n"
                        + "\t\"action\" : \"REGISTER\",\n"
                        + "\t\"endpoint\" : \""
                        + encoderUri1.toString()
                        + "\"\n"
                        + "  }"
                        + "}";
        MockResponse signalsResponse1 = new MockResponse().setBody(json1);
        MockResponse encoderResponse1 = new MockResponse().setBody(encodeSignalsJS1);
        Uri uri1 = mMockWebServerRule.uriForPath(SIGNALS_PATH + "1");

        String encodeSignalsJS2 =
                "\nfunction encodeSignals(signals, maxSize) {\n"
                        + "    return {'status' : 0, 'results' : new Uint8Array( [0x02] )};\n"
                        + "}\n";
        Uri encoderUri2 = mMockWebServerRule.uriForPath(ENCODER_PATH + "2");
        String json2 =
                "{"
                        // Add another encoder registration event
                        + "\"update_encoder\" : {\n"
                        + "\t\"action\" : \"REGISTER\",\n"
                        + "\t\"endpoint\" : \""
                        + encoderUri2.toString()
                        + "\"\n"
                        + "  }"
                        + "}";
        MockResponse signalsResponse2 = new MockResponse().setBody(json2);
        MockResponse encoderResponse2 = new MockResponse().setBody(encodeSignalsJS2);

        // Wire signals and encoder endpoint to respective responses
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request)
                            throws InterruptedException {
                        switch (request.getPath()) {
                            case SIGNALS_PATH + "1":
                                return signalsResponse1;
                            case SIGNALS_PATH + "2":
                                return signalsResponse2;
                            case ENCODER_PATH + "1":
                                return encoderResponse1;
                            case ENCODER_PATH + "2":
                                return encoderResponse2;
                            default:
                                return new MockResponse().setResponseCode(404);
                        }
                    }
                });
        Uri uri2 = mMockWebServerRule.uriForPath(SIGNALS_PATH + "2");

        CountDownLatch updateEventLatch1 = new CountDownLatch(1);
        SignalsEncodingE2ETest.EncoderUpdateEventTestObserver observer1 =
                new EncoderUpdateEventTestObserver(updateEventLatch1, BUYER, true);
        mUpdateEncoderEventHandler.addObserver(observer1);

        callForUri(uri1);
        List<DBProtectedSignal> expected =
                Arrays.asList(generateSignal(1, 101), generateSignal(2, 102));
        List<DBProtectedSignal> actual = mSignalsDao.getSignalsByBuyer(BUYER);
        assertSignalsUnorderedListEqualsExceptIdAndTime(expected, actual);

        // Wait for the update event to be completed
        updateEventLatch1.await(WAIT_TIME_SECONDS, TimeUnit.SECONDS);
        assertEquals("Latch timed out but did not countdown", 0, updateEventLatch1.getCount());

        // Manually trigger encoding job worker to validate encoding gets done
        mPeriodicEncodingJobWorker.encodeProtectedSignals().get(5, TimeUnit.SECONDS);

        // Validate that the encoded results are correctly persisted
        byte[] payload1A = mEncodedPayloadDao.getEncodedPayload(BUYER).getEncodedPayload();
        assertArrayEquals(new byte[] {0x01}, payload1A);

        // We make second call with new encoder logic, but encoder logic will not be downloaded
        callForUri(uri2);

        // Manually trigger encoding job worker to check the encoding logic is still the same
        mPeriodicEncodingJobWorker.encodeProtectedSignals().get(5, TimeUnit.SECONDS);

        // Validate that the encoded results are correctly persisted
        byte[] payload1B = mEncodedPayloadDao.getEncodedPayload(BUYER).getEncodedPayload();
        assertArrayEquals(
                "Encoder should have still remained the same", new byte[] {0x01}, payload1B);

        CountDownLatch updateEventLatch2 = new CountDownLatch(1);
        SignalsEncodingE2ETest.EncoderUpdateEventTestObserver observer2 =
                new EncoderUpdateEventTestObserver(updateEventLatch2, BUYER, true);
        mUpdateEncoderEventHandler.addObserver(observer2);

        // We make second call with new encoder logic, but we clear any registered encoders
        mEncoderEndpointsDao.deleteEncoderEndpoint(BUYER);
        // This should trigger a new download for new encoder logic
        callForUri(uri2);

        // Wait for the update event to be completed
        updateEventLatch2.await(WAIT_TIME_SECONDS, TimeUnit.SECONDS);
        assertEquals("Latch timed out but did not countdown", 0, updateEventLatch2.getCount());

        // Manually trigger encoding job worker to check the encoding logic is updated
        mPeriodicEncodingJobWorker.encodeProtectedSignals().get(5, TimeUnit.SECONDS);

        // Validate that the encoded results are correctly persisted, and we used the second encoder
        byte[] payload2 = mEncodedPayloadDao.getEncodedPayload(BUYER).getEncodedPayload();
        assertArrayEquals(new byte[] {0x02}, payload2);
    }

    @Test
    public void testPeriodicEncodingUpdatesEncoders_Success() throws Exception {
        String encodeSignalsJS =
                "\nfunction encodeSignals(signals, maxSize) {\n"
                        + "  return {'status' : 0, 'results' : new Uint8Array([signals.size])};\n"
                        + "}\n";
        Uri encoderUri = mMockWebServerRule.uriForPath(ENCODER_PATH);
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
                        // Add an encoder registration event
                        + "\"update_encoder\" : {\n"
                        + "\t\"action\" : \"REGISTER\",\n"
                        + "\t\"endpoint\" : \""
                        + encoderUri.toString()
                        + "\"\n"
                        + "  }"
                        + "}";
        MockResponse signalsResponse = new MockResponse().setBody(json);
        MockResponse encoderResponse = new MockResponse().setBody(encodeSignalsJS);

        CountDownLatch encoderLogicDownloadedLatch = new CountDownLatch(2);

        // Wire signals and encoder endpoint to respective responses
        mMockWebServerRule.startMockWebServer(
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request)
                            throws InterruptedException {
                        switch (request.getPath()) {
                            case SIGNALS_PATH:
                                return signalsResponse;
                            case ENCODER_PATH:
                                encoderLogicDownloadedLatch.countDown();
                                return encoderResponse;
                            default:
                                return new MockResponse().setResponseCode(404);
                        }
                    }
                });
        Uri uri = mMockWebServerRule.uriForPath(SIGNALS_PATH);

        CountDownLatch updateEventLatch = new CountDownLatch(1);
        SignalsEncodingE2ETest.EncoderUpdateEventTestObserver observer =
                new EncoderUpdateEventTestObserver(updateEventLatch, BUYER, true);
        mUpdateEncoderEventHandler.addObserver(observer);

        callForUri(uri);
        List<DBProtectedSignal> expected =
                Arrays.asList(generateSignal(1, 101), generateSignal(2, 102));
        List<DBProtectedSignal> actual = mSignalsDao.getSignalsByBuyer(BUYER);
        assertSignalsUnorderedListEqualsExceptIdAndTime(expected, actual);

        // Verify that encoder gets registered, downloaded and persisted
        assertEquals(
                "Encoder endpoint should have been registered",
                encoderUri,
                mEncoderEndpointsDao.getEndpoint(BUYER).getDownloadUri());

        // Wait for the update event to be completed
        updateEventLatch.await(WAIT_TIME_SECONDS, TimeUnit.SECONDS);
        assertEquals(
                "Download Encoder should have been counted down once",
                1,
                encoderLogicDownloadedLatch.getCount());
        assertEquals("Latch timed out but did not countdown", 0, updateEventLatch.getCount());

        assertEquals(
                "Downloaded encoder logic should have been same as one wired with encoder uri",
                encodeSignalsJS,
                mEncoderPersistenceDao.getEncoder(BUYER));

        // Validate that the periodic job for encoding would have been scheduled
        verify(
                () -> PeriodicEncodingJobService.scheduleIfNeeded(any(), any(), eq(false)),
                times(1));

        FlagsWithEnabledPeriodicEncoding flagsWithLargeUpdateWindow =
                new FlagsWithEnabledPeriodicEncoding() {
                    @Override
                    public long getProtectedSignalsEncoderRefreshWindowSeconds() {
                        return 20L * 24L * 60L * 60L; // 20 days
                    }
                };

        PeriodicEncodingJobWorker jobWorkerWithLargeTimeWindow =
                new PeriodicEncodingJobWorker(
                        mEncoderLogicHandler,
                        mEncoderLogicMetadataDao,
                        mEncodedPayloadDao,
                        mSignalStorageManager,
                        mSignalsDao,
                        mAdSelectionScriptEngine,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mDevContextFilterMock,
                        flagsWithLargeUpdateWindow);

        // Manually trigger encoding job worker to validate encoding gets done
        jobWorkerWithLargeTimeWindow.encodeProtectedSignals().get(5, TimeUnit.SECONDS);

        // Validate that the encoded results are correctly persisted
        DBEncodedPayload firstEncodingRun = mEncodedPayloadDao.getEncodedPayload(BUYER);
        byte[] payload = firstEncodingRun.getEncodedPayload();
        assertArrayEquals(
                "Encoding JS should have returned size of signals as result",
                new byte[] {(byte) expected.size()},
                payload);
        assertEquals(
                "Encoder should not have been downloaded again",
                1,
                encoderLogicDownloadedLatch.getCount());

        // We trigger the periodic job worker with flags set to tiny update window
        FlagsWithEnabledPeriodicEncoding flagsWithTinyUpdateWindow =
                new FlagsWithEnabledPeriodicEncoding() {
                    @Override
                    public long getProtectedSignalsEncoderRefreshWindowSeconds() {
                        return 0L;
                    }
                };
        PeriodicEncodingJobWorker jobWorkerWithTinyTimeWindow =
                new PeriodicEncodingJobWorker(
                        mEncoderLogicHandler,
                        mEncoderLogicMetadataDao,
                        mEncodedPayloadDao,
                        mSignalStorageManager,
                        mSignalsDao,
                        mAdSelectionScriptEngine,
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mDevContextFilterMock,
                        flagsWithTinyUpdateWindow);

        // Manually trigger encoding job worker to validate encoding gets done
        jobWorkerWithTinyTimeWindow.encodeProtectedSignals().get(5, TimeUnit.SECONDS);

        // Validate that the encoded results are correctly persisted
        DBEncodedPayload secondEncodingRun = mEncodedPayloadDao.getEncodedPayload(BUYER);
        byte[] payload2 = secondEncodingRun.getEncodedPayload();
        assertArrayEquals(
                "Encoding JS should have returned size of signals as result",
                new byte[] {(byte) expected.size()},
                payload2);
        // The second run should skip based on the logic that we will skip encoding for unchanged
        // buyer.
        assertEquals(secondEncodingRun.getCreationTime(), firstEncodingRun.getCreationTime());

        encoderLogicDownloadedLatch.await(5, TimeUnit.SECONDS);
        assertEquals(
                "Encoder should have been downloaded second time",
                0,
                encoderLogicDownloadedLatch.getCount());
    }

    private void callForUri(Uri uri) throws Exception {
        UpdateSignalsInput input =
                new UpdateSignalsInput.Builder(uri, CommonFixture.TEST_PACKAGE_NAME).build();
        SignalsIntakeE2ETest.CallbackForTesting callback =
                new SignalsIntakeE2ETest.CallbackForTesting();
        mService.updateSignals(input, callback);
        callback.mSuccessLatch.await(WAIT_TIME_SECONDS, TimeUnit.SECONDS);
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

    static class EncoderUpdateEventTestObserver implements UpdateEncoderEventHandler.Observer {
        CountDownLatch mCountDownLatch;
        AdTechIdentifier mBuyer;
        boolean mResult;

        EncoderUpdateEventTestObserver(
                CountDownLatch latch, AdTechIdentifier buyer, boolean result) {
            mCountDownLatch = latch;
            mBuyer = buyer;
            mResult = result;
        }

        @Override
        public void update(AdTechIdentifier buyer, String eventType, FluentFuture<?> event) {
            try {
                assertEquals("Encoder update event buyer mismatch", mBuyer, buyer);
                assertEquals(
                        "Encoder update event status mismatch",
                        mResult,
                        ((FluentFuture<Boolean>) event).get(WAIT_TIME_SECONDS, TimeUnit.SECONDS));
                mCountDownLatch.countDown();
            } catch (Exception e) {
                throw new IllegalStateException("Encoder update event failed");
            }
        }
    }

    private static class FlagsWithEnabledPeriodicEncoding implements Flags {
        @Override
        public boolean getGaUxFeatureEnabled() {
            return true;
        }

        @Override
        public boolean getProtectedSignalsPeriodicEncodingEnabled() {
            return true;
        }

        @Override
        public boolean getBackgroundJobsLoggingKillSwitch() {
            return false;
        }

        @Override
        public boolean getProtectedSignalsEnabled() {
            return true;
        }

        @Override
        public boolean getGlobalKillSwitch() {
            return false;
        }

        @Override
        public boolean getDisableFledgeEnrollmentCheck() {
            return true;
        }
    }
}
