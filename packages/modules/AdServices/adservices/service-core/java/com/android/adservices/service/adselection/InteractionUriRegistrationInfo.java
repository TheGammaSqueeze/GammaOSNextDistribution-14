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

package com.android.adservices.service.adselection;

import static com.android.adservices.service.adselection.ReportImpressionScriptEngine.INTERACTION_KEY_ARG_NAME;
import static com.android.adservices.service.adselection.ReportImpressionScriptEngine.INTERACTION_REPORTING_URI_ARG_NAME;
import static com.android.adservices.service.common.JsonUtils.getStringFromJson;

import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.LoggerFactory;

import com.google.auto.value.AutoValue;

import org.json.JSONException;
import org.json.JSONObject;

/** POJO to represent an {@code InteractionKey:Uri} pairing */
@AutoValue
public abstract class InteractionUriRegistrationInfo {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    public static String EXPECTED_STRUCTURE_MISMATCH =
            "InteractionUriRegistrationInfo does not match expected structure!";

    /** @return the name of the interaction (e.g., click, view, etc.) */
    @NonNull
    public abstract String getInteractionKey();

    /** @return Uri to be used during interaction reporting */
    @NonNull
    public abstract Uri getInteractionReportingUri();

    /**
     * Deserializes a {@link InteractionUriRegistrationInfo} from a {@code JSONObject}
     *
     * @throws IllegalArgumentException if {@code JSONObject} fails to meet these conditions: 1.
     *     {@code JSONObject} has keys with String values named: {@link
     *     ReportImpressionScriptEngine#INTERACTION_KEY_ARG_NAME} and {@link
     *     ReportImpressionScriptEngine#INTERACTION_REPORTING_URI_ARG_NAME} 2. The value of key
     *     {@link ReportImpressionScriptEngine#INTERACTION_REPORTING_URI_ARG_NAME} properly parses
     *     into a Uri
     */
    @NonNull
    public static InteractionUriRegistrationInfo fromJson(@NonNull JSONObject jsonObject)
            throws IllegalArgumentException {
        try {
            return builder()
                    .setInteractionKey(getStringFromJson(jsonObject, INTERACTION_KEY_ARG_NAME))
                    .setInteractionReportingUri(
                            Uri.parse(
                                    getStringFromJson(
                                            jsonObject, INTERACTION_REPORTING_URI_ARG_NAME)))
                    .build();
        } catch (JSONException e) {
            sLogger.v(String.format("Unexpected object structure: %s", jsonObject));
            throw new IllegalArgumentException(EXPECTED_STRUCTURE_MISMATCH);
        }
    }

    /** @return generic builder */
    @NonNull
    public static InteractionUriRegistrationInfo.Builder builder() {
        return new AutoValue_InteractionUriRegistrationInfo.Builder();
    }

    /** Builder class for a {@link InteractionUriRegistrationInfo} object. */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Sets the interactionKey for the {@link InteractionUriRegistrationInfo} object. */
        @NonNull
        public abstract InteractionUriRegistrationInfo.Builder setInteractionKey(
                @NonNull String interactionKey);

        /**
         * Sets the interactionReportingUri for the {@link InteractionUriRegistrationInfo} object.
         */
        @NonNull
        public abstract InteractionUriRegistrationInfo.Builder setInteractionReportingUri(
                @NonNull Uri interactionReportingUri);

        /** Builds a {@link InteractionUriRegistrationInfo} object. */
        @NonNull
        public abstract InteractionUriRegistrationInfo build();
    }
}
