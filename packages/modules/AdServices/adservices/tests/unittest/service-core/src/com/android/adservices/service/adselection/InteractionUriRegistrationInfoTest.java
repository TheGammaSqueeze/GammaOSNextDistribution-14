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

import static com.android.adservices.service.adselection.InteractionUriRegistrationInfo.EXPECTED_STRUCTURE_MISMATCH;
import static com.android.adservices.service.adselection.ReportImpressionScriptEngine.INTERACTION_KEY_ARG_NAME;
import static com.android.adservices.service.adselection.ReportImpressionScriptEngine.INTERACTION_REPORTING_URI_ARG_NAME;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import android.net.Uri;

import com.android.adservices.common.SdkLevelSupportRule;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;

public class InteractionUriRegistrationInfoTest {
    private static final String CLICK = "click";
    private static final String HOVER = "hover";
    private static final String INTERACTION_REPORTING_URI_STRING = "https://domain.com/click";
    private static final Uri INTERACTION_REPORTING_URI =
            Uri.parse(INTERACTION_REPORTING_URI_STRING);
    private static final Uri DIFFERENT_INTERACTION_REPORTING_URI =
            Uri.parse("https://different.com/click");

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testFromJsonSucceeds() throws Exception {
        JSONObject obj = new JSONObject();
        obj.put(INTERACTION_KEY_ARG_NAME, CLICK);
        obj.put(INTERACTION_REPORTING_URI_ARG_NAME, INTERACTION_REPORTING_URI_STRING);

        InteractionUriRegistrationInfo interactionUriRegistrationInfo =
                InteractionUriRegistrationInfo.fromJson(obj);
        assertEquals(interactionUriRegistrationInfo.getInteractionKey(), CLICK);
        assertEquals(
                interactionUriRegistrationInfo.getInteractionReportingUri(),
                INTERACTION_REPORTING_URI);
    }

    @Test
    public void testFromJsonFailsWithWrongInteractionKeyName() throws Exception {
        JSONObject obj = new JSONObject();
        obj.put(INTERACTION_KEY_ARG_NAME + "wrong", CLICK);
        obj.put(INTERACTION_REPORTING_URI_ARG_NAME, INTERACTION_REPORTING_URI_STRING);

        assertThrows(
                EXPECTED_STRUCTURE_MISMATCH,
                IllegalArgumentException.class,
                () -> {
                    InteractionUriRegistrationInfo.fromJson(obj);
                });
    }

    @Test
    public void testFromJsonFailsWithWrongInteractionReportingUriKeyName() throws Exception {
        JSONObject obj = new JSONObject();
        obj.put(INTERACTION_KEY_ARG_NAME, CLICK);
        obj.put(INTERACTION_REPORTING_URI_ARG_NAME + "wrong", INTERACTION_REPORTING_URI_STRING);

        assertThrows(
                EXPECTED_STRUCTURE_MISMATCH,
                IllegalArgumentException.class,
                () -> {
                    InteractionUriRegistrationInfo.fromJson(obj);
                });
    }

    @Test
    public void testFromJsonFailsWhenInteractionKeyNotAString() throws Exception {
        JSONObject obj = new JSONObject();

        JSONObject innerObj = new JSONObject().put(INTERACTION_KEY_ARG_NAME, CLICK);
        obj.put(INTERACTION_KEY_ARG_NAME, innerObj);

        obj.put(INTERACTION_REPORTING_URI_ARG_NAME, INTERACTION_REPORTING_URI_STRING);

        assertThrows(
                EXPECTED_STRUCTURE_MISMATCH,
                IllegalArgumentException.class,
                () -> {
                    InteractionUriRegistrationInfo.fromJson(obj);
                });
    }

    @Test
    public void testFromJsonFailsWhenInteractionReportingUriNotAString() throws Exception {
        JSONObject obj = new JSONObject();
        obj.put(INTERACTION_KEY_ARG_NAME, CLICK);

        JSONObject innerObj =
                new JSONObject()
                        .put(INTERACTION_REPORTING_URI_ARG_NAME, INTERACTION_REPORTING_URI_STRING);
        obj.put(INTERACTION_REPORTING_URI_ARG_NAME, innerObj);

        assertThrows(
                EXPECTED_STRUCTURE_MISMATCH,
                IllegalArgumentException.class,
                () -> {
                    InteractionUriRegistrationInfo.fromJson(obj);
                });
    }

    @Test
    public void testFromJsonWithEmptyListValueFails() throws Exception {
        JSONObject obj = new JSONObject();
        obj.put(INTERACTION_KEY_ARG_NAME, CLICK);
        obj.put(INTERACTION_REPORTING_URI_ARG_NAME, new JSONArray());

        assertThrows(
                EXPECTED_STRUCTURE_MISMATCH,
                IllegalArgumentException.class,
                () -> {
                    InteractionUriRegistrationInfo.fromJson(obj);
                });
    }

