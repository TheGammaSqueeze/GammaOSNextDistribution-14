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

import static com.android.adservices.data.adselection.EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_AUCTION;

import com.android.adservices.data.adselection.DBEncryptionKey;
import com.android.adservices.data.adselection.DBProtectedServersEncryptionConfig;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class AuctionEncryptionKeyFixture {
    public static final Long DEFAULT_MAX_AGE_SECONDS = 604800L;
    public static final String DEFAULT_MAX_AGE = "max-age=" + DEFAULT_MAX_AGE_SECONDS;
    public static final String DEFAULT_CACHED_AGE = "800";
    private static final Long EXPIRY_TTL_1SEC = 1L;
    private static final String CACHE_CONTROL_HEADER_LABEL = "cache-control";
    private static final String CACHED_AGE_HEADER_LABEL = "age";

    private static final String KEY_ID_LABEL = "id";
    private static final String PUBLIC_KEY_LABEL = "key";
    public static final String COORDINATOR_URL_AUCTION = "https://example-auction.com/full/url";
    public static final String COORDINATOR_URL_AUCTION_ORIGIN = "https://example-auction.com";
    public static final DBEncryptionKey ENCRYPTION_KEY_AUCTION =
            DBEncryptionKey.builder()
                    .setKeyIdentifier("152233fc-f255-4c3d-b3ef-7e2b7fbb9ca7")
                    .setPublicKey("3QKut1VYAJGrE3TTu4NGZq3sPSgAeRaaTIdi7eZqtwk=")
                    .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                    .setExpiryTtlSeconds(1000L)
                    .build();
    public static final DBEncryptionKey ENCRYPTION_KEY_AUCTION_TTL_1SECS =
            DBEncryptionKey.builder()
                    .setKeyIdentifier("1b333681-4fe3-4ba9-b603-5169d421734c")
                    .setPublicKey("nD4MogDIKg+NIXiJ3lEPHcf8mYOl1wjioNFe6h9pUAI=")
                    .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                    .setExpiryTtlSeconds(EXPIRY_TTL_1SEC)
                    .build();
    public static final ImmutableMap<String, List<String>> DEFAULT_RESPONSE_HEADERS =
            ImmutableMap.of(
                    CACHE_CONTROL_HEADER_LABEL,
                    List.of(DEFAULT_MAX_AGE),
                    CACHED_AGE_HEADER_LABEL,
                    List.of(DEFAULT_CACHED_AGE));
    public static final AuctionKey AUCTION_KEY_1 =
            AuctionKey.builder()
                    .setKeyId("152233fc-f255-4c3d-b3ef-7e2b7fbb9ca7")
                    .setPublicKey("3QKut1VYAJGrE3TTu4NGZq3sPSgAeRaaTIdi7eZqtwk=")
                    .build();
    public static final AuctionKey AUCTION_KEY_2 =
            AuctionKey.builder()
                    .setKeyId("1b333681-4fe3-4ba9-b603-5169d421734c")
                    .setPublicKey("nD4MogDIKg+NIXiJ3lEPHcf8mYOl1wjioNFe6h9pUAI=")
                    .build();
    private static final AuctionKey AUCTION_KEY_3 =
            AuctionKey.builder()
                    .setKeyId("420ec4eb-8753-4c45-88b9-7917baecbd21")
                    .setPublicKey("KROE/B6FBOV2+6coHHtpGi1Gxoi+bOh9srrBP64JPBk")
                    .build();
    private static final AuctionKey AUCTION_KEY_4 =
            AuctionKey.builder()
                    .setKeyId("49b4de4f-4c9e-4285-afa0-78c3baea13b3")
                    .setPublicKey("7tabvCt19oMF5Quu4cAQetS6xlLFkjIbcY6330+cjlo=")
                    .build();
    private static final AuctionKey AUCTION_KEY_5 =
            AuctionKey.builder()
                    .setKeyId("7b6724dc-839c-4108-bfa7-2e73eb19e5fe")
                    .setPublicKey("t/dzKzHJKe7k//n2u7wDdvxRtgXy9SncfXz6g8JB/m4=")
                    .build();

    public static final DBProtectedServersEncryptionConfig ENCRYPTION_KEY_AUCTION_WITH_COORDINATOR =
            DBProtectedServersEncryptionConfig.builder()
                    .setKeyIdentifier(AUCTION_KEY_2.keyId())
                    .setPublicKey(AUCTION_KEY_2.publicKey())
                    .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                    .setExpiryTtlSeconds(1000L)
                    .setCoordinatorUrl(COORDINATOR_URL_AUCTION)
                    .build();

    public static String getAuctionResponseBodySingleKey() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("keys", new JSONArray().put(getAuctionKeyJson(AUCTION_KEY_1)));
        return json.toString();
    }

    public static AdServicesHttpClientResponse mockAuctionKeyFetchResponse() throws JSONException {
        return AdServicesHttpClientResponse.builder()
                .setResponseBody(getDefaultAuctionResponseBody())
                .setResponseHeaders(DEFAULT_RESPONSE_HEADERS)
                .build();
    }

    public static AdServicesHttpClientResponse mockAuctionKeyFetchResponseWithOneKey()
            throws JSONException {
        return AdServicesHttpClientResponse.builder()
                .setResponseBody(getDefaultAuctionResponseBodyWithOneKey())
                .setResponseHeaders(DEFAULT_RESPONSE_HEADERS)
                .build();
    }

    /** Gets the AdServicesHttpClientResponse with the provided AuctionKey */
    public static AdServicesHttpClientResponse mockAuctionKeyFetchResponseWithGivenKey(
            AuctionKey auctionKey) throws JSONException {
        return AdServicesHttpClientResponse.builder()
                .setResponseBody(getDeterministicAuctionResponseBody(auctionKey))
                .setResponseHeaders(DEFAULT_RESPONSE_HEADERS)
                .build();
    }

    private static JSONObject getAuctionKeyJson(AuctionKey key) throws JSONException {
        return new JSONObject()
                .put(KEY_ID_LABEL, key.keyId())
                .put(PUBLIC_KEY_LABEL, key.publicKey());
    }

    private static String getDefaultAuctionResponseBody() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(
                "keys",
                new JSONArray()
                        .put(getAuctionKeyJson(AUCTION_KEY_1))
                        .put(getAuctionKeyJson(AUCTION_KEY_2))
                        .put(getAuctionKeyJson(AUCTION_KEY_3))
                        .put(getAuctionKeyJson(AUCTION_KEY_4))
                        .put(getAuctionKeyJson(AUCTION_KEY_5)));
        return json.toString();
    }

    private static String getDeterministicAuctionResponseBody(AuctionKey auctionKey)
            throws JSONException {
        JSONObject json = new JSONObject();
        json.put("keys", new JSONArray().put(getAuctionKeyJson(auctionKey)));
        return json.toString();
    }

    private static String getDefaultAuctionResponseBodyWithOneKey() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("keys", new JSONArray().put(getAuctionKeyJson(AUCTION_KEY_1)));
        return json.toString();
    }

    @AutoValue
    public abstract static class AuctionKey {
        public abstract String keyId();

        public abstract String publicKey();

        public static AuctionKey.Builder builder() {
            return new AutoValue_AuctionEncryptionKeyFixture_AuctionKey.Builder();
        }

        @AutoValue.Builder
        public abstract static class Builder {
            public abstract Builder setKeyId(String keyId);

            public abstract Builder setPublicKey(String publicKey);

            public abstract AuctionKey build();
        }
    }
}
