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

import android.app.smartspace.uitemplatedata.CarouselTemplateData.CarouselItem;
import android.app.smartspace.uitemplatedata.Text;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for {@link CarouselItem}
 *
 * atest CtsSmartspaceServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class CarouselItemTest {

    private static final String TAG = "CarouselItemTest";

    @Test
    public void testCreateCarouselItem() {
        CarouselItem item = new CarouselItem.Builder()
                .setUpperText(new Text.Builder("upper").build())
                .setImage(SmartspaceTestUtils.createSmartspaceIcon("icon"))
                .setLowerText(new Text.Builder("lower").build())
                .setTapAction(
                        SmartspaceTestUtils.createSmartspaceTapAction(getContext(), "item tap"))
                .build();

        assertThat(item.getUpperText()).isEqualTo(new Text.Builder("upper").build());
        assertThat(item.getImage()).isEqualTo(SmartspaceTestUtils.createSmartspaceIcon("icon"));
        assertThat(item.getLowerText()).isEqualTo(new Text.Builder("lower").build());
        assertThat(item.getTapAction()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceTapAction(getContext(), "item tap"));

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        item.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        CarouselItem copyItem = CarouselItem.CREATOR.createFromParcel(parcel);
        assertThat(item).isEqualTo(copyItem);
        parcel.recycle();
    }
}
