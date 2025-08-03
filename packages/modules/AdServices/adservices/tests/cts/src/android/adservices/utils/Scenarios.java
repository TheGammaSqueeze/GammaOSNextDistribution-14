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

package android.adservices.utils;

/** Default constants for CUJ scenarios. */
public class Scenarios {

    public static final String BIDDING_LOGIC_PATH = "bidding";
    public static final String BIDDING_SIGNALS_PATH = "bidding/trusted";
    public static final String SCORING_LOGIC_PATH = "scoring";
    public static final String SCORING_SIGNALS_PATH = "scoring/trusted";
    public static final String FETCH_CA_PATH = "fetch/ca";
    public static final String UPDATE_CA_PATH = "update/ca";
    public static final String MEDIATION_LOGIC_PATH = "mediation";
    public static final String AD_RENDER_1 = "render_ad/1";
    public static final String AD_RENDER_2 = "render_ad/2";

    public static String getDailyUpdatePath(String customAudienceName) {
        return "bidding/daily/" + customAudienceName;
    }
}
