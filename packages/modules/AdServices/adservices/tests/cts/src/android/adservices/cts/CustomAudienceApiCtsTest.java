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

package android.adservices.cts;

import static android.adservices.common.AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE;
import static android.adservices.common.CommonFixture.VALID_BUYER_1;
import static android.adservices.customaudience.CustomAudience.FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS;
import static android.adservices.customaudience.CustomAudienceFixture.INVALID_BEYOND_MAX_EXPIRATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.INVALID_DELAYED_ACTIVATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_ACTIVATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_EXPIRATION_TIME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_NAME;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS;
import static android.adservices.customaudience.CustomAudienceFixture.getValidFetchUriByBuyer;

import static com.android.adservices.service.FlagsConstants.KEY_ENABLE_ENROLLMENT_TEST_SEED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NAME_SIZE_B;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED;
import static com.android.adservices.service.FlagsConstants.KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.adservices.clients.customaudience.AdvertisingCustomAudienceClient;
import android.adservices.clients.customaudience.TestAdvertisingCustomAudienceClient;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.AddCustomAudienceOverrideRequest;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.FetchAndJoinCustomAudienceRequest;
import android.adservices.customaudience.RemoveCustomAudienceOverrideRequest;
import android.adservices.customaudience.TrustedBiddingData;
import android.adservices.customaudience.TrustedBiddingDataFixture;
import android.net.Uri;
import android.os.Process;
import android.util.Pair;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.adservices.common.AdServicesFlagsSetterRule;
import com.android.adservices.common.AdservicesTestHelper;
import com.android.adservices.common.RequiresSdkLevelAtLeastS;
import com.android.adservices.common.annotations.SetFlagEnabled;
import com.android.adservices.common.annotations.SetIntegerFlag;
import com.android.adservices.service.FlagsConstants;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RequiresSdkLevelAtLeastS // TODO(b/291488819) - Remove SDK Level check if Fledge is enabled on R.
@SetFlagEnabled(KEY_ENABLE_ENROLLMENT_TEST_SEED)
public final class CustomAudienceApiCtsTest extends ForegroundCtsTestCase {

    private AdvertisingCustomAudienceClient mClient;
    private TestAdvertisingCustomAudienceClient mTestClient;

    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("buyer");
    private static final String NAME = "name";
    private static final String BIDDING_LOGIC_JS = "function test() { return \"hello world\"; }";
    private static final AdSelectionSignals TRUSTED_BIDDING_DATA =
            AdSelectionSignals.fromString("{\"trusted_bidding_data\":1}");

    private boolean mIsDebugMode;

    private final ArrayList<Pair<AdTechIdentifier, String>> mCustomAudiencesToCleanUp =
            new ArrayList<>();

    @Override
    protected AdServicesFlagsSetterRule getAdServicesFlagsSetterRule() {
        return AdServicesFlagsSetterRule.withAllLogcatTags()
                .setGlobalKillSwitch(false)
                .setFlag(FlagsConstants.KEY_FLEDGE_CUSTOM_AUDIENCE_SERVICE_KILL_SWITCH, false)
                .setSystemProperty(FlagsConstants.KEY_CONSENT_MANAGER_DEBUG_MODE, true)
                .setPpapiAppAllowList(mPackageName);
    }

    @Before
    public void setup() throws Exception {
        if (sdkLevel.isAtLeastT()) {
            assertForegroundActivityStarted();
        }

        mClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(MoreExecutors.directExecutor())
                        .build();
        mTestClient =
                new TestAdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(MoreExecutors.directExecutor())
                        .build();
        DevContext devContext = DevContextFilter.create(sContext).createDevContext(Process.myUid());
        mIsDebugMode = devContext.getDevOptionsEnabled();

        // Needed to test different custom audience limits
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.WRITE_DEVICE_CONFIG);

