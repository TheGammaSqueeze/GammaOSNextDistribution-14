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

package android.adservices.adselection;

import static android.adservices.adselection.GetAdSelectionDataResponseFixture.getAdSelectionDataResponseWithAssetFileDescriptor;
import static android.adservices.adselection.GetAdSelectionDataResponseFixture.getAdSelectionDataResponseWithByteArray;
import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER;
import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;
import static android.adservices.common.CommonFixture.doSleep;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;

import android.adservices.adid.AdId;
import android.adservices.adid.AdIdCompatibleManager;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesOutcomeReceiver;
import android.adservices.common.CallerMetadata;
import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.concurrency.AdServicesExecutors;

import com.google.common.base.Strings;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.security.SecureRandom;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Unit tests for {@link AdSelectionManager} */
public class AdSelectionManagerTest {
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();

    // AdId constants
    private static final String AD_ID = "35a4ac90-e4dc-4fe7-bbc6-95e804aa7dbc";

    // reportEvent constants
    private static final Executor CALLBACK_EXECUTOR = Executors.newCachedThreadPool();
    private static final ExecutorService BLOCKING_EXECUTOR =
            AdServicesExecutors.getBlockingExecutor();
    private static final long AD_SELECTION_ID = 1234L;
    private static final String EVENT_KEY = "click";
    private static final String CALLER_PACKAGE_NAME = TEST_PACKAGE_NAME;
    private static final int REPORTING_DESTINATIONS =
            FLAG_REPORTING_DESTINATION_SELLER | FLAG_REPORTING_DESTINATION_BUYER;
    private String mEventData;
    private ReportEventRequest mReportEventRequest;

    private static final long SLEEP_TIME_MS = 200;
    private static final int TYPICAL_PAYLOAD_SIZE_BYTES = 1024; // 1kb
    private static final int EXCESSIVE_PAYLOAD_SIZE_BYTES =
            TYPICAL_PAYLOAD_SIZE_BYTES * 2 * 1024; // 2Mb

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() throws Exception {
        mEventData = new JSONObject().put("key", "value").toString();
        mReportEventRequest =
                new ReportEventRequest.Builder(
                                AD_SELECTION_ID, EVENT_KEY, mEventData, REPORTING_DESTINATIONS)
                        .build();
    }

