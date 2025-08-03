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

package android.adservices.cts;

import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER;
import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;
import static android.view.KeyEvent.ACTION_DOWN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.adservices.adselection.ReportEventRequest;
import android.view.InputEvent;
import android.view.KeyEvent;

import com.android.adservices.common.SdkLevelSupportRule;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ReportEventRequestTest {
    private static final long AD_SELECTION_ID = 1234L;
    private static final String INTERACTION_KEY = "click";
    private static final InputEvent INPUT_EVENT = new KeyEvent(ACTION_DOWN, 0);
    private String mInteractionData;
    private static final int DESTINATIONS =
            FLAG_REPORTING_DESTINATION_SELLER | FLAG_REPORTING_DESTINATION_BUYER;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() throws Exception {
        JSONObject obj = new JSONObject().put("key", "value");
        mInteractionData = obj.toString();
    }

    @Test
    public void testBuildReportEventRequestSuccess_viewInputEvent() throws Exception {
        ReportEventRequest request =
                new ReportEventRequest.Builder(
                                AD_SELECTION_ID, INTERACTION_KEY, mInteractionData, DESTINATIONS)
                        .build();

        assertEquals(AD_SELECTION_ID, request.getAdSelectionId());
        assertEquals(INTERACTION_KEY, request.getKey());
        assertNull(request.getInputEvent());
        assertEquals(mInteractionData, request.getData());
        assertEquals(DESTINATIONS, request.getReportingDestinations());
    }

    @Test
    public void testBuildReportEventRequestSuccess_clickInputEvent() throws Exception {
        ReportEventRequest request =
                new ReportEventRequest.Builder(
                                AD_SELECTION_ID, INTERACTION_KEY, mInteractionData, DESTINATIONS)
                        .setInputEvent(INPUT_EVENT)
                        .build();

        assertEquals(AD_SELECTION_ID, request.getAdSelectionId());
        assertEquals(INTERACTION_KEY, request.getKey());
        assertEquals(INPUT_EVENT, request.getInputEvent());
        assertEquals(mInteractionData, request.getData());
        assertEquals(DESTINATIONS, request.getReportingDestinations());
    }

    @Test
    public void testFailsToBuildWithUnsetAdSelectionId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new ReportEventRequest.Builder(
                                    0, INTERACTION_KEY, mInteractionData, DESTINATIONS)
                            .build();
                });
    }

    @Test
    public void testFailsToBuildWithUnsetInteractionKey() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new ReportEventRequest.Builder(
                                    AD_SELECTION_ID, null, mInteractionData, DESTINATIONS)
                            .build();
                });
    }

    @Test
    public void testFailsToBuildWithUnsetInteractionData() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new ReportEventRequest.Builder(
                                    AD_SELECTION_ID, INTERACTION_KEY, null, DESTINATIONS)
                            .build();
                });
    }

    @Test
    public void testFailsToBuildWithUnsetDestinations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new ReportEventRequest.Builder(
                                    AD_SELECTION_ID, INTERACTION_KEY, mInteractionData, 0)
                            .build();
                });
    }

    @Test
    public void testFailsToBuildWithInvalidDestinations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new ReportEventRequest.Builder(
                                    AD_SELECTION_ID, INTERACTION_KEY, mInteractionData, 5)
                            .build();
                });
    }

    @Test
    public void testFailsToBuildWithEventDataExceedsMaxSize() {
        char[] largePayload = new char[65 * 1024]; // 65KB
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new ReportEventRequest.Builder(
                            AD_SELECTION_ID,
                            INTERACTION_KEY,
                            new String(largePayload),
                            DESTINATIONS);
                });
    }
}
