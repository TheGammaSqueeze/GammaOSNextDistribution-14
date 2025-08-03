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

import android.app.smartspace.uitemplatedata.CarouselTemplateData;
import android.app.smartspace.uitemplatedata.CarouselTemplateData.CarouselItem;
import android.app.smartspace.uitemplatedata.Text;
import android.os.Parcel;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests for {@link CarouselTemplateData}
 *
 * atest CtsSmartspaceServiceTestCases
 */
@RunWith(AndroidJUnit4.class)
public class CarouselTemplateDataTest {

    private static final String TAG = "CarouselTemplateDataTest";

    @Test
    public void testCreateCarouselTemplateData() {
        List<CarouselItem> items = new ArrayList<>();
        items.add(createCarouselItem());
        items.add(createCarouselItem());
        CarouselTemplateData carouselTemplateData = new CarouselTemplateData.Builder(items)
                .setCarouselAction(
                        SmartspaceTestUtils.createSmartspaceTapAction(getContext(), "card tap"))
                .build();

        assertThat(carouselTemplateData.getCarouselItems()).isEqualTo(items);
        assertThat(carouselTemplateData.getCarouselAction()).isEqualTo(
                SmartspaceTestUtils.createSmartspaceTapAction(getContext(), "card tap"));

        Parcel parcel = Parcel.obtain();
        parcel.setDataPosition(0);
        carouselTemplateData.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        CarouselTemplateData copyData =
                CarouselTemplateData.CREATOR.createFromParcel(parcel);
        assertThat(carouselTemplateData).isEqualTo(copyData);
        parcel.recycle();
    }

    private CarouselItem createCarouselItem() {
        return new CarouselItem.Builder()
                .setUpperText(new Text.Builder("upper").build())
                .setImage(SmartspaceTestUtils.createSmartspaceIcon("icon"))
                .setLowerText(new Text.Builder("lower").build())
                .setTapAction(
                        SmartspaceTestUtils.createSmartspaceTapAction(getContext(), "item tap"))
                .build();
    }
}
