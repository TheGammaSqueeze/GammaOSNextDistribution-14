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
package android.smartspace.cts;

import static androidx.test.InstrumentationRegistry.getContext;

import static com.google.common.truth.Truth.assertThat;

import android.app.smartspace.uitemplatedata.HeadToHeadTemplateData;
import android.app.smartspace.uitemplatedata.Text;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link HeadToHeadTemplateData}
 *
 * atest CtsSmartspaceServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class HeadToHeadTemplateDataTest {

    private static final String TAG = "HeadToHeadTemplateDataTest";

    @Test
    public void testCreateHeadToHeadTemplateData() {
        HeadToHeadTemplateData headToHeadTemplateData =
                new HeadToHeadTemplateData.Builder()
                        .setHeadToHeadTitle(new Text.Builder("title").build())
                        .setHeadToHeadFirstCompetitorIcon(
                                SmartspaceTestUtils.createSmartspaceIcon("icon1"))
                        .setHeadToHeadSecondCompetitorIcon(
                                SmartspaceTestUtils.createSmartspaceIcon("icon2"))
                        .setHeadToHeadFirstCompetitorText(new Text.Builder("text1").build())
                        .setHeadToHeadSecondCompetitorText(new Text.Builder("text1").build())
                        .setHeadToHeadAction(
                                SmartspaceTestUtils.createSmartspaceTapAction(getContext(), "tap"))
                        .build();

        assertThat(headToHeadTemplateData.getHeadToHeadTitle()).isEqualTo(
                new Text.Builder("title").build());
        assertThat(headToHeadTemplateData.getHeadToHeadFirstCompetitorIcon()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceIcon("icon1"));
        assertThat(headToHeadTemplateData.getHeadToHeadSecondCompetitorIcon()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceIcon("icon2"));
        assertThat(headToHeadTemplateData.getHeadToHeadFirstCompetitorText()).isEqualTo(
                new Text.Builder("text1").build());
        assertThat(headToHeadTemplateData.getHeadToHeadSecondCompetitorText()).isEqualTo(
                new Text.Builder("text1").build());
        assertThat(headToHeadTemplateData.getHeadToHeadAction()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceTapAction(getContext(), "tap"));

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        headToHeadTemplateData.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        HeadToHeadTemplateData copyData =
                HeadToHeadTemplateData.CREATOR.createFromParcel(parcel);
        assertThat(headToHeadTemplateData).isEqualTo(copyData);
        parcel.recycle();
    }
}
