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

import android.app.smartspace.SmartspaceTarget;
import android.app.smartspace.uitemplatedata.BaseTemplateData;
import android.app.smartspace.uitemplatedata.BaseTemplateData.SubItemInfo;
import android.app.smartspace.uitemplatedata.BaseTemplateData.SubItemLoggingInfo;
import android.app.smartspace.uitemplatedata.CombinedCardsTemplateData;
import android.app.smartspace.uitemplatedata.Text;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link CombinedCardsTemplateData}
 *
 * atest CtsSmartspaceServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class CombinedCardsTemplateDataTest {

    private static final String TAG = "CombinedCardsTemplateDataTest";

    @Test
    public void testCreateCombinedCardsTemplateData() {
        List<BaseTemplateData> dataList = new ArrayList<>();
        dataList.add(createBaseTemplateData());
        dataList.add(createBaseTemplateData());
        CombinedCardsTemplateData combinedCardsTemplateData =
                new CombinedCardsTemplateData.Builder(dataList).build();

        assertThat(combinedCardsTemplateData.getCombinedCardDataList()).isEqualTo(dataList);

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        combinedCardsTemplateData.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        CombinedCardsTemplateData copyData =
                CombinedCardsTemplateData.CREATOR.createFromParcel(parcel);
        assertThat(combinedCardsTemplateData).isEqualTo(copyData);
        parcel.recycle();
    }

    private BaseTemplateData createBaseTemplateData() {
        return new BaseTemplateData.Builder(SmartspaceTarget.UI_TEMPLATE_DEFAULT)
                .setPrimaryItem(new SubItemInfo.Builder()
                        .setText(new Text.Builder("title").build())
                        .setIcon(SmartspaceTestUtils.createSmartspaceIcon("title icon"))
                        .setTapAction(
                                SmartspaceTestUtils.createSmartspaceTapAction(getContext(),
                                        "primary action"))
                        .setLoggingInfo(new SubItemLoggingInfo.Builder(0, 0).setPackageName(
                                "package name 0").build())
                        .build())
                .setSubtitleItem(new SubItemInfo.Builder()
                        .setText(new Text.Builder("subtitle").build())
                        .setIcon(SmartspaceTestUtils.createSmartspaceIcon("subtitle icon"))
                        .setTapAction(
                                SmartspaceTestUtils.createSmartspaceTapAction(getContext(),
                                        "subtitle action"))
                        .setLoggingInfo(new SubItemLoggingInfo.Builder(1, 1).setPackageName(
                                "package name 1").build())
                        .build())
                .setSubtitleSupplementalItem(new SubItemInfo.Builder()
                        .setText(new Text.Builder("subtitle supplemental").build())
                        .setIcon(SmartspaceTestUtils.createSmartspaceIcon(
                                "subtitle supplemental icon"))
                        .setTapAction(
                                SmartspaceTestUtils.createSmartspaceTapAction(getContext(),
                                        "subtitle supplemental action"))
                        .setLoggingInfo(new SubItemLoggingInfo.Builder(2, 2).setPackageName(
                                "package name 2").build())
                        .build())
                .setSupplementalLineItem(new SubItemInfo.Builder()
                        .setText(new Text.Builder("supplemental line").build())
                        .setIcon(SmartspaceTestUtils.createSmartspaceIcon(
                                "supplemental line icon"))
                        .setTapAction(
                                SmartspaceTestUtils.createSmartspaceTapAction(getContext(),
                                        "supplemental line action"))
                        .setLoggingInfo(new SubItemLoggingInfo.Builder(3, 3).setPackageName(
                                "package name 3").build())
                        .build())
                .setSupplementalAlarmItem(new SubItemInfo.Builder()
                        .setText(new Text.Builder("alarm supplemental").build())
                        .setIcon(SmartspaceTestUtils.createSmartspaceIcon("alarm icon"))
                        .setTapAction(
                                SmartspaceTestUtils.createSmartspaceTapAction(getContext(),
                                        "alarm action"))
                        .setLoggingInfo(new SubItemLoggingInfo.Builder(4, 4).setPackageName(
                                "package name 4").build())
                        .build())
                .setLayoutWeight(1)
                .build();
    }
}
