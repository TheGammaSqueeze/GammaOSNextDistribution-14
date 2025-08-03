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

import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER;
import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;
import static android.view.KeyEvent.ACTION_UP;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;


import android.adservices.common.CommonFixture;
import android.os.Parcel;
import android.view.InputEvent;
import android.view.KeyEvent;

import com.android.adservices.common.SdkLevelSupportRule;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ReportInteractionInputTest {
    private static final long AD_SELECTION_ID = 1234L;
    private static final String INTERACTION_KEY = "click";
    private static final String CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final String CALLER_SDK_NAME = "sdk.package.name";
    private String mInteractionData;
    private static final int DESTINATIONS =
            FLAG_REPORTING_DESTINATION_SELLER | FLAG_REPORTING_DESTINATION_BUYER;
    private static final InputEvent CLICK_EVENT =
            new KeyEvent(/* action= */ ACTION_UP, /* code= */ 90);
    private static final String AD_ID = "35a4ac90-e4dc-4fe7-bbc6-95e804aa7dbc";

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() throws Exception {
        JSONObject obj = new JSONObject().put("key", "value");
        mInteractionData = obj.toString();
    }

    @Test
    public void testWriteToParcel_nonNullOptionalParameters() throws Exception {
        ReportInteractionInput input =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setInteractionKey(INTERACTION_KEY)
                        .setInteractionData(mInteractionData)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setReportingDestinations(DESTINATIONS)
                        .setInputEvent(CLICK_EVENT)
                        .setAdId(AD_ID)
                        .setCallerSdkName(CALLER_SDK_NAME)
                        .build();

        Parcel p = Parcel.obtain();
        input.writeToParcel(p, 0);
        p.setDataPosition(0);

        ReportInteractionInput fromParcel = ReportInteractionInput.CREATOR.createFromParcel(p);

        assertEquals(AD_SELECTION_ID, fromParcel.getAdSelectionId());
        assertEquals(INTERACTION_KEY, fromParcel.getInteractionKey());
        assertEquals(mInteractionData, fromParcel.getInteractionData());
        assertEquals(CALLER_PACKAGE_NAME, fromParcel.getCallerPackageName());
        assertEquals(DESTINATIONS, fromParcel.getReportingDestinations());
        // KeyEventTest tests equality using the string representation of its keys
        assertEquals(CLICK_EVENT.toString(), fromParcel.getInputEvent().toString());
        assertEquals(AD_ID, fromParcel.getAdId());
        assertEquals(CALLER_SDK_NAME, fromParcel.getCallerSdkName());
    }

    @Test
    public void testWriteToParcel_nullInputEvent() throws Exception {
        ReportInteractionInput input =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setInteractionKey(INTERACTION_KEY)
                        .setInteractionData(mInteractionData)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setReportingDestinations(DESTINATIONS)
                        .setInputEvent(null)
                        .setAdId(AD_ID)
                        .setCallerSdkName(CALLER_SDK_NAME)
                        .build();

        Parcel p = Parcel.obtain();
        input.writeToParcel(p, 0);
        p.setDataPosition(0);

        ReportInteractionInput fromParcel = ReportInteractionInput.CREATOR.createFromParcel(p);

        assertEquals(AD_SELECTION_ID, fromParcel.getAdSelectionId());
        assertEquals(INTERACTION_KEY, fromParcel.getInteractionKey());
        assertEquals(mInteractionData, fromParcel.getInteractionData());
        assertEquals(CALLER_PACKAGE_NAME, fromParcel.getCallerPackageName());
        assertEquals(DESTINATIONS, fromParcel.getReportingDestinations());
        assertThat(fromParcel.getInputEvent()).isNull();
        assertEquals(AD_ID, fromParcel.getAdId());
        assertEquals(CALLER_SDK_NAME, fromParcel.getCallerSdkName());
    }

    @Test
    public void testWriteToParcel_nullAdId() throws Exception {
        ReportInteractionInput input =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setInteractionKey(INTERACTION_KEY)
                        .setInteractionData(mInteractionData)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setReportingDestinations(DESTINATIONS)
                        .setInputEvent(CLICK_EVENT)
                        .setAdId(null)
                        .setCallerSdkName(CALLER_SDK_NAME)
                        .build();

        Parcel p = Parcel.obtain();
        input.writeToParcel(p, 0);
        p.setDataPosition(0);

        ReportInteractionInput fromParcel = ReportInteractionInput.CREATOR.createFromParcel(p);

        assertEquals(AD_SELECTION_ID, fromParcel.getAdSelectionId());
        assertEquals(INTERACTION_KEY, fromParcel.getInteractionKey());
        assertEquals(mInteractionData, fromParcel.getInteractionData());
        assertEquals(CALLER_PACKAGE_NAME, fromParcel.getCallerPackageName());
        assertEquals(DESTINATIONS, fromParcel.getReportingDestinations());
        // KeyEventTest tests equality using the string representation of its keys
        assertEquals(CLICK_EVENT.toString(), fromParcel.getInputEvent().toString());
        assertThat(fromParcel.getAdId()).isNull();
        assertEquals(CALLER_SDK_NAME, fromParcel.getCallerSdkName());
    }

    @Test
    public void testWriteToParcel_nullCallerSdkName() throws Exception {
        ReportInteractionInput input =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setInteractionKey(INTERACTION_KEY)
                        .setInteractionData(mInteractionData)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setReportingDestinations(DESTINATIONS)
                        .setInputEvent(CLICK_EVENT)
                        .setAdId(AD_ID)
                        .setCallerSdkName(null)
                        .build();

        Parcel p = Parcel.obtain();
        input.writeToParcel(p, 0);
        p.setDataPosition(0);

        ReportInteractionInput fromParcel = ReportInteractionInput.CREATOR.createFromParcel(p);

        assertEquals(AD_SELECTION_ID, fromParcel.getAdSelectionId());
        assertEquals(INTERACTION_KEY, fromParcel.getInteractionKey());
        assertEquals(mInteractionData, fromParcel.getInteractionData());
        assertEquals(CALLER_PACKAGE_NAME, fromParcel.getCallerPackageName());
        assertEquals(DESTINATIONS, fromParcel.getReportingDestinations());
        // KeyEventTest tests equality using the string representation of its keys
        assertEquals(CLICK_EVENT.toString(), fromParcel.getInputEvent().toString());
        assertEquals(AD_ID, fromParcel.getAdId());
        assertThat(fromParcel.getCallerSdkName()).isNull();
    }

    @Test
    public void testFailsToBuildWithUnsetAdSelectionId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new ReportInteractionInput.Builder()
                            .setInteractionKey(INTERACTION_KEY)
                            .setInteractionData(mInteractionData)
                            .setCallerPackageName(CALLER_PACKAGE_NAME)
                            .setReportingDestinations(DESTINATIONS)
                            .build();
                });
    }

    @Test
    public void testFailsToBuildWithNullInteractionKey() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new ReportInteractionInput.Builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setInteractionData(mInteractionData)
                            .setCallerPackageName(CALLER_PACKAGE_NAME)
                            .setReportingDestinations(DESTINATIONS)
                            .build();
                });
    }

    @Test
    public void testFailsToBuildWithNullInteractionData() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new ReportInteractionInput.Builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setInteractionKey(INTERACTION_KEY)
                            .setCallerPackageName(CALLER_PACKAGE_NAME)
                            .setReportingDestinations(DESTINATIONS)
                            .build();
                });
    }

    @Test
    public void testFailsToBuildWithUnsetCallerPackageName() {
        assertThrows(
                NullPointerException.class,
                () -> {
                    new ReportInteractionInput.Builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setInteractionKey(INTERACTION_KEY)
                            .setInteractionData(mInteractionData)
                            .setReportingDestinations(DESTINATIONS)
                            .build();
                });
    }

    @Test
    public void testFailsToBuildWithUnsetDestinations() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new ReportInteractionInput.Builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setInteractionKey(INTERACTION_KEY)
                            .setInteractionData(mInteractionData)
                            .setCallerPackageName(CALLER_PACKAGE_NAME)
                            .build();
                });
    }

    @Test
    public void testReportInteractionInputDescribeContents() {
        ReportInteractionInput input =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setInteractionKey(INTERACTION_KEY)
                        .setInteractionData(mInteractionData)
                        .setReportingDestinations(DESTINATIONS)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        assertEquals(input.describeContents(), 0);
    }
}
