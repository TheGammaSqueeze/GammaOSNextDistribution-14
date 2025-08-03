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

import android.app.smartspace.uitemplatedata.Icon;
import android.app.smartspace.uitemplatedata.SubImageTemplateData;
import android.app.smartspace.uitemplatedata.Text;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link SubImageTemplateData}
 *
 * atest CtsSmartspaceServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class SubImageTemplateDataTest {

    private static final String TAG = "SubImageTemplateDataTest";

    @Test
    public void testCreateSubImageTemplateData() {
        List<Text> texts = new ArrayList<>();
        texts.add(new Text.Builder("text1").build());
        texts.add(new Text.Builder("text2").build());

        List<Icon> images = new ArrayList<>();
        images.add(SmartspaceTestUtils.createSmartspaceIcon("icon1"));
        images.add(SmartspaceTestUtils.createSmartspaceIcon("icon2"));
        images.add(SmartspaceTestUtils.createSmartspaceIcon("icon3"));

        SubImageTemplateData subImageTemplateData =
                new SubImageTemplateData.Builder(texts, images)
                        .setSubImageAction(
                                SmartspaceTestUtils.createSmartspaceTapAction(getContext(), "tap"))
                        .build();

        assertThat(subImageTemplateData.getSubImageTexts()).isEqualTo(texts);
        assertThat(subImageTemplateData.getSubImages()).isEqualTo(images);
        assertThat(subImageTemplateData.getSubImageAction()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceTapAction(getContext(), "tap"));

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        subImageTemplateData.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SubImageTemplateData copyData =
                SubImageTemplateData.CREATOR.createFromParcel(parcel);
        assertThat(subImageTemplateData).isEqualTo(copyData);
        parcel.recycle();
    }
}
