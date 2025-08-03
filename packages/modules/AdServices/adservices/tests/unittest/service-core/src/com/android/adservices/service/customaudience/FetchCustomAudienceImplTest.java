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

package com.android.adservices.service.customaudience;

import static android.adservices.common.AdServicesStatusUtils.ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.RATE_LIMIT_REACHED_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_OBJECT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SERVER_RATE_LIMIT_REACHED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
import static android.adservices.common.CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;
import static android.adservices.common.CommonFixture.VALID_BUYER_1;
import static android.adservices.common.CommonFixture.VALID_BUYER_2;
import static android.adservices.customaudience.CustomAudience.FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS;
import static android.adservices.customaudience.CustomAudienceFixture.CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN;
import static android.adservices.customaudience.CustomAudienceFixture.INVALID_BEYOND_MAX_EXPIRATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.INVALID_DELAYED_ACTIVATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_EXPIRATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_NAME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_OWNER;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS;
import static android.adservices.customaudience.CustomAudienceFixture.getValidDailyUpdateUriByBuyer;
import static android.adservices.exceptions.RetryableAdServicesNetworkException.DEFAULT_RETRY_AFTER_VALUE;

import static com.android.adservices.service.common.AdTechUriValidator.IDENTIFIER_AND_URI_ARE_INCONSISTENT;
import static com.android.adservices.service.common.Validator.EXCEPTION_MESSAGE_FORMAT;
import static com.android.adservices.service.common.ValidatorUtil.AD_TECH_ROLE_BUYER;
import static com.android.adservices.service.customaudience.CustomAudienceBlob.BUYER_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceBlob.OWNER_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceBlobFixture.addDailyUpdateUri;
import static com.android.adservices.service.customaudience.CustomAudienceBlobFixture.addName;
import static com.android.adservices.service.customaudience.CustomAudienceBlobValidator.CLASS_NAME;
import static com.android.adservices.service.customaudience.CustomAudienceExpirationTimeValidatorTest.CUSTOM_AUDIENCE_MAX_EXPIRE_IN;
import static com.android.adservices.service.customaudience.CustomAudienceFieldSizeValidator.VIOLATION_NAME_TOO_LONG;
import static com.android.adservices.service.customaudience.CustomAudienceFieldSizeValidator.VIOLATION_USER_BIDDING_SIGNAL_TOO_BIG;
import static com.android.adservices.service.customaudience.CustomAudienceQuantityChecker.CUSTOM_AUDIENCE_QUANTITY_CHECK_FAILED;
import static com.android.adservices.service.customaudience.CustomAudienceQuantityChecker.THE_MAX_NUMBER_OF_CUSTOM_AUDIENCE_FOR_THE_DEVICE_HAD_REACHED;
import static com.android.adservices.service.customaudience.CustomAudienceQuantityChecker.THE_MAX_NUMBER_OF_CUSTOM_AUDIENCE_FOR_THE_OWNER_HAD_REACHED;
import static com.android.adservices.service.customaudience.CustomAudienceTimestampValidator.VIOLATION_ACTIVATE_AFTER_MAX_ACTIVATE;
import static com.android.adservices.service.customaudience.CustomAudienceTimestampValidator.VIOLATION_EXPIRE_AFTER_MAX_EXPIRE_TIME;
import static com.android.adservices.service.customaudience.CustomAudienceTimestampValidator.VIOLATION_EXPIRE_BEFORE_ACTIVATION;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.ADS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.USER_BIDDING_SIGNALS_KEY;
import static com.android.adservices.service.customaudience.CustomAudienceValidator.DAILY_UPDATE_URI_FIELD_NAME;
import static com.android.adservices.service.customaudience.FetchCustomAudienceFixture.getFullSuccessfulJsonResponse;
import static com.android.adservices.service.customaudience.FetchCustomAudienceFixture.getFullSuccessfulJsonResponseString;
import static com.android.adservices.service.customaudience.FetchCustomAudienceFixture.getFullSuccessfulJsonResponseStringWithAdRenderId;
import static com.android.adservices.service.customaudience.FetchCustomAudienceImpl.FUSED_CUSTOM_AUDIENCE_EXCEEDS_SIZE_LIMIT_MESSAGE;
import static com.android.adservices.service.customaudience.FetchCustomAudienceImpl.FUSED_CUSTOM_AUDIENCE_INCOMPLETE_MESSAGE;
import static com.android.adservices.service.customaudience.FetchCustomAudienceImpl.REQUEST_CUSTOM_HEADER_EXCEEDS_SIZE_LIMIT_MESSAGE;
import static com.android.adservices.service.customaudience.FetchCustomAudienceReader.ACTIVATION_TIME_KEY;
import static com.android.adservices.service.customaudience.FetchCustomAudienceReader.DAILY_UPDATE_URI_KEY;
import static com.android.adservices.service.customaudience.FetchCustomAudienceReader.EXPIRATION_TIME_KEY;
import static com.android.adservices.service.customaudience.FetchCustomAudienceReader.NAME_KEY;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.FetchAndJoinCustomAudienceCallback;
import android.adservices.customaudience.FetchAndJoinCustomAudienceInput;
import android.adservices.http.MockWebServerRule;
import android.net.Uri;
import android.os.LimitExceededException;
import android.os.Process;
import android.os.RemoteException;

