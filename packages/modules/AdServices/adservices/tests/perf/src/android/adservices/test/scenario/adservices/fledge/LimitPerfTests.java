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

package android.adservices.test.scenario.adservices.fledge;

import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.TrustedBiddingData;
import android.net.Uri;
import android.platform.test.scenario.annotation.Scenario;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Scenario
@RunWith(JUnit4.class)
public class LimitPerfTests extends AbstractPerfTest {

    private static final String URL_PREFIX = "https://";
    private static final String BUYER = "localhost";

    @Test
    public void test_joinBigCustomAudience() throws Exception {
        joinAndLeaveNBigCas(1);
    }

    @Test
    public void test_join100BigCustomAudiences() throws Exception {
        // Will take 3+ minutes to run
        joinAndLeaveNBigCas(100);
    }

    @Test
    public void test_auctionBigCa() throws Exception {
        auctionNBigCas(1);
    }

    @Test
    public void test_auction10BigCas() throws Exception {
        auctionNBigCas(10);
    }

    @Test
    public void test_auction100BigCas() throws Exception {
        // Will take 3+ minutes to run
        auctionNBigCas(100);
    }

    @Test
    public void test_100Auctions100BigCas() throws Exception {
        // Will take 5+ minutes to run
        nAuctionsMBigCas(100, 100);
    }


    private void auctionNBigCas(int n) throws Exception {
        nAuctionsMBigCas(1, n);
    }

    private void nAuctionsMBigCas(int n, int m) throws Exception {
        List<CustomAudience> caList = createNBigCas(m);
        mMockWebServerRule.startMockWebServer(mDefaultDispatcher);
        joinAll(caList);

        for (int i = 0; i < n; i++) {
            AdSelectionOutcome outcome =
                    mAdSelectionClient
                            .selectAds(createAdSelectionConfig())
                            .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // Check that a valid URL won
            Assert.assertEquals(BUYER, outcome.getRenderUri().getHost());

            ReportImpressionRequest reportImpressionRequest =
                    new ReportImpressionRequest(
                            outcome.getAdSelectionId(), createAdSelectionConfig());
            // Performing reporting, and asserting that no exception is thrown
            mAdSelectionClient
                    .reportImpression(reportImpressionRequest)
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        leaveAll(caList);
    }

    private void joinAndLeaveNBigCas(int n) throws Exception {
        List<CustomAudience> caList = createNBigCas(n);
        joinAll(caList);
        leaveAll(caList);
    }

    private void joinAll(List<CustomAudience> caList) throws Exception {
        for (CustomAudience ca : caList) {
            mCustomAudienceClient
                    .joinCustomAudience(ca)
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private void leaveAll(List<CustomAudience> caList) throws Exception {
        for (CustomAudience ca : caList) {
            mCustomAudienceClient
                    .leaveCustomAudience(ca.getBuyer(), ca.getName())
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    private List<CustomAudience> createNBigCas(int n) {
        List<CustomAudience> caList = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            caList.add(createBigCustomAudience("" + i, i + 1));
        }
        return caList;
    }
    /**
     * Creates as large of a CA as possible with a name prefixed by nameStart
     *
     * @param nameStart The start of the CA name, the name will be padded out the maximum length
     * @param bid How much all the ads in the CA should bid
     * @return The large CA.
     */
    private CustomAudience createBigCustomAudience(String nameStart, int bid) {
        int minUrlSize = URL_PREFIX.length() + BUYER.length();
        Uri trustedBiddingUri =
                getValidTrustedBiddingUriByBuyer(AdTechIdentifier.fromString(BUYER));
        return new CustomAudience.Builder()
                // CA names are limited to 200 bytes
                .setName(nameStart + repeatCompatImpl("a", 200 - nameStart.length()))
                .setActivationTime(Instant.now())
                .setExpirationTime(Instant.now().plus(Duration.ofDays(1)))
                // Daily update and bidding URLS are limited to 400 bytes
                .setDailyUpdateUri(nBitUriFromAdtech(BUYER, 400))
                .setBiddingLogicUri(
                        nBitUriFromUri(
                                mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH), 400))
                // User bidding signals are limited to 10,000 bytes
                .setUserBiddingSignals(nBitAdSelectionSignals(10000))
                /* TrustedBidding signals are limited to 10 KiB. We're adding as many keys objects
                 * as possible to increase object overhead.
                 */
                .setTrustedBiddingData(
                        new TrustedBiddingData.Builder()
                                .setTrustedBiddingUri(trustedBiddingUri)
                                .setTrustedBiddingKeys(
                                        Collections.nCopies(
                                                10000 - trustedBiddingUri.toString().length(), "a"))
                                .build())
                .setBuyer(AdTechIdentifier.fromString(BUYER))
                // Maximum size is 10,000 bytes for each ad and 100 ads. Making 100 ads of size 100.
                .setAds(
                        Collections.nCopies(
                                100,
                                new AdData.Builder()
                                        .setRenderUri(nBitUriFromAdtech(BUYER, minUrlSize))
                                        .setMetadata(
                                                nBitJsonWithFields(
                                                        100 - minUrlSize, "\"result\": " + bid))
                                        .build()))
                .build();
    }

    private AdSelectionSignals nBitAdSelectionSignals(int n) {
        return AdSelectionSignals.fromString(nBitJson(n));
    }

    private String nBitJson(int n) {
        if (n < 8) {
            throw new IllegalArgumentException("n too small");
        }
        return "{\"a\":\"" + repeatCompatImpl("a", n - 8) + "\"}";
    }

    private String nBitJsonWithFields(int n, String fields) {
        if (n < 8) {
            throw new IllegalArgumentException("n too small");
        }

        return "{"
                + fields
                + ",\"a\":\""
                + repeatCompatImpl("a", n - (9 + fields.length()))
                + "\"}";
    }

    private Uri nBitUriFromAdtech(String adtech, int n) {
        String uriStart = URL_PREFIX + adtech;
        if (n < uriStart.length()) {
            throw new IllegalArgumentException("n too small ");
        } else if (n == uriStart.length()) {
            return Uri.parse(uriStart);
        } else {
            return Uri.parse(uriStart + "#" + repeatCompatImpl("a", n - 3 - uriStart.length()));
        }
    }

    private Uri nBitUriFromUri(Uri uri, int n) {
        if (n < uri.toString().length()) {
            throw new IllegalArgumentException("n too small ");
        } else if (n == uri.toString().length()) {
            return uri;
        } else {
            return Uri.parse(uri + "#" + repeatCompatImpl("a", n - 3 - uri.toString().length()));
        }
    }

    /**
     * Since we run the test on both Android S and T, this util method provides a
     * backward-compatible way to concatenate a string N times without using Java 11 repeat String
     * method.
     */
    private static String repeatCompatImpl(String str, int numTimes) {
        StringBuilder sb = new StringBuilder();

        for (int num = 0; num < numTimes; num++) {
            sb.append(str);
        }

        return sb.toString();
    }
}
