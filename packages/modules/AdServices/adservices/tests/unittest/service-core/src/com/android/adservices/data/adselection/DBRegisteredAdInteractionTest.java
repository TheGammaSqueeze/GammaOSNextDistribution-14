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

package com.android.adservices.data.adselection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.adselection.ReportEventRequest;
import android.net.Uri;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

public class DBRegisteredAdInteractionTest {
    public static final int AD_SELECTION_ID = 1;
    public static final String INTERACTION_KEY_CLICK = "CLICK";

    @ReportEventRequest.ReportingDestination
    public static final int DESTINATION_SELLER =
            ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;

    private static final String BASE_URI = "https://www.seller.com/";
    public static final Uri EVENT_REPORTING_URI = Uri.parse(BASE_URI + INTERACTION_KEY_CLICK);

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildDBRegisteredAdInteraction() {
        DBRegisteredAdInteraction dbRegisteredAdInteraction =
                DBRegisteredAdInteraction.builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setInteractionKey(INTERACTION_KEY_CLICK)
                        .setDestination(DESTINATION_SELLER)
                        .setInteractionReportingUri(EVENT_REPORTING_URI)
                        .build();

        assertEquals(AD_SELECTION_ID, dbRegisteredAdInteraction.getAdSelectionId());
        assertEquals(INTERACTION_KEY_CLICK, dbRegisteredAdInteraction.getInteractionKey());
        assertEquals(DESTINATION_SELLER, dbRegisteredAdInteraction.getDestination());
        assertEquals(EVENT_REPORTING_URI, dbRegisteredAdInteraction.getInteractionReportingUri());
    }

    @Test
    public void testThrowsExceptionWithNoAdSelectionId() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBRegisteredAdInteraction.builder()
                            .setInteractionKey(INTERACTION_KEY_CLICK)
                            .setDestination(DESTINATION_SELLER)
                            .setInteractionReportingUri(EVENT_REPORTING_URI)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoInteractionKey() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBRegisteredAdInteraction.builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setDestination(DESTINATION_SELLER)
                            .setInteractionReportingUri(EVENT_REPORTING_URI)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoDestination() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBRegisteredAdInteraction.builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setInteractionKey(INTERACTION_KEY_CLICK)
                            .setInteractionReportingUri(EVENT_REPORTING_URI)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoInteractionReportingUri() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBRegisteredAdInteraction.builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setInteractionKey(INTERACTION_KEY_CLICK)
                            .setDestination(DESTINATION_SELLER)
                            .build();
                });
    }
}
