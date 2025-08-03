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

import android.app.smartspace.uitemplatedata.Icon;
import android.graphics.Bitmap;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link Icon}
 *
 * atest CtsSmartspaceServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class IconTest {

    private static final String TAG = "IconTest";

    @Test
    public void testCreateIcon() {
        android.graphics.drawable.Icon icon = android.graphics.drawable.Icon.createWithBitmap(
                Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8));
        Icon smartspaceIcon = new Icon.Builder(icon).setContentDescription(
                "test content").setShouldTint(false).build();

        assertThat(smartspaceIcon.getIcon()).isEqualTo(icon);
        assertThat(smartspaceIcon.getContentDescription()).isEqualTo("test content");
        assertThat(smartspaceIcon.shouldTint()).isFalse();

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        smartspaceIcon.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        Icon copyIcon = Icon.CREATOR.createFromParcel(parcel);
        assertThat(smartspaceIcon).isEqualTo(copyIcon);
        parcel.recycle();
    }
}
