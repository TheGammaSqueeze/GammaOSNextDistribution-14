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

import android.app.smartspace.uitemplatedata.BaseTemplateData.SubItemInfo;
import android.app.smartspace.uitemplatedata.BaseTemplateData.SubItemLoggingInfo;
import android.app.smartspace.uitemplatedata.Text;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link SubItemInfo}
 *
 * atest CtsSmartspaceServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class SubItemInfoTest {

    private static final String TAG = "SubItemInfoTest";

    @Test
    public void testCreateCarouselItem() {
        SubItemInfo itemInfo = new SubItemInfo.Builder()
                .setText(new Text.Builder("test").build())
                .setIcon(SmartspaceTestUtils.createSmartspaceIcon("icon"))
                .setTapAction(
                        SmartspaceTestUtils.createSmartspaceTapAction(getContext(), "action"))
                .setLoggingInfo(new SubItemLoggingInfo.Builder(0, 0)
                        .setPackageName("package name").build())
                .build();

        assertThat(itemInfo.getText()).isEqualTo(new Text.Builder("test").build());
        assertThat(itemInfo.getIcon()).isEqualTo(SmartspaceTestUtils.createSmartspaceIcon("icon"));
        assertThat(itemInfo.getTapAction()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceTapAction(getContext(), "action"));
        assertThat(itemInfo.getLoggingInfo()).isEqualTo(new SubItemLoggingInfo.Builder(0, 0)
                .setPackageName("package name").build());

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        itemInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SubItemInfo copyItemInfo = SubItemInfo.CREATOR.createFromParcel(parcel);
        assertThat(itemInfo).isEqualTo(copyItemInfo);
        parcel.recycle();
    }
}
