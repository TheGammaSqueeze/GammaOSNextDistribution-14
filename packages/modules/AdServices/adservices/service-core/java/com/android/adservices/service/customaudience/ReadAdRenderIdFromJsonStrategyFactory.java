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

import static com.android.adservices.service.customaudience.CustomAudienceUpdatableDataReader.AD_RENDER_ID_KEY;

import android.annotation.NonNull;

import com.android.adservices.data.common.DBAdData;
import com.android.adservices.service.common.AdRenderIdValidator;
import com.android.adservices.service.common.JsonUtils;

import org.json.JSONException;
import org.json.JSONObject;

/** Factory for {@link ReadAdRenderIdFromJsonStrategy} */
public class ReadAdRenderIdFromJsonStrategyFactory {

    private static class AdRenderIdEnabledStrategy implements ReadAdRenderIdFromJsonStrategy {

        @NonNull private final AdRenderIdValidator mAdRenderIdValidator;

        AdRenderIdEnabledStrategy(long maxLength) {
            mAdRenderIdValidator = AdRenderIdValidator.createEnabledInstance(maxLength);
        }

        /**
         * Adds ad render id field to the provided AdData builder.
         *
         * @param adDataBuilder the AdData builder to modify.
         * @param adDataJsonObj the AdData JSON to extract from
         * @throws JSONException if the key is found but the schema is incorrect
         * @throws NullPointerException if the key found by the field is null
         */
        @Override
        public void readId(DBAdData.Builder adDataBuilder, JSONObject adDataJsonObj)
                throws JSONException, NullPointerException, IllegalArgumentException {
            String adRenderId = null;
            if (adDataJsonObj.has(AD_RENDER_ID_KEY)) {
                adRenderId = JsonUtils.getStringFromJson(adDataJsonObj, AD_RENDER_ID_KEY);
                mAdRenderIdValidator.validate(adRenderId);
            }
            adDataBuilder.setAdRenderId(adRenderId);
        }
    }

    private static class AdRenderIdDisabledStrategy implements ReadAdRenderIdFromJsonStrategy {
        /**
         * Does nothing.
         *
         * @param adDataBuilder unused
         * @param adDataJsonObj unused
         */
        @Override
        public void readId(DBAdData.Builder adDataBuilder, JSONObject adDataJsonObj) {}
    }

    /**
     * Returns the appropriate {@link ReadAdRenderIdFromJsonStrategy} based whether ad render id is
     * enabled
     *
     * @param adRenderIdEnabled Should be true if ad render id is enabled.
     * @return An implementation of {@link ReadAdRenderIdFromJsonStrategy}
     */
    public static ReadAdRenderIdFromJsonStrategy getStrategy(
            boolean adRenderIdEnabled, long adRenderIdMaxLength) {
        if (adRenderIdEnabled) {
            return new ReadAdRenderIdFromJsonStrategyFactory.AdRenderIdEnabledStrategy(
                    adRenderIdMaxLength);
        }
        return new ReadAdRenderIdFromJsonStrategyFactory.AdRenderIdDisabledStrategy();
    }
}