    @Test
    public void testAdSelectionManagerCtor_TPlus() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU);
        assertThat(AdSelectionManager.get(CONTEXT)).isNotNull();
        assertThat(CONTEXT.getSystemService(AdSelectionManager.class)).isNotNull();
    }

    @Test
    public void testAdSelectionManagerCtor_SMinus() {
        Assume.assumeTrue(Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU);
        assertThat(AdSelectionManager.get(CONTEXT)).isNotNull();
        assertThat(CONTEXT.getSystemService(AdSelectionManager.class)).isNull();
    }

    @Test
    public void testAdSelectionManager_reportEvent_adIdEnabled()
            throws JSONException, RemoteException {
        // TODO(b/296852054): Remove assumption once AdServicesOutcomeReceiver is used by FLEDGE.
        Assume.assumeTrue(Build.VERSION.SDK_INT > Build.VERSION_CODES.R);

        // Initialize manager with mocks
        MockAdIdManager mockAdIdManager = new MockAdIdManager(CONTEXT);
        MockServiceToReportEvent mockServiceToReportEvent = new MockServiceToReportEvent();
        AdSelectionManager mAdSelectionManager =
                AdSelectionManager.get(CONTEXT, mockAdIdManager, mockServiceToReportEvent);

        // Set expected outcome of AdIdManager#getAdId
        mockAdIdManager.setResult(new AdId(AD_ID, true));

        mAdSelectionManager.reportEvent(
                mReportEventRequest, CALLBACK_EXECUTOR, new MockOutcomeReceiver<>());

        // Assert values passed to the service are as expected
        ReportInteractionInput input = mockServiceToReportEvent.mInput;
        assertThat(input.getAdSelectionId()).isEqualTo(AD_SELECTION_ID);
        assertThat(input.getCallerPackageName()).isEqualTo(CALLER_PACKAGE_NAME);
        assertThat(input.getInteractionKey()).isEqualTo(EVENT_KEY);
        assertThat(input.getInteractionData()).isEqualTo(mEventData);
        assertThat(input.getReportingDestinations()).isEqualTo(REPORTING_DESTINATIONS);
        assertThat(input.getInputEvent()).isNull();
        assertThat(input.getAdId()).isEqualTo(AD_ID);
        assertThat(input.getCallerSdkName()).isEqualTo("");
    }

    @Test
    public void testAdSelectionManager_reportEvent_adIdZeroOut()
            throws JSONException, RemoteException {
        // TODO(b/296852054): Remove assumption once AdServicesOutcomeReceiver is used by FLEDGE.
        Assume.assumeTrue(Build.VERSION.SDK_INT > Build.VERSION_CODES.R);

        // Initialize manager with mocks
        MockAdIdManager mockAdIdManager = new MockAdIdManager(CONTEXT);
        MockServiceToReportEvent mockServiceToReportEvent = new MockServiceToReportEvent();
        AdSelectionManager mAdSelectionManager =
                AdSelectionManager.get(CONTEXT, mockAdIdManager, mockServiceToReportEvent);

        // Set expected outcome of AdIdManager#getAdId
        mockAdIdManager.setResult(new AdId(AdId.ZERO_OUT, true));

        mAdSelectionManager.reportEvent(
                mReportEventRequest, CALLBACK_EXECUTOR, new MockOutcomeReceiver<>());

        // Assert values passed to the service are as expected
        ReportInteractionInput input = mockServiceToReportEvent.mInput;
        assertThat(input.getAdSelectionId()).isEqualTo(AD_SELECTION_ID);
        assertThat(input.getCallerPackageName()).isEqualTo(CALLER_PACKAGE_NAME);
        assertThat(input.getInteractionKey()).isEqualTo(EVENT_KEY);
        assertThat(input.getInteractionData()).isEqualTo(mEventData);
        assertThat(input.getReportingDestinations()).isEqualTo(REPORTING_DESTINATIONS);
        assertThat(input.getInputEvent()).isNull();
        assertThat(input.getAdId()).isNull();
        assertThat(input.getCallerSdkName()).isEqualTo("");
    }

    @Test
    public void testAdSelectionManager_reportEvent_adIdDisabled()
            throws JSONException, RemoteException {
        // TODO(b/296852054): Remove assumption once AdServicesOutcomeReceiver is used by FLEDGE.
        Assume.assumeTrue(Build.VERSION.SDK_INT > Build.VERSION_CODES.R);

        // Initialize manager with mocks
        MockAdIdManager mockAdIdManager = new MockAdIdManager(CONTEXT);
        MockServiceToReportEvent mockServiceToReportEvent = new MockServiceToReportEvent();
        AdSelectionManager mAdSelectionManager =
                AdSelectionManager.get(CONTEXT, mockAdIdManager, mockServiceToReportEvent);

        // Set expected outcome of AdIdManager#getAdId
        mockAdIdManager.setError(new SecurityException());

        mAdSelectionManager.reportEvent(
                mReportEventRequest, CALLBACK_EXECUTOR, new MockOutcomeReceiver<>());

        // Assert values passed to the service are as expected
        ReportInteractionInput input = mockServiceToReportEvent.mInput;
        assertThat(input.getAdSelectionId()).isEqualTo(AD_SELECTION_ID);
        assertThat(input.getCallerPackageName()).isEqualTo(CALLER_PACKAGE_NAME);
        assertThat(input.getInteractionKey()).isEqualTo(EVENT_KEY);
        assertThat(input.getInteractionData()).isEqualTo(mEventData);
        assertThat(input.getReportingDestinations()).isEqualTo(REPORTING_DESTINATIONS);
        assertThat(input.getInputEvent()).isNull();
        assertThat(input.getAdId()).isNull();
        assertThat(input.getCallerSdkName()).isEqualTo("");
    }

    @Test
    public void testAdSelectionManagerGetAdSelectionDataWhenResultIsByteArray() throws Exception {
        // TODO(b/296852054): Remove assumption once AdServicesOutcomeReceiver is used by FLEDGE.
        Assume.assumeTrue(Build.VERSION.SDK_INT > Build.VERSION_CODES.R);

        // Initialize manager with mocks
        MockAdIdManager mockAdIdManager = new MockAdIdManager(CONTEXT);
        MockServiceGetAdSelectionData mockServiceGetAdSelectionData =
                new MockServiceGetAdSelectionData();

        byte[] expectedByteArray = getRandomByteArray(TYPICAL_PAYLOAD_SIZE_BYTES);
        int expectedAdSelectionId = 1;
        mockServiceGetAdSelectionData.setResult(
                getAdSelectionDataResponseWithByteArray(expectedAdSelectionId, expectedByteArray));

        AdSelectionManager adSelectionManager =
                AdSelectionManager.get(CONTEXT, mockAdIdManager, mockServiceGetAdSelectionData);

        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(AdSelectionConfigFixture.SELLER)
                        .build();

        MockOutcomeReceiverGetAdSelectionData<GetAdSelectionDataOutcome, Exception>
                outcomeReceiver = new MockOutcomeReceiverGetAdSelectionData<>();

        adSelectionManager.getAdSelectionData(request, CALLBACK_EXECUTOR, outcomeReceiver);

        // Need time to sleep to allow time for outcome receiver to get setup
        doSleep(SLEEP_TIME_MS);

        assertNotNull(outcomeReceiver.getResult());
        assertThat(outcomeReceiver.getResult().getAdSelectionId()).isEqualTo(expectedAdSelectionId);
        assertArrayEquals(expectedByteArray, outcomeReceiver.getResult().getAdSelectionData());
    }

    @Test
    public void testAdSelectionManagerGetAdSelectionDataCoordinatorWasPassed() throws Exception {
        // TODO(b/296852054): Remove assumption once AdServicesOutcomeReceiver is used by FLEDGE.
        Assume.assumeTrue(Build.VERSION.SDK_INT > Build.VERSION_CODES.R);

        // Initialize manager with mocks
        MockAdIdManager mockAdIdManager = new MockAdIdManager(CONTEXT);
        MockServiceGetAdSelectionData mockServiceGetAdSelectionData =
                new MockServiceGetAdSelectionData();

        byte[] expectedByteArray = getRandomByteArray(TYPICAL_PAYLOAD_SIZE_BYTES);
        int expectedAdSelectionId = 1;
        mockServiceGetAdSelectionData.setResult(
                getAdSelectionDataResponseWithByteArray(expectedAdSelectionId, expectedByteArray));

        AdSelectionManager adSelectionManager =
                AdSelectionManager.get(CONTEXT, mockAdIdManager, mockServiceGetAdSelectionData);

        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(AdSelectionConfigFixture.SELLER)
                        .setCoordinatorOriginUri(Uri.parse("https://example.com"))
                        .build();

        MockOutcomeReceiverGetAdSelectionData<GetAdSelectionDataOutcome, Exception>
                outcomeReceiver = new MockOutcomeReceiverGetAdSelectionData<>();

        adSelectionManager.getAdSelectionData(request, CALLBACK_EXECUTOR, outcomeReceiver);

        // Need time to sleep to allow time for outcome receiver to get setup
        doSleep(SLEEP_TIME_MS);

        assertNotNull(outcomeReceiver.getResult());
        assertThat(mockServiceGetAdSelectionData.wasCoordinatorSet()).isTrue();
    }

    @Test
    public void testAdSelectionManagerGetAdSelectionDataWhenResultIsAssetFileDescriptor()
            throws Exception {
        // TODO(b/296852054): Remove assumption once AdServicesOutcomeReceiver is used by FLEDGE.
        Assume.assumeTrue(Build.VERSION.SDK_INT > Build.VERSION_CODES.R);

        // Initialize manager with mocks
        MockAdIdManager mockAdIdManager = new MockAdIdManager(CONTEXT);
        MockServiceGetAdSelectionData mockServiceGetAdSelectionData =
                new MockServiceGetAdSelectionData();

        byte[] expectedByteArray = getRandomByteArray(TYPICAL_PAYLOAD_SIZE_BYTES);
        int expectedAdSelectionId = 1;
        mockServiceGetAdSelectionData.setResult(
                getAdSelectionDataResponseWithAssetFileDescriptor(
                        expectedAdSelectionId, expectedByteArray, BLOCKING_EXECUTOR));

        AdSelectionManager adSelectionManager =
                AdSelectionManager.get(CONTEXT, mockAdIdManager, mockServiceGetAdSelectionData);

        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(AdSelectionConfigFixture.SELLER)
                        .build();

        MockOutcomeReceiverGetAdSelectionData<GetAdSelectionDataOutcome, Exception>
                outcomeReceiver = new MockOutcomeReceiverGetAdSelectionData<>();

        adSelectionManager.getAdSelectionData(request, CALLBACK_EXECUTOR, outcomeReceiver);

        // Need time to sleep to allow time for outcome receiver to get setup
        doSleep(SLEEP_TIME_MS);

        assertNotNull(outcomeReceiver.getResult());
        assertThat(outcomeReceiver.getResult().getAdSelectionId()).isEqualTo(expectedAdSelectionId);
        assertArrayEquals(expectedByteArray, outcomeReceiver.getResult().getAdSelectionData());
    }

    @Test
    public void
            testAdSelectionManagerGetAdSelectionDataWhenResultIsAssetFileDescriptorWithExcessiveSize()
                    throws Exception {
        // TODO(b/296852054): Remove assumption once AdServicesOutcomeReceiver is used by FLEDGE.
        Assume.assumeTrue(Build.VERSION.SDK_INT > Build.VERSION_CODES.R);

        // Initialize manager with mocks
        MockAdIdManager mockAdIdManager = new MockAdIdManager(CONTEXT);
        MockServiceGetAdSelectionData mockServiceGetAdSelectionData =
                new MockServiceGetAdSelectionData();

        byte[] expectedByteArray = getRandomByteArray(EXCESSIVE_PAYLOAD_SIZE_BYTES);
        int expectedAdSelectionId = 1;
        mockServiceGetAdSelectionData.setResult(
                getAdSelectionDataResponseWithAssetFileDescriptor(
                        expectedAdSelectionId, expectedByteArray, BLOCKING_EXECUTOR));
        AdSelectionManager adSelectionManager =
                AdSelectionManager.get(CONTEXT, mockAdIdManager, mockServiceGetAdSelectionData);

        GetAdSelectionDataRequest request =
                new GetAdSelectionDataRequest.Builder()
                        .setSeller(AdSelectionConfigFixture.SELLER)
                        .build();

        MockOutcomeReceiverGetAdSelectionData<GetAdSelectionDataOutcome, Exception>
                outcomeReceiver = new MockOutcomeReceiverGetAdSelectionData<>();
        adSelectionManager.getAdSelectionData(request, CALLBACK_EXECUTOR, outcomeReceiver);
        // Need time to sleep to allow time for outcome receiver to get setup
        doSleep(SLEEP_TIME_MS);
        assertNotNull(outcomeReceiver.getResult());
        assertThat(outcomeReceiver.getResult().getAdSelectionId()).isEqualTo(expectedAdSelectionId);

        byte[] result = outcomeReceiver.getResult().getAdSelectionData();
        assertThat(result.length).isEqualTo(expectedByteArray.length);
        assertArrayEquals(expectedByteArray, result);
    }

    private static byte[] getRandomByteArray(int size) {
        SecureRandom secureRandom = new SecureRandom();
        byte[] result = new byte[size];
        secureRandom.nextBytes(result);
        return result;
    }

    // TODO(b/296886238): Remove this mock once Mockito issue is resolved.
    private static class MockAdIdManager extends AdIdCompatibleManager {
        private AdId mAdId;
        private Exception mException;

        MockAdIdManager(Context context) {
            super(context);
        }

        public void setResult(AdId adId) {
            mAdId = adId;
            mException = null;
        }

        public AdId getResult() {
            return mAdId;
        }

        public void setError(Exception exception) {
            mException = exception;
            mAdId = null;
        }

        public Exception getError() {
            return mException;
        }

        @Override
        @NonNull
        public void getAdId(
                @NonNull @CallbackExecutor Executor executor,
                @NonNull AdServicesOutcomeReceiver<AdId, Exception> callback) {
            if (mAdId != null) {
                callback.onResult(mAdId);
            } else if (mException != null) {
                callback.onError(mException);
            } else {
                throw new NullPointerException("Neither result nor error are set.");
            }
        }
    }

    // TODO(b/296886238): Remove this mock once Mockito issue is resolved.
    private static class MockAdSelectionService implements AdSelectionService {
        MockAdSelectionService() {}

        @Override
        public void getAdSelectionData(
                GetAdSelectionDataInput getAdSelectionDataInput,
                CallerMetadata callerMetadata,
                GetAdSelectionDataCallback getAdSelectionDataCallback)
                throws RemoteException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void persistAdSelectionResult(
                PersistAdSelectionResultInput persistAdSelectionResultInput,
                CallerMetadata callerMetadata,
                PersistAdSelectionResultCallback persistAdSelectionResultCallback)
                throws RemoteException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void selectAds(
                AdSelectionInput adSelectionInput,
                CallerMetadata callerMetadata,
                AdSelectionCallback adSelectionCallback)
                throws RemoteException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void selectAdsFromOutcomes(
                AdSelectionFromOutcomesInput adSelectionFromOutcomesInput,
                CallerMetadata callerMetadata,
                AdSelectionCallback adSelectionCallback)
                throws RemoteException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void reportImpression(
                ReportImpressionInput reportImpressionInput,
                ReportImpressionCallback reportImpressionCallback)
                throws RemoteException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void reportInteraction(
                ReportInteractionInput reportInteractionInput,
                ReportInteractionCallback reportInteractionCallback)
                throws RemoteException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateAdCounterHistogram(
                UpdateAdCounterHistogramInput updateAdCounterHistogramInput,
                UpdateAdCounterHistogramCallback updateAdCounterHistogramCallback)
                throws RemoteException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void overrideAdSelectionConfigRemoteInfo(
                AdSelectionConfig adSelectionConfig,
                String s,
                AdSelectionSignals adSelectionSignals,
                PerBuyerDecisionLogic perBuyerDecisionLogic,
                AdSelectionOverrideCallback adSelectionOverrideCallback)
                throws RemoteException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setAppInstallAdvertisers(
                SetAppInstallAdvertisersInput setAppInstallAdvertisersInput,
                SetAppInstallAdvertisersCallback setAppInstallAdvertisersCallback)
                throws RemoteException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeAdSelectionConfigRemoteInfoOverride(
                AdSelectionConfig adSelectionConfig,
                AdSelectionOverrideCallback adSelectionOverrideCallback)
                throws RemoteException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void resetAllAdSelectionConfigRemoteOverrides(
                AdSelectionOverrideCallback adSelectionOverrideCallback) throws RemoteException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void overrideAdSelectionFromOutcomesConfigRemoteInfo(
                AdSelectionFromOutcomesConfig adSelectionFromOutcomesConfig,
                String s,
                AdSelectionSignals adSelectionSignals,
                AdSelectionOverrideCallback adSelectionOverrideCallback)
                throws RemoteException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeAdSelectionFromOutcomesConfigRemoteInfoOverride(
                AdSelectionFromOutcomesConfig adSelectionFromOutcomesConfig,
                AdSelectionOverrideCallback adSelectionOverrideCallback)
                throws RemoteException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void resetAllAdSelectionFromOutcomesConfigRemoteOverrides(
                AdSelectionOverrideCallback adSelectionOverrideCallback) throws RemoteException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setAdCounterHistogramOverride(
                SetAdCounterHistogramOverrideInput setAdCounterHistogramOverrideInput,
                AdSelectionOverrideCallback adSelectionOverrideCallback)
                throws RemoteException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeAdCounterHistogramOverride(
                RemoveAdCounterHistogramOverrideInput removeAdCounterHistogramOverrideInput,
                AdSelectionOverrideCallback adSelectionOverrideCallback)
                throws RemoteException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void resetAllAdCounterHistogramOverrides(
                AdSelectionOverrideCallback adSelectionOverrideCallback) throws RemoteException {
            throw new UnsupportedOperationException();
        }

        @Override
        public IBinder asBinder() {
            return null;
        }
    }

    // TODO(b/296886238): Remove this mock once Mockito issue is resolved.
    private static class MockServiceToReportEvent extends MockAdSelectionService {
        ReportInteractionInput mInput;

        MockServiceToReportEvent() {}

        @Override
        public void reportInteraction(
                ReportInteractionInput reportInteractionInput,
                ReportInteractionCallback reportInteractionCallback)
                throws RemoteException {
            this.mInput = reportInteractionInput;
        }
    }

    // TODO(b/296886238): Remove this mock once Mockito issue is resolved.
    private static class MockServiceGetAdSelectionData extends MockAdSelectionService {

        MockServiceGetAdSelectionData() {}

        private GetAdSelectionDataResponse mGetAdSelectionDataResponse;
        private boolean mWasCoordinatorSet;

        @Override
        public void getAdSelectionData(
                GetAdSelectionDataInput getAdSelectionDataInput,
                CallerMetadata callerMetadata,
                GetAdSelectionDataCallback getAdSelectionDataCallback)
                throws RemoteException {
            mWasCoordinatorSet =
                    getAdSelectionDataInput.getCoordinatorOriginUri() != null
                            && !Strings.isNullOrEmpty(
                                    getAdSelectionDataInput.getCoordinatorOriginUri().toString());
            getAdSelectionDataCallback.onSuccess(mGetAdSelectionDataResponse);
        }

        public void setResult(GetAdSelectionDataResponse response) {
            mGetAdSelectionDataResponse = response;
        }

        public boolean wasCoordinatorSet() {
            return mWasCoordinatorSet;
        }
    }

    // TODO(b/296886238): Remove this mock once Mockito issue is resolved.
    private static class MockOutcomeReceiver<R, E extends Throwable>
            implements android.os.OutcomeReceiver<Object, Exception> {
        @Override
        public void onResult(Object result) {}

        @Override
        public void onError(Exception exception) {}
    }

    // TODO(b/296886238): Remove this mock once Mockito issue is resolved.
    private static class MockOutcomeReceiverGetAdSelectionData<R, E extends Throwable>
            implements android.os.OutcomeReceiver<GetAdSelectionDataOutcome, Exception> {
        private GetAdSelectionDataOutcome mGetAdSelectionDataOutcome;

        public GetAdSelectionDataOutcome getResult() {
            return mGetAdSelectionDataOutcome;
        }

        @Override
        public void onResult(GetAdSelectionDataOutcome result) {
            mGetAdSelectionDataOutcome = result;
        }

        @Override
        public void onError(Exception exception) {}
    }
}
