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

import android.app.smartspace.uitemplatedata.Text;
import android.os.Parcel;
import android.text.TextUtils;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link Text}
 *
 * atest CtsSmartspaceServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class TextTest {

    private static final String TAG = "TextTest";

    @Test
    public void testCreateText_defaultValues() {
        Text text = new Text.Builder("test").build();

        assertThat(text.getText()).isEqualTo("test");
        assertThat(text.getTruncateAtType()).isEqualTo(TextUtils.TruncateAt.END);
        assertThat(text.getMaxLines()).isEqualTo(1);

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        text.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        Text copyText = Text.CREATOR.createFromParcel(parcel);
        assertThat(text).isEqualTo(copyText);
        parcel.recycle();
    }

    @Test
    public void testCreateText_marqueeTrunctAtType_maxLinesTwo() {
        Text text = new Text.Builder("test")
                .setTruncateAtType(TextUtils.TruncateAt.MARQUEE).setMaxLines(2).build();

        assertThat(text.getText()).isEqualTo("test");
        assertThat(text.getTruncateAtType()).isEqualTo(TextUtils.TruncateAt.MARQUEE);
        assertThat(text.getMaxLines()).isEqualTo(2);

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        text.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        Text copyText = Text.CREATOR.createFromParcel(parcel);
        assertThat(text).isEqualTo(copyText);
        parcel.recycle();
    }
}