    @Test
    public void testFromJsonWithPopulatedListValueFails() throws Exception {
        JSONObject obj = new JSONObject();
        obj.put(INTERACTION_KEY_ARG_NAME, CLICK);
        obj.put(
                INTERACTION_REPORTING_URI_ARG_NAME,
                new JSONArray().put(INTERACTION_REPORTING_URI_STRING));

        assertThrows(
                EXPECTED_STRUCTURE_MISMATCH,
                IllegalArgumentException.class,
                () -> {
                    InteractionUriRegistrationInfo.fromJson(obj);
                });
    }

    @Test
    public void testEqualsSuccessfulCase() throws Exception {
        InteractionUriRegistrationInfo interactionUriRegistrationInfo1 =
                InteractionUriRegistrationInfo.builder()
                        .setInteractionKey(CLICK)
                        .setInteractionReportingUri(INTERACTION_REPORTING_URI)
                        .build();
        InteractionUriRegistrationInfo interactionUriRegistrationInfo2 =
                InteractionUriRegistrationInfo.builder()
                        .setInteractionKey(CLICK)
                        .setInteractionReportingUri(INTERACTION_REPORTING_URI)
                        .build();

        assertEquals(interactionUriRegistrationInfo1, interactionUriRegistrationInfo2);
    }

    @Test
    public void testEqualsFailedCaseInteractionKey() throws Exception {
        InteractionUriRegistrationInfo interactionUriRegistrationInfo1 =
                InteractionUriRegistrationInfo.builder()
                        .setInteractionKey(CLICK)
                        .setInteractionReportingUri(INTERACTION_REPORTING_URI)
                        .build();
        InteractionUriRegistrationInfo interactionUriRegistrationInfo2 =
                InteractionUriRegistrationInfo.builder()
                        .setInteractionKey(HOVER)
                        .setInteractionReportingUri(INTERACTION_REPORTING_URI)
                        .build();

        assertNotEquals(interactionUriRegistrationInfo1, interactionUriRegistrationInfo2);
    }

    @Test
    public void testEqualsFailedCaseInteractionReportingUri() throws Exception {
        InteractionUriRegistrationInfo interactionUriRegistrationInfo1 =
                InteractionUriRegistrationInfo.builder()
                        .setInteractionKey(CLICK)
                        .setInteractionReportingUri(INTERACTION_REPORTING_URI)
                        .build();
        InteractionUriRegistrationInfo interactionUriRegistrationInfo2 =
                InteractionUriRegistrationInfo.builder()
                        .setInteractionKey(CLICK)
                        .setInteractionReportingUri(DIFFERENT_INTERACTION_REPORTING_URI)
                        .build();

        assertNotEquals(interactionUriRegistrationInfo1, interactionUriRegistrationInfo2);
    }

    @Test
    public void testHashCodeSuccessfulCase() throws Exception {
        InteractionUriRegistrationInfo interactionUriRegistrationInfo1 =
                InteractionUriRegistrationInfo.builder()
                        .setInteractionKey(CLICK)
                        .setInteractionReportingUri(INTERACTION_REPORTING_URI)
                        .build();
        InteractionUriRegistrationInfo interactionUriRegistrationInfo2 =
                InteractionUriRegistrationInfo.builder()
                        .setInteractionKey(CLICK)
                        .setInteractionReportingUri(INTERACTION_REPORTING_URI)
                        .build();

        assertEquals(
                interactionUriRegistrationInfo1.hashCode(),
                interactionUriRegistrationInfo2.hashCode());
    }

    @Test
    public void testHashCodeFailedCaseInteractionKey() throws Exception {
        InteractionUriRegistrationInfo interactionUriRegistrationInfo1 =
                InteractionUriRegistrationInfo.builder()
                        .setInteractionKey(CLICK)
                        .setInteractionReportingUri(INTERACTION_REPORTING_URI)
                        .build();
        InteractionUriRegistrationInfo interactionUriRegistrationInfo2 =
                InteractionUriRegistrationInfo.builder()
                        .setInteractionKey(HOVER)
                        .setInteractionReportingUri(INTERACTION_REPORTING_URI)
                        .build();

        assertNotEquals(
                interactionUriRegistrationInfo1.hashCode(),
                interactionUriRegistrationInfo2.hashCode());
    }

    @Test
    public void testHashCodeFailedCaseInteractionReportingUri() throws Exception {
        InteractionUriRegistrationInfo interactionUriRegistrationInfo1 =
                InteractionUriRegistrationInfo.builder()
                        .setInteractionKey(CLICK)
                        .setInteractionReportingUri(INTERACTION_REPORTING_URI)
                        .build();
        InteractionUriRegistrationInfo interactionUriRegistrationInfo2 =
                InteractionUriRegistrationInfo.builder()
                        .setInteractionKey(CLICK)
                        .setInteractionReportingUri(DIFFERENT_INTERACTION_REPORTING_URI)
                        .build();

        assertNotEquals(
                interactionUriRegistrationInfo1.hashCode(),
                interactionUriRegistrationInfo2.hashCode());
    }
}
