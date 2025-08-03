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

import static com.google.common.truth.Truth.assertThat;

import android.app.smartspace.uitemplatedata.BaseTemplateData.SubItemLoggingInfo;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link SubItemLoggingInfo}
 *
 * atest CtsSmartspaceServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class SubItemLoggingInfoTest {

    private static final String TAG = "SubItemLoggingInfoTest";

    @Test
    public void testCreateSubItemLoggingInfo() {
        SubItemLoggingInfo itemLoggingInfo = new SubItemLoggingInfo.Builder(1, 1)
                .setPackageName("package name").build();

        assertThat(itemLoggingInfo.getInstanceId()).isEqualTo(1);
        assertThat(itemLoggingInfo.getFeatureType()).isEqualTo(1);
        assertThat(itemLoggingInfo.getPackageName()).isEqualTo("package name");

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        itemLoggingInfo.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SubItemLoggingInfo copyItemLoggingInfo = SubItemLoggingInfo.CREATOR.createFromParcel(
                parcel);
        assertThat(itemLoggingInfo).isEqualTo(copyItemLoggingInfo);
        parcel.recycle();
    }
}
