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
package android.adservices.test.scenario.adservices.fledge.utils;

import java.util.List;
import java.util.Map;
/**
 * A simple SelectAdRequest POJO used to convert to and from json that will be used to make HTTPS
 * POST SelectAd call.
 *
 * <p>https://github.com/privacysandbox/fledge-docs/blob/main/bidding_auction_services_api.md#sellerfrontend-service-and-api-endpoints
 */
public class SelectAdRequest {
    public String protectedAudienceCiphertext;
    public AuctionConfig auctionConfig;
    public String clientType;

    public void setProtectedAudienceCiphertext(String protectedAudienceCiphertext) {
        this.protectedAudienceCiphertext = protectedAudienceCiphertext;
    }

    public static class AuctionConfig {
        public String sellerSignals;
        public String auctionSignals;
        public List<String> buyerList;
        public String seller;
        public Map<String, PerBuyerConfig> perBuyerConfig;
        public String sellerDebugId;
        public int buyerTimeoutMs;
    }

    public static class PerBuyerConfig {
        public String buyerSignals;
        public int buyerKvExperimentGroupId;
        public String generateBidCodeVersion;
        public String buyerDebugId;
        public String protectedSignalsGenerateBidCodeVersion;
        public String protectedSignalsGenerateEmbeddingsVersion;
    }
}