import com.android.adservices.LoggerFactory;
import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.customaudience.AdDataConversionStrategy;
import com.android.adservices.data.customaudience.AdDataConversionStrategyFactory;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceStats;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBCustomAudienceQuarantine;
import com.android.adservices.service.Flags;
import com.android.adservices.service.adselection.AdFilteringFeatureFactory;
import com.android.adservices.service.common.AdRenderIdValidator;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.CustomAudienceServiceFilter;
import com.android.adservices.service.common.FledgeAllowListsFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(MockitoJUnitRunner.class)
public class FetchCustomAudienceImplTest {
    private static final int API_NAME =
            AD_SERVICES_API_CALLED__API_NAME__FETCH_AND_JOIN_CUSTOM_AUDIENCE;
    private static final ExecutorService DIRECT_EXECUTOR = MoreExecutors.newDirectExecutorService();
    private static final AdDataConversionStrategy AD_DATA_CONVERSION_STRATEGY =
            AdDataConversionStrategyFactory.getAdDataConversionStrategy(true, true);
    private static final Clock CLOCK = CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI;
    private final AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);
    @Mock private CustomAudienceServiceFilter mCustomAudienceServiceFilterMock;
    @Mock private CustomAudienceDao mCustomAudienceDaoMock;
    @Mock private AppInstallDao mAppInstallDaoMock;
    @Mock private FrequencyCapDao mFrequencyCapDaoMock;
    private final AdRenderIdValidator mAdRenderIdValidator =
            AdRenderIdValidator.createEnabledInstance(100);
    private AdFilteringFeatureFactory mAdFilteringFeatureFactory;

    @Spy
    private final AdServicesHttpsClient mHttpClientSpy =
            new AdServicesHttpsClient(
                    AdServicesExecutors.getBlockingExecutor(),
                    CacheProviderFactory.createNoOpCache());

    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("localhost");
    private int mCallingAppUid;
    private Uri mFetchUri;
    private FetchCustomAudienceImpl mFetchCustomAudienceImpl;
    private FetchAndJoinCustomAudienceInput.Builder mInputBuilder;
    private MockitoSession mStaticMockSession = null;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .mockStatic(ConsentManager.class)
                        .startMocking();

        mCallingAppUid = CallingAppUidSupplierProcessImpl.create().getCallingAppUid();

        mFetchUri = mMockWebServerRule.uriForPath("/fetch");

        mInputBuilder =
                new FetchAndJoinCustomAudienceInput.Builder(mFetchUri, VALID_OWNER)
                        .setName(CustomAudienceFixture.VALID_NAME)
                        .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                        .setExpirationTime(VALID_EXPIRATION_TIME)
                        .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS);

        Flags flags = new FetchCustomAudienceFlags();

        mAdFilteringFeatureFactory =
                new AdFilteringFeatureFactory(mAppInstallDaoMock, mFrequencyCapDaoMock, flags);

        mFetchCustomAudienceImpl = getImplWithFlags(flags);

        doReturn(BUYER)
                .when(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        mFetchUri,
                        VALID_OWNER,
                        false,
                        true,
                        true,
                        Process.myUid(),
                        API_NAME,
                        Throttler.ApiKey.FLEDGE_API_FETCH_CUSTOM_AUDIENCE,
                        DevContext.createForDevOptionsDisabled());
        doReturn(BUYER)
                .when(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        mFetchUri,
                        VALID_OWNER,
                        false,
                        true,
                        true,
                        Process.myUid(),
                        API_NAME,
                        Throttler.ApiKey.FLEDGE_API_FETCH_CUSTOM_AUDIENCE,
                        DevContext.builder().setDevOptionsEnabled(true).build());
        doReturn(
                        CustomAudienceStats.builder()
                                .setTotalCustomAudienceCount(1)
                                .setBuyer(BUYER)
                                .setOwner(VALID_OWNER)
                                .setPerOwnerCustomAudienceCount(1)
                                .setPerBuyerCustomAudienceCount(1)
                                .setTotalBuyerCount(1)
                                .setTotalOwnerCount(1)
                                .build())
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceStats(eq(VALID_OWNER));

        doReturn(false)
                .when(mCustomAudienceDaoMock)
                .doesCustomAudienceQuarantineExist(VALID_OWNER, BUYER);
    }

    @After
    public void tearDown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testImpl_disabled() throws Exception {
        // Use flag value to disable the API.
        mFetchCustomAudienceImpl =
                getImplWithFlags(
                        new FetchCustomAudienceFlags() {
                            @Override
                            public boolean getFledgeFetchCustomAudienceEnabled() {
                                return false;
                            }
                        });

        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(
                                                getFullSuccessfulJsonResponseString(
                                                        VALID_BUYER_1))));

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertEquals(0, mockWebServer.getRequestCount());
        assertFalse(callback.mIsSuccess);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_INTERNAL_ERROR), anyInt());
    }

    @Test
    public void testImpl_invalidPackageName_throws() throws Exception {
        String otherPackageName = VALID_OWNER + "incorrectPackage";

        doThrow(new FledgeAuthorizationFilter.CallerMismatchException())
                .when(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        mFetchUri,
                        otherPackageName,
                        false,
                        true,
                        true,
                        Process.myUid(),
                        API_NAME,
                        Throttler.ApiKey.FLEDGE_API_FETCH_CUSTOM_AUDIENCE,
                        DevContext.createForDevOptionsDisabled());

        FetchCustomAudienceTestCallback callback =
                callFetchCustomAudience(
                        mInputBuilder.setCallerPackageName(otherPackageName).build());

        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_UNAUTHORIZED, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());

        // Confirm a duplicate log entry does not exist.
        // CustomAudienceServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_UNAUTHORIZED), anyInt());
    }

    @Test
    public void testImpl_throttled_throws() throws Exception {
        doThrow(new LimitExceededException(RATE_LIMIT_REACHED_ERROR_MESSAGE))
                .when(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        mFetchUri,
                        VALID_OWNER,
                        false,
                        true,
                        true,
                        Process.myUid(),
                        API_NAME,
                        Throttler.ApiKey.FLEDGE_API_FETCH_CUSTOM_AUDIENCE,
                        DevContext.createForDevOptionsDisabled());

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_RATE_LIMIT_REACHED, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                RATE_LIMIT_REACHED_ERROR_MESSAGE, callback.mFledgeErrorResponse.getErrorMessage());

        // Confirm a duplicate log entry does not exist.
        // CustomAudienceServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(API_NAME),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_RATE_LIMIT_REACHED),
                        anyInt());
    }

    @Test
    public void testImpl_failedForegroundCheck_throws() throws Exception {
        doThrow(new AppImportanceFilter.WrongCallingApplicationStateException())
                .when(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        mFetchUri,
                        VALID_OWNER,
                        false,
                        true,
                        true,
                        Process.myUid(),
                        API_NAME,
                        Throttler.ApiKey.FLEDGE_API_FETCH_CUSTOM_AUDIENCE,
                        DevContext.createForDevOptionsDisabled());

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_BACKGROUND_CALLER, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());

        // Confirm a duplicate log entry does not exist.
        // CustomAudienceServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(API_NAME),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_BACKGROUND_CALLER),
                        anyInt());
    }

    @Test
    public void testImpl_failedEnrollmentCheck_throws() throws Exception {
        doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        mFetchUri,
                        VALID_OWNER,
                        false,
                        true,
                        true,
                        Process.myUid(),
                        API_NAME,
                        Throttler.ApiKey.FLEDGE_API_FETCH_CUSTOM_AUDIENCE,
                        DevContext.createForDevOptionsDisabled());

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_CALLER_NOT_ALLOWED, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());

        // Confirm a duplicate log entry does not exist.
        // CustomAudienceServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(API_NAME),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_CALLER_NOT_ALLOWED),
                        anyInt());
    }

    @Test
    public void testImpl_appCannotUsePPAPI_throws() throws Exception {
        doThrow(new FledgeAllowListsFilter.AppNotAllowedException())
                .when(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        mFetchUri,
                        VALID_OWNER,
                        false,
                        true,
                        true,
                        Process.myUid(),
                        API_NAME,
                        Throttler.ApiKey.FLEDGE_API_FETCH_CUSTOM_AUDIENCE,
                        DevContext.createForDevOptionsDisabled());

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_CALLER_NOT_ALLOWED, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());

        // Confirm a duplicate log entry does not exist.
        // CustomAudienceServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(API_NAME),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_CALLER_NOT_ALLOWED),
                        anyInt());
    }

    @Test
    public void testImpl_revokedConsent_failsSilently() throws Exception {
        doThrow(new ConsentManager.RevokedConsentException())
                .when(mCustomAudienceServiceFilterMock)
                .filterRequestAndExtractIdentifier(
                        mFetchUri,
                        VALID_OWNER,
                        false,
                        true,
                        true,
                        Process.myUid(),
                        API_NAME,
                        Throttler.ApiKey.FLEDGE_API_FETCH_CUSTOM_AUDIENCE,
                        DevContext.createForDevOptionsDisabled());

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertTrue(callback.mIsSuccess);

        // Confirm a duplicate log entry does not exist.
        // CustomAudienceServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(API_NAME),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        anyInt());
    }

    @Test
    public void testImpl_invalidRequest_quotaExhausted_throws() throws Exception {
        // Use flag values with a clearly small quota limits.
        mFetchCustomAudienceImpl =
                getImplWithFlags(
                        new FetchCustomAudienceFlags() {
                            @Override
                            public long getFledgeCustomAudienceMaxOwnerCount() {
                                return 0;
                            }

                            @Override
                            public long getFledgeCustomAudienceMaxCount() {
                                return 0;
                            }

                            @Override
                            public long getFledgeCustomAudiencePerAppMaxCount() {
                                return 0;
                            }
                        });

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_INVALID_ARGUMENT, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                String.format(
                        Locale.ENGLISH,
                        CUSTOM_AUDIENCE_QUANTITY_CHECK_FAILED,
                        ImmutableList.of(
                                THE_MAX_NUMBER_OF_CUSTOM_AUDIENCE_FOR_THE_DEVICE_HAD_REACHED,
                                THE_MAX_NUMBER_OF_CUSTOM_AUDIENCE_FOR_THE_OWNER_HAD_REACHED)),
                callback.mFledgeErrorResponse.getErrorMessage());

        // Assert failure due to the invalid argument is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_INVALID_ARGUMENT), anyInt());
    }

    @Test
    public void testImpl_invalidRequest_invalidName_throws() throws Exception {
        // Use flag value with a clearly small size limit.
        mFetchCustomAudienceImpl =
                getImplWithFlags(
                        new FetchCustomAudienceFlags() {
                            @Override
                            public int getFledgeCustomAudienceMaxNameSizeB() {
                                return 1;
                            }
                        });

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_INVALID_ARGUMENT, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                String.format(
                        Locale.ENGLISH,
                        EXCEPTION_MESSAGE_FORMAT,
                        CLASS_NAME,
                        ImmutableList.of(
                                String.format(
                                        Locale.ENGLISH,
                                        VIOLATION_NAME_TOO_LONG,
                                        1,
                                        VALID_NAME.getBytes(StandardCharsets.UTF_8).length))),
                callback.mFledgeErrorResponse.getErrorMessage());

        // Assert failure due to the invalid argument is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_INVALID_ARGUMENT), anyInt());
    }

    @Test
    public void testImpl_invalidRequest_invalidActivationTime_throws() throws Exception {
        mInputBuilder.setActivationTime(INVALID_DELAYED_ACTIVATION_TIME);

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_INVALID_ARGUMENT, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                String.format(
                        Locale.ENGLISH,
                        EXCEPTION_MESSAGE_FORMAT,
                        CLASS_NAME,
                        ImmutableList.of(
                                String.format(
                                        Locale.ENGLISH,
                                        VIOLATION_ACTIVATE_AFTER_MAX_ACTIVATE,
                                        CUSTOM_AUDIENCE_MAX_ACTIVATION_DELAY_IN,
                                        FIXED_NOW_TRUNCATED_TO_MILLI,
                                        INVALID_DELAYED_ACTIVATION_TIME),
                                String.format(
                                        VIOLATION_EXPIRE_BEFORE_ACTIVATION,
                                        INVALID_DELAYED_ACTIVATION_TIME,
                                        VALID_EXPIRATION_TIME))),
                callback.mFledgeErrorResponse.getErrorMessage());

        // Assert failure due to the invalid argument is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_INVALID_ARGUMENT), anyInt());
    }

    @Test
    public void testImpl_invalidRequest_invalidExpirationTime_throws() throws Exception {
        mInputBuilder.setExpirationTime(INVALID_BEYOND_MAX_EXPIRATION_TIME);

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_INVALID_ARGUMENT, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                String.format(
                        Locale.ENGLISH,
                        EXCEPTION_MESSAGE_FORMAT,
                        CLASS_NAME,
                        ImmutableList.of(
                                String.format(
                                        Locale.ENGLISH,
                                        VIOLATION_EXPIRE_AFTER_MAX_EXPIRE_TIME,
                                        CUSTOM_AUDIENCE_MAX_EXPIRE_IN,
                                        FIXED_NOW_TRUNCATED_TO_MILLI,
                                        FIXED_NOW_TRUNCATED_TO_MILLI,
                                        INVALID_BEYOND_MAX_EXPIRATION_TIME))),
                callback.mFledgeErrorResponse.getErrorMessage());

        // Assert failure due to the invalid argument is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_INVALID_ARGUMENT), anyInt());
    }

    @Test
    public void testImpl_invalidRequest_invalidUserBiddingSignals_throws() throws Exception {
        // Use flag value with a clearly small size limit.
        mFetchCustomAudienceImpl =
                getImplWithFlags(
                        new FetchCustomAudienceFlags() {
                            @Override
                            public int getFledgeFetchCustomAudienceMaxUserBiddingSignalsSizeB() {
                                return 1;
                            }
                        });

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_INVALID_ARGUMENT, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                String.format(
                        Locale.ENGLISH,
                        EXCEPTION_MESSAGE_FORMAT,
                        CLASS_NAME,
                        ImmutableList.of(
                                String.format(
                                        Locale.ENGLISH,
                                        VIOLATION_USER_BIDDING_SIGNAL_TOO_BIG,
                                        1,
                                        VALID_USER_BIDDING_SIGNALS.getSizeInBytes()))),
                callback.mFledgeErrorResponse.getErrorMessage());

        // Assert failure due to the invalid argument is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_INVALID_ARGUMENT), anyInt());
    }

    @Test
    public void testImpl_customHeaderExceedsLimit_throws() throws Exception {
        // Use flag value with a clearly small size limit.
        mFetchCustomAudienceImpl =
                getImplWithFlags(
                        new FetchCustomAudienceFlags() {
                            @Override
                            public int getFledgeFetchCustomAudienceMaxRequestCustomHeaderSizeB() {
                                return 1;
                            }
                        });

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_INVALID_ARGUMENT, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                REQUEST_CUSTOM_HEADER_EXCEEDS_SIZE_LIMIT_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());

        // Assert failure due to the invalid argument is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_INVALID_ARGUMENT), anyInt());
    }

    @Test
    public void testImpl_invalidResponse_invalidJSONObject() throws Exception {
        String jsonString = "Not[A]VALID[JSON]";
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse().setBody("Not[A]VALID[JSON]")));

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertEquals(1, mockWebServer.getRequestCount());
        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_INVALID_OBJECT, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                "Value Not of type "
                        + jsonString.getClass().getName()
                        + " cannot be converted to JSONObject",
                callback.mFledgeErrorResponse.getErrorMessage());

        // Assert failure due to the invalid response is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_INVALID_OBJECT), anyInt());
    }

    @Test
    public void testImpl_invalidFused_missingField() throws Exception {
        // Remove ads from the response, resulting in an incomplete fused custom audience.
        JSONObject validResponse =
                getFullSuccessfulJsonResponse(BUYER, /* auctionServerRequestFlagsEnabled= */ false);
        validResponse.remove(ADS_KEY);

        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse().setBody(validResponse.toString())));

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertEquals(1, mockWebServer.getRequestCount());
        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_INVALID_OBJECT, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                FUSED_CUSTOM_AUDIENCE_INCOMPLETE_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());

        // Assert failure due to the invalid response is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_INVALID_OBJECT), anyInt());
    }

    @Test
    public void testImpl_invalidFused_missingFieldAuctionServerFlagsEnabled() throws Exception {
        enableAuctionServerRequestFlags();

        // Remove adsKey from the response, resulting in an incomplete fused custom audience.
        JSONObject validResponse =
                getFullSuccessfulJsonResponse(BUYER, /* auctionServerRequestFlagsEnabled= */ true);
        validResponse.remove(ADS_KEY);

        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse().setBody(validResponse.toString())));

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertEquals(1, mockWebServer.getRequestCount());
        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_INVALID_OBJECT, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                FUSED_CUSTOM_AUDIENCE_INCOMPLETE_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());

        // Assert failure due to the invalid response is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_INVALID_OBJECT), anyInt());
    }

    @Test
    public void testImpl_invalidFused_invalidField() throws Exception {
        // Replace buyer to cause mismatch.
        JSONObject validResponse = getFullSuccessfulJsonResponse(BUYER, false);
        validResponse.remove(DAILY_UPDATE_URI_KEY);
        JSONObject invalidResponse =
                addDailyUpdateUri(
                        validResponse, getValidDailyUpdateUriByBuyer(VALID_BUYER_2), false);

        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse().setBody(invalidResponse.toString())));

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertEquals(1, mockWebServer.getRequestCount());
        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_INVALID_ARGUMENT, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                String.format(
                        Locale.ENGLISH,
                        EXCEPTION_MESSAGE_FORMAT,
                        CLASS_NAME,
                        ImmutableList.of(
                                String.format(
                                        Locale.ENGLISH,
                                        IDENTIFIER_AND_URI_ARE_INCONSISTENT,
                                        AD_TECH_ROLE_BUYER,
                                        BUYER,
                                        AD_TECH_ROLE_BUYER,
                                        DAILY_UPDATE_URI_FIELD_NAME,
                                        VALID_BUYER_2))),
                callback.mFledgeErrorResponse.getErrorMessage());

        // Assert failure due to the invalid response is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_INVALID_ARGUMENT), anyInt());
    }

    @Test
    public void testImpl_invalidFused_exceedsSizeLimit() throws Exception {
        // Use flag value with a clearly small size limit.
        mFetchCustomAudienceImpl =
                getImplWithFlags(
                        new FetchCustomAudienceFlags() {
                            @Override
                            public int getFledgeFetchCustomAudienceMaxCustomAudienceSizeB() {
                                return 1;
                            }
                        });

        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(getFullSuccessfulJsonResponseString(BUYER))));

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertEquals(1, mockWebServer.getRequestCount());
        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_INVALID_OBJECT, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                FUSED_CUSTOM_AUDIENCE_EXCEEDS_SIZE_LIMIT_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());

        // Assert failure due to the invalid response is logged.
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_INVALID_OBJECT), anyInt());
    }

    @Test
    public void testImpl_runNormally_partialResponse() throws Exception {
        // Remove all fields from the response that the request itself has.
        JSONObject partialResponse = getFullSuccessfulJsonResponse(BUYER, false);
        partialResponse.remove(OWNER_KEY);
        partialResponse.remove(BUYER_KEY);
        partialResponse.remove(NAME_KEY);
        partialResponse.remove(ACTIVATION_TIME_KEY);
        partialResponse.remove(EXPIRATION_TIME_KEY);
        partialResponse.remove(USER_BIDDING_SIGNALS_KEY);

        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse().setBody(partialResponse.toString())));

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertEquals(1, mockWebServer.getRequestCount());
        assertTrue(callback.mIsSuccess);
        verify(mCustomAudienceDaoMock)
                .insertOrOverwriteCustomAudience(
                        FetchCustomAudienceFixture.getFullSuccessfulDBCustomAudience(),
                        getValidDailyUpdateUriByBuyer(BUYER),
                        DevContext.createForDevOptionsDisabled().getDevOptionsEnabled());
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_SUCCESS), anyInt());
    }

    @Test
    public void testImpl_runNormally_discardedResponseValues() throws Exception {
        // Replace response name with a valid but different name from the original request.
        JSONObject validResponse = getFullSuccessfulJsonResponse(BUYER, false);
        validResponse.remove(NAME_KEY);
        String validNameFromTheServer = VALID_NAME + "FromTheServer";
        validResponse = addName(validResponse, validNameFromTheServer, false);

        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse().setBody(validResponse.toString())));

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertEquals(1, mockWebServer.getRequestCount());
        assertTrue(callback.mIsSuccess);
        // Assert the response value is in fact discarded in favor of the request value.
        verify(mCustomAudienceDaoMock)
                .insertOrOverwriteCustomAudience(
                        FetchCustomAudienceFixture.getFullSuccessfulDBCustomAudience(),
                        getValidDailyUpdateUriByBuyer(BUYER),
                        DevContext.createForDevOptionsDisabled().getDevOptionsEnabled());
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_SUCCESS), anyInt());
    }

    @Test
    public void testImpl_runNormally_completeResponse() throws Exception {
        // Respond with a complete custom audience including the request values as is.
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(getFullSuccessfulJsonResponseString(BUYER))));

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertEquals(1, mockWebServer.getRequestCount());
        assertTrue(callback.mIsSuccess);
        verify(mCustomAudienceDaoMock)
                .insertOrOverwriteCustomAudience(
                        FetchCustomAudienceFixture.getFullSuccessfulDBCustomAudience(),
                        getValidDailyUpdateUriByBuyer(BUYER),
                        DevContext.createForDevOptionsDisabled().getDevOptionsEnabled());
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_SUCCESS), anyInt());
    }

    @Test
    public void testImpl_runNormally_completeResponseWithAuctionServerFlagsEnabled()
            throws Exception {
        enableAuctionServerRequestFlags();

        // Respond with a complete custom audience including the request values as is and auction
        // request flags
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(
                                                getFullSuccessfulJsonResponse(BUYER, true)
                                                        .toString())));

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertEquals(1, mockWebServer.getRequestCount());
        assertTrue(callback.mIsSuccess);
        verify(mCustomAudienceDaoMock)
                .insertOrOverwriteCustomAudience(
                        FetchCustomAudienceFixture
                                .getFullSuccessfulDBCustomAudienceWithAuctionServerRequestFlags(
                                        FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS),
                        getValidDailyUpdateUriByBuyer(BUYER),
                        DevContext.createForDevOptionsDisabled().getDevOptionsEnabled());
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_SUCCESS), anyInt());
    }

    @Test
    public void testImpl_runNormally_completeResponseWithAuctionServerFlagsDisabled()
            throws Exception {
        // Respond with a complete custom audience including the request values as is and auction
        // request flags
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(
                                                getFullSuccessfulJsonResponse(BUYER, true)
                                                        .toString())));

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertEquals(1, mockWebServer.getRequestCount());
        assertTrue(callback.mIsSuccess);
        // Expect a CA without auction server request flags
        verify(mCustomAudienceDaoMock)
                .insertOrOverwriteCustomAudience(
                        FetchCustomAudienceFixture.getFullSuccessfulDBCustomAudience(),
                        getValidDailyUpdateUriByBuyer(BUYER),
                        DevContext.createForDevOptionsDisabled().getDevOptionsEnabled());
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_SUCCESS), anyInt());
    }

    @Test
    public void
            testImpl_runNormally_completeResponseWithAuctionServerFlagsEnabledButNoFlagsInResponse()
                    throws Exception {
        enableAuctionServerRequestFlags();

        // Respond with a complete custom audience including the request values as is
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(
                                                getFullSuccessfulJsonResponse(BUYER, false)
                                                        .toString())));

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertEquals(1, mockWebServer.getRequestCount());
        assertTrue(callback.mIsSuccess);
        // Expect a CA without auction server request flags
        verify(mCustomAudienceDaoMock)
                .insertOrOverwriteCustomAudience(
                        FetchCustomAudienceFixture.getFullSuccessfulDBCustomAudience(),
                        getValidDailyUpdateUriByBuyer(BUYER),
                        DevContext.createForDevOptionsDisabled().getDevOptionsEnabled());
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_SUCCESS), anyInt());
    }

    @Test
    public void testImpl_runNormally_withDevOptionsEnabled() throws Exception {
        // Respond with a complete custom audience including the request values as is.
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(getFullSuccessfulJsonResponseString(BUYER))));

        CountDownLatch resultLatch = new CountDownLatch(1);
        FetchCustomAudienceTestCallback callback = new FetchCustomAudienceTestCallback(resultLatch);
        mFetchCustomAudienceImpl.doFetchCustomAudience(
                mInputBuilder.build(),
                callback,
                DevContext.builder().setDevOptionsEnabled(true).build());
        resultLatch.await();

        assertEquals(1, mockWebServer.getRequestCount());
        assertTrue(callback.mIsSuccess);
        verify(mCustomAudienceDaoMock)
                .insertOrOverwriteCustomAudience(
                        FetchCustomAudienceFixture.getFullSuccessfulDBCustomAudience(),
                        getValidDailyUpdateUriByBuyer(BUYER),
                        /*debuggable=*/ true);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_SUCCESS), anyInt());
    }

    @Test
    public void testImpl_runNormally_withAdRenderId() throws Exception {
        // Enable server auction Ad Render Ids
        mFetchCustomAudienceImpl =
                getImplWithFlags(
                        new FetchCustomAudienceFlags() {
                            @Override
                            public boolean getFledgeAuctionServerAdRenderIdEnabled() {
                                return true;
                            }
                        });

        // Respond with a complete custom audience with ad render ids including the request values
        // as is.
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(
                                                getFullSuccessfulJsonResponseStringWithAdRenderId(
                                                        BUYER))));

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        assertEquals(1, mockWebServer.getRequestCount());
        assertTrue(callback.mIsSuccess);
        verify(mCustomAudienceDaoMock)
                .insertOrOverwriteCustomAudience(
                        FetchCustomAudienceFixture
                                .getFullSuccessfulDBCustomAudienceWithAdRenderId(),
                        getValidDailyUpdateUriByBuyer(BUYER),
                        DevContext.createForDevOptionsDisabled().getDevOptionsEnabled());
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_SUCCESS), anyInt());
    }

    @Test
    public void testImpl_runNormally_withAdRenderId_AdRenderIdFlagDisabled() throws Exception {
        // Disable server auction Ad Render Ids
        mFetchCustomAudienceImpl =
                getImplWithFlags(
                        new FetchCustomAudienceFlags() {
                            @Override
                            public boolean getFledgeAuctionServerAdRenderIdEnabled() {
                                return false;
                            }
                        });

        // Respond with a complete custom audience with ad render ids including the request values
        // as is, but expect that ad render ids to not be read
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(
                                                getFullSuccessfulJsonResponseStringWithAdRenderId(
                                                        BUYER))));

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());

        ArgumentCaptor<DBCustomAudience> argumentDBCustomAudience =
                ArgumentCaptor.forClass(DBCustomAudience.class);

        assertEquals(1, mockWebServer.getRequestCount());
        assertTrue(callback.mIsSuccess);
        verify(mCustomAudienceDaoMock)
                .insertOrOverwriteCustomAudience(
                        argumentDBCustomAudience.capture(),
                        eq(getValidDailyUpdateUriByBuyer(BUYER)),
                        eq(DevContext.createForDevOptionsDisabled().getDevOptionsEnabled()));
        DBCustomAudience dbCustomAudience = argumentDBCustomAudience.getValue();
        Assert.assertNotEquals(
                FetchCustomAudienceFixture.getFullSuccessfulDBCustomAudienceWithAdRenderId(),
                dbCustomAudience);
        assertTrue(dbCustomAudience.getAds().stream().allMatch(ad -> ad.getAdRenderId() == null));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_SUCCESS), anyInt());
    }

    @Test
    public void testImpl_runNormally_differentResponsesToSameFetchUri() throws Exception {
        // Respond with a complete custom audience including the request values as is.
        JSONObject response1 = getFullSuccessfulJsonResponse(BUYER, false);
        Uri differentDailyUpdateUri = CommonFixture.getUri(BUYER, "/differentUpdate");
        JSONObject response2 =
                getFullSuccessfulJsonResponse(BUYER, false)
                        .put(DAILY_UPDATE_URI_KEY, differentDailyUpdateUri.toString());

        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            AtomicInteger mNumCalls = new AtomicInteger(0);

                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                if (mNumCalls.get() == 0) {
                                    mNumCalls.addAndGet(1);
                                    return new MockResponse().setBody(response1.toString());
                                } else if (mNumCalls.get() == 1) {
                                    mNumCalls.addAndGet(1);
                                    return new MockResponse().setBody(response2.toString());
                                } else {
                                    throw new IllegalStateException("Expected only 2 calls!");
                                }
                            }
                        });

        FetchCustomAudienceTestCallback callback1 = callFetchCustomAudience(mInputBuilder.build());
        assertTrue(callback1.mIsSuccess);
        verify(mCustomAudienceDaoMock)
                .insertOrOverwriteCustomAudience(
                        FetchCustomAudienceFixture.getFullSuccessfulDBCustomAudience(),
                        getValidDailyUpdateUriByBuyer(BUYER),
                        DevContext.createForDevOptionsDisabled().getDevOptionsEnabled());

        FetchCustomAudienceTestCallback callback2 = callFetchCustomAudience(mInputBuilder.build());
        assertTrue(callback2.mIsSuccess);
        verify(mCustomAudienceDaoMock)
                .insertOrOverwriteCustomAudience(
                        FetchCustomAudienceFixture.getFullSuccessfulDBCustomAudience(),
                        differentDailyUpdateUri,
                        DevContext.createForDevOptionsDisabled().getDevOptionsEnabled());

        verify(mAdServicesLoggerMock, times(2))
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_SUCCESS), anyInt());
    }

    @Test
    public void testImpl_requestRejectedByServer() throws Exception {
        // Respond with a 403
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse().setResponseCode(403)));

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());
        assertEquals(1, mockWebServer.getRequestCount());
        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_INTERNAL_ERROR, callback.mFledgeErrorResponse.getStatusCode());
        verify(mCustomAudienceDaoMock, times(/* wantedNumberOfInvocations= */ 0))
                .insertOrOverwriteCustomAudience(any(), any(), anyBoolean());
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_INTERNAL_ERROR), anyInt());
    }

    @Test
    public void testImpl_AddsToQuarantineTableWhenServerReturns429() throws Exception {
        // Respond with a 429
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse().setResponseCode(429)));

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());
        assertEquals(1, mockWebServer.getRequestCount());
        assertFalse(callback.mIsSuccess);
        assertEquals(
                STATUS_SERVER_RATE_LIMIT_REACHED, callback.mFledgeErrorResponse.getStatusCode());
        verify(mCustomAudienceDaoMock)
                .safelyInsertCustomAudienceQuarantine(
                        any(DBCustomAudienceQuarantine.class), anyLong());
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SERVER_RATE_LIMIT_REACHED),
                        anyInt());
    }

    @Test
    public void testImpl_ReturnsServerRateLimitReachedWhenEntryIsInQuarantineTable()
            throws Exception {
        doReturn(true)
                .when(mCustomAudienceDaoMock)
                .doesCustomAudienceQuarantineExist(VALID_OWNER, BUYER);
        // Return a time in the future so request gets filtered
        doReturn(CLOCK.instant().plusMillis(2 * DEFAULT_RETRY_AFTER_VALUE.toMillis()))
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceQuarantineExpiration(VALID_OWNER, BUYER);

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());
        assertFalse(callback.mIsSuccess);
        assertEquals(
                STATUS_SERVER_RATE_LIMIT_REACHED, callback.mFledgeErrorResponse.getStatusCode());
        verify(mCustomAudienceDaoMock).doesCustomAudienceQuarantineExist(VALID_OWNER, BUYER);
        verify(mCustomAudienceDaoMock).getCustomAudienceQuarantineExpiration(VALID_OWNER, BUYER);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SERVER_RATE_LIMIT_REACHED),
                        anyInt());
    }

    @Test
    public void testImpl_SucceedsAndRemovesEntryFromQuarantineTable() throws Exception {
        doReturn(true)
                .when(mCustomAudienceDaoMock)
                .doesCustomAudienceQuarantineExist(VALID_OWNER, BUYER);
        // Return a time in the past so request is not filtered
        doReturn(CLOCK.instant().minusMillis(2 * DEFAULT_RETRY_AFTER_VALUE.toMillis()))
                .when(mCustomAudienceDaoMock)
                .getCustomAudienceQuarantineExpiration(VALID_OWNER, BUYER);

        // Respond with a complete custom audience including the request values as is.
        MockWebServer mockWebServer =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse()
                                        .setBody(getFullSuccessfulJsonResponseString(BUYER))));

        FetchCustomAudienceTestCallback callback = callFetchCustomAudience(mInputBuilder.build());
        assertEquals(1, mockWebServer.getRequestCount());
        assertTrue(callback.mIsSuccess);

        verify(mCustomAudienceDaoMock).doesCustomAudienceQuarantineExist(VALID_OWNER, BUYER);
        verify(mCustomAudienceDaoMock).getCustomAudienceQuarantineExpiration(VALID_OWNER, BUYER);
        verify(mCustomAudienceDaoMock).deleteQuarantineEntry(VALID_OWNER, BUYER);
        verify(mCustomAudienceDaoMock)
                .insertOrOverwriteCustomAudience(
                        FetchCustomAudienceFixture.getFullSuccessfulDBCustomAudience(),
                        getValidDailyUpdateUriByBuyer(BUYER),
                        DevContext.createForDevOptionsDisabled().getDevOptionsEnabled());

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(API_NAME), eq(TEST_PACKAGE_NAME), eq(STATUS_SUCCESS), anyInt());
    }

    private void enableAuctionServerRequestFlags() {
        // Enable auction server request flags
        mFetchCustomAudienceImpl =
                getImplWithFlags(
                        new FetchCustomAudienceFlags() {
                            @Override
                            public boolean getFledgeAuctionServerRequestFlagsEnabled() {
                                return true;
                            }
                        });
    }

    private FetchCustomAudienceTestCallback callFetchCustomAudience(
            FetchAndJoinCustomAudienceInput input) throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        FetchCustomAudienceTestCallback callback = new FetchCustomAudienceTestCallback(resultLatch);
        mFetchCustomAudienceImpl.doFetchCustomAudience(
                input, callback, DevContext.createForDevOptionsDisabled());
        resultLatch.await();
        return callback;
    }

    private FetchCustomAudienceImpl getImplWithFlags(Flags flags) {
        return new FetchCustomAudienceImpl(
                flags,
                CLOCK,
                mAdServicesLoggerMock,
                DIRECT_EXECUTOR,
                mCustomAudienceDaoMock,
                mCallingAppUid,
                mCustomAudienceServiceFilterMock,
                mHttpClientSpy,
                mAdFilteringFeatureFactory.getFrequencyCapAdDataValidator(),
                mAdRenderIdValidator,
                AD_DATA_CONVERSION_STRATEGY);
    }

    public static class FetchCustomAudienceTestCallback
            extends FetchAndJoinCustomAudienceCallback.Stub {
        private final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        public FetchCustomAudienceTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        public boolean isSuccess() {
            return mIsSuccess;
        }

        @Override
        public void onSuccess() {
            LoggerFactory.getFledgeLogger()
                    .v("Reporting success to FetchCustomAudienceTestCallback.");
            mIsSuccess = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            LoggerFactory.getFledgeLogger()
                    .v("Reporting failure to FetchCustomAudienceTestCallback.");
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }

    private static class FetchCustomAudienceFlags implements Flags {
        @Override
        public boolean getFledgeFetchCustomAudienceEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeAdSelectionFilteringEnabled() {
            return true;
        }
    }
}