        // Kill AdServices process
        AdservicesTestHelper.killAdservicesProcess(sContext);
    }

    @After
    public void tearDown() throws Exception {
        leaveJoinedCustomAudiences();
    }

    @Test
    public void testJoinCustomAudience_validCustomAudience_success()
            throws ExecutionException, InterruptedException, TimeoutException {
        joinCustomAudience(CustomAudienceFixture.getValidBuilderForBuyer(VALID_BUYER_1).build());
    }

    @Test
    public void testJoinCustomAudience_validCustomAudience_successWithAuctionServerRequestFlags()
            throws ExecutionException, InterruptedException, TimeoutException {
        joinCustomAudience(
                CustomAudienceFixture.getValidBuilderByBuyerWithAuctionServerRequestFlags(
                                VALID_BUYER_1, FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                        .build());
    }

    @Test
    public void testJoinCustomAudience_validCustomAudience_success_usingGetMethodToCreateManager()
            throws ExecutionException, InterruptedException, TimeoutException {
        // Override mClient with a new value that explicitly uses the Get method to create manager
        createClientUsingGetMethod();
        testJoinCustomAudience_validCustomAudience_success();
    }

    @Test
    public void testJoinCustomAudience_withMissingEnrollment_fail() {
        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                joinCustomAudience(
                                        CustomAudienceFixture.getValidBuilderForBuyer(
                                                        CommonFixture.NOT_ENROLLED_BUYER)
                                                .build()));
        assertThat(exception).hasCauseThat().isInstanceOf(SecurityException.class);
        assertThat(exception)
                .hasCauseThat()
                .hasMessageThat()
                .isEqualTo(SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE);
    }

    @Test
    public void testJoinCustomAudience_withMissingEnrollment_fail_usingGetMethodToCreateManager() {
        // Override mClient with a new value that explicitly uses the Get method to create manager
        createClientUsingGetMethod();
        testJoinCustomAudience_withMissingEnrollment_fail();
    }

    @Test
    public void testJoinCustomAudience_withValidSubdomains_success() throws Exception {
        joinCustomAudience(
                CustomAudienceFixture.getValidBuilderWithSubdomainsForBuyer(VALID_BUYER_1).build());
    }

    @Test
    public void testJoinCustomAudience_withValidSubdomains_success_usingGetMethodToCreateManager()
            throws Exception {
        // Override mClient with a new value that explicitly uses the Get method to create manager
        createClientUsingGetMethod();
        testJoinCustomAudience_withValidSubdomains_success();
    }

    @Test
    public void testJoinCustomAudience_withManyValidSubdomains_success() throws Exception {
        joinCustomAudience(
                CustomAudienceFixture.getValidBuilderWithSubdomainsForBuyer(
                                CommonFixture.VALID_BUYER_1)
                        .setBiddingLogicUri(
                                CommonFixture.getUriWithGivenSubdomain(
                                        "bidding.subdomain",
                                        CommonFixture.VALID_BUYER_1.toString(),
                                        "/bidding/logic"))
                        .setDailyUpdateUri(
                                CommonFixture.getUriWithGivenSubdomain(
                                        "daily.update.subdomain",
                                        CommonFixture.VALID_BUYER_1.toString(),
                                        "/daily/update"))
                        .setTrustedBiddingData(
                                new TrustedBiddingData.Builder()
                                        .setTrustedBiddingUri(
                                                CommonFixture.getUriWithGivenSubdomain(
                                                        "trusted.bidding",
                                                        CommonFixture.VALID_BUYER_1.toString(),
                                                        "/bidding/trusted"))
                                        .setTrustedBiddingKeys(
                                                TrustedBiddingDataFixture
                                                        .VALID_TRUSTED_BIDDING_KEYS)
                                        .build())
                        .build());
    }

    @Test
    public void
            testJoinCustomAudience_withManyValidSubdomains_success_usingGetMethodToCreateManager()
                    throws Exception {
        // Override mClient with a new value that explicitly uses the Get method to create manager
        createClientUsingGetMethod();
        testJoinCustomAudience_withManyValidSubdomains_success();
    }

    @Test
    public void testJoinCustomAudience_invalidAdsMetadata_fail() {
        CustomAudience customAudienceWithInvalidAdDataMetadata =
                CustomAudienceFixture.getValidBuilderForBuyer(VALID_BUYER_1)
                        .setAds(AdDataFixture.getInvalidAdsByBuyer(VALID_BUYER_1))
                        .build();

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> joinCustomAudience(customAudienceWithInvalidAdDataMetadata));
        assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
        assertThat(exception).hasCauseThat().hasMessageThat().isEqualTo(null);
    }

    @Test
    public void testJoinCustomAudience_invalidAdsMetadata_fail_usingGetMethodToCreateManager() {
        // Override mClient with a new value that explicitly uses the Get method to create manager
        createClientUsingGetMethod();
        testJoinCustomAudience_invalidAdsMetadata_fail();
    }

    @Test
    public void testJoinCustomAudience_invalidAdsRenderUris_fail() {
        CustomAudience customAudienceWithInvalidAdDataRenderUris =
                CustomAudienceFixture.getValidBuilderForBuyer(VALID_BUYER_1)
                        .setAds(
                                AdDataFixture.getInvalidAdsByBuyer(
                                        AdTechIdentifier.fromString(
                                                "!\\@#\"$#@NOTAREALURI$%487\\")))
                        .build();

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> joinCustomAudience(customAudienceWithInvalidAdDataRenderUris));
        assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
        assertThat(exception).hasCauseThat().hasMessageThat().isEqualTo(null);
    }

    @Test
    @SetIntegerFlag(name = KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NUM_ADS, value = 2)
    public void testJoinCustomAudience_invalidNumberOfAds_fail() {
        CustomAudience customAudienceWithInvalidNumberOfAds =
                CustomAudienceFixture.getValidBuilderForBuyer(VALID_BUYER_1)
                        .setAds(
                                ImmutableList.of(
                                        AdDataFixture.getValidAdDataByBuyer(VALID_BUYER_1, 1),
                                        AdDataFixture.getValidAdDataByBuyer(VALID_BUYER_1, 2),
                                        AdDataFixture.getValidAdDataByBuyer(VALID_BUYER_1, 3)))
                        .build();

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> joinCustomAudience(customAudienceWithInvalidNumberOfAds));
        assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
        assertThat(exception).hasCauseThat().hasMessageThat().isNull();
    }

    @Test
    public void testJoinCustomAudience_mismatchDailyFetchUriDomain_fail() {
        CustomAudience customAudienceWithMismatchedDailyFetchUriDomain =
                CustomAudienceFixture.getValidBuilderForBuyer(VALID_BUYER_1)
                        .setDailyUpdateUri(
                                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                        CommonFixture.VALID_BUYER_2))
                        .build();

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> joinCustomAudience(customAudienceWithMismatchedDailyFetchUriDomain));
        assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
        assertThat(exception).hasCauseThat().hasMessageThat().isEqualTo(null);
    }

    @Test
    public void testJoinCustomAudience_illegalExpirationTime_fail() {
        CustomAudience customAudience =
                CustomAudienceFixture.getValidBuilderForBuyer(VALID_BUYER_1)
                        .setExpirationTime(CustomAudienceFixture.INVALID_BEYOND_MAX_EXPIRATION_TIME)
                        .build();
        Exception exception =
                assertThrows(ExecutionException.class, () -> joinCustomAudience(customAudience));
        assertTrue(exception.getCause() instanceof IllegalArgumentException);
        assertThat(exception).hasCauseThat().hasMessageThat().isEqualTo(null);
    }

    @Test
    @SetIntegerFlag(name = KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT, value = 2)
    @SetIntegerFlag(name = KEY_FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT, value = 1000)
    @SetIntegerFlag(name = KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT, value = 1000)
    public void testJoinCustomAudience_maxTotalCustomAudiences_fail() {
        CustomAudience customAudience1 =
                CustomAudienceFixture.getValidBuilderForBuyer(VALID_BUYER_1).setName("CA1").build();
        CustomAudience customAudience2 =
                CustomAudienceFixture.getValidBuilderForBuyer(VALID_BUYER_1).setName("CA2").build();
        CustomAudience customAudience3 =
                CustomAudienceFixture.getValidBuilderForBuyer(VALID_BUYER_1).setName("CA3").build();

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            joinCustomAudience(customAudience1);
                            joinCustomAudience(customAudience2);
                            joinCustomAudience(customAudience3);
                        });
        assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
        assertThat(exception).hasCauseThat().hasMessageThat().isNull();
    }

    @Test
    @SetIntegerFlag(name = KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_COUNT, value = 4000)
    @SetIntegerFlag(name = KEY_FLEDGE_CUSTOM_AUDIENCE_PER_APP_MAX_COUNT, value = 2)
    @SetIntegerFlag(name = KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_OWNER_COUNT, value = 1000)
    public void testJoinCustomAudience_maxCustomAudiencesPerApp_fail() {
        CustomAudience customAudience1 =
                CustomAudienceFixture.getValidBuilderForBuyer(VALID_BUYER_1).setName("CA1").build();
        CustomAudience customAudience2 =
                CustomAudienceFixture.getValidBuilderForBuyer(VALID_BUYER_1).setName("CA2").build();
        CustomAudience customAudience3 =
                CustomAudienceFixture.getValidBuilderForBuyer(VALID_BUYER_1).setName("CA3").build();

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            joinCustomAudience(customAudience1);
                            joinCustomAudience(customAudience2);
                            joinCustomAudience(customAudience3);
                        });
        assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
        assertThat(exception).hasCauseThat().hasMessageThat().isNull();
    }

    @Test
    @Ignore("b/319330548")
    public void testFetchAndJoinCustomAudience_validFetchUri_validRequest() {
        // NOTE: not using flag annotations because it's called by other test
        flags.setFlag(KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED, true);
        FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(
                                getValidFetchUriByBuyer(VALID_BUYER_1))
                        .build();

        // Without an actual server to respond to this request, the service will fail while
        // executing the HTTP request and throw an IllegalStateException. If a request field was
        // invalid, the service will fail before executing the HTTP request and throw an
        // IllegalArgumentException.
        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchAndJoinCustomAudience(request, VALID_BUYER_1, VALID_NAME));
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Ignore("b/319330548")
    public void testFetchAndJoinCustomAudience_validFetchUri_validRequest_getMethod() {
        createClientUsingGetMethod();
        testFetchAndJoinCustomAudience_validFetchUri_validRequest();
    }

    @Test
    @Ignore("b/319330548")
    public void testFetchAndJoinCustomAudience_unenrolledFetchUri_invalidRequest() {
        // NOTE: not using flag annotations because it's called by other test
        flags.setFlag(KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED, true);
        FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(Uri.parse("invalid-uri.com")).build();

        // Without an actual server to respond to this request, the service will fail while
        // executing the HTTP request and throw an IllegalStateException. If a request field was
        // invalid, the service will fail before executing the HTTP request and throw an
        // IllegalArgumentException.
        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchAndJoinCustomAudience(request, VALID_BUYER_1, VALID_NAME));
        // A valid buyer will not be extracted from an invalid uri, thus failing due to lack of
        // authorization.
        assertThat(exception).hasCauseThat().isInstanceOf(SecurityException.class);
        assertThat(exception)
                .hasCauseThat()
                .hasMessageThat()
                .isEqualTo(SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE);
    }

    @Test
    @Ignore("b/319330548")
    public void testFetchAndJoinCustomAudience_unenrolledFetchUri_invalidRequest_getMethod() {
        createClientUsingGetMethod();
        testFetchAndJoinCustomAudience_unenrolledFetchUri_invalidRequest();
    }

    @Test
    @Ignore("b/319330548")
    public void testFetchAndJoinCustomAudience_validName_validRequest() {
        // NOTE: not using flag annotations because it's called by other test
        flags.setFlag(KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED, true);
        FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(
                                getValidFetchUriByBuyer(VALID_BUYER_1))
                        .setName(VALID_NAME)
                        .build();

        // Without an actual server to respond to this request, the service will fail while
        // executing the HTTP request and throw an IllegalStateException. If a request field was
        // invalid, the service will fail before executing the HTTP request and throw an
        // IllegalArgumentException.
        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchAndJoinCustomAudience(request, VALID_BUYER_1, VALID_NAME));
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Ignore("b/319330548")
    public void testFetchAndJoinCustomAudience_validName_validRequest_getMethod() {
        createClientUsingGetMethod();
        testFetchAndJoinCustomAudience_validName_validRequest();
    }

    @Test
    @Ignore("b/319330548")
    public void testFetchAndJoinCustomAudience_tooLongName_invalidRequest() {
        // NOTE: not using flag annotations because it's called by other test
        flags.setFlag(KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED, true);
        // Use a clearly small size limit.
        flags.setFlag(KEY_FLEDGE_CUSTOM_AUDIENCE_MAX_NAME_SIZE_B, 1);
        FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(
                                getValidFetchUriByBuyer(VALID_BUYER_1))
                        .setName(VALID_NAME)
                        .build();

        // Without an actual server to respond to this request, the service will fail while
        // executing the HTTP request and throw an IllegalStateException. If a request field was
        // invalid, the service will fail before executing the HTTP request and throw an
        // IllegalArgumentException.
        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchAndJoinCustomAudience(request, VALID_BUYER_1, VALID_NAME));
        // The name exceeds size limit.
        assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Ignore("b/319330548")
    public void testFetchAndJoinCustomAudience_tooLongName_invalidRequest_getMethod() {
        createClientUsingGetMethod();
        testFetchAndJoinCustomAudience_tooLongName_invalidRequest();
    }

    @Test
    @Ignore("b/319330548")
    public void testFetchAndJoinCustomAudience_validActivationTime_validRequest() {
        // NOTE: not using flag annotations because it's called by other test
        flags.setFlag(KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED, true);

        FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(
                                getValidFetchUriByBuyer(VALID_BUYER_1))
                        .setActivationTime(VALID_ACTIVATION_TIME)
                        .build();

        // Without an actual server to respond to this request, the service will fail while
        // executing the HTTP request and throw an IllegalStateException. If a request field was
        // invalid, the service will fail before executing the HTTP request and throw an
        // IllegalArgumentException.
        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchAndJoinCustomAudience(request, VALID_BUYER_1, VALID_NAME));
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Ignore("b/319330548")
    public void testFetchAndJoinCustomAudience_validActivationTime_validRequest_getMethod() {
        createClientUsingGetMethod();
        testFetchAndJoinCustomAudience_validActivationTime_validRequest();
    }

    @Test
    @Ignore("b/319330548")
    public void testFetchAndJoinCustomAudience_activationExceedsDelay_invalidRequest() {
        // NOTE: not using flag annotations because it's called by other test
        flags.setFlag(KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED, true);
        FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(
                                getValidFetchUriByBuyer(VALID_BUYER_1))
                        .setActivationTime(INVALID_DELAYED_ACTIVATION_TIME)
                        .build();

        // Without an actual server to respond to this request, the service will fail while
        // executing the HTTP request and throw an IllegalStateException. If a request field was
        // invalid, the service will fail before executing the HTTP request and throw an
        // IllegalArgumentException.
        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchAndJoinCustomAudience(request, VALID_BUYER_1, VALID_NAME));
        // The activation time exceeds delay limit.
        assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Ignore("b/319330548")
    public void testFetchAndJoinCustomAudience_activationExceedsDelay_invalidRequest_getMethod() {
        createClientUsingGetMethod();
        testFetchAndJoinCustomAudience_activationExceedsDelay_invalidRequest();
    }

    @Test
    @Ignore("b/319330548")
    public void testFetchAndJoinCustomAudience_validExpirationTime_validRequest() {
        // NOTE: not using flag annotations because it's called by other test
        flags.setFlag(KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED, true);
        FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(
                                getValidFetchUriByBuyer(VALID_BUYER_1))
                        .setExpirationTime(VALID_EXPIRATION_TIME)
                        .build();

        // Without an actual server to respond to this request, the service will fail while
        // executing the HTTP request and throw an IllegalStateException. If a request field was
        // invalid, the service will fail before executing the HTTP request and throw an
        // IllegalArgumentException.
        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchAndJoinCustomAudience(request, VALID_BUYER_1, VALID_NAME));
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Ignore("b/319330548")
    public void testFetchAndJoinCustomAudience_validExpirationTime_validRequest_getMethod() {
        createClientUsingGetMethod();
        testFetchAndJoinCustomAudience_validExpirationTime_validRequest();
    }

    @Test
    @Ignore("b/319330548")
    public void testFetchAndJoinCustomAudience_beyondMaxExpiration_invalidRequest() {
        // NOTE: not using flag annotations because it's called by other test
        flags.setFlag(KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED, true);
        FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(
                                getValidFetchUriByBuyer(VALID_BUYER_1))
                        .setExpirationTime(INVALID_BEYOND_MAX_EXPIRATION_TIME)
                        .build();

        // Without an actual server to respond to this request, the service will fail while
        // executing the HTTP request and throw an IllegalStateException. If a request field was
        // invalid, the service will fail before executing the HTTP request and throw an
        // IllegalArgumentException.
        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchAndJoinCustomAudience(request, VALID_BUYER_1, VALID_NAME));
        // The expiration time exceeds max limit.
        assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Ignore("b/319330548")
    public void testFetchAndJoinCustomAudience_beyondMaxExpiration_invalidRequest_getMethod() {
        createClientUsingGetMethod();
        testFetchAndJoinCustomAudience_beyondMaxExpiration_invalidRequest();
    }

    @Test
    @Ignore("b/319330548")
    public void testFetchAndJoinCustomAudience_validUserBiddingSignals_validRequest() {
        // NOTE: not using flag annotations because it's called by other test
        flags.setFlag(KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED, true);
        FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(
                                getValidFetchUriByBuyer(VALID_BUYER_1))
                        .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                        .build();

        // Without an actual server to respond to this request, the service will fail while
        // executing the HTTP request and throw an IllegalStateException. If a request field was
        // invalid, the service will fail before executing the HTTP request and throw an
        // IllegalArgumentException.
        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchAndJoinCustomAudience(request, VALID_BUYER_1, VALID_NAME));
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @Ignore("b/319330548")
    public void testFetchAndJoinCustomAudience_validUserBiddingSignals_validRequest_getMethod() {
        createClientUsingGetMethod();
        testFetchAndJoinCustomAudience_validUserBiddingSignals_validRequest();
    }

    @Test
    @Ignore("b/319330548")
    public void testFetchAndJoinCustomAudience_tooBigUserBiddingSignals_invalidRequest() {
        // NOTE: not using flag annotations because it's called by other test
        flags.setFlag(KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_ENABLED, true);
        // Use a clearly small size limit.
        flags.setFlag(KEY_FLEDGE_FETCH_CUSTOM_AUDIENCE_MAX_USER_BIDDING_SIGNALS_SIZE_B, 1);
        FetchAndJoinCustomAudienceRequest request =
                new FetchAndJoinCustomAudienceRequest.Builder(
                                getValidFetchUriByBuyer(VALID_BUYER_1))
                        .setUserBiddingSignals(VALID_USER_BIDDING_SIGNALS)
                        .build();

        // Without an actual server response, we expect an IllegalStateException if the request
        // was well-formed and valid.
        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> fetchAndJoinCustomAudience(request, VALID_BUYER_1, VALID_NAME));
        // The user bidding signals exceeds size limit.
        assertThat(exception).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Ignore("b/319330548")
    public void testFetchAndJoinCustomAudience_tooBigUserBiddingSignals_invalidRequest_getMethod() {
        createClientUsingGetMethod();
        testFetchAndJoinCustomAudience_tooBigUserBiddingSignals_invalidRequest();
    }

    @Test
    public void testLeaveCustomAudience_joinedCustomAudience_success()
            throws ExecutionException, InterruptedException, TimeoutException {
        joinCustomAudience(CustomAudienceFixture.getValidBuilderForBuyer(VALID_BUYER_1).build());
        mClient.leaveCustomAudience(VALID_BUYER_1, CustomAudienceFixture.VALID_NAME).get();
    }

    @Test
    public void testLeaveCustomAudience_notJoinedCustomAudience_doesNotFail()
            throws ExecutionException, InterruptedException {
        mClient.leaveCustomAudience(VALID_BUYER_1, "not_exist_name").get();
    }

    @Test
    public void testLeaveCustomAudience_withMissingEnrollment_fail() {
        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                mClient.leaveCustomAudience(
                                                CommonFixture.NOT_ENROLLED_BUYER,
                                                CustomAudienceFixture.VALID_NAME)
                                        .get());
        assertThat(exception).hasCauseThat().isInstanceOf(SecurityException.class);
        assertThat(exception)
                .hasCauseThat()
                .hasMessageThat()
                .isEqualTo(SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE);
    }

    @Test
    public void testAddOverrideFailsWithDebugModeDisabled() {
        Assume.assumeFalse(mIsDebugMode);

        AddCustomAudienceOverrideRequest request =
                new AddCustomAudienceOverrideRequest.Builder()
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .setBiddingLogicJs(BIDDING_LOGIC_JS)
                        .setTrustedBiddingSignals(TRUSTED_BIDDING_DATA)
                        .build();

        ListenableFuture<Void> result = mTestClient.overrideCustomAudienceRemoteInfo(request);

        Exception exception =
                assertThrows(ExecutionException.class, () -> result.get(10, TimeUnit.SECONDS));
        assertThat(exception.getCause()).isInstanceOf(SecurityException.class);
    }

    @Test
    public void testRemoveOverrideFailsWithDebugModeDisabled() {
        Assume.assumeFalse(mIsDebugMode);

        RemoveCustomAudienceOverrideRequest request =
                new RemoveCustomAudienceOverrideRequest.Builder()
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .build();

        ListenableFuture<Void> result = mTestClient.removeCustomAudienceRemoteInfoOverride(request);

        Exception exception =
                assertThrows(ExecutionException.class, () -> result.get(10, TimeUnit.SECONDS));
        assertThat(exception.getCause()).isInstanceOf(SecurityException.class);
    }

    @Test
    public void testResetAllOverridesFailsWithDebugModeDisabled() {
        Assume.assumeFalse(mIsDebugMode);

        ListenableFuture<Void> result = mTestClient.resetAllCustomAudienceOverrides();

        Exception exception =
                assertThrows(ExecutionException.class, () -> result.get(10, TimeUnit.SECONDS));
        assertThat(exception.getCause()).isInstanceOf(SecurityException.class);
    }

    private void joinCustomAudience(CustomAudience customAudience)
            throws ExecutionException, InterruptedException, TimeoutException {
        mClient.joinCustomAudience(customAudience).get(10, TimeUnit.SECONDS);
        mCustomAudiencesToCleanUp.add(
                new Pair<>(customAudience.getBuyer(), customAudience.getName()));
    }

    private void fetchAndJoinCustomAudience(
            FetchAndJoinCustomAudienceRequest request, AdTechIdentifier buyer, String name)
            throws ExecutionException, InterruptedException, TimeoutException {
        mClient.fetchAndJoinCustomAudience(request).get(10, TimeUnit.SECONDS);
        mCustomAudiencesToCleanUp.add(new Pair<>(buyer, name));
    }

    private void leaveJoinedCustomAudiences()
            throws ExecutionException, InterruptedException, TimeoutException {
        try {
            for (Pair<AdTechIdentifier, String> map : mCustomAudiencesToCleanUp) {
                mClient.leaveCustomAudience(map.first, map.second).get(10, TimeUnit.SECONDS);
            }
        } finally {
            mCustomAudiencesToCleanUp.clear();
        }
    }

    private void createClientUsingGetMethod() {
        mClient =
                new AdvertisingCustomAudienceClient.Builder()
                        .setContext(sContext)
                        .setExecutor(MoreExecutors.directExecutor())
                        .setUseGetMethodToCreateManagerInstance(true)
                        .build();
    }
}
