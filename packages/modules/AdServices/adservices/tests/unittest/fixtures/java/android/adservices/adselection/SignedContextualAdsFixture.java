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

import static com.android.adservices.service.adselection.signature.ProtectedAudienceSignatureManager.PRIVATE_TEST_KEY_STRING;

import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.adselection.signature.SignedContextualAdsHashUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * This is a static class meant to help with tests that involve creating an {@link
 * SignedContextualAds}.
 */
public class SignedContextualAdsFixture {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private static final byte[] PLACEHOLDER_EMPTY_SIGNATURE = new byte[] {};
    public static final AdTechIdentifier BUYER = CommonFixture.VALID_BUYER_1;
    public static final AdTechIdentifier BUYER_2 = CommonFixture.VALID_BUYER_2;

    // Uri Constants
    public static final String DECISION_LOGIC_FRAGMENT = "/decisionFragment";

    public static final Uri DECISION_LOGIC_URI =
            CommonFixture.getUri(BUYER, DECISION_LOGIC_FRAGMENT);

    private static final AdData VALID_AD_DATA = AdDataFixture.getValidAdDataByBuyer(BUYER, 0);
    private static final double TEST_BID = 0.1;

    public static final AdWithBid AD_WITH_BID_1 = new AdWithBid(VALID_AD_DATA, TEST_BID);
    public static final AdWithBid AD_WITH_BID_2 = new AdWithBid(VALID_AD_DATA, TEST_BID * 2);
    public static final List<AdWithBid> ADS_WITH_BID =
            ImmutableList.of(AD_WITH_BID_1, AD_WITH_BID_2);

    /**
     * Returns a {@link SignedContextualAds} object with a placeholder signature
     *
     * <p>This object's signature will not pass the verification
     */
    public static SignedContextualAds.Builder aContextualAdsWithEmptySignatureBuilder() {
        return new SignedContextualAds.Builder()
                .setBuyer(BUYER)
                .setDecisionLogicUri(DECISION_LOGIC_URI)
                .setAdsWithBid(ADS_WITH_BID)
                .setSignature(PLACEHOLDER_EMPTY_SIGNATURE);
    }

    /**
     * Returns a {@link SignedContextualAds} object with a placeholder signature and the given
     * buyer.
     *
     * <p>This object's signature will not pass the verification
     */
    public static SignedContextualAds.Builder aContextualAdsWithEmptySignatureBuilder(
            AdTechIdentifier buyer) {
        return aContextualAdsWithEmptySignatureBuilder()
                .setBuyer(buyer)
                .setDecisionLogicUri(CommonFixture.getUri(buyer, DECISION_LOGIC_FRAGMENT));
    }

    /**
     * Returns a {@link SignedContextualAds} object with a placeholder signature with given buyer
     * and bids.
     *
     * <p>This object's signature will not pass the verification
     */
    public static SignedContextualAds.Builder aContextualAdsWithEmptySignatureBuilder(
            AdTechIdentifier buyer, List<Double> bids) {
        List<AdWithBid> adsWithBid =
                bids.stream()
                        .map(
                                bid ->
                                        new AdWithBid(
                                                AdDataFixture.getValidFilterAdDataByBuyer(
                                                        buyer, bid.intValue()),
                                                bid))
                        .collect(Collectors.toList());
        return aContextualAdsWithEmptySignatureBuilder(buyer).setAdsWithBid(adsWithBid);
    }

    /**
     * Returns a {@link SignedContextualAds} object that is signed.
     *
     * <p>This object's signature can be verified using {@link SignedContextualAdsFixture
     * #PUBLIC_KEY_STRING}.
     */
    public static SignedContextualAds aSignedContextualAds() {
        return signContextualAds(aContextualAdsWithEmptySignatureBuilder());
    }

    /**
     * Returns a {@link SignedContextualAds} object that is signed with given buyer.
     *
     * <p>This object's signature can be verified using {@link SignedContextualAdsFixture
     * #PUBLIC_KEY_STRING}.
     */
    public static SignedContextualAds aSignedContextualAds(AdTechIdentifier buyer) {
        return signContextualAds(aContextualAdsWithEmptySignatureBuilder(buyer));
    }

    /**
     * Returns a {@link SignedContextualAds} object that is signed with given buyer.
     *
     * <p>This object's signature can be verified using {@link SignedContextualAdsFixture
     * #PUBLIC_KEY_STRING}.
     */
    public static SignedContextualAds aSignedContextualAds(
            AdTechIdentifier buyer, List<Double> bids) {
        return signContextualAds(aContextualAdsWithEmptySignatureBuilder(buyer, bids));
    }

    public static ImmutableMap<AdTechIdentifier, SignedContextualAds>
            getBuyerSignedContextualAdsMap() {
        return ImmutableMap.of(
                CommonFixture.VALID_BUYER_1,
                aSignedContextualAds(CommonFixture.VALID_BUYER_1),
                CommonFixture.VALID_BUYER_2,
                aSignedContextualAds(CommonFixture.VALID_BUYER_2));
    }

    /**
     * Signs contextual ads using {@link
     * com.android.adservices.service.adselection.signature.ProtectedAudienceSignatureManager
     * #PRIVATE_KEY_STRING}.
     *
     * <p>Bundle can be verified using {@link
     * com.android.adservices.service.adselection.signature.ProtectedAudienceSignatureManager
     * #PUBLIC_KEY_STRING}
     */
    public static SignedContextualAds signContextualAds(
            SignedContextualAds.Builder notSignedContextualAdsBuilder) {
        SignedContextualAds signedContextualAds;
        try {
            Signature ecdsaSigner = getECDSASignatureInstance();
            ecdsaSigner.update(
                    new SignedContextualAdsHashUtil()
                            .serialize(notSignedContextualAdsBuilder.build()));
            signedContextualAds =
                    notSignedContextualAdsBuilder.setSignature(ecdsaSigner.sign()).build();
        } catch (Exception e) {
            String errMsg =
                    String.format(
                            "Something went wrong during signing a contextual ad bundle: %s", e);
            sLogger.v(errMsg);
            throw new RuntimeException(errMsg, e);
        }
        return signedContextualAds;
    }

    private static Signature getECDSASignatureInstance() throws Exception {
        byte[] privateKeyBytes = Base64.getDecoder().decode(PRIVATE_TEST_KEY_STRING);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(privateKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        PrivateKey privateKey = keyFactory.generatePrivate(spec);
        Signature ecdsaSign = Signature.getInstance("SHA256withECDSA");
        ecdsaSign.initSign(privateKey);
        return ecdsaSign;
    }
}
