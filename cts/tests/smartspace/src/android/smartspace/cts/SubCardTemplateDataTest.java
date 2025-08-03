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

import android.app.smartspace.uitemplatedata.SubCardTemplateData;
import android.app.smartspace.uitemplatedata.Text;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link SubCardTemplateData}
 *
 * atest CtsSmartspaceServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class SubCardTemplateDataTest {

    private static final String TAG = "SubCardTemplateDataTest";

    @Test
    public void testCreateSubCardTemplateData() {
        SubCardTemplateData subCardTemplateData =
                new SubCardTemplateData.Builder(
                        SmartspaceTestUtils.createSmartspaceIcon("icon"))
                        .setSubCardText(new Text.Builder("text").build())
                        .setSubCardAction(
                                SmartspaceTestUtils.createSmartspaceTapAction(getContext(), "tap"))
                        .build();

        assertThat(subCardTemplateData.getSubCardIcon()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceIcon("icon"));
        assertThat(subCardTemplateData.getSubCardText()).isEqualTo(
                new Text.Builder("text").build());
        assertThat(subCardTemplateData.getSubCardAction()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceTapAction(getContext(), "tap"));

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        subCardTemplateData.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SubCardTemplateData copyData =
                SubCardTemplateData.CREATOR.createFromParcel(parcel);
        assertThat(subCardTemplateData).isEqualTo(copyData);
        parcel.recycle();
    }
}
